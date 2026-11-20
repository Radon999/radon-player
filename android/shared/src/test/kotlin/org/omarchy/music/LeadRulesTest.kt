package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class LeadRulesTest {
    private val set = listOf("Self-Serving Human Being", "Next Semester", "Cloud", "OTT")

    @Test fun theSongTheLineNamesPlaysFirst() {
        val line = "You hit repeat on these more than anything else. “Next Semester” by twenty one pilots leads, with 164 repeats."
        assertEquals(listOf("Next Semester", "Self-Serving Human Being", "Cloud", "OTT"), LeadRules.leadFirst(set, line) { it })
    }

    @Test fun ofSeveralNamedTheFirstNamedLeads() {
        assertEquals("Cloud", LeadRules.leadFirst(set, "Once was never enough for these. “Cloud”, “OTT” and “Next Semester”.") { it }[0])
    }

    @Test fun aLineThatNamesNoSongLeavesTheOrder() {
        assertEquals(set, LeadRules.leadFirst(set, "Staying with alternative for the next few.") { it })
        assertEquals(set, LeadRules.leadFirst(set, "“Self-Serving Human Being” gets us going.") { it })
        assertEquals("a song that isn't in the set", set, LeadRules.leadFirst(set, "After “Walking On Ice”, more of the same.") { it })
    }
}
