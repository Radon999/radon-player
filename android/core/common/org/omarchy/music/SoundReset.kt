package org.omarchy.music

/** Reset sound to defaults (Settings › Sound): the output playing now goes back to a flat equalizer with nothing
 *  added, and the switches that change the sound go off. Your saved Sound presets, headphone corrections you added
 *  and every other output's EQ are kept. */
object SoundReset {
    /** What it does, for the question asked first. */
    const val ASK = "Reset sound to defaults?\n\n" +
        "• The equalizer of the output playing now: off and flat, Bass, Treble and Boost at 0, no headphone correction\n" +
        "• Crossfeed, Mono, Detail and loudness compensation: off\n" +
        "• Balance and Tilt: back to the middle\n\n" +
        "Your saved Sound presets and every other output's EQ are kept."

    fun now() {
        Music.setEq { e -> e.copy(on = false, headphones = "off", preset = "flat", bands = List(e.bands.size) { 0.0 },
            custom = List(e.custom.size) { 0.0 }, bass = 0.0, treble = 0.0, boost = 0.0) }
        for (k in listOf(Prefs.CROSSFEED, Prefs.MONO, Prefs.DETAIL, Prefs.LOUDNESS)) if (Prefs.on(k)) runCatching { Music.setOpt(k, false) }
        Prefs.setNum(Prefs.BALANCE, 0f); Prefs.setNum(Prefs.TILT, 0f)
        runCatching { Music.applySound() }
        Music.toast("Sound is back to its defaults")
    }
}
