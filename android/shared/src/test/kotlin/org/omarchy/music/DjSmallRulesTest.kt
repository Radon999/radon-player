package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId

/** The DJ's smaller rules: the part of the day, which mood, new songs taken in turns, a word about the day, what it
 *  says before a song, how the songs it played are kept, and how it says numbers and times. */
class DjSmallRulesTest {
    @Test fun theHourHasItsPartOfTheDay() {
        assertEquals("this morning" to "morning", KindRules.dayPart(5))
        assertEquals("this morning" to "morning", KindRules.dayPart(11))
        assertEquals("this afternoon" to "afternoon", KindRules.dayPart(12))
        assertEquals("tonight" to "evening", KindRules.dayPart(22))
        assertEquals("at this hour" to "late night", KindRules.dayPart(23))
        assertEquals("at this hour" to "late night", KindRules.dayPart(4))
    }

    @Test fun theHeavierIsTheLikelier() {
        val top = listOf("a" to 5, "b" to 3, "c" to 2)
        assertEquals("a", KindRules.weighted(top, 0))
        assertEquals("a", KindRules.weighted(top, 4))
        assertEquals("b", KindRules.weighted(top, 5))
        assertEquals("b", KindRules.weighted(top, 7))
        assertEquals("c", KindRules.weighted(top, 8))
        assertEquals("c", KindRules.weighted(top, 9))
    }

    @Test fun newSongsAreTakenInTurns() {
        assertEquals(listOf(1, 10, 2, 20, 3), KindRules.interleave(listOf(listOf(1, 2, 3), listOf(10, 20))))
        assertEquals(listOf(7, 8), KindRules.interleave(listOf(emptyList(), listOf(7, 8))))
        assertTrue(KindRules.interleave(emptyList<List<Int>>()).isEmpty())
    }

    @Test fun aGenreSetWeighsTheTagsOfYourArtists() {
        val weight = KindRules.tagWeights(listOf(
            listOf("Indie Rock" to 100, "seen live" to 90, "rock" to 50),
            emptyList(),
            listOf("indie rock" to 60, "a" to 5, "b" to 4, "c" to 3, "d" to 2, "e" to 1, "seventh" to 99)), junk = setOf("seen live"))
        assertEquals(160, weight["indie rock"])
        assertEquals(50, weight["rock"])
        assertNull("says nothing about the music", weight["seen live"])
        assertNull("only an artist's first six", weight["seventh"])
        assertEquals(listOf("indie rock", "rock", "a"), KindRules.topThree(weight))
        assertTrue(KindRules.topThree(emptyMap<String, Int>()).isEmpty())
    }

    @Test fun aTagsSongsByYourArtistsComeFirst() {
        val pool = listOf(Track("1", title = "One", artist = "Stranger"), Track("2", title = "Two", artist = "Mine, Guest"),
            Track("3", title = "Three", artist = "Other"), Track("4", title = "Four", artist = "Mine"))
        assertEquals(listOf("2", "4", "1", "3"), KindRules.yoursFirst(pool, setOf(norm("Mine"))).map { it.id })
    }

    @Test fun aWordAboutTheDayOnlyWhenOneFits() {
        assertEquals("t-fri", KindRules.timeFlavor(DayOfWeek.FRIDAY, 17))
        assertNull(KindRules.timeFlavor(DayOfWeek.FRIDAY, 14))
        assertEquals("Saturday goes before the late hour", "t-sat", KindRules.timeFlavor(DayOfWeek.SATURDAY, 2))
        assertEquals("t-sun", KindRules.timeFlavor(DayOfWeek.SUNDAY, 9))
        assertNull(KindRules.timeFlavor(DayOfWeek.SUNDAY, 15))
        assertEquals("t-mon", KindRules.timeFlavor(DayOfWeek.MONDAY, 8))
        assertEquals("t-late", KindRules.timeFlavor(DayOfWeek.WEDNESDAY, 1))
        assertEquals("t-early", KindRules.timeFlavor(DayOfWeek.WEDNESDAY, 6))
        assertNull(KindRules.timeFlavor(DayOfWeek.WEDNESDAY, 4))
        assertNull(KindRules.timeFlavor(DayOfWeek.TUESDAY, 20))
    }

    @Test fun whatTheDjSaysBeforeASong() {
        assertTrue(KindRules.milestone(100)); assertFalse(KindRules.milestone(101)); assertFalse(KindRules.milestone(10))
        assertTrue(KindRules.topTen(0, 10)); assertTrue(KindRules.topTen(9, 50))
        assertFalse("eleventh", KindRules.topTen(10, 50)); assertFalse("not found", KindRules.topTen(-1, 50)); assertFalse("barely played", KindRules.topTen(0, 9))
        assertTrue(KindRules.neverSkipped(0, 8)); assertFalse(KindRules.neverSkipped(1, 20)); assertFalse(KindRules.neverSkipped(0, 7))
        assertTrue(KindRules.newObsession(1, 5)); assertTrue(KindRules.newObsession(21, 9))
        assertFalse("today", KindRules.newObsession(0, 9)); assertFalse(KindRules.newObsession(22, 9)); assertFalse(KindRules.newObsession(3, 4))
        assertTrue(KindRules.oldFriend(730)); assertFalse(KindRules.oldFriend(729))
    }

    @Test fun aPlayedSongIsKeptWithBothItsKeysAndReadBack() {
        val own = "a song | live" to "the artist"; val stats = "a song" to "artist"
        val entry = SetRules.recentEntry(own, stats)
        assertEquals("a song | live|the artist\ta song|artist", entry)
        assertEquals(listOf(own, stats), SetRules.recentKeys(entry))
    }

    @Test fun aLineBreakNeverSplitsAKeptSong() {
        val entry = SetRules.recentEntry("two\nlines" to "x", "two lines" to "x")
        assertFalse(entry.contains('\n'))
        assertEquals(listOf("two lines" to "x", "two lines" to "x"), SetRules.recentKeys(entry))
    }

    @Test fun aKeptLineWithoutKeysGivesNone() {
        assertTrue(SetRules.recentKeys("").isEmpty())
        assertEquals(listOf("t" to "a"), SetRules.recentKeys("t|a\tjunk"))
    }

    @Test fun numbersAndTimesAsTheDjSaysThem() {
        assertEquals("1,234", ScriptRules.num(1234)); assertEquals("25", ScriptRules.num(25))
        assertEquals("12", ScriptRules.hoursText(12.4)); assertEquals("3.5", ScriptRules.hoursText(3.5)); assertEquals("4", ScriptRules.hoursText(4.0))
        assertEquals("midnight", ScriptRules.hourName(0)); assertEquals("noon", ScriptRules.hourName(12))
        assertEquals("3 PM", ScriptRules.hourName(15)); assertEquals("9 AM", ScriptRules.hourName(9))
        assertEquals("three", ScriptRules.ordinal(3)); assertEquals("7", ScriptRules.ordinal(7))
        assertEquals("March 2021", ScriptRules.month(1_615_800_000L, ZoneId.of("UTC")))
    }

    @Test fun theDjCountsOutsidePlaysWhenThereAreAny() {
        assertEquals(40, TasteRules.outside(all = 50, here = 10, anyOutside = true))
        assertEquals(50, TasteRules.outside(all = 50, here = 10, anyOutside = false))
    }
}
