package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The DJ's no-repeat picking: kinds of set, facts and lines don't come back too soon. */
class NoRepeatTest {
    @Test fun windowLeavesAChoice() {
        assertEquals(15, NoRepeat.window(15, 30, 2))
        assertEquals(9, NoRepeat.window(15, 11, 2))
        assertEquals(0, NoRepeat.window(15, 1, 2))
        assertEquals(4, NoRepeat.window(15, 5, 1))
    }

    @Test fun orderPutsRecentLastStalestFirst() {
        val kinds = listOf("a", "b", "c", "d", "e")
        val got = NoRepeat.order(kinds, listOf("a", "b", "c"), 3, Random(1))
        assertEquals(5, got.size)
        assertEquals(setOf("d", "e"), got.take(2).toSet())
        assertEquals(listOf("a", "b", "c"), got.drop(2))   // a was picked longest ago
    }

    @Test fun neverRepeatsWithinTheWindow() {
        val kinds = listOf("repeat", "fresh", "hour", "throwback", "discover", "deep", "rediscover", "favorites", "genre", "like", "mood", "fresh", "discover")
        val usable = kinds.distinct().size
        val w = NoRepeat.window(15, usable, 2)
        val rng = Random(42)
        var history = emptyList<String>()
        repeat(500) {
            val k = NoRepeat.order(kinds, history, w, rng).first()
            assertFalse("$k came back within $w", k in history.takeLast(w))
            history = NoRepeat.remember(history, k)
        }
        assertTrue(history.size <= 40)
    }

    @Test fun skippedKindsDontCount() {
        // "b" can't build: the next one in the order is tried, and b isn't remembered.
        val rng = Random(3)
        var history = emptyList<String>()
        repeat(200) {
            val k = NoRepeat.order(listOf("a", "b", "c", "d"), history, NoRepeat.window(15, 3, 2), rng).first { it != "b" }
            assertFalse(k in history.takeLast(1))
            history = NoRepeat.remember(history, k)
        }
        assertFalse("b" in history)
    }

    @Test fun pickAvoidsRecentFacts() {
        val facts = (1..20).map { "f$it" }
        val rng = Random(9)
        var history = emptyList<String>()
        repeat(300) {
            val f = NoRepeat.pick(facts, history, 15, rng)!!
            assertFalse(f in history.takeLast(15))
            history = NoRepeat.remember(history, f)
        }
        assertEquals("only", NoRepeat.pick(listOf("only"), listOf("only"), 15))
        assertEquals(null, NoRepeat.pick(emptyList(), emptyList(), 15))
        // two facts: never the same one twice in a row
        var last = "x"
        repeat(50) { val f = NoRepeat.pick(listOf("x", "y"), listOf(last), 15, rng)!!; assertNotEquals(last, f); last = f }
    }

    @Test fun bagUsesEveryLineBeforeAgain() {
        val rng = Random(5)
        var bag = emptyList<Int>()
        val seen = ArrayList<Int>()
        repeat(50) { bag = NoRepeat.bag(bag, 5, rng); seen += bag.last() }
        for (round in seen.chunked(5)) assertEquals(setOf(0, 1, 2, 3, 4), round.toSet())
        for (i in 1 until seen.size) assertNotEquals(seen[i - 1], seen[i])
        assertEquals(listOf(0), NoRepeat.bag(listOf(0), 1, rng))
    }

    @Test fun historyKeeps() {
        val h = listOf("hours:song|artist", "top-artist-month")
        assertEquals(h, NoRepeat.decode(NoRepeat.encode(h)))
        assertEquals(emptyList<String>(), NoRepeat.decode(""))
        assertEquals(listOf("b", "c"), NoRepeat.remember(listOf("a", "b"), "c", keep = 2))
    }
}
