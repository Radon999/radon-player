package org.omarchy.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import org.omarchy.music.ui.drawWidgetVis
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Visualizer: the music playing as Now playing's visualizer (Settings › Personalization › Widgets: or the Visualizer
 *  tab's, or one of its own), changing with it, in the colours the app draws it in (the cover's, or the look's with
 *  Widgets in the app's look), with the song under it and play/pause and next; a tap on it opens Now playing.
 *
 *  The bars move only while they can be seen and there's music: a Visualizer widget on the home screen, the screen on
 *  and the phone unlocked, the app not in front (its own visualizer then) and the music playing ([follow]); and for ten
 *  minutes at a time at most (Android can't tell an app whether the home screen itself is showing, so after that they
 *  rest until the screen goes off and on, the music is paused and played, or the song changes). Then Android's
 *  visualizer is opened on the app's own sound (as the app's Visualizer tab does, the same leave needed) and read about
 *  8 times a second, off the music's own threads; each frame is a picture of the visualizer at the widget's own size,
 *  drawn by the app's own drawing of that style ([drawWidgetVis]), and only that picture is sent. Otherwise nothing is read or drawn at all, and
 *  Android's visualizer is let go. Bit-perfect straight to a USB DAC: the bars come from the app's own output
 *  (AudioTap), as in the app. When the music can't be heard that way (the leave not given, a cast device, a phone that
 *  gives nothing), the bars sway slowly instead, a frame every second and a half. */
class VisualizerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { present = true; update(ctx) }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: android.os.Bundle) = update(ctx)

    override fun onDeleted(ctx: Context, ids: IntArray) { present = HomeWidgets.ids(ctx, VisualizerWidget::class.java).isNotEmpty() }

    override fun onDisabled(ctx: Context) { present = false }

    companion object {
        private const val BARS = PhoneWidgetRules.VIZ_BARS
        /** The picture's most pixels: drawn at the widget's own size up to this many, so each frame (in full colour,
         *  sent to the home screen several times a second) stays light enough to send. Always in the widget's own
         *  shape: it was capped at 360 by 200 whatever the widget's shape, and a tall widget stretched it five times up
         *  and twice across (fat bars, a circle an oval, blurred and faded). */
        private const val MAX_PIXELS = 90_000   // (about 350 KB a frame: Android takes up to about 500 KB at a time)

        /** A Visualizer widget on the home screen (Compose state: [follow] waits on it). */
        var present by mutableStateOf(false)

        /** What the widget shows besides the bars, made on the main thread ([update]). */
        private class Face(val title: String, val artist: String, val playing: Boolean, val solid: Int, val ink: Int, val hint: String?,
                           val open: PendingIntent, val play: PendingIntent, val next: PendingIntent)
        @Volatile private var running = false
        /** The visualizer as it moves (the app's own: its levels eased, its sparks, rings, history), stepped each frame. */
        @Volatile private var state = org.omarchy.music.ui.VisState()

        private fun playing() = !Remote.active && !Music.paused && Music.player != null
        private fun live() = PhoneWidgetRules.vizLive(present, HomeWidgets.unlocked, HomeWidgets.appShown, playing())
        private fun heard(ctx: Context) = AudioTap.active ||
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** Every one on the home screen, as things stand (the song, playing or paused, its colours). */
        fun update(ctx: Context) {
            val ids = HomeWidgets.ids(ctx, VisualizerWidget::class.java)
            present = ids.isNotEmpty()
            if (ids.isEmpty()) return
            Music.init(ctx)
            val t = Music.shownTrack()
            val f = Face(t?.title ?: "Nothing playing", t?.artist?.takeIf { it.isNotEmpty() } ?: "Visualizer",
                if (Remote.active) !Remote.status.paused else !Music.paused, HomeWidgets.solid(ctx), HomeWidgets.ink(),
                if (heard(ctx)) null else "For moving bars, open the Visualizer in the app once and allow what it asks",
                HomeWidgets.open(ctx, "player", 7480), PlayerWidget.pi(ctx, PlayerWidget.PLAY, 1), PlayerWidget.pi(ctx, PlayerWidget.NEXT, 3))
            // Still: as it last was while it moves (the frames carry on from it), else at rest
            val (w, h, dens) = size(ctx, ids)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            draw(bmp, if (running) state else resting(), dens)
            runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(ids, views(ctx, f, bmp)) }
        }

        private fun views(ctx: Context, f: Face, bars: Bitmap): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_visualizer)
            HomeWidgets.paint(ctx, v, f.solid, f.ink)
            HomeWidgets.volume(ctx, v)   // the volume row (as every widget with play/pause has)
            v.setImageViewBitmap(R.id.wv_bars, bars)
            v.setInt(R.id.wv_bars, "setColorFilter", 0)   // drawn in its own colours (the one-shade bars were tinted here)
            v.setTextViewText(R.id.wv_title, f.title)
            v.setTextViewText(R.id.wv_artist, f.artist)
            v.setImageViewResource(R.id.wv_play, if (f.playing) R.drawable.w_pause else R.drawable.w_play)
            v.setViewVisibility(R.id.wv_hint, if (f.hint != null) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.wv_hint, f.hint ?: "")
            v.setOnClickPendingIntent(R.id.wv_bars, f.open)
            v.setOnClickPendingIntent(R.id.wv_hint, f.open)
            v.setOnClickPendingIntent(R.id.wv_play, f.play)
            v.setOnClickPendingIntent(R.id.wv_next, f.next)
            return v
        }

        /** The picture's size (pixels): the visualizer's place in the largest widget as it's sized now (its edges and
         *  the song's line under it taken off), at the screen's own pixels, or as many as [MAX_PIXELS] allows in the same
         *  shape; and its pixels to a dp (the screen's density, less where the picture is made smaller), for the lines'
         *  widths in the drawing. */
        private fun size(ctx: Context, ids: IntArray): Triple<Int, Int, Float> {
            val mgr = AppWidgetManager.getInstance(ctx)
            val portrait = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            var wDp = 0; var hDp = 0
            for (id in ids) runCatching {
                val o = mgr.getAppWidgetOptions(id)
                wDp = maxOf(wDp, o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH))
                hDp = maxOf(hDp, o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
            }
            if (wDp <= 0 || hDp <= 0) { wDp = 250; hDp = 110 }
            val d = ctx.resources.displayMetrics.density
            val placeW = (wDp - 24f).coerceAtLeast(24f); val placeH = (hDp - 24f - 46f - 32f).coerceAtLeast(12f)   // (the song's line, and the volume row under it)
            // the screen's pixels, or fewer in the same shape (one scale both ways: never stretched one way more)
            val scale = minOf(d, kotlin.math.sqrt(MAX_PIXELS / (placeW * placeH)))
            val w = (placeW * scale).toInt().coerceAtLeast(48)
            val h = (placeH * scale).toInt().coerceAtLeast(16)
            return Triple(w, h, scale.coerceAtLeast(0.5f))
        }

        /** The levels at rest, or swaying slowly (phase moves them along): gentle, low. */
        private fun rest(phase: Float): FloatArray = FloatArray(org.omarchy.music.Spectrum.BANDS) { i ->
            val x = i * BARS.toFloat() / org.omarchy.music.Spectrum.BANDS
            0.18f + 0.14f * kotlin.math.sin(x * 0.55f + phase) + 0.06f * kotlin.math.sin(x * 1.7f - phase * 0.7f)
        }

        /** The visualizer at rest: eased up to the resting levels, as it would be after a second of them. */
        private fun resting(): org.omarchy.music.ui.VisState = org.omarchy.music.ui.VisState().apply {
            val r = rest(0f)
            repeat(30) { step(r, false, 1f / 30f) }
        }

        /** The visualizer into the picture, by the app's own drawing ([drawWidgetVis]): the style picked for the widget,
         *  in its colours ("Look's colour": the cover's light shade, or the look's accent), on nothing (the widget's
         *  background shows through). dens: the picture's pixels to a dp. */
        private fun draw(bmp: Bitmap, s: org.omarchy.music.ui.VisState, dens: Float) {
            bmp.eraseColor(Color.TRANSPARENT)
            val base = androidx.compose.ui.graphics.Color(HomeWidgets.accent())
            val fg = androidx.compose.ui.graphics.Color(HomeWidgets.ink())
            runCatching {
                androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(androidx.compose.ui.unit.Density(dens),
                    androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap()),
                    androidx.compose.ui.geometry.Size(bmp.width.toFloat(), bmp.height.toFloat())) { drawWidgetVis(s, base, fg) }
            }.onFailure { logWarn("The Visualizer widget couldn't draw: ${it.message}") }
        }

        /** (the music's service, for as long as it runs) The bars moved while they can be seen and music plays; nothing
         *  at all otherwise. A gap of a moment in the music (between two songs) doesn't stop them; the app coming in front,
         *  the screen going off or the phone locking stops them at once (in front, the app's own visualizer: two on the
         *  same sound get in each other's way). After [PhoneWidgetRules.VIZ_RUN_MS] they rest until something changes. */
        suspend fun follow(ctx: Context) = kotlinx.coroutines.coroutineScope {
            present = HomeWidgets.ids(ctx, VisualizerWidget::class.java).isNotEmpty()
            fun unseen() = !present || !HomeWidgets.unlocked || HomeWidgets.appShown
            try {
                while (true) {
                    snapshotFlow { live() }.first { it }
                    running = true
                    val song = Music.shownTrack()?.id
                    val frames = launch(Dispatchers.Default) { frames(ctx) }
                    val tooLong = withTimeoutOrNull(PhoneWidgetRules.VIZ_RUN_MS) {
                        while (true) {
                            snapshotFlow { live() }.first { !it }
                            if (unseen()) break
                            val back = withTimeoutOrNull(1500L) { snapshotFlow { live() || unseen() }.first { it } }
                            if (back == null || !live()) break
                        }
                    } == null
                    frames.cancelAndJoin()
                    running = false
                    state = org.omarchy.music.ui.VisState()
                    runCatching { update(ctx) }   // at rest
                    // Ran its time: at rest until the screen, the music or the song changes
                    if (tooLong) snapshotFlow { !live() || Music.shownTrack()?.id != song }.first { it }
                }
            } finally {
                running = false
            }
        }

        /** (off the main thread) Frame after frame until cancelled: the levels read, the bars drawn and sent. */
        private suspend fun frames(ctx: Context) {
            val cap = Capture()
            val mgr = AppWidgetManager.getInstance(ctx)
            var ids = IntArray(0)
            var bmp: Bitmap? = null
            var dens = 1f
            var n = 0
            var last = SystemClock.uptimeMillis()
            val quiet = FloatArray(org.omarchy.music.Spectrum.BANDS)
            var heardAt = SystemClock.uptimeMillis()
            var phase = 0f
            try {
                while (true) {
                    val start = SystemClock.uptimeMillis()
                    if (n++ % 90 == 0) {   // every 3 seconds or so: the widgets and their size, and the player's sound (a new session)
                        ids = HomeWidgets.ids(ctx, VisualizerWidget::class.java)
                        val (w, h, d) = size(ctx, ids)
                        dens = d
                        if (bmp == null || bmp.width != w || bmp.height != h) bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val session = withContext(Dispatchers.Main) { sessionNow() }
                        cap.follow(session, heard(ctx))
                    }
                    val bands = cap.bands()
                    val now = bands?.takeIf { b -> b.any { it > 0.02f } }
                    if (now != null) heardAt = start
                    // Nothing to hear for a few seconds (no leave, a cast device, a phone that gives nothing): swaying slowly
                    val slow = now == null && start - heardAt > 4000
                    val dt = ((start - last) / 1000f).coerceIn(0.02f, 0.2f)
                    last = start
                    state.step(when {
                        now != null -> now
                        slow -> rest(phase.also { phase += 0.5f })
                        else -> quiet
                    }, false, if (slow) 0.2f else dt)
                    val b = bmp
                    if (b != null && ids.isNotEmpty()) {
                        draw(b, state, dens)
                        // Only the bars' picture goes to the home screen each frame (the rest stays as last drawn)
                        val v = RemoteViews(ctx.packageName, R.layout.widget_visualizer)
                        v.setImageViewBitmap(R.id.wv_bars, b)
                        runCatching { mgr.partiallyUpdateAppWidget(ids, v) }
                    }
                    delay(if (slow) 1500L else (PhoneWidgetRules.VIZ_FRAME_MS - (SystemClock.uptimeMillis() - start)).coerceAtLeast(8L))
                }
            } finally {
                cap.close()
            }
        }

        private fun sessionNow() = ((Music.player as? ExoEngine)?.p as? androidx.media3.exoplayer.ExoPlayer)?.audioSessionId ?: 0

        /** Where the levels come from: the app's own output when it streams straight to a USB DAC (AudioTap), else
         *  Android's visualizer on the player's sound, open only while the bars move. */
        private class Capture {
            private var vis: android.media.audiofx.Visualizer? = null
            private var session = 0
            private var buf = ByteArray(0)

            fun follow(s: Int, allowed: Boolean) {
                if (AudioTap.active || !allowed) { close(); return }
                if (s == session && vis != null) return
                close()
                if (s == 0) return
                session = s
                vis = runCatching {
                    android.media.audiofx.Visualizer(s).apply {
                        captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                        enabled = true
                    }
                }.onFailure { logWarn("The Visualizer widget couldn't hear the music: ${it.message}") }.getOrNull()
                buf = ByteArray(vis?.captureSize ?: 0)
            }

            fun bands(): FloatArray? {
                if (AudioTap.active) return AudioTap.fft()?.let { Spectrum.bands(it, AudioTap.rate) }
                val v = vis ?: return null
                return runCatching {
                    if (v.getFft(buf) != android.media.audiofx.Visualizer.SUCCESS) null else Spectrum.bands(buf, v.samplingRate / 1000)   // millihertz
                }.getOrNull()
            }

            fun close() {
                vis?.runCatching { enabled = false; release() }
                vis = null
                session = 0
            }
        }
    }
}
