package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a song fades into the next, and when it doesn't. */
class CrossfadeRulesTest {
    private fun unavailable(on: Boolean = true, bitPerfect: Boolean = false, repeatOne: Boolean = false, remote: Boolean = false,
                            casting: Boolean = false, streaming: Boolean = false, ducked: Boolean = false) =
        CrossfadeRules.unavailable(on, bitPerfect, repeatOne, remote, casting, streaming, ducked)

    @Test fun onlyWhenItsOnAndCanBeDone() {
        assertFalse(unavailable())
        assertTrue(unavailable(on = false))
        assertTrue(unavailable(bitPerfect = true))
        assertTrue(unavailable(repeatOne = true))
        assertTrue(unavailable(remote = true))
        assertTrue(unavailable(casting = true))
        assertTrue(unavailable(streaming = true))
        assertTrue("not while the DJ is talking", unavailable(ducked = true))
    }

    @Test fun theFadeBeginsItsLengthBeforeTheEndAndShortSongsDontFade() {
        assertEquals(194.0, CrossfadeRules.fadeAt(200.0, 6.0)!!, 0.0)
        assertEquals(12.0, CrossfadeRules.fadeAt(18.0, 6.0)!!, 0.0)   // just long enough
        assertNull(CrossfadeRules.fadeAt(17.9, 6.0))
        assertNull(CrossfadeRules.fadeAt(0.0, 6.0))                   // a length not known yet
    }

    @Test fun songsOfOneAlbumAndTheDjsWordsAreNotFadedOver() {
        assertFalse(CrossfadeRules.keptApart(sameAlbum = false, smartTransitions = true, djTalksBetween = false))
        assertTrue(CrossfadeRules.keptApart(sameAlbum = true, smartTransitions = true, djTalksBetween = false))
        assertFalse("without Smart transitions an album fades like anything else", CrossfadeRules.keptApart(sameAlbum = true, smartTransitions = false, djTalksBetween = false))
        assertTrue(CrossfadeRules.keptApart(sameAlbum = false, smartTransitions = false, djTalksBetween = true))
    }

    @Test fun theEndingIsGotReadyInTheFiveSecondsBefore() {
        assertFalse(CrossfadeRules.getReady(188.9, 194.0, ready = false))
        assertTrue(CrossfadeRules.getReady(189.0, 194.0, ready = false))
        assertTrue(CrossfadeRules.getReady(193.9, 194.0, ready = false))
        assertFalse("at the fade it's too late to begin", CrossfadeRules.getReady(194.0, 194.0, ready = false))
        assertFalse(CrossfadeRules.getReady(190.0, 194.0, ready = true))
    }
}
