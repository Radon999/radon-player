package org.omarchy.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.Music
import org.omarchy.music.Playlist
import org.omarchy.music.Track

/** The discovered songs, each with Download and Remove, and Apply. */
@Composable
fun ReviewDialog(ui: Ui, pl: Playlist) {
    val get = remember(pl.id) { mutableStateMapOf<String, Boolean>() }
    val drop = remember(pl.id) { mutableStateMapOf<String, Boolean>() }
    val box = CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.darker, uncheckedColor = C.muted)
    AlertDialog(onDismissRequest = { ui.reviewFor = null }, containerColor = C.light,
        title = { Text(pl.name, style = T.head, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text("Download the ones to keep, remove the ones to let go, then Apply.", style = T.sub)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Download all", quiet = true) { pl.tracks.filter { !Music.lib.has(it.id) }.forEach { get[it.id] = true } }
                    Pill("Remove all", quiet = true) { pl.tracks.forEach { drop[it.id] = true } }
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(pl.tracks.size) { i ->
                        val t = pl.tracks[i]
                        val have = Music.lib.has(t.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(t.artist, t.album).filter { it.isNotEmpty() }.joinToString(" · "), style = T.sub, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Checkbox(get[t.id] == true, { get[t.id] = it }, enabled = !have, colors = box)
                                Text(if (have) "Saved" else "Download", style = T.time)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Checkbox(drop[t.id] == true, { drop[t.id] = it }, colors = box)
                                Text("Remove", style = T.time)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                Music.reviewDiscovered(pl.id, get.filterValues { it }.keys, drop.filterValues { it }.keys)
                ui.reviewFor = null
            }) { Text("Apply", color = C.accent) }
        },
        dismissButton = { TextButton({ ui.reviewFor = null }) { Text("Cancel", color = C.muted) } })
}

/** Where a song's file is: its folder, to copy (Android can't open a file manager at a file). */
fun showLocation(ui: Ui, t: Track) {
    val folder = "/storage/emulated/0/" + (if (Music.lib.has(t.id)) Music.downloadFolder() else Music.folderOf(t)).trimEnd('/')
    ui.choices = "In $folder" to listOf("Copy the folder" to {
        val cm = Music.ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Folder", folder))
        Music.toast("Copied")
    })
}

/** Tag now, seen first: what the tagger would change in the first few songs (yours → new), then Tag all. */
@Composable
fun TagPreviewDialog(ui: Ui, ids: List<String>, full: Boolean) {
    val tagger = org.omarchy.music.Tagger
    var plans by androidx.compose.runtime.remember(ids) { androidx.compose.runtime.mutableStateOf<List<org.omarchy.music.Tagger.Plan>?>(null) }
    var err by androidx.compose.runtime.remember(ids) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(ids) {
        Music.bg({ ids.take(3).mapNotNull { id ->
            tagger.plan(id, light = !full && (Music.lib.items[id]?.tagged ?: 0) >= 1) } }) { p, e ->
            plans = p ?: emptyList(); err = e?.message
        }
    }
    val n = ids.size
    fun short(v: List<String>?) = v?.joinToString("; ")?.replace(Regex("\\s+"), " ")?.let { if (it.length > 90) it.take(88) + "…" else it }
        ?.ifEmpty { null } ?: "—"
    AlertDialog(onDismissRequest = { ui.tagPreview = null }, containerColor = C.light,
        title = { Text("Tag preview", style = T.head) },
        text = {
            Column {
                Text(when {
                    err != null -> "Couldn't look it up: ${err?.take(120)}"
                    plans == null -> if (n == 1) "Looking it up…" else "Looking up the first ${minOf(3, n)} of $n songs, to show what changes…"
                    n == 1 -> "What changes, yours on the left:"
                    else -> "What changes in the first ${plans!!.size}, yours on the left. The rest are done the same way."
                }, style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    val ps = plans ?: emptyList()
                    items(ps.size) { i ->
                        val p = ps[i]
                        val e = Music.lib.items[p.id]
                        Text("${e?.title ?: ""} — ${e?.artist ?: ""}", style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        if (p.fill.isEmpty()) Text("Nothing to change", style = T.sub)
                        p.fill.forEach { (k, v) ->
                            Column(Modifier.padding(bottom = 4.dp)) {
                                Text(tagger.NAMES[k] ?: k, style = T.time)
                                Text("${short(p.old[k])}  →  ${short(v)}", style = T.sub)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                val ps = plans ?: return@TextButton
                ui.tagPreview = null
                Music.bg({ ps.forEach { tagger.write(it) } }) { _, _ -> Music.savedVersion++ }   // looked up already: written as shown
                val rest = ids.filter { id -> ps.none { it.id == id } }
                if (rest.isNotEmpty()) tagger.start(rest, full) else Music.toast(if (n == 1) "Tagged" else "Tagged $n songs")
            }, enabled = plans != null) { Text(if (n == 1) "Tag it" else "Tag all $n", color = C.accent) }
        },
        dismissButton = { TextButton({ ui.tagPreview = null }) { Text("Cancel", color = C.muted) } })
}
