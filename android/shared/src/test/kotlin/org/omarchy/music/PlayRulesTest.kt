package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What counts as a play, a scrobble and a skip. */
class PlayRulesTest {
    @Test fun aPlayIsHalfTheSong() {
        assertFalse(PlayRules.countsAsPlay(99.0, 200.0, 30.0))
        assertTrue(PlayRules.countsAsPlay(100.0, 200.0, 30.0))
        // A length that isn't known: after a set time
        assertFalse(PlayRules.countsAsPlay(29.0, 0.0, 30.0))
        assertTrue(PlayRules.countsAsPlay(30.0, 0.0, 30.0))
    }

    @Test fun aScrobbleIsHalfTheSongOrFourMinutes() {
        assertFalse(PlayRules.scrobbles(99.0, 200.0))
        assertTrue(PlayRules.scrobbles(100.0, 200.0))
        assertFalse(PlayRules.scrobbles(239.0, 1200.0))
        assertTrue(PlayRules.scrobbles(240.0, 1200.0))   // a long one: four minutes is enough
        assertFalse(PlayRules.scrobbles(30.0, 30.0))     // half a minute or less: never
        assertFalse(PlayRules.scrobbles(100.0, 0.0))
    }

    @Test fun aSkipIsLeavingBeforeTheLastSeconds() {
        assertTrue(PlayRules.isSkip(heard = 5.0, length = 200.0, at = 60.0, skipLeft = 15.0))
        assertFalse("not a second heard", PlayRules.isSkip(0.5, 200.0, 60.0, 15.0))
        assertFalse("in its last seconds", PlayRules.isSkip(180.0, 200.0, 190.0, 15.0))
        assertTrue("just before them", PlayRules.isSkip(180.0, 200.0, 184.0, 15.0))
        assertFalse("too short to have a middle", PlayRules.isSkip(5.0, 30.0, 10.0, 15.0))
    }
}
