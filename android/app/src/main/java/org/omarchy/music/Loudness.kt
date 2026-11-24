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
 *  the phone's media volume: Android's settings are watched for it only while it's on (the watch wakes the app for
 *  every setting any app changes, which is nothing to do while it's off). */
object Loudness {
    private var ctx: Context? = null
    private var am: AudioManager? = null
    private var watching: ContentObserver? = null

    fun start(ctx: Context) {
        this.ctx = ctx.applicationContext
        am = ctx.getSystemService(AudioManager::class.java)
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

    /** (the main thread: started, and each time a sound switch changes) */
    fun apply() {
        val on = Prefs.on(Prefs.LOUDNESS)
        watch(on)
        Music.eqProcessor.loudnessDb = if (on) downDb() else 0.0
    }

    private fun watch(on: Boolean) {
        val c = ctx ?: return
        if (on && watching == null) {
            val o = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) = apply() }
            runCatching { c.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, o) }.onSuccess { watching = o }
        } else if (!on) watching?.let { o -> runCatching { c.contentResolver.unregisterContentObserver(o) }; watching = null }
    }
}
