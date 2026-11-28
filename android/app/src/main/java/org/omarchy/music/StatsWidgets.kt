package org.omarchy.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Most played: a tap brings you straight to Most played in the app (the stats). On it, your most played songs (every
 *  one: the top ones first, scrolled for the rest) over the period Most played shows (This week, This month, This year or All time, chosen
 *  there). It never wakes the phone by itself: it's made again when your plays or the period change, and when you
 *  come back to the home screen after half an hour. */
class TopWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: android.os.Bundle) = update(ctx)

    /** A tap on the period (This week…): the next one, as Most played's own choices go round. */
    override fun onReceive(ctx: Context, intent: android.content.Intent) {
        super.onReceive(ctx, intent)
        if (intent.action != CYCLE) return
        Music.init(ctx)
        val keys = PERIODS.keys.toList()
        Prefs.setText(PERIOD_KEY, keys[(keys.indexOf(period()) + 1) % keys.size])
        top = null
        refresh(ctx); update(ctx)
    }

    companion object {
        private const val CYCLE = "org.omarchy.music.widget.top.period"
        /** The widget's own period (a tap on it changes it): at first, the one Most played shows in the app. */
        private const val PERIOD_KEY = "widget_top_period"
        private fun period(): String = Prefs.text(PERIOD_KEY).takeIf { it in PERIODS } ?: Music.topPeriod
        private val PERIODS = mapOf("week" to "This week", "month" to "This month", "year" to "This year", "all" to "All time")

        /** The songs shown, and the period they're for (made in the background: [refresh]). */
        @Volatile internal var top: Pair<String, List<Track>>? = null
        @Volatile private var madeAt = 0L
        @Volatile private var askedAt = 0L   // when they were last asked for
        private var tries = 0

        /** The top songs worked out again (plays came in, the period changed), then shown. */
        fun refresh(ctx: Context) {
            if (HomeWidgets.ids(ctx, TopWidget::class.java).isEmpty()) return
            Music.init(ctx)
            askedAt = System.currentTimeMillis()
            val period = period()
            // The plays themselves added up again when they're hours old (while the phone is in use): they were only ever
            // added up when Most played or Artists was opened in the app, so "This week" here could be weeks old
            if (HomeWidgets.unlocked && Stats.ageS > 6 * 3600) runCatching { Stats.refresh() }
            Music.bg({ songsNow(period) }) { songs, _ ->
                if (songs == null) return@bg
                top = period to songs
                madeAt = System.currentTimeMillis()
                update(ctx)
                // Just after the app started the plays may not be counted yet: looked at again a little later
                if (songs.isEmpty() && tries++ < 3) Music.scope.launch { delay(5000); refresh(ctx) }
            }
        }

        /** (blocking) The period's songs, as the widget lists them. */
        private fun songsNow(period: String): List<Track> = Music.mostPlayed(period).filter { !StatHides.play(it.title, it.artist, it.album) }

        /** (blocking; the list's own thread) The songs to list: the ones made, or (the app closed by Android and started
         *  again for the list alone: nothing made yet, and the list came up empty) made now. */
        internal fun rowsNow(ctx: Context): List<Track> {
            top?.second?.let { return it }
            Music.init(ctx)
            val period = period()
            // (the counts are read from the phone as the app starts: a moment for them to be in)
            var songs = runCatching { songsNow(period) }.getOrDefault(emptyList())
            var waited = 0
            while (Stats.songs.isEmpty() && waited++ < 20) { Thread.sleep(150); songs = runCatching { songsNow(period) }.getOrDefault(songs) }
            if (songs.isNotEmpty()) { top = period to songs; madeAt = System.currentTimeMillis() }
            return songs
        }

        fun update(ctx: Context) {
            val ids = HomeWidgets.ids(ctx, TopWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val made = top
            val now = System.currentTimeMillis()
            // Not made yet, made for another period, or half an hour old: made again (and shown once it's in)
            if ((made == null || made.first != period() || now - madeAt > 30 * 60_000L) && now - askedAt > 5_000L) refresh(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            runCatching {
                mgr.updateAppWidget(ids, views(ctx, made))
                @Suppress("DEPRECATION") mgr.notifyAppWidgetViewDataChanged(ids, R.id.wt_list)
            }
        }

        private fun views(ctx: Context, made: Pair<String, List<Track>>?): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_top)
            HomeWidgets.paint(ctx, v)
            v.setTextViewText(R.id.wt_period, (PERIODS[made?.first ?: period()] ?: "") + "  ▾")   // a tap: the next period
            v.setOnClickPendingIntent(R.id.wt_period, HomeWidgets.tap(ctx, TopWidget::class.java, CYCLE, 7431))
            val songs = made?.second.orEmpty()
            @Suppress("DEPRECATION") v.setRemoteAdapter(R.id.wt_list, android.content.Intent(ctx, TopWidgetRows::class.java))
            v.setPendingIntentTemplate(R.id.wt_list, HomeWidgets.openTemplate(ctx, 7432))   // a song: Most played in the app
            v.setViewVisibility(R.id.wt_empty, if (made != null && songs.isEmpty()) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.wt_root, HomeWidgets.open(ctx, "top", 7430))
            return v
        }
    }
}

/** The Most played widget's list: every song of the period, numbered, with its plays. */
class TopWidgetRows : android.widget.RemoteViewsService() {
    override fun onGetViewFactory(intent: android.content.Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var rows = emptyList<Track>()
        override fun onCreate() {}
        override fun onDataSetChanged() { rows = runCatching { TopWidget.rowsNow(applicationContext) }.getOrDefault(emptyList()) }
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews {
            val t = rows.getOrNull(position) ?: return RemoteViews(packageName, R.layout.widget_top_row)
            return RemoteViews(packageName, R.layout.widget_top_row).apply {
                setTextViewText(R.id.wtr_title, "${position + 1}.  ${t.title}" + if (t.artist.isNotEmpty()) " — ${t.artist}" else "")
                setTextViewText(R.id.wtr_count, if (t.plays > 0) "%,d".format(t.plays) else "")
                setTextColor(R.id.wtr_title, HomeWidgets.ink())
                setTextColor(R.id.wtr_count, HomeWidgets.ink())
                setOnClickFillInIntent(R.id.wtr_row, android.content.Intent().putExtra(HomeWidgets.OPEN, "top"))
            }
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}

/** Wrapped: brings you straight to your Wrapped, this week's, this month's or all time's (a tap on its name: Wrapped
 *  opens on its own choice of period, as from the menu). */
class WrappedWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    companion object {
        fun update(ctx: Context) {
            val ids = HomeWidgets.ids(ctx, WrappedWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_wrapped)
            HomeWidgets.paint(ctx, v)
            v.setOnClickPendingIntent(R.id.ww_main, HomeWidgets.open(ctx, "wrapped", 7440))
            val buttons = mapOf("week" to R.id.ww_week, "month" to R.id.ww_month, "all" to R.id.ww_all)
            PhoneWidgetRules.WRAPPED.forEachIndexed { i, (key, name) ->
                val id = buttons[key] ?: return@forEachIndexed
                v.setTextViewText(id, name)
                v.setOnClickPendingIntent(id, HomeWidgets.open(ctx, "wrapped:$key", 7441 + i))
            }
            runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v) }
        }
    }
}
