package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The music paused by another app taking the sound for good: when it goes on by itself. */
class FocusRulesTest {
    private val now = 10_000_000L

    @Test fun goesOnWhenTheAppThatTookTheSoundPlaysNothing() {
        assertTrue(FocusRules.goOn(on = true, stillPaused = true, inCall = false, otherPlaying = false, resumed = emptyList(), now = now))
    }

    @Test fun staysPausedWhenSomethingElseReallyPlaysOrACallIsOn() {
        assertFalse("another app is playing", FocusRules.goOn(true, true, inCall = false, otherPlaying = true, resumed = emptyList(), now = now))
        assertFalse("a call", FocusRules.goOn(true, true, inCall = true, otherPlaying = false, resumed = emptyList(), now = now))
        assertFalse("you paused or played since", FocusRules.goOn(true, stillPaused = false, inCall = false, otherPlaying = false, resumed = emptyList(), now = now))
        assertFalse("switched off", FocusRules.goOn(on = false, stillPaused = true, inCall = false, otherPlaying = false, resumed = emptyList(), now = now))
    }

    @Test fun stopsAfterAFewTimesSoItNeverFightsAnotherApp() {
        val fight = List(FocusRules.MOST) { now - it * 30_000L }
        assertFalse(FocusRules.goOn(true, true, false, false, fight, now))
        val long = List(FocusRules.MOST) { now - FocusRules.SPAN_MS - it * 1000L }
        assertTrue("the old ones don't count", FocusRules.goOn(true, true, false, false, long, now))
    }
}
