package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An album's page after its tags change: the album is found again by its songs, so the page shows the new names. */
class AlbumSongsTest {
    private fun song(id: String, title: String, artist: String, album: String) = Track(id, "file", title, artist).copy(album = album)
    private fun group(id: String, title: String, artist: String, vararg songs: Track) = Album("album:$id", title, artist) to songs.toList()

    @Test fun anAlbumWhoseArtistYouRenamedIsFoundByItsSongs() {
        // The owner's case: "cavetown offline" made "Cavetown" in the Tagger while the album's page was open
        val was = listOf(song("s1", "Hug All Ur Friends", "cavetown offline", "Gd Vibes"))
        val all = listOf(
            group("other", "Lemon Boy", "Cavetown", song("x1", "Lemon Boy", "Cavetown", "Lemon Boy")),
            group("renamed", "Gd Vibes", "Cavetown", song("s1", "Hug All Ur Friends", "Cavetown", "Gd Vibes")))
        val hit = AlbumSongs.follow(all, was)
        assertEquals("album:renamed", hit?.first?.id)
        assertEquals("the page's line under the name is the new artist", "Cavetown", hit?.first?.sub)
        assertEquals("Cavetown", hit?.second?.single()?.artist)
    }

    @Test fun songsWhoseFilesMovedAreKnownByTheirNamesOnTheSameAlbum() {
        val was = listOf(song("old/a", "Alpha", "Old Name", "Record"), song("old/b", "Beta", "Old Name", "Record"))
        val moved = group("new", "Record", "New Name", song("new/a", "Alpha", "New Name", "Record"), song("new/b", "Beta (Remastered)", "New Name", "Record"))
        val another = group("no", "Another Record", "Somebody", song("z", "Alpha", "Somebody", "Another Record"))
        assertEquals("album:new", AlbumSongs.follow(listOf(another, moved), was)?.first?.id)
        assertNull("a song named the same on another album by another artist is not it", AlbumSongs.follow(listOf(another), was))
        assertNull(AlbumSongs.follow(listOf(moved), emptyList()))
    }
}
