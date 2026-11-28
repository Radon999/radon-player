package org.omarchy.music

/** Play, Pause, Next, a seek and the heart, wherever they are pressed: in the app, on the headphones, in the car, on
 *  the lock screen or the PC's media keys. While the app is the desktop's remote they go to the desktop. [Music]
 *  keeps the names the screens call and hands on. */
object PlayButtons {
    /** Play from the headphones, the car, the lock screen or the notification: what the app's own Play does, so the DJ
     *  is never left stuck (talking that never came, a set it was still picking, a set it hadn't introduced yet). */
    fun play() {
        if (Remote.active) { Remote.cmd("play"); return }
        val p = Music.player ?: return
        if (DjDirector.djPlay(p)) return   // the DJ's part of Play: it is never left stuck
        if (p.state == Engine.ENDED) {
            // The queue ran out and its last song is over: Play starts that song again (on the phone the player's own
            // play() did nothing there: the button said playing, in silence)
            Music.loadCurrent()
        } else if (p.state == Engine.IDLE || !p.hasItem) {
            // The player let go of the song it was playing: it goes on from where it was, not from its beginning
            if (PlaybackCore.loadedFor == Music.current()?.id && Music.position > 1.0) PlaybackCore.resumeAt = Music.position
            Music.loadCurrent()
        } else {
            // Playing on, so the queue isn't "ended" any more (Next on the last song had left it marked ended: the next
            // song added to the queue then cut this one off)
            PlaybackCore.ended = false
            p.play()
        }
    }

    /** Next from your headphones, the car, the lock screen or the PC's media keys. With the DJ on and the music paused
     *  (not while the DJ is talking), it switches it up: a new kind of set, which starts playing at once. Pause with your
     *  headphones, then double tap. Playing, Next skips one song as always. (Two double taps in a row were meant to do
     *  it before, but earbuds wait after each tap to see if more are coming, so the two came too far apart.) */
    fun next() {
        if (PlayButtonRules.nextSwitchesSet(Music.dj.on, Remote.active, Music.paused, Music.djTalking, Music.current() != null)) {
            Music.djSwitch()
            return
        }
        Music.next()
    }

    fun togglePause() {
        if (Remote.active) { Remote.cmd(if (Remote.status.paused) "play" else "pause"); return }
        val p = Music.player ?: return
        if (Music.current() == null) return
        // Pause while the DJ talks: she stops, and the set's song waits paused at its start
        if (Music.djTalking) { Music.djHush(); p.pause(); return }
        if (p.isPlaying) p.pause() else play()
    }

    fun seek(seconds: Double) {
        if (Remote.active) { Remote.cmd("seek", "position" to seconds); return }
        Music.player?.seekTo((seconds * 1000).toLong())
        Music.position = seconds
    }

    /** The heart: liked on the song's own service (and loved on Last.fm); taken back if the service says no. */
    fun toggleLike() {
        if (Remote.active) { Remote.cmd("like"); return }
        val t = Music.current() ?: return
        val src = Music.sources[t.source] ?: return
        if (!src.signedIn) return
        val like = t.id !in Music.liked
        Music.liked = if (like) Music.liked + t.id else Music.liked - t.id
        Music.bg({ src.rate(t, like); if (LastFm.connected) runCatching { LastFm.love(t, like) } }) { _, err ->
            if (err != null) {
                Music.liked = if (like) Music.liked - t.id else Music.liked + t.id
                Music.toast("Couldn't ${if (like) "like" else "unlike"} it: ${err.message?.take(80)}")
            }
        }
    }
}
