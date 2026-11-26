package org.omarchy.music

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** New releases: the newest releases the app found from your artists (its own daily look, New releases by themselves,
 *  and Refresh… on the New releases screen), each with its cover, its artist and how long ago it came out. A tap on one
 *  plays it without opening the app (found on Tidal or YouTube Music, as its page in the app is; see WidgetStart); one
 *  that can't be found there says so, and a tap on that note opens it in the app. The head opens New releases. Nothing
 *  is looked up for the widget itself: it shows what the app already found (the covers come once, through the app's
 *  picture cache). "Today" and "Yesterday" move on with the day: looked at again after midnight, the next time the
 *  phone is awake anyway (it isn't woken for it), and as you unlock it. */
class ReleasesWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx, rows = true)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            PLAY -> WidgetStart.request(ctx, "release:" + (intent.getStringExtra("key") ?: return))
            NEW_DAY -> { Music.init(ctx); update(ctx, rows = true) }
        }
    }

    companion object {
        private const val PLAY = "org.omarchy.music.releaseswidget.play"
        private const val NEW_DAY = "org.omarchy.music.releaseswidget.newday"
        private const val SHOWN = 15
        private val KINDS = mapOf("single" to "Single", "ep" to "EP")

        /** The covers, by address, once they're in (fetched off the list's own thread: [fetchCovers]). */
        private val covers = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
        private val fetching = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
        /** The day the rows' "Today" and "Yesterday" were written for. */
        @Volatile private var rowsDay = ""

        /** Every release the app found (the bell's and the screen's). */
        private fun all() = NewReleases.told + NewReleases.found

        /** The newest ones, each once, the latest first. */
        fun newest(): List<NewReleases.Release> = PhoneWidgetRules.newest(all(), { NewReleases.key(it) }, { it.date }, SHOWN)

        /** A release by its key (NewReleases.key). */
        fun find(key: String): NewReleases.Release? = all().firstOrNull { NewReleases.key(it) == key }

        fun sub(r: NewReleases.Release): String = listOfNotNull(r.artist.takeIf { it.isNotEmpty() }, KINDS[r.type],
            PhoneWidgetRules.released(r.date, java.time.LocalDate.now()).takeIf { it.isNotEmpty() }).joinToString(" · ")

        fun cover(url: String): Bitmap? = covers[url]

        /** (the music's service, as you unlock the phone) A new day since the rows were written: written again. */
        fun newDay(ctx: Context) { if (rowsDay.isNotEmpty() && rowsDay != java.time.LocalDate.now().toString()) update(ctx, rows = true) }

        private var waited = false

        fun update(ctx: Context, rows: Boolean = false) {
            val ids = HomeWidgets.ids(ctx, ReleasesWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val list = newest()
            // Just after the app started, what was found is still being read from the phone: drawn again once it's in
            if (list.isEmpty() && !waited) { waited = true; Music.scope.launch { delay(3000); update(ctx, rows = true) } }
            val v = RemoteViews(ctx.packageName, R.layout.widget_releases)
            HomeWidgets.paint(ctx, v)
            v.setViewVisibility(R.id.wr_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.wr_head, HomeWidgets.open(ctx, "newreleases", 7470))
            v.setOnClickPendingIntent(R.id.wr_empty, HomeWidgets.open(ctx, "newreleases", 7470))
            @Suppress("DEPRECATION") v.setRemoteAdapter(R.id.wr_list, Intent(ctx, ReleasesWidgetRows::class.java))
            v.setPendingIntentTemplate(R.id.wr_list, PendingIntent.getBroadcast(ctx, 0, Intent(ctx, ReleasesWidget::class.java).setAction(PLAY),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            if (rows) rowsDay = java.time.LocalDate.now().toString()
            runCatching {
                val mgr = AppWidgetManager.getInstance(ctx)
                mgr.updateAppWidget(ids, v)
                @Suppress("DEPRECATION") if (rows) mgr.notifyAppWidgetViewDataChanged(ids, R.id.wr_list)
            }
            fetchCovers(ctx, list)
            afterMidnight(ctx)
        }

        /** The covers not in yet, fetched in the background (each given 8 seconds at most), and the rows drawn again with
         *  them; the list itself never waits on the network. */
        private fun fetchCovers(ctx: Context, list: List<NewReleases.Release>) {
            val want = list.map { it.cover }.filter { it.isNotEmpty() && !covers.containsKey(it) && fetching.add(it) }
            if (want.isEmpty()) return
            Music.bg({
                var got = 0
                for (url in want) runCatching {
                    val bmp = kotlinx.coroutines.runBlocking {
                        withTimeoutOrNull(8000L) {
                            val res = coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(url).size(120).allowHardware(false).build())
                            (res as? coil.request.SuccessResult)?.drawable?.toBitmap()
                        }
                    }
                    if (bmp != null) { covers[url] = bmp; got++ }
                }
                want.forEach { fetching.remove(it) }
                got
            }) { got, _ ->
                if ((got ?: 0) > 0) {
                    val ids = HomeWidgets.ids(ctx, ReleasesWidget::class.java)
                    @Suppress("DEPRECATION") runCatching { AppWidgetManager.getInstance(ctx).notifyAppWidgetViewDataChanged(ids, R.id.wr_list) }
                }
            }
        }

        /** Just after midnight, the next time the phone is awake anyway (an alarm that doesn't wake it): the days told again. */
        private fun afterMidnight(ctx: Context) {
            val at = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
            val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, ReleasesWidget::class.java).setAction(NEW_DAY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            runCatching { ctx.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at, pi) }
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
                setTextColor(R.id.wrr_title, HomeWidgets.ink())
                setTextColor(R.id.wrr_sub, HomeWidgets.ink())
                val bmp = r.cover.takeIf { it.isNotEmpty() }?.let { ReleasesWidget.cover(it) }
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
