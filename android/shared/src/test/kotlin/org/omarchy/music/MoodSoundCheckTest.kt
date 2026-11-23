package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sound check: moods the sound plainly says otherwise of. */
class MoodSoundCheckTest {
    private val loudFast = SoundFeatures(loud = -6.0, range = 10.0, bpm = 140.0, beat = 0.6, bright = 2400.0, onsets = 6.0, major = 0.4)
    private val softSlow = SoundFeatures(loud = -17.0, range = 4.0, bpm = 70.0, beat = 0.15, bright = 1200.0, onsets = 2.5, major = 0.3)
    private val middling = SoundFeatures(loud = -9.5, range = 6.0, bpm = 105.0, beat = 0.4, bright = 1900.0, onsets = 4.5)

    @Test fun aLoudFastSongInChillBecomesEnergetic() {
        val fix = MoodRules.soundCheck(listOf("Chill", "Happy"), loudFast)!!
        assertEquals(listOf("Energetic", "Happy"), fix.moods)
        assertTrue(fix.why, fix.why.contains("140 BPM") && fix.why.contains("chill"))
    }

    @Test fun darkAndLoudBecomesIntense() {
        assertEquals(listOf("Intense", "Dark"), MoodRules.soundCheck(listOf("Calm", "Dark"), loudFast)!!.moods)
    }

    @Test fun aSoftSlowSongInHypeBecomesChill() {
        assertEquals(listOf("Chill"), MoodRules.soundCheck(listOf("Hype", "Energetic"), softSlow)!!.moods)
        assertEquals(listOf("Sad", "Calm"), MoodRules.soundCheck(listOf("Sad", "Intense"), softSlow)!!.moods)
    }

    @Test fun nothingWhenTheSoundAgreesOrIsInBetween() {
        assertNull(MoodRules.soundCheck(listOf("Energetic", "Party"), loudFast))
        assertNull(MoodRules.soundCheck(listOf("Chill", "Energetic"), loudFast))   // already both
        assertNull(MoodRules.soundCheck(listOf("Chill"), middling))
        assertNull(MoodRules.soundCheck(listOf("Hype"), middling))
        assertNull(MoodRules.soundCheck(listOf("Chill"), null))
        assertTrue(MoodRules.liveliness(loudFast) >= MoodRules.LIVELY_AT)
        assertTrue(MoodRules.liveliness(softSlow) <= MoodRules.CALM_AT)
    }
}
