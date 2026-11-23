package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.omarchy.music.KeepPlayingRules.Act
import org.omarchy.music.KeepPlayingRules.Change

/** Keep playing: the music never pauses for another app, only for a call. */
class KeepPlayingRulesTest {
    @Test fun anotherAppTakingTheSoundNeverPausesTheMusic() {
        assertEquals("for good (a car connecting, an app opening)", Act.PLAY_ON, KeepPlayingRules.act(Change.LOSS, inCall = false, playing = true, pausedForCall = false))
        assertEquals("for a moment (voice typing): turned down", Act.DUCK, KeepPlayingRules.act(Change.LOSS_TRANSIENT, false, true, false))
        assertEquals(Act.DUCK, KeepPlayingRules.act(Change.LOSS_TRANSIENT_CAN_DUCK, false, true, false))
        assertEquals("and back up", Act.UNDUCK, KeepPlayingRules.act(Change.GAIN, false, true, false))
    }

    @Test fun aCallIsTheOnlyPauseAndTheMusicComesBackAfterIt() {
        assertEquals(Act.PAUSE_FOR_CALL, KeepPlayingRules.act(Change.LOSS_TRANSIENT, inCall = true, playing = true, pausedForCall = false))
        assertEquals(Act.PAUSE_FOR_CALL, KeepPlayingRules.act(Change.LOSS, inCall = true, playing = true, pausedForCall = false))
        assertEquals(Act.PLAY_AFTER_CALL, KeepPlayingRules.act(Change.GAIN, inCall = false, playing = false, pausedForCall = true))
        assertEquals("paused by you before the call: stays paused", Act.DUCK, KeepPlayingRules.act(Change.LOSS_TRANSIENT, true, playing = false, pausedForCall = false))
        assertTrue(KeepPlayingRules.inCall(1)); assertTrue(KeepPlayingRules.inCall(2)); assertTrue(KeepPlayingRules.inCall(3))
        assertFalse("normal", KeepPlayingRules.inCall(0))
    }
}
