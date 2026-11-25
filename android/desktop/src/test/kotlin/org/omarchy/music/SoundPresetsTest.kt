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

    @Test fun aPresetOfYoursChangedIsSavedOverAndRenamed() {
        Harness.ctx
        Harness.ui {
            val was = Music.eq
            try {
                Music.setEqCount(10)
                SoundPresets.savePreset("Presets Test Two", List(10) { 0.0 }, 10)
                val key = "presets-test-two"
                Music.setEqBand(2, 5.0)
                assertEquals("moving a band makes it custom", "custom", Music.eq.preset)
                assertEquals("but it remembers which of yours it came from", key, Music.eq.soundFrom)
                SoundPresets.overwritePreset()
                assertEquals("saved over: the band in it", 5.0, Music.eq.myPresets[key]!!.bands[2], 0.05)
                assertEquals("and it's the one in use again", "my:$key", Music.eq.preset)
                assertEquals("no copy made", was.myPresets.size + 1, Music.eq.myPresets.size)
                SoundPresets.renamePreset("my:$key", "Renamed Two")
                assertEquals("Renamed Two", Music.eq.myPresets[key]!!.name)
                Music.setEqPreset("rock")
                assertEquals("a built-in one forgets it", "", Music.eq.soundFrom)
            } finally { Music.setEq { was } }
        }
    }

    @Test fun anOutputsPresetChangedIsSavedOverAndEdited() {
        Harness.ctx
        Harness.ui {
            val was = Music.eq
            try {
                Music.setEq { it.copy(perOutput = true) }
                val out = Music.eq.output.ifEmpty { Music.outputNow.key }
                Music.setEqCount(10)
                Music.saveOutputPreset("Out Test", asDefault = false)
                val key = Music.eqPresetsFor(out).first { it.second.name == "Out Test" }.first
                assertEquals("saved as: remembered for Save over", key, Music.eq.outputFrom[out])
                Music.setEqBand(3, -4.0)
                Music.overwriteOutputPreset(key)
                assertEquals("saved over: the band in it", -4.0, Music.eq.outputPresets[key]!!.eq.bands[3], 0.05)
                assertEquals("no copy made", 1, Music.eqPresetsFor(out).count { it.second.name == "Out Test" })
                Music.editOutputPreset(key, "Out Test Renamed", asDefault = true)
                assertEquals("Out Test Renamed", Music.eq.outputPresets[key]!!.name)
                assertEquals("made its default", key, Music.eq.outputDefaults[out])
                Music.editOutputPreset(key, "Out Test Renamed", asDefault = false)
                assertEquals("and not any more", null, Music.eq.outputDefaults[out])
                Music.deleteOutputPreset(key)
                assertEquals("deleted: nothing left to save over", null, Music.eq.outputFrom[out])
            } finally { Music.setEq { was } }
        }
    }
}
