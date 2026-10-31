package org.omarchy.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.Music
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.omarchy.music.EmptyFolder
import org.omarchy.music.FixupChange
import org.omarchy.music.FixupPick
import org.omarchy.music.FixupSong
import org.omarchy.music.LiveName
import org.omarchy.music.MusicTools
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch

// The Python app's tools on your songs' files (MusicTools): each a full-screen list you check before anything changes.

@Composable
fun ToolScreens(ui: Ui) {
    when (ui.tool) {
        "fixup" -> FixupScreen(ui)
        "live" -> LiveNamesScreen(ui)
        "movie" -> LiveNamesScreen(ui, movie = true)
        "lone" -> LiveNamesScreen(ui, lone = true)
        "nocover" -> NoCoversScreen(ui)
        "truth" -> AlbumTruthScreen(ui)
        "empty" -> EmptyFoldersScreen(ui)
        "skip" -> SkipFoldersScreen(ui)
        "quality" -> QualityScreen(ui)
    }
    ui.identifyFor?.let { IdentifyDialog(ui, it) }
}

/** "N hidden · Show them again": the things you marked in this scan to never show, brought back (the scan looks again). */
@Composable
private fun HiddenLine(ui: Ui, scan: String) {
    org.omarchy.music.ScanIgnore.version
    val n = org.omarchy.music.ScanIgnore.count(scan)
    if (n == 0) return
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$n hidden from this scan", style = T.sub, modifier = Modifier.weight(1f))
        Pill("Show them again", quiet = true) {
            org.omarchy.music.ScanIgnore.clear(scan)
            val t = ui.tool; ui.tool = null
            Music.scope.launch { kotlinx.coroutines.delay(60); ui.tool = t }
        }
    }
}

