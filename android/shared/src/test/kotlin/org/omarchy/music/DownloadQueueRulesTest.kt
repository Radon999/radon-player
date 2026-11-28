package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQueueRulesTest {
    private val R = DownloadQueueRules

    @Test fun theNewestFailuresComeFirstEachSongOnce() {
        val before = listOf("a" to "no connection", "b" to "timed out")
        val now = R.failedNow(before, listOf("b" to "Tidal said no", "c" to R.NOT_FOUND), { false }) { it }
        assertEquals(listOf("b" to "Tidal said no", "c" to R.NOT_FOUND, "a" to "no connection"), now)
    }

    @Test fun aSongThatIsHereAfterAllLeavesTheList() {
        val now = R.failedNow(listOf("a" to "x", "b" to "y"), emptyList(), { it == "a" }) { it }
        assertEquals(listOf("b" to "y"), now)
    }

    @Test fun onlyTheNewestAreKept() {
        val many = (1..400).map { "s$it" to "x" }
        assertEquals(R.KEEP, R.failedNow(many, emptyList(), { false }) { it }.size)
    }

    @Test fun theReasonIsShortAndNeverEmpty() {
        assertEquals("it couldn't be downloaded", R.why(null))
        assertEquals("a b", R.why(" a \n b "))
        assertEquals(120, R.why("x".repeat(500)).length)
        assertEquals("Carrying on with 1 download from last time", R.carryOn(1))
        assertEquals("Carrying on with 12 downloads from last time", R.carryOn(12))
    }
}
