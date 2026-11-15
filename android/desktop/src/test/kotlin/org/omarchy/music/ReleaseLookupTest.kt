package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** New releases: where a release is on the streaming services, with a service of the test's own in YouTube Music's place. */
class ReleaseLookupTest {
    private fun release(title: String, artist: String = "Lookup Test Band") = NewReleases.Release(title, artist, "2026-11-01", "album")
    private fun song(id: String, title: String, artist: String = "Lookup Test Band", album: String = "") = Track("rl-$id", "ytm", title, artist).copy(album = album)

    /** A service that finds what the test gives it, and knows one album's songs. */
    private class TestService(val found: Found, val albums: Map<String, List<Track>> = emptyMap()) : Source {
        override val key = "ytm"
        override val name = "The test's service"
        override val hint = ""
        override val signedIn = false
        override fun search(q: String): List<Track> = found.songs
        override fun find(q: String): Found = found
        override fun myPlaylists(): List<Album> = emptyList()
        override fun playlist(id: String): List<Track> = emptyList()
        override fun likedIds(): Set<String> = emptySet()
        override fun rate(t: Track, like: Boolean) {}
        override fun album(id: String): List<Track> = albums[id].orEmpty()
        override fun artist(name: String): ArtistInfo = ArtistInfo(name, "", emptyList(), emptyList())
        override fun radio(t: Track): List<Track> = emptyList()
    }

    @Test fun theAlbumNamedAsTheReleaseByItsArtistIsTheOne() {
        val res = Found(songs = emptyList(), albums = listOf(
            Album("other", "Night Drive", "Somebody Else · 2026"),
            Album("live", "Night Drive (Live at the Hall)", "Lookup Test Band · 2026"),
            Album("it", "Night Drive (Deluxe)", "Lookup Test Band, A Guest · 2026"),
            Album("spelled", "Night Drive", "Lookup-Test Band · 2026")))
        // cleanTitle leaves an edition in the name, so only the same name is the release
        assertEquals("by another artist is not it; the artist written another way is", "spelled", ReleaseLookup.albumIn(res, release("Night Drive"))?.id)
        assertEquals("it", ReleaseLookup.albumIn(res, release("Night Drive (Deluxe)"))?.id)
        assertNull(ReleaseLookup.albumIn(res, release("Day Drive")))
    }

    @Test fun aSingleIsTheSongNamedSoWithTheArtistFirst() {
        val res = Found(songs = listOf(song("guest", "Glass Sea", "Somebody Else, Lookup Test Band"), song("it", "Glass Sea (feat. A Guest)", "Lookup Test Band, A Guest")))
        assertEquals("the one where they come first, not where they are a guest", "rl-it", ReleaseLookup.songIn(res, release("Glass Sea"))?.id)
        assertNull(ReleaseLookup.songIn(res, release("Glass Lake")))
    }

    @Test fun aReleasesSongsComeFromItsAlbumElseTheSongItself() {
        Harness.ctx
        val before = Music.sources["ytm"]
        val album = Album("al-1", "Lookup Album One", "Lookup Test Band · 2026")
        val svc = TestService(Found(songs = listOf(song("s1", "Lookup Single One")), albums = listOf(album)),
            mapOf("al-1" to listOf(song("a", "Alpha"), song("b", "Beta", album = "Lookup Album One (as the service names it)"))))
        try {
            ReleaseLookup.forget()
            Harness.ui { Music.sources["ytm"] = svc }
            val songs = ReleaseLookup.tracks(release("Lookup Album One"))
            assertEquals(listOf("rl-a", "rl-b"), songs.map { it.id })
            assertEquals("a song with no album gets the release's name", "Lookup Album One", songs[0].album)
            assertEquals("a song's own album name is kept", "Lookup Album One (as the service names it)", songs[1].album)

            val single = ReleaseLookup.tracks(release("Lookup Single One"))
            assertEquals("no album of that name: the song", listOf("rl-s1"), single.map { it.id })
            assertEquals("Lookup Single One", single[0].album)

            assertEquals("not on the services: nothing", emptyList<Track>(), ReleaseLookup.tracks(release("Lookup Nowhere")))
        } finally {
            Harness.ui { if (before != null) Music.sources["ytm"] = before else Music.sources.remove("ytm") }
            ReleaseLookup.forget()
        }
    }
}
