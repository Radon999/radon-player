package org.omarchy.music

/** Moods match (Settings › Remote & sync, under Tags match): at each sync the moods of the main device's songs are
 *  put on the other device's copies of the same songs and written into their files. The main device is picked here,
 *  apart from Tags match's: the tags can follow the phone and the moods the PC. With it off, moods stay on each
 *  device as they are (a sync neither sends nor takes them). The rules are [MoodMatchRules]; the giving and the
 *  writing are [Moods]'. */
object MoodMatch {
    const val ON = "mood_match_on"
    const val MAIN = "mood_match_main"   // "pc" or "phone"

    /** On with its own switch, or with Tags match's: one main device then gives everything, moods included. */
    val on: Boolean get() = Prefs.on(ON) || TagMatch.on
    val main: String get() = if (TagMatch.on) TagMatch.main else Prefs.text(MAIN, "pc")
    val here: String get() = if (platformName == "android") "phone" else "pc"

    /** This device takes the other's moods at a sync. */
    val takes: Boolean get() = MoodMatchRules.takes(on, main, here)
    /** This device's moods lead: sent, never replaced by the other's. */
    val leads: Boolean get() = MoodMatchRules.leads(on, main, here)
    /** The main device, as it's said: "PC" or "phone". */
    val mainName: String get() = if (main == "pc") "PC" else "phone"
}
