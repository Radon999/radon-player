package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app and Android pulling the USB DAC between them is stopped: a few takeovers in a minute are fine, more aren't. */
class TakeoverRulesTest {
    @Test fun aFewInAMinuteAreFineMoreAreATugOfWar() {
        val times = ArrayList<Long>()
        var now = 1_000_000L
        repeat(SinkRules.TAKEOVER_MAX) { assertTrue(SinkRules.mayTakeOver(times, now)); times += now; now += 2_000 }
        assertFalse("taken over again and again within a minute", SinkRules.mayTakeOver(times, now))
    }

    @Test fun takeoversLongAgoAreForgotten() {
        val times = arrayListOf(0L, 1_000L, 2_000L, 3_000L)
        assertTrue(SinkRules.mayTakeOver(times, 3_000L + SinkRules.TAKEOVER_SPAN_MS + 1))
        assertEquals(0, times.size)
    }

    @Test fun onceASongForAnHourIsFine() {
        val times = ArrayList<Long>()
        var now = 0L
        repeat(20) { assertTrue(SinkRules.mayTakeOver(times, now)); times += now; now += 200_000 }   // a song every 3⅓ minutes
    }
}
