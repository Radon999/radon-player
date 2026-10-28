package org.omarchy.music

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin

/** Sound quality, as the desktop has it: bit-perfect to a USB DAC (Android 14 and up: the DAC to
 *  the app, each song at its own rate and depth, with nothing in between), ReplayGain from the
 *  files' tags, and "Is it really lossless?": FLACs made from an MP3 or AAC, and hi-res made from
 *  CD quality, found by the shape of their spectrum. */
object AudioQuality {
    // ---- bit-perfect ----

    /** The USB DAC plugged in, if there is one. */
    private fun usbDac(am: AudioManager): AudioDeviceInfo? = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

    private val media get() = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()

    /** The song about to play is rate Hz (bits deep): ask Android for the DAC bit-perfect at that format. What
     *  happened, in words (for Settings and the EQ). Off: let Android mix again. */
    fun applyBitPerfect(ctx: Context, on: Boolean, rate: Int, bits: Int): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT < 34) return if (on) "Bit-perfect needs Android 14 or later" else ""
        val dac = usbDac(am)
        if (!on) { if (dac != null) runCatching { am.clearPreferredMixerAttributes(media, dac) }; return "" }
        dac ?: return "Bit-perfect is for a USB DAC: plug one in (Bluetooth and the phone's speaker always mix)"
        val all = runCatching { am.getSupportedMixerAttributes(dac) }.getOrDefault(emptyList())
            .filter { it.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        if (all.isEmpty()) return "${dac.productName} doesn't offer bit-perfect playback to Android"
        val atRate = all.filter { it.format.sampleRate == rate || rate <= 0 }
        // The player hands over floats (lossless for 16- and 24-bit), so float first, then the deepest PCM.
        val pick = atRate.firstOrNull { it.format.encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT }
            ?: atRate.maxByOrNull { encBits(it.format.encoding) }
        if (pick == null) {
            val rates = all.map { it.format.sampleRate }.distinct().sorted().joinToString { "%g kHz".format(it / 1000.0) }
            runCatching { am.clearPreferredMixerAttributes(media, dac) }
            return "Not bit-perfect for this song: ${dac.productName} takes $rates, the song is ${"%g".format(rate / 1000.0)} kHz"
        }
        val ok = runCatching { am.setPreferredMixerAttributes(media, dac, pick) }.getOrDefault(false)
        return if (ok) "Bit-perfect: ${if (bits > 0) "$bits-bit/" else ""}${"%g".format(rate / 1000.0)} kHz straight to ${dac.productName}"
            else "${dac.productName} turned bit-perfect down for this song"
    }

    private fun encBits(e: Int) = when (e) {
        android.media.AudioFormat.ENCODING_PCM_32BIT, android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
        else -> 0
    }

    /** ReplayGain from a song's tags (FLAC's Vorbis comments, MP3's TXXX frames): its gain in dB, or null. */
    fun replayGain(metadata: androidx.media3.common.Metadata?, album: Boolean): Double? {
        metadata ?: return null
        val want = if (album) "REPLAYGAIN_ALBUM_GAIN" else "REPLAYGAIN_TRACK_GAIN"
        for (i in 0 until metadata.length()) {
            val e = metadata[i]
            val (k, v) = when (e) {
                is androidx.media3.extractor.metadata.vorbis.VorbisComment -> e.key to e.value
                is androidx.media3.extractor.metadata.id3.TextInformationFrame -> (e.description ?: "") to (e.values.firstOrNull() ?: "")
                else -> continue
            }
            if (k.equals(want, true)) return v.replace("dB", "", true).trim().toDoubleOrNull()
        }
        return null
    }

    // ---- is it really lossless? ----

    @Serializable
    data class Verdict(val verdict: String, val why: String = "", val stamp: Long = 0)

    private val ser = MapSerializer(String.serializer(), Verdict.serializer())
    private var cache: MutableMap<String, Verdict>? = null
    private fun cacheFile() = File(Music.ctx.filesDir, "quality-checks.json")

    /** (blocking) Whether a lossless file is what it says: verdict "lossy" (made from an MP3 or AAC: it stops
     *  like a wall below 21 kHz), "upsampled" (hi-res made from CD quality), "ok" or "unknown". Kept by file. */
    @Synchronized fun check(uri: String, stamp: Long): Verdict {
        val c = cache ?: (readText(cacheFile())?.let { runCatching { JSON.decodeFromString(ser, it) }.getOrNull() }?.toMutableMap()
            ?: mutableMapOf()).also { cache = it }
        c[uri]?.takeIf { it.stamp == stamp }?.let { return it }
        val v = runCatching { analyze(Music.ctx, Uri.parse(uri)) }.getOrElse { Verdict("unknown", it.message ?: "") }.copy(stamp = stamp)
        c[uri] = v
        writeAtomic(cacheFile(), JSON.encodeToString(ser, c))
        return v
    }

    /** When a file last changed and how big it is, so a new file under the same name is checked again. */
    fun stampOf(uri: String): Long = runCatching {
        Music.ctx.contentResolver.query(Uri.parse(uri), arrayOf(android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
            android.provider.MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) * 1_000_003L + c.getLong(1) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    /** What the check found, in a few words, for the list. */
    fun said(v: Verdict) = when (v.verdict) { "lossy" -> "Made from an MP3 or AAC"; "upsampled" -> "Upsampled from CD quality"; else -> v.verdict }

    private fun analyze(ctx: Context, uri: Uri): Verdict {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        val ti = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: return Verdict("unknown", "no sound in it")
        val fmt = ex.getTrackFormat(ti)
        val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val dur = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        ex.selectTrack(ti)
        ex.seekTo(dur / 3, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val n = 4096
        val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
        val acc = DoubleArray(n / 2)
        val buf = DoubleArray(n)
        var fill = 0
        var frames = 0
        val want = rate * 20   // 20 seconds of it
        var got = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (got < want) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o < 0) { if (inputDone && o == MediaCodec.INFO_TRY_AGAIN_LATER) break; continue }
                val out = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                val enc = runCatching { codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) }.getOrDefault(android.media.AudioFormat.ENCODING_PCM_16BIT)
                val floatOut = enc == android.media.AudioFormat.ENCODING_PCM_FLOAT
                out.position(info.offset); out.limit(info.offset + info.size)
                while (out.remaining() >= ch * (if (floatOut) 4 else 2)) {
                    var x = 0.0
                    repeat(ch) { x += if (floatOut) out.float.toDouble() else out.short / 32768.0 }
                    buf[fill++] = x / ch
                    got++
                    if (fill == n) {
                        fft(buf, window, acc); frames++; fill = 0
                    }
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
        if (frames == 0) return Verdict("unknown", "too short")
        // The same levels as the desktop's: each of 256 bins, on a log scale.
        val bins = 256
        val per = acc.size / bins
        val prof = DoubleArray(bins) { b ->
            val m = (0 until per).sumOf { acc[b * per + it] } / per / frames
            (20 * ln(m.coerceAtLeast(1e-12)) / ln(10.0) + 120).coerceIn(0.0, 120.0)
        }
        val cliff = cliff(prof) ?: return Verdict("ok")
        val hz = cliff * rate / 2
        return when {
            rate >= 88200 && hz in 20000.0..24500.0 -> Verdict("upsampled", "nothing above ${"%.1f".format(hz / 1000)} kHz: made from CD quality")
            hz < 21000 -> Verdict("lossy", "it stops like a wall at ${"%.1f".format(hz / 1000)} kHz: made from an MP3 or AAC")
            else -> Verdict("ok")
        }
    }

    /** Where the music stops like a wall, as a fraction of the Nyquist frequency (the desktop's spectrum_cliff). */
    internal fun cliff(prof: DoubleArray): Double? {
        val w = prof.size
        val mids = prof.slice(w / 10 until w * 4 / 10).sorted()
        val ref = mids[mids.size / 2]
        if (ref < 8) return null
        val top = (0 until w).lastOrNull { prof[it] >= ref * 0.5 } ?: return null
        if (top >= w - 2) return null
        val span = maxOf(2, Math.round(w * 0.06).toInt())
        val after = (top + span until minOf(w, top + 3 * span)).map { prof[it] }
        return if (prof[minOf(w - 1, top + span)] < ref * 0.12 && after.all { it < ref * 0.12 }) (top + 1.0) / w else null
    }

    /** One windowed FFT of buf, its magnitudes added to acc. */
    private fun fft(buf: DoubleArray, window: DoubleArray, acc: DoubleArray) {
        val n = buf.size
        val re = DoubleArray(n) { buf[it] * window[it] }
        val im = DoubleArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            for (i in 0 until n step len) for (k in 0 until len / 2) {
                val wr = cos(ang * k); val wi = sin(ang * k)
                val ur = re[i + k]; val ui = im[i + k]
                val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                re[i + k] = ur + vr; im[i + k] = ui + vi
                re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
            }
            len = len shl 1
        }
        for (k in acc.indices) acc[k] += hypot(re[k], im[k])
    }
}
