package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KindRulesTest {
    private val day = KindRules.DAY
    private val now = 1_800_000_000L

    @Test fun rediscoverNeedsThreePlaysAndThreeMonthsAway() {
        assertTrue(KindRules.shelved(3, now - 91 * day, now, false))
        assertFalse("two plays aren't a lot", KindRules.shelved(2, now - 200 * day, now, false))
        assertFalse("played 89 days ago", KindRules.shelved(9, now - 89 * day, now, false))
        assertFalse("exactly 90 days isn't more than three months", KindRules.shelved(9, now - 90 * day, now, false))
        assertFalse("among the songs played lately", KindRules.shelved(9, now - 200 * day, now, true))
        assertTrue("when it was last played isn't known: long ago", KindRules.shelved(5, 0, now, false))
    }

    @Test fun aThrowbackSongWasntPlayedForTwoMonths() {
        assertTrue(KindRules.longUnplayed(now - 61 * day, now))
        assertFalse(KindRules.longUnplayed(now - 60 * day, now))
        assertFalse(KindRules.longUnplayed(now - day, now))
    }

    @Test fun notSkippedIsHeardOutAndNeverSkipped() {
        val rows = listOf(Triple("heard twice", 0, 2), Triple("skipped once", 1, 5), Triple("neither", 0, 0), Triple("heard once", 0, 1))
        assertEquals(listOf("heard twice", "heard once"), KindRules.heardOut(rows).map { it.first })
    }

    @Test fun aSongHeardOutMoreAndSkippedLessIsLikelier() {
        assertTrue(KindRules.heardWeight(4, 0.0) > KindRules.heardWeight(2, 0.0))
        assertTrue(KindRules.heardWeight(4, 0.0) > KindRules.heardWeight(4, 0.5))
        assertEquals(4.0, KindRules.heardWeight(4, 0.0), 1e-9)
        assertEquals(2.0, KindRules.heardWeight(4, 1.0), 1e-9)
    }

    @Test fun secondChancesAreSkippedThreeTimesAndAtLeastAsOftenAsHeard() {
        val rows = listOf(Triple("often skipped", 4, 1), Triple("as often as heard", 3, 3), Triple("skipped twice", 2, 0),
            Triple("heard more", 3, 4), Triple("only skipped", 7, 0))
        assertEquals(listOf("often skipped", "as often as heard", "only skipped"), KindRules.secondChances(rows))
    }

    private fun song(n: Int, artist: String) = Track("kind-$n", "ytm", "Song $n", artist)

    @Test fun oneHitWondersAreArtistsWithASongOrTwo() {
        val songs = listOf(song(1, "Solo"), song(2, "Pair"), song(3, "pair, Guest"), song(4, "Many"), song(5, "Many"), song(6, "MANY"))
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), KindRules.fewOfAnArtist(songs).map { it.title }.sorted())
    }

    @Test fun aDecadeIsTheYearsTen() {
        assertEquals(1980, KindRules.decadeOf(1985))
        assertEquals(2000, KindRules.decadeOf(2000))
        assertEquals(2020, KindRules.decadeOf(2029))
        assertNull("no year", KindRules.decadeOf(0))
        assertNull(KindRules.decadeOf(1949))
        assertNull(KindRules.decadeOf(2101))
        assertEquals("the 80s", KindRules.decadeName(1980))
        assertEquals("the 50s", KindRules.decadeName(1950))
        assertEquals("the 2000s", KindRules.decadeName(2000))
        assertEquals("the 2020s", KindRules.decadeName(2020))
    }

    @Test fun onlyDecadesWithEightSongsCount() {
        val sample = (1..8).map { "eighties $it" to 1980 } + (1..7).map { "nineties $it" to 1990 }
        val by = KindRules.decades(sample)
        assertEquals(setOf(1980), by.keys)
        assertEquals(8, by.getValue(1980).size)
    }

    @Test fun anAlbumsOrderIsByTrackNumberWithoutTheUnnumbered() {
        assertEquals(listOf("one", "two", "five"), KindRules.albumOrder(listOf("five" to 5, "none" to 0, "one" to 1, "two" to 2)))
        assertNull("two numbered songs aren't enough", KindRules.albumOrder(listOf("a" to 1, "b" to 2, "c" to 0, "d" to 0)))
    }

    @Test fun runsOfThreeAreInARowAndNeverHoldASongThatsOut() {
        val album = listOf(1, 2, 3, 4, 5)
        assertEquals(listOf(listOf(1, 2, 3), listOf(2, 3, 4), listOf(3, 4, 5)), KindRules.runsOfThree(album) { false })
        assertEquals(listOf(listOf(3, 4, 5)), KindRules.runsOfThree(album) { it == 2 })
        assertEquals(emptyList<List<Int>>(), KindRules.runsOfThree(album) { it == 3 })
        assertEquals(emptyList<List<Int>>(), KindRules.runsOfThree(listOf(1, 2)) { false })
    }

    @Test fun theSpotlightWaitsForAllButThreeArtistsFortyAtMost() {
        val history = (1..60).map { "artist $it" }
        assertEquals((54..60).map { "artist $it" }.toSet(), KindRules.spotlightWaits(history, 10))
        assertEquals(40, KindRules.spotlightWaits(history, 100).size)
        assertEquals(emptySet<String>(), KindRules.spotlightWaits(history, 3))
        assertEquals(emptySet<String>(), KindRules.spotlightWaits(history, 0))
    }

    @Test fun unreleasedGoesByThePath() {
        assertTrue(KindRules.unreleased("/music/Artist/Unreleased/song.flac"))
        assertTrue(KindRules.unreleased("C:\\Music\\UNRELEASED stuff\\a.mp3"))
        assertFalse(KindRules.unreleased("/music/Artist/Released/song.flac"))
    }
}
