package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MusicFolderRulesTest {
    @Test fun aFolderInsideAnotherIsDropped() {
        assertEquals(listOf("Downloads/", "Music/"), MusicFolderRules.clean(listOf("Music/", "Music/Rock/", "Downloads/", "Music/")))
        assertEquals(listOf("Music/Jazz/", "Music/Rock/"), MusicFolderRules.clean(listOf("Music/Rock/", "Music/Jazz/")))
        assertEquals(emptyList<String>(), MusicFolderRules.clean(emptyList()))
    }

    @Test fun aMovedFileKeepsItsSongAtTheNewAddress() {
        val old = "C:/Music/Old/song.flac"; val new = "C:/Music/New/song.flac"
        val mine = Track(MusicFolderRules.fileId(old), "file", "Song", "Band", file = "C:\\Music\\Old\\song.flac")
        val after = MusicFolderRules.moved(mine, mapOf(old to new))
        assertEquals(new, after.file)
        assertEquals("its id goes with its address", MusicFolderRules.fileId(new), after.id)
        assertEquals("Song", after.title)
        val download = Track("abc", "ytm", "Song", "Band", file = old)
        assertEquals("a download keeps its own id", "abc", MusicFolderRules.moved(download, mapOf(old to new)).id)
        assertEquals(new, MusicFolderRules.moved(download, mapOf(old to new)).file)
    }

    @Test fun aSongThatDidNotMoveIsLeft() {
        val stays = Track("f1", "file", "Song", "Band", file = "C:/Music/stays.flac")
        assertSame(stays, MusicFolderRules.moved(stays, mapOf("C:/Music/other.flac" to "C:/x.flac")))
        val stream = Track("s", "tidal", "Song", "Band")
        assertSame(stream, MusicFolderRules.moved(stream, mapOf("a" to "b")))
    }

    @Test fun whereASongNotHereIsLookedFor() {
        assertEquals("tidal", MusicFolderRules.streamFrom("tidal", tidalSignedIn = true))
        assertEquals("not signed in to Tidal: YouTube Music", "ytm", MusicFolderRules.streamFrom("tidal", tidalSignedIn = false))
        assertEquals("never your own music", "ytm", MusicFolderRules.streamFrom("local", tidalSignedIn = true))
        assertEquals("soundcloud", MusicFolderRules.streamFrom("soundcloud", tidalSignedIn = false))
    }

    @Test fun whatItSays() {
        assertEquals("Found 1 moved song and linked them in your playlists", MusicFolderRules.movedText(1))
        assertEquals("Found 3 moved songs and linked them in your playlists", MusicFolderRules.movedText(3))
        assertEquals("Matched 2 songs from your backup to your music here", MusicFolderRules.matchedText(2))
        assertEquals("Genres added to 1 download", MusicFolderRules.genresText(1))
    }
}
