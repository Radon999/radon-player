package org.omarchy.music

import org.omarchy.music.ui.artColor
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** What the home screen widgets share: their look (the playing song's cover colour, a little see-through, rounded, with
 *  white writing: the first widget's), the taps that open the app at a place (see ui.AppLinks), and keeping them all up
 *  to date while the music's service runs (the song, the queue, the DJ, your playlists and pins, the Lyrics widget's line,
 *  the Visualizer's bars). */
object HomeWidgets {
    /** MainActivity's extra: a place to open (ui.AppLinks says which). */
    const val OPEN = "org.omarchy.music.open"
    private const val OPEN_ACTION = "org.omarchy.music.OPEN_PLACE"

    // ---- the look ----
    @Volatile private var cover: Pair<String, Bitmap?> = "" to null
    @Volatile private var colour: Int = Color.rgb(40, 40, 46)

    /** The song's cover (the PC's song's while this phone is its remote), or null while it loads (once a song; every
     *  widget is drawn again when it's in). Any thread. */
    fun coverOf(ctx: Context, t: Track?): Bitmap? {
        val key = t?.id ?: ""
        if (cover.first != key) {   // the cover and its colour, once a song
            cover = key to null
            if (t != null) {
                val model = if (Remote.active) Remote.artModel(t) else org.omarchy.music.ui.artModel(t, 400)
                Music.bg({
                    val res = kotlinx.coroutines.runBlocking { coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx)
                        .data(model).size(400).allowHardware(false).build()) }
                    (res as? coil.request.SuccessResult)?.drawable?.toBitmap()
                }) { bmp, _ ->
                    if (cover.first == key && bmp != null) {
                        cover = key to bmp
                        colour = runCatching { org.omarchy.music.ui.Tint.artColor(bmp) }.getOrDefault(colour)
                        updateAll(ctx)
                    }
                }
            }
        }
        return cover.second
    }

    /** The background: the cover's own hue, made dark and calm enough that white writing reads well on it (a yellow
     *  or green cover's light colour made deep), a little see-through. */
    fun background(ctx: Context): Int = ColorUtils.setAlphaComponent(deep(ctx), 0xD9)

    /** The background's colour, solid: deep enough for white writing (at least 4.5 to 1, as for text that must read). */
    private fun deep(ctx: Context): Int {
        coverOf(ctx, Music.shownTrack())
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colour, hsl)
        hsl[1] = hsl[1].coerceAtMost(0.6f)
        hsl[2] = hsl[2].coerceIn(0.12f, 0.42f)
        var c = ColorUtils.HSLToColor(hsl)
        while (hsl[2] > 0.1f && ColorUtils.calculateContrast(Color.WHITE, c) < 4.5) { hsl[2] -= 0.02f; c = ColorUtils.HSLToColor(hsl) }
        return c
    }

    /** A light shade of the cover's own colour, for what's drawn on the background (the Visualizer widget's bars). */
    fun accent(): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colour, hsl)
        hsl[1] = hsl[1].coerceIn(0.35f, 0.9f)
        hsl[2] = 0.72f
        return ColorUtils.HSLToColor(hsl)
    }

    /** The rounded background in the cover's colour: every widget's layout has it as a picture behind everything
     *  (w_bg: the rounded shape, Android 12's own corners there), coloured and made a little see-through here. A picture,
     *  not the layout's own background, so Android 10 and 11 keep the rounded corners too. */
    fun paint(ctx: Context, v: RemoteViews) = paint(v, deep(ctx))

    /** The same, with the colour worked out already (the Visualizer's frames, off the main thread). */
    fun paint(v: RemoteViews, solid: Int) {
        v.setInt(R.id.w_bg, "setColorFilter", ColorUtils.setAlphaComponent(solid, 0xFF))
        v.setInt(R.id.w_bg, "setImageAlpha", 0xD9)
    }

    /** The background's solid colour (for [paint] off the main thread). */
    fun solid(ctx: Context): Int = deep(ctx)

    /** One of a two-way switch's halves (the DJ's songs, a playlist's order): lit when chosen. */
    fun choice(v: RemoteViews, id: Int, on: Boolean) {
        v.setInt(id, "setBackgroundResource", if (on) R.drawable.w_chip_on else R.drawable.w_chip_off)
        v.setFloat(id, "setAlpha", if (on) 1f else 0.7f)
        v.setTextColor(id, Color.WHITE)
    }

    // ---- taps ----
    /** Opens the app at a place (link: see ui.AppLinks; null: as it is). code: a number of its own for each place. */
    fun open(ctx: Context, link: String?, code: Int): PendingIntent = PendingIntent.getActivity(ctx, code,
        Intent(ctx, MainActivity::class.java).setAction(OPEN_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { if (link != null) putExtra(OPEN, link) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** A list's rows open the app at the place each row names (its fill-in carries OPEN). */
    fun openTemplate(ctx: Context, code: Int): PendingIntent = PendingIntent.getActivity(ctx, code,
        Intent(ctx, MainActivity::class.java).setAction(OPEN_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** A tap handled by a widget's own receiver (cls), as action. */
    fun tap(ctx: Context, cls: Class<*>, action: String, code: Int = 0, extras: (Intent.() -> Unit)? = null): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, cls).setAction(action).apply { extras?.invoke(this) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** (MainActivity) What a widget's tap asked of the window: a place to open, or music to start that couldn't be
     *  started without the app. Never again for the same intent (the window made again, or opened from Recents). */
    fun asked(ctx: Context, i: Intent?) {
        if (i == null || i.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        i.getStringExtra(OPEN)?.let { org.omarchy.music.ui.AppLinks.asked = it }
        i.getStringExtra(WidgetStart.EXTRA_DO)?.let { WidgetStart.fromApp(ctx, it) }
        i.removeExtra(OPEN); i.removeExtra(WidgetStart.EXTRA_DO)
    }

    // ---- keeping them up to date ----
    /** Whether the home screen can be seen at all: the screen on and the phone unlocked (followed while the music's
     *  service runs), and the app itself not in front. The Lyrics and Visualizer widgets move only then (Compose state:
     *  they wait on it). */
    var unlocked by mutableStateOf(true)
    var appShown by mutableStateOf(false)

    fun ids(ctx: Context, cls: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls)) }.getOrNull() ?: IntArray(0)

    /** Every widget of ours on the home screen, drawn again. */
    fun updateAll(ctx: Context, player: Boolean = true) {
        if (player) runCatching { PlayerWidget.update(ctx) }
        val each = listOf<(Context) -> Unit>({ ListsWidget.update(it) }, { DjWidget.update(it) }, { PlaylistWidget.update(it) },
            { PinnedWidget.update(it) }, { TopWidget.update(it) }, { WrappedWidget.update(it) }, { LyricsWidget.update(it) },
            { ReleasesWidget.update(it) }, { VisualizerWidget.update(it) })
        for (u in each) runCatching { u(ctx) }
    }

    private var jobs = emptyList<Job>()
    private var screenWatch: BroadcastReceiver? = null

    /** (MusicService, as it starts) The widgets follow the music from now: the song, played or paused, the queue, the
     *  DJ, the lyrics' mode; your playlists, pins and new releases as they change; the Lyrics widget's line and the
     *  Visualizer widget's bars, while the home screen can be seen. */
    fun follow(svc: Service) {
        stop(svc)
        val pm = svc.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val km = svc.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        unlocked = pm.isInteractive && !km.isKeyguardLocked
        screenWatch = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // The screen on unlocked, or unlocked now (the lock screen's own: nothing of the home screen shows there)
                unlocked = when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> false
                    Intent.ACTION_USER_PRESENT -> true
                    else -> !km.isKeyguardLocked
                }
            }
        }.also { r ->
            runCatching { androidx.core.content.ContextCompat.registerReceiver(svc, r, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
        }
        jobs = listOf(
            // The first widget: the song (the PC's, as its remote), playing or paused, repeat, and the queue itself (a song
            // moved or the queue shuffled changes neither its length nor the place playing)
            Music.scope.launch {
                snapshotFlow { listOf(Music.shownTrack()?.id, Music.paused, Music.repeat, Music.index, Music.queue,
                    if (Remote.active) listOf(Remote.status.paused, Remote.status.index, Remote.status.queue, Remote.status.repeat) else null) }.collect {
                    delay(150)
                    runCatching { PlayerWidget.update(svc) }
                }
            },
            Music.scope.launch {
                snapshotFlow { listOf(Music.shownTrack()?.id, Music.paused, Music.repeat, Music.dj.on, Music.djBusy, Music.djNew, Music.lyricsMode,
                    if (Remote.active) Remote.status.paused else null) }.collectLatest {
                    delay(150)
                    runCatching { updateAll(svc, player = false) }
                }
            },
            // Your playlists, pins and plays changed: the lists on the widgets read again (once things settle)
            Music.scope.launch {
                snapshotFlow { Music.musicVersion to Music.pins.toList() }.drop(1).collectLatest {
                    delay(2000)
                    runCatching { ListsWidget.update(svc, rows = true); PinnedWidget.update(svc, rows = true); TopWidget.refresh(svc); PlaylistWidget.update(svc) }
                }
            },
            // New releases found (the app's own look, or Refresh… on its screen): the widget's list read again
            Music.scope.launch {
                snapshotFlow { NewReleases.found to NewReleases.told }.drop(1).collectLatest {
                    delay(1500)
                    runCatching { ReleasesWidget.update(svc, rows = true) }
                }
            },
            // Back on the home screen after a while: what tells the day (New releases' "Today") and Most played made fresh
            Music.scope.launch {
                snapshotFlow { unlocked }.drop(1).collect { on ->
                    if (on) runCatching { ReleasesWidget.newDay(svc); TopWidget.update(svc) }
                }
            },
            Music.scope.launch { AppVisible.flow.collect { appShown = it } },
            Music.scope.launch { LyricsWidget.follow(svc) },
            Music.scope.launch { VisualizerWidget.follow(svc) },
        )
    }

    fun stop(svc: Service) {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        screenWatch?.let { runCatching { svc.unregisterReceiver(it) } }
        screenWatch = null
    }
}
