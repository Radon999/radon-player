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

    private val bySong = listOf("Hozier" to "Too Sweet", "Noah Kahan" to "Busyhead", "Mt. Joy" to "Silver Lining", "Noah Kahan" to "Stick Season")

    @Test fun aLineThatNamesArtistsLeadsWithTheFirstArtistNamed() {
        val out = LeadRules.leadFirst(bySong, "From your 2023: Noah Kahan, Mt. Joy and Hozier.", { it.first }) { it.second }
        assertEquals("Busyhead", out[0].second)   // the first of Noah Kahan's in the set
        assertEquals(listOf("Too Sweet", "Silver Lining", "Stick Season"), out.drop(1).map { it.second })   // the rest as they were
    }

    @Test fun aTitleNamedOutranksAnArtistNamedBeforeIt() {
        assertEquals("Silver Lining", LeadRules.leadFirst(bySong, "Hozier fans, “Silver Lining” leads.", { it.first }) { it.second }[0].second)
    }

    @Test fun anArtistOnlyCountsAsAWholeName() {
        val set = listOf("Joy" to "One", "Mt. Joy" to "Two")
        assertEquals("Two", LeadRules.leadFirst(set, "Up next, Mt. Joy.", { it.first }) { it.second }[0].second)   // not "Joy"
        assertEquals(set, LeadRules.leadFirst(set, "Enjoying this?", { it.first }) { it.second })   // "Joy" inside a word
    }
}
