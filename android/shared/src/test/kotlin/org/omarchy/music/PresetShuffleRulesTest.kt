package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kinds of choice can be left out of Personalization Preset Shuffle. */
class PresetShuffleRulesTest {
    @Test fun whatIsLeftOutIsKept() {
        assertTrue(PresetShuffleRules.read(null).isEmpty())
        assertEquals(setOf("look", "layout"), PresetShuffleRules.read("layout, look,nonsense,"))
        assertEquals("in the tab's order", "look,layout", PresetShuffleRules.write(setOf("layout", "look")))
        assertEquals(setOf("look", "layout"), PresetShuffleRules.read(PresetShuffleRules.write(setOf("layout", "look"))))
    }

    @Test fun aKindIsLeftOutAndTakenBackIn() {
        val a = PresetShuffleRules.toggled(emptySet(), "look")
        assertEquals(setOf("look"), a)
        assertTrue(PresetShuffleRules.toggled(a, "look").isEmpty())
        assertEquals("not a kind: nothing changes", a, PresetShuffleRules.toggled(a, "nonsense"))
    }

    @Test fun theSettingsSayWhatStays() {
        assertEquals("Nothing left out: the shuffle changes everything a preset holds", PresetShuffleRules.note(emptySet()))
        assertEquals("Left as you set them: Look, Layout", PresetShuffleRules.note(setOf("layout", "look")))
        assertEquals("Everything is left out: the shuffle changes nothing", PresetShuffleRules.note(PresetShuffleRules.PARTS.keys))
    }
}
