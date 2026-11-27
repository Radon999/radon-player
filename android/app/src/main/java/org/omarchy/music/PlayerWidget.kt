package org.omarchy.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** The home screen widget: like Now playing. The song and its cover, shuffle, previous, play, next and repeat, then
 *  the queue (or the cover, big), and Song / Queue / Open at the bottom. Its background is the cover's main colour,
 *  a little see-through (widgets can't blur). Kept up to date by the music's service (see HomeWidgets.follow). While the
 *  phone is the PC's remote it shows the PC's song and queue, as Now playing does. */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val act = intent.action ?: return
        if (!act.startsWith(PREFIX)) return
        Music.init(ctx)
        // Nothing loaded to play (the music's service gone, the app closed): Play, Next, Previous and a tap in the queue
        // start the music without opening the app, as the DJ widget does (WidgetStart); before, the first tap did nothing
        val idle = Music.player == null && !Remote.active
        when (act) {
            PREV -> if (idle) WidgetStart.request(ctx, "prev") else Music.previous()
            NEXT -> if (idle) WidgetStart.request(ctx, "next") else Music.next()
            PLAY -> if (idle) WidgetStart.request(ctx, "resume") else Music.togglePause()
            SHUFFLE -> Music.shuffle()
            REPEAT -> Music.cycleRepeat()
            SONG -> Prefs.setText(MODE, "song")
            QUEUE -> Prefs.setText(MODE, "queue")
            PLAY_AT -> queueAt(intent)?.let { at -> if (idle) WidgetStart.request(ctx, "at:$at") else Music.playIndex(at) }
            // The volume row: the media volume a step down or up (as the volume keys do, without their panel)
            VOL_DOWN, VOL_UP -> (ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC, if (act == VOL_UP) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER, 0)
        }
        HomeWidgets.updateAll(ctx)   // (this one, and the others that have these buttons)
    }

    /** The queue's place of the song tapped: the row's place, if that song is still there (the queue unchanged since the
     *  list was drawn), else where the song is now; null if it's gone. */
    private fun queueAt(i: Intent): Int? {
        val at = i.getIntExtra("at", -1)
        val id = i.getStringExtra("id") ?: return at.takeIf { it >= 0 }
        val q = Music.shownQueue()
        if (q.getOrNull(at)?.id == id) return at
        return q.indexOfFirst { it.id == id }.takeIf { it >= 0 }
    }

    companion object {
        private const val PREFIX = "org.omarchy.music.widget."
        const val PREV = PREFIX + "prev"; const val NEXT = PREFIX + "next"; const val PLAY = PREFIX + "play"
        const val SHUFFLE = PREFIX + "shuffle"; const val REPEAT = PREFIX + "repeat"
        const val SONG = PREFIX + "song"; const val QUEUE = PREFIX + "queue"; const val PLAY_AT = PREFIX + "at"
        const val VOL_DOWN = PREFIX + "voldown"; const val VOL_UP = PREFIX + "volup"
        private const val MODE = "widget_mode"

        internal fun pi(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(ctx, code,
            Intent(ctx, PlayerWidget::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        /** Every widget on the home screen, as things stand (any thread). */
        fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java)) }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val t = Music.shownTrack()
            val paused = if (Remote.active) Remote.status.paused else Music.paused
            val repeat = if (Remote.active) Remote.status.repeat else Music.repeat
            val fg = HomeWidgets.ink()   // white, or the look's writing (Widgets in the app's look)
            val mode = Prefs.text(MODE, "queue")
            val v = RemoteViews(ctx.packageName, R.layout.widget_player)
            HomeWidgets.paint(ctx, v)   // the rounded background, in the cover's colour (HomeWidgets)
            HomeWidgets.volume(ctx, v)   // the volume row (as every widget with play/pause has)
            v.setTextViewText(R.id.w_title, t?.title ?: "Nothing playing")
            v.setTextViewText(R.id.w_artist, t?.let { listOf(it.artist, it.album).filter { s -> s.isNotEmpty() }.joinToString(" — ") } ?: "Radon Player")
            for (id in listOf(R.id.w_title, R.id.w_artist, R.id.w_empty, R.id.w_tab_song, R.id.w_tab_queue, R.id.w_tab_open)) v.setTextColor(id, fg)
            val bmp = HomeWidgets.coverOf(ctx, t)
            if (bmp != null) { v.setImageViewBitmap(R.id.w_cover, bmp); v.setImageViewBitmap(R.id.w_big, bmp) }
            else { v.setImageViewResource(R.id.w_cover, R.drawable.ic_launcher); v.setImageViewResource(R.id.w_big, R.drawable.ic_launcher) }
            v.setImageViewResource(R.id.w_play, if (paused || t == null) R.drawable.w_play else R.drawable.w_pause)
            v.setImageViewResource(R.id.w_repeat, when (repeat) { "one" -> R.drawable.car_repeat_one; "all" -> R.drawable.car_repeat; else -> R.drawable.car_repeat_off })
            val open = PendingIntent.getActivity(ctx, 40, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // Play with the music not loaded (the app closed): started from here all the same (onReceive)
            v.setOnClickPendingIntent(R.id.w_play, pi(ctx, PLAY, 1))
            v.setOnClickPendingIntent(R.id.w_prev, pi(ctx, PREV, 2))
            v.setOnClickPendingIntent(R.id.w_next, pi(ctx, NEXT, 3))
            v.setOnClickPendingIntent(R.id.w_shuffle, pi(ctx, SHUFFLE, 4))
            v.setOnClickPendingIntent(R.id.w_repeat, pi(ctx, REPEAT, 5))
            v.setOnClickPendingIntent(R.id.w_tab_song, pi(ctx, SONG, 6))
            v.setOnClickPendingIntent(R.id.w_tab_queue, pi(ctx, QUEUE, 7))
            v.setOnClickPendingIntent(R.id.w_tab_open, open)
            v.setOnClickPendingIntent(R.id.w_cover, open)
            v.setFloat(R.id.w_tab_song, "setAlpha", if (mode == "song") 1f else 0.55f)
            v.setFloat(R.id.w_tab_queue, "setAlpha", if (mode == "queue") 1f else 0.55f)
            val queue = mode == "queue"
            v.setViewVisibility(R.id.w_big, if (queue) android.view.View.GONE else android.view.View.VISIBLE)
            v.setViewVisibility(R.id.w_queue, if (queue) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.w_empty, if (queue && Music.shownQueue().size <= Music.shownIndex() + 1) android.view.View.VISIBLE else android.view.View.GONE)
            v.setRemoteAdapter(R.id.w_queue, Intent(ctx, PlayerWidgetRows::class.java))
            v.setPendingIntentTemplate(R.id.w_queue, PendingIntent.getBroadcast(ctx, 8, Intent(ctx, PlayerWidget::class.java).setAction(PLAY_AT),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            runCatching {
                mgr.updateAppWidget(ids, v)
                @Suppress("DEPRECATION") mgr.notifyAppWidgetViewDataChanged(ids, R.id.w_queue)
            }
        }
    }
}

/** The widget's queue: the songs coming up. */
class PlayerWidgetRows : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var rows = emptyList<Pair<Int, Track>>()
        override fun onCreate() {}
        override fun onDataSetChanged() {
            Music.init(applicationContext)
            rows = Music.shownQueue().withIndex().drop(Music.shownIndex() + 1).take(100).map { it.index to it.value }
        }
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews {
            val (i, t) = rows.getOrNull(position) ?: return RemoteViews(packageName, R.layout.widget_row)
            return RemoteViews(packageName, R.layout.widget_row).apply {
                setTextViewText(R.id.wr_title, t.title)
                setTextViewText(R.id.wr_artist, t.artist)
                setTextColor(R.id.wr_title, HomeWidgets.ink())
                setTextColor(R.id.wr_artist, HomeWidgets.ink())
                setOnClickFillInIntent(R.id.wr_row, Intent().putExtra("at", i).putExtra("id", t.id))
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
