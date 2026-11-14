package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** What a listening history adds up to (the counts the DJ's sets and facts are made from). */
class TasteRulesTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 14)
    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).toEpochSecond(zone)
    private val now = at(2026, 11, 14)
    private fun count(vararg plays: Heard, hour: Int = 12, moodOf: (Pair<String, String>) -> String? = { null }) =
        TasteRules.count(plays.toList(), now, hour, today, zone, moodOf)
    private val song = statsKey("Yellow", "Coldplay")

    @Test fun aSongsPlaysItsFirstAndItsLast() {
        val c = count(Heard(at(2024, 3, 1), "Coldplay", "Yellow"), Heard(at(2026, 11, 10), "Coldplay", "Yellow"),
            Heard(at(2025, 6, 1), "Coldplay", "Yellow"), Heard(at(2026, 1, 1), "Muse", "Starlight"))
        assertEquals(4, c.total)
        assertEquals(3, c.count[song])
        assertEquals(at(2024, 3, 1), c.first[song])
        assertEquals(at(2026, 11, 10), c.last[song])
        assertEquals(at(2024, 3, 1), c.since)
        assertEquals("as it was named the first time it's met", at(2024, 3, 1), c.names.getValue(song).t)
        assertEquals(mapOf(2024 to 1, 2025 to 1, 2026 to 1), c.years.mapValues { it.value[song] })
    }

    @Test fun aPlayWithNoTimeIsLeftOut() {
        val c = count(Heard(0, "Coldplay", "Yellow"), Heard(-5, "Coldplay", "Yellow"))
        assertEquals(0, c.total)
        assertTrue(c.count.isEmpty())
        assertEquals(Long.MAX_VALUE, c.since)
    }

    @Test fun oneSongHoweverItsArtistIsWritten() {
        val c = count(Heard(at(2026, 11, 1), "K.Flay", "Blood in the Cut"), Heard(at(2026, 11, 2), "KFlay", "Blood in the Cut"))
        assertEquals(1, c.count.size)
        assertEquals(2, c.count.values.single())
    }

    @Test fun theLastThirtyDaysAndTheLastNinety() {
        val c = count(Heard(now - 29 * TasteRules.DAY, "Coldplay, Rihanna", "Yellow"), Heard(now - 31 * TasteRules.DAY, "Coldplay", "Yellow"),
            Heard(now - 89 * TasteRules.DAY, "Coldplay", "Yellow"), Heard(now - 91 * TasteRules.DAY, "Coldplay", "Yellow"))
        assertEquals(1, c.recent.values.sum())
        assertEquals("the lead artist, for 90 days", mapOf("Coldplay" to 3), c.artists)
        assertEquals(mapOf("Coldplay" to 4), c.artistAll)
        assertEquals(mapOf("Coldplay" to 1), c.artistMonth)
        assertEquals(now - 91 * TasteRules.DAY, c.artistFirst["Coldplay"])
    }

    @Test fun aroundThisHourGoesRoundMidnight() {
        assertTrue(TasteRules.aroundHour(23, 0))
        assertTrue(TasteRules.aroundHour(1, 0))
        assertTrue(TasteRules.aroundHour(0, 23))
        assertFalse(TasteRules.aroundHour(2, 0))
        assertFalse(TasteRules.aroundHour(21, 23))
        val c = count(Heard(at(2026, 10, 1, 23, 30), "Coldplay", "Yellow"), Heard(at(2026, 10, 2, 1, 59), "Coldplay", "Yellow"),
            Heard(at(2026, 10, 3, 2, 0), "Coldplay", "Yellow"), Heard(at(2025, 10, 1, 0, 0), "Coldplay", "Yellow"), hour = 0)
        assertEquals("the one over a year ago and the one at two aren't counted", 2, c.hour[song])
        assertEquals(1, c.hours[23]); assertEquals(1, c.hours[1]); assertEquals(1, c.hours[2]); assertEquals(1, c.hours[0])
    }

    @Test fun onThisDayInPastYearsIsWithinThreeDays() {
        val c = count(Heard(at(2025, 11, 12), "Coldplay", "Yellow"), Heard(at(2025, 11, 17), "Coldplay", "Yellow"),
            Heard(at(2025, 11, 18), "Coldplay", "Yellow"), Heard(at(2023, 11, 14), "Coldplay", "Yellow"), Heard(at(2026, 11, 13), "Coldplay", "Yellow"))
        assertEquals(2, c.dayAgo[1]?.get(song))
        assertEquals(1, c.dayAgo[3]?.get(song))
        assertNull("this year isn't a year ago", c.dayAgo[0])
    }

    @Test fun theDaysOfTheLastWeekASongWasPlayedOnAndThisMonthsPlays() {
        val c = count(Heard(at(2026, 11, 14, 8), "Coldplay", "Yellow"), Heard(at(2026, 11, 14, 9), "Coldplay", "Yellow"),
            Heard(at(2026, 11, 12), "Coldplay", "Yellow"), Heard(at(2026, 11, 9), "Coldplay", "Yellow"),
            Heard(at(2026, 11, 2), "Coldplay", "Yellow"), Heard(at(2026, 10, 31), "Coldplay", "Yellow"))
        assertEquals(3, c.days[song]?.size)
        assertEquals(5, c.monthPlays)
    }

    @Test fun moodsOfWhatWasPlayedAndASongIsAskedAboutOnce() {
        var asked = 0
        val c = count(Heard(at(2026, 11, 10, 12), "Coldplay", "Yellow"), Heard(at(2026, 9, 1, 18), "Coldplay", "Yellow"),
            Heard(at(2026, 11, 11, 13), "Muse", "Starlight"), Heard(at(2026, 11, 12, 12), "Muse", "Starlight"), Heard(at(2026, 11, 13, 12), "Muse", "Starlight")) {
            asked++; if (it == song) "Happy" else null
        }
        assertEquals("each song once, the one with no mood too", 2, asked)
        assertEquals(listOf("Happy", "Happy"), c.moodPlays.map { it.mood })
        assertEquals("${song.first}\t${song.second}", c.moodPlays[0].song)
        assertEquals(mapOf("Happy" to 1), c.moodMonth)
        assertEquals(mapOf("Happy" to 1), c.moodHour)
    }
}
