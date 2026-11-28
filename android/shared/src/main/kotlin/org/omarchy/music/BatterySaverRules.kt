package org.omarchy.music

/** Battery saver (Settings): what it changes. On by its switch, or by itself while the phone's own battery saver is
 *  on. Pure. */
object BatterySaverRules {
    const val PREF = "battery_saver"

    fun on(switch: Boolean, systemSaver: Boolean): Boolean = switch || systemSaver

    /** Whether a scan (lyrics, moods, auto-sync) waits: saving, and not plugged in. */
    fun holdsScans(saving: Boolean, pluggedIn: Boolean): Boolean = saving && !pluggedIn

    fun waits(what: String, device: String): String = "Battery saver is on: $what waits until this $device is plugged in"
}
