package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ignore Bluetooth devices (Settings › Playback, and Now playing's Bluetooth-with-an-X button): when a Bluetooth device
 *  you picked (your car) connects and takes the sound, the app keeps its music on the output it was on (your headphones).
 *  Only the app's own music moves: the device itself still connects, so Android Auto's maps and calls work as before.
 *  This keeps the switch and the lists; each platform's BtRoute does the moving (the phone: ExoPlayer's preferred
 *  device; the PC: mpv's audio device). */
object IgnoreBt {
    /** The ignored devices connected now ("" when none, or ignoring is off). */
    var here by mutableStateOf("")
        private set
    /** Where the music is being kept instead ("" when it isn't). */
    var keptOn by mutableStateOf("")
        private set
    /** Why it couldn't be kept anywhere else ("" when it could, or there was nothing to do). */
    var why by mutableStateOf("")
        private set

    val on: Boolean get() = Prefs.on(Prefs.IGNORE_BT)
    fun ignored(): List<String> = BtIgnoreRules.parse(Prefs.text(Prefs.IGNORE_BT_DEVICES))
    fun seen(): List<String> = BtIgnoreRules.parse(Prefs.text(Prefs.IGNORE_BT_SEEN))

    /** Bluetooth outputs connected now: remembered, to pick from in Settings. */
    fun saw(names: Collection<String>) {
        val was = seen()
        val now = BtIgnoreRules.remember(was, names)
        if (now != was) Prefs.setText(Prefs.IGNORE_BT_SEEN, BtIgnoreRules.join(now))
    }

    fun setOn(v: Boolean) { Prefs.set(Prefs.IGNORE_BT, v); apply() }

    /** Newest Bluetooth device wins (beside Ignore Bluetooth devices): with two connected, the one that connected last
     *  has the music (and its EQ). An ignored one never does. */
    val newestOn: Boolean get() = Prefs.on(Prefs.NEWEST_BT)
    fun setNewest(v: Boolean) { Prefs.set(Prefs.NEWEST_BT, v); newestWhy = ""; apply() }
    /** Which device has the music now because it's the newest ("" when the switch isn't deciding anything). */
    var newestNow by mutableStateOf("")
        private set
    var newestWhy by mutableStateOf("")
        private set
    fun newest(name: String, others: String) {
        if (name == newestNow) return
        newestNow = name
        newestWhy = others
        if (name.isNotEmpty()) Music.toast("$name connected last: the music is on it" + if (others.isNotEmpty()) " (not $others)" else "")
    }

    /** Pick a device to ignore, or stop ignoring it. */
    fun pick(name: String, ignore: Boolean) {
        val l = ignored()
        Prefs.setText(Prefs.IGNORE_BT_DEVICES, BtIgnoreRules.join(if (ignore) l + name else l - name))
        apply()
    }

    /** Take a device off the list of ones seen (and stop ignoring it). */
    fun forget(name: String) {
        Prefs.setText(Prefs.IGNORE_BT_SEEN, BtIgnoreRules.join(seen() - name))
        if (name in ignored()) pick(name, false)
    }

    /** Look again now (the switch or the picks changed), and let the EQ follow where the music plays. */
    fun apply() {
        runCatching { BtRoute.check() }
        runCatching { Music.outputChanged() }
    }

    /** What BtRoute found (each time it looks): shown in Settings, and said once when it changes. */
    fun report(ignoredHere: String, kept: String, reason: String) {
        val changed = ignoredHere != here || kept != keptOn || reason != why
        here = ignoredHere; keptOn = kept; why = reason
        if (!changed || ignoredHere.isEmpty()) return
        if (kept.isNotEmpty()) Music.toast("$ignoredHere is ignored: the music stays on $kept")
        else if (reason.isNotEmpty()) Music.toast("$ignoredHere connected and the music went there: $reason")
    }
}
