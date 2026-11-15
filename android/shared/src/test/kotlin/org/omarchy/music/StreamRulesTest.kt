package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** With Tidal connected, a song plays from Tidal unless Tidal hasn't got it: a moment without an answer is asked again. */
class StreamRulesTest {
    @Test fun tidalNotAnsweringIsAskedAgainTwiceThenLeft() {
        val hiccup = java.io.IOException("HTTP 429")
        assertTrue(StreamRules.again(hiccup, 0))
        assertTrue(StreamRules.again(hiccup, 1))
        assertFalse("after two more tries the song plays from what there is", StreamRules.again(hiccup, 2))
        assertTrue("the second pause is the longer", StreamRules.pauseMs(2) > StreamRules.pauseMs(1))
    }

    @Test fun notOnTidalIsAnAnswer() {
        assertFalse("no error: Tidal answered", StreamRules.again(null, 0))
        assertFalse("Tidal said it hasn't got it: not asked again", StreamRules.again(NoLosslessMatch("not on Tidal"), 0))
    }

    @Test fun aStreamThatIsLessOnlyBecauseTidalDidntAnswerIsLookedUpAgainSoon() {
        assertEquals("a usual stream: as long as its address lasts", 900, StreamRules.keptFor(false, 900))
        assertEquals("one from YouTube for a hiccup: a minute, then Tidal is asked again", 60, StreamRules.keptFor(true, 900))
        assertEquals(30, StreamRules.keptFor(true, 30))
    }
}
