package org.omarchy.music.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.omarchy.music.Album
import org.omarchy.music.ArtistInfo
import org.omarchy.music.Dj
import org.omarchy.music.FolderItem
import org.omarchy.music.LastFm
import org.omarchy.music.PLAYLIST_SORTS
import org.omarchy.music.SpotifyHistory
import org.omarchy.music.Stats
import org.omarchy.music.Found
import org.omarchy.music.Music
import org.omarchy.music.Remote
import org.omarchy.music.SOURCES
import org.omarchy.music.Track
import org.omarchy.music.fmtTotal
import org.omarchy.music.linkSource
import org.omarchy.music.localMatches
import org.omarchy.music.norm

private fun searchName(key: String) = if (key == "local") "your music" else SOURCES[key] ?: ""

@Composable
fun SearchTab(ui: Ui) {
    val res = ui.found
    val where = searchName(ui.foundIn.ifEmpty { Music.searchIn })
    LazyColumn(Modifier.fillMaxSize()) {
        when {
            ui.searching && (res == null || res.isEmpty) -> item { Empty("Searching $where…") }   // else what was found stays till the new results come
            res == null -> item { Empty("Search ${searchName(Music.searchIn)}", "Songs, artists or albums: pick where with the menu by the search bar. Or paste a link to a song, an album or a playlist.") }
            res.isEmpty -> item { Empty("Nothing found", "${ui.query}\nin $where") }
            else -> {
                if (res.artists.isNotEmpty()) {
                    item { Section("ARTISTS") }
                    items(res.artists.size) { i ->
                        val a = res.artists[i]
                        PlaylistRow(a) { ui.pages += Ui.Page.Artist(a.title, ui.foundIn.takeIf { it in SOURCES }) }
                    }
                }
                if (res.albums.isNotEmpty()) {
                    item { Section("ALBUMS") }
                    items(res.albums.size) { i ->
                        val al = res.albums[i]
                        val mine = res.albumTracks[al.id]
                        PlaylistRow(al, mine?.firstOrNull()?.let { artModel(it) }) { ui.pages += Ui.Page.AlbumPage(al, ui.foundIn, mine) }
                    }
                }
                if (res.songs.isNotEmpty()) {
                    if (res.artists.isNotEmpty() || res.albums.isNotEmpty()) item { Section("SONGS") }
                    tracks(res.songs, ui, "search", radio = res.songs.size > 1 && !ui.resultsAreList)
                }
            }
        }
    }
}

/** Search where the search bar's menu says (a pasted link: on its own service), for songs, artists and albums. */
/** Search for ui.query. quiet (searching as you type): no messages about services that aren't connected. */
fun runSearch(ui: Ui, quiet: Boolean = false) {
    val q = ui.query.trim()
    if (q.isEmpty()) return
    ui.searched = q
    val link = linkSource(q)
    var key = link ?: Music.searchIn
    if (key == "tidal" && link == null && Music.sources["tidal"]?.signedIn != true) {
        if (!quiet) Music.toast("Tidal isn't connected: searching YouTube Music")
        key = "ytm"
    }
    val seq = ++ui.searchSeq
    ui.tab = "search"
    ui.resultsAreList = Regex("/(playlist|album|mix)/|list=").containsMatchIn(q)
    if (key == "local") {
        ui.searching = false
        ui.foundIn = key
        ui.found = localMatches(Music.mySongs(), q)
        return
    }
    val src = Music.sources[key]
    if (src == null) { if (!quiet) Music.toast("Still connecting to ${SOURCES[key]}…"); return }
    if (!src.signedIn && key in setOf("tidal", "spotify", "apple")) { if (!quiet) Music.toast("Connect ${SOURCES[key]} in Settings first"); return }
    ui.searching = true
    ui.foundIn = key
    Music.bg({ if (link != null) Found(src.search(q)) else src.find(q) }) { res, err ->
        if (seq != ui.searchSeq) return@bg   // a newer search is on its way
        ui.searching = false
        if (err != null) { ui.found = Found(emptyList()); Music.toast("Search failed: ${err.message?.take(100)}") }
        else ui.found = res
    }
}

