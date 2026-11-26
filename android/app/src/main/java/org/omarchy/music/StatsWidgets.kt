package org.omarchy.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Most played: a tap brings you straight to Most played in the app (the stats). On it, your most played songs (as many
 *  of the top five as fit) over the period Most played shows (This week, This month, This year or All time, chosen
 *  there). It never wakes the phone by itself: it's made again when your plays or the period change, and when you
 *  come back to the home screen after half an hour. */
class TopWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: android.os.Bundle) = update(ctx)

    companion object {
        private val ROWS = intArrayOf(R.id.wt_r0, R.id.wt_r1, R.id.wt_r2, R.id.wt_r3, R.id.wt_r4)
        private val TITLES = intArrayOf(R.id.wt_t0, R.id.wt_t1, R.id.wt_t2, R.id.wt_t3, R.id.wt_t4)
        private val COUNTS = intArrayOf(R.id.wt_c0, R.id.wt_c1, R.id.wt_c2, R.id.wt_c3, R.id.wt_c4)
        private val PERIODS = mapOf("week" to "This week", "month" to "This month", "year" to "This year", "all" to "All time")

        /** The songs shown, and the period they're for (made in the background: [refresh]). */
        @Volatile private var top: Pair<String, List<Track>>? = null
        @Volatile private var madeAt = 0L
        @Volatile private var askedAt = 0L   // when they were last asked for
        private var tries = 0

        /** The top songs worked out again (plays came in, the period changed), then shown. */
        fun refresh(ctx: Context) {
            if (HomeWidgets.ids(ctx, TopWidget::class.java).isEmpty()) return
            Music.init(ctx)
            askedAt = System.currentTimeMillis()
            val period = Music.topPeriod
            Music.bg({ Music.mostPlayed(period).filter { !StatHides.song(it) }.take(ROWS.size) }) { songs, _ ->
                if (songs == null) return@bg
                top = period to songs
                madeAt = System.currentTimeMillis()
                update(ctx)
                // Just after the app started the plays may not be counted yet: looked at again a little later
                if (songs.isEmpty() && tries++ < 3) Music.scope.launch { delay(5000); refresh(ctx) }
            }
        }

        fun update(ctx: Context) {
            val ids = HomeWidgets.ids(ctx, TopWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val made = top
            val now = System.currentTimeMillis()
            // Not made yet, made for another period, or half an hour old: made again (and shown once it's in)
            if ((made == null || made.first != Music.topPeriod || now - madeAt > 30 * 60_000L) && now - askedAt > 5_000L) refresh(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            for (id in ids) runCatching { mgr.updateAppWidget(id, views(ctx, made, rowsFor(ctx, mgr, id))) }
        }

        /** How many song rows fit in the widget as it's sized (the head and the edges take the rest; larger text, taller
         *  rows): never one cut in half. */
        private fun rowsFor(ctx: Context, mgr: AppWidgetManager, id: Int): Int {
            val o = mgr.getAppWidgetOptions(id)
            val portrait = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            val h = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            if (h <= 0) return ROWS.size
            return PhoneWidgetRules.rowsThatFit(h, 24 + 36, (28 * ctx.resources.configuration.fontScale).toInt(), ROWS.size)
        }

        private fun views(ctx: Context, made: Pair<String, List<Track>>?, rows: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_top)
            HomeWidgets.paint(ctx, v)
            v.setTextViewText(R.id.wt_period, PERIODS[made?.first ?: Music.topPeriod] ?: "")
            val songs = made?.second.orEmpty()
            for (i in ROWS.indices) {
                val t = songs.getOrNull(i)?.takeIf { i < rows }
                v.setViewVisibility(ROWS[i], if (t != null) View.VISIBLE else View.GONE)
                if (t == null) continue
                v.setTextViewText(TITLES[i], "${i + 1}.  ${t.title}" + if (t.artist.isNotEmpty()) " — ${t.artist}" else "")
                v.setTextViewText(COUNTS[i], if (t.plays > 0) "%,d".format(t.plays) else "")
            }
            v.setViewVisibility(R.id.wt_empty, if (made != null && songs.isEmpty()) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.wt_root, HomeWidgets.open(ctx, "top", 7430))
            return v
        }
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
