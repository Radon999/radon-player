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

    /** Car always wins (the phone, a failsafe beside Newest Bluetooth device wins): whenever the car is connected (its
     *  Bluetooth or Android Auto), it has the music, whatever headphones are connected too. Ignore Bluetooth devices
     *  still comes first (an ignored car never gets it). */
    val carFirst: Boolean get() = Prefs.on(Prefs.CAR_FIRST)
    fun setCarFirst(v: Boolean) { Prefs.set(Prefs.CAR_FIRST, v); apply() }
    /** The car's Bluetooth names: learnt when Android Auto starts with it, and any name that's plainly a car's. */
    fun carNames(): List<String> = BtIgnoreRules.parse(Prefs.text(Prefs.CAR_NAMES))
    /** Whether a Bluetooth output is a car: what you said of it (Settings › Is this a car?), else one learnt beside
     *  Android Auto, else its name. */
    fun isCar(name: String): Boolean =
        EqRules.isCar(name, BtIgnoreRules.parse(Prefs.text(Prefs.CAR_YES)) + carNames(), BtIgnoreRules.parse(Prefs.text(Prefs.CAR_NO)))
    /** You said it: this output is a car, or is not. */
    fun markCar(name: String, car: Boolean) {
        val yes = BtIgnoreRules.parse(Prefs.text(Prefs.CAR_YES)) - name; val no = BtIgnoreRules.parse(Prefs.text(Prefs.CAR_NO)) - name
        Prefs.setText(Prefs.CAR_YES, BtIgnoreRules.join((if (car) yes + name else yes).takeLast(20)))
        Prefs.setText(Prefs.CAR_NO, BtIgnoreRules.join((if (car) no else no + name).takeLast(20)))
        runCatching { Music.outputChanged() }
    }
    fun learnCar(name: String) {
        val l = carNames()
        if (name !in l) Prefs.setText(Prefs.CAR_NAMES, BtIgnoreRules.join((l + name).takeLast(5)))
    }
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
