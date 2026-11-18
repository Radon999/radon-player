package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** What Listen later says as songs go on it and come off it. */
class ListenLaterRulesTest {
    @Test fun whatAddingSays() {
        assertEquals("Added to Listen later", ListenLaterRules.addedText(1, 1))
        assertEquals("Already on Listen later", ListenLaterRules.addedText(1, 0))
        assertEquals("12 songs added to Listen later", ListenLaterRules.addedText(12, 12))
        assertEquals("9 songs added to Listen later (3 were on it)", ListenLaterRules.addedText(12, 9))
        assertEquals("1 song added to Listen later (1 was on it)", ListenLaterRules.addedText(2, 1))
        assertEquals("All 5 are on Listen later already", ListenLaterRules.addedText(5, 0))
        assertEquals("", ListenLaterRules.addedText(0, 0))
    }

    @Test fun whatTakingOffSays() {
        assertEquals("Taken off Listen later", ListenLaterRules.removedText(1))
        assertEquals("4 songs taken off Listen later", ListenLaterRules.removedText(4))
    }
}
