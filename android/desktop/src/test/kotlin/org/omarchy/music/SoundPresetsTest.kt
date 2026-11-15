package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sound presets of yours: what they are kept under, their names from files, and saving and deleting one. The
 *  equalizer is put back as it was after the test. */
class SoundPresetsTest {
    @Test fun aPresetIsKeptUnderItsNameTidied() {
        assertEquals("late-night", SoundPresets.presetKey("Late Night!"))
        assertEquals("fiio-ka17", SoundPresets.presetKey("  FiiO KA17 "))
        assertEquals("a name of signs only has none (one is made up)", "", SoundPresets.presetKey("!!!"))
    }

    @Test fun theSameHeadphonesFromTwoSourcesAreTwoCorrections() {
        val a = SoundPresets.correctionKey(AutoEq.Entry("HIFIMAN Sundara", "oratory1990"))
        val b = SoundPresets.correctionKey(AutoEq.Entry("HIFIMAN Sundara", "Rtings"))
        assertEquals("ae:hifiman-sundara-oratory1990", a)
        assertFalse(a == b)
    }

    @Test fun aNameFromAFileLeavesOutItsEndingAndWhatOtherAppsAdd() {
        assertEquals("HIFIMAN Sundara", SoundPresets.nameFromFile("HIFIMAN Sundara ParametricEQ.txt", "My EQ"))
        assertEquals("Bass lift", SoundPresets.nameFromFile("Bass lift GraphicEQ.txt", "My preset"))
        assertEquals("Warm", SoundPresets.nameFromFile("Warm.txt", "My preset"))
        assertEquals("no name: the fallback", "My preset", SoundPresets.nameFromFile(null, "My preset"))
        assertEquals("nothing left of it: the fallback", "My EQ", SoundPresets.nameFromFile("GraphicEQ.txt", "My EQ"))
    }

    @Test fun aPresetSavedIsUsedAndDeletedGoesBackToCustom() {
        Harness.ctx
        Harness.ui {
            val was = Music.eq
            try {
                SoundPresets.savePreset("Presets Test One", List(10) { it.toDouble() - 4 }, 10)
                val key = "presets-test-one"
                assertEquals("kept among yours, by its name", "Presets Test One", Music.eq.myPresets[key]?.name)
                val kept = Music.eq.myPresets[key]!!.bands
                assertEquals("ten bands", 10, kept.size)
                for (i in 0 until 10) assertEquals("band $i as given", i.toDouble() - 4, kept[i], 0.05)
                assertEquals("and used straight away", "my:$key", Music.eq.preset)
                assertTrue(Music.eq.on)
                SoundPresets.savePreset("   ")   // no name: nothing saved
                assertEquals(was.myPresets.size + 1, Music.eq.myPresets.size)
                SoundPresets.deletePreset("my:$key")
                assertFalse(Music.eq.myPresets.containsKey(key))
                assertEquals("the one in use deleted: the bands stay, as a custom Sound", "custom", Music.eq.preset)
            } finally { Music.setEq { was } }
        }
    }
}
