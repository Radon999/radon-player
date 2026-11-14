package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** The watch for a song that reached its end without saying so. */
class EndWatchTest {
    private var now = 1_000_000L
    /** One look half a second on: a three-minute song, playing, unless said otherwise. */
    private fun EndWatch.at(at: Long, engineMs: Long = 180_000, songMs: Long = 180_000, playing: Boolean = true, advancing: Boolean = false,
                            remote: Boolean = false, loaded: Boolean = true, ready: Boolean = true, idle: Boolean = false,
                            audioPlaying: Boolean? = null, audioEnded: Boolean = false): EndWatch.Verdict {
        now += 500
        return look(now, engineMs, songMs, at, playing, advancing, remote, loaded, ready, buffering = false, idle = idle, audioPlaying = audioPlaying, audioEnded = audioEnded)
    }
    private val playing = EndWatch.Verdict.PLAYING

    @Test fun aSongPlayingAlongIsLeftAlone() {
        val w = EndWatch()
        for (at in 0L..179_000L step 500) assertEquals(playing, w.at(at))
        // Paused in its last second: not stuck, however long
        repeat(20) { assertEquals(playing, w.at(179_500, playing = false)) }
    }

    @Test fun stoppedInItsLastSecondForTwoAndAHalfSecondsItsOver() {
        val w = EndWatch()
        assertEquals(playing, w.at(179_000))   // (the first look there: where it was before isn't known)
        repeat(4) { assertEquals(playing, w.at(179_000)) }
        assertEquals(EndWatch.Verdict.STUCK, w.at(179_000))
        // Earlier in the song the same stillness is a stall of the network, not the end
        val w2 = EndWatch()
        repeat(20) { assertEquals(playing, w2.at(90_000)) }
        // And one that moves again before the time is up starts counting again
        val w3 = EndWatch()
        repeat(4) { w3.at(179_000) }
        assertEquals(playing, w3.at(179_400))
        repeat(4) { assertEquals(playing, w3.at(179_400)) }
        assertEquals(EndWatch.Verdict.STUCK, w3.at(179_400))
    }

    @Test fun runningOnPastItsEndWithNoMusicItsOverAtOnce() {
        assertEquals(playing, EndWatch().at(181_000))
        assertEquals(EndWatch.Verdict.PAST, EndWatch().at(181_500))
        assertEquals(EndWatch.Verdict.PAST, EndWatch().at(181_500, audioPlaying = false))
        // Music still coming out: it plays to its real end, up to a minute over
        val w = EndWatch()
        for (at in 181_500L..239_500L step 500) assertEquals(playing, w.at(at, audioPlaying = true))
        assertEquals(EndWatch.Verdict.PAST, w.at(240_000, audioPlaying = true))
        // A player that hasn't said a length, or isn't loaded: nothing to go by
        assertEquals(playing, EndWatch().at(181_500, engineMs = 0))
        assertEquals(playing, EndWatch().at(181_500, idle = true))
    }

    @Test fun itsMusicAllPlayedOutItsOverAfterASecond() {
        val w = EndWatch()
        assertEquals(playing, w.at(178_000, audioEnded = true))
        assertEquals(EndWatch.Verdict.AUDIO_DONE, w.at(178_200, audioEnded = true))
        // Not five seconds or more before its end (a gap in the music isn't the end)
        val w2 = EndWatch()
        repeat(6) { assertEquals(playing, w2.at(100_000 + it * 500L, audioEnded = true)) }
    }

    @Test fun onlyTheSongThatsReallyInThePlayer() {
        // Another song's clock (the next is loading), or this device is only the remote: never
        assertEquals(playing, EndWatch().at(181_500, loaded = false))
        assertEquals(playing, EndWatch().at(181_500, remote = true))
        // Moving on to the next song: waited for, for eight seconds; after that it counts again
        val w = EndWatch()
        repeat(16) { assertEquals(playing, w.at(181_500, advancing = true)) }   // the first look, and 7.5 seconds more
        assertEquals(EndWatch.Verdict.PAST, w.at(181_500, advancing = true))
    }

    @Test fun theSongsOwnLengthWhenThePlayersIsShort() {
        assertEquals(200_000L, EndWatch.length(engineMs = 180_000, songMs = 200_000))   // up to a minute longer: the song's
        assertEquals(180_000L, EndWatch.length(engineMs = 180_000, songMs = 300_000))   // more: the player's
        assertEquals(180_000L, EndWatch.length(engineMs = 180_000, songMs = 170_000))
        assertEquals(170_000L, EndWatch.length(engineMs = 0, songMs = 170_000))
        // So a song the player thinks is over at 3:00 but whose tags say 3:20 isn't "past its end" at 3:02
        assertEquals(playing, EndWatch().at(182_000, songMs = 200_000))
    }
}
