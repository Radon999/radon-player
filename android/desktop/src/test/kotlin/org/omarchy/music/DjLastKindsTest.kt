package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The kinds of set that go by every song you've played and by your own songs' genres, playing from everything:
 *  rediscover, most played, your genre. The songs played here and the songs saved are the test's own, put in for the
 *  test and taken out after it. */
class DjLastKindsTest {
    private val day = 86_400L

    private fun song(group: String, i: Int) = Track("lastkinds-${group.lowercase()}-$i", "ytm", "$group song $i", "$group band $i")

    /** With songs played here (each with how often) and a listening history of the test's own. */
    private fun <T> withPlayed(played: List<Pair<Track, Int>>, history: List<Heard>, block: () -> T): T {
        Harness.ctx
        assertTrue("nothing is counted for Most played in a test", Stats.songs.isEmpty())
        Harness.ui { for ((t, n) in played) Music.plays.items[t.id] = PlayEntry(n, 0.0, t) }
        Dj.playFrom(null, null)   // from everything
        Dj.historyStandIn = { history }
        Dj.retaste()
        try { return block() } finally {
            Dj.historyStandIn = null
            Harness.ui { for ((t, _) in played) Music.plays.items.remove(t.id) }
            Dj.retaste()
        }
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

    /** Songs played a lot but not for months, songs played a lot these days, songs played twice long ago, and songs
     *  played here once that the history knows nothing of. */
    private fun <T> withOldAndNew(block: () -> T): T {
        val now = System.currentTimeMillis() / 1000
        val shelved = (1..8).map { song("Shelved", it) }; val current = (1..8).map { song("Current", it) }
        val twice = (1..4).map { song("Twice", it) }; val unheard = (1..4).map { song("Unheard", it) }
        val history = ArrayList<Heard>()
        for (t in shelved) for (k in 0..4) history += Heard(now - (100 + 10 * k) * day, t.artist, t.title)
        for (t in current) for (k in 2..6) history += Heard(now - k * day, t.artist, t.title)
        for (t in twice) for (k in 0..1) history += Heard(now - (200 + k) * day, t.artist, t.title)
        return withPlayed(shelved.map { it to 5 } + current.map { it to 5 } + twice.map { it to 2 } + unheard.map { it to 1 }, history, block)
    }

    @Test fun rediscoverIsWhatYouPlayedALotButNotForThreeMonths() = withOldAndNew { only("rediscover", "Shelved") }

    @Test fun mostPlayedFromEverythingIsOnlySongsYourListeningHasPlaysOf() = withOldAndNew { only("mostplayed", "Shelved", "Current", "Twice") }

    @Test fun yourGenreIsYourOwnSongsOfAGenreYouPlayAndNeverOneWithTooFewSongs() {
        Harness.ctx
        fun saved(group: String, i: Int, genre: String) = LibEntry(id = "lastkinds-${group.lowercase()}-$i", source = "ytm",
            title = "$group song $i", artist = "$group band $i", file = "/music/test/lastkinds-${group.lowercase()}-$i.flac",
            format = "flac", added = 1.0, genre = genre)
        val gaze = (1..8).map { saved("Gaze", it, "Shoegaze") }; val polka = (1..2).map { saved("Polka", it, "Polka") }
        val now = System.currentTimeMillis() / 1000
        val history = (gaze + polka).flatMap { e -> (2..4).map { k -> Heard(now - k * day, e.artist, e.title) } }
        Harness.ui { for (e in gaze + polka) Music.lib.items[e.id] = e }
        try {
            withPlayed(emptyList(), history) {
                only("genre", "Gaze") { b -> assertTrue(b.line, b.line.lowercase().contains("shoegaze")) }
            }
        } finally { Harness.ui { for (e in gaze + polka) Music.lib.items.remove(e.id) }; Dj.retaste() }
    }
}
