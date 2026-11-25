package org.omarchy.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** The DJ widget: a tap starts the DJ without opening the app (see WidgetStart); with the DJ on, a tap carries on when
 *  it was paused, or switches it up (a new set now) while it plays. Its switch is the DJ's own (the same as in the
 *  app): Only my songs, or New songs too. */
class DjWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            START -> WidgetStart.request(ctx, "dj")
            MINE, NEW -> {
                Music.init(ctx)
                val on = intent.action == NEW
                if (on != Music.djNew) {
                    // As in the app: the DJ playing starts again with it. On but paused (or the app closed), the music
                    // isn't started from here: the choice is kept for the DJ's next start.
                    val playing = Remote.active || (Music.player != null && !Music.paused)
                    if (Music.dj.on && !playing) { Music.djNew = on; Music.saveState() } else Music.chooseDjNew(on)
                }
            }
            else -> return
        }
        update(ctx)
    }

    companion object {
        private const val PREFIX = "org.omarchy.music.djwidget."
        private const val START = PREFIX + "start"
        private const val MINE = PREFIX + "mine"
        private const val NEW = PREFIX + "new"

        fun update(ctx: Context) {
            val ids = HomeWidgets.ids(ctx, DjWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val d = Music.shownDj()
            val paused = if (Remote.active) Remote.status.paused else Music.paused || Music.player == null
            val kind = Dj.kindName(d.kind)
            val busy = Music.djBusy
            val state = when {
                busy != null -> busy   // "Picking your first set…"
                d.on && paused -> "On, paused · tap to carry on"
                d.on -> listOfNotNull("On", kind, "tap to switch it up").joinToString(" · ")
                else -> "Tap to start the DJ"
            }
            val v = RemoteViews(ctx.packageName, R.layout.widget_dj)
            HomeWidgets.paint(ctx, v, R.id.wd_root)
            v.setTextViewText(R.id.wd_state, state)
            HomeWidgets.choice(v, R.id.wd_mine, !Music.djNew)
            HomeWidgets.choice(v, R.id.wd_new, Music.djNew)
            v.setOnClickPendingIntent(R.id.wd_main, HomeWidgets.tap(ctx, DjWidget::class.java, START))
            v.setOnClickPendingIntent(R.id.wd_mine, HomeWidgets.tap(ctx, DjWidget::class.java, MINE))
            v.setOnClickPendingIntent(R.id.wd_new, HomeWidgets.tap(ctx, DjWidget::class.java, NEW))
            runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v) }
        }
    }
}
