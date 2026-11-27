package org.omarchy.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Now playing + playlists: the song and its cover with shuffle, previous, play, next and repeat on top (as the first
 *  widget's: the same buttons), and under it your playlists and their folders, as Playlists lists them (the discovery
 *  ones folded under one row). A playlist's tap plays it from its start without opening the app (see WidgetStart); a
 *  folder's opens it right in the widget (← goes back); Open shows them in the app. */
class ListsWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx, rows = true)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ROW -> {
                Music.init(ctx)
                val id = intent.getStringExtra("id") ?: return
                when (intent.getStringExtra("kind")) {
                    "list" -> WidgetStart.request(ctx, PhoneWidgetRules.list(id, shuffled = false))
                    "folder" -> { Prefs.setText(FOLDER, id); update(ctx, rows = true) }
                }
            }
            BACK -> { Music.init(ctx); Prefs.setText(FOLDER, ""); update(ctx, rows = true) }
        }
    }

    /** A row: a playlist ("list") or a folder of them ("folder"; the discovery playlists' fold is the folder [DISCOVERY]). */
    data class Row(val kind: String, val id: String, val title: String, val sub: String)

    companion object {
        private const val PREFIX = "org.omarchy.music.listswidget."
        private const val ROW = PREFIX + "row"
        private const val BACK = PREFIX + "back"
        /** The folder open in the widget ("" none). */
        private const val FOLDER = "phone_widget_lists_folder"
        private const val DISCOVERY = "discovery"

        private fun songs(n: Int) = "$n song${if (n != 1) "s" else ""}"
        private fun list(p: Playlist) = Row("list", p.id, p.shown, songs(p.tracks.size))

        /** What the list shows now: the open folder's playlists, or the folders and the playlists in none. */
        fun rows(): List<Row> {
            val all = Music.lists.all()
            val open = Prefs.text(FOLDER)
            if (open == DISCOVERY) return all.filter { it.discovery != null }.map(::list)
            Music.playlistFolders.get(open)?.let { f -> return f.playlists.filter { it.kind == "mine" }.mapNotNull { Music.lists.get(it.id) }.map(::list) }
            val outside = YourPlaylists.outsideFolders(all)
            val disc = outside.filter { it.discovery != null }
            return Music.playlistFolders.all().map { Row("folder", it.id, it.name, "${it.playlists.size} playlist${if (it.playlists.size != 1) "s" else ""}") } +
                outside.filter { it.discovery == null }.map(::list) +
                (if (disc.isNotEmpty()) listOf(Row("folder", DISCOVERY, "Discovery playlists", "${disc.size} playlists")) else emptyList())
        }

        /** Every one on the home screen, as things stand; rows: the list read again too (any thread). */
        fun update(ctx: Context, rows: Boolean = false) {
            val ids = HomeWidgets.ids(ctx, ListsWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val t = Music.shownTrack()   // (the PC's, while this phone is its remote: the buttons work the PC then)
            val paused = if (Remote.active) Remote.status.paused else Music.paused
            val repeat = if (Remote.active) Remote.status.repeat else Music.repeat
            val v = RemoteViews(ctx.packageName, R.layout.widget_lists)
            HomeWidgets.paint(ctx, v)
            HomeWidgets.volume(ctx, v)   // the volume row (as every widget with play/pause has)
            v.setTextViewText(R.id.wls_title, t?.title ?: "Nothing playing")
            v.setTextViewText(R.id.wls_artist, t?.let { listOf(it.artist, it.album).filter { s -> s.isNotEmpty() }.joinToString(" — ") } ?: "Radon Player")
            val bmp = HomeWidgets.coverOf(ctx, t)
            if (bmp != null) v.setImageViewBitmap(R.id.wls_cover, bmp) else v.setImageViewResource(R.id.wls_cover, R.drawable.ic_launcher)
            v.setImageViewResource(R.id.wls_play, if (paused || t == null) R.drawable.w_play else R.drawable.w_pause)
            v.setImageViewResource(R.id.wls_repeat, when (repeat) { "one" -> R.drawable.car_repeat_one; "all" -> R.drawable.car_repeat; else -> R.drawable.car_repeat_off })
            val app = HomeWidgets.open(ctx, "player", 7410)
            // The same buttons as the first widget's (PlayerWidget: with the music not loaded, Play starts it from here)
            v.setOnClickPendingIntent(R.id.wls_play, PlayerWidget.pi(ctx, PlayerWidget.PLAY, 1))
            v.setOnClickPendingIntent(R.id.wls_prev, PlayerWidget.pi(ctx, PlayerWidget.PREV, 2))
            v.setOnClickPendingIntent(R.id.wls_next, PlayerWidget.pi(ctx, PlayerWidget.NEXT, 3))
            v.setOnClickPendingIntent(R.id.wls_shuffle, PlayerWidget.pi(ctx, PlayerWidget.SHUFFLE, 4))
            v.setOnClickPendingIntent(R.id.wls_repeat, PlayerWidget.pi(ctx, PlayerWidget.REPEAT, 5))
            v.setOnClickPendingIntent(R.id.wls_cover, app)
            // The list's head: your playlists, or the folder open in it with ← back
            val open = Prefs.text(FOLDER)
            val folder = if (open == DISCOVERY) "Discovery playlists" else Music.playlistFolders.get(open)?.name
            v.setTextViewText(R.id.wls_head, (folder ?: "Your playlists").uppercase())
            v.setViewVisibility(R.id.wls_back, if (folder != null) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.wls_back, HomeWidgets.tap(ctx, ListsWidget::class.java, BACK))
            v.setOnClickPendingIntent(R.id.wls_open, HomeWidgets.open(ctx, if (folder != null && open != DISCOVERY) "folder:$open" else "playlists", 7411))
            val shown = rows()
            v.setViewVisibility(R.id.wls_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.wls_empty, if (folder != null) "No playlists of yours in this folder" else "No playlists yet: make one in the app")
            @Suppress("DEPRECATION") v.setRemoteAdapter(R.id.wls_list, Intent(ctx, ListsWidgetRows::class.java))
            v.setPendingIntentTemplate(R.id.wls_list, android.app.PendingIntent.getBroadcast(ctx, 0,
                Intent(ctx, ListsWidget::class.java).setAction(ROW), android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
            runCatching {
                val mgr = AppWidgetManager.getInstance(ctx)
                mgr.updateAppWidget(ids, v)
                @Suppress("DEPRECATION") if (rows) mgr.notifyAppWidgetViewDataChanged(ids, R.id.wls_list)
            }
        }
    }
}

/** The Now playing + playlists widget's list: your playlists and their folders. */
class ListsWidgetRows : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var rows = emptyList<ListsWidget.Row>()
        override fun onCreate() {}
        override fun onDataSetChanged() {
            Music.init(applicationContext)
            rows = runCatching { ListsWidget.rows() }.getOrDefault(emptyList())
        }
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews {
            val r = rows.getOrNull(position) ?: return RemoteViews(packageName, R.layout.widget_list_row)
            return RemoteViews(packageName, R.layout.widget_list_row).apply {
                setImageViewResource(R.id.wlr_icon, if (r.kind == "folder") R.drawable.w_folder else R.drawable.w_playlist)
                HomeWidgets.ink().let { if (it != Color.WHITE) setInt(R.id.wlr_icon, "setColorFilter", it) }   // the look's writing colour
                setTextViewText(R.id.wlr_title, r.title)
                setTextViewText(R.id.wlr_sub, r.sub)
                setTextColor(R.id.wlr_title, HomeWidgets.ink())
                setTextColor(R.id.wlr_sub, HomeWidgets.ink())
                setOnClickFillInIntent(R.id.wlr_row, Intent().putExtra("kind", r.kind).putExtra("id", r.id))
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
