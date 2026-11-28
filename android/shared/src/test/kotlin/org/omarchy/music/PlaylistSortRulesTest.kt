package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSortRulesTest {
    private val R = PlaylistSortRules

    @Test fun eachPlaylistKeepsItsOwnSortAndTheRestFollowTheLastPick() {
        val own = R.picked(R.picked(emptyMap(), "gym", "artist"), "sleep", "title")
        assertEquals("artist", R.of("gym", own, "order")); assertEquals("title", R.of("sleep", own, "order"))
        assertEquals("order", R.of("road trip", own, "order")); assertEquals("album", R.of(null, own, "album"))
    }

    @Test fun itIsKeptAsTextAndReadBack() {
        val own = R.picked(R.picked(emptyMap(), "a", "artist"), "b", "title")
        assertEquals(own, R.parse(R.write(own)))
        assertEquals(emptyMap<String, String>(), R.parse("a\tno such sort\nrubbish"))
    }

    @Test fun pickingAgainReplacesAndMovesItLast() {
        val own = R.picked(R.picked(R.picked(emptyMap(), "a", "artist"), "b", "title"), "a", "album")
        assertEquals(listOf("b" to "title", "a" to "album"), own.entries.map { it.key to it.value })
    }
}
