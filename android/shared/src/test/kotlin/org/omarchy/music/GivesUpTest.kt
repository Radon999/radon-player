package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Songs that can't be had one after another: the music stops after a few, instead of skipping through the queue. */
class GivesUpTest {
    @Test fun aFewFailuresAreSkippedPast() {
        for (n in 0 until PlayRules.FAILS_BEFORE_STOPPING) assertFalse("$n", PlayRules.givesUp(n))
    }

    @Test fun thenTheMusicStops() {
        assertTrue(PlayRules.givesUp(PlayRules.FAILS_BEFORE_STOPPING))
        assertTrue(PlayRules.givesUp(PlayRules.FAILS_BEFORE_STOPPING + 5))
        assertTrue("more than one failure is allowed before stopping", PlayRules.FAILS_BEFORE_STOPPING >= 3)
    }
}
