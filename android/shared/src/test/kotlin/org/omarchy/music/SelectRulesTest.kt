package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Picking one of two identical songs in the queue picks that one only. */
class SelectRulesTest {
    @Test fun theSameSongTwiceInTheQueueIsTwoRows() {
        assertNotEquals(SelectRules.key("a", "queue", 2), SelectRules.key("a", "queue", 5))
        assertEquals(SelectRules.key("a", "queue", 2), SelectRules.key("a", "queue", 2))
        assertEquals(2, SelectRules.queueIndex(SelectRules.key("a", "queue", 2)))
    }

    @Test fun inAListASongIsTheSong() {
        assertEquals("a", SelectRules.key("a", "playlist", 3))
        assertEquals("a", SelectRules.key("a", "", -1))
        assertEquals("a", SelectRules.key("a", "queue", -1))
        assertNull(SelectRules.queueIndex("a"))
    }

    @Test fun takingOutOfTheQueueTakesOnlyTheRowsPicked() {
        val queue = listOf("a", "b", "a", "c", "a")
        assertEquals(listOf(2), SelectRules.queuePlaces(listOf(SelectRules.key("a", "queue", 2)), queue))
        assertEquals(listOf(0, 3), SelectRules.queuePlaces(listOf(SelectRules.key("c", "queue", 3), SelectRules.key("a", "queue", 0)), queue))
        assertEquals("a song picked in a list: every place it waits at", listOf(0, 2, 4), SelectRules.queuePlaces(listOf("a"), queue))
        assertEquals("a place no longer in the queue is passed over", emptyList<Int>(), SelectRules.queuePlaces(listOf(SelectRules.key("a", "queue", 9)), queue))
    }
}
