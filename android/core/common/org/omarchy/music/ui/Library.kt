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
    // Albums: the big ones (more than six songs here) or the ones you have only a few songs of
    val bigAlbums = kind == "albums" && ui.albumsLow.not()
    val sized = if (kind != "albums") all else all.filter { (_, songs) -> if (ui.albumsLow) songs.size <= 6 else songs.size > 6 }
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
                if (kind == "albums") Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Albums (${all.count { it.second.size > 6 }})", !ui.albumsLow) { ui.albumsLow = false }
                    Chip("Low song count albums (${all.count { it.second.size <= 6 }})", ui.albumsLow) { ui.albumsLow = true }
                }
            }
            Section("${albums.size} ${if (eps) "EP" else if (singles) "SINGLE" else if (!bigAlbums) "LOW SONG COUNT ALBUM" else "ALBUM"}${if (albums.size != 1) "S" else ""}")
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
    val songs = rememberSorted(page.tracks, sort) { d -> org.omarchy.music.sortTracks(page.tracks, sort, d) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListActions(songs, onDj = { ui.djFrom(Dj.From("songs", name = page.title, songs = page.tracks)) },
                pin = if (page.id.isNotEmpty() && page.kind in setOf("genre", "folder")) Pin(page.kind, page.id, page.title) else null)
            Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) { SortMenu(tab, sorts) }
        }
        tracks(songs, ui, page.kind)
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
            if (Music.queues.isNotEmpty()) TextButton({ Music.removeOtherQueues() }) { Text("Remove all other queues", color = C.red) }
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
        if (!current) IconButton(Ico.CLOSE, C.muted, 16.dp, 6.dp, desc = "Remove") { Music.deleteQueue(id) }
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
    val picked = ui.selectedTracks()
    val place = ui.selectPlace
    // Always open all the way, so every option shows without dragging the sheet up.
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { ui.showOptions = false }, sheetState = sheet, containerColor = C.light) {
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            item { Text("Options for ${picked.size} selected song${if (picked.size != 1) "s" else ""}", style = T.head,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            val opts = buildList<Triple<Ico, String, () -> Unit>> {
                add(Triple(Ico.PLAY, "Play") { Music.playList(picked) })
                add(Triple(Ico.SHUFFLE, "Shuffle play") { Music.playList(picked, shuffled = true) })
                add(Triple(Ico.NEXT, "Play after the current song") { Music.enqueue(picked, "next") })
                add(Triple(Ico.QUEUE, "Add to the queue") { Music.enqueue(picked) })
                add(Triple(Ico.PLAYLIST, "Add to a playlist…") { ui.pickPlaylistFor = picked })
                add(Triple(Ico.STAR, "Add to favorites") { Music.setFavorite(picked, true) })
                add(Triple(Ico.STAR_LINE, "Remove from favorites") { Music.setFavorite(picked, false) })
                add(Triple(Ico.DOWNLOAD, "Download") { Music.download(picked) })
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
                    Music.removeFromPlaylist(place.playlistId, picked.map { it.id }.toSet()) })
                if (place?.kind == "queue") add(Triple(Ico.CLOSE, "Remove from the queue") {
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
                if (picked.any { Music.deletable(it) }) add(Triple(Ico.TRASH, "Delete permanently…") { ui.deleteFor = picked })
            }
            items(opts.size) { i ->
                val (ico, label, act) = opts[i]
                Row(Modifier.fillMaxWidth().clickable {
                    ui.showOptions = false
                    act()
                    // The ones that open a dialog close the selection when they're done.
                    if (label.endsWith("…").not()) ui.afterOption()
                }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ico, if (label.startsWith("Delete")) C.red else C.accent, 20.dp)
                    Text(label, style = T.body.copy(color = if (label.startsWith("Delete")) C.red else C.fg), modifier = Modifier.padding(start = 16.dp))
                }
            }
            item {
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
