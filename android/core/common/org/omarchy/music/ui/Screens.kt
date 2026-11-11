package org.omarchy.music.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.graphicsLayer
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
import org.omarchy.music.rankSongs
import org.omarchy.music.norm

private fun searchName(key: String) = if (key == "local") "your music" else SOURCES[key] ?: ""

@Composable
fun SearchTab(ui: Ui) {
    val res = ui.found
    val where = searchName(ui.foundIn.ifEmpty { Music.searchIn })
    LazyColumn(Modifier.fillMaxSize()) {
        // A new search on its way while the last one's results show: said at the top, so they aren't taken for the new ones
        if (ui.searching && res != null && !res.isEmpty) item {
            Text("Searching $where for “${ui.searched}”…", style = T.sub.copy(color = C.accent), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        when {
            ui.searching && (res == null || res.isEmpty) -> item { Empty("Searching $where…") }   // else what was found stays till the new results come
            res == null -> item { Empty("Search ${searchName(Music.searchIn)}", "Songs, artists or albums: pick where with the menu by the search bar. Or paste a link to a song, an album or a playlist.") }
            res.isEmpty -> item { Empty("Nothing found", "${ui.query}\nin $where") }
            else -> {
                if (res.artists.isNotEmpty()) {
                    item { Section("ARTISTS") }
                    // Their pictures as on the Artists tab (looked up for the ones not known yet)
                    item { androidx.compose.runtime.LaunchedEffect(res) { Music.lookUpArtists(res.artists.map { it.title }) } }
                    items(res.artists.size) { i ->
                        val a = res.artists[i]
                        PlaylistRow(a, artistArt(a.title, a.thumb)) { ui.pages += Ui.Page.Artist(a.title, ui.foundIn.takeIf { it in SOURCES }) }
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
                    tracks(res.songs, ui, "search", radio = res.songs.size > 1 && !ui.resultsAreList) { t ->
                        // Your own songs: which tab of My music they're in
                        when (Music.tabOf(t)) { "album" -> "In Albums"; "ep" -> "In EPs"; "single" -> "In Singles"; else -> "" }
                    }
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
    if (key == "local") {   // your own songs: looked through off the screen's thread, so typing never stutters
        ui.foundIn = key
        ui.searching = true
        Music.bg({ localMatches(Music.mySongs(), q) }) { res, _ ->
            if (seq != ui.searchSeq) return@bg
            ui.searching = false
            ui.found = res ?: Found(emptyList())
        }
        return
    }
    val src = Music.sources[key]
    if (src == null) { if (!quiet) Music.toast("Still connecting to ${SOURCES[key]}…"); return }
    if (!src.signedIn && key in setOf("tidal", "spotify", "apple", "deezer")) { if (!quiet) Music.toast("Connect ${SOURCES[key]} in Settings first"); return }
    ui.foundIn = key
    // Searched a moment ago (typing on, then back): at once
    val cacheKey = "$key|${q.lowercase()}"
    SearchCache.get(cacheKey)?.let { ui.searching = false; ui.found = it; return }
    ui.searching = true
    Music.bg({ if (link != null) Found(src.search(q)) else src.find(q).let { it.copy(songs = rankSongs(it.songs, q)) } }) { res, err ->
        if (res != null && err == null) SearchCache.put(cacheKey, res)
        if (seq != ui.searchSeq) return@bg   // a newer search is on its way
        ui.searching = false
        if (err != null) { ui.found = Found(emptyList()); Music.toast("Search failed: ${err.message?.take(100)}") }
        else ui.found = res
    }
}

/** The last searches' results (for ten minutes), so going back to something you typed shows it at once. */
object SearchCache {
    private val map = object : LinkedHashMap<String, Pair<Long, Found>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Found>>?) = size > 60
    }
    @Synchronized fun get(k: String): Found? = map[k]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.second
    @Synchronized fun put(k: String, f: Found) { map[k] = System.currentTimeMillis() to f }
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
                ActionRow("Open Settings  ›") { ui.openSettings() }
            }
            !src.signedIn -> item {
                Empty("Not signed in", src.hint + if (key == org.omarchy.music.SoundCloud.KEY) "\n\n${org.omarchy.music.SoundCloud.APART}." else "")
                ActionRow("Connect ${SOURCES[key]}  ›") { ui.openSettings("Accounts") }
            }
            open == null && ui.openFolder != null && Music.playlistFolders.get(ui.openFolder!!) != null ->
                folderView(ui, Music.playlistFolders.get(ui.openFolder!!)!!)
            open != null -> {
                item { BackRow(open.first.title) { ui.openList = null } }
                val tracks = open.second?.let { Music.sortedPlaylist(it) }
                if (tracks == null) item { Section("LOADING…") }
                else {
                    item { ListActions(tracks, extra = if (key == org.omarchy.music.SoundCloud.KEY) emptyList()
                        else listOf("Import to my playlists" to { Music.importServicePlaylist(key, open.first) }),
                        onDj = { ui.djFrom(Dj.From("songs", name = open.first.title, songs = tracks)) },
                pin = org.omarchy.music.Pin("service", open.first.id, open.first.title, open.first.sub, open.first.thumb, key)) }
                    if (tracks.isNotEmpty()) item { PlaylistSortChips() }
                    tracks(tracks, ui, "list")
                }
            }
            loaded?.second == null -> item { Empty("Loading your library…") }
            else -> {
                if (key == org.omarchy.music.SoundCloud.KEY) item { Text(org.omarchy.music.SoundCloud.APART + ".", style = T.sub,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                else item { SyncLine() }
                if (LastFmOn()) item { ActionRow("✦  Your ${org.omarchy.music.Wrapped.year()} Wrapped  ›") { ui.sheet = "wrapped" } }
                val lists = loaded.second!!
                item { ActionRow("＋  New folder") { ui.askName = Triple("New folder", "") { n -> Music.newFolder(n) } } }
                if (lists.isNotEmpty() && key != org.omarchy.music.SoundCloud.KEY) item { ActionRow("⇩  Import all ${lists.size} into my playlists") {
                    ui.confirm = ("Import all ${lists.size} of your ${SOURCES[key]} playlists into your playlists here? Their songs stream " +
                        "until you download them: $LONG_PRESS a playlist in Music › Playlists and pick Download. Playlists imported " +
                        "before get just their new songs.") to { Music.importAllServicePlaylists(key, lists) }
                } }
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
                    ui.choices = pl.title to (if (key == org.omarchy.music.SoundCloud.KEY) listOf(pinChoice(org.omarchy.music.Pin("service", pl.id, pl.title, pl.sub, pl.thumb, key)))
                        else listOf(pinChoice(org.omarchy.music.Pin("service", pl.id, pl.title, pl.sub, pl.thumb, key)),
                        "Import to my playlists" to { Music.importServicePlaylist(key, pl) },
                        "Put in a folder…" to { ui.folderFor = FolderItem.service(pl, key) }))
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
    LaunchedEffect(ui.musicView, Music.accountsVersion, SpotifyHistory.count, org.omarchy.music.OtherPlays.count, org.omarchy.music.YouTubeHistory.count, org.omarchy.music.TidalHistory.count) { if (ui.musicView == "top") Stats.refresh() }
    // Smart playlists: their songs worked out again (in the background) as your music, moods and formats change.
    LaunchedEffect(ui.musicView, Music.musicVersion, org.omarchy.music.Moods.version, org.omarchy.music.FlacDepths.version) {
        if (ui.musicView == "smartlists") org.omarchy.music.SmartLists.refresh() }
    // Mood lists: the ones never made are made once, as the tab opens (a mood with no songs yet is tried again next time).
    LaunchedEffect(ui.musicView) { if (ui.musicView == "moodlists") org.omarchy.music.MoodLists.makeMissing() }
    val importer = rememberOpenFile { uri -> if (uri != null) Music.importPlaylist(uri) }
    Music.musicVersion
    Music.savedVersion
    // My music, Favorites, Not favorited and Recently played: songs, as their Sort menu says.
    val songView = ui.musicView in setOf("all", "favorites", "unfavorited", "recent")
    val songTab = "music-" + ui.musicView
    val songOpts = org.omarchy.music.songSorts(withOrder = ui.musicView != "all")
    val songSort = sortNow(songTab, songOpts, if (ui.musicView == "all") Music.musicSort.takeIf { it in songOpts } ?: "title" else "order")
    val songBase = if (!songView) emptyList() else runCatching { when (ui.musicView) {
        "all" -> Music.mySongs()
        "recent" -> Music.plays.recent().map { Music.mineOf(it) }   // your own files first
        "favorites" -> Music.favorites.tracks()
        else -> Music.mySongs().filter { !Music.favorites.has(it) }.sortedBy { norm(it.title) }
    } }.getOrDefault(emptyList())
    val songSorted = rememberSorted(songBase, songSort) { d -> org.omarchy.music.sortTracks(songBase, songSort, d) }
    Column(Modifier.fillMaxSize()) {
        // Which of these is shown is picked from the library's list (Main.kt's categories).
        val state = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            Music.musicVersion   // read here too: the list is built apart from the screen, and must follow a scan
            Music.savedVersion
            if (!granted && ui.musicView == "all") item {
                ActionRow("Allow access to the music on this $DEVICE  ›") { access.ask() }
            } else if (ui.musicView == "all") item {
                val from = if (Music.folders.isEmpty()) "no folders" else Music.folders.joinToString { it.trimEnd('/') }
                ActionRow("From $from  ·  Choose folders  ›") { choosing = true }
            }
            when (ui.musicView) {
                "playlists" -> myPlaylists(ui) { importer.launch(arrayOf("*/*")) }
                "smartlists" -> smartPlaylists(ui)
                "moodlists" -> moodLists(ui)
                "dj" -> djView(ui, ask) { ask = it }
                "dupes" -> dupesView(ui)
                "pinned" -> pinnedView(ui)
                "newreleases" -> newReleasesView(ui)
                "discovery" -> discoveryView(ui)
                "repeat" -> repeatView(ui)
                else -> {
                    // Your own copy of each song (its file's cover and tags), not the streamed copy the count was made with
                    val skipRows = if (ui.musicView == "skips") Music.songStats.rows(Music.skipsSince())
                        .map { (t, k, f) -> Triple(Music.mineOf(t), k, f) } else emptyList()
                    val skipInfo = skipRows.associate { (t, k, f) -> org.omarchy.music.songKey(t) to (k to f) }
                    val tracks = when (ui.musicView) {
                        "all", "recent", "favorites", "unfavorited" -> songSorted
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
                            "favorites" -> "No favorites yet" to "${LONG_PRESS.cap()} a song (or select a few) and pick Favorite, or $TAP the ☆ by the song playing."
                            "unfavorited" -> "Every song is a favorite" to "Your songs you haven't starred show up here."
                            "skips" -> "No skips counted yet" to "Go on to the next song with more than ${org.omarchy.music.SongStats.SKIP_LEFT} seconds left and it counts as a skip; hear it to the end and it counts as heard out. Last.fm keeps no skips, so this starts now."
                            else -> "Nothing played yet" to "The songs you play most show up here."
                        }
                        Empty(t, b)
                    } else {
                        item {
                            if (ui.musicView == "all") {
                                // My music: play it all, shuffled, or as a DJ set
                                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Pill("Play") { Music.playList(tracks) }
                                    Pill("Shuffle") { Music.playList(tracks, shuffled = true) }
                                    Pill("DJ", quiet = true) { ui.djFrom(org.omarchy.music.Dj.From("mine")) }
                                }
                                val dl = Music.lib.items.size
                                Section("${tracks.size} SONGS · $dl DOWNLOADED · ${fmtTotal(tracks).uppercase()}")
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SortMenu(songTab, songOpts, songSort)
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
                                "recent" -> "RECENTLY PLAYED · THE LAST ${tracks.size} SONG${if (tracks.size != 1) "S" else ""}"
                                "favorites" -> "${tracks.size} FAVORITES"
                                "unfavorited" -> "${tracks.size} SONGS NOT FAVORITED"
                                else -> if (Music.mostPlayedFromStats) "MOST PLAYED · STATS" else "MOST PLAYED"
                            })
                            if (ui.musicView == "recent") Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Pill("Refresh", quiet = true) { Music.musicVersion++; Music.toast("Recently played is up to date") }
                                SortMenu(songTab, songOpts, songSort, names = mapOf("order" to "Last played first"))
                            }
                            if (ui.musicView == "favorites" || ui.musicView == "unfavorited") Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
                                SortMenu(songTab, songOpts, songSort, names = mapOf("order" to if (ui.musicView == "favorites") "Newest favorite first" else "Title (A–Z)"))
                            }
                            if (ui.musicView == "top" && (org.omarchy.music.LastFm.connected || Music.mostPlayedFromStats)) {
                                val st = org.omarchy.music.Stats
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("songs" to "Songs", "albums" to "Albums", "artists" to "Artists", "time_songs" to "Songs time",
                                        "time_albums" to "Albums time", "time_artists" to "Artists time", "moods" to "Moods").forEach { (k, l) -> Chip(l, Music.topKind == k) { Music.chooseTopKind(k) } }
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
                                    Chip("Only songs on this $DEVICE", Music.topMineOnly) { Music.chooseTopMineOnly(!Music.topMineOnly) }
                                    if (!Music.topKind.startsWith("time") && Music.topKind != "moods") Chip("Collage…", false) { ui.collageFor = Music.topKind }
                                    if (Music.topKind != "moods") Chip("Share card…", false) { ui.cardFor = Music.topKind }
                                }
                                Milestones()
                            }
                        }
                        if (ui.musicView == "top" && Music.topKind.startsWith("time")) timeItems(ui, Music.topKind.removePrefix("time_"))   // listening time
                        else if (ui.musicView == "top" && Music.topKind == "moods") moodPlaysItems(ui)   // plays by your songs' moods
                        else if (ui.musicView == "top" && Music.topKind == "albums") topAlbumsItems(ui)   // the songs above, added up by album
                        else if (ui.musicView == "top" && Music.topKind == "artists") topArtistsItems(ui)   // every play of theirs
                        else if (ui.musicView == "all" && songSort in setOf("artist", "album") && layoutNow(ui) == "list" && ui.filter.isNullOrBlank()) grouped(tracks, ui, songSort == "artist")
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
                val picks = groups.map { g -> g to g.filterIndexed { i, t -> ui.dupeRemove[t.id] ?: (i != 0) }.map { it.id }.toSet() }
                val n = picks.sumOf { (g, rm) -> if (rm.size >= g.size) g.size - 1 else rm.size }
                if (n > 0) ActionRow("Remove the $n cop${if (n != 1) "ies" else "y"} marked Remove  ›") {
                    ui.confirm = ("Remove $n duplicate cop${if (n != 1) "ies" else "y"}? In each song the copies marked Keep stay, and your " +
                        "playlists get them in place of the ones removed (a song with every copy marked keeps its best)." +
                        if (onPhone) " Android asks you once more, and the files go to its trash." else " Files go to the Recycle Bin.") to { Music.removeDupes(picks) }
                }
            }
            groups.forEach { g ->
                item(key = "dupe-head-${g[0].id}") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Section(g[0].title.uppercase() + " · " + g[0].artist.uppercase()) }
                        Pill("Not a duplicate", quiet = true) { Music.markNotDupe(g) }
                        Spacer(Modifier.width(10.dp))
                    }
                }
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
                            val rm = ui.dupeRemove[t.id] ?: (i != 0)
                            Chip("Keep", !rm) { ui.dupeRemove[t.id] = false }
                            Spacer(Modifier.width(4.dp))
                            Chip("Remove", rm) { ui.dupeRemove[t.id] = true }
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
            Text("DJ" + (Dj.kindName(d.kind)?.let { " · ${it.uppercase()}" } ?: ""), style = T.section)
            Text(d.line.ifEmpty {
                "Your own DJ, made from everything you've played: Last.fm, your Spotify, Apple Music, Deezer, YouTube Music " +
                    "and Tidal imports, and the plays here. Sets of a few songs at a time: what you've had on repeat, what " +
                    "you play at this hour, the mood you've been in, throwbacks, favourites, songs you haven't heard in a while, deep cuts, artists " +
                    "like yours and new songs from Tidal and YouTube Music. Skips are only counted, never used to leave songs out. Switch " +
                    "it up for something else, or ask for an artist, a mood, a genre, a decade or a vibe."
            }, style = if (d.line.isNotEmpty()) T.title else T.body)
            // What the DJ knows: every source and stat it uses (Wrapped.DJ_KNOWS), folded away till asked for
            var knows by remember { mutableStateOf(false) }
            Text((if (knows) "▾  " else "▸  ") + "What the DJ knows", style = T.sub.copy(color = C.accent),
                modifier = Modifier.clickable { knows = !knows }.padding(vertical = 2.dp))
            if (knows) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                org.omarchy.music.Wrapped.DJ_KNOWS.forEach { (head, what) ->
                    Text(head, style = T.title, modifier = Modifier.padding(top = 6.dp))
                    Text(what, style = T.sub)
                }
            }
            if (d.fact.isNotEmpty()) Text(d.fact, style = T.sub.copy(color = C.accent))
            if (d.busy.isNotEmpty()) Text(d.busy, style = T.sub)
            Box {
                var menu by remember { mutableStateOf(false) }
                // Choose a folder… (the PC): any folder, even outside your music folders, its songs read for the DJ
                val folderPick = rememberFolderPick { path ->
                    if (path == null) return@rememberFolderPick
                    Music.bg({
                        java.io.File(path).walkTopDown().filter { it.isFile }.take(5000)
                            .mapNotNull { f -> Music.local.trackOf(f.path) }.toList()
                    }) { songs, _ ->
                        if (songs.isNullOrEmpty()) Music.toast("No songs in that folder")
                        else ui.djFrom(Dj.From("songs", name = java.io.File(path).name, songs = songs))
                    }
                }
                Pill("From: ${d.from.ifEmpty { "everything" }}  ▾", quiet = true) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val pick = { f: Dj.From -> menu = false; ui.djFrom(f) }
                    if (folderPick != null) DropdownMenuItem(text = { Text("Choose a folder…", style = T.body) }, onClick = { menu = false; folderPick() })
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
                    // Smart playlists, with the songs they have now (worked out once Smart playlists has been opened)
                    Music.lists.smart().forEach { pl -> org.omarchy.music.SmartLists.songs(pl)?.takeIf { it.isNotEmpty() }
                        ?.let { item("${pl.name} · ${it.size}", Dj.From("songs", name = pl.name, songs = it)) } }
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
            if (org.omarchy.music.Voice.canSpeak) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (Music.djVoice) "Voice on: the DJ talks between sets" else "Voice off: the DJ's lines show only", Music.djVoice) {
                    Music.chooseDjVoice(!Music.djVoice) }
                if (Music.djTalking) Chip("Skip the talk", false) { Music.djHush(); Music.togglePause() }
            }
            // The DJ's voice: the PC's natural voice (Kokoro), the voices to pick, an ElevenLabs key (ui/DjVoice.kt)
            DjVoiceLines(ui)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (d.on) {
                    Pill("↻  Switch it up") { Music.djSwitch() }
                    Pill("Stop the DJ", quiet = true) { Music.djStop() }
                } else Pill("▶  Start the DJ") { Music.djStart() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).border(1.dp, C.line).background(C.fg.copy(alpha = 0.04f)).padding(horizontal = 10.dp, vertical = 10.dp)) {
                if (ask.isEmpty()) Text("Ask the DJ: something chill, 2010s, house, Daft Punk…", style = T.body.copy(color = C.muted), maxLines = 1)
                BasicTextField(ask, setAsk, singleLine = true, textStyle = T.body, cursorBrush = SolidColor(C.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (ask.isNotBlank()) { focus.clearFocus(); Music.djSwitch(ask); setAsk("") }
                    }),
                    modifier = Modifier.fillMaxWidth().typing())
            }
            MicButton { said -> focus.clearFocus(); Music.djSwitch(said); setAsk("") }   // ask out loud, like Spotify's DJ
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
        TrackRow(q[i], RowPlace(q, i, "queue", queueIndex = i), ui, Dj.kindName(d.kinds[q[i].dj.toString()]) ?: "") { Music.playIndex(i) }
    }
}

