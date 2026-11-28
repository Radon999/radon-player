package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's home screen widgets: the Lyrics widget's line and its waits, the links a widget sends, how a start is done. */
class PhoneWidgetRulesTest {
    private val R = PhoneWidgetRules
    private val times = listOf(10.0, 14.5, 19.0, 25.0)

    @Test fun theLineBeingSungComesAQuarterSecondEarly() {
        assertEquals("before the first line: none", -1, R.lineAt(times, 3.0))
        assertEquals("a quarter second before a line, it's lit", 0, R.lineAt(times, 9.75))
        assertEquals(0, R.lineAt(times, 14.0))
        assertEquals(1, R.lineAt(times, 14.3))
        assertEquals("after the last line, the last stays lit", 3, R.lineAt(times, 400.0))
    }

    @Test fun theWaitEndsJustAfterTheNextLine() {
        assertEquals("a line two seconds off: looked at once a second meanwhile, for seeks", R.MAX_WAIT, R.waitMs(times, 12.0))
        val close = R.waitMs(times, 14.0)   // 0.25 s to go
        assertTrue("lands just after it ($close ms)", close in 250L..300L)
        assertEquals("long gaps are looked at once a second, for seeks", R.MAX_WAIT, R.waitMs(times, 0.0))
        assertEquals("right on a line: not a busy loop", R.MIN_WAIT, R.waitMs(listOf(5.0, 5.01), 4.75))
        assertEquals("after the last line", R.MAX_WAIT, R.waitMs(times, 30.0))
    }

    @Test fun theLineSungStaysInPlaceWhileTheLinesGoBy() {
        assertEquals("before the first line: from the first", 0..8, R.window(-1, 40, 9, 2))
        assertEquals("the first lines: from the first", 0..8, R.window(1, 40, 9, 2))
        assertEquals("then two lines above it", 8..16, R.window(10, 40, 9, 2))
        assertEquals("near the end: no jumping back, the slots under the last stay empty", 37..39, R.window(39, 40, 9, 2))
        assertEquals("fewer lines than slots", 0..2, R.window(0, 3, 9, 2))
        assertTrue("no lines", R.window(-1, 0, 9, 2).isEmpty())
    }

    @Test fun aPinsKeyKeepsItsColons() {
        assertEquals("pin" to "mine::ab12cd", R.split("pin:mine::ab12cd"))
        assertEquals("top" to "", R.split("top"))
        assertEquals("wrapped" to "month", R.split("wrapped:month"))
    }

    @Test fun aPlaylistStartComesBackAsItWasWritten() {
        assertEquals("ab12cd" to true, R.playlistOf(R.list("ab12cd", true)))
        assertEquals("ab12cd" to false, R.playlistOf(R.list("ab12cd", false)))
        assertEquals("no order said: in order", "ab12cd" to false, R.playlistOf("list:ab12cd"))
        assertNull(R.playlistOf("list:"))
        assertNull(R.playlistOf("dj"))
        assertNull(R.playlistOf("pin:mine::ab12cd"))
    }

    @Test fun aPausedAppStartsMusicThroughItsService() {
        assertEquals("playing: carried on in the app", "now", R.startHow(playerUp = true, playing = true, remote = false))
        assertEquals("paused in the background: Android needs the service started first", "service", R.startHow(playerUp = true, playing = false, remote = false))
        assertEquals("the app not running", "service", R.startHow(playerUp = false, playing = false, remote = false))
        assertEquals("the PC's remote plays nothing here", "now", R.startHow(playerUp = false, playing = false, remote = true))
    }

    @Test fun theDjWidgetStartsCarriesOnOrSwitches() {
        assertEquals("start", R.djTap(on = false, paused = true))
        assertEquals("start", R.djTap(on = false, paused = false))
        assertEquals("resume", R.djTap(on = true, paused = true))
        assertEquals("switch", R.djTap(on = true, paused = false))
    }


    @Test fun newReleasesAreListedOnceNewestFirst() {
        // (key, date): the app's daily look and the screen's Refresh… can both have found the same one
        val told = listOf("a" to "2026-11-20", "b" to "2026-10-02")
        val found = listOf("c" to "2026-11-24", "a" to "2026-11-20", "d" to "2025-12-31")
        val got = R.newest(told + found, { it.first }, { it.second }, 3).map { it.first }
        assertEquals(listOf("c", "a", "b"), got)
    }

    @Test fun aReleaseSaysHowLongAgo() {
        val today = java.time.LocalDate.of(2026, 11, 25)
        assertEquals("Today", R.released("2026-11-25", today))
        assertEquals("Yesterday", R.released("2026-11-24", today))
        assertEquals("6 days ago", R.released("2026-11-19", today))
        assertEquals("1 week ago", R.released("2026-11-18", today))
        assertEquals("4 weeks ago", R.released("2026-10-26", today))
        assertEquals("long ago: the date", "Oct 1", R.released("2026-10-01", today))
        assertEquals("another year: with it", "Dec 31, 2025", R.released("2025-12-31", today))
        assertEquals("still to come", "Out Dec 5", R.released("2026-12-05", today))
        assertEquals("a date it can't read, as it is", "2026", R.released("2026", today))
    }

    @Test fun theVisualizerRunsOnlyWhenItCanBeSeenAndMusicPlays() {
        assertTrue(R.vizLive(present = true, screenOn = true, appShown = false, playing = true))
        assertTrue("no widget", !R.vizLive(false, true, false, true))
        assertTrue("screen off", !R.vizLive(true, false, false, true))
        assertTrue("the app in front", !R.vizLive(true, true, true, true))
        assertTrue("paused", !R.vizLive(true, true, false, false))
        assertTrue("about 30 frames a second: smooth, and no more than a home screen takes", 1000 / R.VIZ_FRAME_MS in 24..31)
    }

    @Test fun eachBarIsTheLoudestOfItsBands() {
        val bands = FloatArray(48) { if (it == 5) 0.9f else 0.1f }
        val b = R.bars(bands, 24)
        assertEquals(24, b.size)
        assertEquals("band 5 is in bar 2 (two bands a bar)", 0.9f, b[2], 0f)
        assertEquals(0.1f, b[3], 0f)
        assertEquals("fewer bands than bars: each bar still has one", listOf(0.2f, 0.2f, 0.7f, 0.7f), R.bars(floatArrayOf(0.2f, 0.7f), 4).toList())
        assertEquals("nothing heard yet: flat bars", listOf(0f, 0f), R.bars(FloatArray(0), 2).toList())
    }

    @Test fun barsRiseAtOnceAndFallGently() {
        val shown = floatArrayOf(1f, 0.2f)
        val next = R.fall(shown, floatArrayOf(0f, 0.8f), keep = 0.5f)
        assertEquals("a loud bar falls to half, not to nothing", 0.5f, next[0], 1e-6f)
        assertEquals("a bar goes up with the music at once", 0.8f, next[1], 1e-6f)
    }
}
