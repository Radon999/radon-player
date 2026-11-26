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
 *  The bars move only while they can be seen and there's music: a Visualizer widget on the home screen, the screen on,
 *  the app not in front (its own visualizer then) and the music playing ([follow]). Then Android's visualizer is opened
 *  on the app's own sound (as the app's Visualizer tab does, the same leave needed) and read about 8 times a second, off
 *  the music's own threads; each frame is a small picture of the bars (one shade, tinted on the home screen). Otherwise
 *  nothing is read or drawn at all, and Android's visualizer is let go. Bit-perfect straight to a USB DAC: the bars come
 *  from the app's own output (AudioTap), as in the app. When the music can't be heard that way (the leave not given, a
 *  cast device, a phone that gives nothing), the bars sway slowly instead, a frame every second and a half. */
class VisualizerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { present = true; update(ctx) }

    override fun onDeleted(ctx: Context, ids: IntArray) { present = HomeWidgets.ids(ctx, VisualizerWidget::class.java).isNotEmpty() }

    override fun onDisabled(ctx: Context) { present = false }

    companion object {
        private const val W = 160
        private const val H = 56
        private const val BARS = PhoneWidgetRules.VIZ_BARS

        /** A Visualizer widget on the home screen (Compose state: [follow] waits on it). */
        var present by mutableStateOf(false)

        /** What every frame shows besides the bars, made on the main thread ([update]): the frames only add the bars. */
        private class Face(val title: String, val artist: String, val playing: Boolean, val bg: Int, val bar: Int, val hint: String?,
                           val open: PendingIntent, val play: PendingIntent, val next: PendingIntent)
        @Volatile private var face: Face? = null
        @Volatile private var running = false
        @Volatile private var shown = FloatArray(BARS)

        private fun playing() = !Remote.active && !Music.paused && Music.player != null
        private fun live() = PhoneWidgetRules.vizLive(present, HomeWidgets.screenOn, HomeWidgets.appShown, playing())
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
                if (Remote.active) !Remote.status.paused else !Music.paused, HomeWidgets.background(ctx), HomeWidgets.accent(),
                if (heard(ctx)) null else "For moving bars, open the Visualizer in the app once and allow what it asks",
                HomeWidgets.open(ctx, "player", 7480),
                if (Music.player == null && !Remote.active) HomeWidgets.open(ctx, "player", 7480) else PlayerWidget.pi(ctx, PlayerWidget.PLAY, 1),
                PlayerWidget.pi(ctx, PlayerWidget.NEXT, 3))
            face = f
            // Still: the bars as they last were while it moves (the frames carry on from them), else at rest
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ALPHA_8)
            draw(Canvas(bmp), Paint(Paint.ANTI_ALIAS_FLAG), if (running) shown else rest(0f, 0.6f))
            runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(ids, views(ctx, f, bmp)) }
        }

        private fun views(ctx: Context, f: Face, bars: Bitmap): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_visualizer)
            if (android.os.Build.VERSION.SDK_INT >= 31) v.setColorStateList(R.id.wv_root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(f.bg))
            else v.setInt(R.id.wv_root, "setBackgroundColor", f.bg)
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

        /** The bars at rest, or swaying slowly (phase moves them along): gentle, low. */
        private fun rest(phase: Float, height: Float): FloatArray =
            FloatArray(BARS) { i -> (0.18f + 0.14f * kotlin.math.sin(i * 0.55f + phase) + 0.06f * kotlin.math.sin(i * 1.7f - phase * 0.7f)) * height / 0.6f }

        /** The bars into the picture: rounded, a little fainter at their foot. */
        private fun draw(c: Canvas, p: Paint, levels: FloatArray) {
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            if (p.shader == null) p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Color.argb(255, 0, 0, 0), Color.argb(140, 0, 0, 0), Shader.TileMode.CLAMP)
            val gap = 2f
            val w = (W - gap * (levels.size - 1)) / levels.size
            for (i in levels.indices) {
                val h = (levels[i].coerceIn(0f, 1f) * (H - 2)).coerceAtLeast(2f)
                val x = i * (w + gap)
                c.drawRoundRect(x, H - h, x + w, H.toFloat(), 2f, 2f, p)
            }
        }

        /** (the music's service, for as long as it runs) The bars moved while they can be seen and music plays; nothing
         *  at all otherwise. A gap of a moment in the music (between two songs) doesn't stop them; the app coming in front
         *  stops them at once (its own visualizer then: two on the same sound get in each other's way). */
        suspend fun follow(ctx: Context) = kotlinx.coroutines.coroutineScope {
            present = HomeWidgets.ids(ctx, VisualizerWidget::class.java).isNotEmpty()
            fun unseen() = !present || !HomeWidgets.screenOn || HomeWidgets.appShown
            while (true) {
                snapshotFlow { live() }.first { it }
                running = true
                val frames = launch(Dispatchers.Default) { frames(ctx) }
                while (true) {
                    snapshotFlow { live() }.first { !it }
                    if (unseen()) break
                    val back = withTimeoutOrNull(1500L) { snapshotFlow { live() || unseen() }.first { it } }
                    if (back == null || !live()) break
                }
                frames.cancelAndJoin()
                running = false
                shown = FloatArray(BARS)
                runCatching { update(ctx) }   // at rest
            }
        }

        /** (off the main thread) Frame after frame until cancelled: the levels read, the bars drawn and sent. */
        private suspend fun frames(ctx: Context) {
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ALPHA_8)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cap = Capture()
            val mgr = AppWidgetManager.getInstance(ctx)
            var ids = IntArray(0)
            var n = 0
            var heardAt = SystemClock.uptimeMillis()
            var phase = 0f
            try {
                while (true) {
                    val start = SystemClock.uptimeMillis()
                    if (n++ % 25 == 0) {   // every 3 seconds or so: the widgets, and the player's sound (a new player, a new session)
                        ids = HomeWidgets.ids(ctx, VisualizerWidget::class.java)
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
                        slow -> rest(phase.also { phase += 0.5f }, 0.6f)
                        else -> PhoneWidgetRules.fall(shown, FloatArray(BARS))
                    }
                    val f = face
                    if (f != null && ids.isNotEmpty()) {
                        draw(canvas, paint, shown)
                        runCatching { mgr.updateAppWidget(ids, views(ctx, f, bmp)) }
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
