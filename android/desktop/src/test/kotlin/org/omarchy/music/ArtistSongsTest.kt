package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is an artist's among your music, and the order of an artist playlist. */
class ArtistSongsTest {
    private fun song(id: String, title: String, artist: String, album: String = "") = Track("as-$id", "file", title, artist).copy(album = album)

    @Test fun aSongIsTheArtistsWhenTheyAreCreditedOrFeatured() {
        assertTrue(ArtistSongs.credits(song("a", "Alpha", "Artist Test Band"), "Artist Test Band", emptyMap()))
        assertTrue("written another way", ArtistSongs.credits(song("b", "Beta", "Artist-Test Band"), "artist test band", emptyMap()))
        assertTrue("one of two", ArtistSongs.credits(song("c", "Gamma", "Somebody Else, Artist Test Band"), "Artist Test Band", emptyMap()))
        assertTrue("featured, said in the title", ArtistSongs.credits(song("d", "Delta (feat. Artist Test Band)", "Somebody Else"), "Artist Test Band", emptyMap()))
        assertFalse(ArtistSongs.credits(song("e", "Epsilon", "Somebody Else"), "Artist Test Band", emptyMap()))
        assertFalse("no name asked for", ArtistSongs.credits(song("a", "Alpha", "Artist Test Band"), "  ", emptyMap()))
    }

    @Test fun anArtistMergedIntoAnotherIsThatArtists() {
        val picks = mapOf(pairKey("Old Name") to "Artist Test Band")
        assertTrue(ArtistSongs.credits(song("a", "Alpha", "Old Name"), "Artist Test Band", picks))
        assertFalse("without the merge it isn't", ArtistSongs.credits(song("a", "Alpha", "Old Name"), "Artist Test Band", emptyMap()))
    }

    @Test fun anAlbumIsTheirsWhenBilledToThemOrMostlyTheirs() {
        val theirs = listOf(song("a", "Alpha", "Artist Test Band"), song("b", "Beta", "Artist Test Band"), song("c", "Gamma", "A Guest"))
        assertTrue("billed to them", ArtistSongs.isTheirs(Album("x", "Record", "Artist Test Band"), emptyList(), "Artist Test Band", emptyMap()))
        assertTrue("two of three songs theirs", ArtistSongs.isTheirs(Album("x", "Various", "Various Artists"), theirs, "Artist Test Band", emptyMap()))
        assertFalse("one of three isn't most", ArtistSongs.isTheirs(Album("x", "Various", "Various Artists"), theirs, "A Guest", emptyMap()))
    }

    @Test fun anArtistPlaylistGoesAlbumByAlbumInTrackOrder() {
        val list = listOf(
            song("s", "Loose Single", "Artist Test Band") to 9999,
            song("b2", "Second", "Artist Test Band", "Beta Album") to 2,
            song("a2", "Zulu", "Artist Test Band", "Alpha Album") to 2,
            song("b1", "First", "Artist Test Band", "Beta Album") to 1,
            song("a1", "Yankee", "Artist Test Band", "Alpha Album") to 1,
            song("a0", "No Number", "Artist Test Band", "Alpha Album") to 9999,
            song("a1", "Yankee", "Artist Test Band", "Alpha Album") to 1)   // the same song twice: once
        assertEquals("albums by name, each in track order, unnumbered after, songs with no album last",
            listOf("as-a1", "as-a2", "as-a0", "as-b1", "as-b2", "as-s"), ArtistSongs.inOrder(list).map { it.id })
    }

    @Test fun whatMakingItSays() {
        assertEquals("Made the playlist Artist Test Band (1 song)", ArtistSongs.madeText("Artist Test Band", 1, updated = false, unreachable = false))
        assertEquals("Updated the playlist Artist Test Band (12 songs): only yours, the services couldn't be reached",
            ArtistSongs.madeText("Artist Test Band", 12, updated = true, unreachable = true))
    }
}
