package org.omarchy.music

/** Settings › Sound's switches and numbers, and bit-perfect: each put into the sound (the EQ processor) with what it
 *  takes to take effect. [Music] keeps the names the screens call and hands on. */
object SoundSwitches {
    private val eqProcessor get() = Music.eqProcessor

    /** Settings › Sound's switches and numbers into the EQ processor. */
    fun apply() {
        eqProcessor.limiter = Prefs.on(Prefs.CLIP_GUARD)
        eqProcessor.dither = Prefs.on(Prefs.DITHER)
        eqProcessor.smooth = Prefs.on(Prefs.SMOOTH_PEAKS)
        eqProcessor.balance = Prefs.num(Prefs.BALANCE, 0f).toDouble()
        eqProcessor.crossfeedK = Prefs.num(Prefs.CROSSFEED_K, 0.3f).toDouble()
        eqProcessor.tilt = Prefs.num(Prefs.TILT, 0f).toDouble()
        eqProcessor.detail = Prefs.on(Prefs.DETAIL)
        Loudness.apply()
    }

    fun setNum(key: String, v: Float) { Prefs.setNum(key, v); apply() }

    /** One of 2.0's switches (Prefs), with what it takes to take effect. */
    fun setOpt(key: String, on: Boolean) {
        Prefs.set(key, on)
        if (key == Prefs.CROSSFEED) eqProcessor.crossfeed = on
        if (key == Prefs.AUTO_MONO) AutoMono.check()
        if (key == Prefs.CLIP_GUARD || key == Prefs.DITHER || key == Prefs.LOUDNESS || key == Prefs.DETAIL || key == Prefs.SMOOTH_PEAKS) apply()
        if (key == Prefs.MONO) { eqProcessor.mono = on || AutoMono.active; if (on && Music.bitPerfect) Music.toast("Mono isn't heard in bit-perfect: turn bit-perfect off for it") }
    }

    /** The song playing, loaded again where it is: bit-perfect turned on (or the DAC allowed) takes effect at once. */
    fun replayForBitPerfect() {
        if (Music.current() == null || Remote.active || Music.cast != null) return
        val at = Music.position; val wasPlaying = !Music.paused
        Music.resumeAt = at; Music.loadCurrent(autoplay = wasPlaying)
    }

    fun chooseBitPerfect(on: Boolean) {
        Music.bitPerfect = on
        eqProcessor.bypass = Music.bitPerfectNow()
        if (on) eqProcessor.rgGain = 1.0
        Music.current()?.let { t -> SongMarks.playback(t).let { (sp, pi) -> Music.player?.setSpeed(sp, pi) } }
        val rate = Music.player?.audioRate ?: 0; val bits = Music.player?.audioBits ?: 0
        Music.bg({ Music.dacNote = AudioQuality.applyBitPerfect(Music.ctx, on, rate, bits) }) { _, _ ->
            if (platformName == "android") replayForBitPerfect()   // the song goes (or stops going) straight to the DAC now
        }
        Music.saveState()
    }
}
