package org.omarchy.music

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

/** How loud the DJ's voice is (the DJ's Voice settings): a level from a quarter to twice as loud as it's made, put on
 *  its sound before it's heard. Louder than it's made, its loudest moments are rounded off rather than cut (no
 *  crackle). */
object VoiceVolume {
    const val KEY = "dj_voice_volume"
    const val MIN = 0.25f
    const val MAX = 2f
    private const val KNEE = 0.8f

    fun clamp(gain: Float): Float = if (gain.isNaN()) 1f else gain.coerceIn(MIN, MAX)

    /** "100%" for a level. */
    fun label(gain: Float): String = "${Math.round(clamp(gain) * 100)}%"

    /** One sample at gain: the same under the knee, rounded off towards full scale above it (never past it). */
    fun level(v: Float, gain: Float): Float {
        val x = v * gain
        val a = abs(x)
        if (a <= KNEE) return x
        return sign(x) * (KNEE + (1 - KNEE) * tanh((a - KNEE) / (1 - KNEE)))
    }

    /** samples (-1 to 1) at gain, in place; as they are at 100%. */
    fun apply(samples: FloatArray, gain: Float): FloatArray {
        val g = clamp(gain)
        if (g == 1f) return samples
        for (i in samples.indices) samples[i] = level(samples[i], g)
        return samples
    }

    /** 16-bit little-endian PCM at gain, in place; as it is at 100%. */
    fun apply16(pcm: ByteArray, gain: Float): ByteArray {
        val g = clamp(gain)
        if (g == 1f) return pcm
        var i = 0
        while (i + 1 < pcm.size) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort() / 32768f
            val o = Math.round(level(s, g) * 32767f).coerceIn(-32768, 32767)
            pcm[i] = o.toByte(); pcm[i + 1] = (o shr 8).toByte()
            i += 2
        }
        return pcm
    }
}
