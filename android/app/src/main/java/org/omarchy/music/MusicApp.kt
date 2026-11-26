package org.omarchy.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.omarchy.music.ui.FileArtFetcher

/** The process: the music and the services start with it, whether the window or the
 *  playback service came first; covers load through Coil, the phone's own songs' too. */
class MusicApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Crashes.init(this)
        // Off the screen until a screen of the app starts (AppVisible): started by the music alone (Android Auto, a
        // headset's button, the service coming back), the timers that only serve the screen rest from the first moment.
        AppVisible.set(false)
        MainActivity.watchScreens(this)
        // The last start closed with a crash within seconds: this time nothing starts but the report (the window shows
        // it, to send on), so a crash at start can always be seen and fixed.
        val starting = java.io.File(filesDir, "starting")
        val report = java.io.File(filesDir, "problems.txt")
        if (starting.exists() && report.isFile && System.currentTimeMillis() - report.lastModified() < 15 * 60_000L) {
            starting.delete(); safeMode = true; return
        }
        runCatching { starting.writeText("1") }
        android.os.Handler(mainLooper).postDelayed({ starting.delete() }, 10_000)
        appliesChanges()
        Music.init(this)
        LastFm.init(this)
        SpotifyHistory.init(this)
        DeezerHistory.init(this)
        YouTubeHistory.init(this)
        TidalHistory.init(this)
        AppleHistory.init(this)
        StatHides.init(this)
        NewReleases.init(this)
        Stats.init(this)
        Pictures.init(this)
        PhoneShare.init(this)
        Services.start(this)
        Updater.init(this)
        runCatching { AutoMono.start(this) }
        runCatching { Loudness.start(this) }
    }

    companion object {
        @Volatile var safeMode = false   // the last start crashed: only the crash report is shown
        private var applying = false
    }

    /** The music's state is Compose state, and what follows it outside a screen (the home screen widgets, the music's
     *  notification buttons) hears of a change only once it's "applied". The app's screens do that while one is drawn;
     *  started without one (a widget's tap after Android closed the app, a headset button) nothing did, and the widgets
     *  stood still. So the process does it itself: a change made, then applied once on the main thread soon after (one
     *  waiting at a time, however many changes come meanwhile). Once a process. */
    private fun appliesChanges() {
        if (applying) return
        applying = true
        val waiting = java.util.concurrent.atomic.AtomicBoolean(false)
        val main = android.os.Handler(mainLooper)
        androidx.compose.runtime.snapshots.Snapshot.registerGlobalWriteObserver {
            if (waiting.compareAndSet(false, true)) main.post {
                waiting.set(false)
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(FileArtFetcher.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        // Every cover from the web kept on the phone after its first load (512 MB, the oldest go first), whatever the
        // server says about caching: shown from there after, never downloaded again.
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("art")).maxSizeBytes(512L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
