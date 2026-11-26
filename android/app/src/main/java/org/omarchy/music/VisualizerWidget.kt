package org.omarchy.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.getValue
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

/** Visualizer: the music playing as bars, in its cover's colours, with the song under them and play/pause and next; a
 *  tap on the bars opens Now playing.
 *
 *  The bars move only while they can be seen and there's music: a Visualizer widget on the home screen, the screen on
 *  and the phone unlocked, the app not in front (its own visualizer then) and the music playing ([follow]); and for ten
 *  minutes at a time at most (Android can't tell an app whether the home screen itself is showing, so after that they
 *  rest until the screen goes off and on, the music is paused and played, or the song changes). Then Android's
 *  visualizer is opened on the app's own sound (as the app's Visualizer tab does, the same leave needed) and read about
 *  8 times a second, off the music's own threads; each frame is a picture of the bars at the widget's own size (one
 *  shade, tinted on the home screen), and only that picture is sent. Otherwise nothing is read or drawn at all, and
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
        /** The picture's largest size (pixels): the bars drawn at the widget's own size up to this, so they're sharp
         *  without each frame growing heavy on a big widget. */
        private const val MAX_W = 420
        private const val MAX_H = 150

        /** A Visualizer widget on the home screen (Compose state: [follow] waits on it). */
        var present by mutableStateOf(false)

        /** What the widget shows besides the bars, made on the main thread ([update]). */
        private class Face(val title: String, val artist: String, val playing: Boolean, val solid: Int, val bar: Int, val hint: String?,
                           val open: PendingIntent, val play: PendingIntent, val next: PendingIntent)
        @Volatile private var running = false
        @Volatile private var shown = FloatArray(BARS)

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
                if (Remote.active) !Remote.status.paused else !Music.paused, HomeWidgets.solid(ctx), HomeWidgets.accent(),
                if (heard(ctx)) null else "For moving bars, open the Visualizer in the app once and allow what it asks",
                HomeWidgets.open(ctx, "player", 7480), PlayerWidget.pi(ctx, PlayerWidget.PLAY, 1), PlayerWidget.pi(ctx, PlayerWidget.NEXT, 3))
            // Still: the bars as they last were while they move (the frames carry on from them), else at rest
            val (w, h) = size(ctx, ids)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
            draw(Canvas(bmp), Paint(Paint.ANTI_ALIAS_FLAG), if (running) shown else rest(0f), w, h)
            runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(ids, views(ctx, f, bmp)) }
        }

        private fun views(ctx: Context, f: Face, bars: Bitmap): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_visualizer)
            HomeWidgets.paint(v, f.solid)
            v.setImageViewBitmap(R.id.wv_bars, bars)
            v.setInt(R.id.wv_bars, "setColorFilter", f.bar)   // the one-shade picture in the cover's colour
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

        /** The bars' picture size (pixels): the bars' place in the largest widget as it's sized now (its edges and the
         *  song's line under it taken off), at the screen's own pixels, up to [MAX_W] by [MAX_H]. */
        private fun size(ctx: Context, ids: IntArray): Pair<Int, Int> {
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
            val w = ((wDp - 24) * d).toInt().coerceIn(48, MAX_W)
            val h = ((hDp - 24 - 46) * d).toInt().coerceIn(16, MAX_H)
            return w to h
        }

        /** The bars at rest, or swaying slowly (phase moves them along): gentle, low. */
        private fun rest(phase: Float): FloatArray =
            FloatArray(BARS) { i -> 0.18f + 0.14f * kotlin.math.sin(i * 0.55f + phase) + 0.06f * kotlin.math.sin(i * 1.7f - phase * 0.7f) }

        /** The bars into the picture (w by h pixels): on whole pixels, so they're sharp, a little fainter at their foot. */
        private fun draw(c: Canvas, p: Paint, levels: FloatArray, w: Int, h: Int) {
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.argb(255, 0, 0, 0), Color.argb(140, 0, 0, 0), Shader.TileMode.CLAMP)
            val gap = maxOf(2, w / 90)
            val bar = maxOf(1, (w - gap * (levels.size - 1)) / levels.size)
            val left = (w - (bar * levels.size + gap * (levels.size - 1))) / 2   // what's left over, either side
            val r = minOf(bar / 3f, 4f)
            for (i in levels.indices) {
                val top = h - maxOf(2, (levels[i].coerceIn(0f, 1f) * (h - 1)).toInt())
                val x = left + i * (bar + gap)
                c.drawRoundRect(x.toFloat(), top.toFloat(), (x + bar).toFloat(), h.toFloat(), r, r, p)
            }
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
                    shown = FloatArray(BARS)
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
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cap = Capture()
            val mgr = AppWidgetManager.getInstance(ctx)
            var ids = IntArray(0)
            var bmp: Bitmap? = null
            var canvas = Canvas()
            var n = 0
            var heardAt = SystemClock.uptimeMillis()
            var phase = 0f
            try {
                while (true) {
                    val start = SystemClock.uptimeMillis()
                    if (n++ % 25 == 0) {   // every 3 seconds or so: the widgets and their size, and the player's sound (a new session)
                        ids = HomeWidgets.ids(ctx, VisualizerWidget::class.java)
                        val (w, h) = size(ctx, ids)
                        if (bmp == null || bmp.width != w || bmp.height != h) { bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8); canvas = Canvas(bmp) }
                        val session = withContext(Dispatchers.Main) { sessionNow() }
                        cap.follow(session, heard(ctx))
                    }
                    val bands = cap.bands()
                    val now = bands?.takeIf { b -> b.any { it > 0.02f } }?.let { PhoneWidgetRules.bars(it, BARS) }
                    if (now != null) heardAt = start
                    // Nothing to hear for a few seconds (no leave, a cast device, a phone that gives nothing): swaying slowly
                    val slow = now == null && start - heardAt > 4000
                    shown = when {
                        now != null -> PhoneWidgetRules.fall(shown, now)
                        slow -> rest(phase.also { phase += 0.5f })
                        else -> PhoneWidgetRules.fall(shown, FloatArray(BARS))
                    }
                    val b = bmp
                    if (b != null && ids.isNotEmpty()) {
                        draw(canvas, paint, shown, b.width, b.height)
                        // Only the bars' picture goes to the home screen each frame (the rest stays as last drawn)
                        val v = RemoteViews(ctx.packageName, R.layout.widget_visualizer)
                        v.setImageViewBitmap(R.id.wv_bars, b)
                        runCatching { mgr.partiallyUpdateAppWidget(ids, v) }
                    }
                    delay(if (slow) 1500L else (PhoneWidgetRules.VIZ_FRAME_MS - (SystemClock.uptimeMillis() - start)).coerceAtLeast(40L))
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
