package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omarchy.music.Dj
import org.omarchy.music.Album
import org.omarchy.music.Music
import org.omarchy.music.Pin
import org.omarchy.music.Track

// Albums, Genres and Folders (from the ☰ menu), a named list of songs, and your queues.

/** Albums: your songs by album: covers in a grid (or a list, or a compact grid). */
@Composable
fun AlbumsTab(ui: Ui, kind: String = "albums") {
    // EPs and Singles: by the album tag ("Title - EP", "Title - Single"); songs with no album are singles; the rest are albums
    val eps = kind == "eps"; val singles = kind == "singles"
    val all = remember(Music.musicVersion, Music.savedVersion, kind) { runCatching { Music.albums() }.getOrDefault(emptyList())
        .flatMap { (al, songs) ->
            if (songs.first().album.isEmpty() && songs.none { org.omarchy.music.folderKind(Music.folderOf(it)) != null })   // no album at all: each song is its own single
                songs.map { t -> Album("single:" + (t.file ?: t.id), t.title, t.artist.split(",")[0].trim(), count = "1") to listOf(t) }
            else listOf(al to songs)
        }
        .filter { (al, songs) ->
            // Each release is in exactly one of the three tabs: an EP is only in EPs; a single (named one, or an album
            // of one song) only in Singles; everything else only in Albums
            // (by the album tag: "… - EP" an EP, "… - Single" a single, else an album however few of its songs you have)
            val k = Music.releaseKind(al.title, songs, al.id.startsWith("single:"))
            when (kind) {
                "eps" -> k == "ep"
                "singles" -> k == "single"
                else -> k == "album"
            }
        }.sortedBy { org.omarchy.music.norm(it.first.title) } }
    if (all.isEmpty()) { Empty(if (eps) "No EPs yet" else if (singles) "No singles yet" else "No albums yet",
        if (eps) "Your EPs (\"- EP\" at the end of their album name) show up here." else if (singles) "Your singles (\"- Single\" at the end of their album name, or no album) show up here." else "Your songs, by album, show up here."); return }
    // Albums: the big ones (more than six songs here) or the ones you have only a few songs of; while you search
    // the page, all of them (whichever group they're in), so a search finds any album
    val searching = !ui.filter.isNullOrBlank()
    val split = kind == "albums" && !searching
    val bigAlbums = kind == "albums" && ui.albumsLow.not()
    val sized = if (!split) all else all.filter { (_, songs) -> if (ui.albumsLow) songs.size <= 6 else songs.size > 6 }
    val sorts = if (singles) org.omarchy.music.GROUP_SORTS - setOf("songs", "songs-least") else org.omarchy.music.GROUP_SORTS
    val sort = sortNow(kind, sorts)
    val fitting = sized.filter { fits(ui, it.first.title, it.first.sub) }
    val albums = rememberSorted(fitting, sort) { d ->
        org.omarchy.music.sortGroups(fitting, sort, d) { (al, songs) ->
            org.omarchy.music.GroupFacts(al.title, al.sub, songs) } }
    fun pin(al: Album) = Pin("album", al.id, al.title, al.sub, source = "local")
    val picking = ui.pickingIn(kind)
    val picked = ui.albumsPicked
    fun menu(al: Album, songs: List<Track>) {
        if (picking) { picked[al.id] = songs; ui.optionsFor(picked.values.flatten()); return }   // picking: Options for them
        ui.choices = al.title to listOf(pinChoice(pin(al)), "Options…" to { ui.optionsFor(songs) },
            "Select" to { ui.startPick(kind, al.id, songs) })
    }
    fun tap(al: Album, songs: List<Track>) {
        if (picking) { if (al.id in picked) picked.remove(al.id) else picked[al.id] = songs }
        else ui.pages += Ui.Page.AlbumPage(al, "local", songs)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (picking) PickBar(ui, if (eps) "EPs" else if (singles) "singles" else "albums")   // Select: tap albums, then Options for all their songs
            else {
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Collage of your top ${if (eps) "EPs" else if (singles) "singles" else "albums"}…", false) { ui.collageFor = "$kind-all" }
                    Chip("Select", false) { ui.startPick(kind) }
                    SortMenu(kind, sorts)
                }
                if (split) Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Albums (${all.count { it.second.size > 6 }})", !ui.albumsLow) { ui.albumsLow = false }
                    Chip("Low song count albums (${all.count { it.second.size <= 6 }})", ui.albumsLow) { ui.albumsLow = true }
                }
            }
            Section("${albums.size} ${if (eps) "EP" else if (singles) "SINGLE" else if (split && !bigAlbums) "LOW SONG COUNT ALBUM" else "ALBUM"}${if (albums.size != 1) "S" else ""}")
        }
        laidOutItems(albums, ui, key = { "al-" + albums[it].first.id }, row = { i ->
            val (al, songs) = albums[i]
            PlaylistRow(Album(al.id, al.title, al.sub, "", songs.size.toString()), artModel(songs.first()),
                onLongClick = { menu(al, songs) }, checked = if (picking) al.id in picked else null) { tap(al, songs) }
        }) { i, mod, compact ->
            val (al, songs) = albums[i]
            Tile(artModel(songs.first(), tilePx(compact)), al.title, al.sub, mod, compact, ico = Ico.DISC, lit = picking && al.id in picked,
                onLongClick = { menu(al, songs) }) { tap(al, songs) }
        }
    }
}

