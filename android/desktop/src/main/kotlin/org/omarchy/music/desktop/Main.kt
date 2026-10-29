package org.omarchy.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.omarchy.music.fmtTime
import org.omarchy.music.ui.C
import org.omarchy.music.ui.Ico
import org.omarchy.music.ui.Icon
import org.omarchy.music.ui.OmarchyTheme
import org.omarchy.music.ui.R
import org.omarchy.music.ui.T

/** Radon Player Next on the PC: your songs, searched as you type (the phone's own matching), played through mpv,
 *  in the phone's own look and icons (ui-common). The first screen of the one-codebase app. */
fun main() = application {
    val player = remember { Player() }
    Window(onCloseRequest = { player.stop(); exitApplication() }, title = "Radon Player Next",
        state = rememberWindowState(width = 1100.dp, height = 760.dp)) {
        OmarchyTheme { App(player) }
    }
}

@Composable
fun App(player: Player) {
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var q by remember { mutableStateOf("") }
    var now by remember { mutableStateOf<Song?>(null) }
    var paused by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("songs") }   // songs, albums, artists or playlists
    var lists by remember { mutableStateOf<List<Library.Playlist>>(emptyList()) }
    var open by remember { mutableStateOf<Library.Playlist?>(null) }
    var group by remember { mutableStateOf<Library.Group?>(null) }   // the album or artist open
    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) { Library.scan() }
        lists = withContext(Dispatchers.IO) { Library.playlists() }
    }
    // What the list shows: your songs searched, or the open playlist's songs that are on this PC
    val shown = remember(songs, q, tab, open) {
        val all = songs ?: emptyList()
        val pl = open
        val g = group
        if (tab == "playlists" && pl != null) pl.tracks.mapNotNull { t ->
            Library.fileOf(t, all)?.let { f -> Song(f, t.title, t.artist, t.album, "", 0, t.duration.toInt()) } }
        else if (g != null) g.songs
        else Library.search(all, q)
    }
    // Albums or artists, searched as you type (by their name and artist)
    val groups = remember(songs, q, tab) {
        val all = songs ?: emptyList()
        when (tab) {
            "albums" -> Library.albums(all)
            "artists" -> Library.artists(all)
            else -> emptyList()
        }.let { gs -> Library.searchGroups(gs, q) }
    }

    fun play(i: Int) {
        val s = shown.getOrNull(i) ?: return
        now = s; paused = false
        player.play(s.file) { now = null }
    }

    Column(Modifier.fillMaxSize().background(C.bg).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("Radon Player", style = T.brand)
        Text(when (val all = songs) {
            null -> "Reading your music…"
            else -> "%,d songs · %s".format(all.size, Library.folders().joinToString { it.path }.ifEmpty { "no Music folder (set RADON_MUSIC)" })
        }, style = T.sub, modifier = Modifier.padding(bottom = 10.dp))
        Row(Modifier.fillMaxWidth().clip(R.pill).background(C.light).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Ico.SEARCH, C.muted, 20.dp)
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                if (q.isEmpty()) Text("Search your music", style = T.sub)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = T.body, cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth())
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, name) in listOf("songs" to "Songs", "albums" to "Albums", "artists" to "Artists", "playlists" to "Playlists")) {
                val on = tab == key
                Text(name, style = T.tab.copy(color = if (on) C.darker else C.fg), modifier = Modifier.clip(R.pill)
                    .background(if (on) C.accent else C.light).clickable { tab = key; open = null; group = null }
                    .padding(horizontal = 14.dp, vertical = 6.dp))
            }
            open?.let { Text("‹ ${it.name}", style = T.title, modifier = Modifier.clickable { open = null }.padding(6.dp)) }
            group?.let { Text("‹ ${it.name}", style = T.title, modifier = Modifier.clickable { group = null }.padding(6.dp)) }
        }
        if (tab == "playlists" && open == null) LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(lists.size) { i ->
                val pl = lists[i]
                Row(Modifier.fillMaxWidth().clip(R.small).clickable { open = pl }.padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.PLAYLIST, C.muted, 20.dp)
                    Text(pl.name, style = T.title, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("%,d songs".format(pl.tracks.size), style = T.time)
                }
            }
        } else if ((tab == "albums" || tab == "artists") && group == null) LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(groups.size) { i ->
                val gr = groups[i]
                Row(Modifier.fillMaxWidth().clip(R.small).clickable { group = gr }.padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (tab == "albums") Ico.DISC else Ico.PERSON, C.muted, 20.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(gr.name, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (gr.sub.isNotEmpty()) Text(gr.sub, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("%,d songs".format(gr.songs.size), style = T.time)
                }
            }
        } else LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(shown.size) { i ->
                val s = shown[i]
                val on = s == now
                Row(Modifier.fillMaxWidth().clip(R.small).background(if (on) C.selection.copy(alpha = 0.35f) else C.bg)
                    .clickable { play(i) }.padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.NOTE, if (on) C.accent else C.muted, 20.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.title, style = T.title.copy(color = if (on) C.accent else C.fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(s.artist, s.album).filter { it.isNotEmpty() }.joinToString(" · "), style = T.sub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.seconds > 0) Text(fmtTime(s.seconds.toDouble()), style = T.time)
                }
            }
        }
        // The player bar: what's playing, back 10 s, play/pause, forward 10 s, stop
        Row(Modifier.fillMaxWidth().clip(R.card).background(C.light).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(now?.title ?: "Nothing playing", style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now?.artist ?: "Pick a song", style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.clickable { player.seek(-10.0) }) { Icon(Ico.PREV, C.fg, 26.dp) }
            Box(Modifier.size(44.dp).clip(R.pill).background(C.accent).clickable {
                if (now != null) { player.togglePause(); paused = !paused } else play(0)
            }, contentAlignment = Alignment.Center) { Icon(if (now == null || paused) Ico.PLAY else Ico.PAUSE, C.darker, 26.dp) }
            Box(Modifier.clickable { player.seek(10.0) }) { Icon(Ico.NEXT, C.fg, 26.dp) }
            Box(Modifier.clickable { player.stop(); now = null }) { Icon(Ico.CLOSE, C.muted, 22.dp) }
        }
    }
}
