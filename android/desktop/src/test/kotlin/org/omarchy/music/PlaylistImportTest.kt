package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Importing an .m3u8 playlist: its lines found among your songs by name, the rest looked for on a service, and the
 *  ones not found kept with their place. The songs and the service are the test's own. */
class PlaylistImportTest {
    private fun mine(id: String, title: String) = Track("pi-$id", "file", title, "Import Test Band", file = "/import-test/$id.flac")

    /** A service that knows one song by name and nothing else. */
    private class TestService : Source {
        override val key = "ytm"
        override val name = "The test's service"
        override val hint = ""
        override val signedIn = false
        override fun search(q: String): List<Track> =
            if ("streamed one" in q.lowercase()) listOf(Track("pi-svc", "ytm", "Streamed One", "Import Test Band").copy(duration = 200.0)) else emptyList()
        override fun myPlaylists(): List<Album> = emptyList()
        override fun playlist(id: String): List<Track> = emptyList()
        override fun likedIds(): Set<String> = emptySet()
        override fun rate(t: Track, like: Boolean) {}
        override fun album(id: String): List<Track> = emptyList()
        override fun artist(name: String): ArtistInfo = ArtistInfo(name, "", emptyList(), emptyList())
        override fun radio(t: Track): List<Track> = emptyList()
    }

    @Test fun aPlaylistsLinesAreYourSongsThenTheServicesAndTheRestAreKeptToLookFor() {
        Harness.ctx
        val had = Music.local.files
        val before = Music.sources["ytm"]; val tidal = Music.sources["tidal"]
        val file = java.io.File.createTempFile("import-test", ".m3u8").apply { deleteOnExit() }
        file.writeText(listOf("#EXTM3U",
            "#EXTINF:200,Import Test Band - Yours One", "somewhere/else/yours-one.flac",
            "#EXTINF:200,Import Test Band - Streamed One", "somewhere/else/streamed-one.flac",
            "#EXTINF:200,Import Test Band - Nowhere At All", "somewhere/else/nowhere.flac",
            "#EXTINF:200,Import Test Band - Yours Two", "somewhere/else/yours-two.flac").joinToString("\n"))
        try {
            Harness.ui {
                Music.local.files = had + listOf(mine("one", "Yours One"), mine("two", "Yours Two")); Music.musicVersion++
                Music.sources["ytm"] = TestService(); Music.sources.remove("tidal")
            }
            val (_, songs, missed) = PlaylistImport.importM3u(file.absolutePath.replace('\\', '/'))
            assertEquals("yours by name, the service's for the one you haven't got, in the playlist's order",
                listOf("pi-one", "pi-svc", "pi-two"), songs.map { it.id })
            assertEquals(listOf("Import Test Band - Nowhere At All"), missed.map { it.name })
            assertEquals("the one not found was the third line, and belongs after two songs", 2 to 2, missed[0].n to missed[0].at)
            assertTrue("why it isn't there is said", missed[0].why.isNotEmpty())
        } finally {
            Harness.ui {
                Music.local.files = had; Music.musicVersion++; Music.importing = null
                if (before != null) Music.sources["ytm"] = before else Music.sources.remove("ytm")
                if (tidal != null) Music.sources["tidal"] = tidal
            }
        }
    }
}
