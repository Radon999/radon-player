package org.omarchy.music

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlin.math.log10
import kotlin.math.max

/** Loudness compensation (Settings › Sound): turned down, music loses its bass and treble to the ear first (the equal-
 *  loudness curves); this lifts them back by as much as the volume is down, and not at all at full volume. It follows
 *  the phone's media volume. */
object Loudness {
    private var am: AudioManager? = null

    fun start(ctx: Context) {
        am = ctx.getSystemService(AudioManager::class.java)
        ctx.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = apply()
        })
        apply()
    }

    /** How far the media volume is down from full, in dB (Android's volume steps are about 2–3 dB apart). */
    fun downDb(): Double {
        val m = am ?: return 0.0
        val v = m.getStreamVolume(AudioManager.STREAM_MUSIC)
        val top = max(1, m.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        if (v <= 0) return 0.0
        return max(0.0, -40.0 * log10(v.toDouble() / top))   // a step curve close to Android's own
    }

    fun apply() {
        Music.eqProcessor.loudnessDb = if (Prefs.on(Prefs.LOUDNESS)) downDb() else 0.0
    }
}
