package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sorting by how you listen: a tab keeps its own sort when it allows it, and a song's plays are counted once. */
class PlaySortsTest {
    @Test fun aTabUsesItsKeptSortOnlyWhenItAllowsIt() {
        assertEquals("artist", PlaySorts.pick("artist", "title", listOf("title", "artist")))
        assertEquals("a sort the tab does not have: its usual one", "title", PlaySorts.pick("plays", "title", listOf("title", "artist")))
        assertEquals("title", PlaySorts.pick(null, "title", listOf("title", "artist")))
    }

    @Test fun aSongsPlaysHereAndInTheStatsAreNotAddedUp() {
        val counts = PlaySorts.merged(here = listOf("a" to 5, "b" to 2), stats = listOf("a" to 40, "b" to 1, "c" to 7))
        assertEquals(mapOf("a" to 40, "b" to 2, "c" to 7), counts)
    }

    @Test fun aSongHeardElsewhereSinceCountsAsHeardThen() {
        val last = hashMapOf("a" to 100.0, "b" to 500.0)
        PlaySorts.latest(last, mapOf("a" to 300L, "b" to 200L, "c" to 50L))
        assertEquals(mapOf("a" to 300.0, "b" to 500.0, "c" to 50.0), last)
        PlaySorts.latest(last, null)
        assertEquals(3, last.size)
    }

    @Test fun aTabsSortIsKept() {
        Harness.ctx
        val tab = "play-sorts-test-tab"
        try {
            Harness.ui {
                assertEquals("title", PlaySorts.of(tab, "title", listOf("title", "artist")))
                PlaySorts.set(tab, "artist")
                assertEquals("artist", PlaySorts.of(tab, "title", listOf("title", "artist")))
                assertEquals("title", PlaySorts.of(tab, "title", listOf("title")))
            }
        } finally {
            Harness.ui { Music.tabSorts.remove(tab); Music.saveState() }
        }
    }
}