private fun leadArtist(t: Track) = t.artist.split(",")[0].trim().ifEmpty { "Unknown artist" }

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
internal fun androidx.compose.foundation.lazy.LazyListScope.folderRows(ui: Ui) {
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
internal fun androidx.compose.foundation.lazy.LazyListScope.folderView(ui: Ui, f: org.omarchy.music.PlaylistFolder) {
    item { BackRow(f.name) { ui.openFolder = null } }
    item {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PinPill(org.omarchy.music.Pin("listfolder", f.id, f.name))
            Pill("Rename", quiet = true) { ui.askName = Triple("Rename folder", f.name) { n -> Music.renameFolder(f.id, n) } }
            Pill("Delete", quiet = true) { ui.confirm = "Delete the folder “${f.name}”? Its playlists stay where they are." to { Music.deleteFolder(f.id); ui.openFolder = null } }
        }
    }
    val items = f.playlists.filter { it.kind != "mine" || Music.lists.get(it.id) != null }   // yours deleted since: gone
    if (items.isEmpty()) { item { Empty("Nothing in it yet", "${LONG_PRESS.cap()} a playlist and pick this folder.") }; return }
    item { Section("${items.size} PLAYLIST${if (items.size != 1) "S" else ""}") }
    items(items.size, key = { "fi-${items[it].key}" }) { i ->
        val it = items[i]
        val mine = if (it.kind == "mine") Music.lists.get(it.id) else null
        val sub = if (mine?.smart != null) "smart" else if (mine != null) "yours" else "Playlist · ${SOURCES[it.source] ?: it.source}"
        val songs = if (mine?.smart != null) org.omarchy.music.SmartLists.songs(mine) else mine?.tracks   // a smart one's: as last worked out
        PlaylistRow(Album(it.id, mine?.name ?: it.title, sub, it.thumb, songs?.size?.toString() ?: ""),
            songs?.firstOrNull { t -> t.thumb.isNotEmpty() || t.isFile }?.let { t -> artModel(t) },
            onLongClick = { ui.confirm = "Take “${mine?.name ?: it.title}” out of “${f.name}”?" to { Music.removeFromFolder(f.id, it) } }) {
            ui.openFolder = null
            if (mine != null) { ui.tab = "music"; ui.musicView = if (mine.smart != null) "smartlists" else "playlists"; ui.openMine = mine.id; return@PlaylistRow }
            openServicePlaylist(ui, it.id, it.title, it.thumb, it.source)
        }
    }
}

