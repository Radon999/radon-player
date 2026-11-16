package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseTabRulesTest {
    @Test fun aSongsKeyIsItsAddressWrittenOneWay() {
        assertEquals("c:/music/band/song.flac", ReleaseTabRules.key("C:\\Music\\Band\\Song.flac"))
        assertEquals(ReleaseTabRules.key("C:/Music/Band/Song.flac"), ReleaseTabRules.key("c:\\music\\band\\song.flac"))
    }

    @Test fun putByHandOnlyWhenMostOfItsSongsWere() {
        assertEquals("ep", ReleaseTabRules.byHand(listOf("ep", "ep", "single"), songs = 4))
        assertNull("half is not most", ReleaseTabRules.byHand(listOf("ep", "ep"), songs = 4))
        assertNull(ReleaseTabRules.byHand(emptyList(), songs = 3))
        assertEquals("single", ReleaseTabRules.byHand(listOf("single"), songs = 1))
    }

    @Test fun whatAnAlbumTagSays() {
        assertEquals("ep", ReleaseTabRules.ofTitle("Night Drive - EP", single = false))
        assertEquals("single", ReleaseTabRules.ofTitle("HandClap - Single", single = false))
        assertEquals("single", ReleaseTabRules.ofTitle("HandClap", single = true))
        assertNull(ReleaseTabRules.ofTitle("Night Drive", single = false))
    }

    @Test fun whatOneSongsTagSays() {
        assertEquals("ep", ReleaseTabRules.ofSong("Night Drive - EP", folder = null))
        assertEquals("single", ReleaseTabRules.ofSong("HandClap - Single", folder = "album"))
        assertEquals("no album at all, in a plain folder: a single", "single", ReleaseTabRules.ofSong("", folder = null))
        assertNull("no album, but its folder says what it is: the folder decides", ReleaseTabRules.ofSong("", folder = "ep"))
        assertNull(ReleaseTabRules.ofSong("Night Drive", folder = null))
    }

    @Test fun whatMovingSays() {
        assertEquals("Moved to EPs", ReleaseTabRules.movedText("ep"))
        assertEquals("Moved to Singles", ReleaseTabRules.movedText("single"))
        assertEquals("Back where their tags put them", ReleaseTabRules.movedText(null))
    }

    @Test fun genresMostSongsFirstAndUnknownLast() {
        val groups = listOf("Unknown genre" to listOf(1, 2, 3, 4), "Rock" to listOf(1), "Pop" to listOf(1, 2))
        assertEquals(listOf("Pop", "Rock", "Unknown genre"), ReleaseTabRules.genreOrder(groups).map { it.first })
    }
}
