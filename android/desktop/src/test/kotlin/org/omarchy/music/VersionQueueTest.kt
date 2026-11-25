package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Queued clean/explicit changes: once the version wanted is downloaded, the version it replaces is deleted and the new
 *  one takes its place in your playlists. The songs, the file and the playlist are the test's own. */
class VersionQueueTest {
    @Test fun theVersionReplacedIsDeletedAndTheNewOneTakesItsPlace() {
        Harness.ctx
        val name = "Version queue test playlist"
        val file = java.io.File.createTempFile("vq-old", ".flac").apply { writeText("not music") }
        val old = Track("vq-old", "tidal", "Version Song", "Version Test Band")
        val new = Track("vq-new", "tidal", "Version Song", "Version Test Band")
        val oldFile = Track("vq-old-file", "file", "Other Version Song", "Version Test Band", file = "/version-queue-test/none.flac")
        val newFile = Track("vq-new-file", "tidal", "Other Version Song", "Version Test Band")
        try {
            Harness.ui {
                Music.lib.add(old, file.path, null, "flac")
                val id = Music.lists.create(name).id
                Music.lists.add(id, listOf(old, oldFile))
                VersionQueue.oldGone(listOf(old to new, oldFile to newFile))
                assertFalse("the old download is forgotten", Music.lib.has(old.id))
                assertFalse("and its file deleted", file.exists())
                assertEquals("the new versions in the old ones' places", listOf(new.id, newFile.id), Music.lists.get(id)!!.tracks.map { it.id })
            }
        } finally {
            file.delete()
            Harness.ui { Music.lib.forget(old.id); Music.lists.all().filter { it.name == name }.forEach { Music.lists.delete(it.id) }; Music.musicVersion++ }
        }
    }
}