/** Select on a list of groups (albums, genres, folders, artists, playlists): the list's line on what to do; the bar
 *  itself (Options for all their songs, the list's extra options, Clear, Cancel) stays at the bottom (PickDock),
 *  so it's there wherever you've scrolled to. */
@Composable
fun PickBar(ui: Ui, noun: String, extra: List<Pair<String, () -> Unit>> = emptyList()) {
    androidx.compose.runtime.SideEffect { ui.pickNoun = noun; ui.pickExtra = extra }
    val picked = ui.albumsPicked
    Section(if (picked.isEmpty()) "PICK ${noun.uppercase()}: A TAP TICKS ONE" else "${picked.size} PICKED")
}

/** The bar at the bottom while you pick albums, artists, playlists…: how many, Options, the list's own options, Clear, Cancel. */
@Composable
fun PickDock(ui: Ui) {
    val picked = ui.albumsPicked
    val n = picked.values.sumOf { it.size }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).shadow(10.dp, R.big).clip(R.big).background(C.light)) {
        Text(if (picked.isEmpty()) "Tap ${ui.pickNoun} to pick them" else "${picked.size} ${ui.pickNoun} picked · %,d song%s".format(n, if (n != 1) "s" else ""),
            style = T.sub, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            BarButton(Ico.MORE, "Options") { if (picked.isNotEmpty()) ui.optionsFor(picked.values.flatten()) }
            if (picked.isNotEmpty()) for ((label, run) in ui.pickExtra) Chip(label, false) { run() }
            if (picked.isNotEmpty()) BarButton(Ico.SELECT, "Clear") { picked.clear() }
            BarButton(Ico.CLOSE, "Cancel") { picked.clear(); ui.pickingAlbums = false }
        }
    }
}

/** A row in Genres and Folders: an icon, a name, how many songs. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(ico: Ico, name: String, sub: String, count: Int, onLongClick: () -> Unit, picked: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(R.card).background(if (picked) C.accent.copy(alpha = 0.22f) else C.light.copy(alpha = 0.6f))
        .pressable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(R.card).background(C.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(ico, C.accent, 22.dp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("%,d".format(count), style = T.sub)
    }
}

/** Genres or Folders: a group of your songs each, as rows or covers. */
private fun androidx.compose.foundation.lazy.LazyListScope.groups(ui: Ui, kind: String, list: List<Pair<String, List<Track>>>,
                                                                  name: (String) -> String, sortTab: String = kind + "s", pinnable: Boolean = true,
                                                                  head: @Composable () -> Unit = {}, sub: (String, List<Track>) -> String) {
    val ico = if (kind == "genre") Ico.GENRE else Ico.FOLDER
    val picking = ui.pickingIn(kind)
    val picked = ui.albumsPicked
    fun open(id: String, songs: List<Track>) {
        if (picking) { if (id in picked) picked.remove(id) else picked[id] = songs; return }
        ui.pages += Ui.Page.Songs(name(id), songs, kind, id)
    }
    fun menu(id: String, songs: List<Track>) {
        if (picking) { picked[id] = songs; ui.optionsFor(picked.values.flatten()); return }
        ui.choices = name(id) to listOfNotNull(if (pinnable) pinChoice(Pin(kind, id, name(id))) else null, "Options…" to { ui.optionsFor(songs) }, "Select" to { ui.startPick(kind, id, songs) })
    }
    item { if (picking) PickBar(ui, if (kind == "genre") "genres" else "folders") else Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) { head(); Chip("Select", false) { ui.startPick(kind) }; SortMenu(sortTab, GROUPS_SORTS) } }
    laidOutItems(list, ui, key = { "$kind-" + list[it].first }, row = { i ->
        val (id, songs) = list[i]
        GroupRow(ico, name(id), sub(id, songs), songs.size, onLongClick = { menu(id, songs) }, picked = picking && id in picked) { open(id, songs) }
    }) { i, mod, compact ->
        val (id, songs) = list[i]
        // Folders show as folders in the grid too (the folder icon, not a song's cover), on the phone and the PC.
        Tile(if (kind == "folder") null else artModel(songs.first(), tilePx(compact)), name(id), "%,d songs".format(songs.size), mod, compact, ico = ico, lit = picking && id in picked,
            onLongClick = { menu(id, songs) }) { open(id, songs) }
    }
}

@Composable
fun GenresTab(ui: Ui) {
    val all = remember(Music.musicVersion, Music.savedVersion) { runCatching { Music.genres() }.getOrDefault(emptyList()) }
    if (all.isEmpty()) { Empty("No genres yet", "Your songs, by the genre their files give, show up here."); return }
    val genres = sortedGroups("genres", all.filter { fits(ui, it.first) }) { it }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Section("${genres.size} GENRES") }
        groups(ui, "genre", genres, { it }) { _, songs -> songs.take(3).joinToString { it.artist.split(",")[0] } }
    }
}

/** Each mood's colour, for its tile and row. */
internal fun moodColor(mood: String): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(when (mood) {
    "Happy" -> 0xFFF6C343; "Energetic" -> 0xFFFF7A30; "Party" -> 0xFFD94BF0; "Hype" -> 0xFFFF3D5A
    "Chill" -> 0xFF26C6DA; "Calm" -> 0xFF66BB6A; "Dreamy" -> 0xFF9575CD; "Focus" -> 0xFF42A5F5
    "Romantic" -> 0xFFF06292; "Sad" -> 0xFF5C7AA8; "Dark" -> 0xFF5E5470; "Intense" -> 0xFFD32F2F
    else -> 0xFF8A8A8A
})

