package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Undo for the queue: the queue from before a change comes back, and the song playing goes on. */
class QueueUndoRulesTest {
    @Test fun theSongPlayingKeepsItsPlaceInTheQueueThatComesBack() {
        val before = listOf("a", "b", "c", "d")
        assertEquals("shuffled, then undone while b still plays", 1, QueueUndoRules.indexIn(before, 1, "b"))
        assertEquals("two songs on since the change: d plays, at its place in the old order", 3, QueueUndoRules.indexIn(before, 1, "d"))
    }

    @Test fun aSongInTheQueueTwiceIsTheOneNearestWhereItWas() {
        val before = listOf("a", "b", "c", "a", "d")
        assertEquals(3, QueueUndoRules.indexIn(before, 3, "a"))
        assertEquals(0, QueueUndoRules.indexIn(before, 0, "a"))
    }

    @Test fun nothingToPutBackWhenSomethingElseWasStarted() {
        assertNull(QueueUndoRules.indexIn(listOf("a", "b"), 0, "z"))
        assertNull(QueueUndoRules.indexIn(listOf("a", "b"), 0, null))
    }

    @Test fun theLastTenChangesAreKept() {
        var stack = emptyList<Int>()
        for (i in 1..14) stack = QueueUndoRules.kept(stack, i)
        assertEquals((5..14).toList(), stack)
    }

    @Test fun whatTheButtonSays() {
        assertEquals("Undo: queue cleared", QueueUndoRules.label("clear"))
        assertEquals("Undo: shuffled", QueueUndoRules.label("shuffle"))
        assertEquals("Undo: 3 songs removed", QueueUndoRules.label("remove", 3))
        assertEquals("Undo: song removed", QueueUndoRules.label("remove", 1))
        assertEquals("Undo: 12 songs added", QueueUndoRules.label("add", 12))
        assertEquals("The queue is back as it was before you cleared it", QueueUndoRules.done("clear"))
    }
}