/** Artists' view: "View: Most plays ▾", a tap lists Most plays and Folders. */
@Composable
private fun ArtistsViewMenu(view: String, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val names = linkedMapOf("plays" to "Most plays", "folders" to "Folders")
    Box {
        Chip("View: ${names[view] ?: view} ▾", false) { open = true }
        androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.light) {
            names.forEach { (k, l) ->
                androidx.compose.material3.DropdownMenuItem({ Text((if (k == view) "✓  " else "     ") + l, style = T.body) }, { open = false; pick(k) })
            }
        }
    }
}

/** Your playlists' sorts (none by plays: the list is built apart from the screen). */
internal val PLAYLISTS_SORTS = listOf("order", "name", "name-desc", "created", "created-old", "updated", "songs", "songs-least", "length", "length-short")

/** Artists' sorts: your most played first, or any of these. */
private val ARTIST_SORTS = listOf("plays", "plays-least", "name", "name-desc", "played", "played-old", "added", "added-old",
    "songs", "songs-least", "length", "length-short", "random")

/** Chips to sort a playlist's songs by (PLAYLIST_SORTS), the same for every playlist. */
@Composable
internal fun PlaylistSortChips() {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SORT", style = T.section, modifier = Modifier.padding(end = 2.dp))
        PLAYLIST_SORTS.forEach { (k, l) -> Chip(l, Music.playlistSort == k) { Music.pickPlaylistSort(k) } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.myPlaylists(ui: Ui, import: () -> Unit) {
    val open = ui.openMine?.let { Music.lists.get(it) }?.takeIf { it.smart == null }   // smart ones open in Smart playlists
    val folder = ui.openFolder?.let { Music.playlistFolders.get(it) }
    if (open != null) { minePlaylist(ui, open); return }
    if (open == null && folder != null) { folderView(ui, folder); return }
    if (open == null) {
        item { ActionRow("＋  New playlist") { ui.askName = Triple("New playlist", "") { n -> Music.newPlaylist(n) } } }
        // A synced playlist (⇄): the same on the phone and the PC, songs added or taken out on either
        item { ActionRow("⇄  New synced playlist") { ui.askName = Triple("New synced playlist", "") { n -> Music.newPlaylist(n, synced = true) } } }
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
            Text("Make one here, or $LONG_PRESS any song (or select a few) and pick Add to playlist.", style = T.sub,
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        val plSort = sortNow("playlists", PLAYLISTS_SORTS)
        val shown = org.omarchy.music.sortGroups(lists.filter { fits(ui, it.name) }, plSort) { pl ->
            org.omarchy.music.GroupFacts(pl.name, songs = pl.tracks, created = pl.created, updated = pl.updated, added = pl.created) }
        val picking = ui.pickingIn("playlists")
        val picked = ui.albumsPicked
        fun menu(pl: org.omarchy.music.Playlist) {
            if (picking) { picked[pl.id] = pl.tracks; return }
            val toGet = pl.tracks.count { !it.isFile && !Music.lib.has(it.id) }
            ui.choices = pl.name to listOfNotNull(pinChoice(org.omarchy.music.Pin("mine", pl.id, pl.name)),
                ("Download ($toGet song${if (toGet != 1) "s" else ""} not on this $DEVICE)" to { Music.downloadPlaylists(listOf(pl.id)) }).takeIf { toGet > 0 },
                "Put in a folder…" to { ui.folderFor = FolderItem.mine(pl) },
                (if (pl.synced == true) "Stop syncing" else "Sync with phone and PC") to { Music.setPlaylistSynced(pl.id, pl.synced != true) },
                "Select" to { ui.startPick("playlists", pl.id, pl.tracks) })
        }
        fun tapList(pl: org.omarchy.music.Playlist) {
            if (picking) { if (pl.id in picked) picked.remove(pl.id) else picked[pl.id] = pl.tracks; return }
            ui.openMine = pl.id
        }
        item {
            if (picking) PickBar(ui, "playlists", listOf(
                "Download" to { Music.downloadPlaylists(picked.keys.toList()) },
                "Delete…" to {
                    val ids = picked.keys.toList()
                    ui.confirm = ("Delete ${ids.size} playlist${if (ids.size != 1) "s" else ""}? " +
                        ids.mapNotNull { Music.lists.get(it)?.name }.joinToString(", ") { "“$it”" } + ". The songs stay in your music.") to {
                        ids.forEach { Music.deletePlaylist(it) }; picked.clear(); ui.pickingAlbums = false }
                },
                "Rename…" to {
                    fun next(ids: List<String>) {
                        val pl = ids.firstOrNull()?.let { Music.lists.get(it) } ?: return
                        ui.askName = Triple("Rename playlist", pl.name) { n -> Music.renamePlaylist(pl.id, n); next(ids.drop(1)) }
                    }
                    next(picked.keys.toList())
                },
                "Edit" to { picked.keys.firstOrNull()?.let { id -> picked.clear(); ui.pickingAlbums = false; ui.openMine = id } },
                "Favorite" to { Music.setFavorite(picked.values.flatten().distinctBy { it.id }, true) },
            ))
            else if (lists.isNotEmpty()) Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Select", false) { ui.startPick("playlists") }
                SortMenu("playlists", PLAYLISTS_SORTS, names = mapOf("order" to "Your order"))
            }
        }
        fun lay(some: List<org.omarchy.music.Playlist>) = laidOutItems(some, ui, key = { "pl-" + some[it].id }, row = { i ->
            val pl = some[i]
            val songs = pl.tracks
            val cover = songs.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
            PlaylistRow(Album(pl.id, pl.shown, "", "", songs.size.toString()), cover?.let { artModel(it) },
                onLongClick = { menu(pl) }, checked = if (picking) pl.id in picked else null) { tapList(pl) }
        }) { i, mod, compact ->
            val pl = some[i]
            val songs = pl.tracks
            val cover = songs.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
            Tile(cover?.let { artModel(it, tilePx(compact)) }, pl.shown, "${songs.size} songs", mod, compact, ico = Ico.PLAYLIST, lit = picking && pl.id in picked,
                onLongClick = { menu(pl) }) { tapList(pl) }
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
    item { BackRow(open.shown) { ui.openMine = null } }
    val sorted = Music.sortedPlaylist(open.tracks)
    item {
        ListActions(sorted, onDj = { ui.djFrom(Dj.From("playlist", id = open.id)) }, extra = listOfNotNull(
            "Rename" to { ui.askName = Triple("Rename playlist", open.name) { n -> Music.renamePlaylist(open.id, n) } },
            (if (open.synced == true) "Stop syncing" else "Sync with phone and PC") to { Music.setPlaylistSynced(open.id, open.synced != true) },
            // A discovered playlist: review just its songs (download them or let them go).
            if (open.discovery?.role == "discovered" && open.tracks.isNotEmpty()) "Review" to { ui.reviewFor = open } else null,
            "Delete" to { ui.confirm = "Delete the playlist “${open.name}”? The songs stay where they are." to { Music.deletePlaylist(open.id); ui.openMine = null } },
            "Export" to { ui.exportFor = open.name to sorted },
        ), pin = org.omarchy.music.Pin("mine", open.id, open.name))
    }
    if (sorted.isEmpty()) item { Empty("No songs in it yet", "${LONG_PRESS.cap()} a song anywhere and pick Add to playlist.") }
    else { item { PlaylistSortChips() }; tracks(sorted, ui, "mine", playlistId = open.id) }
}

/** In the DJ's queue only (the DJ on and its song playing): what it said last, the set's category (this song's) and the
 *  stat it gave, as on the DJ tab. Nothing in any other queue. */
internal fun djQueueOn(): Boolean {
    val d = Music.shownDj()
    val q = Music.shownQueue(); val idx = Music.shownIndex()
    return d.on && idx in q.indices && q[idx].dj > 0
}

/** The category of a DJ song in the queue (Throwbacks, On repeat…), or "" for a song the DJ didn't pick. */
internal fun djKindOf(t: Track): String = if (t.dj > 0) Dj.kindName(Music.shownDj().kinds[t.dj.toString()]) ?: "" else ""

@Composable
internal fun DjQueueNote(text: Color = C.fg, soft: Color = C.muted, accent: Color = C.accent, modifier: Modifier = Modifier) {
    if (!djQueueOn()) return
    val d = Music.shownDj()
    val q = Music.shownQueue(); val idx = Music.shownIndex()
    val kind = djKindOf(q[idx]).ifEmpty { Dj.kindName(d.kind) ?: "" }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("DJ" + (if (kind.isNotEmpty()) " · ${kind.uppercase()}" else ""), style = T.section.copy(color = accent))
        if (d.line.isNotEmpty()) Text(d.line, style = T.title.copy(color = text))
        if (d.fact.isNotEmpty()) Text(d.fact, style = T.sub.copy(color = accent))
    }
}

@Composable
fun QueueTab(ui: Ui) {
    val q = Music.shownQueue()
    val idx = Music.shownIndex()
    LazyColumn(Modifier.fillMaxSize()) {
        if (!Remote.active) item { QueueHeader(ui) }
        if (q.isEmpty() || idx < 0) { item { Empty("Nothing queued", if (Remote.active) "Nothing is playing on the desktop. Play a song here and it plays there." else "Play a song, or $LONG_PRESS one and pick Add to the queue.") }; return@LazyColumn }
        if (djQueueOn()) item("djnote") { DjQueueNote(modifier = Modifier.padding(horizontal = 12.dp)) }
        item { Section("NOW PLAYING") }
        item { TrackRow(q[idx], RowPlace(q, idx, "queue", queueIndex = idx), ui, djKindOf(q[idx])) { } }
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
                TrackRow(q[i], RowPlace(q, i, "queue", queueIndex = i), ui, if (djQueueOn()) djKindOf(q[i]) else "") { Music.playIndex(i) }
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
/** A long-press on an artist (Most played, Artists): Pin, a picture of them to pick, or Merge into…. */
fun artistMenu(ui: Ui, name: String, thumb: String) {
    ui.choices = name to listOf(pinChoice(org.omarchy.music.Pin("artist", name, name, thumb = thumb)),
        "Change picture…" to { ui.artistPictureFor = name }, "Merge into…" to { ui.mergeArtist = name })
}

fun artistArt(name: String, fallback: String = ""): Any? =
    Music.artistsVersion.let { org.omarchy.music.Pictures.known(name) }   // (read: drawn again as pictures come in)
        ?.let { a -> a.saved.takeIf { it.isNotEmpty() && java.io.File(it).exists() }?.let { java.io.File(it) }   // kept on the phone
            ?: a.image.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("/")) java.io.File(it) else it } }
        ?: fallback.ifEmpty { null }

@Composable
fun RoundArt(model: Any?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(C.light), contentAlignment = Alignment.Center) {
        if (model != null) AsyncImage(model = model, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(size))
        else Text("♪", style = T.title.copy(color = C.muted))
    }
}

/** Artists: the ones you listen to most, from every play added up ([org.omarchy.music.Stats]). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ArtistsTab(ui: Ui) {
    // View: Most plays (your artists, most played first, with their pictures) or Folders (your artist folders)
    var view by remember { mutableStateOf(org.omarchy.music.Prefs.text("artists_view").ifEmpty { "plays" }) }
    val viewMenu: @Composable () -> Unit = { ArtistsViewMenu(view) { view = it; org.omarchy.music.Prefs.setText("artists_view", it) } }
    if (view == "folders") { ArtistFoldersView(ui, viewMenu); return }
    LaunchedEffect(Unit) { org.omarchy.music.Stats.refresh() }
    val stats = org.omarchy.music.Stats.artists
    // Your most played (120 of them), then every other artist you have songs of: by plays, then name.
    val top = remember(stats, Music.musicVersion, Music.savedVersion, Music.artistPicks) {
        val merged = Music.artistPicks.filterValues { it.isNotEmpty() }   // Merge into…: shown under the one merged into
        val first = stats.take(120)
        val have = first.mapTo(HashSet()) { norm(it.name) }
        val plays = stats.associateBy { norm(it.name) }
        val rest = LinkedHashMap<String, org.omarchy.music.Stats.ArtistPlays>()
        for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList()))
            for (own in org.omarchy.music.ownArtists(t.artist)) {
                val n = merged[org.omarchy.music.pairKey(own)] ?: own
                val k = norm(n)
                if (k.isEmpty() || k in have || k in rest || n == "Unknown artist" || k in setOf("various artists", "various", "va")) continue
                rest[k] = plays[k] ?: org.omarchy.music.Stats.ArtistPlays(n)
            }
        first + rest.values.sortedWith(compareBy({ -it.plays }, { norm(it.name) }))
    }
    @Suppress("UNUSED_VARIABLE") val v = Music.artistsVersion   // redraw as pictures come in
    LaunchedEffect(top) { Music.lookUpArtists(top.map { it.name }) }
    if (top.isEmpty()) {
        Empty(if (org.omarchy.music.Stats.busy) "Working your plays out…" else "No plays yet",
            "Connect Last.fm, import your Spotify history, or play a few songs: your most played artists show here.")
        return
    }
    val sort = sortNow("artists", ARTIST_SORTS)
    val fitting = top.filter { fits(ui, it.name) }
    val shown = rememberSorted(fitting, sort) { d ->
        val own = HashMap<String, MutableList<Track>>()   // each artist's songs here
        if (sort !in setOf("name", "name-desc", "plays", "plays-least", "random"))
            for (t in Music.mySongs()) for (a in org.omarchy.music.ownArtists(t.artist)) own.getOrPut(norm(a)) { ArrayList() }.add(t)
        org.omarchy.music.sortGroups(fitting, sort, d) { a -> org.omarchy.music.GroupFacts(a.name, songs = own[norm(a.name)].orEmpty(), plays = a.plays) }
    }
    val picking = ui.pickingIn("artists")
    val picked = ui.albumsPicked
    fun menu(name: String, thumb: String) {
        if (picking) { picked[name] = Music.artistSongs(name); ui.optionsFor(picked.values.flatten()); return }
        artistMenu(ui, name, thumb)
        ui.choices = ui.choices?.let { (t, l) -> t to (l + listOf("Create artist playlist" to { Music.makeArtistPlaylist(name) },
            "Options…" to { ui.optionsFor(Music.artistSongs(name)) },
            "Select" to { ui.startPick("artists", name, Music.artistSongs(name)) })) }
    }
    fun open(name: String) {
        if (picking) { if (name in picked) picked.remove(name) else picked[name] = Music.artistSongs(name); return }
        ui.pages += Ui.Page.Artist(name)
    }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Section((listOf("${top.size} ARTISTS" + if (sort == "plays") ", YOUR MOST PLAYED FIRST" else "") + org.omarchy.music.Stats.sources.joinToString(" + ").uppercase().ifEmpty { null })
                .filterNotNull().joinToString(" · "))
            if (picking) PickBar(ui, "artists")
            else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                viewMenu()
                Chip("Collage…", false) { ui.collageFor = "artists-all" }
                Chip("Select", false) { ui.startPick("artists") }
                SortMenu("artists", ARTIST_SORTS)
            }
        }
        laidOutItems(shown, ui, key = { "ar-" + shown[it].name }, row = { i ->
            val a = shown[i]
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(R.card)
                .background(if (picking && a.name in picked) C.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                .pressable(onClick = { open(a.name) }, onLongClick = { menu(a.name, a.thumb) })
                .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundArt(artistArt(a.name, a.thumb), 52.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(a.name, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plays(a.plays), style = T.sub, maxLines = 1)
                }
            }
        }) { i, mod, compact ->
            val a = shown[i]
            Tile(artistArt(a.name, a.thumb), a.name, plays(a.plays), mod, compact, round = true, ico = Ico.PERSON, lit = picking && a.name in picked,
                onLongClick = { menu(a.name, a.thumb) }) { open(a.name) }
        }
    }
}

@Composable
private fun ArtistPage(ui: Ui, page: Ui.Page.Artist) {
    var info by remember(page) { mutableStateOf<ArtistInfo?>(null) }
    var failed by remember(page) { mutableStateOf<String?>(null) }
    var morePics by remember(page) { mutableStateOf(false) }
    LaunchedEffect(page) {
        Music.lookUpArtists(listOf(page.name))
        // Their top songs: your own copy of each you have (it plays your file, shows its cover), the service's otherwise.
        Music.bg({ Music.artistInfo(page.name, page.prefer)?.let { it.copy(top = Music.yourCopies(it.top)) } }) { res, err ->
            info = res
            failed = err?.message ?: if (res == null) "No service knows ${page.name}" else null
        }
    }
    val v = Music.artistsVersion
    val pics = remember(page, v) { org.omarchy.music.Pictures.known(page.name) }
    val songs = remember(page, Music.musicVersion) { Music.artistSongs(page.name) }
    // Songs that aren't yours and have no picture (ones only Last.fm or your history knows, demos): their covers looked
    // up one by one (each found shows at once; remembered, never looked up again), else the artist's picture.
    var coverTick by remember(page) { mutableStateOf(0) }
    fun needsCover(t: Track) = t.file == null && !t.isFile && t.thumb.isEmpty() && !Music.lib.has(t.id)
    fun withCover(t: Track): Track = if (!needsCover(t)) t
        else org.omarchy.music.SongCovers.shown(t.title, t.artist).let { if (it.isEmpty()) t else t.copy(thumb = it) }
    LaunchedEffect(songs, info) {
        val todo = (songs + info?.top.orEmpty()).filter { needsCover(it) && !org.omarchy.music.SongCovers.settled(it.title, it.artist) }
            .distinctBy { org.omarchy.music.songPickKey(it.title, it.artist) }.take(200)
        for (t in todo) {   // leaving the page stops it (what was found is kept)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { org.omarchy.music.SongCovers.look(t) } }
            coverTick++
        }
        if (todo.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { org.omarchy.music.SongCovers.flush() } }
    }
    // Where the page's songs and albums come from (the dropdown at the top, remembered): everything (the service's,
    // with your own copy in place of each one you have) or only your music (your downloads and files).
    var from by remember { mutableStateOf(org.omarchy.music.Prefs.text(Music.ARTIST_PAGE_FROM).ifEmpty { "all" }) }
    val onlyMine = from == "mine"
    fun yours(t: Track) = t.isFile || t.file != null || Music.lib.has(t.id)
    val ownAlbums = remember(page, Music.musicVersion) { runCatching { Music.artistOwnAlbums(page.name) }.getOrDefault(emptyList()) }
    val all = remember(songs, coverTick, v, onlyMine) { songs.filter { !onlyMine || yours(it) }.map { withCover(it) } }
    val top = remember(info, coverTick, v, onlyMine) { info?.top.orEmpty().filter { !onlyMine || yours(it) }.map { withCover(it) } }
    // The albums: only yours; or the service's (yours in place of each one you have: its cover, your songs), then
    // yours the service doesn't list.
    val albums = remember(info, ownAlbums, onlyMine) {
        if (onlyMine) ownAlbums.map { ArtistAlbum(it.first, it, streamed = false) }
        else {
            val byBase = ownAlbums.associateBy { org.omarchy.music.albumBase(it.first.title) }
            val streamed = info?.albums.orEmpty().map { ArtistAlbum(it, byBase[org.omarchy.music.albumBase(it.title)], streamed = true) }
            val used = streamed.mapNotNull { it.own?.first?.id }.toSet()
            streamed + ownAlbums.filter { it.first.id !in used }.map { ArtistAlbum(it.first, it, streamed = false) }
        }
    }
    var opening by remember(page) { mutableStateOf(false) }
    /** An album: yours opens your album's page (your files, their covers and tags); the service's is looked up first, and
     *  opens as your album when you have every song on it, else as the service's with your copy of each song you have. */
    fun openAlbum(a: ArtistAlbum) {
        val own = a.own
        val src = info?.source.orEmpty()
        if (!a.streamed || src.isEmpty()) {
            ui.pages += if (own != null) Ui.Page.AlbumPage(own.first, "local", own.second) else Ui.Page.AlbumPage(a.al, src)
            return
        }
        if (opening) return
        opening = true
        Music.bg({
            Music.yourCopies(Music.sources[src]?.album(a.al.id).orEmpty().map { if (it.album.isEmpty()) it.copy(album = a.al.title) else it })
        }) { res, _ ->
            opening = false
            if (ui.pages.lastOrNull() != page) return@bg   // you went somewhere else meanwhile
            val got = res.orEmpty()
            ui.pages += when {
                own != null && got.all { yours(it) } -> Ui.Page.AlbumPage(own.first, "local", own.second)
                got.isNotEmpty() -> Ui.Page.AlbumPage(a.al, src, got)
                else -> Ui.Page.AlbumPage(a.al, src)   // it tries again, and says what went wrong
            }
        }
    }
    fun albumMenu(a: ArtistAlbum) {
        val own = a.own
        val pin = if (a.streamed || own == null) org.omarchy.music.Pin("album", a.al.id, a.al.title, a.al.sub, a.al.thumb, info?.source.orEmpty())
            else org.omarchy.music.Pin("album", own.first.id, own.first.title, own.first.sub, source = "local")
        ui.choices = a.al.title to listOf(pinChoice(pin))
    }
    val sort = Music.artistSort
    val mine = when (sort) {
        "least" -> all.sortedWith(compareBy({ it.plays }, { norm(it.title) }))
        "title" -> all.sortedBy { norm(it.title) }
        "album" -> all.sortedWith(compareBy({ norm(it.album).ifEmpty { "~" } }, { norm(it.title) }))
        "added" -> all.sortedByDescending { it.added }
        else -> all.sortedWith(compareBy({ -it.plays }, { norm(it.title) }))
    }
    val links = androidx.compose.ui.platform.LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackRow(page.name) { ui.pages.removeAt(ui.pages.lastIndex) } }
        item { ArtistFromMenu(from) { from = it; org.omarchy.music.Prefs.setText(Music.ARTIST_PAGE_FROM, it) } }
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
                }
                PinPill(org.omarchy.music.Pin("artist", page.name, page.name, thumb = i?.thumb ?: "", source = page.prefer ?: ""))
                Pill("Change picture…", quiet = true) { ui.artistPictureFor = page.name }
                Pill("Discovery playlist", quiet = true) { Music.makeDiscovery(page.name) }
                Pill("Create artist playlist", quiet = true) { Music.makeArtistPlaylist(page.name) }
                Pill("Merge into…", quiet = true) { ui.mergeArtist = page.name }
            }
        }
        if (mine.isNotEmpty()) {
            item { Section("YOUR SONGS") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("most" to "Most played", "least" to "Least played", "title" to "Title", "album" to "Album", "added" to "Newest")
                        .forEach { (k, l) -> Chip(l, sort == k) { Music.artistSort = k } }
                }
            }
            tracks(mine, ui, "artist") { t -> "${t.plays} play${if (t.plays != 1) "s" else ""}" }
        }
        if (onlyMine && mine.isEmpty() && albums.isEmpty()) item { Empty("None of their music is yours yet",
            "You have no downloads or files of ${page.name}'s. Pick “Everything” at the top to see their songs on the streaming services.") }
        when {
            i == null && failed == null -> if (!onlyMine) item { Section("LOADING…") }
            i == null -> if (!onlyMine && mine.isEmpty() && albums.isEmpty()) item { Empty("Couldn't find them", failed ?: "") }
            i != null && top.isNotEmpty() -> {
                item { Section(if (onlyMine) "YOUR COPIES OF THEIR TOP SONGS"
                    else "TOP SONGS" + if (i.topSource != i.source) " · ${SOURCES[i.topSource]?.uppercase()}" else "") }
                tracks(top, ui, "artist", radio = true, section = "artist-top")
            }
        }
        if (albums.isNotEmpty()) {
            item { Section(if (onlyMine) "YOUR ALBUMS AND SINGLES" else "ALBUMS AND SINGLES") }
            val shown = albums.filter { fits(ui, it.al.title, it.al.sub) }
            laidOutItems(shown, ui, key = { "aa-" + shown[it].al.id + "#" + it }, row = { j ->
                val a = shown[j]
                PlaylistRow(a.al, a.ownCover(), onLongClick = { albumMenu(a) }) { openAlbum(a) }
            }) { j, mod, compact ->
                val a = shown[j]
                Tile(a.ownCover() ?: a.al.thumb.ifEmpty { null }, a.al.title, a.al.sub, mod, compact, ico = Ico.DISC,
                    onLongClick = { albumMenu(a) }) { openAlbum(a) }
            }
        }
    }
}

