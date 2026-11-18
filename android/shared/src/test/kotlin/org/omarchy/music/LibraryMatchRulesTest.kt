package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The songs your other device has that this one hasn't, found by comparing the two libraries. */
class LibraryMatchRulesTest {
    private fun t(title: String, artist: String = "HOAX") = title to artist
    private fun missing(theirs: List<Pair<String, String>>, mine: List<Pair<String, String>>, leftOut: Set<String> = emptySet()) =
        LibraryMatchRules.missing(theirs, mine, leftOut, { it.first }, { it.second }).map { it.first }

    @Test fun whatTheOtherHasAndThisOneHasnt() {
        val pc = listOf(t("drew"), t("soju"), t("Moon Moon Baby"), t("TV"))
        val phone = listOf(t("Drew"), t("TV"))
        assertEquals(listOf("soju", "Moon Moon Baby"), missing(pc, phone))
        assertTrue("the other way round: the phone has nothing the PC lacks", missing(phone, pc).isEmpty())
    }

    @Test fun theSameSongHoweverEachDeviceTagsIt() {
        val phone = listOf(t("Just Don't Give a F**k", "Eminem"), t("Bad Bitch (feat. Someone)", "X"), t("TV (Single Version)"), t("Drew", "HOAX, Someone"))
        val pc = listOf(t("Just Don't Give A Fuck", "Eminem"), t("Bad B*tch", "X"), t("TV"), t("drew", "Hoax"))
        assertTrue(missing(pc, phone).isEmpty())
        assertTrue(missing(phone, pc).isEmpty())
    }

    @Test fun aLiveVersionIsAnotherSong() {
        assertEquals(listOf("Drew (Live)"), missing(listOf(t("Drew"), t("Drew (Live)")), listOf(t("Drew"))))
    }

    @Test fun eachOnceAndNotTheOnesYouLeftOut() {
        val pc = listOf(t("soju"), t("Soju"), t("Clouds"), t(""), t("Sway"))
        assertEquals(listOf("soju", "Clouds", "Sway"), missing(pc, emptyList()))
        assertEquals("left out: not offered again", listOf("soju", "Sway"), missing(pc, emptyList(), setOf(LibraryMatchRules.key("clouds", "hoax"))))
    }

    @Test fun whatItSays() {
        assertEquals("Nothing missing: this device has every song the PC has", LibraryMatchRules.heading(0, "the PC"))
        assertEquals("42 songs on the PC that this device doesn't have", LibraryMatchRules.heading(42, "the PC"))
        assertEquals("1 song on the PC that this device doesn't have", LibraryMatchRules.heading(1, "the PC"))
        assertEquals("", LibraryMatchRules.afterSync(0, "the PC"))
        assertEquals("42 songs on the PC aren't on this device: ☰ › Other device's songs", LibraryMatchRules.afterSync(42, "the PC"))
        assertEquals("1 song on the PC isn't on this device: ☰ › Other device's songs", LibraryMatchRules.afterSync(1, "the PC"))
    }
}
