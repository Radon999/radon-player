package org.omarchy.music

/** Watches for a song that reached its end but never said so (seen with some AAC/M4A files, and with some sound
 *  outputs that never report the last sound played): the player sits at -0:00 "playing" and nothing comes next, or
 *  its clock runs on past the end. Looked at twice a second ([look]); it says when the song is over after all. Taken
 *  out of Music (the playback service, docs/rebuild/RADON_REBUILD_PLAN.md, Phase 9): no player and no app here, only
 *  what the player reports, so it's tested by itself. Times in milliseconds. */
class EndWatch {
    enum class Verdict {
        /** Nothing wrong: playing, paused, loading, or not this song's clock. */ PLAYING,
        /** Its music has all been played out but the player hasn't said it ended: on to the next, quietly. */ AUDIO_DONE,
        /** Within its last 1.5 seconds, meant to be playing, and its place hasn't moved for 2.5 seconds. */ STUCK,
        /** 1.5 seconds past its end with no music coming out (or a minute past, whatever comes out). */ PAST,
    }

    private var stuckMs = 0
    private var audioDoneMs = 0
    private var stuckAt = -1L
    private var advancingSince = 0L

    companion object {
        /** Its length: the player's, or the song's own (its tags', the service's) when that's longer by up to a minute,
         *  so a file whose player underestimates its length (a VBR MP3 without its header, a stream) isn't cut short
         *  while it still plays. */
        fun length(engineMs: Long, songMs: Long): Long =
            if (engineMs > 0) maxOf(engineMs, if (songMs in engineMs..engineMs + 60_000) songMs else 0L) else songMs
    }

    /** One look, half a second after the last. now: the clock; engineMs: the length the player says (0: none yet);
     *  songMs: the song's own; at: where the player is; advancing: the app is moving on to another song; remote: this
     *  device is only the remote; loaded: the song in the player is the one the queue is on; ready, buffering, idle:
     *  the player's state; audioPlaying: music is coming out (null: it can't say); audioEnded: it has all come out. */
    fun look(now: Long, engineMs: Long, songMs: Long, at: Long, playWhenReady: Boolean, advancing: Boolean, remote: Boolean, loaded: Boolean,
             ready: Boolean, buffering: Boolean, idle: Boolean, audioPlaying: Boolean?, audioEnded: Boolean): Verdict {
        val d = length(engineMs, songMs)
        // Moving on that never finished (it should take a moment): not waited for any longer
        if (advancing) { if (advancingSince == 0L) advancingSince = now } else advancingSince = 0L
        val busy = advancing && now - advancingSince < 8000
        // Only the song that's really in the player: while the next one loads (after a crossfade, the ending one plays
        // on silently in the player till then), its clock is the last song's, and was read as this one's running past
        // its end, so a song was skipped before it played.
        val live = playWhenReady && !busy && !remote && loaded && d > 0
        val stuck = live && at >= (if (engineMs > 0) engineMs else d) - 1500 && (ready || buffering) && kotlin.math.abs(at - stuckAt) < 50
        // Past its end: only when no music is coming out any more. A file whose music runs on longer than its length says
        // (some MP3s and M4As, whose length is only an estimate) is still playing, so it plays to its real end, not cut
        // off 1.5 seconds past the length; a minute over is the limit.
        val past = live && at >= d + 1500 && !idle && engineMs > 0 && (audioPlaying != true || at >= d + 60_000)
        stuckAt = at
        // Its music has all been played out, but the player hasn't said it ended: on to the next now (a moment's wait in
        // case the player's own end is just coming), not 1.5 seconds past the end.
        audioDoneMs = if (live && audioEnded && ready && at >= d - 5000) audioDoneMs + 500 else 0
        if (audioDoneMs >= 1000) { audioDoneMs = 0; stuckMs = 0; advancingSince = 0L; return Verdict.AUDIO_DONE }
        stuckMs = if (stuck) stuckMs + 500 else 0
        if (stuckMs >= 2500 || past) { stuckMs = 0; advancingSince = 0L; return if (past) Verdict.PAST else Verdict.STUCK }
        return Verdict.PLAYING
    }
}
