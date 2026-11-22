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
    var shownAgain by remember { mutableStateOf(0) }
    // Never show: the pairs hidden before are left out; one hidden just now stays as a line with Undo
    val rows = remember(Music.artistPicks, org.omarchy.music.Stats.songs, shownAgain) {
        jointCreditsNow().filter { !org.omarchy.music.ScanIgnore.has(org.omarchy.music.ScanIgnoreRules.JOINT, org.omarchy.music.pairKey(it.first)) } }
    val justHidden = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var changed by remember { mutableStateOf(false) }
    val close = {
        ui.jointArtists = false
        if (changed) { org.omarchy.music.Stats.refresh(force = true); Music.toast("Counting Most played again with your picks") }
    }
    FullScreen("Joint artists", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text(if (rows.isEmpty()) "Every joint credit's plays already go to its main artist: nothing to pick."
                else "${rows.size} pairs whose main artist isn't clear (both or neither have songs of their own here). Pick whose plays " +
                    "they are in Most played, Artists and the time tabs. Never show leaves a pair as it is and out of this list.", style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            StatsHiddenLine(org.omarchy.music.ScanIgnoreRules.JOINT) { justHidden.clear(); shownAgain++ }
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.first }) { (credit, parts, picked) ->
                    val hideKey = org.omarchy.music.pairKey(credit)
                    if (hideKey in justHidden) HiddenUndo(credit) { org.omarchy.music.ScanIgnore.remove(org.omarchy.music.ScanIgnoreRules.JOINT, hideKey); justHidden -= hideKey }
                    else Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
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
                                DropdownMenuItem(text = { Text("Never show", style = T.body) },
                                    onClick = { open = false; org.omarchy.music.ScanIgnore.add(org.omarchy.music.ScanIgnoreRules.JOINT, hideKey); justHidden += hideKey })
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) { Pill("Done") { close() } }
        }
    }
}

/** This is a song of mine…: a song your history has under a name your file doesn't have (an unreleased song put up
 *  under another title and artist). You pick your file once; the song is then shown and played as it and its plays
 *  count for its artist. Your songs of the same length come first: the likely ones. */