/** A mood's square: its colour, fading, with the mood icon. */
@Composable
internal fun MoodSwatch(mood: String, modifier: Modifier, iconSize: androidx.compose.ui.unit.Dp) {
    val c = moodColor(mood)
    Box(modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c, c.copy(alpha = 0.45f)))), contentAlignment = Alignment.Center) {
        Icon(Ico.MOOD, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f), iconSize)
    }
}

/** "about 1 h 20 min left": a scan's time left, roughly. */
private fun moodsLeft(secs: Long): String = when {
    secs < 60 -> "less than a minute left"
    secs < 3600 -> "about ${(secs + 30) / 60} min left"
    else -> "about ${secs / 3600} h ${(secs % 3600 + 30) / 60 % 60} min left".replace(" 0 min", "")
}

/** The Moods tab's scans (Scan moods: a minute of each song; Full scan: whole songs), Stop, how far they've got and
 *  about how long is left, and Write moods into the files (asking first when it's many files). */
@Composable
private fun MoodsReading(ui: Ui, reading: Pair<Int, Int>?, counts: org.omarchy.music.Moods.Counts) {
    val moods = org.omarchy.music.Moods
    val v = moods.version
    // After a scan with many files to write: asked here (on this tab), not written behind your back.
    val ask = moods.askWrite
    androidx.compose.runtime.LaunchedEffect(ask) {
        if (ask > 0) {
            moods.askWrite = 0
            ui.confirm = "Write the moods into %,d files? Each file's MOOD tag is set to its mood (other tags stay as they are).".format(ask) to { moods.writeFiles() }
        }
    }
    val looking = moods.looking
    // Moods already in the files (written on the PC, say): the scans only offer the songs still without one.
    val left = counts.total - counts.done
    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (reading != null) {
            Chip("Stop", false) { moods.stop() }
            Text((if (moods.readingFull) "Full scan… " else "Scanning moods… ") + "%,d of %,d".format(reading.first, reading.second), style = T.sub)
        } else if (looking == null) {
            if (left > 0) Chip(if (counts.inFiles > 0) "Scan moods (%,d song%s, 1 minute each)".format(left, if (left != 1) "s" else "")
                else "Scan moods (1 minute per song)", false) { moods.find() }
            if (counts.full < counts.total) Chip(if (counts.full == 0 || counts.full == counts.inFiles) "Full scan (whole songs%s)".format(
                if (counts.inFiles > 0) ", %,d".format(counts.total - counts.full) else "") else "Resume full scan (%,d left)".format(counts.total - counts.full), false) { moods.findFull() }
        }
    }
    Column(Modifier.padding(horizontal = 14.dp)) {
        if (looking != null) Text("Looking at your files for moods already in them… %,d of %,d".format(looking.first, looking.second), style = T.sub)
        if (reading != null) {
            if (moods.readingNow.isNotEmpty()) Text(moods.readingNow, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(moods.readingLeft?.let { moodsLeft(it) } ?: "Working out the time left…", style = T.sub)
        } else if (counts.total > 0 && looking == null) {
            when {
                counts.inFiles >= counts.total -> Text("All %,d songs already have their moods in their files — no scan needed".format(counts.total),
                    style = T.sub.copy(color = C.accent))
                counts.inFiles > 0 && counts.inFiles * 2 >= counts.total -> Text(("%,d of %,d songs have their moods in their files; Scan moods " +
                    "only listens to the other %,d").format(counts.inFiles, counts.total, left.coerceAtLeast(0)), style = T.sub.copy(color = C.accent))
                else -> {}
            }
            Text("Moods read for %,d of %,d songs (%,d heard whole%s)".format(counts.done, counts.total, counts.full,
                if (counts.inFiles > 0) ", %,d from the files".format(counts.inFiles) else ""), style = T.sub)
        }
    }
    MoodsWords(ui, counts)
    var on by remember(v) { androidx.compose.runtime.mutableStateOf(moods.writeOn()) }
    androidx.compose.runtime.LaunchedEffect(on, reading == null) { if (on && reading == null) moods.recount() }
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(on, { on = it; moods.setWriteOn(it) })
        Text("Write moods into the files", style = T.body)
    }
    if (on) {
        Text(if (org.omarchy.music.platformName == "android") "The songs the app downloaded get a MOOD tag; Android keeps your other files to " +
            "themselves, so their moods stay in the app (and in backups)." else "Your songs' files get a MOOD tag, so other apps and " +
            "the files' next home keep their moods.", style = T.sub, modifier = Modifier.padding(horizontal = 14.dp))
        // A heads-up: the first time takes long, but it's only ever done once
        Text("Please wait until the moods are done writing before you close the app. The scans and the writing take a " +
            "long time the first time, but once the moods are written into your files you never need to do it again. " +
            "When the writing is done, a message pops up in white at the bottom of the screen.",
            style = T.sub.copy(color = if (moods.writing) C.accent else C.fg),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        val n = moods.toWrite
        val wp = moods.writeProgress
        // How far writing has got: files that have their mood, and files still waiting for theirs
        Text(if (moods.writing && wp.second > 0) "Writing %,d of %,d files…".format(wp.first, wp.second)
            else "%,d songs have their mood in the file · %,d still to write".format(counts.inFiles, n),
            style = T.sub, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        if (n > 0 && reading == null) Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
            Chip(if (moods.writing) "Writing…" else "Write into %,d file%s now".format(n, if (n != 1) "s" else ""), false) {
                if (moods.writing) return@Chip
                if (n > org.omarchy.music.Moods.ASK_OVER) { ui.confirm = "Write the moods into %,d files?".format(n) to { moods.writeFiles() } }
                else { moods.writeFiles() }
            }
        }
    }
}

