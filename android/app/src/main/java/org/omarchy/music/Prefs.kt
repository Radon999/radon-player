package org.omarchy.music

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/** 2.0's switches (crossfade, crossfeed, the lock screen player…), each kept on its own; all off until you
 *  turn them on. Read in Compose, a change shows straight away. */
object Prefs {
    private var sp: SharedPreferences? = null
    private val values = mutableStateMapOf<String, Any>()

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.getSharedPreferences("radon-2", Context.MODE_PRIVATE).also { p -> p.all.forEach { (k, v) -> if (v != null) values[k] = v } }
    }

    fun on(key: String, default: Boolean = false): Boolean = values[key] as? Boolean ?: default
    fun set(key: String, on: Boolean) { values[key] = on; sp?.edit()?.putBoolean(key, on)?.apply() }
    fun num(key: String, default: Float): Float = (values[key] as? Float) ?: default
    fun setNum(key: String, v: Float) { values[key] = v; sp?.edit()?.putFloat(key, v)?.apply() }
    fun text(key: String, default: String = ""): String = values[key] as? String ?: default
    fun setText(key: String, v: String) { values[key] = v; sp?.edit()?.putString(key, v)?.apply() }
    fun all(): Map<String, Any> = values.toMap()
    fun restore(m: Map<String, Any?>) {
        val e = sp?.edit() ?: return
        for ((k, v) in m) when (v) {
            is Boolean -> { values[k] = v; e.putBoolean(k, v) }
            is Number -> { values[k] = v.toFloat(); e.putFloat(k, v.toFloat()) }
            is String -> { values[k] = v; e.putString(k, v) }
        }
        e.apply()
    }

    // The keys.
    const val CROSSFADE = "crossfade"            // song crossfade
    const val CROSSFADE_SECS = "crossfade_secs"  // its length
    const val SMART_TRANSITIONS = "smart_transitions"
    const val CROSSFEED = "crossfeed"
    const val MONO = "mono"                      // Now playing's Mono: both sides in each ear
    const val AUTO_MONO = "auto_mono"            // Mono on its own while the chosen earbuds are connected
    const val AUTO_MONO_DEVICE = "auto_mono_device"   // which earbuds (their Bluetooth name)
    const val CONTINUE = "continue_across"       // continue where you left off, across the PC and the phone
    const val SMART_SHUFFLE = "smart_shuffle"
    const val LOUDNESS_CHECK = "loudness_check"
    const val LOCK_SCREEN = "lock_screen_player"
    const val NOTIF_HINTED = "notif_hinted"
}
