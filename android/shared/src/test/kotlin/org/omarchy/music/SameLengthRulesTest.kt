package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class SameLengthRulesTest {
    @Test fun theLengthMostPlaysRanToIsTheSongs() {
        assertEquals(120.0, SameLengthRules.usual(List(62) { 120_000L } + listOf(119_391L, 61_120L, 100_418L)), 0.001)
        assertEquals("nothing to go by", 0.0, SameLengthRules.usual(emptyList()), 0.0)
        assertEquals("plays of a few seconds say nothing", 0.0, SameLengthRules.usual(listOf(4_000L, 9_000L)), 0.0)
        assertEquals("as common: the longer one", 200.0, SameLengthRules.usual(listOf(90_000L, 200_000L)), 0.001)
    }

    @Test fun theOnlySongOfThatLengthIsIt() {
        assertEquals(0, SameLengthRules.pick(120.0, listOf(120.12)))
        assertEquals(1, SameLengthRules.pick(215.0, listOf(158.0, 215.35)))
        assertEquals("another song of that name", -1, SameLengthRules.pick(120.0, listOf(187.0)))
        assertEquals("two of that length: neither", -1, SameLengthRules.pick(120.0, listOf(120.1, 120.9)))
        assertEquals("no length known", -1, SameLengthRules.pick(0.0, listOf(120.0)))
        assertEquals("your file's length unknown", -1, SameLengthRules.pick(120.0, listOf(0.0)))
        assertEquals("too short to tell by", -1, SameLengthRules.pick(20.0, listOf(20.0)))
    }
}
