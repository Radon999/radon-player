package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your playlists and their folders: making, filling and deleting them, copying a service's playlist into yours,
 *  and which songs of a playlist are still to download. The playlists and folders are the test's own, made for the
 *  test and deleted after it. */
class YourPlaylistsTest {
    private val mark = "YPL test"
    private fun song(id: String, title: String, source: String = "ytm") = Track("ypl-$id", source, title, "Playlist Test Band")

    /** A service with one playlist ("pl-1") whose songs the test sets; any other playlist can't be read. */
    private class TestService(var songs: List<Track>) : Source {
        override val key = "ytm"
        override val name = "The test's service"
        override val hint = ""
        override val signedIn = false
        override fun search(q: String): List<Track> = emptyList()
        override fun myPlaylists(): List<Album> = emptyList()
        override fun playlist(id: String): List<Track> = if (id == "pl-1") songs else throw RuntimeException("no such playlist")
        override fun likedIds(): Set<String> = emptySet()
        override fun rate(t: Track, like: Boolean) {}
        override fun album(id: String): List<Track> = emptyList()
        override fun artist(name: String): ArtistInfo = ArtistInfo(name, "", emptyList(), emptyList())
        override fun radio(t: Track): List<Track> = emptyList()
    }

    private fun cleanUp() = Harness.ui {
        Music.lists.items.filter { it.name.startsWith(mark) }.forEach { Music.lists.delete(it.id) }
        Music.playlistFolders.all().filter { it.name.startsWith(mark) }.forEach { Music.playlistFolders.delete(it.id) }
        Music.musicVersion++
    }

    @Test fun whatAddingSongsSays() {
        assertEquals("Added to “Gym”", YourPlaylists.addedText("Gym", asked = 1, new = 1))
        assertEquals("Already in “Gym”", YourPlaylists.addedText("Gym", asked = 1, new = 0))
        assertEquals("Already in “Gym”", YourPlaylists.addedText("Gym", asked = 5, new = 0))
        assertEquals("5 songs added to “Gym”", YourPlaylists.addedText("Gym", asked = 5, new = 5))
        assertEquals("3 songs added to “Gym” (2 were in it)", YourPlaylists.addedText("Gym", asked = 5, new = 3))
    }

    @Test fun whatImportingSays() {
        assertEquals("Imported “Road” from Tidal: 12 songs. Open it in your playlists to download it",
            YourPlaylists.importedText("Road", "Tidal", 12, wasHere = false))
        assertEquals("“Road” was imported already: 1 new song added", YourPlaylists.importedText("Road", "Tidal", 1, wasHere = true))
        assertEquals("“Road” was imported already: 0 new songs added", YourPlaylists.importedText("Road", "Tidal", 0, wasHere = true))
        assertEquals("Imported 4 of 4 Tidal playlists (90 songs)", YourPlaylists.importedAllText("Tidal", 4, 0, 90))
        assertEquals("Imported 3 of 4 Tidal playlists (70 songs), 1 couldn't be read", YourPlaylists.importedAllText("Tidal", 4, 1, 70))
    }

    @Test fun aSongIsInAPlaylistOnceAndSoundCloudSongsStayOut() {
        Harness.ctx
        try {
            Harness.ui {
                val id = YourPlaylists.create("$mark one")
                fun ids() = Music.lists.get(id)!!.tracks.map { it.id }
                YourPlaylists.add(id, listOf(song("a", "Alpha"), song("b", "Beta")))
                YourPlaylists.add(id, listOf(song("b", "Beta"), song("c", "Gamma")))
                assertEquals("each once, in the order added", listOf("ypl-a", "ypl-b", "ypl-c"), ids())
                YourPlaylists.add(id, listOf(song("sc", "From SoundCloud", SoundCloud.KEY), song("d", "Delta")))
                assertEquals("the SoundCloud song stays out, the other goes in", listOf("ypl-a", "ypl-b", "ypl-c", "ypl-d"), ids())
                YourPlaylists.remove(id, setOf("ypl-a", "ypl-c"))
                assertEquals(listOf("ypl-b", "ypl-d"), ids())
                YourPlaylists.rename(id, "$mark renamed")
                assertEquals("$mark renamed", Music.lists.get(id)!!.name)
                YourPlaylists.add("no-such-playlist", listOf(song("e", "Epsilon")))   // nothing happens
            }
        } finally { cleanUp() }
    }

