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

/** The pairs in your plays (and songs) Most played can't tell the main artist of. */
fun jointCreditsNow(): List<Triple<String, List<String>, String?>> {
    val credits = HashMap<String, Int>()
    for (s in org.omarchy.music.Stats.songs) credits.merge(s.artist, s.plays, Int::plus)
    val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList())
    for (t in songs) credits.merge(t.artist, 0, Int::plus)
    return org.omarchy.music.jointCredits(credits, songs, Music.artistPicks)
}

/** Settings › Joint artists…: for each pair Most played can't tell the main artist of, whose plays they are (one of
 *  them, or the pair's own). Most played counts again on the way out. The desktop's Joint artists. */
@Composable
fun JointArtistsScreen(ui: Ui) {
    val rows = remember(Music.artistPicks, org.omarchy.music.Stats.songs) { jointCreditsNow() }
    var changed by remember { mutableStateOf(false) }
    val close = {
        ui.jointArtists = false
        if (changed) { org.omarchy.music.Stats.refresh(force = true); Music.toast("Counting Most played again with your picks") }
    }
    FullScreen("Joint artists", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text(if (rows.isEmpty()) "Every joint credit's plays already go to its main artist: nothing to pick."
                else "${rows.size} pairs whose main artist isn't clear (both or neither have songs of their own here). Pick whose plays " +
                    "they are in Most played, Artists and the time tabs.", style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.first }) { (credit, parts, picked) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(credit, style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box {
                            var open by remember { mutableStateOf(false) }
                            Pill(when (picked) { null -> "Not picked ▾"; "" -> "As a pair ▾"; else -> "$picked ▾" }, quiet = picked == null) { open = true }
                            DropdownMenu(open, { open = false }, containerColor = C.light) {
                                (listOf<String?>(null) + parts + "").forEach { n ->
                                    DropdownMenuItem(text = { Text(when (n) { null -> "Not picked (as a pair)"; "" -> "Keep as a pair"; else -> n }, style = T.body) },
                                        onClick = { open = false; Music.setArtistPick(credit, n); changed = true })
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) { Pill("Done") { close() } }
        }
    }
}

/** Merge into…: an artist's plays count as another's from now on (a Joint artists pick), and their songs the app
 *  downloaded get that artist in their tags (your own files are Android's to keep: the PC can change those). */
@Composable
fun MergeArtistDialog(ui: Ui, name: String) {
    var to by remember { mutableStateOf("") }
    val names = remember {
        (runCatching { Music.mySongs().flatMap { org.omarchy.music.ownArtists(it.artist) } }.getOrDefault(emptyList()) +
            org.omarchy.music.Stats.artists.take(500).map { it.name }).distinctBy { org.omarchy.music.norm(it) }
            .filter { org.omarchy.music.norm(it) != org.omarchy.music.norm(name) }.sortedBy { org.omarchy.music.norm(it) }
    }
    val q = org.omarchy.music.norm(to)
    val shown = names.filter { q.isEmpty() || q in org.omarchy.music.norm(it) }
        .sortedBy { !org.omarchy.music.norm(it).startsWith(q) }.take(30)
    androidx.compose.material3.AlertDialog(onDismissRequest = { ui.mergeArtist = null }, containerColor = C.light,
        title = { Text("Merge $name", style = T.head) },
        text = {
            Column {
                Text("Which artist is $name really? Their plays count as that artist's in Most played, Artists and Wrapped, " +
                    "and the songs the app downloaded get that artist in their tags.", style = T.sub)
                androidx.compose.material3.OutlinedTextField(to, { to = it }, singleLine = true, placeholder = { Text("Type an artist") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).typing())
                LazyColumn(Modifier.padding(top = 2.dp).fillMaxWidth().heightIn(max = 260.dp)) {
                    items(shown, key = { it }) { n ->
                        Text(n, style = T.body, modifier = Modifier.fillMaxWidth()
                            .clickable { to = n }.padding(vertical = 8.dp, horizontal = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton({
                val target = to.trim()
                if (target.isEmpty()) { Music.toast("Type the artist to merge into"); return@TextButton }
                ui.mergeArtist = null
                Music.mergeArtist(name, target)
            }) { Text("Merge", color = C.accent) }
        },
        dismissButton = { androidx.compose.material3.TextButton({ ui.mergeArtist = null }) { Text("Cancel", color = C.muted) } })
}

/** Similar artists…: the artists that look like another of yours, worked out from your songs and your plays. */
fun similarArtistsNow(): List<Pair<Pair<String, String>, Pair<Int, List<String>>>> {
    val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList())
    fun key(n: String) = org.omarchy.music.norm(n).replace(Regex("[\\W_]+"), "")
    val library = HashSet<String>(); val played = HashMap<String, Int>(); val titles = HashMap<String, HashSet<String>>()
    val counts = HashMap<String, Int>()
    for (t in songs) for (n in org.omarchy.music.ownArtists(t.artist)) {
        library += n; played.putIfAbsent(n, 0); counts.merge(key(n), 1, Int::plus)
        titles.getOrPut(key(n)) { HashSet() } += org.omarchy.music.norm(org.omarchy.music.cleanTitle(t.title))
    }
    for (s in org.omarchy.music.Stats.songs) for (n in org.omarchy.music.ownArtists(s.artist)) {
        played.merge(n, s.plays, Int::plus)
        titles.getOrPut(key(n)) { HashSet() } += org.omarchy.music.norm(org.omarchy.music.cleanTitle(s.title))
    }
    return org.omarchy.music.similarArtists(library, played, titles, Music.artistPicks, Music.similarKept.toSet(), counts)
}

/** Settings › Similar artists…: each Merge (the first into the second), the other way, or Keep apart; merged ones'
 *  plays, each song's too, go with them, and their songs the app downloaded get the artist in their tags. */
@Composable
fun SimilarArtistsScreen(ui: Ui) {
    val rows = remember { similarArtistsNow() }
    val pick = remember { androidx.compose.runtime.mutableStateMapOf<Int, String>() }   // "merge", "other", "keep"
    val close = { ui.similarArtists = false }
    FullScreen("Similar artists", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text(if (rows.isEmpty()) "No artists that look like another of yours." else "${rows.size} artists look like another of yours. " +
                "Merge puts the first's songs and plays (each song's too) under the second; Keep apart isn't asked about again.",
                style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(rows.size) { i ->
                    val (names, info) = rows[i]
                    val (from, to) = names
                    val now = pick[i] ?: if (info.second.isNotEmpty()) "merge" else "keep"
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("$from → $to", style = T.title)
                        Text(if (info.first > 0) "%,d plays as %s".format(info.first, from) else "No plays as $from", style = T.sub)
                        if (info.second.isNotEmpty()) Text("${info.second.size} song${if (info.second.size != 1) "s" else ""} of the same name: " +
                            info.second.take(5).joinToString(), style = T.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Merge", now == "merge") { pick[i] = "merge" }
                            Chip("Into $from", now == "other") { pick[i] = "other" }
                            Chip("Keep apart", now == "keep") { pick[i] = "keep" }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Pill(if (rows.isEmpty()) "Done" else "Apply") {
                    rows.forEachIndexed { i, (names, info) ->
                        val (from, to) = names
                        when (pick[i] ?: if (info.second.isNotEmpty()) "merge" else "keep") {
                            "merge" -> Music.mergeArtist(from, to)
                            "other" -> Music.mergeArtist(to, from)
                            else -> Music.keepApart(from, to)
                        }
                    }
                    close()
                }
            }
        }
    }
}

/** Settings › Plays on singles and albums…: songs whose imported plays are split between their single, an EP and the
 *  album (or its deluxe): each Merge plays (onto the album shown), another of its albums, one you type (yours suggested
 *  as you type), or Leave alone (not asked again). The plays stay the song's: this is which album they count for. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AlbumPlaysScreen(ui: Ui) {
    val close = { ui.albumPlays = false }
    var loaded by remember { mutableStateOf<List<org.omarchy.music.AlbumPlays>?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val mine = runCatching { Music.mySongs() }.getOrDefault(emptyList()); val picks = Music.albumPicks
        Music.bg({
            val plays = org.omarchy.music.SpotifyHistory.plays.map { org.omarchy.music.AlbumPlay(it.title, it.artist, it.album, 1) } +
                org.omarchy.music.OtherPlays.all.map { org.omarchy.music.AlbumPlay(it.title, it.artist, it.album, 1) } +
                org.omarchy.music.AppleHistory.rows.map { org.omarchy.music.AlbumPlay(it.title, it.artist, it.album, maxOf(it.count, 0)) } +
                org.omarchy.music.Wrapped.keptScrobbles(Music.ctx).map { org.omarchy.music.AlbumPlay(it.title, it.artist, it.album, 1) }   // Last.fm's too
            org.omarchy.music.albumPlaysPlan(plays, mine, picks)
        }) { r, _ -> loaded = r ?: emptyList() }
    }
    val rows = loaded ?: emptyList()
    val yours = remember { runCatching { Music.mySongs() }.getOrDefault(emptyList()).map { it.album }.filter { it.isNotBlank() }
        .distinctBy { org.omarchy.music.norm(it) } }
    val pick = remember { androidx.compose.runtime.mutableStateMapOf<Int, String>() }   // the album, or "" left alone
    var typing by remember { mutableStateOf<Int?>(null) }
    var typed by remember { mutableStateOf("") }
    FullScreen("Plays on singles and albums", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text(if (loaded == null) "Looking through your plays…" else if (rows.isEmpty()) "Every song's plays are on one album already (or there are no plays to look at yet)." else
                "${rows.size} song${if (rows.size != 1) "s have" else " has"} plays on more than one of their releases. Merge plays counts " +
                "them all for the album shown (a deluxe or extended edition first); Leave alone isn't asked again. The plays stay " +
                "the song's: this is which album they count for.", style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(rows.size) { i ->
                    val r = rows[i]
                    val now = pick[i] ?: r.to
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("${r.title} — ${r.artist}", style = T.title)
                        r.albums.forEach { (name, n) -> Text(if (n > 0) "$name: %,d play${if (n != 1) "s" else ""}".format(n) else "$name (you have it)", style = T.sub) }
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf(r.to) + r.albums.map { it.first }.filter { it != r.to }).forEach { name ->
                                Chip(if (name == r.to) "Merge onto $name" else name, now == name) { pick[i] = name }
                            }
                            if (now.isNotEmpty() && now != r.to && r.albums.none { it.first == now }) Chip(now, true) {}
                            Chip("Choose your own…", false) { typing = i; typed = "" }
                            Chip("Leave alone", now.isEmpty()) { pick[i] = "" }
                        }
                        if (typing == i) {
                            Field(typed, "Type an album") { typed = it }
                            val q = org.omarchy.music.norm(typed)
                            if (q.isNotEmpty()) yours.filter { q in org.omarchy.music.norm(it) }
                                .sortedBy { if (org.omarchy.music.norm(it).startsWith(q)) 0 else 1 }.take(6).forEach { name ->
                                    Text(name, style = T.body, modifier = Modifier.fillMaxWidth().clickable { pick[i] = name; typing = null }
                                        .padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            if (typed.isNotBlank()) Pill("Use “$typed”", quiet = true) { pick[i] = typed.trim(); typing = null }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Pill(if (rows.isEmpty()) "Done" else "Apply") {
                    val more = HashMap<String, String>()
                    rows.forEachIndexed { i, r ->
                        val to = pick[i] ?: r.to
                        for ((name, _) in r.albums)
                            more[org.omarchy.music.albumPlayKey(r.title, r.artist, name)] =
                                if (to.isEmpty() || org.omarchy.music.norm(name) == org.omarchy.music.norm(to)) "" else to
                    }
                    if (more.isNotEmpty()) Music.addAlbumPicks(more)
                    close()
                }
            }
        }
    }
}


/** Settings › Duplicates in Most played…: songs that may be one song counted twice, and versions of one; each Merge
 *  (counted as the one shown), another of its group, one you type (theirs suggested), or Leave alone (not asked again). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DuplicatesScreen(ui: Ui) {
    val close = { ui.duplicates = false }
    val rows = remember { org.omarchy.music.duplicateSongsPlan(org.omarchy.music.Stats.songs, Music.songPicks) }
    val titles = remember { org.omarchy.music.Stats.songs.groupBy { org.omarchy.music.Stats.statsKey("", it.artist).second }
        .mapValues { (_, v) -> v.map { it.title } } }
    val pick = remember { androidx.compose.runtime.mutableStateMapOf<Int, String>() }   // a title, or "" left alone
    var typing by remember { mutableStateOf<Int?>(null) }
    var typed by remember { mutableStateOf("") }
    FullScreen("Duplicates in Most played", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text(if (rows.isEmpty()) "No duplicates: every song counted once." else "${rows.size} song${if (rows.size != 1) "s" else ""} may be " +
                "another counted twice. Merge counts its plays as the one shown; Leave alone isn't asked again. Versions (acoustic, " +
                "live, remix…) are left alone unless you pick Merge.", style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(rows.size) { i ->
                    val r = rows[i]
                    val now = pick[i] ?: if (r.version) "" else r.to.first
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("${r.title} — ${r.artist}", style = T.title)
                        Text("%,d play${if (r.plays != 1) "s" else ""} as “${r.title}” → “${r.to.first}”: %,d".format(r.plays, r.to.third), style = T.sub)
                        if (r.version) Text("A version (acoustic, live, remix…): merge only if it's the same song to you", style = T.sub)
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf(r.to.first) + r.others).forEach { name ->
                                Chip(if (name == r.to.first) "Merge into $name" else name, now == name) { pick[i] = name }
                            }
                            if (now.isNotEmpty() && now != r.to.first && now !in r.others) Chip(now, true) {}
                            Chip("Choose your own…", false) { typing = i; typed = "" }
                            Chip("Leave alone", now.isEmpty()) { pick[i] = "" }
                        }
                        if (typing == i) {
                            Field(typed, "Type a song") { typed = it }
                            val q = org.omarchy.music.norm(typed)
                            if (q.isNotEmpty()) titles[org.omarchy.music.Stats.statsKey("", r.artist).second].orEmpty()
                                .filter { q in org.omarchy.music.norm(it) && it != r.title }
                                .sortedBy { if (org.omarchy.music.norm(it).startsWith(q)) 0 else 1 }.take(6).forEach { name ->
                                    Text(name, style = T.body, modifier = Modifier.fillMaxWidth().clickable { pick[i] = name; typing = null }
                                        .padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Pill(if (rows.isEmpty()) "Done" else "Apply") {
                    val more = HashMap<String, String>()
                    rows.forEachIndexed { i, r ->
                        val to = pick[i] ?: if (r.version) "" else r.to.first
                        val artist = if (to == r.to.first || to in r.others) r.to.second else r.artist
                        val key = org.omarchy.music.songPickKey(r.title, r.artist)
                        more[key] = if (to.isEmpty() || org.omarchy.music.songPickKey(to, artist) == key) "" else "$to\u0000$artist"
                    }
                    if (more.isNotEmpty()) Music.addSongPicks(more)
                    close()
                }
            }
        }
    }
}


/** Singles › Single scan…: each of your singles looked up on Deezer (else Apple Music) for the album or EP it's on; each
 *  Change (its album, album artist, track number, year and cover), another of the artist's albums and EPs, one you type
 *  (albums and EPs suggested as you type), or Leave alone (not asked again). Only the app's downloaded FLACs. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SingleScanScreen(ui: Ui) {
    val close = { ui.singleScan = false }
    var loaded by remember { mutableStateOf<Pair<List<org.omarchy.music.SingleScan.Row>, Int>?>(null) }
    var report by remember { mutableStateOf<List<String>?>(null) }
    var working by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        Music.bg({ org.omarchy.music.SingleScan.scan() }) { r, e -> loaded = r ?: (emptyList<org.omarchy.music.SingleScan.Row>() to -1).also { report = listOf("Couldn't look them up: ${e?.message}") } }
    }
    val rows = loaded?.first ?: emptyList()
    val yours = remember { runCatching { Music.mySongs() }.getOrDefault(emptyList()).filter { it.album.isNotBlank() && !org.omarchy.music.isSingle(it.album, it.title) }
        .map { it.album }.distinctBy { org.omarchy.music.norm(it) } }
    val yoursKeys = remember(yours) { yours.map { org.omarchy.music.norm(it) }.toSet() }
    fun onDevice(r: org.omarchy.music.SingleScan.Row) = org.omarchy.music.norm(r.home.album) in yoursKeys
    val pick = remember { androidx.compose.runtime.mutableStateMapOf<Int, String>() }   // the album, or "" left alone
    var typing by remember { mutableStateOf<Int?>(null) }
    var typed by remember { mutableStateOf("") }
    FullScreen("Single scan", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            val failed = loaded?.second ?: 0
            Text(when {
                report != null -> report!!.firstOrNull() ?: ""
                working -> "Moving them onto their albums…"
                loaded == null -> "Looking up your singles on Deezer (and Apple Music)…"
                rows.isEmpty() -> "Every single checked: none is on an album or EP" + (if (failed > 0) " ($failed couldn't be looked up: try again later)." else ".") +
                    " Only the songs the app downloaded can be changed here (the PC changes all of them)."
                else -> "${rows.size} single${if (rows.size != 1) "s are" else " is"} on an album or EP. Change puts it there: its album, album artist, " +
                    "track number and year, and the album's cover; Leave alone keeps it a single (not asked again); or pick another album." +
                    (if (failed > 0) " $failed couldn't be looked up." else "")
            }, style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            if (report == null && !working && rows.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Change all", quiet = true) { rows.indices.forEach { pick[it] = rows[it].home.album } }
                Pill("Leave all alone", quiet = true) { rows.indices.forEach { pick[it] = "" } }
                Pill("Only albums on this ${if (onPhone) "phone" else "PC"}", quiet = true) {
                    rows.indices.forEach { pick[it] = if (onDevice(rows[it])) rows[it].home.album else "" }
                }
            }
            if (report != null) {
                LazyColumn(Modifier.weight(1f)) { items(report!!.drop(1)) { Text(it, style = T.body, modifier = Modifier.padding(vertical = 4.dp)) } }
            } else LazyColumn(Modifier.weight(1f)) {
                items(if (working) 0 else rows.size) { i ->
                    val r = rows[i]
                    val now = pick[i] ?: r.home.album
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("${r.t.title} — ${r.t.artist}", style = T.title)
                        Text("Album: ${r.t.album.ifEmpty { "(none)" }} → ${r.home.album} (${if (r.home.kind == "ep") "EP" else "Album"}" +
                            (if (onDevice(r)) ", on this ${if (onPhone) "phone" else "PC"})" else " · ${r.home.source.removePrefix("Apple ")})"), style = T.sub)
                        if (r.home.n != null) Text("Track ${r.home.n}" + (if (r.home.date.length >= 4) " · ${r.home.date.take(4)}" else ""), style = T.sub)
                        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("Change", now == r.home.album) { pick[i] = r.home.album }
                            if (now.isNotEmpty() && now != r.home.album) Chip(now, true) {}
                            Chip("Other album…", false) { typing = i; typed = "" }
                            Chip("Leave alone", now.isEmpty()) { pick[i] = "" }
                        }
                        if (typing == i) {
                            Field(typed, "Type an album or EP") { typed = it }
                            val q = org.omarchy.music.norm(typed)
                            (r.releases + yours).distinct().filter { q.isEmpty() || q in org.omarchy.music.norm(it) }
                                .sortedBy { if (org.omarchy.music.norm(it).startsWith(q)) 0 else 1 }.take(8).forEach { name ->
                                    Text(name + if (org.omarchy.music.isEp(name)) "  · EP" else "", style = T.body,
                                        modifier = Modifier.fillMaxWidth().clickable { pick[i] = name; typing = null }.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            if (typed.isNotBlank()) Pill("Use “$typed”", quiet = true) { pick[i] = typed.trim(); typing = null }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Pill(if (rows.isEmpty() || report != null) "Done" else "Apply") {
                    if (rows.isEmpty() || report != null) { close(); return@Pill }
                    if (working) return@Pill
                    val todo = rows.mapIndexedNotNull { i, r -> (pick[i] ?: r.home.album).takeIf { it.isNotEmpty() }?.let { r to it } }
                    Music.keepSingles(rows.filterIndexed { i, _ -> (pick[i] ?: "x").isEmpty() }.map { it.uri })
                    if (todo.isEmpty()) { report = listOf("Nothing changed."); return@Pill }
                    working = true
                    Music.bg({ org.omarchy.music.SingleScan.apply(todo) }) { res, err ->
                        working = false
                        val lines = res ?: listOf("Couldn't finish: ${err?.message}")
                        val n = lines.count { "→" in it }
                        report = listOf("$n single${if (n != 1) "s" else ""} put on ${if (n != 1) "their albums" else "its album"}.") + lines
                        Music.musicVersion++; Music.savedVersion++
                    }
                }
            }
        }
    }
}
