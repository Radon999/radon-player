package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weekly mix: what you play now, with songs you haven't heard in a long while among them. */
class WeeklyMixRulesTest {
    private val now = 1_800_000_000L
    private val day = 86_400L
    private val recent = (1..30).map { "r$it" } + "r1"                       // played lately (one of them twice)
    private val mine = (1..30).map { "r$it" } + (1..40).map { "old$it" } + (1..5).map { "new$it" }
    private fun last(s: String): Long? = when { s.startsWith("r") -> now - day; s.startsWith("old") -> now - 200 * day; else -> null }
    private fun mix(week: Long) = WeeklyMixRules.pick(recent, mine, ::last, now, { it }, week)

    @Test fun twentyRecentAndTenLongUnheardTwoToOne() {
        val m = mix(2960)
        assertEquals(30, m.size)
        assertEquals("each song once", 30, m.toSet().size)
        assertEquals((1..20).map { "r$it" }, m.filter { it.startsWith("r") })
        assertEquals(10, m.count { !it.startsWith("r") })
        assertTrue("two you know, then one to rediscover", m[0] == "r1" && m[1] == "r2" && !m[2].startsWith("r") && m[3] == "r3")
        assertTrue("never played counts as long unheard", mine.filter { it.startsWith("new") }.all { last(it) == null })
    }

    @Test fun theSameThisWeekAndDifferentNextWeek() {
        assertEquals(mix(2960), mix(2960))
        assertNotEquals(mix(2960).filter { !it.startsWith("r") }, mix(2961).filter { !it.startsWith("r") })
    }

    @Test fun withLittleToGoOn() {
        assertTrue(WeeklyMixRules.pick(emptyList<String>(), emptyList(), { null }, now, { it }, 1).isEmpty())
        assertEquals("only songs played lately: just those", listOf("a", "b"), WeeklyMixRules.pick(listOf("a", "b"), listOf("a", "b"), { now }, now, { it }, 1))
        assertEquals("Weekly mix made: 30 songs, 10 of them ones you haven't heard in a while", WeeklyMixRules.said(30, 10))
        assertEquals("Nothing to make a mix from yet: play some music first", WeeklyMixRules.said(0, 0))
    }
}
