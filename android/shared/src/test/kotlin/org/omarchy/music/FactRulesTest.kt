package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When something about your listening is worth the DJ telling you: each fact's own bar. */
class FactRulesTest {
    private val day = FactRules.DAY
    private val year = FactRules.YEAR
    private val now = 1_800_000_000L

    @Test fun yourFiveMostPlayedArtistsHaveAPlace() {
        val ranks = FactRules.artistRanks(mapOf("A" to 5, "B" to 90, "C" to 40, "D" to 30, "E" to 20, "F" to 10))
        assertEquals(1 to 90, ranks["b"])
        assertEquals(5 to 10, ranks["f"])
        assertNull("the sixth has none", ranks["a"])
        assertTrue(FactRules.rankTold(20)); assertFalse(FactRules.rankTold(19))
    }

    @Test fun theMonthsTopMoodNeedsEnoughPlays() {
        assertEquals("Chill", FactRules.topMood(mapOf("Chill" to 15, "Happy" to 3)))
        assertNull(FactRules.topMood(mapOf("Chill" to 14, "Happy" to 3)))
        assertNull(FactRules.topMood(emptyMap()))
    }

    @Test fun mostlyOneMoodMeansAQuarterOfTwentyPlays() {
        assertEquals("Chill" to 50, FactRules.moodShare(mapOf("Chill" to 10, "Happy" to 6, "Dark" to 4)))
        assertNull("too few plays", FactRules.moodShare(mapOf("Chill" to 10, "Happy" to 9)))
        assertNull("no mood stands out", FactRules.moodShare(mapOf("a" to 6, "b" to 6, "c" to 6, "d" to 6, "e" to 6)))
    }

    @Test fun anArtistIsAnOldFriendOrAFind() {
        assertEquals("since", FactRules.artistSince(now, now - 3 * year - day, 0))
        assertEquals("new", FactRules.artistSince(now, now - 10 * day, 10))
        assertNull("found lately, but barely played", FactRules.artistSince(now, now - 10 * day, 9))
        assertNull("neither new nor old", FactRules.artistSince(now, now - year, 50))
    }

    @Test fun theTimeSpentWithASong() {
        assertEquals("hours" to 2.0, FactRules.timeWith(36, 200.0))
        assertEquals("minutes" to 30.0, FactRules.timeWith(10, 180.0))
        assertNull("too few plays", FactRules.timeWith(7, 600.0))
        assertNull("length not known", FactRules.timeWith(50, 0.0))
    }

    @Test fun aSongsFirstPlay() {
        assertEquals("first", FactRules.firstPlay(now, now - 2 * year - day, 1))
        assertEquals("crush", FactRules.firstPlay(now, now - 5 * day, 5))
        assertNull(FactRules.firstPlay(now, now - 5 * day, 4))
        assertNull("today", FactRules.firstPlay(now, now - 3600, 9))
        assertNull(FactRules.firstPlay(now, now - 22 * day, 9))
    }

    @Test fun aSongsSkips() {
        assertEquals("never", FactRules.skipsOf(0, 8))
        assertNull(FactRules.skipsOf(0, 7))
        assertEquals("skipped", FactRules.skipsOf(4, 3))
        assertNull("heard out as often", FactRules.skipsOf(4, 4))
        assertNull(FactRules.skipsOf(3, 0))
    }

    @Test fun whenYouListen() {
        val hours = IntArray(24).also { it[1] = 30; it[7] = 60; it[20] = 110 }
        assertEquals(20, FactRules.peakHour(hours))
        assertNull(FactRules.peakHour(IntArray(24).also { it[9] = 49 }))
        assertEquals(15, FactRules.nightShare(hours, 200))
        assertEquals(30, FactRules.morningShare(hours, 200))
        assertNull("too few plays to say", FactRules.nightShare(hours, 199))
        assertNull("under a tenth", FactRules.nightShare(hours, 400))
        assertNull("under a quarter", FactRules.morningShare(hours, 400))
    }

    @Test fun howFarBackYourListeningGoes() {
        assertEquals(3, FactRules.yearsBack(now, now - 3 * year - day))
        assertNull(FactRules.yearsBack(now, now - year))
        assertNull("not known", FactRules.yearsBack(now, 0))
    }

    @Test fun yourBiggestYear() {
        assertEquals(2024 to 900, FactRules.busiestYear(mapOf(2023 to 600, 2024 to 900)))
        assertNull("nothing to compare with", FactRules.busiestYear(mapOf(2024 to 900)))
        assertNull(FactRules.busiestYear(mapOf(2023 to 100, 2024 to 499)))
    }

    @Test fun yourCollectionCounted() {
        assertEquals(mapOf("A" to 2, "B" to 1), FactRules.songsByArtist(listOf("A", "B", "A", "", "Unknown Artist")))
        assertEquals(mapOf("Rock" to 2, "Jazz" to 1), FactRules.genreCounts(listOf("Rock", "Jazz", "Rock", " ", "unknown genre")))
        assertEquals(10, FactRules.libraryHours(36_000.0)); assertEquals(12, FactRules.libraryHours(45_000.0))
        assertNull(FactRules.libraryHours(35_999.0))
        assertEquals(2, FactRules.addedLately(listOf(0.0, (now - day).toDouble(), (now - 29 * day).toDouble(), (now - 31 * day).toDouble()), now))
        assertTrue(FactRules.unplayedTold(200, 10)); assertFalse(FactRules.unplayedTold(199, 50)); assertFalse(FactRules.unplayedTold(900, 9))
    }

    @Test fun heardOutAndLongUnheard() {
        assertEquals(75, FactRules.heardOutPercent(25, 75))
        assertNull(FactRules.heardOutPercent(20, 79))
        assertTrue(FactRules.longUnheard(now, now - 181 * day))
        assertFalse(FactRules.longUnheard(now, now - 179 * day))
        assertFalse("never played", FactRules.longUnheard(now, 0))
    }
}
