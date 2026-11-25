package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.Music
import org.omarchy.music.VersionQueue

/** Utilities › Queued clean/explicit changes: the songs you put aside to change to their explicit or clean version
 *  (Queue a clean/explicit change), what it's for, the version to change them to beside Scan, and for each song the
 *  version the scan found on Tidal, to listen to and Replace. */
@OptIn(ExperimentalLayoutApi::class)
fun androidx.compose.foundation.lazy.LazyListScope.versionQueueView(ui: Ui) {
    val items = VersionQueue.version.let { VersionQueue.items() }
    val want = if (VersionQueue.explicit) "explicit" else "clean"
    item {
        DisposableEffect(Unit) { onDispose { VersionQueue.stopPreview() } }   // the version playing beside the music stops as you leave
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("Songs to change to their explicit or clean version. A song downloaded from another service or app (DumpMedia " +
                "mostly) often comes as the clean version when you wanted it explicit, or explicit when you wanted it clean. " +
                "Select songs anywhere and pick Queue a clean/explicit change, choose which version you want below, and Scan: " +
                "it reads each song's name, artist, album and length and finds that version on Tidal. Listen to it, then " +
                "Replace: the right version is downloaded, then the version it replaces is deleted (a download of the app's at " +
                "once; a file of yours goes to the ${if (onPhone) "trash, once you say yes to Android" else "Recycle Bin"}), and " +
                "the new one takes its place in your playlists, queue and plays.", style = T.sub)
            if (!Music.tidal.signedIn) Text("The scan needs Tidal: sign in to it in Settings first.", style = T.sub.copy(color = C.accent),
                modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The version to change them to, beside Scan
                Box {
                    var open by remember { mutableStateOf(false) }
                    Pill("Change to: ${want.replaceFirstChar { it.uppercase() }} ▾", quiet = true) { open = true }
                    androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.light) {
                        listOf(true to "Explicit", false to "Clean").forEach { (k, l) ->
                            androidx.compose.material3.DropdownMenuItem({ Text(l, style = T.body.copy(color = if (k == VersionQueue.explicit) C.accent else C.fg)) },
                                { open = false; VersionQueue.want(k) })
                        }
                    }
                }
                val s = VersionQueue.scanning
                if (s != null) {
                    Text("Scanning: %,d of %,d".format(s.first, s.second), style = T.sub.copy(color = C.accent))
                    Pill("Stop", quiet = true) { VersionQueue.stopScan() }
                } else if (items.isNotEmpty()) Pill("Scan") { VersionQueue.scan() }
                val ready = items.count { VersionQueue.found[it.key]?.tidal != null }
                if (ready > 0) Pill("Replace all found (%,d)".format(ready), quiet = true) {
                    ui.confirm = ("Download the $want version of %,d song%s and delete the versions they replace?").format(ready, if (ready != 1) "s" else "") to {
                        VersionQueue.replaceAll() }
                }
                if (items.isNotEmpty()) Pill("Clear the list", quiet = true) { ui.confirm = "Take every song off the list? Nothing is changed." to { VersionQueue.clear() } }
            }
        }
    }
    if (items.isEmpty()) { item { Empty("No queued clean/explicit changes", "Select songs and pick Queue a clean/explicit change.") }; return }
    item { Section("%,d SONG%s".format(items.size, if (items.size != 1) "S" else "")) }
    for (i in items) item(key = "vq-" + i.title + "\t" + i.artist) { QueuedVersionRow(i, want) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueuedVersionRow(i: VersionQueue.Item, want: String) {
    // Your copy, looked for off the screen's thread (it goes through your songs), again when your music changes
    val mine by produceState<org.omarchy.music.Track?>(null, i.key, Music.musicVersion) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { VersionQueue.song(i) }
    }
    val f = VersionQueue.found[i.key]
    val st = VersionQueue.state[i.key]
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(C.light)
        .border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val t = mine
            val art = remember(t) { t?.let { artModel(it) } }
            ArtImage(art, 44.dp, Modifier.clickable { t?.let { Music.playList(listOf(it)) } })
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(i.title, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(i.artist, i.album).filter { it.isNotEmpty() }.joinToString(" · ") +
                    (t?.let { " · yours: " + org.omarchy.music.fmtTime(it.duration) } ?: " · not on this device"),
                    style = T.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            st != null -> Text(st, style = T.sub.copy(color = C.accent), modifier = Modifier.padding(top = 6.dp))
            f == null -> Text("Not scanned yet for the $want version", style = T.sub, modifier = Modifier.padding(top = 6.dp))
            f.track == null -> Text(f.note, style = T.sub, modifier = Modifier.padding(top = 6.dp))
            else -> Text("Found: ${f.track.title} · ${f.track.album} · ${if (f.explicit) "Explicit" else "Clean"} · " +
                org.omarchy.music.fmtTime(f.track.duration), style = T.sub.copy(color = C.accent), modifier = Modifier.padding(top = 6.dp))
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            mine?.let { t -> Pill("▶ Yours", quiet = true) { VersionQueue.stopPreview(); Music.playList(listOf(t)) } }
            if (f?.track != null && st == null) {
                Pill(if (VersionQueue.previewing == i.key) "■ Stop" else "▶ Found version", quiet = true) { VersionQueue.listen(i) }
                Pill("Replace") { VersionQueue.replace(i) }
            }
            Pill("Take off the list", quiet = true) { VersionQueue.remove(i) }
        }
    }
}
