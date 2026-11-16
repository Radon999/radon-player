package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deleting songs: which downloads go, the song playing left, and your own files taken out of your music, the queue
 *  and your playlists once they are gone. No file is deleted here: the songs and the playlist are the test's own. */
class DeletingSongsTest {
    private fun song(id: String, source: String = "ytm") = Track("del-$id", source, "Song $id", "Deleting Test Band")
    private fun mine(id: String) = Track("del-$id", "file", "Song $id", "Deleting Test Band", file = "/deleting-test/$id.flac")

    @Test fun onlyDownloadsGoAndNeverTheSongPlaying() {
        val has = { id: String -> id in setOf("del-a", "del-b", "del-playing") }
        val asked = listOf(song("a"), song("stream"), song("playing"), song("b"))
        val (ids, playing) = DeletingSongs.downloadsToDelete(asked, has, playing = "del-playing")
        assertEquals("the downloads, without the one playing and without the one that is only streamed", listOf("del-a", "del-b"), ids)
        assertTrue("the playing one was asked for: said, and left", playing)
        val (all, none) = DeletingSongs.downloadsToDelete(asked, has, playing = null)
        assertEquals(listOf("del-a", "del-playing", "del-b"), all)
        assertFalse(none)
    }

    @Test fun whatItSays() {
        assertEquals("Deleted 1 download", DeletingSongs.deletedText(1, "download"))
        assertEquals("Deleted 3 songs", DeletingSongs.deletedText(3, "song"))
    }

    @Test fun aStreamedSongIsNotAFileOfYours() {
        Harness.ctx
        assertNull(DeletingSongs.ownFile(song("stream")))
        assertFalse(DeletingSongs.deletable(song("stream")))
    }

    @Test fun filesThatAreGoneLeaveYourMusicAndYourPlaylists() {
        Harness.ctx
        val had = Music.local.files
        val name = "Deleting test playlist"
        val gone = mine("gone"); val stays = mine("stays")
        try {
            Harness.ui {
                Music.local.files = had + listOf(gone, stays); Music.musicVersion++
                val id = Music.lists.create(name).id
                Music.lists.add(id, listOf(gone, stays))
                DeletingSongs.filesDeleted(listOf(gone))
                assertTrue("out of your music", Music.local.files.none { it.id == gone.id })
                assertTrue("the other stays", Music.local.files.any { it.id == stays.id })
                assertEquals("and out of the playlist it was in", listOf(stays.id), Music.lists.get(id)!!.tracks.map { it.id })
            }
        } finally {
            Harness.ui {
                Music.local.files = had
                Music.lists.all().filter { it.name == name }.forEach { Music.lists.delete(it.id) }
                Music.musicVersion++
            }
        }
    }
}
