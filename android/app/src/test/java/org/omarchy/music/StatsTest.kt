package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    private fun play(t: Long, artist: String, title: String, ms: Long = 200_000) = SpotifyHistory.Play(t, artist, title, "", ms)

    @Test fun addsEveryScrobbleAndEveryImportedPlay() {
        val scrobbles = List(10) { play(1000L + it, "Artist", "Song") } + List(2) { play(5000L + it, "Artist", "Other") }
        // One very long play (the song looped) no longer makes the others too short to count.
        val spotify = listOf(play(1, "Artist", "Song (Remastered)", 682_000), play(2, "Artist", "Song", 234_000), play(3, "Band", "Hit"))
        val (songs, artists) = Stats.combine(scrobbles, spotify, emptyList())
        assertEquals(listOf("Song" to 12, "Other" to 2, "Hit" to 1), songs.map { it.title to it.plays })
        assertEquals(10, songs[0].lastfm)
        assertEquals(2, songs[0].spotify)
        assertEquals(listOf("Artist" to 14, "Band" to 1), artists.map { it.name to it.plays })
    }

    @Test fun twoSongsOfOneNameCountedApart() {
        // Imagine Dragons' two "Yesterday"s: 30 plays on Evolve, 4 on the EP sessions', 3 with no album (the main one's)
        fun on(album: String) = SpotifyHistory.Play(1, "Imagine Dragons", "Yesterday", album, 200_000)
        val scrobbles = List(30) { on("Evolve") } + List(4) { on("It's Time EP Sessions") } + List(3) { on("") }
        val versions = mapOf(statsKey("Yesterday", "Imagine Dragons") to
            listOf(listOf(PlaysApart.albumKey("It's Time EP Sessions")), listOf(PlaysApart.albumKey("Evolve"))))
        val (songs, _, apart) = Stats.combine(scrobbles, emptyList(), emptyList(), versions = versions)
        assertEquals(listOf("Evolve" to 33, "It's Time EP Sessions" to 4), songs.map { it.album to it.plays })
        assertEquals("the main one first", PlaysApart.albumKey("Evolve"), apart.values.single()[0][0])
        // and as the screens look them up
        val counts = songs.associate { PlaysApart.key(songKey(Track("", title = it.title, artist = it.artist)), it.title, it.artist, it.album, apart) to it.plays }
        assertEquals(33, counts[PlaysApart.key(songKey(Track("a", title = "Yesterday", artist = "Imagine Dragons", album = "Evolve")), "Yesterday", "Imagine Dragons", "Evolve", apart)])
        assertEquals(4, counts[PlaysApart.key(songKey(Track("b", title = "Yesterday", artist = "Imagine Dragons", album = "It's Time EP Sessions")), "Yesterday", "Imagine Dragons", "It's Time EP Sessions", apart)])
    }

    @Test fun oneArtistWrittenTwoWays() {
        val (songs, _) = Stats.combine(listOf(play(1, "K.Flay", "Blood in the Cut")), listOf(play(2, "kflay", "Blood In The Cut")), emptyList())
        assertEquals(listOf(2), songs.map { it.plays })
    }

    @Test fun withoutLastFmCountsThePlaysHere() {
        val (songs, artists) = Stats.combine(emptyList(), emptyList(),
            listOf(Track("x", title = "Here", artist = "Me, You") to 4))
        assertEquals(listOf("Here" to 4), songs.map { it.title to it.here })
        assertEquals(listOf("Me" to 4), artists.map { it.name to it.plays })
    }
}
