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
        // The last start closed with a crash within seconds: this time nothing starts but the report (the window shows
        // it, to send on), so a crash at start can always be seen and fixed.
        val starting = java.io.File(filesDir, "starting")
        val report = java.io.File(filesDir, "problems.txt")
        if (starting.exists() && report.isFile && System.currentTimeMillis() - report.lastModified() < 15 * 60_000L) {
            starting.delete(); safeMode = true; return
        }
        runCatching { starting.writeText("1") }
        android.os.Handler(mainLooper).postDelayed({ starting.delete() }, 10_000)
        Music.init(this)
        LastFm.init(this)
        SpotifyHistory.init(this)
        DeezerHistory.init(this)
        YouTubeHistory.init(this)
        TidalHistory.init(this)
        AppleHistory.init(this)
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
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(FileArtFetcher.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("art")).maxSizeBytes(200L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