/** The Moods tab's words: Read moods from lyrics (with Stop and how far it's got), Find lyrics (the lyrics scanner), and
 *  how many songs have had their words read and have lyrics. */
@Composable
private fun MoodsWords(ui: Ui, counts: org.omarchy.music.Moods.Counts) {
    val moods = org.omarchy.music.Moods
    val lyrics = org.omarchy.music.LyricsScan
    val words = moods.wordsReading
    val found = lyrics.progress
    androidx.compose.runtime.LaunchedEffect(lyrics.version) { lyrics.recount() }
    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (words != null) {
            Chip("Stop", false) { moods.stopWords() }
            Text("Reading the words… " + if (words.second > 0) "%,d of %,d".format(words.first, words.second) else "", style = T.sub)
        } else Chip("Read moods from lyrics" + if (moods.aiOn()) " (AI)" else "", false) { moods.readWords() }
        if (found != null) {
            Chip("Stop", false) { lyrics.stop() }
            Text("Finding lyrics… " + if (found.second > 0) "%,d of %,d".format(found.first, found.second) else "", style = T.sub)
        } else Chip("Find lyrics (into the files)…", false) { ui.tool = "lyrics" }
    }
    Column(Modifier.padding(horizontal = 14.dp)) {
        if (words != null && moods.wordsNow.isNotEmpty()) Text(moods.wordsNow, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (found != null && lyrics.nowSong.isNotEmpty()) Text(lyrics.nowSong, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (counts.total > 0) Text(("Words read for %,d of %,d songs (%,d in their files)" +
            (if (counts.noWords > 0) " · %,d with no lyrics found" else "")).format(counts.words, counts.total, counts.wordsInFiles, counts.noWords) +
            (if (moods.aiOn()) " · AI reading on" else ""), style = T.sub)
        lyrics.counts?.let { c ->
            Text("Lyrics in files: %,d · kept in the app: %,d · still to find: %,d".format(c.inFiles, c.inApp, c.toFind), style = T.sub)
        }
    }
}

/** Moods: your songs by mood (Chill, Happy, Sad…), found from their sound, genres and tags (see Moods). A tap opens a
 *  mood's songs; Scan moods and Full scan read the songs not read yet, showing how far they've got, with Stop. */
@Composable
fun MoodsTab(ui: Ui) {
    val reading = org.omarchy.music.Moods.reading
    val v = org.omarchy.music.Moods.version
    val all = remember(Music.musicVersion, Music.savedVersion, v) {
        runCatching { org.omarchy.music.Moods.ALL.map { it to org.omarchy.music.Moods.songs(it) }.filter { it.second.isNotEmpty() } }.getOrDefault(emptyList())
    }
    val counts = remember(Music.musicVersion, v, reading == null, org.omarchy.music.Moods.looking == null, org.omarchy.music.Moods.wordsReading == null) {
        runCatching { org.omarchy.music.Moods.counts() }.getOrDefault(org.omarchy.music.Moods.Counts(0, 0, 0))
    }
    // Opening the tab: a quick look at the files for moods already in them (no scan needed for those)
    androidx.compose.runtime.LaunchedEffect(Music.musicVersion) { org.omarchy.music.Moods.lookFiles() }
    if (all.isEmpty()) {
        Column {
            MoodsReading(ui, reading, counts)
            Empty("No moods yet", "Each of your songs gets a mood (Chill, Happy, Sad, Party…) from how it sounds (its tempo, energy, " +
                "brightness and key), its genre, its words (Read moods from lyrics), and Last.fm's tags when you have a Last.fm key. Scan moods hears a minute from the " +
                "middle of each song; Full scan hears every song from start to end (slower, surer). A mood in a file's MOOD tag, or " +
                "one you pick in the Tagger, is used as it is. Scan moods also runs by itself after your music is scanned" +
                (if (org.omarchy.music.platformName == "android") ", while the phone is charging." else "."))
        }
        return
    }
    val sortTab = "moods"
    val moods = sortedGroups(sortTab, all.filter { fits(ui, it.first) }) { it }
    val picking = ui.pickingIn("mood")
    val picked = ui.albumsPicked
    fun open(mood: String, songs: List<Track>) {
        if (picking) { if (mood in picked) picked.remove(mood) else picked[mood] = songs; return }
        ui.pages += Ui.Page.Songs(mood, songs, "mood", mood)
    }
    fun menu(mood: String, songs: List<Track>) {
        if (picking) { picked[mood] = songs; ui.optionsFor(picked.values.flatten()); return }
        ui.choices = mood to listOf("Options…" to { ui.optionsFor(songs) }, "Select" to { ui.startPick("mood", mood, songs) })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            MoodsReading(ui, reading, counts)
            if (picking) PickBar(ui, "moods")
            else Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip("Select", false) { ui.startPick("mood") }; SortMenu(sortTab, GROUPS_SORTS) }
            // Where to put a wrong mood right
            if (!picking) Text("A mood looks wrong? Change it in the Tagger (Tagger › Tags › Mood): long-press a song " +
                "(right-click on the PC) › Tagger…", style = T.sub, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            if (!picking) Text("Note: the moods won't always be spot on. From lots of testing, scanning both the sound (Full scan) and " +
                "the lyrics (Read moods from lyrics) gives the best results: about 90% of songs land in the right moods. There " +
                "will be a few outliers, and those can be changed in the Tagger.", style = T.sub,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            Section("${moods.size} MOOD${if (moods.size != 1) "S" else ""}")
        }
        laidOutItems(moods, ui, key = { "mood-" + moods[it].first }, row = { i ->
            val (mood, songs) = moods[i]
            val lit = picking && mood in picked
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(R.card)
                .background(if (lit) C.accent.copy(alpha = 0.22f) else C.light.copy(alpha = 0.6f))
                .pressable(onClick = { open(mood, songs) }, onLongClick = { menu(mood, songs) }).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MoodSwatch(mood, Modifier.size(42.dp).clip(R.card), 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(mood, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Six of the mood's songs (different artists first), laid along the row toward the count
                val six = remember(songs) { moodSix(songs) }
                Column(Modifier.weight(2.2f).padding(end = 12.dp)) {
                    for (line in six.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (t in line) Text(t.title, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        repeat(3 - line.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("%,d".format(songs.size), style = T.sub)
            }
        }) { i, mod, compact ->
            val (mood, songs) = moods[i]
            Tile(null, mood, "%,d songs".format(songs.size), mod, compact, ico = Ico.MOOD, lit = picking && mood in picked,
                onLongClick = { menu(mood, songs) }, over = { MoodSwatch(mood, Modifier.fillMaxSize(), if (compact) 26.dp else 40.dp) }) { open(mood, songs) }
        }
    }
}

/** Six songs for a mood's row: the surest first, one per artist before any artist comes twice. */
private fun moodSix(songs: List<Track>): List<Track> {
    val seen = HashSet<String>()
    val first = songs.filter { seen.add(it.artist.split(",")[0].trim().lowercase()) }.take(6)
    return if (first.size >= 6) first else (first + songs.filter { it !in first }).take(6)
}

/** Artists › Folders: your artist folders (Artists/<Artist>), each with every song under it, as folders. */
@Composable
fun ArtistFoldersView(ui: Ui, viewMenu: @Composable () -> Unit) {
    val all = remember(Music.musicVersion, Music.savedVersion) { runCatching {
        Music.mySongs().groupBy { org.omarchy.music.artistFolder(Music.folderOf(it)) }.filterKeys { it != null }
            .map { (f, songs) -> f!! to songs }.sortedBy { org.omarchy.music.norm(it.first.substringAfterLast('/')) }
    }.getOrDefault(emptyList()) }
    if (all.isEmpty()) {
        Column { Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) { viewMenu() }
            Empty("No artist folders", "Songs kept in an Artists folder (Artists / the artist / the album) show here, a folder per artist.") }
        return
    }
    val name = { f: String -> f.substringAfterLast('/') }
    val folders = sortedGroups("artist-folders", all.filter { fits(ui, name(it.first)) }, name)
    LazyColumn(Modifier.fillMaxSize()) {
        item { Section("${folders.size} ARTIST FOLDERS") }
        groups(ui, "folder", folders, name, sortTab = "artist-folders", pinnable = false, head = viewMenu) { _, songs -> "%,d songs".format(songs.size) }
    }
}

/** Genres' and Folders' sorts: as they come (Default), or any of these. */
private val GROUPS_SORTS = listOf("order") + (org.omarchy.music.GROUP_SORTS - setOf("artist", "artist-desc"))

/** Genres or Folders, as their Sort menu says. */
@Composable
private fun sortedGroups(tab: String, list: List<Pair<String, List<Track>>>, name: (String) -> String): List<Pair<String, List<Track>>> {
    val sort = sortNow(tab, GROUPS_SORTS)
    return rememberSorted(list, sort) { d -> org.omarchy.music.sortGroups(list, sort, d) { (id, songs) -> org.omarchy.music.GroupFacts(name(id), songs = songs) } }
}

@Composable
fun FoldersTab(ui: Ui) {
    val all = remember(Music.musicVersion, Music.savedVersion) { runCatching { Music.folderSongs() }.getOrDefault(emptyList()) }
    if (all.isEmpty()) { Empty("No folders yet", "The folders your songs are in show up here."); return }
    val folders = sortedGroups("folders", all.filter { fits(ui, it.first) }) { f -> f.trimEnd('/').substringAfterLast('/').ifEmpty { f } }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Section("${folders.size} FOLDERS") }
        groups(ui, "folder", folders, { f -> f.trimEnd('/').substringAfterLast('/').ifEmpty { f } }) { f, _ -> fullFolderPath(f.trimEnd('/')) }
    }
}

/** A genre's or a folder's songs. */
@Composable
fun SongsPage(ui: Ui, page: Ui.Page.Songs) {
    val sorts = org.omarchy.music.songSorts(true)
    val tab = "page-" + page.kind.ifEmpty { "songs" }
    val sort = sortNow(tab, sorts)
    // Most played › Moods: each song carries its plays in the period (most played first, as it came), shown on its
    // row; sorting by plays goes by those, not by all-time plays.
    val periodPlays = page.kind == "mood-plays"
    val songs = rememberSorted(page.tracks, sort) { d ->
        org.omarchy.music.sortTracks(page.tracks, sort, if (periodPlays) org.omarchy.music.SortData(plays = { it.plays }, last = d.last) else d)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListActions(songs, onDj = { ui.djFrom(Dj.From("songs", name = page.title, songs = page.tracks)) },
                pin = if (page.id.isNotEmpty() && page.kind in setOf("genre", "folder")) Pin(page.kind, page.id, page.title) else null)
            Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
                SortMenu(tab, sorts, names = if (periodPlays) mapOf("order" to "Most played") else emptyMap())
            }
        }
        if (periodPlays) tracks(songs, ui, page.kind) { t -> "%,d play%s".format(t.plays, if (t.plays != 1) "s" else "") }
        else tracks(songs, ui, page.kind)
    }
}

/** Above Up next: which queue this is, and the way to your others. */
@Composable
fun QueueHeader(ui: Ui) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(R.card).background(C.light.copy(alpha = 0.7f))
        .clickable { ui.showQueues = true }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ico.QUEUE, C.accent, 20.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(Music.queueName.ifEmpty { "Queue" }, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (Music.queues.isEmpty()) "Your only queue" else "${Music.queues.size + 1} queues · $TAP to switch",
                style = T.sub)
        }
        if (org.omarchy.music.Prefs.on(org.omarchy.music.Prefs.MONO) || org.omarchy.music.AutoMono.active)
            Text("MONO", style = T.tab.copy(color = MONO_RED), modifier = Modifier.padding(end = 8.dp).clip(R.pill)
                .background(MONO_RED.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp))
        Icon(Ico.DOWN, C.muted, 16.dp)
    }
}

