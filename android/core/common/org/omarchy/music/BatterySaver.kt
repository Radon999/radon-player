package org.omarchy.music

import kotlinx.coroutines.launch

/** Battery saver, as the app follows it ([BatterySaverRules]; [Power] is each device's own: whether it is plugged in,
 *  and the phone's own battery saver). */
object BatterySaver {
    fun on(): Boolean = BatterySaverRules.on(Prefs.on(BatterySaverRules.PREF), runCatching { Power.systemSaver() }.getOrDefault(false))

    /** A scan asked for while it must wait: said, and true (don't start). quiet: one the app starts by itself. */
    fun holdsScan(what: String, quiet: Boolean = false): Boolean {
        if (!BatterySaverRules.holdsScans(on(), runCatching { Power.pluggedIn() }.getOrDefault(true))) return false
        if (!quiet) Music.scope.launch { Music.toast(BatterySaverRules.waits(what, org.omarchy.music.ui.DEVICE)) }
        return true
    }
}
