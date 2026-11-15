package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Changing a song's artist or album by hand: where its file belongs after. Nothing is moved or written here. */
class TagEditsTest {
    private val root = "C:/Users/me/Music/Artists"
    private fun placeOf(artist: String, album: String) = downloadPlace(root, artist, album, null, null, "x").first

    @Test fun aFileThatSatWhereItsOldTagsPutItGoesToItsNewArtistsFolder() {
        Harness.ctx
        val was = placeOf("Old Name", "Record") + "01 Song.flac"
        assertEquals("the new artist's folder for the same album", placeOf("New Name", "Record"),
            TagEdits.releaseFolder(was, download = false, oldArtist = "Old Name", oldAlbum = "Record", artist = "New Name", album = "Record"))
        assertEquals("a new album: that album's folder", placeOf("Old Name", "Another Record"),
            TagEdits.releaseFolder(was, download = false, oldArtist = "Old Name", oldAlbum = "Record", artist = "Old Name", album = "Another Record"))
    }

    @Test fun aFolderYouArrangedYourselfStaysAsItIs() {
        Harness.ctx
        // In an Artists folder, but not where its tags would have put it (your unreleased songs, say)
        assertNull(TagEdits.releaseFolder("$root/UNRELEASED/Coldplay/The Race.mp3", download = false,
            oldArtist = "Coldplay", oldAlbum = "", artist = "Coldplay", album = "Demos"))
        assertNull("not in an Artists folder at all", TagEdits.releaseFolder("C:/Users/me/Music/Singles and EPs/x/a.flac", download = false,
            oldArtist = "Old Name", oldAlbum = "Record", artist = "New Name", album = "Record"))
        assertNull("no artist to file it under", TagEdits.releaseFolder(placeOf("Old Name", "Record") + "a.flac", download = false,
            oldArtist = "Old Name", oldAlbum = "Record", artist = "  ", album = "Record"))
    }
}
