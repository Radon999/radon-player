package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A look for new releases that couldn't reach everyone, and the daily look: neither loses what was found before. */
class NewReleaseLookTest {
    private val R = NewReleaseRules

    @Test fun aLookThatReachedNobodyIsNoLook() {
        assertTrue("offline: every artist failed", R.lookFailed(40, 40))
        assertTrue("half of them failed", R.lookFailed(10, 5))
        assertFalse("two of forty failed: a look", R.lookFailed(40, 2))
        assertFalse(R.lookFailed(40, 0)); assertFalse("nobody followed", R.lookFailed(0, 0))
    }

    @Test fun whatStaysOfTheListFromBefore() {
        val before = listOf(Triple("a1", "lauv", "2026-11-25"), Triple("a2", "lauv", "2026-11-05"),
            Triple("b1", "sody", "2026-11-26"), Triple("c1", "muse", "2026-11-02"), Triple("old", "muse", "2026-09-01"))
        // a month's look where Sody couldn't be asked: hers stay, the rest is as found now
        assertEquals(setOf("b1"), R.keptFromBefore(before, setOf("a1"), setOf("sody"), "2026-10-28", null))
        // the daily look (a week back) on a month's list: the month's older ones stay, older than the month go
        assertEquals(setOf("a2", "c1"), R.keptFromBefore(before, setOf("a1", "b1"), emptySet(), "2026-11-21", "2026-10-28"))
        // …and with Sody not asked, her recent one stays too
        assertEquals(setOf("a2", "c1", "b1"), R.keptFromBefore(before, setOf("a1"), setOf("sody"), "2026-11-21", "2026-10-28"))
    }
}
