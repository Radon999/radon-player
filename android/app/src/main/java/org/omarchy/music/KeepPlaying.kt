package org.omarchy.music

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer

/** Keep playing (Settings › Playback, on at first; see [KeepPlayingRules]): the app holds the sound for the player
 *  itself, so no other app taking it pauses the music. Another app taking it for a moment turns the music down; a call
 *  (ringing, on a call, a voice or video chat) is the only pause, and the music plays again when it's over. Off: the
 *  player handles the sound as Android's way, as before. Runs on the main thread. */
class KeepPlaying(ctx: Context, private val exo: ExoPlayer) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val h = Handler(Looper.getMainLooper())
    private var held = false            // the app has the sound now
    private var pausedForCall = false   // this paused the music for a call (it plays again after)
    private var watching = false        // looking at whether a call is on, once a second
    private var callOverAt = 0L         // when the call was last seen over (it plays again a moment later)
    private var applied: Boolean? = null

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setWillPauseWhenDucked(false)   // a spoken direction: Android turns the music down by itself
        .setOnAudioFocusChangeListener({ changed(it) }, h)
        .build()

    private val on get() = Prefs.on(Prefs.KEEP_PLAYING, true)
    private fun inCall() = KeepPlayingRules.inCall(am.mode)

    /** The switch as it is now: who handles the sound, the player (Android's way) or this. */
    fun apply() {
        val now = on
        if (applied == now) return
        applied = now
        BtRoute.handlesFocus(exo, !now)
        if (!now) { drop(); pausedForCall = false } else if (exo.playWhenReady) take()
    }

    /** The music started or paused (by you, the app or this). */
    fun playChanged(play: Boolean) {
        if (!on) return
        if (play) { pausedForCall = false; take(); watch() }
        else if (!pausedForCall) drop()
    }

    private fun take() {
        if (!held) held = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        watch()
    }

    private fun drop() {
        if (held) runCatching { am.abandonAudioFocusRequest(request) }
        held = false
        Music.eqProcessor.focusTarget = 1.0
        // Nothing to watch for while it's paused (not for a call): told of the phone's mode again once it plays
        if (!pausedForCall && android.os.Build.VERSION.SDK_INT >= 31) modes?.let { runCatching { am.removeOnModeChangedListener(it) }; modes = null }
    }

    private fun changed(c: Int) {
        if (!on) return
        val change = when (c) {
            AudioManager.AUDIOFOCUS_GAIN -> KeepPlayingRules.Change.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> KeepPlayingRules.Change.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> KeepPlayingRules.Change.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> KeepPlayingRules.Change.LOSS_TRANSIENT_CAN_DUCK
            else -> return
        }
        if (change == KeepPlayingRules.Change.LOSS) held = false
        if (change == KeepPlayingRules.Change.GAIN) held = true
        when (KeepPlayingRules.act(change, inCall(), exo.playWhenReady, pausedForCall)) {
            KeepPlayingRules.Act.PLAY_ON -> { Music.eqProcessor.focusTarget = 1.0; SoundLog.note("another app took the sound: the music plays on (Keep playing)") }
            KeepPlayingRules.Act.DUCK -> { Music.eqProcessor.focusTarget = KeepPlayingRules.DUCKED; SoundLog.note("another app has the sound for a moment: the music is turned down") }
            KeepPlayingRules.Act.UNDUCK -> { if (Music.eqProcessor.focusTarget != 1.0) SoundLog.note("the sound is the music's again: turned back up"); Music.eqProcessor.focusTarget = 1.0 }
            KeepPlayingRules.Act.PAUSE_FOR_CALL -> pauseForCall()
            KeepPlayingRules.Act.PLAY_AFTER_CALL -> if (!inCall()) playAfterCall()
            KeepPlayingRules.Act.NOTHING -> {}
        }
    }

    private fun pauseForCall() {
        pausedForCall = true; callOverAt = 0L
        SoundLog.note("a call: the music pauses until it's over")
        exo.pause()
        watch()
    }

    private fun playAfterCall() {
        pausedForCall = false
        Music.eqProcessor.focusTarget = 1.0
        SoundLog.note("the call is over: the music plays again")
        exo.play()
    }

    /** A call starting pauses the music (even when another app has the sound, so nothing is told), and a second after
     *  the call ends it plays again. Android 12 on, the app is told the moment the phone's mode changes, so nothing runs
     *  in between (it used to wake the phone every second for as long as the music played); before that, and if
     *  Android won't tell it, a look once a second while the music plays or waits for a call to end. */
    private fun watch() {
        if (android.os.Build.VERSION.SDK_INT >= 31 && listenModes()) {
            h.postDelayed({ modeChanged() }, 1000)   // already in a call when it started playing: paused, as before
            return
        }
        if (watching) return
        watching = true
        fun tick() {
            if (!on || !(exo.playWhenReady || pausedForCall)) { watching = false; return }
            val call = inCall()
            if (call && exo.playWhenReady && !pausedForCall) pauseForCall()
            else if (!call && pausedForCall) {
                val now = System.currentTimeMillis()
                if (callOverAt == 0L) callOverAt = now else if (now - callOverAt >= 1000) playAfterCall()
            } else if (call) callOverAt = 0L
            h.postDelayed({ tick() }, 1000)
        }
        h.postDelayed({ tick() }, 1000)
    }

    private var modes: AudioManager.OnModeChangedListener? = null

    /** (Android 12 on) Told of each change of the phone's mode, from now; false if Android won't. */
    @androidx.annotation.RequiresApi(31)
    private fun listenModes(): Boolean {
        if (modes != null) return true
        val l = AudioManager.OnModeChangedListener { modeChanged() }
        if (runCatching { am.addOnModeChangedListener({ h.post(it) }, l) }.isFailure) return false
        modes = l
        return true
    }

    /** The phone's mode changed (a call started or ended), or the first look after the music started. */
    private fun modeChanged() {
        if (!on) return
        val call = inCall()
        if (call && exo.playWhenReady && !pausedForCall) pauseForCall()
        else if (!call && pausedForCall) h.postDelayed({ if (on && pausedForCall && !inCall()) playAfterCall() }, 1000)
    }
}
