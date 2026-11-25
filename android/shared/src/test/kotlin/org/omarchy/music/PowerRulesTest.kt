package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The timers waking the phone less often only while nothing needs them. */
class PowerRulesTest {
    @Test fun theClockRestsOnlyWhenTheMusicIsReallyPaused() {
        assertTrue("no player yet", PowerRules.clockRests(false, false, false, true, false, false))
        assertTrue("paused", PowerRules.clockRests(true, isPlaying = false, playWhenReady = false, paused = true, advancing = false, changing = false))
        assertFalse("playing", PowerRules.clockRests(true, isPlaying = true, playWhenReady = true, paused = false, advancing = false, changing = false))
        assertFalse("meant to play (loading, stuck at its end)", PowerRules.clockRests(true, false, playWhenReady = true, paused = false, advancing = false, changing = false))
        assertFalse("moving on to the next song", PowerRules.clockRests(true, false, false, true, advancing = true, changing = false))
        assertFalse("a song change being timed", PowerRules.clockRests(true, false, false, true, false, changing = true))
        assertFalse("the app says it plays", PowerRules.clockRests(true, false, false, paused = false, advancing = false, changing = false))
    }

    @Test fun thePcIsAskedEvery20sOnScreenAndLessOftenAway() {
        val t = 1_000_000L
        assertTrue("wanted at once (just paired, a playlist made synced)", PowerRules.syncAskDue(t, 0L, t - 1000, shown = false))
        assertFalse("not time yet", PowerRules.syncAskDue(t, t + 1, t - 19_999, shown = true))
        assertTrue("on screen: every 20 s", PowerRules.syncAskDue(t, t, t - 20_000, shown = true))
        assertFalse("away (playing or not): not after 20 s", PowerRules.syncAskDue(t, t, t - 20_000, shown = false))
        assertTrue("away: every 2 minutes", PowerRules.syncAskDue(t, t, t - 120_000, shown = false))
        assertTrue("back on screen after backing off: asked within 30 s", PowerRules.syncAskDue(t, t + 200_000, t - 30_000, shown = true))
        assertFalse("away and backing off: waits", PowerRules.syncAskDue(t, t + 1, t - 200_000, shown = false))
    }

    @Test fun aPcThatDoesNotAnswerIsAskedLessAndLessOftenAway() {
        assertEquals(30_000L, PowerRules.syncRetryMs(5, shown = true))
        assertEquals(listOf(120_000L, 240_000L, 300_000L, 300_000L), (1..4).map { PowerRules.syncRetryMs(it, shown = false) })
    }

    @Test fun wiFiIsKeptAwakeOnlyWhenTheMusicMayNeedTheNetwork() {
        assertTrue(PowerRules.onPhone("/storage/emulated/0/Music/a.flac"))
        assertTrue(PowerRules.onPhone("content://media/external/audio/media/12"))
        assertTrue(PowerRules.onPhone("file:///data/user/0/x/cache/ahead/1.flac"))
        assertFalse(PowerRules.onPhone("https://sp-pr-fa.audio.tidal.com/x.flac"))
        assertFalse("a hi-res stream in pieces", PowerRules.onPhone("data:application/dash+xml;base64,AAAA"))
        assertFalse(PowerRules.onPhone(null))
        assertFalse("your files, the next one yours too", PowerRules.needsNetwork("/a.flac", nextOnPhone = true))
        assertTrue("your file, nothing after it yet (∞ or the DJ may look for more)", PowerRules.needsNetwork("content://m/1", nextOnPhone = null))
        assertTrue("a stream", PowerRules.needsNetwork("https://x/y.flac", nextOnPhone = true))
        assertTrue("the next one is a stream (fetched ahead)", PowerRules.needsNetwork("/a.flac", nextOnPhone = false))
        assertTrue("nothing loaded: as before", PowerRules.needsNetwork(null, nextOnPhone = null))
    }
}
