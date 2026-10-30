package org.omarchy.music

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.PI

/** The phone's side of the PC's ffmpeg checks, done with Android's own decoders: Loudness check (clipping, a hi-res file
 *  that isn't) and the song's sound as AcoustID knows it (Chromaprint, the same fingerprint ffmpeg makes on the PC). */
object AudioTools {
    val supported = true

    /** The phone checks songs only while it's charging, so the check never costs battery. */
    fun canRunNow(): Boolean = runCatching {
        (Music.ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager).isCharging
    }.getOrDefault(false)
    const val WHEN = ". The phone checks only while it's charging, so it never costs battery"

    /** When a song's file last changed (null: it's gone), for checking it again when it changes. */
    fun modified(path: String): Long? = if (path.startsWith("content:")) runCatching {
        Music.ctx.contentResolver.query(android.net.Uri.parse(path), arrayOf(android.provider.MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) * 1000 else null }
    }.getOrNull() else java.io.File(path).takeIf { it.isFile }?.lastModified()

    /** Decode [path] to floats (-1..1), interleaved, a block at a time; stop after [maxSecs] (0: the whole song).
     *  [block] gets (samples, count, sampleRate, channels). */
    private fun decode(path: String, maxSecs: Int, block: (FloatArray, Int, Int, Int) -> Unit) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(Music.ctx, android.net.Uri.parse(path), null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("no sound in the file")
            ex.selectTrack(track)
            val inFmt = ex.getTrackFormat(track)
            inFmt.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
            val codec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(inFmt, null, null, 0)
                codec.start()
                var rate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var ch = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var enc = AudioFormat.ENCODING_PCM_16BIT
                val info = MediaCodec.BufferInfo()
                var inDone = false
                var buf = FloatArray(16384)
                var frames = 0L
                while (true) {
                    if (!inDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        enc = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    } else if (o >= 0) {
                        val bb = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                        bb.position(info.offset); bb.limit(info.offset + info.size)
                        val n = if (enc == AudioFormat.ENCODING_PCM_FLOAT) info.size / 4 else info.size / 2
                        if (buf.size < n) buf = FloatArray(n)
                        if (enc == AudioFormat.ENCODING_PCM_FLOAT) bb.asFloatBuffer().get(buf, 0, n)
                        else { val s = bb.asShortBuffer(); for (k in 0 until n) buf[k] = s.get(k) / 32768f }
                        codec.releaseOutputBuffer(o, false)
                        if (n > 0) block(buf, n, rate, ch)
                        frames += n / maxOf(ch, 1)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        if (maxSecs > 0 && frames >= maxSecs.toLong() * rate) break
                    }
                }
                codec.stop()
            } finally { codec.release() }
        } finally { ex.release() }
    }

    /** A 2-pole highpass at [f] Hz (ffmpeg's highpass=poles=2, Q 0.707), run twice, as on the PC. */
    private class HighPass(f: Double, rate: Int) {
        private val b0: Double; private val b1: Double; private val b2: Double; private val a1: Double; private val a2: Double
        init {
            val w = 2 * PI * f / rate; val alpha = kotlin.math.sin(w) / (2 * 0.707); val c = kotlin.math.cos(w); val a0 = 1 + alpha
            b0 = (1 + c) / 2 / a0; b1 = -(1 + c) / a0; b2 = (1 + c) / 2 / a0; a1 = -2 * c / a0; a2 = (1 - alpha) / a0
        }
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        fun run(x: Double): Double { val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2; x2 = x1; x1 = x; y2 = y1; y1 = y; return y }
    }

    /** (blocking) clipped: samples at full scale (0: none to speak of); peak in dBFS; fake: "" or why it isn't the hi-res it says. */
    fun quality(path: String, rate: Int, bits: Int): QualityResult {
        var peak = 0.0; var peakCount = 0L; var sum = 0.0; var n = 0L; var deep = false
        val hiRes = rate > 48000
        var hp: Array<Pair<HighPass, HighPass>>? = null; var hpSum = 0.0
        decode(path, 0) { buf, count, r, ch ->
            if (hiRes && hp == null) hp = Array(ch) { HighPass(21000.0, r) to HighPass(21000.0, r) }
            for (k in 0 until count) {
                val x = buf[k].toDouble(); val a = abs(x)
                if (a > peak + 1e-9) { peak = a; peakCount = 1 } else if (a >= peak - 1e-9) peakCount++
                sum += x * x
                if (bits >= 24 && !deep) { val v = Math.round(x * 8388608.0); if (v and 0xFF != 0L) deep = true }
                hp?.let { h -> val p = h[k % h.size]; val y = p.second.run(p.first.run(x)); hpSum += y * y }
            }
            n += count
        }
        if (n == 0L) return QualityResult()
        val peakDb = if (peak > 0) 20 * log10(peak) else null
        val rmsDb = if (sum > 0) 10 * log10(sum / n) else null
        val clipped = if (peakDb != null && peakDb >= -0.1 && peakCount >= 200) peakCount.toInt() else 0
        val fake = ArrayList<String>()
        if (bits >= 24 && !deep) fake += "16-bit music in a 24-bit file"
        if (hiRes && rmsDb != null) {
            val top = if (hpSum > 0) 10 * log10(hpSum / n) else -200.0
            if (top - rmsDb < -80) fake += "upsampled: nothing above 21 kHz in a ${"%.1f".format(rate / 1000.0).removeSuffix(".0")} kHz file"
        }
        return QualityResult(clipped, peakDb?.let { Math.round(it * 100) / 100.0 }, fake.joinToString(", "))
    }

    /** (blocking) A song's sound as AcoustID knows it: (its fingerprint, its length in seconds), from its first two minutes. */
    fun fingerprint(path: String, secs: Int): Pair<String, Int> {
        var cp: Chromaprint? = null
        var shorts = ShortArray(16384)
        decode(path, 120) { buf, count, r, ch ->
            val c = cp ?: Chromaprint(r, ch).also { cp = it }
            if (shorts.size < count) shorts = ShortArray(count)
            for (k in 0 until count) shorts[k] = Math.round(buf[k] * 32768f).coerceIn(-32768, 32767).toShort()
            c.feed(shorts, count)
        }
        val fp = cp?.finish() ?: ""
        if (fp.isEmpty()) error("couldn't hear the song")
        return fp to secs
    }
}

