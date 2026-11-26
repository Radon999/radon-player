package org.omarchy.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Pinned: everything you pinned (playlists, albums, artists, genres, folders, mood lists…), the newest first, as the
 *  menu's Pinned shows them. A tap opens it in the app, where it lives (as a tap there does); Open shows Pinned. */
class PinnedWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx, rows = true)

    companion object {
        /** What each kind of pin is called under its name (Pinned's own words). */
        private val KINDS = mapOf("artist" to "Artist", "album" to "Album", "mine" to "Playlist", "service" to "Playlist",
            "smart" to "Smart playlist", "mood" to "Mood list", "genre" to "Genre", "folder" to "Folder", "listfolder" to "Folder of playlists")

        fun icon(kind: String) = when (kind) {
            "artist" -> R.drawable.w_person; "album" -> R.drawable.w_dj; "folder", "listfolder" -> R.drawable.w_folder
            else -> R.drawable.w_playlist
        }

        fun sub(p: Pin) = listOf(KINDS[p.kind] ?: "", p.sub).filter { it.isNotEmpty() }.joinToString(" · ")

        fun update(ctx: Context, rows: Boolean = false) {
            val ids = HomeWidgets.ids(ctx, PinnedWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_pinned)
            HomeWidgets.paint(ctx, v)
            v.setViewVisibility(R.id.wp_empty, if (Music.pins.isEmpty()) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.wp_head, HomeWidgets.open(ctx, "pinned", 7420))
            v.setOnClickPendingIntent(R.id.wp_empty, HomeWidgets.open(ctx, "pinned", 7420))
            @Suppress("DEPRECATION") v.setRemoteAdapter(R.id.wp_list, Intent(ctx, PinnedWidgetRows::class.java))
            v.setPendingIntentTemplate(R.id.wp_list, HomeWidgets.openTemplate(ctx, 7421))
            runCatching {
                val mgr = AppWidgetManager.getInstance(ctx)
                mgr.updateAppWidget(ids, v)
                @Suppress("DEPRECATION") if (rows) mgr.notifyAppWidgetViewDataChanged(ids, R.id.wp_list)
            }
        }
    }
}

/** The Pinned widget's list. */
class PinnedWidgetRows : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var pins = emptyList<Pin>()
        override fun onCreate() {}
        override fun onDataSetChanged() {
            Music.init(applicationContext)
            pins = runCatching { Music.pins.toList() }.getOrDefault(emptyList())
        }
        override fun onDestroy() {}
        override fun getCount() = pins.size
        override fun getViewAt(position: Int): RemoteViews {
            val p = pins.getOrNull(position) ?: return RemoteViews(packageName, R.layout.widget_list_row)
            return RemoteViews(packageName, R.layout.widget_list_row).apply {
                setImageViewResource(R.id.wlr_icon, PinnedWidget.icon(p.kind))
                HomeWidgets.ink().let { if (it != Color.WHITE) setInt(R.id.wlr_icon, "setColorFilter", it) }   // the look's writing colour
                setTextViewText(R.id.wlr_title, p.title)
                setTextViewText(R.id.wlr_sub, PinnedWidget.sub(p))
                setTextColor(R.id.wlr_title, HomeWidgets.ink())
                setTextColor(R.id.wlr_sub, HomeWidgets.ink())
                setOnClickFillInIntent(R.id.wlr_row, Intent().putExtra(HomeWidgets.OPEN, "pin:" + p.key))
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
