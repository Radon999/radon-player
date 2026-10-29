package org.omarchy.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap

/** The home screen widget: like Now playing. The song and its cover, shuffle, previous, play, next and repeat, then
 *  the queue (or the cover, big), and Song / Queue / Open at the bottom. Its background is the cover's main colour,
 *  a little see-through (widgets can't blur). Kept up to date by the music's service (see MusicService). */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val act = intent.action ?: return
        if (!act.startsWith(PREFIX)) return
        Music.init(ctx)
        when (act) {
            PREV -> Music.previous()
            NEXT -> Music.next()
            PLAY -> Music.togglePause()
            SHUFFLE -> Music.shuffle()
            REPEAT -> Music.cycleRepeat()
            SONG -> Prefs.setText(MODE, "song")
            QUEUE -> Prefs.setText(MODE, "queue")
            PLAY_AT -> intent.getIntExtra("at", -1).takeIf { it >= 0 }?.let { Music.playIndex(it) }
        }
        update(ctx)
    }

    companion object {
        private const val PREFIX = "org.omarchy.music.widget."
        const val PREV = PREFIX + "prev"; const val NEXT = PREFIX + "next"; const val PLAY = PREFIX + "play"
        const val SHUFFLE = PREFIX + "shuffle"; const val REPEAT = PREFIX + "repeat"
        const val SONG = PREFIX + "song"; const val QUEUE = PREFIX + "queue"; const val PLAY_AT = PREFIX + "at"
        private const val MODE = "widget_mode"

        @Volatile private var cover: Pair<String, Bitmap?> = "" to null
        @Volatile private var colour: Int = Color.rgb(40, 40, 46)

        private fun pi(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(ctx, code,
            Intent(ctx, PlayerWidget::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        /** Every widget on the home screen, as things stand (any thread). */
        fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java)) }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val t = Music.current()
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
                        update(ctx)
                    }
                }
            }
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(colour, hsl)
            hsl[2] = hsl[2].coerceIn(0.18f, 0.42f)   // deep enough for white text, the cover's own hue
            val bg = ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), 0xD9)   // a little see-through
            val fg = Color.WHITE
            val mode = Prefs.text(MODE, "queue")
            val v = RemoteViews(ctx.packageName, R.layout.widget_player)
            if (android.os.Build.VERSION.SDK_INT >= 31)   // the rounded background, tinted
                v.setColorStateList(R.id.w_root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(bg))
            else v.setInt(R.id.w_root, "setBackgroundColor", bg)
            v.setTextViewText(R.id.w_title, t?.title ?: "Nothing playing")
            v.setTextViewText(R.id.w_artist, t?.let { listOf(it.artist, it.album).filter { s -> s.isNotEmpty() }.joinToString(" — ") } ?: "Radon Player")
            for (id in listOf(R.id.w_title, R.id.w_artist, R.id.w_empty, R.id.w_tab_song, R.id.w_tab_queue, R.id.w_tab_open)) v.setTextColor(id, fg)
            val bmp = cover.second
            if (bmp != null) { v.setImageViewBitmap(R.id.w_cover, bmp); v.setImageViewBitmap(R.id.w_big, bmp) }
            else { v.setImageViewResource(R.id.w_cover, R.drawable.ic_launcher); v.setImageViewResource(R.id.w_big, R.drawable.ic_launcher) }
            v.setImageViewResource(R.id.w_play, if (Music.paused || t == null) R.drawable.w_play else R.drawable.w_pause)
            v.setImageViewResource(R.id.w_repeat, when (Music.repeat) { "one" -> R.drawable.car_repeat_one; "all" -> R.drawable.car_repeat; else -> R.drawable.car_repeat_off })
            val open = PendingIntent.getActivity(ctx, 40, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // Play with the music not loaded (the app closed): the app opens, as it would to start playing.
            v.setOnClickPendingIntent(R.id.w_play, if (Music.player == null) open else pi(ctx, PLAY, 1))
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
            v.setViewVisibility(R.id.w_empty, if (queue && Music.queue.size <= Music.index + 1) android.view.View.VISIBLE else android.view.View.GONE)
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
            rows = Music.queue.withIndex().drop(Music.index + 1).take(100).map { it.index to it.value }
        }
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews {
            val (i, t) = rows.getOrNull(position) ?: return RemoteViews(packageName, R.layout.widget_row)
            return RemoteViews(packageName, R.layout.widget_row).apply {
                setTextViewText(R.id.wr_title, t.title)
                setTextViewText(R.id.wr_artist, t.artist)
                setTextColor(R.id.wr_title, Color.WHITE)
                setTextColor(R.id.wr_artist, Color.WHITE)
                setOnClickFillInIntent(R.id.wr_row, Intent().putExtra("at", i))
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