/** Your queues: go back to one where you left it, rename them, close them. */
@Composable
fun QueuesDialog(ui: Ui) {
    AlertDialog(onDismissRequest = { ui.showQueues = false }, containerColor = C.light,
        title = { Text("Queues", style = T.head) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    Text("A separate queue for each folder, album, artist and playlist you play from", style = T.sub,
                        modifier = Modifier.weight(1f))
                    Switch(Music.multiQueue, { Music.setMultiQueue(it) }, colors = SwitchDefaults.colors(
                        checkedThumbColor = C.light, checkedTrackColor = C.accent))
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item { QueueLine(ui, Music.queueId, Music.queueName.ifEmpty { "Queue" }, Music.queue, Music.index, true) }
                    items(Music.queues.size) { i ->
                        val q = Music.queues[i]
                        QueueLine(ui, q.id, q.name, q.tracks, q.index, false)
                    }
                }
            }
        },
        confirmButton = { TextButton({ ui.showQueues = false }) { Text("Done", color = C.accent) } },
        dismissButton = {
            if (Music.queues.isNotEmpty()) TextButton({ ui.confirm = "Delete all your other queues? Only the one playing stays." to { Music.removeOtherQueues() } }) { Text("Remove all other queues", color = C.red) }
        })
}

@Composable
private fun QueueLine(ui: Ui, id: String, name: String, tracks: List<Track>, index: Int, current: Boolean) {
    Row(Modifier.fillMaxWidth().clip(R.small).clickable(enabled = !current) { Music.switchQueue(id); ui.showQueues = false }
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(current, { if (!current) { Music.switchQueue(id); ui.showQueues = false } },
            colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.muted))
        Column(Modifier.weight(1f)) {
            Text(name, style = T.title.copy(color = if (current) C.accent else C.fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val t = tracks.getOrNull(index)
            Text("${index + 1}/${tracks.size}" + (t?.let { " · ${it.title}" } ?: ""), style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!current) {
            IconButton(Ico.UP, C.muted, 18.dp, 6.dp, desc = "Move up") { Music.moveQueue(id, -1) }
            IconButton(Ico.DOWN, C.muted, 18.dp, 6.dp, desc = "Move down") { Music.moveQueue(id, 1) }
        }
        IconButton(Ico.EDIT, C.muted, 18.dp, 6.dp, desc = "Rename") {
            ui.askName = Triple("Rename the queue", name) { n -> Music.renameQueue(id, n) }
        }
        if (!current) IconButton(Ico.CLOSE, C.muted, 16.dp, 6.dp, desc = "Remove") { ui.confirm = "Delete this queue?" to { Music.deleteQueue(id) } }
    }
}

/** The bar while you pick songs: how many, Options, Advanced select, Cancel. */
@Composable
fun SelectBar(ui: Ui) {
    var adv by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).shadow(10.dp, R.big).clip(R.big).background(C.light)) {
        Text("${ui.selected.size} song${if (ui.selected.size != 1) "s" else ""} selected", style = T.sub,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BarButton(Ico.MORE, "Options") { if (ui.selected.isNotEmpty()) ui.showOptions = true }
            Box {
                BarButton(Ico.SELECT, "Advanced select") { adv = true }
                androidx.compose.material3.DropdownMenu(adv, { adv = false }, containerColor = C.light) {
                    listOf("Select all from this list" to { ui.selectAllHere() },
                        "Deselect all from this list" to { ui.deselectAllHere() },
                        "Select songs in between the first and last selected" to { ui.selectBetween() },
                        "Invert selection" to { ui.invertHere() },
                        "Deselect everything" to { ui.selected.clear() }).forEach { (l, act) ->
                        androidx.compose.material3.DropdownMenuItem({ Text(l, style = T.body) }, { adv = false; act() })
                    }
                }
            }
            BarButton(Ico.CLOSE, "Cancel") { ui.stopSelecting() }
        }
    }
}

