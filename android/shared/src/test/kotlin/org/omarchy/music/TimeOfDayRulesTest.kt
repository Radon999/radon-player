package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Presets by time of day: which part of the day an hour is in, and when a preset is put on. */
class TimeOfDayRulesTest {
    @Test fun thePartsOfTheDay() {
        assertEquals("night", TimeOfDayRules.part(0)); assertEquals("night", TimeOfDayRules.part(5))
        assertEquals("morning", TimeOfDayRules.part(6)); assertEquals("morning", TimeOfDayRules.part(10))
        assertEquals("day", TimeOfDayRules.part(11)); assertEquals("day", TimeOfDayRules.part(16))
        assertEquals("evening", TimeOfDayRules.part(17)); assertEquals("evening", TimeOfDayRules.part(21))
        assertEquals("night", TimeOfDayRules.part(22)); assertEquals("night", TimeOfDayRules.part(23))
        assertEquals("Evening (17:00 to 22:00)", TimeOfDayRules.label("evening"))
    }

    @Test fun onceWhenAPartBegins() {
        assertTrue(TimeOfDayRules.due(true, "preset-23", "night", 322, "evening@322"))
        assertFalse("put on already for tonight: changing it by hand since then is left alone", TimeOfDayRules.due(true, "preset-23", "night", 322, "night@322"))
        assertTrue("the next day", TimeOfDayRules.due(true, "preset-23", "night", 323, "night@322"))
        assertFalse("no preset for this part", TimeOfDayRules.due(true, "", "night", 322, ""))
        assertFalse("off", TimeOfDayRules.due(false, "preset-23", "night", 322, ""))
    }
}
