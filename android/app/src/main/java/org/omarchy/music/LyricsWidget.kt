package org.omarchy.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Lyrics: the song playing's lyrics, as the Lyrics tab shows them (Synced or Genius, as chosen there; the chip in the
 *  corner switches). Timed lines: the one being sung lit up and kept in place as they go by, a tap on a line goes
 *  there; words alone: a list to scroll. Under them previous, play and next, a lyrics card to share (the app opens on
 *  the card maker for the song) and Open (the Lyrics tab).
 *
 *  The line is moved on by the music's service ([follow]) only while it can be seen: a Lyrics widget on the home
 *  screen, the screen on, and the app itself not in front. It wakes once for each new line, not on a timer. */
class LyricsWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) { present = true; update(ctx) }

    override fun onDeleted(ctx: Context, ids: IntArray) { present = HomeWidgets.ids(ctx, LyricsWidget::class.java).isNotEmpty() }

    override fun onDisabled(ctx: Context) { present = false }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            SEEK -> {
                Music.init(ctx)
                val at = intent.getDoubleExtra("at", -1.0)
                if (at >= 0 && (Music.player != null || Remote.active)) { Music.seek(at); tick(ctx) }
            }
            MODE -> {
                Music.init(ctx)
                // (nothing loaded yet, as after Android closed the app: the mode as chosen, and the lyrics fetched)
                val cur = mode().ifEmpty { Music.lyricsMode }
                Music.setLyricsMode(if (cur == "synced") "genius" else "synced")
                if (Music.shownTrack()?.id != forId || got == null) { forId = null; update(ctx) } else draw(ctx, rows = true)
            }
        }
    }

    companion object {
        private const val PREFIX = "org.omarchy.music.lyricswidget."
        private const val SEEK = PREFIX + "seek"
        private const val MODE = PREFIX + "mode"
        private val SLOTS = intArrayOf(R.id.wy_0, R.id.wy_1, R.id.wy_2, R.id.wy_3, R.id.wy_4, R.id.wy_5, R.id.wy_6, R.id.wy_7, R.id.wy_8)
        /** The line being sung sits this many lines from the top (once there are that many above it). */
        private const val BEFORE = 2

        // ---- what decides whether the line is followed (Compose state, so [follow] waits on it) ----
        var present by mutableStateOf(false)    // a Lyrics widget on the home screen
        private val screenOn get() = HomeWidgets.unlocked   // the screen on and the phone unlocked
        private val appShown get() = HomeWidgets.appShown   // the app in front (its own Lyrics tab then)
        private var synced by mutableStateOf(false)   // timed lines showing, for the song playing

        // ---- the song's lyrics ----
        private var forId: String? = null      // the song they're for
        private var got: Lyrics.Got? = null
        private var state = ""                 // "", "loading", "none", "nothing" (no song), or what went wrong
        private var lines: List<Pair<Double, String>> = emptyList()
        private var times: List<Double> = emptyList()
        /** The words alone, line by line (the list's: LyricsWidgetRows). */
        @Volatile var plain: List<String> = emptyList()
        private var shownCur = Int.MIN_VALUE
        private var stale = false   // something changed with the screen off: drawn as it comes back on

        /** Synced or Genius, as the Lyrics tab chooses it: the one chosen when the song has it, else the other. */
        private fun mode(): String {
            val g = got ?: return ""
            var m = Music.lyricsMode
            if (m == "synced" && lines.isEmpty()) m = "genius"
            if (m == "genius" && g.plain == null) m = "synced"
            return if (m == "synced" && lines.isEmpty()) "" else m
        }

        private fun playing() = if (Remote.active) !Remote.status.paused else !Music.paused

        /** The song's lyrics fetched (in the background), and shown once they're in. */
        private fun load(ctx: Context, t: Track?) {
            forId = t?.id; got = null; lines = emptyList(); times = emptyList(); plain = emptyList(); shownCur = Int.MIN_VALUE
            synced = false
            if (t == null) { state = "nothing"; return }
            state = "loading"
            val id = t.id
            Music.bg({ Lyrics.get(Music.ctx, t) }) { g, err ->
                if (forId != id) return@bg
                got = g
                state = if (err != null) "Couldn't get the lyrics: ${err.message?.take(80)}" else if (g == null) "none" else ""
                lines = Lyrics.parseLrc(g?.synced)
                times = lines.map { it.first }
                plain = g?.plain?.lines().orEmpty()
                if (screenOn) draw(ctx, rows = true) else stale = true
            }
        }

        /** Every one on the home screen, as things stand (the song changed: its lyrics fetched). With the screen off nothing
         *  is sent to the home screen: it's drawn as the screen comes back on. */
        fun update(ctx: Context) {
            val ids = HomeWidgets.ids(ctx, LyricsWidget::class.java)
            present = ids.isNotEmpty()
            if (ids.isEmpty()) return
            Music.init(ctx)
            val t = Music.shownTrack()
            val fresh = t?.id != forId
            if (fresh) load(ctx, t)
            if (!screenOn) { stale = true; return }
            val rows = fresh || stale
            stale = false
            draw(ctx, rows)
        }

        private fun draw(ctx: Context, rows: Boolean) {
            val ids = HomeWidgets.ids(ctx, LyricsWidget::class.java)
            if (ids.isEmpty()) return
            val t = Music.shownTrack()
            val v = RemoteViews(ctx.packageName, R.layout.widget_lyrics)
            HomeWidgets.paint(ctx, v)
            HomeWidgets.volume(ctx, v)   // the volume row (as every widget with play/pause has)
            v.setTextViewText(R.id.wy_title, t?.title ?: "Nothing playing")
            v.setTextViewText(R.id.wy_artist, t?.artist?.takeIf { it.isNotEmpty() } ?: "Lyrics")
            val bmp = if (t != null && !Remote.active) HomeWidgets.coverOf(ctx, t) else null
            if (bmp != null) v.setImageViewBitmap(R.id.wy_cover, bmp) else v.setImageViewResource(R.id.wy_cover, R.drawable.ic_launcher)
            val g = got
            val mode = mode()
            val message = when {
                t == null -> "The lyrics of the song playing show up here"
                state == "loading" -> "Finding the lyrics…"
                state == "none" -> "No lyrics found for this song. Open to search for them by another name"
                state.isNotEmpty() -> state
                g != null && g.instrumental && g.plain == null && g.synced == null -> "Instrumental: no words in this one"
                mode.isEmpty() -> "No lyrics found for this song"
                else -> null
            }
            v.setViewVisibility(R.id.wy_msg, if (message != null) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.wy_msg, message ?: "")
            v.setViewVisibility(R.id.wy_synced, if (message == null && mode == "synced") View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wy_plain, if (message == null && mode == "genius") View.VISIBLE else View.GONE)
            synced = message == null && mode == "synced"
            if (synced) { shownCur = PhoneWidgetRules.lineAt(times, Music.livePosition()); slots(ctx, v, shownCur) }
            // Synced ⇄ Genius, when the song has both
            val both = g?.plain != null && lines.isNotEmpty()
            v.setViewVisibility(R.id.wy_mode, if (both) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.wy_mode, if (mode == "synced") "Genius" else "Synced")
            v.setOnClickPendingIntent(R.id.wy_mode, HomeWidgets.tap(ctx, LyricsWidget::class.java, MODE))
            // The words: a list to scroll (a tap opens the Lyrics tab)
            @Suppress("DEPRECATION") v.setRemoteAdapter(R.id.wy_plain, Intent(ctx, LyricsWidgetRows::class.java))
            v.setPendingIntentTemplate(R.id.wy_plain, HomeWidgets.openTemplate(ctx, 7451))
            v.setOnClickPendingIntent(R.id.wy_msg, HomeWidgets.open(ctx, "lyrics", 7450))
            // The buttons: the first widget's previous, play and next; the card maker; the Lyrics tab
            v.setImageViewResource(R.id.wy_play, if (!playing() || t == null) R.drawable.w_play else R.drawable.w_pause)
            v.setOnClickPendingIntent(R.id.wy_play, PlayerWidget.pi(ctx, PlayerWidget.PLAY, 1))   // (nothing loaded: it starts the music)
            v.setOnClickPendingIntent(R.id.wy_prev, PlayerWidget.pi(ctx, PlayerWidget.PREV, 2))
            v.setOnClickPendingIntent(R.id.wy_next, PlayerWidget.pi(ctx, PlayerWidget.NEXT, 3))
            v.setOnClickPendingIntent(R.id.wy_card, HomeWidgets.open(ctx, "card", 7452))
            v.setOnClickPendingIntent(R.id.wy_edit, HomeWidgets.open(ctx, "lyrics_edit", 7453))
            v.setOnClickPendingIntent(R.id.wy_open, HomeWidgets.open(ctx, "lyrics", 7450))
            v.setOnClickPendingIntent(R.id.wy_cover, HomeWidgets.open(ctx, "lyrics", 7450))
            runCatching {
                val mgr = AppWidgetManager.getInstance(ctx)
                mgr.updateAppWidget(ids, v)
                @Suppress("DEPRECATION") if (rows) mgr.notifyAppWidgetViewDataChanged(ids, R.id.wy_plain)
            }
        }

        /** The timed lines in the slots, cur lit (a tap on one goes to it). */
        private fun slots(ctx: Context, v: RemoteViews, cur: Int) {
            val shown = PhoneWidgetRules.window(cur, lines.size, SLOTS.size, BEFORE)
            for ((slot, id) in SLOTS.withIndex()) {
                val i = shown.first + slot
                val line = lines.getOrNull(i)?.takeIf { i in shown }
                // An empty slot (under the last line): blank, and no tap left on it from a line it held before
                if (line == null) { v.setTextViewText(id, ""); v.setViewVisibility(id, View.INVISIBLE); continue }
                v.setViewVisibility(id, View.VISIBLE)
                val words = line.second.ifEmpty { "♪" }
                v.setTextViewText(id, if (i == cur) SpannableString(words).apply {
                    setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(RelativeSizeSpan(1.15f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else words)
                // The line being sung full, the ones gone by faint, the ones to come between (as the tab)
                v.setFloat(id, "setAlpha", when { i == cur -> 1f; i < cur -> 0.4f; else -> 0.66f })
                v.setOnClickPendingIntent(id, HomeWidgets.tap(ctx, LyricsWidget::class.java, SEEK, 7460 + slot) { putExtra("at", line.first) })
            }
        }

        /** The line being sung, moved on if it changed (only the lines are sent to the home screen). */
        private fun tick(ctx: Context) {
            if (!synced) return
            val cur = PhoneWidgetRules.lineAt(times, Music.livePosition())
            if (cur == shownCur) return
            shownCur = cur
            val ids = HomeWidgets.ids(ctx, LyricsWidget::class.java)
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget_lyrics)
            slots(ctx, v, cur)
            runCatching { AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(ids, v) }
        }

        /** (the music's service, for as long as it runs) Moves the line on as the song plays, while it can be seen; wakes
         *  when the next line comes up (or a second on, for a seek), and not at all with the screen off, the app in front,
         *  the music paused, or no widget or no timed lines to show. */
        suspend fun follow(ctx: Context) = kotlinx.coroutines.coroutineScope {
            present = HomeWidgets.ids(ctx, LyricsWidget::class.java).isNotEmpty()
            // The screen back on: what changed while it was off, drawn now
            launch { snapshotFlow { screenOn }.collect { on -> if (on && stale) runCatching { update(ctx) } } }
            while (true) {
                snapshotFlow { present && screenOn && !appShown && synced }.first { it }
                runCatching { tick(ctx) }
                val moving = playing()
                val wait = if (moving) PhoneWidgetRules.waitMs(times, Music.livePosition()) else null
                // Until the next line (playing), or until something changes: paused, a seek, another song, the screen
                val change = snapshotFlow { listOf<Any?>(present, screenOn, appShown, synced, playing(), Music.shownTrack()?.id,
                    if (moving) null else if (Remote.active) Remote.status.position else Music.position) }.drop(1)
                if (wait != null) withTimeoutOrNull(wait) { change.first() } else change.first()
            }
        }
    }
}

/** The Lyrics widget's words, line by line (lyrics without timing, or Genius's chosen), to scroll. */
class LyricsWidgetRows : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var lines = emptyList<String>()
        private val section = Regex("\\s*\\[[^]]+]\\s*")   // [Verse 1], [Chorus]: Genius's section names
        override fun onCreate() {}
        override fun onDataSetChanged() { lines = LyricsWidget.plain }
        override fun onDestroy() {}
        override fun getCount() = lines.size
        override fun getViewAt(position: Int): RemoteViews {
            val line = lines.getOrNull(position) ?: ""
            return RemoteViews(packageName, R.layout.widget_lyrics_row).apply {
                val head = section.matches(line)
                setTextViewText(R.id.wyr_line, if (head) line.trim().uppercase() else line)
                setTextColor(R.id.wyr_line, HomeWidgets.ink())
                setFloat(R.id.wyr_line, "setAlpha", if (head) 0.6f else 1f)
                setTextViewTextSize(R.id.wyr_line, android.util.TypedValue.COMPLEX_UNIT_SP, if (head) 11f else 15f)
                setOnClickFillInIntent(R.id.wyr_line, Intent().putExtra(HomeWidgets.OPEN, "lyrics"))
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
