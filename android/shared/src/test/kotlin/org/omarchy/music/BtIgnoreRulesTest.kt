package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BtIgnoreRulesTest {
    private val car = setOf("Chevy")

    @Test fun listsRoundTrip() {
        assertEquals(listOf("a", "b"), BtIgnoreRules.parse(" a\n\nb\na "))
        assertEquals("a\nb", BtIgnoreRules.join(listOf("a", " b", "a", "")))
    }

    @Test fun seenKeepsNewestUpToTheLimit() {
        assertEquals(listOf("a", "b"), BtIgnoreRules.remember(listOf("a"), listOf("b", "a", "b")))
        val full = BtIgnoreRules.remember((1..30).map { "$it" }, listOf("new"))
        assertEquals(30, full.size)
        assertEquals("new", full.last())
        assertEquals("2", full.first())
    }

    @Test fun keepsTheHeadphonesWhenTheCarComes() {
        assertEquals("XM6", BtIgnoreRules.keepOn(true, car, listOf("Chevy", "XM6"), "XM6", listOf("XM6", "Chevy")))
    }

    @Test fun leavesTheSoundAlone() {
        assertNull(BtIgnoreRules.keepOn(false, car, listOf("Chevy"), "XM6", listOf("XM6")))          // ignoring off
        assertNull(BtIgnoreRules.keepOn(true, car, listOf("XM6"), "XM6", listOf("XM6")))             // no ignored device
        assertNull(BtIgnoreRules.keepOn(true, car, listOf("Chevy"), "XM6", listOf("Chevy")))         // headphones gone
        assertNull(BtIgnoreRules.keepOn(true, car, listOf("Chevy"), null, listOf("Chevy")))          // nothing before
        assertNull(BtIgnoreRules.keepOn(true, car, listOf("Chevy"), "Chevy", listOf("Chevy")))       // before was ignored
        assertNull(BtIgnoreRules.keepOn(true, emptySet(), listOf("Chevy"), "XM6", listOf("XM6")))    // none picked
    }

    @Test fun previousStaysWhileIgnoring() {
        assertEquals("XM6", BtIgnoreRules.previousAfter(true, car, listOf("XM6"), "XM6", null))
        assertEquals("XM6", BtIgnoreRules.previousAfter(true, car, listOf("Chevy", "XM6"), "Chevy", "XM6"))
        assertNull(BtIgnoreRules.previousAfter(false, car, listOf("Chevy"), "Chevy", "XM6"))
        assertNull(BtIgnoreRules.previousAfter(true, car, emptyList(), null, "XM6"))
    }
}
