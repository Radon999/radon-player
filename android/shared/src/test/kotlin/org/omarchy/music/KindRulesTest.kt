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

    @Test fun artistsForYouAreTheOnesMostOfYourArtistsLeadToAndNeverOnesYouPlay() {
        val similar = mapOf(
            "Big" to listOf("Shared" to 0.9, "Only Big's" to 0.8, "Known Band" to 1.0),
            "Small" to listOf("Shared" to 0.9, "Only Small's" to 0.8),
        )
        val got = KindRules.artistsForYou(mapOf("Big" to 100, "Small" to 5), emptyMap(), emptyList(), setOf(norm("Known Band"))) { similar[it].orEmpty() }
        assertEquals(listOf("Shared", "Only Big's", "Only Small's"), got.map { it.first })
        assertEquals("both of yours lead to it", setOf("Big", "Small"), got[0].second.toSet())
        assertEquals(listOf("Big"), got[1].second)
    }

    @Test fun whatYouPlayThisMonthCountsDouble() {
        val similar = mapOf("Old love" to listOf("From the old" to 1.0), "New love" to listOf("From the new" to 1.0))
        val got = KindRules.artistsForYou(mapOf("Old love" to 30, "New love" to 20), mapOf("New love" to 20), emptyList(), emptySet()) { similar[it].orEmpty() }
        assertEquals(listOf("From the new", "From the old"), got.map { it.first })
    }

    @Test fun withNoCountsYourArtistsStandInAndWithNoArtistsThereIsNoOne() {
        val got = KindRules.artistsForYou(emptyMap(), emptyMap(), listOf("Yours"), emptySet()) { listOf("Like $it" to 0.5) }
        assertEquals(listOf("Like Yours" to listOf("Yours")), got)
        assertEquals(emptyList<Pair<String, List<String>>>(), KindRules.artistsForYou(emptyMap(), emptyMap(), emptyList(), emptySet()) { listOf("x" to 1.0) })
    }

    @Test fun anArtistSeveralOfYoursLeadToBeatsOneOnlyABigOneDoes() {
        // Big alone brings "Big's best" (6.0) further than all three bring "Shared" added up (5.0); but three of yours
        // leading to it makes it the surer bet
        val similar = mapOf("Big" to listOf("Big's best" to 1.0, "Shared" to 0.6), "Mid" to listOf("Shared" to 0.2), "Small" to listOf("Shared" to 0.2))
        val got = KindRules.artistsForYou(mapOf("Big" to 400, "Mid" to 60, "Small" to 20), emptyMap(), emptyList(), emptySet()) { similar[it].orEmpty() }
        assertEquals(listOf("Shared", "Big's best"), got.map { it.first })
        assertEquals(setOf("Big", "Mid", "Small"), got[0].second.toSet())
    }

    @Test fun artistsYouveFallenForLeadAndOnesYouveLeftBehindBringNone() {
        val similar = mapOf("Old flame" to listOf("Like the old" to 1.0), "Steady" to listOf("Like the steady" to 1.0),
            "New crush" to listOf("Like the new" to 1.0))
        val plays = mapOf("Old flame" to 500, "Steady" to 100, "New crush" to 60)
        val phase = mapOf(statsKey("", "Old flame").second to HabitRules.FADED, statsKey("", "New crush").second to HabitRules.NEW_LOVE)
        assertEquals("without phases: the most played first", listOf("Like the old", "Like the steady", "Like the new"),
            KindRules.artistsForYou(plays, emptyMap(), emptyList(), emptySet()) { similar[it].orEmpty() }.map { it.first })
        assertEquals("the faded one's are gone; the new love's count half again (60 plays beat 100)", listOf("Like the new", "Like the steady"),
            KindRules.artistsForYou(plays, emptyMap(), emptyList(), emptySet(), phase) { similar[it].orEmpty() }.map { it.first })
    }

    @Test fun aRisingArtistBelowTheTopTwelveBringsTheirsToo() {
        val plays = (1..12).associate { "Top $it" to 1000 - it } + ("Riser" to 30)
        val phase = mapOf(statsKey("", "Riser").second to HabitRules.RISING)
        val like = { a: String -> if (a == "Riser") listOf("Like the riser" to 1.0) else emptyList<Pair<String, Double>>() }
        assertEquals(listOf("Like the riser" to listOf("Riser")), KindRules.artistsForYou(plays, emptyMap(), emptyList(), emptySet(), phase, like))
        assertTrue("not without its phase", KindRules.artistsForYou(plays, emptyMap(), emptyList(), emptySet(), similarTo = like).isEmpty())
    }

    @Test fun newSongsComeThirdAndFifthAmongTheOnesYouKnow() {
        assertEquals(listOf("k1", "k2", "n1", "k3", "n2", "k4"), KindRules.withNewOnes(listOf("k1", "k2", "k3", "k4", "k5"), listOf("n1", "n2"), 6))
        assertEquals("no new ones: the known ones", listOf("k1", "k2", "k3"), KindRules.withNewOnes(listOf("k1", "k2", "k3"), emptyList<String>(), 6))
        assertEquals("the known ones run out: the new ones follow", listOf("k1", "n1", "n2"), KindRules.withNewOnes(listOf("k1"), listOf("n1", "n2"), 6))
        assertEquals("never more than a set", 4, KindRules.withNewOnes((1..9).map { "k$it" }, listOf("n1", "n2"), 4).size)
    }

    @Test fun unreleasedGoesByThePath() {
        assertTrue(KindRules.unreleased("/music/Artist/Unreleased/song.flac"))
        assertTrue(KindRules.unreleased("C:\\Music\\UNRELEASED stuff\\a.mp3"))
        assertFalse(KindRules.unreleased("/music/Artist/Released/song.flac"))
    }
}
