package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Search history: ten a place, newest first. */
class SearchHistoryRulesTest {
    private fun of(vararg qs: String) = qs.fold(emptyList<String>()) { l, q -> SearchHistoryRules.add(l, q) }

    @Test fun newestFirstAndTenAtMost() {
        val l = of(*(1..12).map { "song $it" }.toTypedArray())
        assertEquals(10, l.size)
        assertEquals("song 12", l.first()); assertEquals("song 3", l.last())
    }

    @Test fun searchingAgainMovesItToTheTopOnce() {
        assertEquals(listOf("Hoax", "Lauv"), of("hoax", "Lauv", "Hoax"))
    }

    @Test fun theStepsOfOneSearchTypedArentEachKept() {
        assertEquals(listOf("taylor swift"), of("tay", "taylor", "taylor swift"))
        assertEquals("typed back and a new one: both kept", listOf("taylor", "taylor swift"), of("taylor swift", "taylor"))
        assertEquals("one letter or blank: not kept", listOf("hoax"), of("hoax", "a", "   "))
    }
}