@Composable
fun MySongDialog(ui: Ui, t: org.omarchy.music.Track) {
    var typed by remember(t) { mutableStateOf("") }
    val mine = remember { runCatching { Music.mySongs() }.getOrDefault(emptyList()) }
    val likely = remember(t) { runCatching { org.omarchy.music.YourCopies.sameLengthAs(t) }.getOrDefault(emptyList()) }
    val q = org.omarchy.music.norm(typed)
    val shown = remember(q) {
        if (q.isEmpty()) likely.take(40)
        else mine.filter { q in org.omarchy.music.norm("${it.title} ${it.artist}") }
            .sortedBy { !org.omarchy.music.norm(it.title).startsWith(q) }.take(40)
    }
    val close = { ui.mySongFor = null }
    androidx.compose.material3.AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text("Which song of yours is this?", style = T.head) },
        text = {
            Column {
                Text("“${t.title}” by ${t.artist} will be shown and played as the song you pick, and its plays will count for " +
                    "that song's artist in Most played, Artists and Wrapped. " +
                    (if (likely.isNotEmpty()) "Listed first: your songs that run just as long." else "Type its name to find it."), style = T.sub)
                androidx.compose.material3.OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text("Type a song of yours") },
                    trailingIcon = { MicButton { said -> typed = said } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).typing())
                LazyColumn(Modifier.padding(top = 2.dp).fillMaxWidth().heightIn(max = 300.dp)) {
                    items(shown, key = { it.id }) { s ->
                        Column(Modifier.fillMaxWidth().clickable {
                            if (org.omarchy.music.YourCopies.link(t, s)) { close(); Music.toast("“${t.title}” is now your “${s.title}”") }
                            else Music.toast("“${s.title}” has no artist in its tags: give it one in the Tagger first, so its plays have someone to count for")
                        }.padding(vertical = 7.dp, horizontal = 4.dp)) {
                            Text(s.title, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.artist} · ${org.omarchy.music.ClipRules.clock(s.duration)}" + if (s.album.isNotBlank()) " · ${s.album}" else "",
                                style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(close) { Text("Cancel", color = C.muted) } })
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
                    trailingIcon = { MicButton { said -> to = said } },
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
    var shownAgain by remember { mutableStateOf(0) }
    fun hideKey(from: String, to: String) = org.omarchy.music.StatPickRules.apartKey(from, to)
    // Never show: the pairs hidden before are left out; one hidden just now stays as a line with Undo, and isn't applied
    val rows = remember(shownAgain) { similarArtistsNow().filter { (n, _) -> !org.omarchy.music.ScanIgnore.has(org.omarchy.music.ScanIgnoreRules.SIMILAR, hideKey(n.first, n.second)) } }
    val pick = remember { androidx.compose.runtime.mutableStateMapOf<Int, String>() }   // "merge", "other", "keep"
    val justHidden = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    val close = { ui.similarArtists = false }
    FullScreen("Similar artists", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text(if (rows.isEmpty()) "No artists that look like another of yours." else "${rows.size} artists look like another of yours. " +
                "Merge puts the first's songs and plays (each song's too) under the second; Keep apart isn't asked about again. " +
                "Never show changes nothing and leaves it out of this list.",
                style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            StatsHiddenLine(org.omarchy.music.ScanIgnoreRules.SIMILAR) { justHidden.clear(); pick.clear(); shownAgain++ }
            LazyColumn(Modifier.weight(1f)) {
                items(rows.size) { i ->
                    val (names, info) = rows[i]
                    val (from, to) = names
                    if (i in justHidden) { HiddenUndo("$from → $to") { org.omarchy.music.ScanIgnore.remove(org.omarchy.music.ScanIgnoreRules.SIMILAR, hideKey(from, to)); justHidden -= i }; return@items }
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
                            Chip("Never show", false) { org.omarchy.music.ScanIgnore.add(org.omarchy.music.ScanIgnoreRules.SIMILAR, hideKey(from, to)); justHidden += i }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Pill(if (rows.isEmpty()) "Done" else "Apply") {
                    rows.forEachIndexed { i, (names, info) ->
                        val (from, to) = names
                        if (i in justHidden) return@forEachIndexed   // Never show: left as it is
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

/** Settings › Hidden from stats…: everything you hid from the stats lists; tap one to show it again. */
@Composable
fun StatHidesScreen(ui: Ui) {
    val H = org.omarchy.music.StatHides
    val close = { ui.statHides = false }
    H.version
    val rows = H.all()
    FullScreen("Hidden from stats", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text(if (rows.isEmpty()) "Nothing is hidden. In a stats list, $LONG_PRESS a song, an album or an artist and pick Hide from stats."
                else "${org.omarchy.music.StatHideRules.count(rows.size)}. They aren't shown in the stats lists, but their plays still count. Show again puts one back.",
                style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.first }) { (key, name) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(org.omarchy.music.StatHideRules.kind(key), style = T.sub)
                        }
                        Pill("Show again", quiet = true) { H.show(key) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (rows.isNotEmpty()) Pill("Show all again", quiet = true) { H.showAll() }
                Pill("Done") { close() }
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
    var shownAgain by remember { mutableStateOf(0) }
    fun hideKey(r: org.omarchy.music.DuplicateSong) = org.omarchy.music.songPickKey(r.title, r.artist)
    // Never show: the songs hidden before are left out; one hidden just now stays as a line with Undo, and isn't applied
    val rows = remember(shownAgain) { org.omarchy.music.duplicateSongsPlan(org.omarchy.music.Stats.songs, Music.songPicks)
        .filter { !org.omarchy.music.ScanIgnore.has(org.omarchy.music.ScanIgnoreRules.DUPE_SONGS, hideKey(it)) } }
    val justHidden = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    val titles = remember { org.omarchy.music.Stats.songs.groupBy { org.omarchy.music.Stats.statsKey("", it.artist).second }
        .mapValues { (_, v) -> v.map { it.title } } }
    val pick = remember { androidx.compose.runtime.mutableStateMapOf<Int, String>() }   // a title, or "" left alone
    var typing by remember { mutableStateOf<Int?>(null) }
    var typed by remember { mutableStateOf("") }
    FullScreen("Duplicates in Most played", close) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).navigationBarsPadding()) {
            Text(if (rows.isEmpty()) "No duplicates: every song counted once." else "${rows.size} song${if (rows.size != 1) "s" else ""} may be " +
                "another counted twice. Merge counts its plays as the one shown; Leave alone isn't asked again. Versions (acoustic, " +
                "live, remix…) are left alone unless you pick Merge. Never show changes nothing and leaves it out of this list.",
                style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
            StatsHiddenLine(org.omarchy.music.ScanIgnoreRules.DUPE_SONGS) { justHidden.clear(); pick.clear(); shownAgain++ }
            LazyColumn(Modifier.weight(1f)) {
                items(rows.size) { i ->
                    val r = rows[i]
                    if (i in justHidden) { HiddenUndo("${r.title} — ${r.artist}") { org.omarchy.music.ScanIgnore.remove(org.omarchy.music.ScanIgnoreRules.DUPE_SONGS, hideKey(r)); justHidden -= i }; return@items }
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
                            Chip("Never show", false) { org.omarchy.music.ScanIgnore.add(org.omarchy.music.ScanIgnoreRules.DUPE_SONGS, hideKey(r)); justHidden += i; if (typing == i) typing = null }
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
                        if (i in justHidden) return@forEachIndexed   // Never show: left as it is
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


/** The stats screens' Never show: "N hidden from this list · Show them again" (they come back, to pick for). */
@Composable
private fun StatsHiddenLine(scan: String, shown: () -> Unit) {
    org.omarchy.music.ScanIgnore.version
    val n = org.omarchy.music.ScanIgnore.count(scan)
    if (n == 0) return
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("%,d hidden from this list (Never show)".format(n), style = T.sub, modifier = Modifier.weight(1f))
        Pill("Show them again", quiet = true) { org.omarchy.music.ScanIgnore.clear(scan); shown() }
    }
}
