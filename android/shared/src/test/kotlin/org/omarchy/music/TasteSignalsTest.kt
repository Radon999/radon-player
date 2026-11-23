package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The taste signals: habits from your history, and autoplay's order. */
class TasteSignalsTest {
    private val day = 86_400L
    private val now = 2_000_000_000L

    @Test fun whatYouPlayAfterWhat() {
        val plays = ArrayList<Heard>()
        var t = now - 10 * day
        repeat(5) { plays += Heard(t, "Lauv", "Paris in the Rain"); plays += Heard(t + 200, "Keane", "Somewhere Only We Know"); t += day }
        plays += Heard(t, "Lauv", "Paris in the Rain"); plays += Heard(t + 5000, "Sody", "Nothing")   // over 20 minutes: not after
        val h = HabitRules.count(plays, now) { 12 }
        val after = h.after.getValue(SignalRules.songKey("Paris in the Rain", "Lauv"))
        assertEquals(SignalRules.songKey("Somewhere Only We Know", "Keane"), after.first().first)
        assertEquals(5, after.first().second)
        assertTrue(after.none { it.first.endsWith("sody") })
        // Played in the same sittings: close
        assertTrue(h.near.getValue(statsKey("", "Lauv").second).any { it.first == statsKey("", "Keane").second })
    }

    @Test fun phases() {
        val plays = ArrayList<Heard>()
        repeat(20) { plays += Heard(now - 30 * day + it * 3600, "New Band", "Song $it") }          // new love
        repeat(60) { plays += Heard(now - 400 * day + it * 3600, "Old Band", "Song $it") }        // faded
        val h = HabitRules.count(plays, now) { 12 }
        assertEquals(HabitRules.NEW_LOVE, h.phase[statsKey("", "New Band").second])
        assertEquals(HabitRules.FADED, h.phase[statsKey("", "Old Band").second])
    }

    @Test fun yourSimilarArtistsBlendLastFmAndYourHabits() {
        // Coldplay, played in the same sittings, moves up past Keane; Sody (only from your sittings) is never new to you
        val s = HabitRules.similar(listOf("Keane" to 0.8, "Coldplay" to 0.6), listOf(statsKey("", "Coldplay").second to 0.8, statsKey("", "Sody").second to 0.9))
        assertEquals("Coldplay", s.first().first)
        assertTrue(s.none { statsKey("", it.first).second == statsKey("", "Sody").second })
    }

    @Test fun autoplayLeavesOutWhatYouSaidNoTo() {
        val cs = listOf(
            AutoplayRules.Cand("a", "x", 0, thumb = -1),
            AutoplayRules.Cand("b", "y", 1, blocked = true),
            AutoplayRules.Cand("c", "z", 2, lastPlayed = now - 600),
            AutoplayRules.Cand("d", "w", 3, early = 2),
            AutoplayRules.Cand("e", "v", 4, skips = 5, full = 1),
            AutoplayRules.Cand("f", "u", 5),
        )
        assertEquals(listOf("f"), AutoplayRules.rank(cs, now).map { it.key })
    }

    @Test fun autoplayFavoursYourHabitsAndSpacesArtists() {
        val cs = listOf(
            AutoplayRules.Cand("a1", "a", 0), AutoplayRules.Cand("a2", "a", 1), AutoplayRules.Cand("a3", "a", 2),
            AutoplayRules.Cand("b1", "b", 3), AutoplayRules.Cand("c1", "c", 4, after = 5, thumb = 1),
        )
        val order = AutoplayRules.rank(cs, now).map { it.key }
        assertEquals("c1", order.first())
        for (i in 1 until order.size) assertFalse(order[i].first() == order[i - 1].first() && order.count { it.first() == order[i].first() } < 3)
    }

    @Test fun soundCloseness() {
        assertEquals(1.0, AutoplayRules.soundClose(120.0, -8.0, 120.0, -8.0)!!, 1e-9)
        assertTrue(AutoplayRules.soundClose(120.0, -8.0, 60.0, -8.0)!! > 0.99)   // half time is the same beat
        assertTrue(AutoplayRules.soundClose(70.0, -20.0, 170.0, -6.0)!! < 0.3)
        assertNull(AutoplayRules.soundClose(0.0, -8.0, 120.0, -8.0))
    }

    @Test fun outcomesAndSuccess() {
        assertEquals("kept", SignalRules.outcome("skipped", "kept"))
        assertEquals("kept", SignalRules.outcome("kept", "skipped"))
        assertNull(SignalRules.introSuccess(listOf("kept")))
        assertNotNull(SignalRules.introSuccess(listOf("skipped", "skipped")))
        assertEquals(0.0, SignalRules.introSuccess(listOf("skipped", "skipped"))!!, 1e-9)
        assertEquals(1, SignalRules.thumb(SignalEntry(up = 5, down = 2)))
        assertEquals(2, SignalRules.earlyLately(SignalEntry(early = listOf(now - day, now - 2 * day, now - 100 * day)), now))
    }
}
