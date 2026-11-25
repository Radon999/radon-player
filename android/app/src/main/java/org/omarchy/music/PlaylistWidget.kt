package org.omarchy.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.omarchy.music.ui.C
import org.omarchy.music.ui.Chip
import org.omarchy.music.ui.Empty
import org.omarchy.music.ui.OmarchyTheme
import org.omarchy.music.ui.PlaylistCovers
import org.omarchy.music.ui.PlaylistRow
import org.omarchy.music.ui.T
import org.omarchy.music.ui.artModel

/** The Playlist shortcut: one playlist of yours, picked as the widget is added (PlaylistWidgetSetup; long-press the
 *  widget to pick another), with its cover and name. A tap plays it without opening the app (see WidgetStart): In order,
 *  or Shuffled (as the playlist's own Shuffle does), as the switch under it says. Each widget keeps its own playlist and
 *  order. */
class PlaylistWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = update(ctx)

    override fun onDeleted(ctx: Context, ids: IntArray) {
        Music.init(ctx)
        for (id in ids) { Prefs.setText(listKey(id), ""); Prefs.set(shuffleKey(id), false) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        when (intent.action) {
            PLAY -> {
                Music.init(ctx)
                val list = Prefs.text(listKey(id)).takeIf { it.isNotEmpty() } ?: return
                WidgetStart.request(ctx, PhoneWidgetRules.list(list, Prefs.on(shuffleKey(id))))
            }
            ORDER, SHUFFLE -> { Music.init(ctx); Prefs.set(shuffleKey(id), intent.action == SHUFFLE); update(ctx) }
        }
    }

    companion object {
        private const val PREFIX = "org.omarchy.music.playlistwidget."
        private const val PLAY = PREFIX + "play"
        private const val ORDER = PREFIX + "order"
        private const val SHUFFLE = PREFIX + "shuffle"

        /** The settings each widget keeps: its playlist, and whether it plays it shuffled. */
        fun listKey(id: Int) = "phone_widget_playlist_$id"
        fun shuffleKey(id: Int) = "phone_widget_playlist_${id}_shuffle"

        /** Each playlist's cover as the widget shows it: (what it was made from, the picture; null while it loads). */
        private val covers = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Bitmap?>>()

        private fun coverOf(ctx: Context, pl: Playlist): Bitmap? {
            // Its own cover (Set a cover…), else its first song's, as the Playlists tab shows it
            val model: Any? = PlaylistCovers.of(pl.id) ?: pl.tracks.firstOrNull { it.thumb.isNotEmpty() || it.isFile }?.let { artModel(it, 300) }
            val key = model?.toString() ?: ""
            covers[pl.id]?.takeIf { it.first == key }?.let { return it.second }
            covers[pl.id] = key to null
            if (model != null) Music.bg({
                val res = kotlinx.coroutines.runBlocking { coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx)
                    .data(model).size(300).allowHardware(false).build()) }
                (res as? coil.request.SuccessResult)?.drawable?.toBitmap()
            }) { bmp, _ ->
                if (bmp != null && covers[pl.id]?.first == key) { covers[pl.id] = key to bmp; update(ctx) }
            }
            return null
        }

        private fun tap(ctx: Context, action: String, id: Int) =
            HomeWidgets.tap(ctx, PlaylistWidget::class.java, action, id) { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) }

        /** The widget's playlist picker, to pick (another) one. */
        private fun setup(ctx: Context, id: Int) = PendingIntent.getActivity(ctx, id,
            Intent(ctx, PlaylistWidgetSetup::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun update(ctx: Context) {
            val ids = HomeWidgets.ids(ctx, PlaylistWidget::class.java)
            if (ids.isEmpty()) return
            Music.init(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            for (id in ids) runCatching { mgr.updateAppWidget(id, views(ctx, id)) }
        }

        /** One widget, just set up (the home screen doesn't ask for it to be drawn then). */
        fun update(ctx: Context, id: Int) {
            Music.init(ctx)
            runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(id, views(ctx, id)) }
        }

        private fun views(ctx: Context, id: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_playlist)
            HomeWidgets.paint(ctx, v, R.id.wpl_root)
            val listId = Prefs.text(listKey(id))
            val pl = listId.takeIf { it.isNotEmpty() }?.let { Music.lists.get(it) }
            val shuffled = Prefs.on(shuffleKey(id))
            v.setTextViewText(R.id.wpl_name, pl?.name ?: if (listId.isEmpty()) "Tap to pick a playlist" else "Its playlist was deleted: tap to pick another")
            val bmp = pl?.let { coverOf(ctx, it) }
            if (bmp != null) v.setImageViewBitmap(R.id.wpl_cover, bmp) else v.setImageViewResource(R.id.wpl_cover, R.drawable.w_playlist)
            HomeWidgets.choice(v, R.id.wpl_order, !shuffled)
            HomeWidgets.choice(v, R.id.wpl_shuffle, shuffled)
            v.setOnClickPendingIntent(R.id.wpl_main, if (pl != null) tap(ctx, PLAY, id) else setup(ctx, id))
            v.setOnClickPendingIntent(R.id.wpl_order, tap(ctx, ORDER, id))
            v.setOnClickPendingIntent(R.id.wpl_shuffle, tap(ctx, SHUFFLE, id))
            return v
        }
    }
}

/** The Playlist shortcut's picker: shown as the widget is added (and when it's set up again): your playlists, and
 *  whether it plays the one you pick in order or shuffled. */
class PlaylistWidgetSetup : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backed out of: no widget is added
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        Music.init(this)
        enableEdgeToEdge()
        setContent {
            OmarchyTheme {
                var shuffled by remember { mutableStateOf(Prefs.on(PlaylistWidget.shuffleKey(id))) }
                val lists = remember { Music.lists.all() }
                Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
                    Text("Pick a playlist", style = T.head, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp))
                    Text("A tap on the widget plays it, without opening the app. Its switch changes the order later.", style = T.sub,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("In order", !shuffled) { shuffled = false }
                        Chip("Shuffled", shuffled) { shuffled = true }
                    }
                    if (lists.isEmpty()) Empty("No playlists yet", "Make one in Radon Player, then add this widget again.")
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(lists, key = { it.id }) { pl ->
                            val cover = pl.tracks.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
                            PlaylistRow(Album(pl.id, pl.shown, "", "", pl.tracks.size.toString()), PlaylistCovers.of(pl.id) ?: cover?.let { artModel(it) }) {
                                pick(id, pl.id, shuffled)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pick(id: Int, list: String, shuffled: Boolean) {
        Prefs.setText(PlaylistWidget.listKey(id), list)
        Prefs.set(PlaylistWidget.shuffleKey(id), shuffled)
        PlaylistWidget.update(this, id)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
