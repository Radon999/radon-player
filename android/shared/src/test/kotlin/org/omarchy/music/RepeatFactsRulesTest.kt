package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.omarchy.music.RepeatFactsRules.Play

/** Repeats for the DJ and Wrapped, and the ones an imported history shows. */
class RepeatFactsRulesTest {
    @Test fun whenASongStartingOverIsWorthAWord() {
        assertEquals("three in a row", "run", RepeatFactsRules.remark(3, 3, 0, true))
        assertNull(RepeatFactsRules.remark(4, 4, 0, true))
        assertEquals("its tenth repeat ever", "milestone", RepeatFactsRules.remark(2, 10, 6, true))
        assertEquals("past its longest run, said once", "record", RepeatFactsRules.remark(7, 31, 6, true))
        assertNull(RepeatFactsRules.remark(9, 33, 6, true))
        assertEquals("a long run: something else is offered", "nudge", RepeatFactsRules.remark(8, 32, 20, true))
        assertNull("not with the nudge off", RepeatFactsRules.remark(8, 32, 20, false))
    }

    @Test fun aHistoryShowsTheSameSongPlayedAgainTheMomentItEnded() {
        val plays = listOf(
            Play("Blood", "K.Flay", 1000, 200.0),      // began at 1000, ran 200 s
            Play("Blood", "KFlay", 1201, 200.0),       // began as it ended: a repeat
            Play("Blood", "K.Flay", 1402, 40.0),       // and again (cut short this time)
            Play("Blood", "K.Flay", 5000, 200.0),      // an hour later: picked again, not a repeat
            Play("Other", "Someone", 5201, 180.0),
            Play("Other", "Someone", 5381, 5.0))       // again, at once
        assertEquals(listOf("Blood" to 1, "Blood" to 2, "Other" to 1), RepeatFactsRules.inferred(plays).map { it.first.title to it.second })
        // a history that keeps when each play stopped (Spotify's)
        val ends = listOf(Play("Blood", "K.Flay", 1200, 200.0), Play("Blood", "K.Flay", 1401, 200.0), Play("Blood", "K.Flay", 1900, 200.0))
        assertEquals(1, RepeatFactsRules.inferred(ends).size)
        // scrobbles: only when each began
        val scrobbles = listOf(Play("Blood", "K.Flay", 1000), Play("Blood", "K.Flay", 1200), Play("Blood", "K.Flay", 9000), Play("Blood", "K.Flay", 9010))
        assertEquals("within ten minutes, and not seconds apart", 1, RepeatFactsRules.inferred(scrobbles).size)
    }

    @Test fun twoServicesHoldingTheSamePlaysCountThemOnce() {
        val a = listOf(Play("Blood", "K.Flay", 1000, 200.0), Play("Blood", "K.Flay", 1200, 200.0), Play("Blood", "K.Flay", 1400, 200.0))
        val scrobbled = listOf(Play("Blood", "K.Flay", 1000), Play("Blood", "K.Flay", 1200), Play("Blood", "K.Flay", 1400))
        val (songs, hours) = RepeatLoopRules.fromHistory(listOf(a, scrobbled), { "2025-03-01" }, { 21 }, "")
        assertEquals(1, songs.size)
        assertEquals(2, songs.values.first().n); assertEquals(2, songs.values.first().best)
        assertEquals(mapOf("2025-03|21" to 2), hours)
        assertEquals("only days before the app's own count began", 0, RepeatLoopRules.fromHistory(listOf(a), { "2025-03-01" }, { 21 }, "2025-03-01").first.size)
    }

    @Test fun aStretchOfTimeAddedUp() {
        var s = RepeatLoopRules.Saved()
        val a = Track("1", title = "1997", artist = "Saint Motel", duration = 240.0); val b = Track("2", title = "Blood", artist = "K.Flay", duration = 200.0)
        repeat(6) { s = RepeatLoopRules.add(s, a, "2026-03-07", 1, 21) }
        s = RepeatLoopRules.add(s, b, "2026-04-02", 2, 9)
        s = RepeatLoopRules.add(s, b, "2025-12-30", 3, 9)
        val y = RepeatFactsRules.summary(s, "2026-01-01", "2027-01-01", 15)!!
        assertEquals(7, y.total); assertEquals(2, y.songs); assertEquals(6 * 240.0 + 200.0, y.secs, 0.01)
        assertEquals(listOf("1997" to 6, "Blood" to 1), y.top.map { it.first.track.title to it.second })
        assertEquals("1997" to 6, y.bestRun!!.let { it.first.track.title to it.second })
        assertEquals(Triple("2026-03-07", 6, "1997"), y.topDay)
        assertEquals("a Saturday", 6, y.weekdays[5]); assertEquals(6, y.hours[21])
        assertEquals(listOf("2026-03" to "1997", "2026-04" to "Blood"), y.months.map { it.first to it.second.track.title })
        assertEquals("The Faithful", y.type!!.first)
        assertNull(RepeatFactsRules.summary(s, "2024-01-01", "2025-01-01", 15))
        // the ones from your history beside the counted ones
        val both = RepeatLoopRules.all(s.copy(imports = mapOf(RepeatLoopRules.key(b) to RepeatLoopRules.Loop(b, 4, 3, 0, mapOf("2024-05-05" to 4), "2024-05-05"))))
        assertEquals(6, both.songs.getValue(RepeatLoopRules.key(b)).n); assertEquals(3, both.songs.getValue(RepeatLoopRules.key(b)).best)
    }

    @Test fun aSongHiddenFromStatsLeavesWrappedAndTheNextTakesItsPlace() {
        var s = RepeatLoopRules.Saved()
        val a = Track("1", title = "1997", artist = "Saint Motel", duration = 240.0); val b = Track("2", title = "Blood", artist = "K.Flay", duration = 200.0)
        repeat(6) { s = RepeatLoopRules.add(s, a, "2026-03-07", 1, 21) }
        s = RepeatLoopRules.add(s, b, "2026-03-09", 2, 9)
        val y = RepeatFactsRules.summary(s, "2026-01-01", "2027-01-01", 15) { it.track.title == "1997" }!!
        assertEquals(listOf("Blood" to 1), y.top.map { it.first.track.title to it.second })
        assertEquals("March's obsession is the next one", listOf("2026-03" to "Blood"), y.months.map { it.first to it.second.track.title })
        assertEquals(listOf("K.Flay" to 1), y.artists)
        assertNull("nothing left", RepeatFactsRules.summary(s, "2026-01-01", "2027-01-01", 15) { true })
    }
}
