package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenKindRulesTest {
    @Test fun aSongPickedOnItsArtistsPageIsNotRadio() {
        assertEquals("artist" to "", ListenKindRules.fix("radio", "", "HOAX", listOf("HOAX"), "b?", null))
        assertEquals("album" to "", ListenKindRules.fix("radio", "", "In Rainbows", listOf("Radiohead"), "In Rainbows", null))
        assertEquals("a song the radio went on to", "radio" to "", ListenKindRules.fix("radio", "", "HOAX", listOf("Someone Else"), "x", null))
    }

    @Test fun aSongPlayedFromThePlaylistsTabIsItsPlaylists() {
        assertEquals("playlist" to "p1", ListenKindRules.fix("other", "", "Playlists", listOf("HOAX"), "b?", "p1"))
        assertEquals("in none of yours, or in two", "other" to "", ListenKindRules.fix("other", "", "Playlists", listOf("HOAX"), "b?", null))
        assertEquals("from another list", "other" to "", ListenKindRules.fix("other", "", "My music", listOf("HOAX"), "b?", "p1"))
    }

    @Test fun aSittingIsThePlaylistMostOfItsSongsAreIn() {
        val size = mapOf("hoax" to 34, "all" to 3323, "juice" to 372)
        val both = setOf("hoax", "all")
        assertEquals("songs in a small playlist and in the one of everything: the small one", "hoax",
            ListenKindRules.sittingPlaylist(List(8) { both }, size::getValue))
        assertEquals("a sitting of all sorts: the playlist of everything", "all",
            ListenKindRules.sittingPlaylist(List(3) { both } + List(7) { setOf("all") }, size::getValue))
        assertEquals("mostly songs in no playlist: none", null,
            ListenKindRules.sittingPlaylist(List(2) { setOf("juice") } + List(8) { emptySet<String>() }, size::getValue))
        assertEquals(null, ListenKindRules.sittingPlaylist(emptyList(), size::getValue))
    }

    @Test fun whatWasRightStays() {
        assertEquals("dj" to "", ListenKindRules.fix("dj", "", "DJ", listOf("HOAX"), "b?", "p1"))
        assertEquals("playlist" to "p2", ListenKindRules.fix("playlist", "p2", "Gym", listOf("HOAX"), "b?", "p1"))
        assertEquals("search" to "", ListenKindRules.fix("search", "", "Search: hoax", listOf("HOAX"), "b?", null))
    }
}
