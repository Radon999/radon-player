package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The kinds of set that are made from your listening, each by its own rule: given a listening history (a stand-in
 *  for Last.fm and the imports), each kind picks only the songs its rule is about. Playing from everything. */
class DjKindsTest {
    private val day = 86_400L
    private val hour = 3_600L

    /** A history as of now: songs on repeat these weeks, songs played on most days this week, songs of this hour,
     *  songs from this week a year ago, and a year two years back with its own songs. All but the hour's songs were
     *  played six hours from now on the clock, so only those are "around this hour". */
    private fun history(): List<Heard> {
        val now = System.currentTimeMillis() / 1000
        val zone = java.time.ZoneId.systemDefault()
        val out = ArrayList<Heard>()
        fun play(group: String, i: Int, t: Long) { out += Heard(t, "$group band $i", "$group song $i") }
        for (i in 1..8) for (k in 8..12) play("Repeat", i, now - k * day - 6 * hour)
        for (i in 1..5) for (k in 0..4) play("Streak", i, now - k * day - 6 * hour)
        for (i in 1..8) for (k in listOf(100, 130, 160)) play("Hour", i, now - k * day)
        val yearAgo = java.time.ZonedDateTime.now(zone).minusYears(1).minusHours(6).toEpochSecond()
        for (i in 1..6) for (k in 0..1) play("Yearago", i, yearAgo - k * 600)
        val old = java.time.ZonedDateTime.now(zone).minusYears(2).withMonth(6).withDayOfMonth(15).minusHours(6).toEpochSecond()
        for (i in 1..8) for (k in 0..5) play("Throwback", i, old - k * day)
        return out
    }

    private fun <T> withHistory(block: () -> T): T {
        Harness.ctx
        Dj.playFrom(null, null)   // from everything
        Dj.historyStandIn = { history() }
        Dj.retaste()
        try { return block() } finally { Dj.historyStandIn = null; Dj.retaste() }
    }

    /** A set of kind, made a few times over: always made, a whole set or all there is, only of the songs meant. */
    private fun only(kind: String, vararg groups: String, check: (Dj.Built) -> Unit = {}) {
        repeat(8) {
            val b = Dj.build(kind = kind)
            assertNotNull("a $kind set can be made", b)
            try {
                assertTrue("${b!!.songs.size} songs", b.songs.size in 3..Dj.SET)
                assertTrue("$kind: ${b.songs.map { it.title }}", b.songs.all { s -> groups.any { s.title.startsWith("$it song") } })
                assertEquals("no song twice", b.songs.size, b.songs.map { songKey(it) }.toSet().size)
                check(b)
            } finally { Dj.discard(b!!.id) }
        }
    }

    @Test fun onRepeatIsWhatYouPlayedMostTheseLastThirtyDays() = withHistory { only("repeat", "Repeat", "Streak") }

    @Test fun aStreakIsWhatYouPlayedOnMostDaysThisWeek() = withHistory { only("streak", "Streak") }

    @Test fun thisTimeOfDayIsWhatYouPlayAroundThisHour() = withHistory { only("hour", "Hour") }

    @Test fun onThisDayIsWhatYouPlayedThisWeekAYearAgo() = withHistory {
        only("yearago", "Yearago") { assertTrue(it.line, it.line.contains("ne year ago")) }
    }

    @Test fun aThrowbackIsAYearsMostPlayedNotPlayedLately() = withHistory {
        val year = java.time.LocalDate.now().year - 2
        only("throwback", "Throwback") { assertTrue(it.line, it.line.contains(year.toString())) }
    }

    @Test fun aSetMadeHasItsLineAndOneLetGoIsDropped() = withHistory {
        val b = Dj.build(kind = "repeat")!!
        assertEquals(SetState.SCRIPTED, Dj.stateOf(b.id))
        Dj.discard(b.id)
        assertEquals(SetState.DROPPED, Dj.stateOf(b.id))
        Dj.queuedAs(b.id, 1)
        assertEquals("a dropped set is never queued", SetState.DROPPED, Dj.stateOf(b.id))
    }

    @Test fun withNoListeningTheseKindsMakeNoSet() {
        Harness.ctx
        Dj.playFrom(null, null)
        Dj.historyStandIn = { emptyList() }
        Dj.retaste()
        try {
            for (kind in listOf("streak", "yearago", "throwback")) assertNull("no $kind set from nothing", Dj.build(kind = kind))
        } finally { Dj.historyStandIn = null; Dj.retaste() }
    }
}
