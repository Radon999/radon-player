package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Repeats: a song let play again with Repeat one, counted. */
class RepeatLoopRulesTest {
    private val a = Track("1", title = "1997", artist = "Saint Motel")
    private val b = Track("2", title = "Blood", artist = "K.Flay")

    @Test fun eachTimeASongPlaysAgainIsOneRepeatAndTheLongestRunIsKept() {
        var s = RepeatLoopRules.Saved()
        repeat(3) { s = RepeatLoopRules.add(s, a, "2026-11-17", 100) }
        s = RepeatLoopRules.broke(s)
        s = RepeatLoopRules.add(s, a, "2026-11-18", 200)
        s = RepeatLoopRules.add(s, b, "2026-11-18", 300)
        val top = RepeatLoopRules.top(s)
        assertEquals(listOf("1997" to 4, "Blood" to 1), top.map { it.first.track.title to it.second })
        assertEquals("three in a row, then one more another day", 3, top[0].first.best)
        assertEquals(5, RepeatLoopRules.total(s))
        assertEquals("only since a day", listOf("Blood" to 1, "1997" to 1), RepeatLoopRules.top(s, "2026-11-18").map { it.first.track.title to it.second })
    }

    @Test fun aSongsOtherCopyIsTheSameSong() {
        var s = RepeatLoopRules.add(RepeatLoopRules.Saved(), b, "2026-11-18", 1)
        s = RepeatLoopRules.add(s, Track("9", source = "file", title = "Blood", artist = "KFlay"), "2026-11-18", 2)
        assertEquals(1, s.songs.size); assertEquals(2, RepeatLoopRules.total(s))
    }

    @Test fun inWords() {
        assertEquals("#1 · 50 · 12 in a row", RepeatLoopRules.line(1, 50, 12))
        assertEquals("#2 · 1", RepeatLoopRules.line(2, 1, 1))
        assertEquals("2 songs let play again 5 times with Repeat one", RepeatLoopRules.summary(2, 5))
        assertEquals("1,204 plays", RepeatLoopRules.plays(1204)); assertEquals("", RepeatLoopRules.plays(0))
    }
}
