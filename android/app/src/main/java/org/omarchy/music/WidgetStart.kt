package org.omarchy.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Music started from a home screen widget (the DJ, a playlist) without opening the app.
 *
 *  The music plays in MusicService, and Android lets an app in the background start playing only as a foreground
 *  service (one with a notification), and only straight after a tap such as a widget's. So a widget's tap ([request])
 *  starts the service in the foreground with a "Starting…" notification of its own ([begin]), which keeps it there
 *  while the DJ picks its first set or the playlist's first song loads; once the music plays, the music's own
 *  notification takes over ([handedOver], from MusicService.onUpdateNotification). With music already playing, the
 *  change is made at once. Should Android still say no, the app is asked to open and do it ([offer]: by itself where
 *  the phone lets it, else a notification to tap). */
object WidgetStart {
    const val ACTION = "org.omarchy.music.WIDGET_START"
    /** What to start: "dj", or a playlist (PhoneWidgetRules.list). MusicService's extra, and MainActivity's for [offer]. */
    const val EXTRA_DO = "org.omarchy.music.do"
    private const val CHANNEL = "widget-start"
    private const val ID = 7302         // (the lock screen player's is 7301)
    private const val OFFER_ID = 7303

    /** Since when music asked for by a widget is awaited (elapsed ms; 0: none is). */
    @Volatile private var since = 0L
    private var seq = 0

    /** A widget's music is being started: the service's own notification keeps it in the foreground until it plays. */
    fun waiting() = since > 0

