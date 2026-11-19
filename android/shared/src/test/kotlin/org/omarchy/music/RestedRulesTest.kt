package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unplayed and neglected: which songs a shuffle of them holds, in what order, and what the tab says of a song. */
class RestedRulesTest {
    private val day = 86_400L
    private val now = 2_000_000_000L

    @Test fun aSongHeardInItsRoundIsLeftOutUntilTheOthersWereHeardToo() {
        // four played yesterday, four a week ago; the first round began three days ago
        val last = List(4) { now - day } + List(4) { now - 7 * day }
        val floor = now - RestedRules.RECENT
        val got = RestedRules.order(8, { last[it] }, { RestedRules.heard(last[it], 0, floor) }, kotlin.random.Random(1))
        assertFalse(got.restarted)
        assertEquals("only the four from a week ago", setOf(4, 5, 6, 7), got.order.toSet())
        assertEquals(4, got.order.size)
    }

    @Test fun whenEveryoneWasHeardTheRoundBeginsAgainWithAllOfThem() {
        val last = List(6) { now - it * 60L }
        val got = RestedRules.order(6, { last[it] }, { true }, kotlin.random.Random(2))
        assertTrue(got.restarted)
        assertEquals((0..5).toSet(), got.order.toSet())
        // …and once its round has begun again (released: now), a song heard before that is back in
        assertFalse(RestedRules.heard(now - 60, released = now, floor = now - RestedRules.RECENT))
        assertTrue(RestedRules.heard(now + 60, released = now, floor = now - RestedRules.RECENT))
        assertFalse("never played is never heard", RestedRules.heard(0, 0, 0))
    }

    @Test fun neverPlayedComeFirstThenTheOnesHeardLongestAgoLoosely() {
        // 200 songs: 50 never played, then 150 heard from 150 days ago up to yesterday
        val last = List(50) { 0L } + List(150) { now - (150 - it) * day }
        for (seed in 1..20) {
            val o = RestedRules.order(200, { last[it] }, { false }, kotlin.random.Random(seed)).order
            assertEquals(200, o.toSet().size)
            assertTrue("the first twenty are nearly all never played", o.take(20).count { it < 50 } >= 15)
            assertTrue("the ones heard most lately come at the end", o.take(100).none { it >= 190 })
        }
        // not the same order every time
        val a = RestedRules.order(200, { last[it] }, { false }, kotlin.random.Random(1)).order
        val b = RestedRules.order(200, { last[it] }, { false }, kotlin.random.Random(2)).order
        assertTrue(a != b)
        assertEquals(emptyList<Int>(), RestedRules.order(0, { 0L }, { false }, kotlin.random.Random(1)).order)
    }

    @Test fun whatTheTabSaysOfASong() {
        assertTrue(RestedRules.unplayed(0, 0)); assertFalse(RestedRules.unplayed(3, 0)); assertFalse(RestedRules.unplayed(0, now - day))
        assertTrue(RestedRules.neglected(now - 200 * day, now, 183)); assertFalse(RestedRules.neglected(now - 100 * day, now, 183))
        assertFalse("played, but when isn't known", RestedRules.neglected(0, now, 91))
        assertEquals("Never played", RestedRules.line(0, 0, now))
        assertEquals("Last played 7 months ago · 23 plays", RestedRules.line(23, now - 212 * day, now))
        assertEquals("Last played 2 years ago · 1 play", RestedRules.line(1, now - 800 * day, now))
        assertEquals("Last played 5 weeks ago", RestedRules.line(0, now - 36 * day, now))
        assertEquals("Last played 1 day ago · 2 plays", RestedRules.line(2, now - 3600, now))
        assertEquals("12 plays · when isn't known", RestedRules.line(12, 0, now))
    }

    @Test fun theWayToShuffleKeptOrTheOldSwitch() {
        assertEquals(RestedRules.RESTED, RestedRules.mode("rested", smartSwitch = true))
        assertEquals(RestedRules.SMART, RestedRules.mode("", smartSwitch = true))
        assertEquals(RestedRules.RANDOM, RestedRules.mode("", smartSwitch = false))
        assertEquals(RestedRules.RANDOM, RestedRules.mode("something else", smartSwitch = false))
    }
}
