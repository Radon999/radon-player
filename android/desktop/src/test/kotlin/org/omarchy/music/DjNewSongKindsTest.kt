package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The kinds of set that bring songs you haven't heard, from a service: here from a service of the test's own (its
 *  radio of a song, an artist's top songs), with a listening history of the test's own. Playing from everything. */
class DjNewSongKindsTest {
    /** A service that knows a radio for any song and top songs for any artist, and nothing else. */
    private class TestService(private val radioOf: (Track) -> List<Track>) : Source {
        override val key = "ytm"
        override val name = "The test's service"
        override val hint = ""
        override val signedIn = false
        override fun search(q: String): List<Track> = emptyList()
        override fun myPlaylists(): List<Album> = emptyList()
        override fun playlist(id: String): List<Track> = emptyList()
        override fun likedIds(): Set<String> = emptySet()
        override fun rate(t: Track, like: Boolean) {}
        override fun album(id: String): List<Track> = emptyList()
        override fun artist(name: String): ArtistInfo =
            ArtistInfo(name, "", (1..8).map { Track("svc-top-$name-$it", "ytm", "Deeper $it of $name", name) }, emptyList())
        override fun radio(t: Track): List<Track> = radioOf(t)
    }

    private fun radioSongs(t: Track) = (1..10).map { Track("svc-radio-${t.id}-$it", "ytm", "Near ${t.title} no $it", "Nearby band $it of ${t.artist}") }

    /** Eight songs played a lot these last weeks, each by its own artist. */
    private fun history(): List<Heard> {
        val now = System.currentTimeMillis() / 1000
        return (1..8).flatMap { i -> (2..6).map { k -> Heard(now - k * 86_400L - 6 * 3_600L, "Lately band $i", "Lately song $i") } }
    }

    private fun <T> withService(radioOf: (Track) -> List<Track> = ::radioSongs, block: () -> T): T {
        Harness.ctx
        val before = Music.sources["ytm"]
        Harness.ui { Music.sources["ytm"] = TestService(radioOf) }
        Dj.playFrom(null, null)   // from everything
        Dj.historyStandIn = { history() }
        Dj.retaste()
        try { return block() } finally {
            Dj.historyStandIn = null; Dj.retaste()
            Harness.ui { if (before != null) Music.sources["ytm"] = before else Music.sources.remove("ytm") }
        }
    }

    private fun built(kind: String, check: (Dj.Built) -> Unit) {
        repeat(6) {
            val b = Dj.build(kind = kind)
            assertNotNull("a $kind set can be made", b)
            try {
                assertEquals(kind, b!!.kind)
                assertTrue("${b.songs.size} songs", b.songs.size in 3..Dj.SET)
                assertEquals("no song twice", b.songs.size, b.songs.map { songKey(it) }.toSet().size)
                assertTrue("none of them is a song you've played: ${b.songs.map { it.title }}", b.songs.none { it.title.startsWith("Lately song") })
                check(b)
            } finally { Dj.discard(b!!.id) }
        }
    }

    @Test fun newForYouIsTheRadioOfWhatYouPlayLatelyAndSaysWhose() = withService {
        built("fresh") { b ->
            assertTrue("${b.songs.map { it.title }}", b.songs.all { it.title.startsWith("Near Lately song") })
            assertTrue(b.line, b.line.contains("Lately band"))
        }
    }

    @Test fun deepCutsAreSongsYouHaventPlayedByAnArtistYouPlay() = withService {
        built("deep") { b ->
            val who = b.songs[0].artist
            assertTrue(who, who.startsWith("Lately band"))
            assertTrue("one artist: ${b.songs.map { it.artist }}", b.songs.all { it.artist == who })
            assertTrue("${b.songs.map { it.title }}", b.songs.all { it.title.startsWith("Deeper") })
            assertTrue(b.line, b.line.contains(who))
        }
    }

    @Test fun moreLikeThisIsARadioAndNeverHasTheSongItStartsFrom() = withService(radioOf = { listOf(it) + radioSongs(it) }) {   // (a radio often begins with its own song)
        built("like") { b ->
            assertTrue("${b.songs.map { it.title }}", b.songs.all { it.title.startsWith("Near ") })
            val seed = Regex("“([^”]+)”").find(b.line)?.groupValues?.get(1)
            assertNotNull(b.line, seed)
            assertTrue("the song it starts from isn't in it", b.songs.none { it.title == seed })
        }
    }

    @Test fun aServiceWithNothingToOfferMakesNoSetOfNewSongs() = withService(radioOf = { emptyList() }) {
        assertNull(Dj.build(kind = "fresh"))
        assertNull(Dj.build(kind = "like"))
    }
}
