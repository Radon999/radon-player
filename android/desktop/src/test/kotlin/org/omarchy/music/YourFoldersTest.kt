package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your music folders: a song whose file moved is followed in your playlists, the same song of yours is found for
 *  one that is gone, and a file that isn't here is known not to be. No file is touched: the songs and the playlist
 *  are the test's own. */
class YourFoldersTest {
    private fun mine(id: String, title: String, where: String) =
        Track("yf-$id", "file", title, "Folders Test Band", file = "/your-folders-test/$where/$id.flac")

    @Test fun aMovedSongIsFollowedInYourPlaylists() {
        Harness.ctx
        val name = "Your folders test playlist"
        val old = mine("old", "Moved Song", "before"); val new = mine("new", "Moved Song", "after")
        val other = mine("other", "Other Song", "before")
        try {
            Harness.ui {
                val id = Music.lists.create(name).id
                Music.lists.add(id, listOf(old, other))
                YourFolders.relink(mapOf(old.id to new))
                val now = Music.lists.get(id)!!.tracks
                assertEquals("the moved one is the new one, in its place; the other is left", listOf(new.id, other.id), now.map { it.id })
                assertEquals(new.file, now[0].file)
            }
        } finally {
            Harness.ui { Music.lists.all().filter { it.name == name }.forEach { Music.lists.delete(it.id) }; Music.musicVersion++ }
        }
    }

    @Test fun theSameSongOfYoursIsFoundForOneThatIsGone() {
        Harness.ctx
        val had = Music.local.files
        val gone = mine("gone", "Found Again Song", "before"); val here = mine("here", "Found Again Song", "after")
        try {
            Harness.ui {
                Music.local.files = had + here; Music.musicVersion++
                assertEquals(here.id, YourFolders.hereCopy(gone)?.id)
                assertNull("no other copy of a song nobody has", YourFolders.hereCopy(mine("none", "A Song Nobody Has Here", "before")))
            }
        } finally {
            Harness.ui { Music.local.files = had; Music.musicVersion++ }
        }
    }

    @Test fun aStreamingSongYouHaveIsYourFileInYourPlaylists() {
        Harness.ctx
        val had = Music.local.files
        val name = "Your folders local first playlist"
        val file = mine("lf-file", "Local First Song", "here")
        val stream = Track("yf-lf-stream", "tidal", "Local First Song", "Folders Test Band")
        val other = Track("yf-lf-other", "tidal", "Nobody Has This Song", "Folders Test Band")
        try {
            val id = Harness.ui {
                Music.local.files = had + file; Music.musicVersion++
                val id = Music.lists.create(name).id
                Music.lists.add(id, listOf(stream, other))
                YourPlaylists.localFirst()
                id
            }
            Harness.until("the stream swapped for the file") { Harness.ui { Music.lists.get(id)!!.tracks.any { it.id == file.id } } }
            Harness.ui {
                assertEquals("your file in the stream's place; a song you don't have stays a stream", listOf(file.id, other.id), Music.lists.get(id)!!.tracks.map { it.id })
                Music.addToPlaylist(id, listOf(stream))
                assertEquals("adding the stream again adds nothing: your file is in it", 2, Music.lists.get(id)!!.tracks.size)
            }
        } finally {
            Harness.ui { Music.local.files = had; Music.lists.all().filter { it.name == name }.forEach { Music.lists.delete(it.id) }; Music.musicVersion++ }
        }
    }

    @Test fun aFileThatIsNotHereIsKnown() {
        Harness.ctx
        assertTrue("a stream has no file to miss", YourFolders.fileHere(Track("yf-stream", "tidal", "Stream", "Folders Test Band")))
        assertFalse(YourFolders.fileHere(mine("missing", "Missing Song", "nowhere")))
    }

    @Test fun choosingTheSameFoldersChangesNothing() {
        Harness.ctx
        Harness.ui {
            val had = Music.folders
            YourFolders.choose(had)
            assertEquals(had, Music.folders)
        }
    }
}
