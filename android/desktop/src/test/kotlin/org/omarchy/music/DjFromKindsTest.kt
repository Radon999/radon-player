package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The kinds of set the DJ makes when it plays from songs you picked (a playlist, your library): each by its own
 *  rule, given the playlist and a listening history of the test's own. */
class DjFromKindsTest {
    private val day = 86_400L
    private val hour = 3_600L

    private fun song(group: String, i: Int, artist: String = "$group band $i") = Track("from-$group-$i", "ytm", "$group song $i", artist)

    /** The playlist: songs played long ago, songs played these days, songs of this hour, songs never played, two
     *  artists with several songs each, and files from an Unreleased folder. */
    private val played = (1..10).map { song("Played", it) }
    private val lately = (1..8).map { song("Lately", it) }
    private val hourly = (1..8).map { song("Hourly", it) }
    private val never = (1..10).map { song("Never", it) }
    private val solo = (1..6).map { song("Solo", it, "The Soloists") }
    private val pair = (1..6).map { song("Pair", it, "Twice Over") }
    private val vault = (1..6).map { Track("file-vault-$it", "file", "Vault song $it", "Vault band $it", file = "/music/Somebody/Unreleased/vault-$it.flac") }
    private val playlist = played + lately + hourly + never + solo + pair + vault

    private fun history(): List<Heard> {
        val now = System.currentTimeMillis() / 1000
        val out = ArrayList<Heard>()
        for (s in played) for (k in 200..203) out += Heard(now - k * day - 6 * hour, s.artist, s.title)
        for (s in lately) for (k in 3..5) out += Heard(now - k * day - 6 * hour, s.artist, s.title)
        for (s in hourly) for (k in listOf(100, 130, 160)) out += Heard(now - k * day, s.artist, s.title)
        return out
    }

    private fun <T> fromThePlaylist(block: () -> T): T {
        Harness.ctx
        Dj.historyStandIn = { history() }
        Dj.retaste()
        Dj.playFrom("the test playlist", playlist)
        try { return block() } finally { Dj.historyStandIn = null; Dj.retaste(); Dj.playFrom(null, null) }
    }

    private fun only(kind: String, vararg groups: String, check: (Dj.Built) -> Unit = {}) {
        repeat(8) {
            val b = Dj.build(kind = kind)
            assertNotNull("a $kind set can be made", b)
            try {
                assertEquals(kind, b!!.kind)
                assertTrue("${b.songs.size} songs", b.songs.size in 3..Dj.SET)
                assertTrue("$kind: ${b.songs.map { it.title }}", b.songs.all { s -> groups.any { s.title.startsWith("$it song") } })
                assertEquals("no song twice", b.songs.size, b.songs.map { songKey(it) }.toSet().size)
                check(b)
            } finally { Dj.discard(b!!.id) }
        }
    }

    @Test fun mostPlayedIsOnlySongsYouHavePlayed() = fromThePlaylist { only("mostplayed", "Played", "Lately", "Hourly") }

    @Test fun notPlayedYetIsOnlySongsYouNeverPlayed() = fromThePlaylist { only("unplayed", "Never", "Solo", "Pair", "Vault") }

    @Test fun aThrowbackIsSongsPlayedButNotTheseLastWeeks() = fromThePlaylist { only("throwback", "Played", "Hourly") }

    @Test fun thisTimeOfDayIsOnlyWhatYouPlayAroundThisHour() = fromThePlaylist { only("hour", "Hourly") }

    @Test fun anArtistsSetIsOneArtistWithSeveralSongs() = fromThePlaylist {
        only("artist", "Solo", "Pair") { b ->
            assertEquals("one artist: ${b.songs.map { it.artist }}", 1, b.songs.map { it.artist }.toSet().size)
            assertTrue(b.line, b.line.contains(b.songs[0].artist))
        }
    }

    @Test fun theUnreleasedCornerIsOnlyFilesFromAnUnreleasedFolder() = fromThePlaylist { only("unreleased", "Vault") }

    @Test fun aMixIsFromThePlaylistAndNamesIt() = fromThePlaylist {
        only("mix", "Played", "Lately", "Hourly", "Never", "Solo", "Pair", "Vault") { assertTrue(it.line, it.line.contains("the test playlist")) }
    }
}
