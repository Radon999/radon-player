package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.Music
import org.omarchy.music.Prefs
import kotlin.math.roundToInt

/** Your own order of the Playlists screen (its sort "Your order"): the playlists' ids, first at the top. Kept as a
 *  switch (Prefs), so Sync takes it to your other device (a synced playlist has the same id on both). */
object PlaylistOrder {
    const val PREF = "playlists_order"
    fun ids(): List<String> = Prefs.text(PREF).split(',').filter { it.isNotBlank() }
    fun set(ids: List<String>) = Prefs.setText(PREF, ids.distinct().joinToString(","))
    /** [lists] in your order: the ones you placed where you put them; any not placed yet (new ones) first, as they came. */
    fun <T> sorted(lists: List<T>, id: (T) -> String): List<T> {
        val at = ids().withIndex().associate { it.value to it.index }
        if (at.isEmpty()) return lists
        val (placed, fresh) = lists.partition { id(it) in at }
        return fresh + placed.sortedBy { at[id(it)] }
    }
}

/** What's being rearranged: your playlists (the Playlists screen, Your order), or one playlist's songs (its own order). */
sealed class RearrangeWhat {
    object Playlists : RearrangeWhat()
    data class Songs(val playlistId: String) : RearrangeWhat()
}

private class RearrangeItem(val key: String, val title: String, val sub: String, val art: Any?)

/** Rearrange: a playlist's songs, or your playlists. Drag a row's ≡ up or down (as in the queue), or tap ▲ ▼ to move it one
 *  place. Done keeps the new order; ‹ (or Back) leaves it as it was. */
@Composable
fun RearrangeScreen(ui: Ui, what: RearrangeWhat) {
    val items = remember(what) {
        mutableStateListOf<RearrangeItem>().apply {
            when (what) {
                is RearrangeWhat.Songs -> Music.lists.get(what.playlistId)?.tracks.orEmpty().forEachIndexed { i, t ->
                    add(RearrangeItem("$i|" + t.id, t.title, t.artist, artModel(t))) }
                RearrangeWhat.Playlists -> PlaylistOrder.sorted(org.omarchy.music.YourPlaylists.outsideFolders(Music.lists.all())
                    .filter { it.discovery == null }) { it.id }.forEach { pl ->
                        val n = pl.tracks.size
                        add(RearrangeItem(pl.id, pl.shown, "$n song${if (n != 1) "s" else ""}",
                            PlaylistCovers.of(pl.id) ?: pl.tracks.firstOrNull { it.thumb.isNotEmpty() || it.isFile }?.let { artModel(it) })) }
            }
        }
    }
    val close = { ui.rearrange = null }
    val title = when (what) {
        is RearrangeWhat.Songs -> "Rearrange " + (Music.lists.get(what.playlistId)?.name ?: "the playlist")
        RearrangeWhat.Playlists -> "Rearrange playlists"
    }
    fun move(from: Int, to: Int) {
        if (items.isEmpty() || from !in items.indices) return
        val t = to.coerceIn(0, items.size - 1)
        if (from == t) return
        val x = items.removeAt(from); items.add(t, x)
    }
    fun done() {
        when (what) {
            is RearrangeWhat.Songs -> Music.lists.get(what.playlistId)?.let { pl ->
                val byKey = pl.tracks.withIndex().associate { (i, t) -> "$i|" + t.id to t }
                val now = items.mapNotNull { byKey[it.key] }
                if (now.size == pl.tracks.size && now != pl.tracks) {
                    Music.lists.replaceTracks(pl.id, now)
                    Music.musicVersion++
                }
            }
            RearrangeWhat.Playlists -> {
                // Those in folders keep their places, after these
                val shown = items.map { it.key }
                PlaylistOrder.set(shown + PlaylistOrder.ids().filter { it !in shown })
                Music.musicVersion++
            }
        }
        close()
    }
    FullScreen(title, close, actions = { Pill("Done") { done() } }) {
        Text("Drag the ≡ of a row up or down, or tap ▲ ▼ to move it one place. Done keeps the new order.",
            style = T.sub, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        val rowH = 60.dp
        val rowPx = with(LocalDensity.current) { rowH.toPx() }
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            items(items.size, key = { items[it].key }) { i ->
                val item = items[i]
                var dy by remember(item.key) { mutableFloatStateOf(0f) }
                Row(Modifier.fillMaxWidth().height(rowH).graphicsLayer(translationY = dy)
                    .background(if (dy != 0f) C.light else Color.Transparent, R.card).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArtImage(item.art, 46.dp)
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(item.title, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.sub.isNotEmpty()) Text(item.sub, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (i > 0) IconButton(Ico.UP, C.muted, 18.dp, 6.dp, desc = "Move up") { move(i, i - 1) }
                    else Box(Modifier.size(30.dp))
                    if (i < items.size - 1) IconButton(Ico.DOWN, C.muted, 18.dp, 6.dp, desc = "Move down") { move(i, i + 1) }
                    else Box(Modifier.size(30.dp))
                    Box(Modifier.size(44.dp).pointerInput(item.key) {
                        detectVerticalDragGestures(onDragStart = { dy = 0f },
                            onDragEnd = {
                                val steps = (dy / rowPx).roundToInt()
                                dy = 0f
                                val from = items.indexOfFirst { it.key == item.key }
                                if (steps != 0 && from >= 0) move(from, from + steps)
                            },
                            onDragCancel = { dy = 0f }) { _, d -> dy += d }
                    }, contentAlignment = Alignment.Center) { Icon(Ico.GRIP, C.muted, 20.dp) }
                }
            }
        }
    }
}