    /** (a widget's receiver) Start what. */
    fun request(ctx: Context, what: String) {
        Music.init(ctx)
        if (PhoneWidgetRules.startHow(Music.player != null, !Music.paused, Remote.active) == "now") {
            Music.scope.launch { if (!perform(what)) tellFailed(ctx.applicationContext, what) }
            return
        }
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MusicService::class.java).setAction(ACTION).putExtra(EXTRA_DO, what))
        } catch (e: Exception) {
            Crashes.note("A widget couldn't start the music without the app (${e.javaClass.simpleName}): the app was asked to")
            offer(ctx, what)
        }
    }

    /** (MusicService.onStartCommand, for [ACTION]) In the foreground at once, as Android requires, then the music. */
    fun begin(svc: MusicService, intent: Intent, session: MediaSession?) {
        val what = intent.getStringExtra(EXTRA_DO) ?: return
        try {
            ServiceCompat.startForeground(svc, ID, notification(svc, "Starting ${named(what)}…", "Radon Player", null, ongoing = true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (e: Exception) {
            Crashes.note("A widget's music couldn't keep the app in the foreground (${e.javaClass.simpleName}): the app was asked to start it")
            offer(svc, what)
            return
        }
        since = SystemClock.elapsedRealtime()
        val mine = ++seq
        // The music's notification follows the session from now (with the app's window open, the window asks for that)
        session?.let { s -> runCatching { if (!svc.isSessionAdded(s)) svc.addSession(s) } }
        val dj = what == "dj"
        Music.scope.launch {
            val ok = perform(what)
            // Playing, or the DJ having given up (it says why in the app)
            val playing = ok && withTimeoutOrNull(if (dj) 180_000L else 90_000L) {
                snapshotFlow { (!Music.paused && Music.current() != null) to (dj && Music.djBusy == null && !Music.dj.on) }
                    .first { it.first || it.second }.first
            } == true
            if (mine != seq || !waiting()) return@launch
            if (!playing) { giveUp(svc, session, what); return@launch }
            // Playing: the music's notification takes over as the player reports it (MusicService). Should that not have
            // come by in a few seconds, it's handed over now, as things stand (playing: the music's notification keeps the
            // app in the foreground; paused again meanwhile: as after any pause).
            delay(4000)
            if (mine != seq || !waiting()) return@launch
            since = 0L
            session?.let { s ->
                val p = s.player
                runCatching { svc.onUpdateNotification(s, p.playWhenReady && (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING)) }
            }
            handedOver(svc)
        }
    }

    /** (MusicService) The music plays and its own notification now keeps the app in the foreground: ours goes. */
    fun handedOver(ctx: Context) {
        since = 0L
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(ID) }
    }

    /** It couldn't be started: out of the foreground (the music's notification back as it was), and a note saying so. */
    private fun giveUp(svc: MusicService, session: MediaSession?, what: String) {
        since = 0L
        runCatching { ServiceCompat.stopForeground(svc, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        val nm = svc.getSystemService(NotificationManager::class.java)
        runCatching { nm.cancel(ID) }
        session?.let { s -> runCatching { svc.onUpdateNotification(s, false) } }
        tellFailed(svc, what)
    }

    /** A note that what a widget asked for couldn't be played, and why where it's known; a tap opens the app (on the
     *  release, for a new release: its page there). */
    private fun tellFailed(ctx: Context, what: String) {
        val (kind, rest) = PhoneWidgetRules.split(what)
        val release = if (kind == "release") ReleasesWidget.find(rest) else null
        val list = PhoneWidgetRules.playlistOf(what)?.first?.let { Music.lists.get(it) }
        val (title, text) = when {
            what == "dj" -> "The DJ couldn't start" to "Tap to open Radon Player and see why"
            kind == "release" -> (if (release == null) "That release isn't in New releases any more" else "Couldn't find “${release.title}” to play") to
                "Tap to open it in Radon Player"
            list == null -> "That playlist was deleted" to "Tap the widget and pick another"
            list.tracks.isEmpty() -> "“${list.name}” has no songs yet" to "Add some in Radon Player"
            else -> "Couldn't play “${list.name}”" to "Tap to open Radon Player and see why"
        }
        val open = if (kind == "release") HomeWidgets.open(ctx, if (release != null) what else "newreleases", 7471)
            else PendingIntent.getActivity(ctx, OFFER_ID, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(OFFER_ID, notification(ctx, title, text, open, ongoing = false)) }
    }

    /** Android said no: the app opens and starts it (by itself where the phone lets it; else from a notification). */
    private fun offer(ctx: Context, what: String) {
        val open = Intent(ctx, MainActivity::class.java).setAction("org.omarchy.music.WIDGET_DO")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_DO, what)
        runCatching { ctx.startActivity(open) }
        val pi = PendingIntent.getActivity(ctx, OFFER_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(OFFER_ID,
            notification(ctx, "Tap to start ${named(what)}", "Android wanted the app open for this", pi, ongoing = false)) }
    }

    /** (MainActivity) A widget's music the app was opened for ([offer]): started now. */
    fun fromApp(ctx: Context, what: String) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(OFFER_ID) }
        Music.scope.launch { perform(what) }
    }

    /** Does it (the main thread): false when it can't (no such playlist, nothing in it, the DJ with nothing to play, a
     *  release not found on the services). */
    suspend fun perform(what: String): Boolean {
        // Just after the app started (a widget can start it): your songs are read first, so the DJ and the playlist find them
        withTimeoutOrNull(20_000L) { while (Music.local.scanning) delay(150) }
        if (what == "dj") {
            val paused = if (Remote.active) Remote.status.paused else Music.paused
            return when (PhoneWidgetRules.djTap(Music.shownDj().on, paused)) {
                "resume" -> { Music.sessionPlay(); true }
                "switch" -> { Music.djSwitch(); true }
                else -> { Music.djStart(); Remote.active || Music.dj.on || Music.djBusy != null }
            }
        }
        val (kind, rest) = PhoneWidgetRules.split(what)
        if (kind == "release") {
            // A new release: found on Tidal or YouTube Music as its page in the app is (those come up as the app starts:
            // a few seconds are given when it was closed), and played from its first song
            val r = ReleasesWidget.find(rest) ?: return false
            withTimeoutOrNull(10_000L) { while (Music.sources["tidal"] == null && Music.sources["ytm"] == null) delay(200) }
            val songs = withContext(Dispatchers.IO) { runCatching { Music.releaseTracks(r) }.getOrDefault(emptyList()) }
            if (songs.isEmpty()) return false
            Music.listName = r.title
            Music.playList(songs)
            return true
        }
        val (id, shuffled) = PhoneWidgetRules.playlistOf(what) ?: return false
        val pl = Music.lists.get(id) ?: return false
        val songs = Music.sortedPlaylist(pl.tracks)   // in the order the playlist shows them (its sort), as its Play and Shuffle
        if (songs.isEmpty()) return false
        Music.listName = pl.name   // the new queue takes the playlist's name, as when it's played in the app
        Music.playList(songs, shuffled = shuffled)
        return true
    }

    /** What's being started, as a notification names it: the DJ, “a playlist”, “a release”. */
    private fun named(what: String): String {
        if (what == "dj") return "the DJ"
        val (kind, rest) = PhoneWidgetRules.split(what)
        if (kind == "release") return ReleasesWidget.find(rest)?.let { "“${it.title}”" } ?: "the release"
        return PhoneWidgetRules.playlistOf(what)?.first?.let { Music.lists.get(it) }?.let { "“${it.name}”" } ?: "the music"
    }

    private fun notification(ctx: Context, title: String, text: String, tap: PendingIntent?, ongoing: Boolean): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Music started from a widget", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while the DJ or a playlist started from a home screen widget gets going"
                setShowBadge(false)
            })
        return Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentTitle(title).setContentText(text).setOngoing(ongoing).setAutoCancel(!ongoing)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setContentIntent(tap ?: PendingIntent.getActivity(ctx, ID, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
    }
}
