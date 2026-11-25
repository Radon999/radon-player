package org.omarchy.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.ConcertRules
import org.omarchy.music.Music
import org.omarchy.music.SOURCES
import org.omarchy.music.SetlistRules
import org.omarchy.music.Setlists
import org.omarchy.music.Track

/** A setlist to open: the show (or what they've been playing lately), what it's called on top, a line about it, and
 *  the name a playlist of it gets. */
class SetlistView(val set: SetlistRules.Setlist, val title: String, val note: String, val playlist: String)

/** One show of theirs. */
fun showView(artist: String, s: SetlistRules.Setlist) =
    SetlistView(s, SetlistRules.line(s), SetlistRules.sub(s), SetlistRules.playlistName(artist, s.venue, s.city, s.date))

/** What they've been playing at their latest shows; next: their coming show, when one is known (a playlist of it is
 *  named after that show, to listen to before going). */
fun latelyView(artist: String, l: SetlistRules.Setlist, next: ConcertRules.Concert?): SetlistView {
    val from = "From their last " + (if (l.shows == 1) "show" else "${l.shows} shows") + (if (l.tour.isNotEmpty()) " on ${l.tour}" else "")
    return if (next != null) SetlistView(l, "What they've been playing on this tour", "$from, before ${ConcertRules.line(next)}",
        SetlistRules.playlistName(artist, next.venue, next.city, next.date))
    else SetlistView(l, "What they've been playing lately", from, SetlistRules.playlistName(artist, l.tour.ifEmpty { "Live lately" }, "", l.date))
}

/** The Setlists section of an artist's page, below its heading: without a key, how to get one; else what they've been
 *  playing lately (on this tour, when a coming show is known) and their recent shows, each opening its songs. */
fun LazyListScope.setlistItems(artist: String, on: Boolean, got: Pair<List<SetlistRules.Setlist>?, String>,
                               next: ConcertRules.Concert?, open: (SetlistView) -> Unit) {
    val pad = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    if (!on) {
        item {
            val links = LocalUriHandler.current
            Column(pad, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Setlists come from setlist.fm, which asks for a key of your own. It's free: make an account at setlist.fm, " +
                    "ask for an API key on its API page (for personal use), then paste it in Settings › Remote & sync › Setlists.", style = T.sub)
                Pill("Get a key…", quiet = true) { runCatching { links.openUri("https://www.setlist.fm/settings/api") } }
            }
        }
        return
    }
    val (shows, why) = got
    if (shows == null) { item { Text(why.ifEmpty { "Looking at setlist.fm…" }, style = T.sub, modifier = pad) }; return }
    if (shows.isEmpty()) { item { Text("setlist.fm has no shows of $artist's listed", style = T.sub, modifier = pad) }; return }
    SetlistRules.lately(shows)?.let { l ->
        item(key = "setlist-lately") { val v = latelyView(artist, l, next); SetlistRow(v.title, v.note, lit = true) { open(v) } }
    }
    items(shows.size, key = { "setlist-" + shows[it].id }) { i -> val v = showView(artist, shows[i]); SetlistRow(v.title, v.note) { open(v) } }
}

@Composable
private fun SetlistRow(title: String, sub: String, lit: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp).clip(R.card).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = if (lit) T.body.copy(color = C.accent) else T.body)
            Text(sub, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Songs ›", style = T.sub)
    }
}

/** On a show in Concerts: its setlist once it has happened, else what they've been playing on this tour. Nothing
 *  while setlist.fm hasn't answered or has nothing of theirs. */
@Composable
fun SetlistLink(artist: String, c: ConcertRules.Concert, shows: List<SetlistRules.Setlist>?, open: (SetlistView) -> Unit) {
    val today = remember { java.time.LocalDate.now().toString() }
    val s = remember(shows, c) { shows?.let { SetlistRules.forShow(c.date, today, it) } } ?: return
    Text("Setlist ›", style = T.sub.copy(color = C.accent), modifier = Modifier.padding(end = 12.dp).clip(R.pill)
        .clickable { open(if (s.id.startsWith("lately:")) latelyView(artist, s, c) else showView(artist, s)) }.padding(horizontal = 4.dp, vertical = 2.dp))
}

