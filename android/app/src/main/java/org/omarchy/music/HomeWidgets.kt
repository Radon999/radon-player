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
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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

    /** The app's look as the widgets wear it, with Widgets in the app's look on (Settings › Personalization; a preset's
     *  look when one is on, the Now playing look in this cover's colours); null: off, the cover's colour as always. */
    fun copied(): org.omarchy.music.ui.WidgetLook.Copied? = org.omarchy.music.ui.WidgetLook.colors(colour.takeIf { cover.first.isNotEmpty() })

    /** The writing and icons' colour: the look's writing (dark on a light look), else white. */
    fun ink(): Int = copied()?.fg?.toArgb() ?: Color.WHITE

    /** The background: the cover's own hue, made dark and calm enough that white writing reads well on it (a yellow
     *  or green cover's light colour made deep), a little see-through. */
    fun background(ctx: Context): Int = ColorUtils.setAlphaComponent(deep(ctx), 0xD9)

    /** The background's colour, solid: deep enough for white writing (at least 4.5 to 1, as for text that must read). */
    private fun deep(ctx: Context): Int {
        coverOf(ctx, Music.shownTrack())
        copied()?.let { return it.solid.toArgb() or 0xFF000000.toInt() }   // the look's own background
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
        copied()?.let { return it.accent.toArgb() }
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colour, hsl)
        hsl[1] = hsl[1].coerceIn(0.35f, 0.9f)
        hsl[2] = 0.72f
        return ColorUtils.HSLToColor(hsl)
    }

    /** The rounded background in the cover's colour: every widget's layout has it as a picture behind everything
     *  (w_bg: the rounded shape, Android 12's own corners there), coloured and made a little see-through here. A picture,
     *  not the layout's own background, so Android 10 and 11 keep the rounded corners too. */
    fun paint(ctx: Context, v: RemoteViews) = paint(ctx, v, deep(ctx), ink())

    /** The same, with the colours worked out already (the Visualizer's, off the main thread): the background, and the
     *  writing and icons in ink (white, or the look's writing). */
    fun paint(ctx: Context, v: RemoteViews, solid: Int, ink: Int) {
        v.setInt(R.id.w_bg, "setColorFilter", ColorUtils.setAlphaComponent(solid, 0xFF))
        v.setInt(R.id.w_bg, "setImageAlpha", 0xD9)
        inked(ctx, v, ink)
    }

    /** Every piece of writing and every icon in a widget's layout (or a row's) in ink. White is how the layouts are
     *  drawn, so that's left as it is. The pieces are read once from the layout itself: its TextViews, its buttons, and
     *  the icons named in [ICONS] (never a cover: some covers stand in an icon's place until they're in). */
    fun inked(ctx: Context, v: RemoteViews, ink: Int = ink()) {
        if (ink == Color.WHITE) return
        val (texts, icons) = pieces(ctx, v.layoutId)
        for (id in texts) v.setTextColor(id, ink)
        for (id in icons) v.setInt(id, "setColorFilter", ink)
    }

    /** The icons drawn white on a widget that aren't buttons: these ImageViews (by their names). */
    private val ICONS = setOf("wd_icon", "wlr_icon", "wls_back", "w_head_icon")
    private val piecesOf = java.util.concurrent.ConcurrentHashMap<Int, Pair<IntArray, IntArray>>()
    private fun pieces(ctx: Context, layout: Int): Pair<IntArray, IntArray> = piecesOf.getOrPut(layout) {
        val texts = ArrayList<Int>(); val icons = ArrayList<Int>()
        val ns = "http://schemas.android.com/apk/res/android"
        runCatching {
            val x = ctx.resources.getLayout(layout)
            try {
                while (x.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (x.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                    if (x.name == "include") {   // a shared part (the volume row): its pieces too
                        val inner = x.getAttributeResourceValue(null, "layout", 0)
                        if (inner != 0) pieces(ctx, inner).let { (t, i) -> texts += t.toList(); icons += i.toList() }
                        continue
                    }
                    val id = x.getAttributeResourceValue(ns, "id", 0)
                    if (id == 0) continue
                    when (x.name.substringAfterLast('.')) {
                        "TextView" -> texts += id
                        "ImageButton" -> icons += id
                        "ImageView" -> if (runCatching { ctx.resources.getResourceEntryName(id) }.getOrNull() in ICONS) icons += id
                    }
                }
            } finally { x.close() }
        }
        texts.toIntArray() to icons.toIntArray()
    }

    /** The background's solid colour (for [paint] off the main thread). */
    fun solid(ctx: Context): Int = deep(ctx)

    /** The volume row (widget_volume) on a widget with play/pause: the media volume now as the bar, and its buttons a
     *  step down and up (PlayerWidget does them). Home screen widgets can't hold a slider that's dragged. */
    fun volume(ctx: Context, v: RemoteViews) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        v.setProgressBar(R.id.w_vol_bar, max, am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC), false)
        v.setOnClickPendingIntent(R.id.w_vol_down, PlayerWidget.pi(ctx, PlayerWidget.VOL_DOWN, 11))
        v.setOnClickPendingIntent(R.id.w_vol_up, PlayerWidget.pi(ctx, PlayerWidget.VOL_UP, 12))
        // The bar in the look's writing colour, where Android lets a widget change it (12 and up); white elsewhere
        val ink = ink()
        if (ink != Color.WHITE && android.os.Build.VERSION.SDK_INT >= 31)
            v.setColorStateList(R.id.w_vol_bar, "setProgressTintList", android.content.res.ColorStateList.valueOf(ink))
    }

    /** One of a two-way switch's halves (the DJ's songs, a playlist's order): lit when chosen. */
    fun choice(v: RemoteViews, id: Int, on: Boolean) {
        val dark = copied()?.light == true   // a light look: dark pills under dark writing
        v.setInt(id, "setBackgroundResource", if (on) (if (dark) R.drawable.w_chip_on_dark else R.drawable.w_chip_on)
            else if (dark) R.drawable.w_chip_off_dark else R.drawable.w_chip_off)
        v.setFloat(id, "setAlpha", if (on) 1f else 0.7f)
        v.setTextColor(id, ink())
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

    /** Whether one of the app's own screens is in front (not just the music's service running): Android's word for it. */
    private fun appInFront(ctx: Context): Boolean = runCatching {
        val me = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(me)
        me.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(true)

    /** The screen came on (or the phone was unlocked): wakes the check in [follow] from its rest. */
    private val screenOn = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
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
                if (i.action != Intent.ACTION_SCREEN_OFF) screenOn.trySend(Unit)
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
            Music.scope.launch { AppVisible.flow.collect { appShown = it && appInFront(svc) } },
            // …and checked against how things really are every few seconds: Android's notices aren't to be counted on.
            // The music started from a widget, the lock screen or headphones (the app never opened): "the app in
            // front" had stayed on, and the Visualizer widget never moved; an unlock missed: "locked" stayed on, and the
            // Lyrics widget kept "Nothing playing" and its visualizer stood still
            // Only while it can matter: a Lyrics or Visualizer widget is on the home screen (the others don't ask), and the
            // screen is on (off, the screen's own notice says so, and that one is reliable: nothing is asked until it's on
            // again)
            Music.scope.launch {
                while (true) {
                    snapshotFlow { LyricsWidget.present || VisualizerWidget.present }.first { it }
                    if (!runCatching { pm.isInteractive }.getOrDefault(true)) {
                        if (unlocked) unlocked = false
                        screenOn.receive()   // (the screen's coming on: sent by the receiver above)
                        continue
                    }
                    val screen = runCatching { !km.isKeyguardLocked }.getOrDefault(unlocked)
                    if (screen != unlocked) unlocked = screen
                    val front = AppVisible.on && appInFront(svc)
                    if (front != appShown) appShown = front
                    kotlinx.coroutines.withTimeoutOrNull(4000) { screenOn.receive() }
                }
            },
            // The volume rows follow the volume keys (and anything else that changes the media volume)
            Music.scope.launch {
                val changes = kotlinx.coroutines.flow.callbackFlow {
                    val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { trySend(Unit) } }
                    androidx.core.content.ContextCompat.registerReceiver(svc, r, IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                        androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
                    awaitClose { runCatching { svc.unregisterReceiver(r) } }
                }
                changes.collectLatest { delay(250); runCatching { updateAll(svc) } }
            },
            // Widgets in the app's look: drawn again as the look changes (a preset put on, another look picked, the
            // switch itself), and as the Visualizer widget's choices change
            Music.scope.launch {
                snapshotFlow { listOf(org.omarchy.music.ui.WidgetLook.copyLook(), copied()?.let { listOf(it.bg, it.fg, it.accent, it.gradient) },
                    Prefs.text(org.omarchy.music.ui.WidgetLook.VIS), Prefs.text(org.omarchy.music.ui.WidgetLook.VIS_COLOR)) }.drop(1).collectLatest {
                    delay(300)
                    runCatching { updateAll(svc) }
                }
            },
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