/** Fix albums and artists: albums split by featured artists, albums beside their deluxe edition, singles of songs on an
 *  album you have. Each: Change (as shown), Leave alone, or another album; then the files moved to the right folders. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FixupScreen(ui: Ui) {
    val close = { ui.tool = null }
    var status by remember { mutableStateOf("Reading your songs' tags…") }
    var plan by remember { mutableStateOf<Triple<List<FixupChange>, List<FixupSong>, Int>?>(null) }
    val pick = remember { mutableStateMapOf<Int, FixupPick>() }
    var moves by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var report by remember { mutableStateOf<List<String>?>(null) }
    var working by remember { mutableStateOf(false) }
    val hidden = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(Unit) {
        Music.bg({ MusicTools.fixupScan { s -> Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { status = s } } }) { r, e ->
            if (r == null) { report = listOf("Couldn't scan: ${e?.message}"); return@bg }
            plan = r
            org.omarchy.music.fixupDefaults(r.first).forEachIndexed { i, on -> pick[i] = if (on) FixupPick.Change else FixupPick.Leave }
        }
    }
    FullScreen("Fix albums and artists", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            val p = plan
            Text(when {
                report != null -> report!!.first()
                working -> "Changing them…"
                p == null -> status
                moves != null -> "${moves!!.size} file${if (moves!!.size != 1) "s" else ""} would be better in another folder: Move puts each with its album"
                p.first.isEmpty() -> "Nothing to fix: no split albums, editions or singles" + if (p.third > 0) " (${p.third} in folders left alone)" else ""
                else -> "${p.first.size - hidden.size} song${if (p.first.size - hidden.size != 1) "s" else ""} to fix in the files' tags (titles are never changed). " +
                    "Change as shown, Leave alone, or pick another album" + if (p.third > 0) " · ${p.third} in folders left alone" else ""
            }, style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            if (report == null && moves == null) HiddenLine(ui, "fixup")
            when {
                report != null -> LazyColumn(Modifier.weight(1f)) { items(report!!.drop(1)) { Text(it, style = T.body, modifier = Modifier.padding(vertical = 4.dp)) } }
                moves != null -> LazyColumn(Modifier.weight(1f)) { items(moves!!) { (a, b) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(a.substringAfterLast('/'), style = T.title)
                        Text("→ ${b.substringBeforeLast('/')}", style = T.sub)
                    }
                } }
                p != null && !working -> LazyColumn(Modifier.weight(1f)) {
                    items(p.first.size) { i ->
                        val c = p.first[i]
                        if (hidden[i] == true) return@items
                        val now = pick[i] ?: FixupPick.Change
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(c.title, style = T.title)
                            Text(c.why, style = T.sub)
                            for ((k, v) in c.new) Text("$k: ${c.old.get(k).ifEmpty { "(none)" }} → ${v.ifEmpty { "(none)" }}", style = T.sub)
                            if (c.unsure) Text("Not sure of this one: check it", style = T.sub.copy(color = C.red))
                            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Chip("Change", now is FixupPick.Change) { pick[i] = FixupPick.Change }
                                Chip("Leave alone", now is FixupPick.Leave) { pick[i] = FixupPick.Leave }
                                for (o in c.others.orEmpty()) Chip(o, now is FixupPick.Other && now.name == o) { pick[i] = FixupPick.Other(o) }
                                Chip("Never show", false) {
                                    org.omarchy.music.ScanIgnore.add("fixup", org.omarchy.music.ScanIgnore.fixupKey(c)); pick[i] = FixupPick.Leave; hidden[i] = true }
                            }
                        }
                    }
                }
                else -> Spacer(Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill(if (report != null) "Done" else "Cancel", quiet = true) { close() }
                if (p != null && report == null && !working && moves == null && p.first.isNotEmpty()) Pill("Change ${pick.values.count { it !is FixupPick.Leave }}") {
                    working = true
                    val picks = p.first.indices.map { pick[it] ?: FixupPick.Change }
                    Music.bg({
                        val chosen = org.omarchy.music.fixupWithOthers(p.first, picks)
                        val (done, failed) = MusicTools.fixupApply(chosen)
                        Triple(done, failed, MusicTools.fixupMovesFor(chosen, p.second))
                    }) { r, e ->
                        working = false
                        if (r == null) { report = listOf("Couldn't change them: ${e?.message}"); return@bg }
                        Music.rescan()
                        if (r.third.isNotEmpty() && r.second.isEmpty()) moves = r.third
                        else report = listOf("Changed ${r.first} song${if (r.first != 1) "s" else ""}") + r.second
                    }
                }
                if (moves != null && report == null && !working) {
                    Pill("Leave them", quiet = true) { report = listOf("Changed: the files stay where they are") }
                    Pill("Move") {
                        working = true
                        Music.bg({ MusicTools.moveFiles(moves!!) }) { r, e ->
                            working = false
                            report = listOf(if (r != null) "Moved ${r.first} file${if (r.first != 1) "s" else ""}" else "Couldn't move them: ${e?.message}") + (r?.second ?: emptyList())
                        }
                    }
                }
            }
        }
    }
}

/** Live in song names: songs named as live recordings you'd rather have plain; the live part taken off, the rest kept. */
@Composable
private fun LiveNamesScreen(ui: Ui, movie: Boolean = false, lone: Boolean = false) {
    val close = { ui.tool = null }
    var found by remember { mutableStateOf<List<LiveName>?>(null) }
    val pick = remember { mutableStateMapOf<Int, Boolean>() }
    val hidden = remember { mutableStateMapOf<Int, Boolean>() }
    val scan = if (lone) "lone" else if (movie) "movie" else "live"
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { Music.bg({ MusicTools.songs().let { if (lone) MusicTools.loneFound(it) else if (movie) MusicTools.movieFound(it) else MusicTools.liveFound(it) } }) { r, e -> found = r ?: emptyList(); if (r == null) report = "Couldn't read them: ${e?.message}" } }
    FullScreen(if (lone) "Songs alone in an album" else if (movie) "Soundtrack notes in names" else "Live in song names", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            val f = found
            Text(report ?: when {
                f == null -> "Reading your songs' tags…"
                f.isEmpty() -> if (lone) "No song is alone in an album or EP" else if (movie) "No song has a soundtrack note in its name" else "No song is named as a live recording"
                lone -> "${f.size} song${if (f.size != 1) "s are" else " is"} the only one you have of ${if (f.size != 1) "their" else "its"} album or EP. Tick the ones to make singles (the album tag becomes the song's name); the others stay in their album (not asked again)"
                movie -> "${f.size} song${if (f.size != 1) "s have" else " has"} a note like “(From the motion picture …)” in the name. Tick the ones to take it off; the others are kept as they are (not asked again)"
                else -> "${f.size} song${if (f.size != 1) "s are" else " is"} named as live recording${if (f.size != 1) "s" else ""}. Tick the ones to make plain; the others are kept as they are (not asked again)"
            }, style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            if (report == null) HiddenLine(ui, scan)
            LazyColumn(Modifier.weight(1f)) {
                items(if (report == null) f?.size ?: 0 else 0) { i ->
                    val l = f!![i]
                    if (hidden[i] == true) return@items
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(pick[i] ?: true, { pick[i] = it })
                        Column(Modifier.weight(1f)) { Text("${l.title} → ${l.name}", style = T.body); Text(l.artist, style = T.sub) }
                        Pill(if (lone) "Keep in album" else if (movie) "Not a soundtrack note" else "Not live", quiet = true) {
                            org.omarchy.music.ScanIgnore.add(scan, org.omarchy.music.ScanIgnore.fileKey(l.file)); pick[i] = false; hidden[i] = true }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill(if (report != null) "Done" else "Cancel", quiet = true) { close() }
                if (report == null && !f.isNullOrEmpty()) Pill(if (lone) "Make singles" else "Rename") {
                    val picks = f.indices.map { if (pick[it] ?: true) FixupPick.Change else FixupPick.Leave }
                    Music.bg({ if (lone) MusicTools.loneApply(f, picks) else if (movie) MusicTools.movieApply(f, picks) else MusicTools.liveApply(f, picks) }) { n, e ->
                        report = if (n != null) (if (lone) "Made $n single${if (n != 1) "s" else ""}" else "Renamed $n") else "Couldn't: ${e?.message}"; Music.rescan() }
                }
            }
        }
    }
}

/** Albums, EPs and singles: each release looked up for what it really is; its name made to say so. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AlbumTruthScreen(ui: Ui) {
    val close = { ui.tool = null }
    var status by remember { mutableStateOf("Looking up your releases…") }
    var rows by remember { mutableStateOf<List<org.omarchy.music.AlbumTruth.Row>?>(null) }
    val pick = remember { mutableStateMapOf<Int, String>() }   // the kind to make it, or "" left alone
    var report by remember { mutableStateOf<String?>(null) }
    val hidden = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(Unit) {
        Music.bg({ org.omarchy.music.AlbumTruth.scan { s -> Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { status = s } }
            .filter { !org.omarchy.music.ScanIgnore.has("truth", org.omarchy.music.ScanIgnore.albumKey(it.artist, it.album)) } }) { r, e ->
            rows = r ?: emptyList(); if (r == null) report = "Couldn't look them up: ${e?.message}"
        }
    }
    fun label(k: String) = when (k) { "single" -> "Single"; "ep" -> "EP"; else -> "Album" }
    FullScreen("Albums, EPs and singles", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            val r = rows
            Text(report ?: when {
                r == null -> status
                r.isEmpty() -> "Every release is named as what it is: nothing to change"
                else -> "${r.size} release${if (r.size != 1) "s" else ""} to check. Change gives the name " +
                    "“ - Single” or “ - EP” (or takes it off an album) so it shows in the right tab; the ones no service knows are left alone unless you pick"
            }, style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            if (report == null) HiddenLine(ui, "truth")
            if (report == null && !r.isNullOrEmpty()) androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Change all", quiet = true) { r.indices.forEach { i -> if (hidden[i] != true) r[i].truth?.let { pick[i] = it } } }
                Pill("Leave all alone", quiet = true) { r.indices.forEach { pick[it] = "" } }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(if (report == null) r?.size ?: 0 else 0) { i ->
                    val row = r!![i]
                    if (hidden[i] == true) return@items
                    val now = pick[i] ?: row.truth ?: ""
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(row.album, style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${row.artist} · ${row.songs.size} song${if (row.songs.size != 1) "s" else ""} here · now ${label(row.now)}", style = T.sub)
                        Text(if (row.truth == null) "No service knows it: not sure. Pick what it is, or leave it"
                            else "${row.source}: ${label(row.truth)}" + (row.tracks?.let { " of $it song${if (it != 1) "s" else ""}" } ?: "") +
                                " → “${org.omarchy.music.AlbumTruth.named(row.album, row.truth)}”", style = T.sub)
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Leave alone", now.isEmpty()) { pick[i] = "" }
                            for (k in listOf("album", "ep", "single")) Chip(label(k), now == k) { pick[i] = k }
                            Chip("Never show", false) {
                                org.omarchy.music.ScanIgnore.add("truth", org.omarchy.music.ScanIgnore.albumKey(row.artist, row.album)); pick[i] = ""; hidden[i] = true }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill(if (report != null) "Done" else "Cancel", quiet = true) { close() }
                if (report == null && !r.isNullOrEmpty()) Pill("Change") {
                    var n = 0
                    r.forEachIndexed { i, row ->
                        val k = pick[i] ?: row.truth ?: ""
                        if (k.isEmpty()) return@forEachIndexed
                        val name = org.omarchy.music.AlbumTruth.named(row.album, k)
                        if (name != row.album) { Music.editTags(row.songs, mapOf("album" to name)); n++ }
                    }
                    report = if (n > 0) "Renaming $n release${if (n != 1) "s" else ""}: they move to the right tab as the tags are written" else "Nothing changed"
                }
            }
        }
    }
}

/** Songs without covers: each album whose songs show no cover anywhere, with Pick a cover (the Cover… dialog), one
 *  album or all of them in turn. The list is looked at again after each pick. */
@Composable
private fun NoCoversScreen(ui: Ui) {
    val close = { ui.tool = null }
    var status by remember { mutableStateOf("Looking at your covers…") }
    var found by remember { mutableStateOf<List<List<org.omarchy.music.Track>>?>(null) }
    val picking = ui.coverFor != null
    LaunchedEffect(picking) {
        if (picking) return@LaunchedEffect
        Music.bg({ MusicTools.noCovers { s -> Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { status = s } } }) { r, e ->
            found = r ?: emptyList(); if (r == null) status = "Couldn't look: ${e?.message}"
        }
    }
    org.omarchy.music.ScanIgnore.version
    FullScreen("Songs without covers", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            val f = found
            Text(when {
                f == null -> status
                f.isEmpty() -> "Every song has a cover"
                else -> "${f.size} album${if (f.size != 1) "s" else ""} (${f.sumOf { it.size }} songs) show no cover. Pick one for each: " +
                    "it's searched for you, or choose a picture of your own"
            }, style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            HiddenLine(ui, "nocover")
            LazyColumn(Modifier.weight(1f)) {
                items(f.orEmpty()) { songs ->
                    val t = songs[0]
                    val key = org.omarchy.music.ScanIgnore.albumKey(t.artist, t.album.ifEmpty { t.title })
                    if (org.omarchy.music.ScanIgnore.has("nocover", key)) return@items
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.album.ifEmpty { t.title }, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${t.artist} · ${songs.size} song${if (songs.size != 1) "s" else ""}", style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Pill("No cover needed", quiet = true) { org.omarchy.music.ScanIgnore.add("nocover", key) }
                        Spacer(Modifier.padding(start = 6.dp))
                        Pill("Pick a cover", quiet = true) { ui.coverFor = listOf(songs) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill("Done", quiet = true) { close() }
                if (!f.isNullOrEmpty()) Pill("Fix them all…") { ui.coverFor = f.filter { g -> !org.omarchy.music.ScanIgnore.has("nocover",
                    org.omarchy.music.ScanIgnore.albumKey(g[0].artist, g[0].album.ifEmpty { g[0].title })) } }
            }
        }
    }
}

/** Empty folders: folders in your music folders with no songs left in them; you pick which go to the Recycle Bin. */
@Composable
private fun EmptyFoldersScreen(ui: Ui) {
    val close = { ui.tool = null }
    var found by remember { mutableStateOf<List<EmptyFolder>?>(null) }
    val pick = remember { mutableStateMapOf<Int, Boolean>() }
    var report by remember { mutableStateOf<String?>(null) }
    val hidden = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(Unit) { Music.bg({ MusicTools.emptyScan() }) { r, e -> found = r ?: emptyList(); if (r == null) report = "Couldn't look: ${e?.message}" } }
    FullScreen("Empty folders", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            val f = found
            Text(report ?: when {
                f == null -> "Looking through your music folders…"
                f.isEmpty() -> "No empty folders: every folder has songs in it"
                else -> "${f.size} folder${if (f.size != 1) "s have" else " has"} no songs left in ${if (f.size != 1) "them" else "it"} (only pictures, lyrics and the like). Tick the ones to delete"
            }, style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            if (report == null) HiddenLine(ui, "empty")
            LazyColumn(Modifier.weight(1f)) {
                items(if (report == null) f?.size ?: 0 else 0) { i ->
                    val d = f!![i]
                    if (hidden[i] == true) return@items
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(pick[i] ?: true, { pick[i] = it })
                        Pill("Keep this folder", quiet = true) { org.omarchy.music.ScanIgnore.add("empty", d.folder); pick[i] = false; hidden[i] = true }
                        Spacer(Modifier.padding(start = 6.dp))
                        Column {
                            Text(d.folder, style = T.body)
                            Text("%.1f MB · %s".format(d.bytes / 1e6, d.files.take(4).joinToString().ifEmpty { "nothing in it" }), style = T.sub)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill(if (report != null) "Done" else "Cancel", quiet = true) { close() }
                if (report == null && !f.isNullOrEmpty()) Pill("Delete") {
                    val chosen = f.filterIndexed { i, _ -> pick[i] ?: true }
                    Music.bg({ MusicTools.emptyRemove(chosen) }) { n, e -> report = if (n != null) "$n folder${if (n != 1) "s" else ""} to $TRASH" else "Couldn't: ${e?.message}" }
                }
            }
        }
    }
}

/** Folders to leave alone: no scan here touches or looks in them, and no songs are moved into them. */
@Composable
private fun SkipFoldersScreen(ui: Ui) {
    val close = { ui.tool = null }
    val list = remember { mutableStateListOf<String>().apply { addAll(MusicTools.skip) } }
    val counts = remember { Music.folderSongs().map { (f, s) -> org.omarchy.music.ui.fullFolderPath(f.trimEnd('/')).replace('\\', '/') to s.size } }
    FullScreen("Folders to leave alone", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text("Fix albums and artists, the tag scanner, Empty folders and Live in song names never touch the songs in these folders, " +
                "and no songs are moved into them: unreleased songs, sessions…", style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(counts.size) { i ->
                    val (f, n) = counts[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(f in list, { if (it) list += f else list -= f })
                        Column { Text(f, style = T.body); Text("$n song${if (n != 1) "s" else ""}", style = T.sub) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill("Cancel", quiet = true) { close() }
                Pill("Save") { MusicTools.chooseSkip(list.toList()); close() }
            }
        }
    }
}

/** Loudness check › Show…: the songs clipped or not the hi-res they say, the worst first. */
@Composable
private fun QualityScreen(ui: Ui) {
    val list = remember { org.omarchy.music.QualityCheck.flagged() }
    org.omarchy.music.ScanIgnore.version
    FullScreen("Loudness check", { ui.tool = null }) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            item { HiddenLine(ui, "quality") }
            items(list.filter { !org.omarchy.music.ScanIgnore.has("quality", org.omarchy.music.ScanIgnore.fileKey(it.first)) }) { (f, r) ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Pill("It's fine: hide it", quiet = true) { org.omarchy.music.ScanIgnore.add("quality", org.omarchy.music.ScanIgnore.fileKey(f)) }
                    Text(f.substringAfterLast('/').substringAfterLast('\\'), style = T.title)
                    Text(listOfNotNull(if (r.clipped > 0) "clipped (${"%,d".format(r.clipped)} samples at full scale)" else null,
                        r.fake.ifEmpty { null }, r.peak?.let { "peak ${it} dBFS" }).joinToString(" · "), style = T.sub)
                    Text(f.substringBeforeLast('/'), style = T.sub)
                }
            }
        }
    }
}

/** Identify by sound…: what the song is by its sound (AcoustID); Use these tags writes them into its file. */
@Composable
private fun IdentifyDialog(ui: Ui, t: org.omarchy.music.Track) {
    var got by remember { mutableStateOf<List<org.omarchy.music.QualityCheck.Identified>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val file = t.file ?: Music.lib.items[t.id]?.file
    LaunchedEffect(t.id) {
        if (file == null) { err = "Only a song's file can be identified"; return@LaunchedEffect }
        Music.bg({ org.omarchy.music.QualityCheck.identify(file, t.duration.toInt()) }) { r, e -> got = r; err = e?.message?.take(160) }
    }
    AlertDialog(onDismissRequest = { ui.identifyFor = null }, containerColor = C.light,
        title = { Text("Identify by sound", style = T.title) },
        text = {
            Column {
                Text(err ?: when {
                    got == null -> "Listening to “${t.title}”…"
                    got!!.isEmpty() -> "AcoustID doesn't know this song"
                    file != null && !org.omarchy.music.FileTags.canWrite(file) -> "What it sounds like (the likeliest first). The phone can " +
                        "only change the tags of its own downloads, so this one's are shown but can't be written"
                    else -> "What it sounds like (the likeliest first): pick one to use its tags"
                }, style = T.sub)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(got.orEmpty()) { r ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = file != null && org.omarchy.music.FileTags.canWrite(file)) {
                            val f = file ?: return@clickable
                            Music.bg({ org.omarchy.music.FileTags.write(Music.ctx, f, mapOf("title" to r.title, "artist" to r.artist) +
                                (if (r.album.isNotEmpty()) mapOf("album" to r.album) else emptyMap())) }) { ok, _ ->
                                Music.toast(if (ok == true) "Tagged: ${r.title} — ${r.artist}" else "Couldn't write its tags"); Music.rescan()
                            }
                            ui.identifyFor = null
                        }.padding(vertical = 6.dp)) {
                            Text("${r.title} — ${r.artist}", style = T.body)
                            Text(listOf(r.album, r.type, "${(r.score * 100).toInt()}%").filter { it.isNotEmpty() }.joinToString(" · "), style = T.sub)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ ui.identifyFor = null }) { Text("Close", color = C.muted) } })
}