    @Test fun aDeletedPlaylistLeavesItsFoldersAndADeletedFolderKeepsItsPlaylists() {
        Harness.ctx
        try {
            Harness.ui {
                val id = YourPlaylists.create("$mark in folders")
                val other = YourPlaylists.create("$mark stays")
                val item = FolderItem.mine(Music.lists.get(id)!!)
                val stays = FolderItem.mine(Music.lists.get(other)!!)
                fun folder(name: String) = Music.playlistFolders.all().firstOrNull { it.name == name }
                YourPlaylists.newFolder("$mark folder A", item)
                YourPlaylists.newFolder("$mark folder B")
                val a = folder("$mark folder A")!!.id; val b = folder("$mark folder B")!!.id
                assertEquals("a new folder made for a playlist holds it", listOf(item.key), folder("$mark folder A")!!.playlists.map { it.key })
                YourPlaylists.addToFolder(b, item); YourPlaylists.addToFolder(b, item); YourPlaylists.addToFolder(b, stays)
                assertEquals("a playlist is in a folder once", listOf(item.key, stays.key), Music.playlistFolders.get(b)!!.playlists.map { it.key })

                YourPlaylists.delete(id)
                assertNull("the playlist is gone", Music.lists.get(id))
                assertTrue("and out of the one folder", Music.playlistFolders.get(a)!!.playlists.isEmpty())
                assertEquals("and the other, which keeps the rest", listOf(stays.key), Music.playlistFolders.get(b)!!.playlists.map { it.key })

                YourPlaylists.removeFromFolder(b, stays)
                assertTrue(Music.playlistFolders.get(b)!!.playlists.isEmpty())
                YourPlaylists.addToFolder(b, stays)
                YourPlaylists.renameFolder(b, "$mark folder B2")
                assertEquals("$mark folder B2", Music.playlistFolders.get(b)!!.name)
                YourPlaylists.deleteFolder(b)
                assertNull(Music.playlistFolders.get(b))
                assertNotNull("deleting a folder doesn't delete the playlists in it", Music.lists.get(other))
            }
        } finally { cleanUp() }
    }

    @Test fun aServicesPlaylistIsCopiedOnceAndImportedAgainOnlyGetsWhatsNew() {
        Harness.ctx
        val svc = TestService(listOf(song("a", "Alpha"), song("b", "Beta")))
        val before = Music.sources["ytm"]
        val name = "$mark from the service"
        fun mine() = Music.lists.all().filter { it.name.equals(name, ignoreCase = true) }
        fun import(pl: Album): Int {
            val got = java.util.concurrent.atomic.AtomicReference<Int?>(null)
            Harness.ui { YourPlaylists.importFrom("ytm", pl, quiet = true) { got.set(it) } }
            Harness.until("the import of ${pl.title} to end") { got.get() != null }
            return got.get()!!
        }
        try {
            Harness.ui { Music.sources["ytm"] = svc }
            assertEquals("both songs are new", 2, import(Album("pl-1", name)))
            assertEquals(listOf("ypl-a", "ypl-b"), Harness.ui { mine().single().tracks.map { it.id } })

            svc.songs = svc.songs + song("c", "Gamma")
            assertEquals("imported again (its name in other letters): only the new song", 1, import(Album("pl-1", name.uppercase())))
            assertEquals("still one playlist of that name", listOf("ypl-a", "ypl-b", "ypl-c"), Harness.ui { mine().single().tracks.map { it.id } })

            assertEquals("a playlist the service can't read: said so, nothing made", -1, import(Album("pl-gone", "$mark unreadable")))
            assertTrue(Harness.ui { Music.lists.all().none { it.name == "$mark unreadable" } })
            val sc = java.util.concurrent.atomic.AtomicReference<Int?>(null)
            Harness.ui { YourPlaylists.importFrom(SoundCloud.KEY, Album("pl-1", "$mark soundcloud"), quiet = true) { sc.set(it) } }
            assertEquals("SoundCloud's playlists aren't copied", -1, sc.get())
            assertTrue(Harness.ui { Music.lists.all().none { it.name == "$mark soundcloud" } })
        } finally {
            Harness.ui { if (before != null) Music.sources["ytm"] = before else Music.sources.remove("ytm") }
            cleanUp()
        }
    }

    @Test fun theSongsOfPlaylistsStillToDownloadAreEachOnceWithoutYourFiles() {
        Harness.ctx
        try {
            Harness.ui {
                val one = YourPlaylists.create("$mark dl one"); val two = YourPlaylists.create("$mark dl two")
                YourPlaylists.add(one, listOf(song("a", "Alpha"), song("f", "A File Of Yours", "file")))
                YourPlaylists.add(two, listOf(song("a", "Alpha"), song("b", "Beta")))
                assertEquals("each streamed song once; your file left out", listOf("ypl-a", "ypl-b"), YourPlaylists.toDownload(listOf(one, two)).map { it.id })
                assertTrue("a playlist that isn't there has none", YourPlaylists.toDownload(listOf("no-such-playlist")).isEmpty())
            }
        } finally { cleanUp() }
    }
}
