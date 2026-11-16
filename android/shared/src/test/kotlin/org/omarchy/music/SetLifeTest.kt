package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A DJ set's life: it only moves forward, can be dropped until it's announced, and is found again by the number
 *  its songs carry in the queue. */
class SetLifeTest {
    @Test fun aSetGoesFromPlannedToAnnounced() {
        val book = SetLife.Book()
        book.planned(1)
        assertEquals(SetState.PLANNED, book.of(1))
        assertTrue(book.move(1, SetState.SCRIPTED))
        assertTrue(book.move(1, SetState.VOICED))
        assertTrue(book.queuedAs(1, n = 7))
        assertTrue(book.announced(7))
        assertEquals(SetState.ANNOUNCED, book.of(1))
    }

    @Test fun theVoiceIsNotNeeded() {
        val book = SetLife.Book()
        book.planned(1); book.move(1, SetState.SCRIPTED)
        assertTrue("queued without a voice made ahead", book.queuedAs(1, 1))
    }

    @Test fun aSetNeverGoesBack() {
        val book = SetLife.Book()
        book.planned(1); book.move(1, SetState.SCRIPTED); book.queuedAs(1, 1)
        assertFalse(book.move(1, SetState.SCRIPTED))
        assertFalse(book.move(1, SetState.VOICED))
        assertEquals(SetState.QUEUED, book.of(1))
        assertFalse("not queued before its line is written", SetLife.allowed(SetState.PLANNED, SetState.QUEUED))
    }

    @Test fun aSetCanBeDroppedUntilItIsAnnounced() {
        for (s in listOf(SetState.PLANNED, SetState.SCRIPTED, SetState.VOICED, SetState.QUEUED)) assertTrue("$s", SetLife.allowed(s, SetState.DROPPED))
        assertFalse(SetLife.allowed(SetState.ANNOUNCED, SetState.DROPPED))
        for (s in SetState.values()) assertFalse("dropped is the end", SetLife.allowed(SetState.DROPPED, s))
    }

    @Test fun aDroppedSetIsNeverAnnounced() {
        val book = SetLife.Book()
        book.planned(1); book.move(1, SetState.SCRIPTED); book.move(1, SetState.DROPPED)
        assertFalse(book.queuedAs(1, 3))
        assertFalse(book.announced(3))
        assertEquals(SetState.DROPPED, book.of(1))
    }

    @Test fun anUnknownSetIsLeftAlone() {
        val book = SetLife.Book()
        assertFalse(book.move(9, SetState.SCRIPTED))
        assertFalse(book.announced(9))
        assertNull(book.of(9))
    }

    @Test fun onlyTheLastSetsAreKept() {
        val book = SetLife.Book(keep = 3)
        for (id in 1L..5L) book.planned(id)
        assertNull(book.of(1)); assertNull(book.of(2))
        assertEquals(SetState.PLANNED, book.of(3))
        assertEquals(mapOf(SetState.PLANNED to 3), book.counts())
    }
}
