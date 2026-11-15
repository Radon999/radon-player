package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Discovery playlists: an artist's songs you haven't got, in a pair of playlists; a song heard to the end moves
 *  from the one to the other. The playlists are the test's own, made for the test and deleted after it. */
class DiscoveriesTest {
    private fun song(id: String, title: String, album: String = "") = Track("disco-$id", "ytm", title, "Disco Test Band").copy(album = album)

    @Test fun anArtistsSongsYouHaventGotAlbumByAlbumThenTheirTopSongsEachOnce() {
        val first = Album("al-1", "First Record"); val second = Album("al-2", "Second Record")
        val onAlbums = mapOf(
            "al-1" to listOf(song("a", "Alpha"), song("b", "Beta", "First Record (as the service names it)"), song("mine", "Mine Already")),
            "al-2" to listOf(song("a2", "Alpha"), song("c", "Gamma")))
        val info = ArtistInfo("Disco Test Band", "", top = listOf(song("c-top", "Gamma"), song("d", "Delta")), albums = listOf(first, second))
        val out = Discoveries.notYours(info, have = setOf(songKey(song("x", "Mine Already")))) { onAlbums[it.id].orEmpty() }
        assertEquals("each album in order, then the top songs; none twice; none of yours",
            listOf("disco-a", "disco-b", "disco-c", "disco-d"), out.map { it.id })
        assertEquals("a song with no album gets its album's name", "First Record", out[0].album)
        assertEquals("a song's own album name is kept", "First Record (as the service names it)", out[1].album)
        assertEquals("a top song has none", "", out[3].album)
    }

    @Test fun aSongYouHaveUnderAnotherNameOfTheArtistsIsNotNewToYou() {
        // Your file says "Disco Test" (a scan gave the artist one name); the service credits "Disco Test Band, A Guest".
        val mine = listOf(Track("mine-sos", "file", "SOS", "Disco Test"), Track("mine-other", "file", "Alpha", "Somebody Else"))
        val yours = Discoveries.yourTitles(mine, listOf("Disco Test Band"))
        assertEquals("only the song by them, not the one by somebody else", setOf(SameArtist.title("SOS")), yours)
        val al = Album("al-1", "First Record")
        val onAlbum = listOf(Track("svc-sos", "ytm", "SOS (feat. A Guest)", "Disco Test Band, A Guest"), song("a", "Alpha"))
        val info = ArtistInfo("Disco Test Band", "", top = listOf(Track("svc-sos-top", "ytm", "SOS", "Disco Test Band, A Guest")), albums = listOf(al))
        val out = Discoveries.notYours(info, have = setOf(songKey(mine[0]), songKey(mine[1])), yourTitles = yours) { onAlbum }
        assertEquals("SOS is yours on the album and among the top songs; Alpha by this artist is new", listOf("disco-a"), out.map { it.id })
    }

    @Test fun thePairIsMadeOnceAndASongHeardToTheEndMovesToTheDiscoveredOne() {
        Harness.ctx
        val name = "Disco Test Band"
        fun pair() = Discoveries.pairs().firstOrNull { it.first == name }
        val songs = listOf(song("a", "Alpha"), song("b", "Beta"), song("c", "Gamma"))
        try {
            Harness.ui {
                assertEquals("nothing of the test's is there", null, pair())
                val (disc, n) = Discoveries.keep(name, songs)
                assertEquals(3, n)
                assertEquals("$name Discovery playlist", disc.name)
                val p = pair()
                assertNotNull(p)
                assertEquals("$name discovered", p!!.third?.name)
                assertEquals(listOf("disco-a", "disco-b", "disco-c"), p.second!!.tracks.map { it.id })

                assertEquals("made again with one more song: only that one is new", 1, Discoveries.keep(name, songs + song("d", "Delta")).second)
                assertEquals("still one pair", 2, Music.lists.all().count { it.discovery?.artist == name })

                Discoveries.heardOut(songs[1])
                val after = pair()!!
                assertEquals(listOf("disco-a", "disco-c", "disco-d"), after.second!!.tracks.map { it.id })
                assertEquals(listOf("disco-b"), after.third!!.tracks.map { it.id })

                Discoveries.heardOut(song("other", "Not In Any"))   // a song in no discovery playlist: nothing moves
                assertEquals(3, pair()!!.second!!.tracks.size)

                Discoveries.review(after.third!!.id, download = emptySet(), remove = setOf("disco-b"))
                assertTrue("let go in the review", pair()!!.third!!.tracks.isEmpty())
            }
        } finally {
            Harness.ui { Music.lists.all().filter { it.discovery?.artist == name }.forEach { Music.lists.delete(it.id) }; Music.musicVersion++ }
        }
    }
}