/** An album on an artist's page: the service's (streamed) or one of yours, with your copy of it (album, songs) if you have one. */
private data class ArtistAlbum(val al: Album, val own: Pair<Album, List<Track>>?, val streamed: Boolean) {
    /** Your copy's cover (the one in its files), or null. */
    fun ownCover(): Any? = own?.second?.firstOrNull()?.let { artModel(it) }
}

/** The dropdown at the top of an artist's page: everything (streaming and yours) or only your music (downloads and files). */
@Composable
private fun ArtistFromMenu(from: String, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val names = linkedMapOf("all" to "Everything (streaming and yours)", "mine" to "Only my music (downloaded and local)")
    Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        Chip("${names[from] ?: "Everything (streaming and yours)"} ▾", false) { open = true }
        DropdownMenu(open, { open = false }, containerColor = C.light) {
            names.forEach { (k, l) ->
                DropdownMenuItem({ Text((if (k == from) "✓  " else "     ") + l, style = T.body) }, { open = false; pick(k) })
            }
        }
    }
}

@Composable
private fun AlbumPage(ui: Ui, page: Ui.Page.AlbumPage) {
    var tracks by remember(page) { mutableStateOf<List<Track>?>(null) }
    // Your own album: its songs you haven't got, at the very bottom (Download missing tracks…).
    var missing by remember(page) { mutableStateOf<List<Music.Missing>>(emptyList()) }
    var picking by remember(page) { mutableStateOf(false) }
    val artist = page.tracks?.firstOrNull()?.artist?.let { org.omarchy.music.splitArtists(it).firstOrNull() ?: it } ?: ""
    // An EP of yours (as the EPs tab sorts it) says EP in its missing section, and is looked for as an EP
    val kind = remember(page) {
        page.tracks?.takeIf { page.source == "local" && it.isNotEmpty() }
            ?.let { runCatching { Music.releaseKind(page.album.title, it, page.album.id.startsWith("single:")) }.getOrNull() } ?: "album"
    }
    LaunchedEffect(page) {
        val mine = page.tracks
        if (page.source == "local" && !mine.isNullOrEmpty())
            Music.bg({ Music.albumMissing(page.album.title, artist, mine, kind) }) { res, _ -> missing = res.orEmpty() }
    }
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
            if (missing.isNotEmpty()) {
                item { Section("MISSING FROM THIS ${if (kind == "ep") "EP" else "ALBUM"} · ${missing.size}") }
                items(missing.size) { i ->
                    val m = missing[i]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).graphicsLayer(alpha = 0.55f),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.n}", style = T.sub, modifier = Modifier.width(28.dp))
                        Text(m.title, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (m.secs > 0) Text(org.omarchy.music.fmtTime(m.secs.toDouble()), style = T.sub)
                    }
                }
                item { Row(Modifier.padding(12.dp)) { Pill("Download missing tracks…") { picking = true } } }
            }
        }
    }
    if (picking) MissingPicker(page.album.title, missing, { picking = false }) { want -> picking = false; Music.downloadMissing(artist, page.album.title, want) }
}