/** A setlist opened: its songs in the order they were played, set by set, the encores marked. Each is looked for as
 *  you open it, yours first, else on a streaming service; Play plays the ones found, Make a playlist makes a playlist
 *  of yours of them (named after the show), and those not found are said. */
@Composable
fun SetlistDialog(artist: String, v: SetlistView, close: () -> Unit) {
    val songs = v.set.songs
    val todo = remember(v) { songs.indices.filter { !songs[it].tape } }
    val found = remember(v) { mutableStateMapOf<Int, Track?>() }   // a song's place in the list -> what was found (null: nothing)
    var done by remember(v) { mutableStateOf(todo.isEmpty()) }
    val links = LocalUriHandler.current
    LaunchedEffect(v) {
        for (i in todo) found[i] = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { Setlists.songOf(artist, songs[i]) }.getOrNull() }
        done = true
    }
    fun playable(): List<Track> = todo.mapNotNull { found[it] }.distinctBy { it.id }
    fun missing(): List<String> = todo.filter { it in found && found[it] == null }.map { songs[it].name }

    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(v.title, style = T.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(v.note, style = T.sub)
                val mine = todo.count { found[it]?.let { t -> Setlists.yours(t) } == true }
                val streamed = todo.count { found[it]?.let { t -> !Setlists.yours(t) } == true }
                Text(when {
                    todo.isEmpty() -> "setlist.fm has no songs listed for this show yet."
                    !done -> "Finding the songs… ${found.size} of ${todo.size}"
                    else -> "$mine yours, $streamed streamed" + missing().size.let { if (it > 0) ", $it not found" else "" }
                }, style = T.sub.copy(color = C.accent))
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(songs.size) { i ->
                        val s = songs[i]
                        val n = todo.indexOf(i)
                        Column {
                            if (s.part.isNotEmpty() && songs.getOrNull(i - 1)?.part != s.part)
                                Text(s.part, style = T.sub.copy(fontWeight = FontWeight.Bold, color = C.accent), modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                            val t = found[i]
                            Row(Modifier.fillMaxWidth().clip(R.card).clickable(enabled = t != null) {
                                val l = playable(); Music.playFrom(l, l.indexOfFirst { it.id == t?.id }.coerceAtLeast(0), radio = false)
                            }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text((if (n >= 0) "${n + 1}. " else "") + s.name, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val about = listOf(if (s.cover.isNotEmpty()) "${s.cover}'s song" else "", if (s.with.isNotEmpty()) "with ${s.with}" else "", s.info,
                                        if (s.tape) "played from a tape" else "",
                                        if (v.set.shows > 1 && s.played > 0) "played at ${s.played} of ${v.set.shows} shows" else "").filter { it.isNotEmpty() }
                                    if (about.isNotEmpty()) Text(about.joinToString(" · "), style = T.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (!s.tape) Text(when {
                                    i !in found -> "…"
                                    t == null -> "Not found"
                                    Setlists.yours(t) -> "Yours"
                                    else -> SOURCES[t.source] ?: t.source
                                }, style = T.sub.copy(color = if (t != null && Setlists.yours(t)) C.accent else C.muted), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                if (v.set.url.isNotEmpty()) Text("On setlist.fm ›", style = T.sub, modifier = Modifier.clip(R.pill)
                    .clickable { runCatching { links.openUri(v.set.url) } }.padding(vertical = 2.dp))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({
                    val l = playable()
                    val id = Music.newPlaylist(v.playlist)
                    Music.addToPlaylist(id, l)
                    missing().let { m -> if (m.isNotEmpty()) Music.toast("Not found, so not in “${v.playlist}”: " + m.joinToString(", ")) }
                    close()
                }, enabled = done && playable().isNotEmpty()) { Text("Make a playlist", color = if (done && playable().isNotEmpty()) C.accent else C.muted) }
                TextButton({ Music.playList(playable()); close() }, enabled = done && playable().isNotEmpty()) {
                    Text("Play", color = if (done && playable().isNotEmpty()) C.accent else C.muted) }
            }
        },
        dismissButton = { TextButton(close) { Text("Close", color = C.muted) } })
}
