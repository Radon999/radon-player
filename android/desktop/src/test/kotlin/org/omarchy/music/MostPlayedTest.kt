package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Most played's lists: counted songs become your own songs, and are added up by artist and by album. The saved
 *  songs are the test's own, put in for the test and taken out after it. */
class MostPlayedTest {
    private fun counted(title: String, artist: String, plays: Int, album: String = "") = Stats.Song(title, artist, album, "", plays)
    private fun played(id: String, title: String, artist: String, album: String, plays: Int) =
        Track("top-$id", "ytm", title, artist).copy(album = album, plays = plays)

    @Test fun twoNamesForOneSongOfYoursAreOneRowAndSongsYouHaveNotGotAreThereToo() {
        val mineEntry = LibEntry(id = "top-mine", source = "ytm", title = "Top Song", artist = "Top Band", album = "Top Record",
            file = "/music/test/top-mine.flac", format = "flac", added = 1.0)
        Harness.ctx
        Harness.ui { Music.lib.items[mineEntry.id] = mineEntry; Music.musicVersion++ }
        try {
            val counts = listOf(counted("Elsewhere Song", "Far Band", 50), counted("Top Song", "Top Band", 30),
                counted("Top Song (feat. Some Guest)", "Top Band", 5))
            val mine = Music.songIndex()
            val all = MostPlayed.songsOf(counts, mine)
            assertEquals("most played first", listOf(50, 35), all.map { it.plays })
            assertEquals("Elsewhere Song", all[0].title)
            assertEquals("top-mine", all[1].id)
        } finally { Harness.ui { Music.lib.items.remove(mineEntry.id); Music.musicVersion++ } }
    }

    @Test fun anAlbumAndItsDeluxeEditionAreOneUnderTheEditionsName() {
        val albums = MostPlayed.albumsOf(listOf(
            played("n1", "Night One", "Album Band", "Night Record", 10),
            played("n2", "Night Two", "Album Band", "Night Record (Deluxe Edition)", 4),
            played("n1d", "Night One", "Album Band", "Night Record (Deluxe Edition)", 3),
            played("d1", "Day One", "Album Band", "Day Record", 40),
            played("none", "No Album Song", "Album Band", "", 99)), emptyMap())
        assertEquals("most played first; a song with no album is in none", listOf("Day Record", "Night Record (Deluxe Edition)"), albums.map { it.title })
        val night = albums[1]
        assertEquals(17, night.plays)
        assertEquals("Album Band", night.artist)
        assertEquals("the same song on both editions is one", 2, night.tracks.size)
        assertEquals(13, night.tracks.first { it.title == "Night One" }.plays)
    }

    @Test fun aSinglesPlaysCountForTheAlbumYouHaveTheSongOn() {
        val single = played("lone", "Lone Song", "Album Band", "Lone Song", 7)
        val onAlbum = played("lone-album", "Lone Song", "Album Band", "Day Record", 0)
        val songs = listOf(single, played("d1", "Day One", "Album Band", "Day Record", 40))
        assertTrue("named for its song: a single", isSingle(single.album, single.title))
        val without = MostPlayed.albumsOf(songs, emptyMap())
        assertEquals("no copy on an album: the single is its own", listOf("Day Record" to 40, "Lone Song" to 7), without.map { it.title to it.plays })
        val with = MostPlayed.albumsOf(songs, mapOf(Stats.statsKey("Lone Song", "Album Band") to listOf(onAlbum)))
        assertEquals(listOf("Day Record" to 47), with.map { it.title to it.plays })
        assertEquals(2, with[0].tracks.size)
    }

    @Test fun aPairsPlaysAreTheLeadArtistsNamedAsTheyAreOnTheirOwn() {
        val counts = listOf(counted("A", "Duo One & Guest Two", 5), counted("B", "Duo One", 9), counted("C", "Other Act, Someone", 20))
        val artists = MostPlayed.artistsOf(counts) { if (it.startsWith("Duo One")) "Duo One" else "" }
        assertEquals(listOf("Other Act" to 20, "Duo One" to 14), artists.map { it.name to it.plays })
    }
}
