package org.omarchy.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** New releases: the newest releases the app found from your artists (its own daily look, New releases by themselves,
 *  and Refresh… on the New releases screen), each with its cover, its artist and how long ago it came out. A tap on one
 *  plays it without opening the app (found on Tidal or YouTube Music, as its page in the app is; see WidgetStart); one
 *  that can't be found there says so, and a tap on that note opens it in the app. The head opens New releases. Nothing
 *  is looked up for the widget itself: it shows what the app already found. */
class ReleasesWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx, rows = true)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action != PLAY) return
        val key = intent.getStringExtra("key") ?: return
        WidgetStart.request(ctx, "release:$key")
    }

    companion object {
        private const val PLAY = "org.omarchy.music.releaseswidget.play"
        private const val SHOWN = 15
        private val KINDS = mapOf("single" to "Single", "ep" to "EP")

        /** Every release the app found (the bell's and the screen's). */
        private fun all() = NewReleases.told + NewReleases.found

        /** The newest ones, each once, the latest first. */
        fun newest(): List<NewReleases.Release> = PhoneWidgetRules.newest(all(), { NewReleases.key(it) }, { it.date }, SHOWN)

        /** A release by its key (NewReleases.key). */
        fun find(key: String): NewReleases.Release? = all().firstOrNull { NewReleases.key(it) == key }

        fun sub(r: NewReleases.Release): String = listOfNotNull(r.artist.takeIf { it.isNotEmpty() }, KINDS[r.type],
            PhoneWidgetRules.released(r.date, java.time.LocalDate.now()).takeIf { it.isNotEmpty() }).joinToString(" · ")

        private var waited = false

        fun update(ctx: Context, rows: Boolean = false) {
            val ids = HomeWidgets.ids(ctx, ReleasesWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val list = newest()
            // Just after the app started, what was found is still being read from the phone: drawn again once it's in
            if (list.isEmpty() && !waited) { waited = true; Music.scope.launch { delay(3000); update(ctx, rows = true) } }
            val v = RemoteViews(ctx.packageName, R.layout.widget_releases)
            HomeWidgets.paint(ctx, v, R.id.wr_root)
            v.setViewVisibility(R.id.wr_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.wr_head, HomeWidgets.open(ctx, "newreleases", 7470))
            v.setOnClickPendingIntent(R.id.wr_empty, HomeWidgets.open(ctx, "newreleases", 7470))
            @Suppress("DEPRECATION") v.setRemoteAdapter(R.id.wr_list, Intent(ctx, ReleasesWidgetRows::class.java))
            v.setPendingIntentTemplate(R.id.wr_list, PendingIntent.getBroadcast(ctx, 0, Intent(ctx, ReleasesWidget::class.java).setAction(PLAY),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            runCatching {
                val mgr = AppWidgetManager.getInstance(ctx)
                mgr.updateAppWidget(ids, v)
                @Suppress("DEPRECATION") if (rows) mgr.notifyAppWidgetViewDataChanged(ids, R.id.wr_list)
            }
        }
    }
}

/** The New releases widget's list. */
class ReleasesWidgetRows : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var rows = emptyList<NewReleases.Release>()
        override fun onCreate() {}
        override fun onDataSetChanged() {
            Music.init(applicationContext)
            rows = runCatching { ReleasesWidget.newest() }.getOrDefault(emptyList())
        }
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews {
            val r = rows.getOrNull(position) ?: return RemoteViews(packageName, R.layout.widget_release_row)
            return RemoteViews(packageName, R.layout.widget_release_row).apply {
                setTextViewText(R.id.wrr_title, r.title)
                setTextViewText(R.id.wrr_sub, ReleasesWidget.sub(r))
                setTextColor(R.id.wrr_title, Color.WHITE)
                setTextColor(R.id.wrr_sub, Color.WHITE)
                // Its cover (kept by the app's picture cache once loaded; this list's own thread may wait for it)
                val bmp = r.cover.takeIf { it.isNotEmpty() }?.let { url ->
                    runCatching {
                        val res = kotlinx.coroutines.runBlocking { coil.Coil.imageLoader(applicationContext).execute(coil.request.ImageRequest.Builder(applicationContext)
                            .data(url).size(120).allowHardware(false).build()) }
                        (res as? coil.request.SuccessResult)?.drawable?.toBitmap()
                    }.getOrNull()
                }
                if (bmp != null) setImageViewBitmap(R.id.wrr_cover, bmp) else setImageViewResource(R.id.wrr_cover, R.drawable.w_dj)
                setOnClickFillInIntent(R.id.wrr_row, Intent().putExtra("key", NewReleases.key(r)))
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
