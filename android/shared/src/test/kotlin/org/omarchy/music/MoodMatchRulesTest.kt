package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moods match: only the device that isn't the main one takes moods, and only with the switch on. */
class MoodMatchRulesTest {
    @Test fun offNothingGoesAcross() {
        assertFalse(MoodMatchRules.takes(false, "pc", "phone"))
        assertFalse(MoodMatchRules.takes(false, "phone", "phone"))
        assertFalse(MoodMatchRules.leads(false, "pc", "pc"))
    }

    @Test fun theOtherDeviceTakesTheMainOnes() {
        assertTrue("the PC leads: the phone takes", MoodMatchRules.takes(true, "pc", "phone"))
        assertFalse("the PC leads: it takes nothing", MoodMatchRules.takes(true, "pc", "pc"))
        assertTrue(MoodMatchRules.leads(true, "pc", "pc"))
        assertTrue("the phone leads: the PC takes", MoodMatchRules.takes(true, "phone", "pc"))
        assertFalse(MoodMatchRules.takes(true, "phone", "phone"))
    }

    @Test fun settingsSaysWhichWayTheyGo() {
        assertEquals("", MoodMatchRules.note(false, "pc", "phone", "phone", "PC", false))
        assertTrue(MoodMatchRules.note(true, "pc", "phone", "phone", "PC", false).contains("put on this phone's songs"))
        assertTrue(MoodMatchRules.note(true, "pc", "pc", "PC", "phone", false).contains("go to the phone"))
        assertTrue("frozen moods take nothing, and it says so", MoodMatchRules.note(true, "pc", "phone", "phone", "PC", true).contains("Prevent auto mood scans"))
        assertFalse("the main device isn't told about a freeze that doesn't stop it sending", MoodMatchRules.note(true, "pc", "pc", "PC", "phone", true).contains("Prevent"))
    }

    @Test fun whatIsSaidAfterwards() {
        assertEquals("Moods from the PC given to 1,200 songs, written into 1,150 files", MoodMatchRules.takenText(1200, 1150, "PC"))
        assertEquals("Moods from the phone given to 1 song", MoodMatchRules.takenText(1, 0, "phone"))
    }
}