/** Download missing tracks…: each song Download or Leave alone. */
@Composable
private fun MissingPicker(album: String, missing: List<Music.Missing>, close: () -> Unit, go: (List<Music.Missing>) -> Unit) {
    val on = remember(missing) { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>().apply { missing.indices.forEach { put(it, true) } } }
    val box = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.darker, uncheckedColor = C.muted)
    androidx.compose.material3.AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text("Missing from “$album”", style = T.head, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(missing.size) { i ->
                    val m = missing[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.n}  ${m.title}", style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.Checkbox(on[i] == true, { on[i] = true }, colors = box); Text("Download", style = T.time)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.Checkbox(on[i] != true, { on[i] = false }, colors = box); Text("Leave alone", style = T.time)
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({ go(missing.filterIndexed { i, _ -> on[i] == true }) }) { Text("Download", color = C.accent) } },
        dismissButton = { androidx.compose.material3.TextButton(close) { Text("Cancel", color = C.muted) } })
}


// The pages that come with 1.4 (filled in as they're built).
/** New releases: albums, singles and EPs of the artists in your songs, newest first, from the last
 *  Refresh (it looks only when you tap it). */
fun androidx.compose.foundation.lazy.LazyListScope.newReleasesView(ui: Ui) {
    val nr = org.omarchy.music.NewReleases
    item {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (nr.status == null) Pill("Refresh…") {
                ui.choices = "New releases: what the artists with songs on your $DEVICE put out in…" to listOf(
                    "The last week" to { nr.scan("week") }, "The last month" to { nr.scan("month") }, "The last year" to { nr.scan("year") })
            } else Pill("Stop", quiet = true) { nr.stop = true }
            if (nr.scanned > 0 && nr.found.isNotEmpty())
                Pill(if (nr.selecting) "Done" else "Select", quiet = true) { nr.selecting = !nr.selecting; if (!nr.selecting) nr.picked.clear() }
        }
        if (nr.selecting) {   // picks kept across Albums, Singles, EPs and Artists
            Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (nr.picked.isEmpty()) "Discovery playlist" else "Discovery playlist (${nr.picked.size})") {
                    if (nr.picked.isEmpty()) Music.toast("Pick some releases or artists first (on any tab)")
                    else { ui.releasePick = nr.pickedReleases(); nr.picked.clear(); nr.selecting = false }
                }
                if (nr.picked.isNotEmpty()) Pill("Clear", quiet = true) { nr.picked.clear() }
            }
            Section(if (nr.picked.isEmpty()) "PICK RELEASES OR ARTISTS, ON ANY TAB" else "${nr.picked.size} PICKED (ON ANY TAB)")
        }
        nr.status?.let { Section(it.uppercase()) }
    }
    if (nr.scanned <= 0) {
        item { Empty("New releases", "${TAP.cap()} Refresh… to look at every artist with songs on your $DEVICE for what they've put out in the last " +
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
    // Long-press: a discovery playlist of it (its songs picked first), or Select to pick several.
    fun menu(title: String, rs: List<org.omarchy.music.NewReleases.Release>, key: String, pick: Pair<org.omarchy.music.NewReleases.Release?, String>) {
        ui.choices = title to listOfNotNull("Discovery playlist…" to { ui.releasePick = rs },
            if (!nr.selecting) "Select" to { nr.selecting = true; nr.toggle(key, pick) } else null)
    }
    if (nr.kind == "artists") {
        val by = nr.found.filter { fits(ui, it.artist, it.title) }.groupBy { it.artist }.toList()
        item { Section("${by.size} ARTISTS WITH NEW MUSIC · $whenText") }
        items(by.size, key = { "nra-${by[it].first}" }) { i ->
            val (artist, rs) = by[i]
            val k = nr.artistKey(artist)
            PlaylistRow(Album(artist, artist, "${rs.size} new · latest ${rs[0].title} (${rs[0].date})", rs[0].cover),
                onLongClick = { menu(artist, rs, k, null to artist) },
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
        PlaylistRow(Album(r.link, r.title, "${r.artist} · ${r.date}", r.cover), onLongClick = { menu(r.title, listOf(r), nr.key(r), r to r.artist) },
            checked = if (nr.selecting) nr.key(r) in nr.picked else null) { find(r) }
    }) { i, mod, compact ->
        val r = rows[i]
        val on = nr.selecting && nr.key(r) in nr.picked
        Tile(r.cover.ifEmpty { null }, r.title, r.artist, mod, compact, extra = r.date, ico = Ico.DISC, lit = on,
            onLongClick = { menu(r.title, listOf(r), nr.key(r), r to r.artist) },
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
        item { Empty("Discovery", "${LONG_PRESS.cap()} a song (or select one) and pick “Discovery playlist for…”, or open an artist and $TAP " +
            "Discovery playlist: every song of theirs you haven't got, album by album. The ones you hear to the end go to " +
            "“… discovered”, to download or let go.") }
        return
    }
    // Select (hold one, or Select): then tap to pick more, and delete (or download) them together, as in Playlists
    val picking = ui.pickingIn("discovery")
    val picked = ui.albumsPicked
    item(key = "disc-bar") {
        if (picking) PickBar(ui, "discovery playlists", listOf(
            "Delete…" to {
                val ids = picked.keys.toList()
                ui.confirm = ("Delete ${ids.size} discovery playlist${if (ids.size != 1) "s" else ""}? " +
                    ids.mapNotNull { Music.lists.get(it)?.name }.joinToString(", ") { "“$it”" } + ". The songs you have stay in your music.") to {
                    ids.forEach { Music.deletePlaylist(it) }; picked.clear(); ui.pickingAlbums = false }
            },
            "Download" to { Music.downloadPlaylists(picked.keys.toList()) },
            "Select all" to { pairs.forEach { (_, d, f) -> listOfNotNull(d, f).forEach { picked[it.id] = it.tracks } } },
        ))
        else Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) { Chip("Select", false) { ui.startPick("discovery") } }
    }
    for ((artist, disc, found) in pairs) {
        item(key = "dh-$artist") { Section(artist.uppercase()) }
        listOfNotNull(disc, found).forEach { pl ->
            item(key = "dp-${pl.id}") {
                val cover = pl.tracks.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
                PlaylistRow(Album(pl.id, pl.name, (if (pl === disc) "Songs to hear" else "Heard to the end: open it to review them") +
                    " · ${pl.tracks.size}"), cover?.let { artModel(it) },
                    onLongClick = { if (picking) picked[pl.id] = pl.tracks else ui.startPick("discovery", pl.id, pl.tracks) },
                    checked = if (picking) pl.id in picked else null) {
                    if (picking) { if (pl.id in picked) picked.remove(pl.id) else picked[pl.id] = pl.tracks } else ui.openMine = pl.id
                }
            }
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


/** Milestones: the songs and artists that reached 100, 250, 500, 1,000… plays in the last month. */
@Composable
fun Milestones() {
    val now = org.omarchy.music.now().toLong()
    val recent = org.omarchy.music.Stats.periodMilestones(now - 30 * 86400, now + 1, 5)
    if (recent.isEmpty()) return
    Section("MILESTONES")
    recent.forEach { m ->
        val what = if (m.what == "song") "“${m.name}”" + (if (m.artist.isNotEmpty()) " · ${m.artist}" else "") else m.name
        Text("✦  ${"%,d".format(m.plays)} plays of $what · ${java.time.Instant.ofEpochSecond(m.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}",
            style = T.body.copy(color = C.accent), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

/** "312 h", "4 h 20 min", "45 min". */
/** Settings › Last.fm & your stats › Time choice: listening time in "hours" or "minutes". */
object TimeChoice {
    var unit by mutableStateOf(org.omarchy.music.Prefs.text("time_unit", "hours").ifEmpty { "hours" })
        private set
    fun choose(u: String) { unit = u; org.omarchy.music.Prefs.setText("time_unit", u) }
}

fun fmtHours(secs: Long): String {
    val m = (secs + 30) / 60
    if (TimeChoice.unit == "minutes") return "%,d min".format(m)
    return if (m >= 6000) "%,d h".format(m / 60) else if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/** A song or album of listening time: a song of yours (or one to find) for its cover and to play. */
class TimeItem(val title: String, val artist: String, val secs: Long, val tracks: List<Track>, val key: String)

/** Listening time's songs or albums, each with your songs (or one to find), longest first. The desktop's time_items.
 *  Kept until the listening, your music or the choices change: the list redraws often, and this reads all your songs. */
fun timeItemsOf(what: String, t: org.omarchy.music.Stats.Time): List<TimeItem> {
    val stamp = listOf<Any>(what, Music.topPeriod, Music.musicVersion, Music.topMineOnly)
    TimeItemsMemo.get(t, stamp)?.let { return it }
    val out = buildTimeItems(what, t)
    TimeItemsMemo.put(t, stamp, out)
    return out
}

/** The last listening-time list worked out, per kind (songs, albums), and what it was worked out from. */
private object TimeItemsMemo {
    private class Kept(val t: org.omarchy.music.Stats.Time, val stamp: List<Any>, val items: List<TimeItem>)
    private val kept = HashMap<String, Kept>()
    @Synchronized fun get(t: org.omarchy.music.Stats.Time, stamp: List<Any>): List<TimeItem>? =
        kept[stamp[0] as String]?.takeIf { it.t === t && it.stamp == stamp }?.items
    @Synchronized fun put(t: org.omarchy.music.Stats.Time, stamp: List<Any>, items: List<TimeItem>) { kept[stamp[0] as String] = Kept(t, stamp, items) }
}

private fun buildTimeItems(what: String, t: org.omarchy.music.Stats.Time): List<TimeItem> {
    val mine = runCatching { Music.songIndex() }.getOrDefault(emptyMap())
    if (what == "songs") {
        // A song you haven't got: the picture its plays came with (Last.fm's), as Most played › Songs shows.
        val pics = HashMap<Pair<String, String>, String>()
        for (s in Stats.periods[Music.topPeriod].orEmpty() + Stats.songs)
            if (s.thumb.isNotEmpty()) pics.putIfAbsent(Stats.statsKey(s.title, s.artist), s.thumb)
        return t.songs.map { s ->
            val tr = Music.fmSong(s.title, s.artist, pics[Stats.statsKey(s.title, s.artist)].orEmpty(), mine, s.album)
            TimeItem(s.title, s.artist, s.secs, listOf(tr), "s-" + s.title + "|" + s.artist)
        }
    }
    val byKey = runCatching { Music.topAlbums(Music.topPeriod) }.getOrDefault(emptyList()).associateBy { it.key }
    return t.albums.mapIndexed { i, (al, ar, v) ->
        val key = t.albumKeys.getOrElse(i) { "" }
        val tracks = byKey[key]?.tracks?.sortedBy { it.title }
            ?: TopArt.myAlbumSongs(al, ar).take(30).ifEmpty { listOf(Music.fmSong(al, ar, "", emptyMap(), al)) }
        TimeItem(al, ar, v, tracks, key.ifEmpty { al })
    }
}

/** Most played's covers, your own first: your copy of the song or album (the cover in its file, else its folder's
 *  cover.jpg; or a cover you saved or picked) when you have one; only else the online picture, which is kept on disk
 *  once loaded (Coil's disk cache on the phone, CoverCache on the PC), so it's never downloaded again. */
object TopArt {
    private var ver = -1
    private var albums: Map<String, List<Track>> = emptyMap()

    /** Goes up as covers are found online, so the rows showing them redraw. */
    var tick by mutableStateOf(0)
        private set

    private fun albumKey(name: String) = norm(org.omarchy.music.cleanTitle(org.omarchy.music.albumEdition(org.omarchy.music.Wrapped.bareRelease(name)).first))

    /** A song of yours (a file, or a download). */
    fun yours(t: Track) = t.isFile || !t.file.isNullOrEmpty() || Music.lib.has(t.id)

    /** Your copy of the song (same title and artist, as Most played matches) when you have one, else the song. */
    fun song(t: Track): Track = if (yours(t)) t else runCatching { Music.mineOf(t) }.getOrNull()?.takeIf { yours(it) } ?: t

    /** Your songs on the album (its name without "- Single", "(Deluxe)"…, by one of its artists), as Wrapped matches. */
    @Synchronized
    fun myAlbumSongs(title: String, artist: String): List<Track> {
        val v = Music.musicVersion
        if (v != ver) {
            val m = HashMap<String, MutableList<Track>>()
            for (s in runCatching { Music.mySongs() }.getOrDefault(emptyList())) if (s.album.isNotBlank()) m.getOrPut(albumKey(s.album)) { ArrayList() } += s
            albums = m
            ver = v
        }
        val theirs = (org.omarchy.music.splitArtists(artist).map { norm(it) } + norm(artist)).filter { it.isNotEmpty() }.toSet()
        val ak = org.omarchy.music.artistKey(artist)
        return albums[albumKey(title)].orEmpty().filter { c ->
            norm(c.artist) in theirs || org.omarchy.music.splitArtists(c.artist).any { norm(it) in theirs } || org.omarchy.music.artistKey(c.artist) == ak
        }.distinctBy { it.id }
    }

    /** The song to show an album's cover from: one of yours on it, else the played song with a picture. */
    fun album(title: String, artist: String, tracks: List<Track>): Track =
        tracks.firstOrNull { yours(it) }
            ?: myAlbumSongs(title, artist).firstOrNull()
            ?: tracks.firstOrNull { it.thumb.isNotEmpty() }
            ?: tracks.firstOrNull() ?: Track("", "lastfm", title, artist, album = title)

    /** The covers of songs that aren't yours and have no picture, looked up online one by one (each shows as it's
     *  found and is remembered, so never looked up again). Run from a list's LaunchedEffect: leaving the list stops it. */
    suspend fun lookUp(make: () -> List<Track>) {
        val todo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { make() }.getOrDefault(emptyList())
                .filter { !yours(it) && it.thumb.isEmpty() && !org.omarchy.music.SongCovers.settled(it.title, it.artist) }
                .distinctBy { org.omarchy.music.songPickKey(it.title, it.artist) }.take(150)
        }
        for (t in todo) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { org.omarchy.music.SongCovers.look(t) } }
            tick++
        }
        if (todo.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { org.omarchy.music.SongCovers.flush() } }
    }
}

/** A Most played cover (see [TopArt]); drawn again as covers are found online. */
@Composable
fun topArt(t: Track, px: Int = 226): Any? {
    if (TopArt.tick < 0) return null   // (read so a cover found online shows)
    return artModel(t, px)
}

/** Most played › Songs time, Albums time, Artists time: how long you listened in the period and when, then your songs,
 *  albums or artists by listening time, in the grid (or list) as Most played's others, each with its time showing. */
fun androidx.compose.foundation.lazy.LazyListScope.timeItems(ui: Ui, what: String = "artists") {
    val t = org.omarchy.music.Stats.time[Music.topPeriod]
    if (t == null || t.secs <= 0) { item { Section("NO LISTENING IN THIS TIME (OR TAP REFRESH)") }; return }
    item {
        Section("${fmtHours(t.secs)} OF MUSIC")
        Bars("By hour of the day", t.hours, List(24) { if (it % 3 == 0) "$it" else "" })
        Bars("By day of the week", t.days, listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"))
    }
    if (what == "artists") {
        val artists = t.artists.filter { fits(ui, it.first) }
        item {
            Section("${artists.size} ARTISTS · LISTENED TO LONGEST FIRST")
            LaunchedEffect(artists.size, Music.topPeriod) { Music.lookUpArtists(artists.take(400).map { it.first }) }
        }
        laidOutItems(artists, ui, key = { "tta-" + artists[it].first }, row = { i ->
            val (n, v) = artists[i]
            PlaylistRow(Album(n, n, fmtHours(v)), artistArt(n, ""), onLongClick = { artistMenu(ui, n, "") }) { ui.pages += Ui.Page.Artist(n) }
        }) { i, mod, compact ->
            val (n, v) = artists[i]
            Tile(artistArt(n, ""), n, fmtHours(v), mod, compact, round = true, ico = Ico.PERSON, onLongClick = { artistMenu(ui, n, "") }) { ui.pages += Ui.Page.Artist(n) }
        }
        return
    }
    val list = timeItemsOf(what, t).filter { fits(ui, it.title, it.artist) }
    // Each cover from your own copy of the song or album first (see TopArt); only else the online one.
    fun cover(x: TimeItem): Track = if (what == "songs") TopArt.song(x.tracks.first()) else TopArt.album(x.title, x.artist, x.tracks)
    item {
        Section("${list.size} ${if (what == "songs") "SONGS" else "ALBUMS"} · LISTENED TO LONGEST FIRST")
        LaunchedEffect(list.size, Music.topPeriod, what) { TopArt.lookUp { list.map { cover(it) } } }
    }
    fun open(i: Int) {
        val x = list[i]
        if (what == "songs") Music.playFrom(list.map { it.tracks.first() }, i, false)
        else ui.pages += Ui.Page.AlbumPage(Album("time-" + x.key, x.title, x.artist, x.tracks.first().thumb), "local", x.tracks)
    }
    laidOutItems(list, ui, key = { "tt-$what-" + list[it].key }, row = { i ->
        val x = list[i]
        PlaylistRow(Album(x.key, x.title, "${x.artist} · ${fmtHours(x.secs)}"), topArt(cover(x))) { open(i) }
    }) { i, mod, compact ->
        val x = list[i]
        Tile(topArt(cover(x), tilePx(compact)), x.title, x.artist, mod, compact, extra = fmtHours(x.secs),
            ico = if (what == "songs") Ico.NOTE else Ico.DISC) { open(i) }
    }
}

@Composable
private fun Bars(label: String, values: List<Long>, names: List<String>) {
    val top = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = T.sub)
        Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            values.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.fillMaxWidth().height((72 * v / top).toInt().coerceAtLeast(2).dp).clip(R.small).background(C.accent))
                    Text(names[i], style = T.time, maxLines = 1)
                }
            }
        }
    }
}

/** Most played › Moods: your plays over the period added up by your songs' moods (see MoodStats), most played first,
 *  each with its plays and about how long; a mood opens its songs, most played first. */
fun androidx.compose.foundation.lazy.LazyListScope.moodPlaysItems(ui: Ui) {
    val ms = org.omarchy.music.MoodStats
    val period = Music.topPeriod
    val rows = ms.rows[period]
    item {
        LaunchedEffect(period, org.omarchy.music.Stats.songs, org.omarchy.music.Stats.periods, org.omarchy.music.Moods.version, Music.musicVersion) { ms.refresh(period) }
        Section(when {
            rows == null -> "ADDING UP YOUR PLAYS BY MOOD…"
            rows.isEmpty() -> "NO PLAYS OF YOUR SONGS WITH A MOOD IN THIS TIME"
            else -> "%d MOOD%s · MOST PLAYED FIRST".format(rows.size, if (rows.size != 1) "S" else "")
        })
        // A song can be in several moods: its plays count in each (so the moods' plays add up to more than the plays).
        if (!rows.isNullOrEmpty()) Text("A song in several moods counts in each of them.", style = T.sub,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        if (rows != null && rows.isEmpty()) Text("Plays count here once your songs have moods (the Moods tab). Each play is matched to " +
            "your own copy of the song, from all your play history.", style = T.sub, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
    }
    val list = rows.orEmpty().filter { fits(ui, it.mood) }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    fun open(r: org.omarchy.music.MoodStats.Row) {
        // Each song with its plays in the period (shown on its row), most played first.
        val time = mapOf("week" to "this week", "month" to "this month", "year" to "this year")[period] ?: "all time"
        ui.pages += Ui.Page.Songs("${r.mood} · most played $time", r.songs.map { (t, n) -> t.copy(plays = n) }, "mood-plays", r.mood)
    }
    laidOutItems(list, ui, key = { "mpl-" + list[it].mood }, row = { i ->
        val r = list[i]
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(R.card).background(C.light.copy(alpha = 0.6f))
            .clickable { open(r) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            MoodSwatch(r.mood, Modifier.size(42.dp).clip(R.card), 22.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(r.mood, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${plays(r.plays)} · about ${fmtHours(r.secs)} · ${r.songs.size} song${if (r.songs.size != 1) "s" else ""}", style = T.sub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            r.songs.firstOrNull()?.let { (t, _) -> Text(t.title, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.8f)) }
        }
    }) { i, mod, compact ->
        val r = list[i]
        Tile(null, r.mood, plays(r.plays), mod, compact, extra = "about " + fmtHours(r.secs), ico = Ico.MOOD,
            over = { MoodSwatch(r.mood, Modifier.fillMaxSize(), if (compact) 26.dp else 40.dp) }) { open(r) }
    }
}

/** Most played albums: Most played's songs added up by album; an album opens its songs. */
fun androidx.compose.foundation.lazy.LazyListScope.topAlbumsItems(ui: Ui) {
    val albums = runCatching { Music.topAlbums(Music.topPeriod) }.getOrDefault(emptyList()).filter { fits(ui, it.title, it.artist) }
    item {
        Section(if (albums.isEmpty()) "NO ALBUMS PLAYED IN THIS TIME" else "${albums.size} ALBUMS · MOST PLAYED FIRST")
        LaunchedEffect(albums.size, Music.topPeriod) { TopArt.lookUp { albums.map { TopArt.album(it.title, it.artist, it.tracks) } } }
    }
    fun open(al: Music.TopAlbum) {
        ui.pages += Ui.Page.AlbumPage(Album("top-" + al.key, al.title, al.artist, al.tracks.first().thumb), "local", al.tracks)
    }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    laidOutItems(albums, ui, key = { "tal-" + albums[it].key }, row = { i ->
        val al = albums[i]
        PlaylistRow(Album(al.key, al.title, "${al.artist} · ${plays(al.plays)}"), topArt(TopArt.album(al.title, al.artist, al.tracks))) { open(al) }
    }) { i, mod, compact ->
        val al = albums[i]
        Tile(topArt(TopArt.album(al.title, al.artist, al.tracks), tilePx(compact)), al.title, al.artist, mod, compact, extra = plays(al.plays), ico = Ico.DISC) { open(al) }
    }
}


/** Most played artists: every play of theirs over the period; an artist opens their page. */
fun androidx.compose.foundation.lazy.LazyListScope.topArtistsItems(ui: Ui) {
    val artists = runCatching { Music.topArtists(Music.topPeriod) }.getOrDefault(emptyList()).filter { fits(ui, it.name) }
    item {
        Section(if (artists.isEmpty()) "NO ARTISTS PLAYED IN THIS TIME" else "${artists.size} ARTISTS · MOST PLAYED FIRST")
        LaunchedEffect(artists.size, Music.topPeriod) { Music.lookUpArtists(artists.take(400).map { it.name }) }
    }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    laidOutItems(artists, ui, key = { "tar-" + artists[it].name }, row = { i ->
        val a = artists[i]
        PlaylistRow(Album(a.name, a.name, plays(a.plays)), artistArt(a.name, a.thumb), onLongClick = { artistMenu(ui, a.name, a.thumb) }) { ui.pages += Ui.Page.Artist(a.name) }
    }) { i, mod, compact ->
        val a = artists[i]
        Tile(artistArt(a.name, a.thumb), a.name, plays(a.plays), mod, compact, round = true, ico = Ico.PERSON, onLongClick = { artistMenu(ui, a.name, a.thumb) }) { ui.pages += Ui.Page.Artist(a.name) }
    }
}
