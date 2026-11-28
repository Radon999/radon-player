package org.omarchy.music

/** Battery saver (Settings): what it changes. On by its switch, or by itself while the phone's own battery saver is
 *  on. Pure. */
object BatterySaverRules {
    const val PREF = "battery_saver"

    fun on(switch: Boolean, systemSaver: Boolean): Boolean = switch || systemSaver

    /** The Visualizer widget's frame: half as many a second while saving (15, for 30). */
    fun vizFrameMs(saving: Boolean): Long = if (saving) PhoneWidgetRules.VIZ_FRAME_MS * 2 else PhoneWidgetRules.VIZ_FRAME_MS

    /** Whether a scan (lyrics, moods, auto-sync) waits: saving, and not plugged in. */
    fun holdsScans(saving: Boolean, pluggedIn: Boolean): Boolean = saving && !pluggedIn

    fun waits(what: String, device: String): String = "Battery saver is on: $what waits until this $device is plugged in"
}