@Composable
private fun BarButton(ico: Ico, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(R.card).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(ico, C.accent, 22.dp)
        Text(label, style = T.time.copy(fontSize = 11.sp, color = C.fg), modifier = Modifier.padding(top = 2.dp))
    }
}

/** Options for the songs picked: any of them, one after another if "close after an option" is off. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(ui: Ui) {
    val one = ui.oneSong   // one song's menu (not a selection)
    val picked = one?.let { listOf(it) } ?: ui.selectedTracks()
    val place = ui.selectPlace
    fun close() { ui.showOptions = false; ui.oneSong = null }
    // Always open all the way, so every option shows without dragging the sheet up.
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheet, containerColor = C.light) {
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            item { Text(if (one != null) "“${one.title}”" else "Options for ${picked.size} selected song${if (picked.size != 1) "s" else ""}",
                style = T.head, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            val opts = buildList<Triple<Ico, String, () -> Unit>> {
                if (one != null && place?.kind == "queue" && place.queueIndex != Music.shownIndex()) {
                    add(Triple(Ico.PLAY, "Play now") { Music.playIndex(place.queueIndex) })
                    add(Triple(Ico.CLOSE, "Remove from the queue") { Music.unqueueMany(listOf(place.queueIndex)) })
                }
                add(Triple(Ico.PLAY, "Play") { Music.playList(picked) })
                add(Triple(Ico.SHUFFLE, "Shuffle play") { Music.playList(picked, shuffled = true) })
                add(Triple(Ico.NEXT, "Play after the current song") { Music.enqueue(picked, "next") })
                add(Triple(Ico.QUEUE, "Add to the queue") { Music.enqueue(picked) })
                add(Triple(Ico.PLAYLIST, "Add to a playlist…") { ui.pickPlaylistFor = picked })
                if (one != null) {
                    if (Music.favorites.has(one)) add(Triple(Ico.STAR_LINE, "Remove from favorites") { Music.setFavorite(picked, false) })
                    else add(Triple(Ico.STAR, "Add to favorites") { Music.setFavorite(picked, true) })
                    org.omarchy.music.splitArtists(one.artist).take(3).forEach { n ->
                        add(Triple(Ico.PERSON, "Go to $n") { ui.pages += Ui.Page.Artist(n, one.source) }) }
                } else {
                    add(Triple(Ico.STAR, "Add to favorites") { Music.setFavorite(picked, true) })
                    add(Triple(Ico.STAR_LINE, "Remove from favorites") { Music.setFavorite(picked, false) })
                }
                if (one == null || (!one.isFile && !Music.lib.has(one.id))) add(Triple(Ico.DOWNLOAD, "Download") { Music.download(picked) })
                if (one != null && Music.lib.has(one.id)) add(Triple(Ico.TRASH, "Remove download") {
                    ui.confirm = "Delete the downloaded copy of “${one.title}”?" to { Music.removeDownload(one.id) } })
                add(Triple(Ico.EDIT, "Tagger…") { ui.taggerFor = picked })   // tags, cover, the files' details, lyrics
                add(Triple(Ico.EQ, "Volume…") { ui.volumeFor = picked })
                add(Triple(Ico.EQ, "Custom EQ…") { ui.songEqFor = picked })
                add(Triple(Ico.CLOCK, "Speed…") { ui.speedFor = picked })
                if (Music.upgrading == null && picked.any { org.omarchy.music.upgradable(it) })
                    add(Triple(Ico.SPARKLE, "Upgrade to FLAC…") { ui.upgradeFor = picked.filter { org.omarchy.music.upgradable(it) } })
                picked.filter { it.album.isNotBlank() }.distinctBy { org.omarchy.music.norm(org.omarchy.music.bareTitle(it.album)) }
                    .takeIf { it.size in 1..3 }?.forEach { t ->   // the albums the songs are on (up to 3): go to one
                        add(Triple(Ico.DISC, "Go to album “${t.album.trim()}”") { goToAlbum(ui, t) }) }
                if (picked.size == 1) {   // one song: the options about a single song
                    val one = picked[0]
                    org.omarchy.music.splitArtists(one.artist).firstOrNull()?.let { lead ->
                        add(Triple(Ico.COMPASS, "Discovery playlist for $lead") { Music.makeDiscovery(lead) }) }
                    if (one.isFile || Music.lib.has(one.id)) add(Triple(Ico.FOLDER_OPEN, "File location") { showLocation(ui, one) })
                }
                if (place?.playlistId != null) add(Triple(Ico.CLOSE, "Remove from this playlist") {
                    val ids = picked.map { it.id }.toSet()
                    ui.confirm = "Take ${ids.size} song${if (ids.size != 1) "s" else ""} out of this playlist? They stay in your music." to {
                        Music.removeFromPlaylist(place.playlistId, ids) } })
                if (place?.kind == "queue" && one == null) add(Triple(Ico.CLOSE, "Remove from the queue") {
                    val ids = picked.map { it.id }.toSet()
                    Music.unqueueMany(Music.queue.indices.filter { Music.queue[it].id in ids }) })
                val files = picked.mapNotNull { it.file ?: Music.lib.items[it.id]?.file }.filter { sharable(it) }
                if (org.omarchy.music.AudioTools.supported && picked.size == 1 && (picked[0].file ?: Music.lib.items[picked[0].id]?.file) != null)
                    add(Triple(Ico.SEARCH, "Identify by sound…") { ui.identifyFor = picked[0] })
                add(Triple(Ico.COPY, "Copy the names") {   // "Title — Artist", a line each
                    copyText("Songs", picked.joinToString("\n") { "${it.title} — ${it.artist}" }); Music.toast("Copied ${picked.size} name${if (picked.size != 1) "s" else ""}")
                })
                if (files.isNotEmpty()) add(Triple(Ico.SHARE, "Share") {
                    shareFiles(files, "Share ${files.size} songs")
                })
                if (picked.any { Music.deletable(it) } && !(one != null && Music.lib.has(one.id)))
                    add(Triple(Ico.TRASH, "Delete permanently…") { ui.deleteFor = picked })
                if (one != null && place != null) add(Triple(Ico.SELECT, "Select…") { ui.startSelecting(place.group, place, one) })
            }
            items(opts.size) { i ->
                val (ico, label, act) = opts[i]
                Row(Modifier.fillMaxWidth().clickable {
                    val single = one != null
                    close()
                    act()
                    // The ones that open a dialog close the selection when they're done.
                    if (!single && label.endsWith("…").not()) ui.afterOption()
                }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ico, if (label.startsWith("Delete")) C.red else C.accent, 20.dp)
                    Text(label, style = T.body.copy(color = if (label.startsWith("Delete")) C.red else C.fg), modifier = Modifier.padding(start = 16.dp))
                }
            }
            if (one == null) item {
                Row(Modifier.fillMaxWidth().clickable { ui.closeAfter = !ui.closeAfter; org.omarchy.music.Prefs.set("close_after", ui.closeAfter) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(ui.closeAfter, { ui.closeAfter = it; org.omarchy.music.Prefs.set("close_after", it) },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.light))
                    Text("Close the selection after an option (off: use several options on the same songs)", style = T.sub)
                }
            }
        }
    }
}

/** 24-bit FLACs or 32-bit FLACs (☰ menu): your FLAC songs of that bit depth, each with its sample rate
 *  ("24-bit · 96 kHz"), the highest sample rate first; as a list or a grid. Songs not read yet are read on opening. */
