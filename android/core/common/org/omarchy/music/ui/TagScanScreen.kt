package org.omarchy.music.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.Music
import org.omarchy.music.TagScan

/** Settings › Tagger › Scan…: the desktop's tag scanner. Your downloads' tags checked against Deezer, an album at a
 *  time: each tag Replace (ticked) or Keep, each cover one of up to three found or Keep; then written, and what was
 *  done said. */
@Composable
fun TagScanScreen(ui: Ui) {
    val close = { ui.tagScan = false }
    var groups by remember { mutableStateOf<List<TagScan.Group>?>(null) }
    var done by remember { mutableStateOf(0 to 0) }
    var report by remember { mutableStateOf<List<String>?>(null) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateMapOf<Pair<String, String>, Boolean>() }
    val covers = remember { mutableStateMapOf<String, String?>() }
    // Folders to leave alone are never scanned; songs checked before and unchanged since are skipped (Scan all again: not)
    val every = remember { TagScan.songs().filter { !org.omarchy.music.inFolders(it.first, org.omarchy.music.MusicTools.skip) } }
    var songs by remember { mutableStateOf<List<Pair<String, org.omarchy.music.Track>>?>(null) }
    val markAll = { songs?.map { it.first }?.let { fs -> Music.bg({ TagScan.Done.mark(fs) }) { _, _ -> } } }
    LaunchedEffect(Unit) {
        val fresh = ui.tagScanFresh; ui.tagScanFresh = false
        songs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { if (fresh) TagScan.Done.forget(); every.filter { !TagScan.Done.done(it.first) } }
        val songs = songs ?: emptyList()
        if (songs.isEmpty()) return@LaunchedEffect
        Music.bg({
            TagScan.plan(songs, TagScan::findTrack, TagScan::albumInfo, { ar, al, found ->
                found + runCatching { TagScan.albumCovers(ar, al) }.getOrDefault(emptyList()) +
                    runCatching { org.omarchy.music.Pictures.coverChoices(ar, al).map { it.url } }.getOrDefault(emptyList())
            }, progress = { i, n -> Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { done = i to n } })
        }) { res, err ->
            if (err != null) failed = err.message ?: "Couldn't scan"
            groups = res ?: emptyList()
            res?.forEach { g -> if (g.covers.isNotEmpty()) covers[g.key] = if (g.coverNote.startsWith("No cover")) g.covers.first() else null }
        }
    }
    FullScreen("Scan tags", close) {
        val gs = groups
        val r = report
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            val songs = songs
            when {
                songs == null -> Text("Looking at your songs…", style = T.sub)
                songs.isEmpty() && every.isNotEmpty() -> {
                    Text("All ${"%,d".format(every.size)} songs were checked before and haven't changed since: nothing new to scan. " +
                        "Scan all again (Settings › Tagger) checks every one.", style = T.sub)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) { Pill("Done") { close() } }
                }
                songs.isEmpty() -> Text("Only songs the app downloaded (FLAC) can have their tags changed on the phone: Android keeps " +
                    "other apps' files (your own songs) to themselves. Scan on the PC for those.", style = T.sub)
                r != null -> {
                    Text("Done.", style = T.title)
                    LazyColumn(Modifier.weight(1f)) { items(r) { Text(it, style = T.sub, modifier = Modifier.padding(vertical = 2.dp)) } }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) { Pill("Done") { close() } }
                }
                working -> Text("Writing…", style = T.sub)
                failed != null -> Text("Couldn't scan: $failed", style = T.sub)
                gs == null -> {
                    Text("Checking ${songs.size} songs' tags against Deezer…", style = T.sub)
                    LinearProgressIndicator(progress = { if (done.second > 0) done.first.toFloat() / done.second else 0f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    Text("%,d of %,d songs".format(done.first, done.second), style = T.sub)
                }
                gs.isEmpty() -> {
                    LaunchedEffect(Unit) { markAll() }
                    Text("All ${songs.size} songs' tags look right: nothing missing, nothing to change.", style = T.sub)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) { Pill("Done") { close() } }
                }
                else -> {
                    Text("${gs.size} albums to look at. Missing tags are ticked to Replace, different ones to Keep: change what you like, then Apply.",
                        style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(gs, key = { it.key }) { g -> ScanCard(g, picked, covers) }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        Pill("Replace all", quiet = true) { gs.forEach { g -> g.songs.forEach { s -> s.changes.keys.forEach { picked[s.uri to it] = true } } } }
                        Pill("Keep all", quiet = true) {
                            gs.forEach { g -> g.songs.forEach { s -> s.changes.keys.forEach { picked[s.uri to it] = false } }; if (g.covers.isNotEmpty()) covers[g.key] = null }
                        }
                        Pill("Apply") {
                            val (tags, cv) = TagScan.changes(gs, picked.toMap(), covers.toMap())
                            if (tags.isEmpty() && cv.isEmpty()) { markAll(); report = listOf("Nothing changed: every tag and cover was kept."); return@Pill }
                            working = true
                            val uris = gs.associate { it.key to it.uris }
                            Music.bg({ TagScan.apply(tags, cv.mapValues { (k, u) -> u to (uris[k] ?: emptyList()) }) }) { res, err ->
                                working = false
                                report = res ?: listOf("Couldn't finish: ${err?.message}")
                                Music.musicVersion++
                                Music.savedVersion++
                                markAll()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanCard(g: TagScan.Group, picked: MutableMap<Pair<String, String>, Boolean>, covers: MutableMap<String, String?>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("${g.album.ifEmpty { "Unknown album" }} — ${g.artist.ifEmpty { "Unknown artist" }}", style = T.title,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (g.covers.isNotEmpty()) {
            Text("Cover (${g.coverNote.lowercase()}):", style = T.sub, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<String?>(null) + g.covers).forEachIndexed { i, url ->
                    val on = covers[g.key] == url
                    Column(Modifier.clip(R.card).border(2.dp, if (on) C.accent else androidx.compose.ui.graphics.Color.Transparent, R.card)
                        .clickable { covers[g.key] = url }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (url == null) ArtImage(null, 84.dp) else ArtImage(url, 84.dp)
                        Text(if (url == null) (if (g.coverNote.startsWith("No cover")) "Keep (none)" else "Keep") else "Cover $i",
                            style = T.sub.copy(color = if (on) C.accent else C.muted))
                    }
                }
            }
        }
        // The album's tags: one tick for each change that's the same on several songs.
        val albumKeys = TagScan.ALBUM_FIELDS.map { it.first }
        val same = LinkedHashMap<Pair<String, TagScan.Change>, MutableList<Pair<String, String>>>()
        for (f in albumKeys) for (s in g.songs) s.changes[f]?.let { c -> same.getOrPut(f to c) { ArrayList() } += s.uri to f }
        for ((fc, keys) in same) {
            val (f, c) = fc
            Tick("${TagScan.FIELDS[f]}: ${c.old.ifEmpty { "(none)" }} → ${c.new}" + if (keys.size > 1) "  · ${keys.size} songs" else "",
                keys.all { picked[it] ?: c.replace }) { on -> keys.forEach { picked[it] = on } }
        }
        for (s in g.songs) {
            val mine = s.changes.filterKeys { it !in albumKeys }
            if (mine.isEmpty()) continue
            Text("♪ ${s.title}", style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            for ((f, c) in mine) Tick("${TagScan.FIELDS[f]}: ${c.old.ifEmpty { "(none)" }} → ${c.new}", picked[s.uri to f] ?: c.replace,
                Modifier.padding(start = 16.dp)) { picked[s.uri to f] = it }
        }
    }
}

@Composable
private fun Tick(text: String, on: Boolean, modifier: Modifier = Modifier, set: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().clickable { set(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, { set(it) }, colors = CheckboxDefaults.colors(checkedColor = C.accent))
        Text(text, style = T.body)
    }
}
