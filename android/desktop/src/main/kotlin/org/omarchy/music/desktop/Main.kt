package org.omarchy.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Radon Player Next on the PC: your songs, searched as you type (the phone's own matching), played through mpv.
 *  The first screen of the one-codebase app; the rest comes over from the phone's screens. */
fun main() = application {
    val player = remember { Player() }
    Window(onCloseRequest = { player.stop(); exitApplication() }, title = "Radon Player Next",
        state = rememberWindowState(width = 1100.dp, height = 760.dp)) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFF3B5C))) { App(player) }
    }
}

@Composable
fun App(player: Player) {
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var q by remember { mutableStateOf("") }
    var now by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(Unit) { songs = withContext(Dispatchers.IO) { Library.scan() } }
    val shown = remember(songs, q) { Library.search(songs ?: emptyList(), q) }

    fun play(i: Int) {
        val s = shown.getOrNull(i) ?: return
        now = s
        player.play(s.file) { now = null }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121214)).padding(16.dp)) {
        Text("Radon Player", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(when (val all = songs) {
            null -> "Reading your music…"
            else -> "${all.size} songs in ${Library.folders().joinToString { it.path }.ifEmpty { "(no Music folder: set RADON_MUSIC)" }}"
        }, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search your music") }, singleLine = true)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(now?.let { "${it.title} — ${it.artist}" } ?: "Nothing playing", color = Color.White, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Button({ player.seek(-10.0) }) { Text("−10 s") }
            Button({ player.togglePause() }) { Text("Play / pause") }
            Button({ player.seek(10.0) }) { Text("+10 s") }
            Button({ player.stop(); now = null }) { Text("Stop") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(shown.size) { i ->
                val s = shown[i]
                Row(Modifier.fillMaxWidth().clickable { play(i) }.padding(vertical = 6.dp, horizontal = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = if (s == now) MaterialTheme.colorScheme.primary else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(s.artist, s.album).filter { it.isNotEmpty() }.joinToString(" · "), color = Color.Gray,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.seconds > 0) Text("%d:%02d".format(s.seconds / 60, s.seconds % 60), color = Color.Gray)
                }
            }
        }
    }
}