@Composable
fun LibraryTab(ui: Ui) {
    val key = Music.source
    Music.accountsVersion
    val src = Music.sources[key]
    val loaded = ui.playlistsBySource.firstOrNull { it.first == key }
    LaunchedEffect(key, src?.signedIn) {
        if (src != null && src.signedIn && loaded == null) {
            ui.playlistsBySource += key to null
            Music.bg({ src.myPlaylists() }) { res, err ->
                ui.playlistsBySource.removeAll { it.first == key }
                ui.playlistsBySource += key to (res ?: emptyList())
                if (err != null) Music.toast("Couldn't load your ${SOURCES[key]} playlists: ${err.message?.take(80)}")
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        val open = ui.openList
        when {
            src == null -> item {
                val err = Music.sourceErrors[key]
                if (err != null) Empty("${SOURCES[key]} isn't available", err.take(140))
                else Empty("Not signed in", "Open the Settings (⚙) to sign in to ${SOURCES[key]}.")
                ActionRow("Open Settings  ›") { ui.sheet = "accounts" }
            }
            !src.signedIn -> item {
                Empty("Not signed in", src.hint)
                ActionRow("Connect ${SOURCES[key]}  ›") { ui.sheet = "accounts" }
            }
            open == null && ui.openFolder != null && Music.playlistFolders.get(ui.openFolder!!) != null ->
                folderView(ui, Music.playlistFolders.get(ui.openFolder!!)!!)
            open != null -> {
                item { BackRow(open.first.title) { ui.openList = null } }
                val tracks = open.second?.let { Music.sortedPlaylist(it) }
                if (tracks == null) item { Section("LOADING…") }
                else {
                    item { ListActions(tracks, onDj = { ui.djFrom(Dj.From("songs", name = open.first.title, songs = tracks)) },
                pin = org.omarchy.music.Pin("service", open.first.id, open.first.title, open.first.sub, open.first.thumb, key)) }
                    if (tracks.isNotEmpty()) item { PlaylistSortChips() }
                    tracks(tracks, ui, "list")
                }
            }
            loaded?.second == null -> item { Empty("Loading your library…") }
            else -> {
                item { SyncLine() }
                if (LastFmOn()) item { ActionRow("✦  Your ${org.omarchy.music.Wrapped.year()} Wrapped  ›") { ui.sheet = "wrapped" } }
                val lists = loaded.second!!
                item { ActionRow("＋  New folder") { ui.askName = Triple("New folder", "") { n -> Music.newFolder(n) } } }
                folderRows(ui)
                if (lists.isEmpty()) item { Section("NO PLAYLISTS YET") }
                else if (Music.playlistFolders.items.isNotEmpty()) item { Section("${SOURCES[key]?.uppercase()} PLAYLISTS") }
                val shown = lists.filter { fits(ui, it.title, it.sub) }
                fun openPl(pl: Album) {
                    ui.openList = pl to null
                    Music.bg({ src.playlist(pl.id) }) { res, err ->
                        if (ui.openList?.first == pl) ui.openList = pl to (res ?: emptyList())
                        if (err != null) Music.toast("Couldn't open it: ${err.message?.take(80)}")
                    }
                }
                fun menu(pl: Album) {
                    ui.choices = pl.title to listOf(pinChoice(org.omarchy.music.Pin("service", pl.id, pl.title, pl.sub, pl.thumb, key)),
                        "Put in a folder…" to { ui.folderFor = FolderItem.service(pl, key) })
                }
                laidOutItems(shown, ui, key = { "sp-" + shown[it].id + "#" + it }, row = { i ->
                    PlaylistRow(shown[i], onLongClick = { menu(shown[i]) }) { openPl(shown[i]) }
                }) { i, mod, compact ->
                    val pl = shown[i]
                    Tile(pl.thumb.ifEmpty { null }, pl.title, pl.sub, mod, compact, ico = Ico.PLAYLIST, onLongClick = { menu(pl) }) { openPl(pl) }
                }
            }
        }
    }
}

private fun LastFmOn() = org.omarchy.music.LastFm.connected

@Composable
fun SyncLine() {
    Music.savedVersion
    val text = when {
        !Music.tidal.connected -> "Connect Tidal to play everything in FLAC  ›"
        Music.syncTracks.isEmpty() -> if (Music.syncing) "FLAC · reading your playlists…" else "FLAC · Tidal connected"
        else -> {
            val tr = Music.syncTracks
            val flac = tr.count { Music.lib.isFlac(it.id) }
            "FLAC · $flac of ${tr.size} songs saved" + if (Music.syncing) " · downloading…" else ""
        }
    }
    Text(text, style = T.sub.copy(color = C.accent), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun ListActions(tracks: List<Track>, extra: List<Pair<String, () -> Unit>> = emptyList(), onDj: (() -> Unit)? = null, pin: org.omarchy.music.Pin? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tracks.isNotEmpty()) {
            Pill("Play") { Music.playList(tracks) }
            Pill("Shuffle") { Music.playList(tracks, shuffled = true) }
            if (onDj != null) Pill("DJ", quiet = true, onClick = onDj)
            if (tracks.any { !it.isFile && !Music.lib.has(it.id) }) Pill("Download", quiet = true) { Music.download(tracks) }
        }
        if (pin != null) PinPill(pin)
        extra.forEach { (label, act) -> Pill(label, quiet = true, onClick = act) }
    }
    if (tracks.isNotEmpty()) Text("${tracks.size} songs · ${fmtTotal(tracks)}" +
        if (Music.downloadNote.isNotEmpty() && tracks.any { t -> Music.downloads.any { it.id == t.id } }) " · ${Music.downloadNote}" else "",
        style = T.section, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
}

@Composable
fun MusicTab(ui: Ui) {
    val access = rememberMusicAccess()
    val granted = access.granted
    var choosing by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf("") }
    if (choosing) FolderBrowser { choosing = false }
    LaunchedEffect(Unit) { if (granted) Music.rescanSoon() }
    LaunchedEffect(ui.musicView, Music.accountsVersion, SpotifyHistory.count) { if (ui.musicView == "top") Stats.refresh() }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) Music.importPlaylist(uri) }
    Music.musicVersion
    Music.savedVersion
    Column(Modifier.fillMaxSize()) {
        // Which of these is shown is picked from the library's list (Main.kt's categories).
        val state = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            Music.musicVersion   // read here too: the list is built apart from the screen, and must follow a scan
            Music.savedVersion
            if (!granted && ui.musicView == "all") item {
                ActionRow("Allow access to the music on this phone  ›") { access.ask() }
            } else if (ui.musicView == "all") item {
                val from = if (Music.folders.isEmpty()) "no folders" else Music.folders.joinToString { it.trimEnd('/') }
                ActionRow("From $from  ·  Choose folders  ›") { choosing = true }
            }
            when (ui.musicView) {
                "playlists" -> myPlaylists(ui) { importer.launch(arrayOf("*/*")) }
                "dj" -> djView(ui, ask) { ask = it }
                "dupes" -> dupesView(ui)
                "pinned" -> pinnedView(ui)
                "newreleases" -> newReleasesView(ui)
                "discovery" -> discoveryView(ui)
                "repeat" -> repeatView(ui)
                else -> {
                    val skipRows = if (ui.musicView == "skips") Music.songStats.rows(Music.skipsSince()) else emptyList()
                    val skipInfo = skipRows.associate { (t, k, f) -> org.omarchy.music.songKey(t) to (k to f) }
                    val tracks = when (ui.musicView) {
                        "all" -> sorted(Music.mySongs(), Music.musicSort)
                        "recent" -> Music.plays.recent()
                        "favorites" -> Music.favorites.tracks()
                        "unfavorited" -> Music.mySongs().filter { !Music.favorites.has(it) }.sortedBy { norm(it.title) }
                        "skips" -> {
                            val r = { a: Int, b: Int -> org.omarchy.music.SongStats.rate(a, b) }
                            when (Music.skipOrder) {
                                "least" -> skipRows.sortedWith(compareBy<Triple<Track, Int, Int>>({ r(it.second, it.third) }, { -it.third }, { it.second }))
                                "heard" -> skipRows.sortedWith(compareBy<Triple<Track, Int, Int>>({ -it.third }, { it.second }))
                                else -> skipRows.sortedWith(compareBy<Triple<Track, Int, Int>>({ -it.second }, { -r(it.second, it.third) }))
                            }.map { it.first }
                        }
                        else -> Music.mostPlayed(Music.topPeriod)
                    }
                    if (tracks.isEmpty() && !(ui.musicView == "skips" && Music.skipPeriod != "all") && !(ui.musicView == "top" && Music.topPeriod != "all")) item {   // (a period with none: its chips stay)
                        val (t, b) = when (ui.musicView) {
                            "all" -> "No music yet" to "Songs you download, and the music in your folders\n(${Music.folders.joinToString()}), show up here."
                            "recent" -> "Nothing played yet" to "The songs you listen to show up here, the last one first."
                            "favorites" -> "No favorites yet" to "Long-press a song (or select a few) and pick Favorite, or tap the ☆ by the song playing."
                            "unfavorited" -> "Every song is a favorite" to "Your songs you haven't starred show up here."
                            "skips" -> "No skips counted yet" to "Go on to the next song with more than ${org.omarchy.music.SongStats.SKIP_LEFT} seconds left and it counts as a skip; hear it to the end and it counts as heard out. Last.fm keeps no skips, so this starts now."
                            else -> "Nothing played yet" to "The songs you play most show up here."
                        }
                        Empty(t, b)
                    } else {
                        item {
                            if (ui.musicView == "all") {
                                val dl = Music.lib.items.size
                                Section("${tracks.size} SONGS · $dl DOWNLOADED · ${fmtTotal(tracks).uppercase()}")
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("SORT", style = T.section, modifier = Modifier.padding(end = 2.dp))
                                    listOf("title" to "Title", "artist" to "Artist", "album" to "Album", "added" to "Added").forEach { (k, l) ->
                                        Chip(l, Music.musicSort == k) { Music.setMusicSort(k) }
                                    }
                                }
                            } else if (ui.musicView == "skips") {
                                Section("${tracks.size} SONGS · SKIPPED OR HEARD OUT")
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("week" to "This week", "month" to "This month", "year" to "This year", "all" to "All time").forEach { (k, l) ->
                                        Chip(l, Music.skipPeriod == k) { Music.chooseSkipPeriod(k) }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("most" to "Skipped most", "least" to "Skipped least", "heard" to "Heard out most").forEach { (k, l) ->
                                        Chip(l, Music.skipOrder == k) { Music.chooseSkipOrder(k) }
                                    }
                                }
                            } else Section(when (ui.musicView) {
                                "recent" -> "RECENTLY PLAYED"
                                "favorites" -> "${tracks.size} FAVORITES"
                                "unfavorited" -> "${tracks.size} SONGS NOT FAVORITED"
                                else -> if (Music.mostPlayedFromStats) "MOST PLAYED · STATS" else "MOST PLAYED"
                            })
                            if (ui.musicView == "top" && (org.omarchy.music.LastFm.connected || Music.mostPlayedFromStats)) {
                                val st = org.omarchy.music.Stats
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("songs" to "Songs", "albums" to "Albums", "artists" to "Artists").forEach { (k, l) -> Chip(l, Music.topKind == k) { Music.chooseTopKind(k) } }
                                }
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("week" to "This week", "month" to "This month", "year" to "This year", "all" to "All time").forEach { (k, l) ->
                                        Chip(l, Music.topPeriod == k) { Music.chooseTopPeriod(k) }
                                    }
                                }
                                ActionRow(if (st.busy) "Refreshing…" else "Refresh  ↻  ${tracks.size} songs · ${st.sources.joinToString(" + ")}") {
                                    if (!st.busy) st.refresh(force = true)
                                }
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Chip("Only songs on this phone", Music.topMineOnly) { Music.chooseTopMineOnly(!Music.topMineOnly) }
                                    Chip("Collage…", false) { ui.collageFor = Music.topKind }
                                }
                            }
                        }
                        if (ui.musicView == "top" && Music.topKind == "albums") topAlbumsItems(ui)   // the songs above, added up by album
                        else if (ui.musicView == "top" && Music.topKind == "artists") topArtistsItems(ui)   // every play of theirs
                        else if (ui.musicView == "all" && Music.musicSort in setOf("artist", "album") && layoutNow(ui) == "list" && ui.filter.isNullOrBlank()) grouped(tracks, ui, Music.musicSort == "artist")
                        else tracks(tracks, ui, ui.musicView) { t ->
                            when (ui.musicView) {
                                "top" -> "${t.plays} play${if (t.plays != 1) "s" else ""}"
                                "skips" -> skipInfo[org.omarchy.music.songKey(t)]?.let { (k, f) ->
                                    (if (k > 0) "$k skip${if (k != 1) "s" else ""}" else "never skipped") + if (f > 0) " · heard out ${f}×" else "" } ?: ""
                                else -> ""
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Duplicates: songs you have more than once, each copy with what it is, to delete the ones you don't want. */
private fun androidx.compose.foundation.lazy.LazyListScope.dupesView(ui: Ui) {
    val groups = Music.dupes
    when {
        Music.dupesBusy -> item { Section("LOOKING FOR DUPLICATES…") }
        groups == null -> item {
            Empty("Duplicates", "Look through all your songs (downloads and your folders) for ones you have more than once. " +
                "You'll see each copy's name, artist and file, and say which to delete.")
            ActionRow("Look for duplicates  ›") { Music.findDupes() }
        }
        groups.isEmpty() -> item {
            Empty("No duplicates", "Every song you have is there once.")
            ActionRow("Look again  ›") { Music.findDupes() }
        }
        else -> {
            item {
                Section("${groups.size} SONG${if (groups.size != 1) "S" else ""} · ${groups.sumOf { it.size - 1 }} EXTRA COPIES · THE BEST COPY FIRST")
                ActionRow("Look again  ›") { Music.findDupes() }
            }
            groups.forEach { g ->
                item(key = "dupe-head-${g[0].id}") { Section(g[0].title.uppercase() + " · " + g[0].artist.uppercase()) }
                g.forEachIndexed { i, t ->
                    item(key = "dupe-${g[0].id}-${t.id}") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${t.title} · ${t.artist}" + if (t.album.isNotEmpty()) " · ${t.album}" else "", style = T.body, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text((if (i == 0) "★ best · " else "") + listOf(Music.copyInfo(t), org.omarchy.music.fmtTime(t.duration))
                                    .filter { it.isNotEmpty() }.joinToString(" · "), style = T.sub)
                                Text(Music.whereIs(t), style = T.time, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("▶", style = T.title.copy(color = C.accent), modifier = Modifier.clickable { Music.playFrom(listOf(t), 0, false) }.padding(8.dp))
                            Pill("Delete…", quiet = true) {
                                ui.confirm = ("Delete this copy of “${t.title}”?\n\n• ${t.title} — ${t.artist}\n   ${Music.copyInfo(t)}\n   ${Music.whereIs(t)}" +
                                    "\n\nIt's a duplicate of:\n" + g.filter { it.id != t.id }.joinToString("\n") { o ->
                                        "• ${o.title} — ${o.artist}\n   ${Music.copyInfo(o)}\n   ${Music.whereIs(o)}" } +
                                    "\n\nYour playlists get the copy you keep in its place." +
                                    if (Music.lib.has(t.id)) "" else " Android asks you once more, and the file goes to its trash.") to { Music.deleteDupe(g, t) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The DJ: what it said last, Start or Switch it up, a box to ask it for something, and its songs to come. */
private fun androidx.compose.foundation.lazy.LazyListScope.djView(ui: Ui, ask: String, setAsk: (String) -> Unit) {
    val d = Music.shownDj()
    item(key = "dj") {
        val focus = LocalFocusManager.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DJ" + (Dj.KINDS[d.kind]?.let { " · ${it.uppercase()}" } ?: ""), style = T.section)
            Text(d.line.ifEmpty {
                "Your own DJ: a mix of the songs you love, ones you haven't heard in a while and new ones from " +
                    "Tidal and YouTube Music, a few at a time. Switch it up for something else, or ask for an artist, " +
                    "a genre, a decade or a vibe."
            }, style = if (d.line.isNotEmpty()) T.title else T.body)
            if (d.busy.isNotEmpty()) Text(d.busy, style = T.sub)
            Box {
                var menu by remember { mutableStateOf(false) }
                Pill("From: ${d.from.ifEmpty { "everything" }}  ▾", quiet = true) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val pick = { f: Dj.From -> menu = false; ui.djFrom(f) }
                    val item = @Composable { label: String, f: Dj.From ->
                        DropdownMenuItem(text = { Text(label, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { pick(f) })
                    }
                    item("Everything: your history and new songs", Dj.From("all"))
                    item("All your music · ${Music.mySongs().size}", Dj.From("mine"))
                    // The folders your songs are in, under the ones you picked, with how many each holds.
                    val counts = Music.local.paths.values.groupingBy { it }.eachCount()
                    val tops = Music.folders.filter { f -> counts.keys.any { it.startsWith(f) } }
                    for (top in tops) {
                        item("${top.trimEnd('/')} · ${counts.filterKeys { it.startsWith(top) }.values.sum()}", Dj.From("folder", path = top))
                        val subs = counts.keys.filter { it.startsWith(top) && it != top }.map { top + it.removePrefix(top).substringBefore('/') + "/" }.distinct().sorted()
                        for (sub in subs) item("      ${sub.removePrefix(top).trimEnd('/')} · ${counts.filterKeys { it.startsWith(sub) }.values.sum()}", Dj.From("folder", path = sub))
                    }
                    Music.lists.all().filter { it.tracks.isNotEmpty() }.forEach { item("${it.name} · ${it.tracks.size}", Dj.From("playlist", id = it.id)) }
                }
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                Pill((if (Music.djNew) "Include new songs" else "Only my songs") + "  ▾", quiet = true) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(false to "Only my songs", true to "Include new songs").forEach { (on, label) ->
                        DropdownMenuItem(text = { Text((if (Music.djNew == on) "✓  " else "     ") + label, style = T.body) },
                            onClick = { menu = false; Music.chooseDjNew(on) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (d.on) {
                    Pill("↻  Switch it up") { Music.djSwitch() }
                    Pill("Stop the DJ", quiet = true) { Music.djStop() }
                } else Pill("▶  Start the DJ") { Music.djStart() }
            }
            Box(Modifier.fillMaxWidth().border(1.dp, C.line).background(C.fg.copy(alpha = 0.04f)).padding(horizontal = 10.dp, vertical = 10.dp)) {
                if (ask.isEmpty()) Text("Ask the DJ: something chill, 2010s, house, Daft Punk…", style = T.body.copy(color = C.muted), maxLines = 1)
                BasicTextField(ask, setAsk, singleLine = true, textStyle = T.body, cursorBrush = SolidColor(C.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (ask.isNotBlank()) { focus.clearFocus(); Music.djSwitch(ask); setAsk("") }
                    }),
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (!d.on) return
    val q = Music.shownQueue()
    val coming = (maxOf(0, Music.shownIndex()) until q.size).filter { q[it].dj > 0 }
    if (coming.isEmpty()) return
    item { Section("FROM THE DJ") }
    items(coming.size, key = { "dj${coming[it]}" }) { j ->
        val i = coming[j]
        TrackRow(q[i], RowPlace(q, i, "queue", queueIndex = i), ui, Dj.KINDS[d.kinds[q[i].dj.toString()]] ?: "") { Music.playIndex(i) }
    }
}

private fun leadArtist(t: Track) = t.artist.split(",")[0].trim().ifEmpty { "Unknown artist" }

/** Your songs, sorted as the Music tab's chips say: by title, artist, album, or newest first. */
private fun sorted(songs: List<Track>, sort: String): List<Track> = when (sort) {
    "artist" -> songs.sortedWith(compareBy({ norm(leadArtist(it)).ifEmpty { "~" } }, { norm(it.album) }, { norm(it.title) }))
    "album" -> songs.sortedWith(compareBy({ norm(it.album).ifEmpty { "~" } }, { norm(leadArtist(it)) }, { norm(it.title) }))
    "added" -> songs.sortedByDescending { it.added }
    else -> songs.sortedWith(compareBy({ norm(it.title) }, { norm(it.artist) }))
}

/** The songs under a heading per artist (tap it for their page) or per album. */
private fun androidx.compose.foundation.lazy.LazyListScope.grouped(list: List<Track>, ui: Ui, byArtist: Boolean) {
    var start = 0
    while (start < list.size) {
        val key = { t: Track -> if (byArtist) norm(leadArtist(t)) else norm(t.album) }
        val k = key(list[start])
        var end = start
        while (end < list.size && key(list[end]) == k) end++
        val first = list[start]
        val from = start
        item(key = "head#$from") {
            Column(Modifier.fillMaxWidth().then(if (byArtist) Modifier.clickable { ui.pages += Ui.Page.Artist(leadArtist(first), first.source) } else Modifier)
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
                Text(if (byArtist) leadArtist(first) else first.album.ifEmpty { "No album" }, style = T.title, maxLines = 1)
                if (!byArtist) Text(leadArtist(first), style = T.sub, maxLines = 1)
            }
        }
        items(end - start, key = { "${list[from + it].id}#${from + it}" }) { j ->
            val i = from + j
            TrackRow(list[i], RowPlace(list, i, "all"), ui, "") { Music.playFrom(list, i, false) }
        }
        start = end
    }
}

/** Your folders of playlists, as rows: in the Library tab and in Music › Playlists. */
private fun androidx.compose.foundation.lazy.LazyListScope.folderRows(ui: Ui) {
    val folders = Music.playlistFolders.all()
    if (folders.isEmpty()) return
    item { Section("FOLDERS") }
    items(folders.size, key = { "folder-${folders[it].id}" }) { i ->
        val f = folders[i]
        val n = f.playlists.size
        PlaylistRow(Album(f.id, f.name, "Folder · $n playlist${if (n != 1) "s" else ""}"), onLongClick = {
            ui.choices = f.name to listOf(pinChoice(org.omarchy.music.Pin("listfolder", f.id, f.name)),
                "Rename…" to { ui.askName = Triple("Rename folder", f.name) { name -> Music.renameFolder(f.id, name) } })
        }) { ui.openFolder = f.id }
    }
}

/** A folder open: its playlists, yours and your services', each opening as it does anywhere. */
private fun androidx.compose.foundation.lazy.LazyListScope.folderView(ui: Ui, f: org.omarchy.music.PlaylistFolder) {
    item { BackRow(f.name) { ui.openFolder = null } }
    item {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PinPill(org.omarchy.music.Pin("listfolder", f.id, f.name))
            Pill("Rename", quiet = true) { ui.askName = Triple("Rename folder", f.name) { n -> Music.renameFolder(f.id, n) } }
            Pill("Delete", quiet = true) { ui.confirm = "Delete the folder “${f.name}”? Its playlists stay where they are." to { Music.deleteFolder(f.id); ui.openFolder = null } }
        }
    }
    val items = f.playlists.filter { it.kind != "mine" || Music.lists.get(it.id) != null }   // yours deleted since: gone
    if (items.isEmpty()) { item { Empty("Nothing in it yet", "Long-press a playlist and pick this folder.") }; return }
    item { Section("${items.size} PLAYLIST${if (items.size != 1) "S" else ""}") }
    items(items.size, key = { "fi-${items[it].key}" }) { i ->
        val it = items[i]
        val mine = if (it.kind == "mine") Music.lists.get(it.id) else null
        val sub = if (mine != null) "yours" else "Playlist · ${SOURCES[it.source] ?: it.source}"
        PlaylistRow(Album(it.id, mine?.name ?: it.title, sub, it.thumb, mine?.tracks?.size?.toString() ?: ""),
            mine?.tracks?.firstOrNull { t -> t.thumb.isNotEmpty() || t.isFile }?.let { t -> artModel(t) },
            onLongClick = { ui.confirm = "Take “${mine?.name ?: it.title}” out of “${f.name}”?" to { Music.removeFromFolder(f.id, it) } }) {
            ui.openFolder = null
            if (mine != null) { ui.tab = "music"; ui.musicView = "playlists"; ui.openMine = mine.id; return@PlaylistRow }
            openServicePlaylist(ui, it.id, it.title, it.thumb, it.source)
        }
    }
}

/** Chips to sort a playlist's songs by (PLAYLIST_SORTS), the same for every playlist. */
@Composable
private fun PlaylistSortChips() {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SORT", style = T.section, modifier = Modifier.padding(end = 2.dp))
        PLAYLIST_SORTS.forEach { (k, l) -> Chip(l, Music.playlistSort == k) { Music.pickPlaylistSort(k) } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.myPlaylists(ui: Ui, import: () -> Unit) {
    val open = ui.openMine?.let { Music.lists.get(it) }
    val folder = ui.openFolder?.let { Music.playlistFolders.get(it) }
    if (open != null) { minePlaylist(ui, open); return }
    if (open == null && folder != null) { folderView(ui, folder); return }
    if (open == null) {
        item { ActionRow("＋  New playlist") { ui.askName = Triple("New playlist", "") { n -> Music.newPlaylist(n) } } }
        item { ActionRow("＋  New folder") { ui.askName = Triple("New folder", "") { n -> Music.newFolder(n) } } }
        item {
            val busy = Music.importing
            if (busy != null) Text(busy, style = T.sub.copy(color = C.accent), modifier = Modifier.padding(12.dp))
            else ActionRow("⤓  Import a playlist (.m3u8)") { import() }
        }
        folderRows(ui)
        val lists = Music.lists.all()
        if (lists.isNotEmpty() && Music.playlistFolders.items.isNotEmpty()) item { Section("YOUR PLAYLISTS") }
        if (lists.isEmpty()) item {
            Section("NO PLAYLISTS YET")
            Text("Make one here, or long-press any song (or select a few) and pick Add to playlist.", style = T.sub,
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        val shown = lists.filter { fits(ui, it.name) }
        fun menu(pl: org.omarchy.music.Playlist) {
            ui.choices = pl.name to listOf(pinChoice(org.omarchy.music.Pin("mine", pl.id, pl.name)), "Put in a folder…" to { ui.folderFor = FolderItem.mine(pl) })
        }
        fun lay(some: List<org.omarchy.music.Playlist>) = laidOutItems(some, ui, key = { "pl-" + some[it].id }, row = { i ->
            val pl = some[i]
            val songs = pl.tracks
            val cover = songs.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
            PlaylistRow(Album(pl.id, pl.name, "", "", songs.size.toString()), cover?.let { artModel(it) },
                onLongClick = { menu(pl) }) { ui.openMine = pl.id }
        }) { i, mod, compact ->
            val pl = some[i]
            val songs = pl.tracks
            val cover = songs.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
            Tile(cover?.let { artModel(it, tilePx(compact)) }, pl.name, "${songs.size} songs", mod, compact, ico = Ico.PLAYLIST,
                onLongClick = { menu(pl) }) { ui.openMine = pl.id }
        }
        lay(shown.filter { it.discovery == null })
        // The discovery playlists: folded away under one row until you open it.
        val disc = shown.filter { it.discovery != null }
        if (disc.isNotEmpty()) {
            item(key = "disc-fold") {
                ActionRow("${if (Music.showDiscoveryLists) "▾" else "▸"}  Discovery playlists (${disc.size})") {
                    Music.showDiscoveryLists = !Music.showDiscoveryLists
                }
            }
            if (Music.showDiscoveryLists) lay(disc)
        }
        return
    }
}

/** One of your playlists, open (from Playlists, Pinned or Discovery). */
private fun androidx.compose.foundation.lazy.LazyListScope.minePlaylist(ui: Ui, open: org.omarchy.music.Playlist) {
    item { BackRow(open.name) { ui.openMine = null } }
    val sorted = Music.sortedPlaylist(open.tracks)
    item {
        ListActions(sorted, onDj = { ui.djFrom(Dj.From("playlist", id = open.id)) }, extra = listOfNotNull(
            "Rename" to { ui.askName = Triple("Rename playlist", open.name) { n -> Music.renamePlaylist(open.id, n) } },
            "Delete" to { ui.confirm = "Delete the playlist “${open.name}”? The songs stay where they are." to { Music.deletePlaylist(open.id); ui.openMine = null } },
            "Export" to { ui.exportFor = open.name to sorted },
        ), pin = org.omarchy.music.Pin("mine", open.id, open.name))
    }
    if (sorted.isEmpty()) item { Empty("No songs in it yet", "Long-press a song anywhere and pick Add to playlist.") }
    else { item { PlaylistSortChips() }; tracks(sorted, ui, "mine", playlistId = open.id) }
}

@Composable
fun QueueTab(ui: Ui) {
    val q = Music.shownQueue()
    val idx = Music.shownIndex()
    LazyColumn(Modifier.fillMaxSize()) {
        if (!Remote.active) item { QueueHeader(ui) }
        if (q.isEmpty() || idx < 0) { item { Empty("Nothing queued", if (Remote.active) "Nothing is playing on the desktop. Play a song here and it plays there." else "Play a song, or long-press one and pick Add to the queue.") }; return@LazyColumn }
        item { Section("NOW PLAYING") }
        item { TrackRow(q[idx], RowPlace(q, idx, "queue", queueIndex = idx), ui) { } }
        val rest = (idx + 1 until q.size).toList()
        val mine = rest.filter { q[it].queued }
        val next = rest.filter { !q[it].queued }
        if (mine.isNotEmpty()) {
            item { Section("QUEUED BY YOU") }
            items(mine.size, key = { "q${mine[it]}" }) { j ->
                val i = mine[j]
                TrackRow(q[i], RowPlace(q, i, "queue", queueIndex = i), ui) { Music.playIndex(i) }
            }
        }
        if (next.isNotEmpty()) {
            item { Section(if (Music.shownRadio()) "UP NEXT · RADIO" else if (Music.shownDj().on) "UP NEXT · DJ" else "UP NEXT") }
            items(next.size, key = { "n${next[it]}" }) { j ->
                val i = next[j]
                TrackRow(q[i], RowPlace(q, i, "queue", queueIndex = i), ui) { Music.playIndex(i) }
            }
        }
    }
}

@Composable
fun PageScreen(ui: Ui, page: Ui.Page) {
    when (page) {
        is Ui.Page.Artist -> ArtistPage(ui, page)
        is Ui.Page.AlbumPage -> AlbumPage(ui, page)
        is Ui.Page.Songs -> SongsPage(ui, page)
    }
}

/** An artist's picture: one picked or found (Google, Genius, Wikipedia), else what a service has. */
fun artistArt(name: String, fallback: String = ""): Any? =
    org.omarchy.music.Pictures.known(name)?.image?.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("/")) java.io.File(it) else it }
        ?: fallback.ifEmpty { null }

@Composable
fun RoundArt(model: Any?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(C.light), contentAlignment = Alignment.Center) {
        if (model != null) coil.compose.AsyncImage(model = model, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(size))
        else Text("♪", style = T.title.copy(color = C.muted))
    }
}

/** Artists: the ones you listen to most, from every play added up ([org.omarchy.music.Stats]). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ArtistsTab(ui: Ui) {
    LaunchedEffect(Unit) { org.omarchy.music.Stats.refresh() }
    val top = org.omarchy.music.Stats.artists.take(120)
    @Suppress("UNUSED_VARIABLE") val v = Music.artistsVersion   // redraw as pictures come in
    LaunchedEffect(top) { Music.lookUpArtists(top.map { it.name }) }
    if (top.isEmpty()) {
        Empty(if (org.omarchy.music.Stats.busy) "Working your plays out…" else "No plays yet",
            "Connect Last.fm, import your Spotify history, or play a few songs: your most played artists show here.")
        return
    }
    val shown = top.filter { fits(ui, it.name) }
    fun menu(name: String, thumb: String) { ui.choices = name to listOf(pinChoice(org.omarchy.music.Pin("artist", name, name, thumb = thumb))) }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Section((listOf("YOUR TOP ARTISTS") + org.omarchy.music.Stats.sources.joinToString(" + ").uppercase().ifEmpty { null })
                .filterNotNull().joinToString(" · "))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) { Chip("Collage…", false) { ui.collageFor = "artists-all" } }
        }
        laidOutItems(shown, ui, key = { "ar-" + shown[it].name }, row = { i ->
            val a = shown[i]
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(R.card)
                .combinedClickable(onClick = { ui.pages += Ui.Page.Artist(a.name) }, onLongClick = { menu(a.name, a.thumb) })
                .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundArt(artistArt(a.name, a.thumb), 52.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(a.name, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plays(a.plays), style = T.sub, maxLines = 1)
                }
            }
        }) { i, mod, compact ->
            val a = shown[i]
            Tile(artistArt(a.name, a.thumb), a.name, plays(a.plays), mod, compact, round = true, ico = Ico.PERSON,
                onLongClick = { menu(a.name, a.thumb) }) { ui.pages += Ui.Page.Artist(a.name) }
        }
    }
}

@Composable
private fun ArtistPage(ui: Ui, page: Ui.Page.Artist) {
    var info by remember(page) { mutableStateOf<ArtistInfo?>(null) }
    var failed by remember(page) { mutableStateOf<String?>(null) }
    var down by remember(page) { mutableStateOf(true) }
    var morePics by remember(page) { mutableStateOf(false) }
    LaunchedEffect(page) {
        Music.lookUpArtists(listOf(page.name))
        Music.bg({ Music.artistInfo(page.name, page.prefer) }) { res, err ->
            info = res
            failed = err?.message ?: if (res == null) "No service knows ${page.name}" else null
        }
    }
    val v = Music.artistsVersion
    val pics = remember(page, v) { org.omarchy.music.Pictures.known(page.name) }
    val all = remember(page, Music.musicVersion) { Music.artistSongs(page.name) }
    val mine = if (down) all.sortedByDescending { it.plays } else all.sortedBy { it.plays }
    val links = androidx.compose.ui.platform.LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackRow(page.name) { ui.pages.removeAt(ui.pages.lastIndex) } }
        val i = info
        item {
            Row(Modifier.padding(12.dp)) {
                RoundArt(artistArt(page.name, i?.thumb ?: ""), 112.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(i?.name ?: page.name, style = T.title.copy(fontSize = T.title.fontSize * 1.3f))
                    val st = org.omarchy.music.Stats.artist(page.name)
                    Text(listOfNotNull(st?.plays?.takeIf { it > 0 }?.let { "%,d plays".format(it) },
                        all.size.takeIf { it > 0 }?.let { "$it song${if (it != 1) "s" else ""}" },
                        i?.let { "from ${SOURCES[it.source]}" } ?: if (failed == null) "looking them up…" else null).joinToString(" · "), style = T.sub)
                    val bio = pics?.bio.orEmpty()
                    if (bio.isNotEmpty()) {
                        Text(bio, style = T.sub, maxLines = if (morePics) Int.MAX_VALUE else 9, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp).clickable { morePics = !morePics })
                        if (pics!!.bioUrl.isNotEmpty()) Text("More on ${pics.bioFrom.ifEmpty { "the web" }}", style = T.sub.copy(color = C.accent),
                            modifier = Modifier.padding(top = 4.dp).clickable { links.openUri(pics.bioUrl) })
                    } else if (pics == null) Text("Looking for their bio…", style = T.sub, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (mine.isNotEmpty()) {
                    Pill("Play") { Music.playFrom(mine, 0, false) }
                    Pill("Shuffle") { Music.playFrom(org.omarchy.music.shuffleSongs(mine), 0, false) }
                    Pill(if (down) "Most played first" else "Least played first", quiet = true) { down = !down }
                }
                PinPill(org.omarchy.music.Pin("artist", page.name, page.name, thumb = i?.thumb ?: "", source = page.prefer ?: ""))
                Pill("Change picture…", quiet = true) { ui.artistPictureFor = page.name }
                Pill("Discovery playlist", quiet = true) { Music.makeDiscovery(page.name) }
            }
        }
        if (mine.isNotEmpty()) {
            item { Section("YOUR SONGS · BY PLAYS " + if (down) "↓" else "↑") }
            tracks(mine, ui, "artist") { t -> "${t.plays} play${if (t.plays != 1) "s" else ""}" }
        }
        when {
            i == null && failed == null -> item { Section("LOADING…") }
            i == null -> if (mine.isEmpty()) item { Empty("Couldn't find them", failed!!) }
            else -> {
                if (i.top.isNotEmpty()) {
                    item { Section("TOP SONGS" + if (i.topSource != i.source) " · ${SOURCES[i.topSource]?.uppercase()}" else "") }
                    tracks(i.top, ui, "artist", radio = true)
                }
                if (i.albums.isNotEmpty()) {
                    item { Section("ALBUMS AND SINGLES") }
                    val albums = i.albums.filter { fits(ui, it.title, it.sub) }
                    fun menu(al: Album) { ui.choices = al.title to listOf(pinChoice(org.omarchy.music.Pin("album", al.id, al.title, al.sub, al.thumb, i.source))) }
                    laidOutItems(albums, ui, key = { "aa-" + albums[it].id + "#" + it }, row = { j ->
                        PlaylistRow(albums[j], onLongClick = { menu(albums[j]) }) { ui.pages += Ui.Page.AlbumPage(albums[j], i.source) }
                    }) { j, mod, compact ->
                        val al = albums[j]
                        Tile(al.thumb.ifEmpty { null }, al.title, al.sub, mod, compact, ico = Ico.DISC, onLongClick = { menu(al) }) {
                            ui.pages += Ui.Page.AlbumPage(al, i.source)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPage(ui: Ui, page: Ui.Page.AlbumPage) {
    var tracks by remember(page) { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(page) {
        if (page.tracks != null) { tracks = page.tracks; return@LaunchedEffect }
        val src = Music.sources[page.source]
        Music.bg({ src?.album(page.album.id) ?: emptyList() }) { res, err ->
            tracks = res ?: emptyList()
            if (err != null) Music.toast("Couldn't open the album: ${err.message?.take(80)}")
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackRow(page.album.title) { ui.pages.removeAt(ui.pages.lastIndex) } }
        item {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(page.album.thumb.ifEmpty { null } ?: page.tracks?.firstOrNull()?.let { artModel(it) }, 96.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(page.album.title, style = T.title.copy(fontSize = T.title.fontSize * 1.2f))
                    if (page.album.sub.isNotEmpty()) Text(page.album.sub, style = T.sub)
                }
            }
        }
        val t = tracks
        if (t == null) item { Section("LOADING…") } else {
            item { ListActions(t, onDj = { ui.djFrom(Dj.From("songs", name = page.album.title, songs = t)) },
                pin = if (page.source.isEmpty()) null else org.omarchy.music.Pin("album", page.album.id, page.album.title, page.album.sub, page.album.thumb, page.source)) }
            tracks(t, ui, "album")
        }
    }
}


// The pages that come with 1.4 (filled in as they're built).
/** New releases: albums, singles and EPs of the artists in your songs, newest first, from the last
 *  Refresh (it looks only when you tap it). */
fun androidx.compose.foundation.lazy.LazyListScope.newReleasesView(ui: Ui) {
    val nr = org.omarchy.music.NewReleases
    item {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (nr.status == null) Pill("Refresh…") {
                ui.choices = "New releases: what the artists with songs on your phone put out in…" to listOf(
                    "The last week" to { nr.scan("week") }, "The last month" to { nr.scan("month") }, "The last year" to { nr.scan("year") })
            } else Pill("Stop", quiet = true) { nr.stop = true }
            if (nr.scanned > 0 && nr.found.isNotEmpty())
                Pill(if (nr.selecting) "Done" else "Select", quiet = true) { nr.selecting = !nr.selecting; if (!nr.selecting) nr.picked.clear() }
        }
        if (nr.selecting) {   // picks kept across Albums, Singles, EPs and Artists
            Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (nr.picked.isEmpty()) "Discovery playlist" else "Discovery playlist (${nr.picked.size})") {
                    if (nr.picked.isEmpty()) Music.toast("Pick some releases or artists first (on any tab)")
                    else { Music.makeReleaseDiscovery(nr.pickedReleases()); nr.picked.clear(); nr.selecting = false }
                }
                if (nr.picked.isNotEmpty()) Pill("Clear", quiet = true) { nr.picked.clear() }
            }
            Section(if (nr.picked.isEmpty()) "PICK RELEASES OR ARTISTS, ON ANY TAB" else "${nr.picked.size} PICKED (ON ANY TAB)")
        }
        nr.status?.let { Section(it.uppercase()) }
    }
    if (nr.scanned <= 0) {
        item { Empty("New releases", "Tap Refresh… to look at every artist with songs on your phone for what they've put out in the last " +
            "week, month or year. Nothing is checked on its own.") }
        return
    }
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("album" to "Albums", "single" to "Singles", "ep" to "EPs", "artists" to "Artists").forEach { (k, l) -> Chip(l, nr.kind == k) { nr.kind = k } }
        }
    }
    val whenText = mapOf("week" to "THE LAST WEEK", "month" to "THE LAST MONTH", "year" to "THE LAST YEAR")[nr.range] ?: ""
    // A release: its own page on Tidal or YouTube Music, straight away (your files aren't looked through).
    fun find(r: org.omarchy.music.NewReleases.Release) {
        if (nr.selecting) { nr.toggle(nr.key(r), r to r.artist); return }
        Music.toast("Opening “${r.title}”…")
        Music.bg({ Music.releaseFind(r) }) { found, err ->
            if (found == null) { Music.toast("Couldn't find “${r.title}” on your services" + (err?.message?.let { ": ${it.take(80)}" } ?: "")); return@bg }
            val (key, al, songs) = found
            ui.pages += if (al != null) Ui.Page.AlbumPage(al, key)
                else Ui.Page.AlbumPage(Album("single-${songs[0].id}", r.title, "${r.artist} · Single", songs[0].thumb.ifEmpty { r.cover }), key, songs)
        }
    }
    if (nr.kind == "artists") {
        val by = nr.found.filter { fits(ui, it.artist, it.title) }.groupBy { it.artist }.toList()
        item { Section("${by.size} ARTISTS WITH NEW MUSIC · $whenText") }
        items(by.size, key = { "nra-${by[it].first}" }) { i ->
            val (artist, rs) = by[i]
            val k = nr.artistKey(artist)
            PlaylistRow(Album(artist, artist, "${rs.size} new · latest ${rs[0].title} (${rs[0].date})", rs[0].cover),
                checked = if (nr.selecting) k in nr.picked else null) {
                if (nr.selecting) nr.toggle(k, null to artist)
                else ui.pages += Ui.Page.Artist(artist, Music.source.takeIf { it == "tidal" || it == "ytm" })
            }
        }
        return
    }
    val rows = nr.found.filter { it.type == nr.kind && fits(ui, it.artist, it.title) }
    val name = mapOf("album" to "ALBUMS", "single" to "SINGLES", "ep" to "EPS")[nr.kind] ?: ""
    item { Section("${rows.size} ${if (rows.size == 1) name.dropLast(1) else name} · $whenText · NEWEST FIRST") }
    if (rows.isEmpty()) item { Empty("None in this time", "Refresh… a longer time, or look at the other tabs.") }
    laidOutItems(rows, ui, key = { "nr-${rows[it].link}-${rows[it].title}-$it" }, row = { i ->
        val r = rows[i]
        PlaylistRow(Album(r.link, r.title, "${r.artist} · ${r.date}", r.cover), checked = if (nr.selecting) nr.key(r) in nr.picked else null) { find(r) }
    }) { i, mod, compact ->
        val r = rows[i]
        val on = nr.selecting && nr.key(r) in nr.picked
        Tile(r.cover.ifEmpty { null }, r.title, r.artist, mod, compact, extra = r.date, ico = Ico.DISC, lit = on,
            over = { if (nr.selecting) Tick(on, Modifier.align(Alignment.TopEnd).padding(6.dp)) }) { find(r) }
    }
}

/** Discovery: the artists you're discovering, each with its discovery playlist (the songs to hear)
 *  and its discovered one (the ones heard to the end), to review. */
fun androidx.compose.foundation.lazy.LazyListScope.discoveryView(ui: Ui) {
    val open = ui.openMine?.let { Music.lists.get(it) }
    if (open != null) { minePlaylist(ui, open); return }
    Music.musicVersion
    val pairs = Music.discoveryLists()
    if (Music.discovering.isNotEmpty()) item { Section("FINDING SONGS…") }
    if (pairs.isEmpty()) {
        item { Empty("Discovery", "Long-press a song (or select one) and pick “Discovery playlist for…”, or open an artist and tap " +
            "Discovery playlist: every song of theirs you haven't got, album by album. The ones you hear to the end go to " +
            "“… discovered”, to download or let go.") }
        return
    }
    for ((artist, disc, found) in pairs) {
        item(key = "dh-$artist") { Section(artist.uppercase()) }
        listOfNotNull(disc, found).forEach { pl ->
            item(key = "dp-${pl.id}") {
                val cover = pl.tracks.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
                PlaylistRow(Album(pl.id, pl.name, (if (pl === disc) "Songs to hear" else "Heard to the end: review them") +
                    " · ${pl.tracks.size}"), cover?.let { artModel(it) }) { ui.openMine = pl.id }
            }
        }
        if (found != null && found.tracks.isNotEmpty()) item(key = "dr-$artist") {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { Pill("Review…") { ui.reviewFor = found } }
        }
    }
}

/** Repeat: your top 50 of the last week, 100 of the month, 200 of the year. */
fun androidx.compose.foundation.lazy.LazyListScope.repeatView(ui: Ui) {
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("week" to "Week · top 50", "month" to "Month · top 100", "year" to "Year · top 200").forEach { (k, l) ->
                Chip(l, Music.repeatPeriod == k) { Music.repeatPeriod = k }
            }
        }
    }
    val chart = org.omarchy.music.Repeat.chart(Music.repeatPeriod)
    when {
        chart == null -> item { Section("ADDING UP YOUR PLAYS…") }
        chart.isEmpty() -> item { Empty("Nothing played in this time yet", "Play some songs (or connect Last.fm, or import your Spotify or Apple Music history in Settings) and your charts show up here.") }
        else -> {
            val days = org.omarchy.music.Repeat.PERIODS[Music.repeatPeriod]?.first ?: 7
            item { Section("YOUR TOP ${chart.size} · THE LAST $days DAYS") }
            val rank = chart.withIndex().associate { (i, t) -> t.id to i + 1 }
            tracks(chart, ui, "repeat") { t -> "#${rank[t.id] ?: ""} · ${t.plays} play${if (t.plays != 1) "s" else ""}" }
        }
    }
}


/** Most played albums: Most played's songs added up by album; an album opens its songs. */
fun androidx.compose.foundation.lazy.LazyListScope.topAlbumsItems(ui: Ui) {
    val albums = runCatching { Music.topAlbums(Music.topPeriod) }.getOrDefault(emptyList()).filter { fits(ui, it.title, it.artist) }
    item { Section(if (albums.isEmpty()) "NO ALBUMS PLAYED IN THIS TIME" else "${albums.size} ALBUMS · MOST PLAYED FIRST") }
    fun open(al: Music.TopAlbum) {
        ui.pages += Ui.Page.AlbumPage(Album("top-" + al.key, al.title, al.artist, al.tracks.first().thumb), "local", al.tracks)
    }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    laidOutItems(albums, ui, key = { "tal-" + albums[it].key }, row = { i ->
        val al = albums[i]
        PlaylistRow(Album(al.key, al.title, "${al.artist} · ${plays(al.plays)}"), artModel(al.tracks.first())) { open(al) }
    }) { i, mod, compact ->
        val al = albums[i]
        Tile(artModel(al.tracks.first(), tilePx(compact)), al.title, al.artist, mod, compact, extra = plays(al.plays), ico = Ico.DISC) { open(al) }
    }
}


/** Most played artists: every play of theirs over the period; an artist opens their page. */
fun androidx.compose.foundation.lazy.LazyListScope.topArtistsItems(ui: Ui) {
    val artists = runCatching { Music.topArtists(Music.topPeriod) }.getOrDefault(emptyList()).filter { fits(ui, it.name) }
    item { Section(if (artists.isEmpty()) "NO ARTISTS PLAYED IN THIS TIME" else "${artists.size} ARTISTS · MOST PLAYED FIRST") }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    laidOutItems(artists, ui, key = { "tar-" + artists[it].name }, row = { i ->
        val a = artists[i]
        PlaylistRow(Album(a.name, a.name, plays(a.plays)), artistArt(a.name, a.thumb)) { ui.pages += Ui.Page.Artist(a.name) }
    }) { i, mod, compact ->
        val a = artists[i]
        Tile(artistArt(a.name, a.thumb), a.name, plays(a.plays), mod, compact, round = true, ico = Ico.PERSON) { ui.pages += Ui.Page.Artist(a.name) }
    }
}
