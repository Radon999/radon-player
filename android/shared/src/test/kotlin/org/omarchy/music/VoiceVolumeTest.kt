package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DJ's voice level: quieter is plainly quieter, louder never goes past full scale, 100% leaves it untouched. */
class VoiceVolumeTest {
    @Test fun halfIsHalfAndFullIsUntouched() {
        val s = floatArrayOf(0.5f, -0.4f, 0.1f)
        assertEquals(0.25f, VoiceVolume.apply(s.copyOf(), 0.5f)[0], 1e-6f)
        assertEquals(-0.2f, VoiceVolume.apply(s.copyOf(), 0.5f)[1], 1e-6f)
        assertTrue("100%: the same samples", s.copyOf().contentEquals(VoiceVolume.apply(s.copyOf(), 1f)))
    }

    @Test fun louderIsRoundedOffBelowFullScale() {
        val loud = VoiceVolume.apply(floatArrayOf(0.9f, -0.9f, 0.3f), 2f)
        assertTrue("never past full scale", loud.all { kotlin.math.abs(it) < 1f })
        assertTrue("still louder than it was", loud[0] > 0.9f && loud[1] < -0.9f)
        assertEquals("quiet parts simply doubled", 0.6f, loud[2], 1e-6f)
    }

    @Test fun pcmSixteenBitsGoesTheSameWay() {
        val pcm = byteArrayOf(0x00, 0x40, 0x00, 0xC0.toByte())   // 16384 (0.5), -16384 (-0.5)
        VoiceVolume.apply16(pcm, 0.5f)
        val a = ((pcm[1].toInt() shl 8) or (pcm[0].toInt() and 0xff)).toShort()
        val b = ((pcm[3].toInt() shl 8) or (pcm[2].toInt() and 0xff)).toShort()
        assertEquals(8192.0, a.toDouble(), 1.0)
        assertEquals(-8192.0, b.toDouble(), 1.0)
    }

    @Test fun theLevelStaysInItsRange() {
        assertEquals(VoiceVolume.MAX, VoiceVolume.clamp(9f))
        assertEquals(VoiceVolume.MIN, VoiceVolume.clamp(0f))
        assertEquals("125%", VoiceVolume.label(1.25f))
    }
}
