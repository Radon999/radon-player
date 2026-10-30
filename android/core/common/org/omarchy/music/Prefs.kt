package org.omarchy.music

import androidx.compose.runtime.mutableStateMapOf

/** 2.0's switches (crossfade, crossfeed, the lock screen player…), each kept on its own; all off until you
 *  turn them on. Read in Compose, a change shows straight away. */
object Prefs {
    private var store: PrefsStore? = null
    private val values = mutableStateMapOf<String, Any>()

    fun init(ctx: Ctx) {
        if (store != null) return
        store = PrefsStore(ctx, "radon-2").also { values.putAll(it.load()) }
    }

    fun on(key: String, default: Boolean = false): Boolean = values[key] as? Boolean ?: default
    fun set(key: String, on: Boolean) { values[key] = on; store?.put(key, on) }
    fun num(key: String, default: Float): Float = (values[key] as? Float) ?: default
    fun setNum(key: String, v: Float) { values[key] = v; store?.put(key, v) }
    fun text(key: String, default: String = ""): String = values[key] as? String ?: default
    fun setText(key: String, v: String) { values[key] = v; store?.put(key, v) }
    fun all(): Map<String, Any> = values.toMap()
    fun restore(m: Map<String, Any?>) {
        for ((k, v) in m) when (v) {
            is Boolean -> { values[k] = v; store?.put(k, v) }
            is Number -> { values[k] = v.toFloat(); store?.put(k, v.toFloat()) }
            is String -> { values[k] = v; store?.put(k, v) }
        }
    }

    // The keys.
    const val CROSSFADE = "crossfade"            // song crossfade
    const val CROSSFADE_SECS = "crossfade_secs"  // its length
    const val SMART_TRANSITIONS = "smart_transitions"
    const val CROSSFEED = "crossfeed"
    const val MONO = "mono"                      // Now playing's Mono: both sides in each ear
    const val AUTO_MONO = "auto_mono"            // Mono on its own while the chosen earbuds are connected
    const val CLIP_GUARD = "clip_guard"          // a peak limiter instead of hard clipping
    const val DITHER = "dither"                  // TPDF dither when cut to 16-bit
    const val BALANCE = "balance"                // -1 … 1 (a number)
    const val CROSSFEED_K = "crossfeed_k"        // crossfeed's strength (a number: 0.2, 0.3, 0.45)
    const val DETAIL = "detail"                  // Detail: background sounds brought forward
    const val TILT = "tilt"                      // dB: > 0 brighter, < 0 warmer (a number)
    const val RG_TARGET = "rg_target"            // ReplayGain's levelling target, LUFS (a number: -23, -18, -16, -14)
    const val LOUDNESS = "loudness_comp"         // loudness compensation at low volume
    const val AUTO_MONO_DEVICE = "auto_mono_device"   // which earbuds (their Bluetooth name)
    const val CONTINUE = "continue_across"       // continue where you left off, across the PC and the phone
    const val SMART_SHUFFLE = "smart_shuffle"
    const val LOUDNESS_CHECK = "loudness_check"
    const val LOCK_SCREEN = "lock_screen_player"
    const val NOTIF_HINTED = "notif_hinted"
}
