package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shuffle takes the whole list again, and Play next / Add to queue can move a waiting song instead of doubling it. */
class QueueShuffleRulesTest {
    private fun t(id: String, queued: Boolean = false) = Track(id, title = id, artist = "A", queued = queued)
    private fun ids(q: List<Track>) = q.map { it.id }

    @Test fun shuffleBringsBackTheSongsAlreadyPlayed() {
        val q = listOf(t("a"), t("b"), t("c"), t("d"), t("e"))
        val out = QueueRules.reshuffled(q, index = 2) { it.reversed() }
        assertEquals("the song playing is first", "c", out.first().id)
        assertEquals("every song is still there, once", setOf("a", "b", "c", "d", "e"), ids(out).toSet())
        assertEquals(5, out.size)
        assertEquals(listOf("c", "e", "d", "b", "a"), ids(out))
    }

    @Test fun theSongsYouQueuedStayNextInYourOrder() {
        val q = listOf(t("a"), t("b"), t("q1", queued = true), t("q2", queued = true), t("c"))
        val out = QueueRules.reshuffled(q, index = 1) { it.reversed() }
        assertEquals(listOf("b", "q1", "q2", "c", "a"), ids(out))
    }

    @Test fun withNothingPlayingTheQueueIsLeft() {
        val q = listOf(t("a"), t("b"))
        assertEquals(q, QueueRules.reshuffled(q, index = -1) { it.reversed() })
        assertTrue(QueueRules.reshuffled(emptyList(), 0) { it }.isEmpty())
    }

    @Test fun addedTwiceByDefault() {
        val q = listOf(t("a"), t("b"), t("c"), t("d"))
        val out = QueueRules.enqueued(q, index = 0, add = listOf(t("d", queued = true)), next = true, move = false)
        assertEquals("it plays next, and again where it was", listOf("a", "d", "b", "c", "d"), ids(out))
    }

    @Test fun movedWhenTheSwitchIsOn() {
        val q = listOf(t("a"), t("b"), t("c"), t("d"))
        val out = QueueRules.enqueued(q, index = 0, add = listOf(t("d", queued = true)), next = true, move = true)
        assertEquals("in the queue once, where you put it", listOf("a", "d", "b", "c"), ids(out))
    }

    @Test fun addToQueueGoesAfterWhatYouQueuedBefore() {
        val q = listOf(t("a"), t("q1", queued = true), t("b"), t("c"))
        assertEquals(listOf("a", "q1", "c", "b"), ids(QueueRules.enqueued(q, 0, listOf(t("c", queued = true)), next = false, move = true)))
        assertEquals(listOf("a", "c", "q1", "b"), ids(QueueRules.enqueued(q, 0, listOf(t("c", queued = true)), next = true, move = true)))
    }

    @Test fun whatHasPlayedAndTheSongPlayingAreLeftAlone() {
        val q = listOf(t("a"), t("b"), t("c"))
        val out = QueueRules.enqueued(q, index = 1, add = listOf(t("a", queued = true), t("b", queued = true)), next = true, move = true)
        assertEquals("the one played and the one playing stay; both are added after", listOf("a", "b", "a", "b", "c"), ids(out))
    }
}
