package org.omarchy.music

import org.omarchy.music.ui.artColor
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
 *  to date while the music's service runs (the song, the DJ, your playlists and pins, the Lyrics widget's line). */
object HomeWidgets {
    /** MainActivity's extra: a place to open (ui.AppLinks says which). */
    const val OPEN = "org.omarchy.music.open"
    private const val OPEN_ACTION = "org.omarchy.music.OPEN_PLACE"

    // ---- the look ----
    @Volatile private var cover: Pair<String, Bitmap?> = "" to null
    @Volatile private var colour: Int = Color.rgb(40, 40, 46)

    /** The song's cover, or null while it loads (once a song; every widget is drawn again when it's in). Any thread. */
    fun coverOf(ctx: Context, t: Track?): Bitmap? {
        val key = t?.id ?: ""
        if (cover.first != key) {   // the cover and its colour, once a song
            cover = key to null
            if (t != null) Music.bg({
                val res = kotlinx.coroutines.runBlocking { coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx)
                    .data(org.omarchy.music.ui.artModel(t, 400)).size(400).allowHardware(false).build()) }
                (res as? coil.request.SuccessResult)?.drawable?.toBitmap()
            }) { bmp, _ ->
                if (cover.first == key && bmp != null) {
                    cover = key to bmp
                    colour = runCatching { org.omarchy.music.ui.Tint.artColor(bmp) }.getOrDefault(colour)
                    updateAll(ctx)
                }
            }
        }
        return cover.second
    }

    /** The background: the cover's own hue, deep enough for white writing, a little see-through. */
    fun background(ctx: Context): Int {
        coverOf(ctx, Music.current())
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colour, hsl)
        hsl[2] = hsl[2].coerceIn(0.18f, 0.42f)   // deep enough for white text, the cover's own hue
        return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), 0xD9)   // a little see-through
    }

    /** The rounded background (root), tinted. */
    fun paint(ctx: Context, v: RemoteViews, root: Int) {
        val bg = background(ctx)
        if (android.os.Build.VERSION.SDK_INT >= 31) v.setColorStateList(root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(bg))
        else v.setInt(root, "setBackgroundColor", bg)
    }

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
    fun ids(ctx: Context, cls: Class<*>): IntArray =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls)) }.getOrNull() ?: IntArray(0)

    /** Every widget of ours on the home screen, drawn again (player: the first one too; the music's service keeps that
     *  one up to date by itself). */
    fun updateAll(ctx: Context, player: Boolean = true) {
        if (player) PlayerWidget.update(ctx)
        val each = listOf<(Context) -> Unit>({ ListsWidget.update(it) }, { DjWidget.update(it) }, { PlaylistWidget.update(it) },
            { PinnedWidget.update(it) }, { TopWidget.update(it) }, { WrappedWidget.update(it) }, { LyricsWidget.update(it) })
        for (u in each) runCatching { u(ctx) }
    }

    private var jobs = emptyList<Job>()
    private var screenWatch: BroadcastReceiver? = null

    /** (MusicService, as it starts) The widgets follow the music from now: the song, played or paused, the DJ, the
     *  lyrics' mode; your playlists and pins as they change; the Lyrics widget's line, while the screen is on. */
    fun follow(svc: Service) {
        stop(svc)
        val pm = svc.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        LyricsWidget.screenOn = pm.isInteractive
        screenWatch = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { LyricsWidget.screenOn = i.action == Intent.ACTION_SCREEN_ON }
        }.also { r ->
            runCatching { androidx.core.content.ContextCompat.registerReceiver(svc, r, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
        }
        jobs = listOf(
            Music.scope.launch {
                snapshotFlow { listOf(Music.current()?.id, Music.paused, Music.repeat, Music.dj.on, Music.djBusy, Music.djNew, Music.lyricsMode,
                    if (Remote.active) Remote.status.track?.id to Remote.status.paused else null) }.collectLatest {
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
            Music.scope.launch { AppVisible.flow.collect { LyricsWidget.appShown = it } },
            Music.scope.launch { LyricsWidget.follow(svc) },
        )
    }

    fun stop(svc: Service) {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        screenWatch?.let { runCatching { svc.unregisterReceiver(it) } }
        screenWatch = null
    }
}
