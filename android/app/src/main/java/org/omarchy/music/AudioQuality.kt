package org.omarchy.music

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Sound quality, as the desktop has it: bit-perfect to a USB DAC (Android 14 and up: the DAC to
 *  the app, each song at its own rate and depth, with nothing in between), and ReplayGain from the
 *  files' tags. */
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



}