@Composable
fun FlacTab(ui: Ui, bits: Int) {
    androidx.compose.runtime.LaunchedEffect(Music.musicVersion, Music.savedVersion) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { org.omarchy.music.FlacDepths.scan() } }
    }
    val v = org.omarchy.music.FlacDepths.version
    val all = remember(Music.musicVersion, Music.savedVersion, v, bits) { runCatching { org.omarchy.music.FlacDepths.songs(bits) }.getOrDefault(emptyList()) }
    val reading = org.omarchy.music.FlacDepths.reading
    if (all.isEmpty()) {
        Empty(if (reading != null) "Reading your FLAC files… ${reading.first} of ${reading.second}" else "No $bits-bit FLACs",
            "Your FLAC songs recorded at $bits bits show up here, each with its sample rate.")
        return
    }
    val formats = remember(all) { all.associate { it.first.id to it.second } }
    val sorts = org.omarchy.music.songSorts(true, extra = listOf("rate", "rate-low"))
    val sort = sortNow("flac$bits", sorts)
    val plain = remember(all) { all.map { it.first } }
    val songs = rememberSorted(plain, sort) { d -> org.omarchy.music.sortTracks(plain, sort, d) { t -> formats[t.id]?.rate ?: 0 } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListActions(songs, onDj = { ui.djFrom(Dj.From("songs", name = "$bits-bit FLACs", songs = songs)) })
            Section("${all.size} $bits-BIT FLAC${if (all.size != 1) "S" else ""}" +
                (reading?.let { " · READING ${it.first} OF ${it.second}" } ?: ""))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) { SortMenu("flac$bits", sorts) }
        }
        tracks(songs, ui, "flac$bits") { t -> formats[t.id]?.let { org.omarchy.music.FlacDepths.label(it) } ?: "" }
    }
}

/** Downloads (☰ menu): the songs you downloaded from a service (Tidal, or YouTube where Tidal hasn't them), the newest
 *  first, each with its quality; as a list or a grid. */
@Composable
fun DownloadsTab(ui: Ui) {
    val all = remember(Music.savedVersion) { runCatching { Music.lib.items.values.sortedByDescending { it.added } }.getOrDefault(emptyList()) }
    if (all.isEmpty()) { Empty("No downloads yet", "Songs you download from Tidal or YouTube show up here."); return }
    val got = remember(all) { all.map { it.track() } }
    val quality = remember(all) { all.associate { it.track().id to it.quality } }
    val sorts = org.omarchy.music.songSorts(true)
    val sort = sortNow("downloads", sorts)
    val songs = rememberSorted(got, sort) { d -> org.omarchy.music.sortTracks(got, sort, d) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListActions(songs, onDj = { ui.djFrom(Dj.From("songs", name = "Downloads", songs = songs)) })
            Section("${songs.size} DOWNLOADED SONG${if (songs.size != 1) "S" else ""}")
            Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) { SortMenu("downloads", sorts, names = mapOf("order" to "Newest download first")) }
        }
        tracks(songs, ui, "downloads") { t -> quality[t.id].orEmpty() }
    }
}
