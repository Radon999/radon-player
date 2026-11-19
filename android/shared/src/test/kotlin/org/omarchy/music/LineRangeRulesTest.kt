package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** A lyrics card's lines ticked two taps at a time: the first and the last, with everything between. */
class LineRangeRulesTest {
    private val R = LineRangeRules
    private fun taps(vararg at: Int, max: Int = 25) = at.fold(LineRangeRules.State()) { s, i -> R.tap(s, i, max) }

    @Test fun theFirstLineIsOneAndTheSecondTakesEverythingBetween() {
        val one = taps(4)
        assertEquals(listOf(4), one.picked); assertEquals("1", R.mark(one, 4)); assertEquals("", R.mark(one, 5))
        val two = taps(4, 9)
        assertEquals(listOf(4, 5, 6, 7, 8, 9), two.picked)
        assertEquals("1", R.mark(two, 4)); assertEquals("2", R.mark(two, 9)); assertEquals("", R.mark(two, 6))
    }

    @Test fun theSecondLineCanBeAboveTheFirst() {
        val s = taps(9, 4)
        assertEquals(listOf(4, 5, 6, 7, 8, 9), s.picked)
        assertEquals("1", R.mark(s, 9)); assertEquals("2", R.mark(s, 4))
    }

    @Test fun aThirdLineStartsAnotherRunAndKeepsTheFirst() {
        val s = taps(2, 4, 10)
        assertEquals(listOf(2, 3, 4, 10), s.picked); assertEquals("1", R.mark(s, 10)); assertEquals("", R.mark(s, 2))
        assertEquals(listOf(2, 3, 4, 10, 11, 12), taps(2, 4, 10, 12).picked)
    }

    @Test fun aTickedLineIsUntickedOnItsOwn() {
        val middle = taps(2, 6, 4)
        assertEquals(listOf(2, 3, 5, 6), middle.picked); assertEquals("1", R.mark(middle, 2)); assertEquals("2", R.mark(middle, 6))
        // The 2 unticked: the 1 waits for a new 2
        val end = taps(2, 6, 6)
        assertEquals(listOf(2, 3, 4, 5), end.picked); assertEquals("1", R.mark(end, 2))
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8), R.tap(end, 8, 25).picked)
        // The 1 unticked: the next line ticked is a new 1, nothing is filled
        val start = taps(2, 6, 2)
        assertEquals(listOf(3, 4, 5, 6), start.picked); assertEquals("", R.mark(start, 6))
        assertEquals(listOf(3, 4, 5, 6, 12), R.tap(start, 12, 25).picked)
    }

    @Test fun neverMoreThanThereIsRoomFor() {
        val s = taps(0, 40, max = 25)
        assertEquals((0..24).toList(), s.picked); assertEquals("2", R.mark(s, 24))
        assertEquals(s, R.tap(s, 30, 25))
        val up = taps(40, 0, max = 5)
        assertEquals(listOf(36, 37, 38, 39, 40), up.picked); assertEquals("2", R.mark(up, 36))
    }
}
