package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The queue's rules: what comes after a song, what Previous does, how your other queues are kept. */
class QueueRulesTest {
    private fun song(n: Int) = Track("s$n", "file", "Song $n", "Band")
    private fun q(id: String, name: String, songs: Int, at: Int) = SavedQueue(id, name, (1..songs).map { song(it) }, at)

    @Test fun whatComesAfterASong() {
        assertEquals(QueueRules.After.NEXT_SONG, QueueRules.after(0, 3, djOn = false, repeat = "off", anotherQueue = false))
        assertEquals(QueueRules.After.NEXT_SONG, QueueRules.after(1, 3, djOn = true, repeat = "all", anotherQueue = true))   // whatever else is on
        // The last song
        assertEquals(QueueRules.After.END, QueueRules.after(2, 3, djOn = false, repeat = "off", anotherQueue = false))
        assertEquals(QueueRules.After.ROUND_AGAIN, QueueRules.after(2, 3, djOn = false, repeat = "all", anotherQueue = true))   // Repeat all before another queue
        assertEquals(QueueRules.After.NEXT_QUEUE, QueueRules.after(2, 3, djOn = false, repeat = "off", anotherQueue = true))
        assertEquals(QueueRules.After.END, QueueRules.after(2, 3, djOn = false, repeat = "one", anotherQueue = true))   // Next on Repeat one, at the end
        // With the DJ: always its next set
        assertEquals(QueueRules.After.DJ_SET, QueueRules.after(2, 3, djOn = true, repeat = "all", anotherQueue = true))
        assertEquals(QueueRules.After.DJ_SET, QueueRules.after(-1, 0, djOn = true, repeat = "off", anotherQueue = false))
        // Nothing queued
        assertEquals(QueueRules.After.END, QueueRules.after(-1, 0, djOn = false, repeat = "all", anotherQueue = false))
    }

    @Test fun previousStartsTheSongAgainUnlessItsOnlyJustBegun() {
        assertTrue(QueueRules.previousRestarts(10.0, 3))
        assertTrue(QueueRules.previousRestarts(125.0, 3))
        assertFalse(QueueRules.previousRestarts(9.9, 3))
        assertTrue("the first song has none before it", QueueRules.previousRestarts(2.0, 0))
    }

    @Test fun theNextOfYourOtherQueues() {
        val done = q("a", "Done", 3, 2); val mid = q("b", "Mid", 3, 0); val later = q("c", "Later", 5, 1)
        assertEquals("b", QueueRules.next(listOf(done, mid, later), "x")?.id)   // the first with songs still to play
        assertEquals("c", QueueRules.next(listOf(done, mid, later), "b")?.id)   // never the one you're in
        assertNull(QueueRules.next(listOf(done), "x"))
        assertNull(QueueRules.next(emptyList(), "x"))
    }

    @Test fun theQueueYouLeaveIsPutFirstAndOnlySoManyAreKept() {
        val a = q("a", "A", 3, 1); val b = q("b", "B", 3, 0); val c = q("c", "C", 3, 0)
        assertEquals(listOf("c", "a", "b"), QueueRules.putAside(listOf(a, b), c, 5).map { it.id })
        // An older copy of itself (the same id, or the same name) is replaced
        assertEquals(listOf("a", "b"), QueueRules.putAside(listOf(a, b), a.copy(index = 2), 5).map { it.id })
        assertEquals(2, QueueRules.putAside(listOf(a, b), a.copy(index = 2), 5)[0].index)
        assertEquals(listOf("z", "b"), QueueRules.putAside(listOf(a, b), q("z", "A", 2, 0), 5).map { it.id })
        assertEquals(listOf("c", "a"), QueueRules.putAside(listOf(a, b), c, 2).map { it.id })
    }

    @Test fun goingBackToAQueuePutsTheOneYouLeaveInItsPlace() {
        val a = q("a", "A", 3, 1); val b = q("b", "B", 3, 0); val c = q("c", "C", 3, 0); val now = q("n", "Now", 3, 2)
        assertEquals(listOf("a", "n", "c"), QueueRules.swap(listOf(a, b, c), now, "b").map { it.id })
        assertEquals(listOf("a", "b", "c"), QueueRules.swap(listOf(a, b, c), now, "zzz").map { it.id })   // (no such queue: nothing changes)
    }
}
