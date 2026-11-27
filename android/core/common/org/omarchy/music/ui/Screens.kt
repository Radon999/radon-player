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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import kotlinx.coroutines.launch
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
import androidx.compose.ui.unit.sp
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

private fun searchName(key: String) = if (key == "local") "your music" else if (key == "genius") "Genius" else SOURCES[key] ?: ""

/** Search: the songs of each artist and album you picked whole (hold or right-click its row), by the row's key. */
private val searchPicks = androidx.compose.runtime.mutableStateMapOf<String, List<Track>>()

/** Whether a row's songs are all picked: its tick while you're picking (null: no tick, not picking). */
private fun pickedAll(ui: Ui, key: String): Boolean? =
    if (!ui.selecting) null else searchPicks[key]?.let { s -> s.isNotEmpty() && s.all { it.id in ui.selected } } ?: false

/** An artist's or an album's songs, all picked at once (or, all picked already, all taken out again). songs is
 *  asked off the screen's thread: a service's album or artist has to be fetched. */
private fun pickAll(ui: Ui, key: String, what: String, songs: () -> List<Track>) {
    val had = searchPicks[key]
    if (ui.selecting && had != null && had.isNotEmpty() && had.all { it.id in ui.selected }) { had.forEach { ui.selected.remove(it.id) }; return }
    Music.bg(songs) { got, err ->
        if (got.isNullOrEmpty()) {
            Music.toast(if (err != null) "Couldn't get the songs of $what: ${err.message?.take(80)}" else "No songs found for $what")
            return@bg
        }
        searchPicks[key] = got
        if (!ui.selecting) ui.startSelecting(got, null, null)
        got.forEach { ui.selected[it.id] = it }
        Music.toast("%,d song%s of %s picked".format(got.size, if (got.size != 1) "s" else "", what))
    }
}

@Composable
fun SearchTab(ui: Ui) {
    val res = ui.found
    val where = searchName(ui.foundIn.ifEmpty { Music.searchIn })
    PlaceColumn(Modifier.fillMaxSize()) {
        // A new search on its way while the last one's results show: said at the top, so they aren't taken for the new ones
        if (ui.searching && res != null && !res.isEmpty) item {
            Text("Searching $where for “${ui.searched}”…", style = T.sub.copy(color = C.accent), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        // The Search tab's last ten searches (its own, apart from each page's), while the search bar is empty
        if (ui.query.isBlank()) item { RecentSearches("search") { q -> ui.query = q; ui.pages.clear(); runSearch(ui) } }
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
                        val key = "artist:${ui.foundIn}:${norm(a.title)}"
                        val from = ui.foundIn
                        // Hold (right-click on the PC): every song of theirs picked, as songs are picked. In your music,
                        // all you have of them; on a service, the songs their page lists. Picking: a tap does the same.
                        val pick = { pickAll(ui, key, a.title) {
                            if (from in SOURCES) Music.artistInfo(a.title, from)?.top.orEmpty() else Music.artistSongs(a.title) } }
                        PlaylistRow(a, artistArt(a.title, a.thumb), onLongClick = pick, checked = pickedAll(ui, key)) {
                            if (ui.selecting) pick()
                            else if (from == "genius") {
                                // Genius tells same-named artists apart with a word in brackets ("Hoax (UK)"): the page is
                                // the name's, and with several of that name found, sorted with the one you tapped
                                val name = a.title.replace(Regex("""\s*\([^)]*\)\s*$"""), "").ifBlank { a.title }
                                if (res.artists.count { org.omarchy.music.GeniusRules.sameNamed(name, it.title) } > 1 && a.id.all { it.isDigit() })
                                    org.omarchy.music.GeniusArtists.pick(name, a.id)
                                ui.pages += Ui.Page.Artist(name, null)
                            }
                            else ui.pages += Ui.Page.Artist(a.title, ui.foundIn.takeIf { it in SOURCES }) }
                    }
                }
                if (res.albums.isNotEmpty()) {
                    item { Section(if (ui.foundIn == "genius") "ALBUMS, EPS AND SINGLES" else "ALBUMS") }
                    items(res.albums.size) { i ->
                        val al = res.albums[i]
                        val mine = res.albumTracks[al.id]
                        val key = "album:${ui.foundIn}:${al.id}"
                        val from = ui.foundIn
                        // Hold (right-click): all the album's songs picked; yours when it's one of yours, else the service's
                        val pick = { pickAll(ui, key, al.title) {
                            mine ?: if (from == "genius") org.omarchy.music.GeniusSearch.albumSongs(al) else Music.sources[from]?.album(al.id).orEmpty() } }
                        PlaylistRow(al, mine?.firstOrNull()?.let { artModel(it) }, onLongClick = pick, checked = pickedAll(ui, key)) {
                            if (ui.selecting) pick()
                            else if (from == "genius" && mine == null) {   // Genius has only names: the release's songs are asked for, then it opens
                                val at = ui.pages.size
                                Music.toast("Opening “${al.title}”…")
                                Music.bg({ org.omarchy.music.GeniusSearch.albumSongs(al) }) { got, err ->
                                    if (got.isNullOrEmpty()) Music.toast(if (err != null) "Couldn't open it: ${err.message?.take(80)}" else "Genius lists no songs for “${al.title}”")
                                    else if (ui.tab == "search" && ui.pages.size == at) ui.pages += Ui.Page.AlbumPage(al, "genius", got)
                                }
                            }
                            else ui.pages += Ui.Page.AlbumPage(al, ui.foundIn, mine) }
                    }
                }
                if (res.songs.isNotEmpty()) {
                    if (res.artists.isNotEmpty() || res.albums.isNotEmpty()) item { Section(if (ui.foundIn == "genius") "SINGLES" else "SONGS") }
                    tracks(res.songs, ui, "search", radio = res.songs.size > 1 && !ui.resultsAreList) { t ->
                        // Your own songs: which tab of My music they're in
                        when (Music.tabOf(t)) { "album" -> "In Albums"; "ep" -> "In EPs"; "single" -> "In Singles"
                            // Genius has no music: a song found there that isn't yours says where it would play from
                            else -> if (ui.foundIn == "genius" && !(t.isFile || t.file != null || Music.lib.has(t.id))) org.omarchy.music.GeniusRules.NOTE else "" }
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
    org.omarchy.music.SearchHistory.add("search", q)   // (each step of one search typed isn't kept: see SearchHistoryRules)
    val link = linkSource(q)
    var key = link ?: Music.searchIn
    if (key == "tidal" && link == null && Music.sources["tidal"]?.signedIn != true) {
        if (!quiet) Music.toast("Tidal isn't connected: searching YouTube Music")
        key = "ytm"
    }
    val seq = ++ui.searchSeq
    ui.tab = "search"
    ui.resultsAreList = Regex("/(playlist|album|mix)/|list=").containsMatchIn(q)
    if (key == "genius") {   // Genius: names only (it has no music), for songs no service has
        ui.foundIn = key
        val cacheKey = "$key|${q.lowercase()}"
        SearchCache.get(cacheKey)?.let { ui.searching = false; ui.found = it; return }
        ui.searching = true
        Music.bg({ org.omarchy.music.GeniusSearch.find(q) }) { res, err ->
            if (res != null && err == null) SearchCache.put(cacheKey, res)
            if (seq != ui.searchSeq) return@bg
            ui.searching = false
            if (err != null) { ui.found = Found(emptyList()); if (!quiet) Music.toast("Genius couldn't be searched: ${err.message?.take(100)}") }
            else ui.found = res
        }
        return
    }
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
    Music.bg({ if (link != null) Found(src.search(q)) else src.find(q).let { org.omarchy.music.ReleaseRules.search(it) }.let { it.copy(songs = rankSongs(it.songs, q)) } }) { res, err ->
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
    PlaceColumn(Modifier.fillMaxSize()) {
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
                val shown = org.omarchy.music.YourPlaylists.outsideFolders(lists, key).filter { fits(ui, it.title, it.sub) }   // one in a folder shows there only
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
                laidOutItems(shown, ui, key = { "sp-" + shown[it].id + "#" + it }, names = { shown[it].title }, row = { i ->
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
            // (the DJ always plays from your whole library: Music › DJ)
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
    val songView = ui.musicView in setOf("all", "favorites", "unfavorited", "recent", "later", "theirs")
    // Other device's songs: what it has that this device hasn't (worked out from all of both libraries, so kept until
    // your music changes or a Sync brings a new list)
    val theirsList = if (ui.musicView == "theirs") remember(Music.musicVersion, Music.savedVersion, org.omarchy.music.LibraryMatch.at) {
        runCatching { org.omarchy.music.LibraryMatch.missing() }.getOrDefault(emptyList()) } else emptyList()
    val songTab = "music-" + ui.musicView
    val songOpts = org.omarchy.music.songSorts(withOrder = ui.musicView != "all")
    val songSort = sortNow(songTab, songOpts, if (ui.musicView == "all") Music.musicSort.takeIf { it in songOpts } ?: "title" else "order")
    val songBase = if (!songView) emptyList() else runCatching { when (ui.musicView) {
        "all" -> Music.mySongs()
        "recent" -> Music.plays.recent().map { TopArt.withCover(Music.mineOf(it)) }   // your own files first
        "favorites" -> Music.favorites.tracks()
        "later" -> org.omarchy.music.ListenLater.tracks().map { TopArt.withCover(Music.mineOf(it)) }   // (your own copy of each one you have since got)
        "theirs" -> theirsList
        else -> Music.mySongs().filter { !Music.favorites.has(it) }.sortedBy { norm(it.title) }
    } }.getOrDefault(emptyList())
    // Recently played and Listen later: the covers of songs that aren't yours and came with none, looked up online
    if (ui.musicView == "recent" || ui.musicView == "later") {
        TopArt.tick
        LaunchedEffect(ui.musicView, songBase.size) { TopArt.lookUp { songBase } }
    }
    val songSorted = rememberSorted(songBase, songSort) { d -> org.omarchy.music.sortTracks(songBase, songSort, d) }
    Column(Modifier.fillMaxSize()) {
        // Which of these is shown is picked from the library's list (Main.kt's categories).
        val state = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            Music.musicVersion   // read here too: the list is built apart from the screen, and must follow a scan
            org.omarchy.music.StatHides.version   // …and something hidden from the stats lists, or shown again
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
                "followed" -> followedView(ui)
                "newreleases" -> newReleasesView(ui)
                "discovery" -> discoveryView(ui)
                "repeat" -> repeatView(ui)
                "repeats" -> repeatsView(ui)
                "neglected" -> neglectedView(ui)
                "milestones" -> milestonesView(ui)
                "taste" -> tasteView(ui)
                "moodqueue" -> moodQueueView(ui)
                "versionqueue" -> versionQueueView(ui)
                else -> {
                    // Your own copy of each song (its file's cover and tags), not the streamed copy the count was made with
                    val skipRows = if (ui.musicView == "skips") Music.songStats.rows(Music.skipsSince())
                        .map { (t, k, f) -> Triple(Music.mineOf(t), k, f) }.filter { !org.omarchy.music.StatHides.song(it.first) }
                        // (one row a song: two counted songs that are one file of yours are added up. Shown apart, both
                        // rows carried the count of one of them, so a row sorted as 100 skips said 1.)
                        .groupBy { org.omarchy.music.songKey(it.first) }.values.map { g -> Triple(g.first().first, g.sumOf { it.second }, g.sumOf { it.third }) }
                        else emptyList()
                    val skipInfo = skipRows.associate { (t, k, f) -> org.omarchy.music.songKey(t) to (k to f) }
                    val tracks = when (ui.musicView) {
                        "all", "recent", "favorites", "unfavorited", "later", "theirs" -> songSorted
                        "skips" -> {
                            val r = { a: Int, b: Int -> org.omarchy.music.SongStats.rate(a, b) }
                            when (Music.skipOrder) {
                                "least" -> skipRows.sortedWith(compareBy<Triple<Track, Int, Int>>({ r(it.second, it.third) }, { -it.third }, { it.second }))
                                "heard" -> skipRows.sortedWith(compareBy<Triple<Track, Int, Int>>({ -it.third }, { it.second }))
                                else -> skipRows.sortedWith(compareBy<Triple<Track, Int, Int>>({ -it.second }, { -r(it.second, it.third) }))
                            }.map { it.first }
                        }
                        else -> Music.mostPlayed(Music.topPeriod).filter { !org.omarchy.music.StatHides.song(it) }
                    }
                    if (tracks.isEmpty() && !(ui.musicView == "skips" && Music.skipPeriod != "all") && !(ui.musicView == "top" && Music.topPeriod != "all")) item {   // (a period with none: its chips stay)
                        val (t, b) = when (ui.musicView) {
                            "all" -> "No music yet" to "Songs you download, and the music in your folders\n(${Music.folders.joinToString()}), show up here."
                            "recent" -> "Nothing played yet" to "The songs you listen to show up here, the last one first."
                            "favorites" -> "No favorites yet" to "${LONG_PRESS.cap()} a song (or select a few) and pick Favorite, or $TAP the ☆ by the song playing."
                            "unfavorited" -> "Every song is a favorite" to "Your songs you haven't starred show up here."
                            "theirs" -> org.omarchy.music.LibraryMatch.from.let { dev ->
                                if (dev.isEmpty()) "Sync with your other device first" to "At a Sync (Settings › Remote & sync) your phone and PC tell each other every song they have. The songs the other has that this one hasn't are listed here, to tick and download or to leave out. Both need this version of the app."
                                else org.omarchy.music.LibraryMatchRules.heading(0, dev) to "Counted at the last Sync. Sync again after adding music on $dev." +
                                    (if (org.omarchy.music.LibraryMatch.leftOutCount > 0) " %,d left out by you aren't shown.".format(org.omarchy.music.LibraryMatch.leftOutCount) else "") }
                            "later" -> "Nothing on Listen later yet" to "For music you want to hear but can't right now. Pick songs, albums or EPs (${LONG_PRESS} one), open Options and choose “Add to Listen later playlist”: they wait here until you have time for them."
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
                                "later" -> "LISTEN LATER · ${tracks.size} SONG${if (tracks.size != 1) "S" else ""}" +
                                    (org.omarchy.music.ListenLater.playlist()?.let { " · " + org.omarchy.music.Sync.mark(it) } ?: "")
                                "theirs" -> org.omarchy.music.LibraryMatchRules.heading(tracks.size, org.omarchy.music.LibraryMatch.from.ifEmpty { "your other device" }).uppercase()
                                else -> if (Music.mostPlayedFromStats) "MOST PLAYED · STATS" else "MOST PLAYED"
                            })
                            // Listen later › Review: the songs on it you haven't got, each to download or leave streaming
                            if (ui.musicView == "later" && tracks.isNotEmpty()) Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
                                Pill("Review…", quiet = true) { org.omarchy.music.ListenLater.playlist()?.let { ui.reviewFor = it } }
                            }
                            if (ui.musicView == "recent") Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Pill("Refresh", quiet = true) { Music.musicVersion++; Music.toast("Recently played is up to date") }
                                SortMenu(songTab, songOpts, songSort, names = mapOf("order" to "Last played first"))
                            }
                            if (ui.musicView == "theirs") {
                                // The checklist: every one ticked to start with; untick what you don't want, then Options ›
                                // Get (or Leave out, for the ones never to be offered again). Each row says how it will come.
                                val copy = org.omarchy.music.SongCopy
                                Text("The songs ${copy.other} has as files that this $DEVICE hasn't. Tick the ones you want and copy them: each one's own " +
                                    "file comes from ${copy.other} into My music, never a download from streaming. " +
                                    "The ones you leave out aren't offered again. Nothing comes by itself. " + org.omarchy.music.SongTransferRules.NEEDS + ".", style = T.sub,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
                                if (copy.busy) Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(copy.note, style = T.sub.copy(color = C.accent), modifier = Modifier.weight(1f, fill = false))
                                    Pill("Stop", quiet = true) { copy.cancel() }
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Pill("Tick them all") { ui.startSelecting(tracks, RowPlace(tracks, 0, "theirs"), null); ui.selectAllHere() }
                                    Pill("Copy all %,d".format(tracks.size), quiet = true) {
                                        ui.confirm = "Copy all %,d song%s from ${copy.other} to this $DEVICE?".format(tracks.size, if (tracks.size != 1) "s" else "") to { copy.get(tracks) } }
                                    if (org.omarchy.music.LibraryMatch.leftOutCount > 0) Pill("Bring back the %,d left out".format(org.omarchy.music.LibraryMatch.leftOutCount), quiet = true) {
                                        org.omarchy.music.LibraryMatch.bringBack() }
                                    SortMenu(songTab, songOpts, songSort, names = mapOf("order" to "As on the other device"))
                                }
                            }
                            if (ui.musicView == "later") Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Pill("Play") { Music.playFrom(tracks, 0, false) }
                                Pill("Shuffle") { Music.playFrom(org.omarchy.music.shuffleSongs(tracks), 0, false) }
                                SortMenu(songTab, songOpts, songSort, names = mapOf("order" to "Last added first"))
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
                                OnThisDay()
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
                                "theirs" -> org.omarchy.music.SongCopy.how(t)   // ⇄ from your PC
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
                // (copies not surely one song: none marked Remove until you say so)
                val picks = groups.map { g -> val sure = org.omarchy.music.sureCopies(g)
                    g to g.filterIndexed { i, t -> ui.dupeRemove[t.id] ?: (sure && i != 0) }.map { it.id }.toSet() }
                val n = picks.sumOf { (g, rm) -> if (rm.size >= g.size) g.size - 1 else rm.size }
                if (n > 0) ActionRow("Remove the $n cop${if (n != 1) "ies" else "y"} marked Remove  ›") {
                    ui.confirm = ("Remove $n duplicate cop${if (n != 1) "ies" else "y"}? In each song the copies marked Keep stay, and your " +
                        "playlists get them in place of the ones removed (a song with every copy marked keeps its best)." +
                        if (onPhone) " Android asks you once more, and the files go to its trash." else " Files go to the Recycle Bin.") to { Music.removeDupes(picks) }
                }
            }
            groups.forEach { g ->
                val sure = org.omarchy.music.sureCopies(g)
                item(key = "dupe-head-${g[0].id}") { DupeGroupHead(g[0], sure) { Music.markNotDupe(g) } }
                g.forEachIndexed { i, t ->
                    item(key = "dupe-${g[0].id}-${t.id}") {
                        val rm = ui.dupeRemove[t.id] ?: (sure && i != 0)
                        DupeCopyRow(t, best = i == 0, info = Music.copyInfo(t), where = Music.whereIs(t), remove = rm) { ui.dupeRemove[t.id] = it }
                    }
                }
            }
        }
    }
}

/** A Duplicates group's heading (the library's or a playlist's): the song, Not a duplicate, and a word when its copies
 *  aren't surely one song. */
@Composable
private fun DupeGroupHead(first: Track, sure: Boolean, notDupe: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Section(first.title.uppercase() + " · " + first.artist.uppercase()) }
        Pill("Not a duplicate", quiet = true, onClick = notDupe)
        Spacer(Modifier.width(10.dp))
    }
    if (!sure) Text("Maybe one song: the names match once a track number or signs are left out, a file names no artist, " +
        "or the same name and length are under different artists. Play them (▶) before you remove one; nothing here is " +
        "marked Remove until you do it.", style = T.sub, modifier = Modifier.padding(horizontal = 12.dp))
}

/** One copy in a Duplicates group: what it is and where, ▶, Keep and Remove. */
@Composable
private fun DupeCopyRow(t: Track, best: Boolean, info: String, where: String, remove: Boolean, pick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${t.title} · ${t.artist}" + if (t.album.isNotEmpty()) " · ${t.album}" else "", style = T.body, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text((if (best) "★ best · " else "") + listOf(info, org.omarchy.music.fmtTime(t.duration))
                .filter { it.isNotEmpty() }.joinToString(" · "), style = T.sub)
            Text(where, style = T.time, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("▶", style = T.title.copy(color = C.accent), modifier = Modifier.clickable { Music.playFrom(listOf(t), 0, false) }.padding(8.dp))
        Chip("Keep", !remove) { pick(false) }
        Spacer(Modifier.width(4.dp))
        Chip("Remove", remove) { pick(true) }
    }
}

/** A playlist's Find duplicates: the songs in it more than once, each copy with Keep or Remove; Remove takes a copy
 *  out of the playlist only (your files stay). Picks are kept in ui.dupeRemove under "pl:" and the copy's place. A copy
 *  that can't be played (its file is gone) starts marked Remove; so do the extra copies of a song that is surely one. */
private fun androidx.compose.foundation.lazy.LazyListScope.playlistDupesView(ui: Ui, d: org.omarchy.music.Copies.PlaylistDupes) {
    val groups = d.groups
    fun again() { ui.dupeRemove.keys.removeAll { it.startsWith("pl:") }; Music.findPlaylistDupes(d.id) }
    item { BackRow("Duplicates in this playlist") { Music.playlistDupes = null } }
    when {
        groups == null -> item { Section("LOOKING FOR DUPLICATES…") }
        groups.isEmpty() -> item {
            Empty("No duplicates", "Every song in this playlist is in it once.")
            ActionRow("Look again  ›") { again() }
        }
        else -> {
            fun key(i: Int) = "pl:$i"
            fun gone(i: Int) = d.where[i].orEmpty().startsWith("Not on")
            val sure = groups.associateWith { g -> org.omarchy.music.sureCopies(g.map { d.tracks[it] }) }
            fun marked(g: List<Int>, j: Int) = ui.dupeRemove[key(g[j])] ?: (j != 0 && (sure[g] == true || gone(g[j])))
            item {
                val extra = groups.sumOf { it.size - 1 }
                Section("${groups.size} SONG${if (groups.size != 1) "S" else ""} · $extra EXTRA COP${if (extra != 1) "IES" else "Y"} · THE BEST COPY FIRST")
                Text("Remove takes a copy out of this playlist only: your files stay.", style = T.sub, modifier = Modifier.padding(horizontal = 12.dp))
                ActionRow("Look again  ›") { again() }
                val picks = groups.map { g -> g to g.filterIndexed { j, _ -> marked(g, j) }.toSet() }
                val n = picks.sumOf { (g, rm) -> if (rm.size >= g.size) g.size - 1 else rm.size }
                if (n > 0) ActionRow("Take the $n cop${if (n != 1) "ies" else "y"} marked Remove out  ›") {
                    ui.confirm = ("Take $n cop${if (n != 1) "ies" else "y"} out of this playlist? The copies marked Keep stay in it (a song with " +
                        "every copy marked keeps its best). Your files stay where they are.") to {
                        Music.removePlaylistDupes(picks); ui.dupeRemove.keys.removeAll { it.startsWith("pl:") } }
                }
            }
            groups.forEach { g ->
                item(key = "pldupe-head-${g[0]}") { DupeGroupHead(d.tracks[g[0]], sure[g] == true) { Music.playlistNotDupe(g) } }
                g.forEachIndexed { j, i ->
                    item(key = "pldupe-$i") {
                        val t = d.tracks[i]
                        DupeCopyRow(t, best = j == 0, info = Music.copyInfo(t), where = d.where[i].orEmpty(), remove = marked(g, j)) { ui.dupeRemove[key(i)] = it }
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
            // The DJ always plays from your whole library: your files, downloads and saved songs, and your playlists' songs
            Text("From your library", style = T.sub)
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
    // Select (a long press › Select): folders ticked together, with the options songs have (for the songs of your
    // playlists in them) and Delete… and Rename… for the folders themselves.
    val picking = ui.pickingIn("folders")
    val picked = ui.albumsPicked
    fun songs(f: org.omarchy.music.PlaylistFolder) = org.omarchy.music.YourPlaylists.folderSongs(f)
    fun askDelete(ids: List<String>) {
        val names = ids.mapNotNull { Music.playlistFolders.get(it)?.name }
        if (names.isEmpty()) return
        ui.confirm = ((if (names.size == 1) "Delete the folder “${names[0]}”?" else "Delete ${names.size} folders? " + names.joinToString(", ") { "“$it”" } + ".") +
            " The playlists in ${if (names.size == 1) "it" else "them"} stay, and show in your playlists again.") to {
            org.omarchy.music.YourPlaylists.deleteFolders(ids); picked.clear(); if (picking) ui.pickingAlbums = false }
    }
    item {
        if (picking) PickBar(ui, "folders", listOf(
            "Delete…" to { askDelete(picked.keys.toList()) },
            "Rename…" to {
                fun next(ids: List<String>) {
                    val f = ids.firstOrNull()?.let { Music.playlistFolders.get(it) } ?: return
                    ui.askName = Triple("Rename folder", f.name) { n -> Music.renameFolder(f.id, n); next(ids.drop(1)) }
                }
                next(picked.keys.toList())
            },
            "Download" to { Music.downloadPlaylists(picked.keys.flatMap { id -> Music.playlistFolders.get(id)?.playlists.orEmpty().filter { it.kind == "mine" }.map { it.id } }) }))
        else Section("FOLDERS")
    }
    items(folders.size, key = { "folder-${folders[it].id}" }) { i ->
        val f = folders[i]
        val n = f.playlists.size
        PlaylistRow(Album(f.id, f.name, "Folder · $n playlist${if (n != 1) "s" else ""}"), onLongClick = {
            if (picking) picked[f.id] = songs(f)
            else ui.choices = f.name to listOf(pinChoice(org.omarchy.music.Pin("listfolder", f.id, f.name)),
                "Rename…" to { ui.askName = Triple("Rename folder", f.name) { name -> Music.renameFolder(f.id, name) } },
                "Delete…" to { askDelete(listOf(f.id)) },
                "Select" to { ui.startPick("folders", f.id, songs(f)) })
        }, checked = if (picking) f.id in picked else null) {
            if (picking) { if (f.id in picked) picked.remove(f.id) else picked[f.id] = songs(f) }
            else ui.openFolder = f.id
        }
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
    if (folder != null) { folderView(ui, folder); return }
    run {   // your playlists and their folders
        // A new playlist syncs (⇄: the same on the phone and the PC) unless Settings › Your files › New playlists sync is
        // off; the second row makes the other kind
        item { ActionRow("＋  New playlist") { ui.askName = Triple("New playlist", "") { n -> Music.newPlaylist(n) } } }
        item {
            if (org.omarchy.music.Prefs.on(org.omarchy.music.SyncPlaylists.AUTO, true))
                ActionRow("＋  New playlist for this device only") { ui.askName = Triple("New playlist for this device only", "") { n -> Music.newPlaylist(n, synced = false) } }
            else ActionRow("⇄  New synced playlist") { ui.askName = Triple("New synced playlist", "") { n -> Music.newPlaylist(n, synced = true) } }
        }
        item { ActionRow("＋  New folder") { ui.askName = Triple("New folder", "") { n -> Music.newFolder(n) } } }
        // This week's mix: what you have been playing, with songs you haven't heard in a long while among them
        item { ActionRow("✦  Make this week's mix") { org.omarchy.music.YourPlaylists.weeklyMix() } }
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
        // A playlist in a folder shows in its folder only
        val shown = org.omarchy.music.sortGroups(org.omarchy.music.YourPlaylists.outsideFolders(lists).filter { fits(ui, it.name) }, plSort) { pl ->
            org.omarchy.music.GroupFacts(pl.name, songs = pl.tracks, created = pl.created, updated = pl.updated, added = pl.created) }
            .let { if (plSort == "order") PlaylistOrder.sorted(it) { pl -> pl.id } else it }   // Your order: as you rearranged them
        val picking = ui.pickingIn("playlists")
        val picked = ui.albumsPicked
        fun menu(pl: org.omarchy.music.Playlist) {
            if (picking) { picked[pl.id] = pl.tracks; return }
            val toGet = pl.tracks.count { !it.isFile && !Music.lib.has(it.id) }
            ui.choices = pl.name to listOfNotNull(pinChoice(org.omarchy.music.Pin("mine", pl.id, pl.name)),
                ("Download ($toGet song${if (toGet != 1) "s" else ""} not on this $DEVICE)" to { Music.downloadPlaylists(listOf(pl.id)) }).takeIf { toGet > 0 },
                "Put in a folder…" to { ui.folderFor = FolderItem.mine(pl) },
                (if (pl.synced == true) "Stop syncing" else "Sync with phone and PC") to { Music.setPlaylistSynced(pl.id, pl.synced != true) },
                ("Send a copy to the ${if (onPhone) "PC" else "phone"}" to { org.omarchy.music.PlaylistSend.send(pl) }).takeIf { org.omarchy.music.PlaylistSend.canSend },
                "Set a cover…" to { ui.playlistCoverFor = pl.id },
                ("Remove its cover" to { PlaylistCovers.clear(pl.id) }).takeIf { PlaylistCovers.of(pl.id) != null },
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
                if (plSort == "order") Chip("Rearrange…", false) { ui.rearrange = RearrangeWhat.Playlists }
            }
        }
        fun lay(some: List<org.omarchy.music.Playlist>) = laidOutItems(some, ui, key = { "pl-" + some[it].id }, names = { some[it].name }, row = { i ->
            val pl = some[i]
            val songs = pl.tracks
            val cover = songs.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
            PlaylistRow(Album(pl.id, pl.name, ShownPlays.allText(songs), "", songs.size.toString()), PlaylistCovers.of(pl.id) ?: cover?.let { artModel(it) },
                onLongClick = { menu(pl) }, checked = if (picking) pl.id in picked else null,
                badge = org.omarchy.music.Sync.mark(pl), badgeOn = pl.synced == true) { tapList(pl) }
        }) { i, mod, compact ->
            val pl = some[i]
            val songs = pl.tracks
            val cover = songs.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
            Tile(PlaylistCovers.of(pl.id) ?: cover?.let { artModel(it, tilePx(compact)) }, pl.shown, ShownPlays.songsAndPlays(songs), mod, compact, ico = Ico.PLAYLIST, lit = picking && pl.id in picked,
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
    // Find duplicates, open: its songs in it more than once, in place of the playlist's songs until Back
    Music.playlistDupes?.takeIf { it.id == open.id }?.let { playlistDupesView(ui, it); return }
    item { BackRow(open.shown) { ui.openMine = null } }
    val sorted = Music.sortedPlaylist(open.tracks)
    item {
        ListActions(sorted, onDj = { ui.djFrom(Dj.From("playlist", id = open.id)) }, extra = listOfNotNull(
            // Its own order (the Order sort), put the way you want: drag the songs
            ("Rearrange songs" to { Music.pickPlaylistSort("order"); ui.rearrange = RearrangeWhat.Songs(open.id) }).takeIf { open.tracks.size > 1 },
            // Songs in it more than once (the same song twice, or two copies of it): Keep or Remove each
            ("Find duplicates" to { ui.dupeRemove.keys.removeAll { it.startsWith("pl:") }; Music.findPlaylistDupes(open.id) }).takeIf { open.tracks.size > 1 },
            "Rename" to { ui.askName = Triple("Rename playlist", open.name) { n -> Music.renamePlaylist(open.id, n) } },
            (if (open.synced == true) "Stop syncing" else "Sync with phone and PC") to { Music.setPlaylistSynced(open.id, open.synced != true) },
            ("Send a copy to the ${if (onPhone) "PC" else "phone"}" to { org.omarchy.music.PlaylistSend.send(open) }).takeIf { org.omarchy.music.PlaylistSend.canSend },
            "Set a cover…" to { ui.playlistCoverFor = open.id },
            ("Remove its cover" to { PlaylistCovers.clear(open.id) }).takeIf { PlaylistCovers.of(open.id) != null },
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

/** Undo for the queue: there while there is a change of yours to take back (cleared, shuffled, moved, removed, added). */
@Composable
fun QueueUndo(modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { Pill("↶  " + Music.queueUndoLabel.ifEmpty { "Undo the last change to the PC's queue" }, quiet = true) { Music.undoQueue() } }
}

@Composable
fun QueueTab(ui: Ui) {
    val q = Music.shownQueue()
    val idx = Music.shownIndex()
    PlaceColumn(Modifier.fillMaxSize()) {
        if (!Remote.active) item { QueueHeader(ui) }
        if (Music.queueUndoLabel.isNotEmpty() || Remote.active) item("undo") { QueueUndo() }
        if (q.isEmpty() || idx < 0) { item { Empty("Nothing queued", if (Remote.active) "Nothing is playing on the desktop. Play a song here and it plays there." else "Play a song, or $LONG_PRESS one and pick Add to the queue.") }; return@PlaceColumn }
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
        // New releases looks for the artists you follow
        if (org.omarchy.music.Follows.isFollowed(name)) "Unfollow (no new releases)" to { org.omarchy.music.Follows.unfollow(listOf(name)) }
        else "Follow (their new releases)" to { org.omarchy.music.Follows.follow(listOf(name)) },
        "Change picture…" to { ui.artistPictureFor = name }, "Merge into…" to { ui.mergeArtist = name },
        if (org.omarchy.music.StatHides.artist(name)) "Show in stats again" to { org.omarchy.music.StatHides.show(org.omarchy.music.StatHideRules.artist(name)) }
        else "Hide from stats" to { org.omarchy.music.StatHides.hideArtist(name); Music.toast("$name is hidden from the stats lists. Settings › Hidden from stats brings it back") })
}

/** A stats row's menu for an album (Most played's albums and their times): hide it from the stats lists. */
fun albumStatMenu(ui: Ui, title: String, artist: String) {
    ui.choices = title to listOf("Hide from stats" to {
        org.omarchy.music.StatHides.hideAlbum(title, artist); Music.toast("$title is hidden from the stats lists. Settings › Hidden from stats brings it back") })
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
        val sig = org.omarchy.music.Signals.ofArtist(name)
        val said = org.omarchy.music.SignalRules.thumb(sig)
        // Your word on the artist, for autoplay, radios and the DJ (Signals)
        val taste = listOfNotNull(
            (if (said == 1) "More of $name: on (tap to undo)" to { org.omarchy.music.Signals.artistThumb(name, null) } else "More of $name" to { org.omarchy.music.Signals.artistThumb(name, true) }),
            (if (said == -1) "Less of $name: on (tap to undo)" to { org.omarchy.music.Signals.artistThumb(name, null) } else "Less of $name" to { org.omarchy.music.Signals.artistThumb(name, false) }),
            (if ((sig?.noAuto ?: 0) > 0) "Let autoplay put $name in again" to { org.omarchy.music.Signals.artistNoAuto(name, false) }
             else "$name: not for autoplay, radios or the DJ" to { org.omarchy.music.Signals.artistNoAuto(name, true) }))
        ui.choices = ui.choices?.let { (t, l) -> t to (l + taste + listOf("Create artist playlist" to { Music.makeArtistPlaylist(name) },
            "Options…" to { ui.optionsFor(Music.artistSongs(name)) },
            "Select" to { ui.startPick("artists", name, Music.artistSongs(name)) })) }
    }
    fun open(name: String) {
        if (picking) { if (name in picked) picked.remove(name) else picked[name] = Music.artistSongs(name); return }
        ui.pages += Ui.Page.Artist(name)
    }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    PlaceColumn(Modifier.fillMaxSize()) {
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
        laidOutItems(shown, ui, key = { "ar-" + shown[it].name }, names = { shown[it].name }, row = { i ->
            val a = shown[i]
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(R.card)
                .background(if (picking && a.name in picked) C.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                .pressable(onClick = { open(a.name) }, onLongClick = { menu(a.name, a.thumb) })
                .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundArt(artistArt(a.name, a.thumb), 52.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(a.name, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plays(a.plays) + (org.omarchy.music.ReleaseCounts.of(org.omarchy.music.ReleaseCounts.artistKey(a.name))?.let { " · " + org.omarchy.music.ReleaseCounts.text(it) } ?: ""),
                        style = T.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    // What the page found the last time it was open (within the half hour): it is shown from that at once, so coming
    // back from an album is as quick as any other screen, at the place you were, with nothing looked up again.
    val snapKey = norm(page.name) + "|" + (page.prefer ?: "")
    val snap = remember(page) { artistSnaps[snapKey]?.takeIf { System.currentTimeMillis() - it.at < 30 * 60_000L } }
    var info by remember(page) { mutableStateOf<ArtistInfo?>(snap?.info) }
    var failed by remember(page) { mutableStateOf<String?>(null) }
    var morePics by remember(page) { mutableStateOf(false) }
    // Two bands with one name: the ones the service has under this name (when more than one), to say which is meant;
    // the page is looked up again once you've said (reload).
    var choices by remember(page) { mutableStateOf<List<org.omarchy.music.ArtistChoice>>(snap?.choices ?: emptyList()) }
    var reload by remember(page) { mutableStateOf(0) }
    // Every song on their releases, once asked for (each release is fetched in turn: everyAt says how far)
    var every by remember(page, reload) { mutableStateOf<List<Track>?>(if (reload == 0) snap?.every else null) }
    var everyFor by remember(page, reload) { mutableStateOf(if (reload == 0) snap?.everyFor else null) }
    var everyAt by remember(page, reload) { mutableStateOf<Pair<Int, Int>?>(null) }
    // Genius as a second opinion on whose a song is (a service can mix two bands of one name; Genius keeps them
    // apart, mostly): the Genius artists of this name, the one you picked, its songs, and the service's releases
    // gone through with them. Apart from the service's own "which one?" below: either can be used, or both.
    var gChoices by remember(page) { mutableStateOf<List<org.omarchy.music.ArtistChoice>>(snap?.gChoices ?: emptyList()) }
    val gPick = remember(page, Music.artistsVersion) { org.omarchy.music.GeniusArtists.picked(page.name) }
    val snapG = snap?.takeIf { it.gPick == gPick }   // (what was kept of Genius is for the Genius artist picked then)
    var gSongs by remember(page, gPick) { mutableStateOf<List<org.omarchy.music.GeniusRules.Hit>?>(snapG?.gSongs) }
    var gFailed by remember(page, gPick) { mutableStateOf(false) }
    var sorted by remember(page, gPick, reload) { mutableStateOf<org.omarchy.music.GeniusArtists.Sorted?>(if (reload == 0) snapG?.sorted else null) }
    var sortAt by remember(page, gPick, reload) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(page) { if (snap == null) Music.bg({ org.omarchy.music.GeniusArtists.choices(page.name) }) { c, _ -> gChoices = c.orEmpty() } }
    // Their whole discography: the releases Genius lists for them that the page doesn't have are added to it (a
    // service's artist page can leave some out). From the Genius artist you picked, or its only one of the name.
    val gId = gPick ?: gChoices.singleOrNull()?.id
    var gAlbums by remember(page, gId) { mutableStateOf<List<Album>>(snap?.takeIf { it.gId == gId }?.gAlbums ?: emptyList()) }
    LaunchedEffect(page, gId) { if (gId != null && gAlbums.isEmpty()) Music.bg({ org.omarchy.music.GeniusArtists.albums(gId) }) { a, _ -> gAlbums = a.orEmpty() } }
    LaunchedEffect(page, gPick) {
        if (gPick != null && gSongs == null) Music.bg({ org.omarchy.music.GeniusArtists.songs(gPick) }) { got, _ -> gSongs = got.orEmpty(); gFailed = got.isNullOrEmpty() }
    }
    LaunchedEffect(page, reload) {
        Music.lookUpArtists(listOf(page.name))
        if (reload == 0 && info != null) return@LaunchedEffect   // shown from what was kept: nothing to look up again
        // Their top songs: your own copy of each you have (it plays your file, shows its cover), the service's otherwise.
        Music.bg({ Music.artistInfo(page.name, page.prefer)?.let { it.copy(top = Music.yourCopies(it.top)) } }) { res, err ->
            info = res
            failed = err?.message ?: if (res == null) "No service knows ${page.name}" else null
            if (res != null && res.source.isNotEmpty() && choices.isEmpty())
                Music.bg({ org.omarchy.music.ArtistLookups.choices(page.name, res.source) }) { c, _ -> choices = c.orEmpty() }
        }
    }
    LaunchedEffect(info, gSongs) {
        val i = info; val hits = gSongs
        if (i != null && !hits.isNullOrEmpty() && sorted == null && sortAt == null) {
            sortAt = 0 to (i.albums.size + i.also.size)
            Music.bg({
                org.omarchy.music.GeniusArtists.sort(i, page.name, hits) { d, n ->
                    Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { if (sortAt != null) sortAt = d to n }
                }
            }) { res, _ -> sorted = res; sortAt = null }
        }
    }
    // Featured on: other artists' songs that credit this one (found once the page knows them; a moment, as their
    // releases are asked for)
    var feats by remember(page, reload) { mutableStateOf<org.omarchy.music.FeatureRules.Featured?>(if (reload == 0) snapG?.feats else null) }
    LaunchedEffect(info, gPick) {   // (Genius's list of features only from the Genius artist you picked: a name alone can be someone else)
        val i = info ?: return@LaunchedEffect
        if (feats != null && i === snap?.info) return@LaunchedEffect
        Music.bg({ org.omarchy.music.ArtistLookups.features(i, page.name, gPick) }) { f, _ -> if (f != null) feats = f }
    }
    // The service's singles that are on an album or an EP of theirs already: left out (the song is on its release).
    // Their releases' songs are asked for one by one, so they go a moment after the page shows.
    var folded by remember(page, reload) { mutableStateOf<Set<String>>(if (reload == 0) snap?.folded ?: emptySet() else emptySet()) }
    LaunchedEffect(info) {
        val i = info
        if (i != null && i.albums.size > 1) Music.bg({ org.omarchy.music.ArtistLookups.foldedSingles(i) }) { f, _ -> folded = f.orEmpty() }
    }
    val v = Music.artistsVersion
    val pics = remember(page, v) { org.omarchy.music.Pictures.known(page.name) }
    // (kept with the page's snapshot while your music hasn't changed: gathering them goes through all your songs)
    val songs = remember(page, Music.musicVersion) { snap?.takeIf { it.musicVer == Music.musicVersion }?.songs ?: Music.artistSongs(page.name) }
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
    val onlyStream = from == "stream"
    // Every song of theirs is listed without being asked for: as soon as the page knows them (and, with a Genius
    // artist picked, Genius's list), their releases are gone through. Again when either changes.
    var everySeq by remember(page) { mutableStateOf(0) }
    LaunchedEffect(info, gPick, gSongs, onlyMine) {
        val i = info ?: return@LaunchedEffect
        if (onlyMine || (gPick != null && gSongs == null)) return@LaunchedEffect   // Genius's list is on its way
        val hits = gSongs?.takeIf { it.isNotEmpty() }
        // Listed already for just this (shown from what was kept): left as it is
        val what = "${System.identityHashCode(i)}|$gPick|${hits?.size ?: -1}"
        if (every != null && everyFor == what) return@LaunchedEffect
        everyFor = what
        val seq = ++everySeq
        every = null
        everyAt = 0 to (i.albums.size + i.also.size)
        Music.bg({
            val step = { d: Int, n: Int -> Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { if (seq == everySeq && everyAt != null) everyAt = d to n }; Unit }
            // With a Genius artist picked: the songs of the releases that are theirs, then the ones only Genius lists
            if (hits != null) org.omarchy.music.GeniusArtists.every(org.omarchy.music.GeniusArtists.sort(i, page.name, hits, step), hits)
            else org.omarchy.music.ArtistLookups.everySong(i, page.name, step)
        }) { res, _ -> if (seq == everySeq) { every = res.orEmpty(); everyAt = null } }
    }
    fun yours(t: Track) = t.isFile || t.file != null || Music.lib.has(t.id)
    val ownAlbums = remember(page, Music.musicVersion) {
        snap?.takeIf { it.musicVer == Music.musicVersion }?.ownAlbums ?: runCatching { Music.artistOwnAlbums(page.name) }.getOrDefault(emptyList()) }
    // Your songs are yours: only what is on this device (your files and downloads). Songs of theirs the app knows from
    // your listening history or a service's library but that you don't have are not listed there (with Only streaming
    // picked, those are what the list shows instead, under their own heading).
    val allRaw = remember(songs, coverTick, v, from) { songs.filter { if (onlyStream) !yours(it) else yours(it) }.map { withCover(it) } }
    val topRaw = remember(info, coverTick, v, onlyMine) { info?.top.orEmpty().filter { !onlyMine || yours(it) }.map { withCover(it) } }
    // The albums: only yours; or the service's (yours in place of each one you have: its cover, your songs), then
    // yours the service doesn't list.
    val albumsRaw = remember(info, ownAlbums, from, gAlbums, gPick) {
        if (onlyMine) ownAlbums.map { ArtistAlbum(it.first, it, streamed = false) }
        else {
            val byBase = ownAlbums.associateBy { org.omarchy.music.albumBase(it.first.title) }
            val streamed = info?.albums.orEmpty().map { ArtistAlbum(it, if (onlyStream) null else byBase[org.omarchy.music.albumBase(it.title)], streamed = true) }
            val used = streamed.mapNotNull { it.own?.first?.id }.toSet()
            val listed = streamed + (if (onlyStream) emptyList() else ownAlbums.filter { it.first.id !in used }.map { ArtistAlbum(it.first, it, streamed = false) })
            // …then the ones only Genius lists (opened, their songs play from your copy or are looked for on a service)
            val have = listed.map { it.al.title } + ownAlbums.map { it.first.title }
            val sure = gPick != null || org.omarchy.music.GeniusRules.sharesARelease(gAlbums.map { it.title }, have)
            listed + (if (!sure) emptyList() else org.omarchy.music.GeniusRules.unlisted(gAlbums, have) { it.title }
                .map { ArtistAlbum(it.copy(sub = listOf(it.sub, "on Genius").filter { s -> s.isNotEmpty() }.joinToString(" · ")), null, streamed = false, genius = true) })
        }
    }
    // What shows: what you hid yourself is off the page, what you put back is on it; with a Genius artist picked, a
    // song or release of the service's that isn't on Genius's list for them is left out too (never your own files).
    val marks = remember(page, v, org.omarchy.music.ArtistShown.version) { org.omarchy.music.ArtistShown.marks(page.name) }
    val theirs = remember(gSongs) { gSongs?.takeIf { it.isNotEmpty() }?.let { org.omarchy.music.GeniusRules.judge(it) } }
    fun songShown(t: Track) = org.omarchy.music.ArtistShownRules.shown(marks[org.omarchy.music.ArtistShownRules.songKey(t.title)],
        theirs == null || yours(t) || theirs(t.title))
    fun albumShown(a: ArtistAlbum) = org.omarchy.music.ArtistShownRules.shown(marks[org.omarchy.music.ArtistShownRules.albumKey(a.al.title)],
        !a.streamed || a.own != null || (sorted?.notTheirs?.contains(a.al.id) != true && a.al.id !in folded))
    val all = allRaw.filter { songShown(it) }
    val top = topRaw.filter { songShown(it) }
    val albums = albumsRaw.filter { albumShown(it) }
    val everyShown = every?.filter { songShown(it) }
    // Features: only what you hid yourself is left out (Genius's list of their own songs has no say on these)
    fun featShown(t: Track) = marks[org.omarchy.music.ArtistShownRules.songKey(t.title)] != org.omarchy.music.ArtistShownRules.HIDE
    val featSongs = if (onlyMine) emptyList() else feats?.songs.orEmpty().filter { featShown(it) && (!onlyStream || !yours(it)) }
    val featAlbums = if (onlyMine) emptyList() else feats?.albums.orEmpty().filter {
        marks[org.omarchy.music.ArtistShownRules.albumKey(it.first.title)] != org.omarchy.music.ArtistShownRules.HIDE }
    val leftSongs = (allRaw + topRaw + every.orEmpty()).filter { !songShown(it) }.distinctBy { it.id }
    val leftAlbums = albumsRaw.filter { !albumShown(it) }
    var opening by remember(page) { mutableStateOf(false) }
    /** An album: yours opens your album's page (your files, their covers and tags); the service's is looked up first, and
     *  opens as your album when you have every song on it, else as the service's with your copy of each song you have. */
    fun openAlbum(a: ArtistAlbum) {
        if (a.genius) {   // one only Genius lists: names only, so its songs are asked for, then it opens
            if (opening) return
            opening = true
            Music.bg({ org.omarchy.music.GeniusSearch.albumSongs(a.al) }) { got, err ->
                opening = false
                if (got.isNullOrEmpty()) Music.toast(if (err != null) "Couldn't open it: ${err.message?.take(80)}" else "Genius lists no songs for “${a.al.title}”")
                else if (ui.pages.lastOrNull() == page) ui.pages += Ui.Page.AlbumPage(a.al, "genius", got)
            }
            return
        }
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
    fun pinOf(a: ArtistAlbum): org.omarchy.music.Pin? {
        val own = a.own
        return if (a.genius) null
            else if (a.streamed || own == null) org.omarchy.music.Pin("album", a.al.id, a.al.title, a.al.sub, a.al.thumb, info?.source.orEmpty())
            else org.omarchy.music.Pin("album", own.first.id, own.first.title, own.first.sub, source = "local")
    }
    /** With Everything showing (yours and streaming together): which of the two a release is, marked under its year
     *  as a song's format is: LOCAL when you have it (your files or downloads), STREAMING when it plays from a service. */
    fun whereBadge(a: ArtistAlbum): String? {
        if (onlyMine || onlyStream || a.genius) return null
        // Some of it yours and some not: both, with how many of each (once the release's songs are known: they are
        // asked for as the page loads, and the mark fills in then)
        folded.size   // (read so the marks are drawn again once the releases have been gone through)
        val all = if (a.streamed) org.omarchy.music.ArtistLookups.knownReleaseSongs(info?.source.orEmpty(), a.al.id)?.map { it.title } else null
        return org.omarchy.music.ReleaseRules.whereMark(all, a.own?.second.orEmpty().map { it.title })
    }
    // Picking releases (hold one, or Select): several albums, EPs and singles at once, to pin or hide them together,
    // or for Options on all their songs as anywhere else. A tap ticks one; the bar at the bottom has what to do.
    val picking = ui.pickingIn("artist-albums")
    val pickedAlbums = ui.albumsPicked
    androidx.compose.runtime.DisposableEffect(page) {
        onDispose { if (ui.pickWhere == "artist-albums" && ui.pickingAlbums) { ui.albumsPicked.clear(); ui.pickingAlbums = false } }
    }
    /** Tick a release (or untick it). Its songs are asked for, so Options has them: yours at once, a service's fetched. */
    fun pickAlbum(a: ArtistAlbum) {
        val id = a.al.id
        if (!picking) ui.startPick("artist-albums")
        else if (id in pickedAlbums) { pickedAlbums.remove(id); return }
        pickedAlbums[id] = a.own?.second.orEmpty()
        if (a.own != null) return
        val src = info?.source.orEmpty()
        Music.bg({
            if (a.genius) org.omarchy.music.GeniusSearch.albumSongs(a.al)
            else Music.yourCopies(org.omarchy.music.ArtistLookups.releaseSongs(src, id).orEmpty().map { if (it.album.isEmpty()) it.copy(album = a.al.title) else it })
        }) { got, _ -> if (ui.pickingIn("artist-albums") && id in pickedAlbums && !got.isNullOrEmpty()) pickedAlbums[id] = got }
    }
    fun albumMenu(a: ArtistAlbum) {
        val own = a.own
        val pin = pinOf(a) ?: org.omarchy.music.Pin("album", a.al.id, a.al.title, a.al.sub, a.al.thumb, info?.source.orEmpty())
        val hidden = !albumShown(a)
        ui.choices = a.al.title to (if (a.genius) emptyList() else listOf(pinChoice(pin))) + listOf("Select" to { pickAlbum(a) },
            (if (hidden) "Show on ${page.name}'s page" else "Hide from ${page.name}'s page") to { org.omarchy.music.ArtistShown.albums(page.name, listOf(a.al.title), hide = !hidden) })
    }
    // Sorting, as on the other tabs (each list's sort is kept): the songs by album unless you pick another; the
    // releases and Every song in the order they come in unless you pick one.
    val songSorts = org.omarchy.music.songSorts(false)
    val sort = sortNow("artist-songs", songSorts, "album")
    val mine = rememberSorted(all, sort) { d -> org.omarchy.music.sortTracks(all, sort, d) }
    val albumSort = sortNow("artist-albums", ARTIST_ALBUM_SORTS)
    val albumsSorted = remember(albums, albumSort) {
        fun year(a: ArtistAlbum) = Regex("""\b(19|20)\d\d\b""").find(a.al.sub)?.value?.toInt() ?: 0
        when (albumSort) {
            "created" -> albums.sortedWith(compareByDescending<ArtistAlbum> { year(it) }.thenBy { norm(it.al.title) })
            "created-old" -> albums.sortedWith(compareBy<ArtistAlbum> { year(it).let { y -> if (y == 0) 9999 else y } }.thenBy { norm(it.al.title) })
            "name" -> albums.sortedBy { norm(it.al.title) }
            "name-desc" -> albums.sortedByDescending { norm(it.al.title) }
            else -> albums
        }
    }
    // Your songs, as a list of songs or as your albums, EPs and singles of theirs (the dropdown by its Sort; kept)
    var songsView by remember { mutableStateOf(org.omarchy.music.Prefs.text("artist_songs_view").ifEmpty { "songs" }) }
    // The releases follow the same sort as the songs (the one menu): a song's sort as it fits a release (by title is
    // by the release's name; most played is the release whose songs you played most)
    val ownFit = remember(ownAlbums, marks) {
        ownAlbums.filter { marks[org.omarchy.music.ArtistShownRules.albumKey(it.first.title)] != org.omarchy.music.ArtistShownRules.HIDE } }
    val ownSort = when (sort) { "title", "album", "artist" -> "name"; "title-desc", "artist-desc" -> "name-desc"; else -> sort }
    val ownShown = rememberSorted(ownFit, ownSort) { d ->
        org.omarchy.music.sortGroups(ownFit, ownSort, d) { a -> org.omarchy.music.GroupFacts(a.first.title, songs = a.second) } }
    // How often you played each song, shown on its row while a list is sorted by plays (worked out off the screen's thread)
    val byPlays = sort.startsWith("plays") || sortNow("artist-every", org.omarchy.music.songSorts(true)).startsWith("plays")
    val playData by androidx.compose.runtime.produceState<org.omarchy.music.SortData?>(null, byPlays, Music.musicVersion) {
        value = if (!byPlays) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { Music.sortData("plays") }.getOrNull() }
    }
    fun playsText(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    fun playsOf(t: Track) = playData?.plays?.invoke(t) ?: t.plays
    val everySorts = org.omarchy.music.songSorts(true)
    val everySort = sortNow("artist-every", everySorts)
    val everyList = everyShown?.let { l -> rememberSorted(l, everySort) { d -> org.omarchy.music.sortTracks(l, everySort, d) } }
    // Kept for the next time the page is opened (coming back from an album, or the same artist again soon)
    androidx.compose.runtime.SideEffect {
        val i = info
        if (i != null) {
            if (artistSnaps.size > 12) artistSnaps.remove(artistSnaps.keys.first())
            artistSnaps[snapKey] = ArtistSnap(snap?.takeIf { it.info === i }?.at ?: System.currentTimeMillis(), i, choices, every, everyFor, gChoices, gPick, gId, gSongs, sorted, gAlbums, feats, folded, Music.musicVersion, songs, ownAlbums)
        }
    }
    val links = androidx.compose.ui.platform.LocalUriHandler.current
    // The page's sections fold open and shut, as Settings' do, and are all shut when the page opens: a list of what
    // there is to look through (their albums and singles, featured on, every song, your songs, top songs, what's
    // left out), and you open the one you want. Which are open is kept for the page, so Back finds it as you left it.
    val placeKey = LocalPlace.current
    var open by remember(placeKey) { mutableStateOf(artistOpen[placeKey] ?: emptySet()) }
    fun isOpen(k: String) = k in open
    fun toggle(k: String) {
        open = if (k in open) open - k else open + k
        if (artistOpen.size > 300) artistOpen.clear()
        artistOpen[placeKey] = open
    }
    // Concerts: their coming shows, asked for when the section is opened (only with a key of yours in Settings › Remote & sync);
    // also when Setlists is, so it knows their next show
    val concertsOn = org.omarchy.music.Concerts.on
    val concertsWanted = isOpen("concerts") || isOpen("setlists")
    val concerts by androidx.compose.runtime.produceState<Pair<List<org.omarchy.music.ConcertRules.Concert>?, String>>(null to "", page.name, concertsOn, concertsWanted) {
        if (!concertsOn || !concertsWanted) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { org.omarchy.music.Concerts.find(page.name) to "" }.getOrElse { null to (it.message ?: "Ticketmaster couldn't be reached") } }
    }
    // Setlists: their recent shows' songs from setlist.fm, asked for when Setlists (or Concerts, for each show's setlist) is
    // opened, with a key of yours; a setlist opened shows over the page
    val setlistsOn = org.omarchy.music.Setlists.on
    val setlistsWanted = isOpen("setlists") || isOpen("concerts")
    val setlists by androidx.compose.runtime.produceState<Pair<List<org.omarchy.music.SetlistRules.Setlist>?, String>>(null to "", page.name, setlistsOn, setlistsWanted) {
        if (!setlistsOn || !setlistsWanted) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { org.omarchy.music.Setlists.find(page.name) to "" }.getOrElse { null to (it.message ?: "setlist.fm couldn't be reached") } }
    }
    var setlistOpen by remember(page.name) { mutableStateOf<SetlistView?>(null) }
    setlistOpen?.let { SetlistDialog(page.name, it) { setlistOpen = null } }
    // Picking releases: what the bar at the bottom offers for the ones ticked (besides Options for all their songs).
    // Said from the page itself, not from a row of its list: a row is only there while it's on the screen, and far
    // down the list the bar was left with options made before anything was ticked (Hide did nothing). Each option
    // looks at what is ticked at the moment it's tapped.
    if (picking) {
        fun ticked() = (albumsRaw + ownAlbums.map { ArtistAlbum(it.first, it, streamed = false) }).filter { it.al.id in pickedAlbums }.distinctBy { it.al.id }
        fun done() { pickedAlbums.clear(); ui.pickingAlbums = false }
        val now = ticked()
        val bar = listOfNotNull<Pair<String, () -> Unit>>(
            if (now.any { a -> pinOf(a)?.let { !Music.pinned(it) } == true }) "Pin" to {
                val n = ticked().count { a -> pinOf(a)?.let { p -> if (!Music.pinned(p)) { Music.togglePin(p); true } else false } == true }
                Music.toast("%,d release%s pinned".format(n, if (n != 1) "s" else "")); done() } else null,
            if (now.any { albumShown(it) }) "Hide from ${page.name}'s page" to {
                val l = ticked().filter { albumShown(it) }
                org.omarchy.music.ArtistShown.albums(page.name, l.map { it.al.title }, hide = true); done() } else null,
            if (now.any { !albumShown(it) }) "Show on ${page.name}'s page" to {
                val l = ticked().filter { !albumShown(it) }
                org.omarchy.music.ArtistShown.albums(page.name, l.map { it.al.title }, hide = false); done() } else null)
        androidx.compose.runtime.SideEffect { ui.pickNoun = "releases"; ui.pickExtra = bar }
    }
    PlaceColumn(Modifier.fillMaxSize()) {
        item { BackRow(page.name) { ui.pages.removeAt(ui.pages.lastIndex) } }
        item { ArtistFromMenu(from) { from = it; org.omarchy.music.Prefs.setText(Music.ARTIST_PAGE_FROM, it) } }
        val i = info
        // Two bands with one name: which one this page is (kept: the name means that one from now on)
        if (choices.size > 1 && i != null) item {
            var open by remember { mutableStateOf(false) }
            val now = org.omarchy.music.ArtistLookups.picked(page.name)?.second ?: i.id
            Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                Chip("${choices.size} artists are called ${page.name}: which one? ▾", false) { open = true }
                DropdownMenu(open, { open = false }, containerColor = C.light) {
                    choices.forEach { c ->
                        DropdownMenuItem({ Text((if (c.id == now) "✓  " else "     ") + c.name + (if (c.hint.isNotEmpty()) " · ${c.hint}" else ""), style = T.body) }, {
                            open = false
                            if (c.id != now) { org.omarchy.music.ArtistLookups.pick(page.name, i.source, c.id); info = null; reload++ }
                        })
                    }
                }
            }
        }
        // Genius's word on whose songs these are (shown when Genius has more than one artist of this name, or you
        // picked one): only what's on that artist's list on Genius stays; Off shows all the service has.
        if (gChoices.size > 1 || gPick != null) item {
            var open by remember { mutableStateOf(false) }
            val name = gChoices.firstOrNull { it.id == gPick }?.name
            Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                Box {
                    Chip(if (gPick == null) "Mixed with another ${page.name}? Sort with Genius ▾" else "Sorted with Genius${name?.let { ": $it" } ?: ""} ▾", gPick != null) { open = true }
                    DropdownMenu(open, { open = false }, containerColor = C.light) {
                        DropdownMenuItem({ Text((if (gPick == null) "✓  " else "     ") + "Off · show all the service has", style = T.body) }, {
                            open = false; if (gPick != null) org.omarchy.music.GeniusArtists.pick(page.name, null) })
                        gChoices.forEach { c ->
                            DropdownMenuItem({ Text((if (c.id == gPick) "✓  " else "     ") + c.name + (if (c.hint.isNotEmpty()) " · ${c.hint}" else ""), style = T.body) }, {
                                open = false; if (c.id != gPick) { every = null; everyAt = null; org.omarchy.music.GeniusArtists.pick(page.name, c.id) } })
                        }
                    }
                }
                val at = sortAt
                if (gPick != null && gFailed) Text("Genius couldn't be reached: showing all the service has", style = T.sub)
                else if (at != null) Text("Going through their releases with Genius's list… ${at.first} of ${at.second}", style = T.sub)
            }
        }
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
        // Their albums and singles first, every one of them; then every song; then your songs and their top songs.
        if (albums.isNotEmpty()) {
            item { ArtistFold(if (onlyMine) "Your albums and singles" else "Albums and singles", "%,d".format(albums.size), isOpen("albums") || picking) { toggle("albums") } }
            if (isOpen("albums") || picking) {
            if (picking) item { Section(if (pickedAlbums.isEmpty()) "PICK RELEASES: A TAP TICKS ONE" else "${pickedAlbums.size} PICKED") }
            else item { Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Select", false) { ui.startPick("artist-albums") }; SortMenu("artist-albums", ARTIST_ALBUM_SORTS) } }
            val shown = albumsSorted.filter { fits(ui, it.al.title, it.al.sub) }
            // Hold a release: picking starts with it ticked (its own menu is then the bar's); picking, a tap ticks
            laidOutItems(shown, ui, key = { "aa-" + shown[it].al.id + "#" + it }, names = { shown[it].al.title }, row = { j ->
                val a = shown[j]
                PlaylistRow(a.al, a.ownCover(), onLongClick = { if (picking) albumMenu(a) else pickAlbum(a) },
                    checked = if (picking) a.al.id in pickedAlbums else null, badge = whereBadge(a)) { if (picking) pickAlbum(a) else openAlbum(a) }
            }) { j, mod, compact ->
                val a = shown[j]
                Tile(a.ownCover() ?: a.al.thumb.ifEmpty { null }, a.al.title, a.al.sub, mod, compact, extra = whereBadge(a)?.lowercase()?.replaceFirstChar { it.uppercase() }.orEmpty(),
                    ico = Ico.DISC, lit = picking && a.al.id in pickedAlbums,
                    onLongClick = { if (picking) albumMenu(a) else pickAlbum(a) }) { if (picking) pickAlbum(a) else openAlbum(a) }
            }
        } }
        // Featured on: songs by others that credit them, between their releases and every song of their own. A list
        // of songs; a release only when all of it features them (rare).
        if (featAlbums.isNotEmpty() || featSongs.isNotEmpty()) {
            item { ArtistFold("Featured on", "%,d".format(featAlbums.size + featSongs.size), isOpen("feat")) { toggle("feat") } }
            if (isOpen("feat")) {
            val src = i?.source.orEmpty()
            laidOutItems(featAlbums, ui, key = { "af-" + featAlbums[it].first.id + "#" + it }, names = { featAlbums[it].first.title }, row = { j ->
                val (al, songs) = featAlbums[j]
                PlaylistRow(al, null, onLongClick = { ui.choices = al.title to listOf("Hide from ${page.name}'s page" to { org.omarchy.music.ArtistShown.albums(page.name, listOf(al.title), hide = true) }) }) {
                    ui.pages += Ui.Page.AlbumPage(al, src, Music.yourCopies(songs)) }
            }) { j, mod, compact ->
                val (al, songs) = featAlbums[j]
                Tile(al.thumb.ifEmpty { null }, al.title, al.sub, mod, compact, ico = Ico.DISC,
                    onLongClick = { ui.choices = al.title to listOf("Hide from ${page.name}'s page" to { org.omarchy.music.ArtistShown.albums(page.name, listOf(al.title), hide = true) }) }) {
                    ui.pages += Ui.Page.AlbumPage(al, src, Music.yourCopies(songs)) }
            }
            tracks(featSongs, ui, "artist", section = "artist-feat") { t -> t.album }
        } }
        // Every song they have, always (no button to ask for it): the songs of all their releases, and of the
        // compilations they're on, each once; with a Genius artist picked, the ones only Genius lists too. It fills
        // in a moment after the page opens (each release is asked for).
        if (i != null && !onlyMine && (i.albums.isNotEmpty() || i.also.isNotEmpty() || gSongs?.isNotEmpty() == true)) {
            val list = everyList
            val at = everyAt
            // How much of it is here: each song once (the list has each once, however many releases it's on), the ones
            // you hid off the page not counted; kept for the Artists tab
            // (your unreleased songs aren't counted: the count is of what can be streamed and downloaded)
            val have = list?.let { l -> l.filter { !runCatching { org.omarchy.music.Unreleased.has(it) }.getOrDefault(false) }
                .distinctBy { org.omarchy.music.ArtistShownRules.songKey(it.title) } }
            val counted = have?.let { l -> l.count { yours(it) } to l.count { !yours(it) } }
            // (kept as soon as it's counted: the list's heading below is drawn once it is)
            if (counted != null && have.isNotEmpty()) item(key = "every-count") { androidx.compose.runtime.LaunchedEffect(counted) {
                org.omarchy.music.ReleaseCounts.put(org.omarchy.music.ReleaseCounts.artistKey(page.name), counted.first, counted.second) } }
            // (while the list is gone through again, the count kept from last time, at once)
            val kept = org.omarchy.music.ReleaseCounts.of(org.omarchy.music.ReleaseCounts.artistKey(page.name))
            item { ArtistFold("Every song", if (list != null) "%,d".format(have?.size ?: list.size) + (counted?.let { " · " + org.omarchy.music.ReleaseCounts.text(it) } ?: "")
                else kept?.let { "%,d · ".format(it.first + it.second) + org.omarchy.music.ReleaseCounts.text(it) } ?: "…", isOpen("every")) { toggle("every") } }
            if (isOpen("every")) {
            if (list == null) item {
                Text(if (at != null) "Going through their releases… ${at.first} of ${at.second}" else "Going through their releases…", style = T.sub,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            } else if (list.isEmpty()) item { Text("No songs could be listed", style = T.sub, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            else {
                item { Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) { SortMenu("artist-every", everySorts) } }
                // Sorted by plays: each song's plays first, then its album
                val everyByPlays = everySort.startsWith("plays")
                tracks(list, ui, "artist", section = "artist-every") { t -> if (everyByPlays) listOf(playsText(playsOf(t)), t.album).filter { it.isNotEmpty() }.joinToString(" · ") else t.album }
            }
        } }
        if (mine.isNotEmpty()) {
            item { ArtistFold(if (onlyStream) "Streaming songs you've played" else "Your songs", "%,d".format(mine.size), isOpen("mine")) { toggle("mine") } }
            if (isOpen("mine")) {
            // As songs, or as your releases of theirs: with a big discography, tapping the album beats scrolling to it
            val asReleases = songsView == "releases" && !onlyStream && ownShown.isNotEmpty()
            item {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!onlyStream && ownAlbums.isNotEmpty()) {
                        var open by remember { mutableStateOf(false) }
                        val names = linkedMapOf("songs" to "Songs", "releases" to "Albums, EPs and singles")
                        Box {
                            Chip("Show: ${names[if (asReleases) "releases" else "songs"]} ▾", false) { open = true }
                            DropdownMenu(open, { open = false }, containerColor = C.light) {
                                names.forEach { (k, l) ->
                                    DropdownMenuItem({ Text((if (k == (if (asReleases) "releases" else "songs")) "✓  " else "     ") + l, style = T.body) }, {
                                        open = false; songsView = k; org.omarchy.music.Prefs.setText("artist_songs_view", k) })
                                }
                            }
                        }
                    }
                    SortMenu("artist-songs", songSorts, "album")   // one sort for both: the releases follow it
                }
            }
            if (asReleases) {
                laidOutItems(ownShown, ui, key = { "ao-" + ownShown[it].first.id + "#" + it }, names = { ownShown[it].first.title }, row = { j ->
                    val a = ArtistAlbum(ownShown[j].first, ownShown[j], streamed = false)
                    // Sorted by plays: how often you played its songs, by its name
                    val al = if (sort.startsWith("plays")) a.al.copy(sub = listOf(a.al.sub, playsText(a.own!!.second.sumOf { playsOf(it) })).filter { it.isNotEmpty() }.joinToString(" · ")) else a.al
                    PlaylistRow(al, a.ownCover(), onLongClick = { albumMenu(a) }) { openAlbum(a) }
                }) { j, mod, compact ->
                    val a = ArtistAlbum(ownShown[j].first, ownShown[j], streamed = false)
                    Tile(a.ownCover() ?: a.al.thumb.ifEmpty { null }, a.al.title, a.al.sub, mod, compact, ico = Ico.DISC,
                        onLongClick = { albumMenu(a) }) { openAlbum(a) }
                }
            } else tracks(mine, ui, "artist") { t -> playsText(playsOf(t)) }
        } }
        if (onlyMine && mine.isEmpty() && albums.isEmpty()) item { Empty("None of their music is yours yet",
            "You have no downloads or files of ${page.name}'s. Pick “Everything” at the top to see their songs on the streaming services.") }
        when {
            i == null && failed == null -> if (!onlyMine) item { Section("LOADING…") }
            i == null -> if (!onlyMine && mine.isEmpty() && albums.isEmpty()) item { Empty("Couldn't find them", failed ?: "") }
            top.isNotEmpty() -> {
                item { ArtistFold(if (onlyMine) "Your copies of their top songs"
                    else "Top songs" + (if (i.topSource != i.source) " · ${SOURCES[i.topSource]}" else ""), "%,d".format(top.size), isOpen("top")) { toggle("top") } }
                if (isOpen("top")) tracks(top, ui, "artist", radio = true, section = "artist-top")
            }
        }
        // What's left out of the page: hidden by you, or not on Genius's list. Nothing is gone; any of it can be put back.
        if (concertsOn) {
            val (shows, why) = concerts
            item { ArtistFold("Concerts", if (shows != null) "%,d".format(shows.size) else "", isOpen("concerts")) { toggle("concerts") } }
            if (isOpen("concerts")) {
                val city = org.omarchy.music.Concerts.city()
                if (shows == null) item { Text(why.ifEmpty { "Looking at Ticketmaster's listings…" }, style = T.sub, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                else if (shows.isEmpty()) item { Text("No shows listed" + (if (city.isNotEmpty()) " in $city (Settings › Remote & sync › Concert dates has the city)" else "") +
                    ". Ticketmaster lists only the shows it sells", style = T.sub, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                else items(shows.size, key = { "concert-" + shows[it].date + shows[it].venue }) { i ->
                    val sh = shows[i]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp).clip(R.card)
                        .clickable(enabled = sh.url.isNotEmpty()) { runCatching { links.openUri(sh.url) } }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(org.omarchy.music.ConcertRules.line(sh), style = T.body)
                            Text(listOf(sh.name, sh.country).filter { it.isNotBlank() }.joinToString(" · "), style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (setlistsOn) SetlistLink(page.name, sh, setlists.first) { setlistOpen = it }
                        if (sh.url.isNotEmpty()) Text("Tickets ›", style = T.sub.copy(color = C.accent))
                    }
                }
            }
        }
        // Setlists: the songs of their recent shows and what they've been playing on this tour (without a key, how to get one)
        item { ArtistFold("Setlists", setlists.first?.let { "%,d".format(it.size) } ?: "", isOpen("setlists")) { toggle("setlists") } }
        if (isOpen("setlists")) {
            val today = java.time.LocalDate.now().toString()
            setlistItems(page.name, setlistsOn, setlists, concerts.first?.firstOrNull { it.date >= today }) { setlistOpen = it }
        }
        // (features hidden by you are among them, to put back)
        val leftFeats = feats?.songs.orEmpty().filter { !featShown(it) }
        val left = leftSongs.size + leftAlbums.size + leftFeats.size
        if (left > 0) {
            item { ArtistFold("Left out of this page", "%,d".format(left), isOpen("left")) { toggle("left") } }
            if (isOpen("left")) {
                item { Text("Hidden by you" + (if (theirs != null) ", not on Genius's list for them" else "") + ", or a single that is on an album or EP already. A song's Options (an album's menu) has “Show on ${page.name}'s page”.",
                    style = T.sub, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
                laidOutItems(leftAlbums, ui, key = { "al-" + leftAlbums[it].al.id + "#" + it }, names = { leftAlbums[it].al.title }, row = { j ->
                    val a = leftAlbums[j]
                    PlaylistRow(a.al, a.ownCover(), onLongClick = { if (picking) albumMenu(a) else pickAlbum(a) },
                        checked = if (picking) a.al.id in pickedAlbums else null) { if (picking) pickAlbum(a) else openAlbum(a) }
                }) { j, mod, compact ->
                    val a = leftAlbums[j]
                    Tile(a.ownCover() ?: a.al.thumb.ifEmpty { null }, a.al.title, a.al.sub, mod, compact, ico = Ico.DISC, lit = picking && a.al.id in pickedAlbums,
                        onLongClick = { if (picking) albumMenu(a) else pickAlbum(a) }) { if (picking) pickAlbum(a) else openAlbum(a) }
                }
                tracks((leftSongs + leftFeats).distinctBy { it.id }, ui, "artist-left", section = "artist-left") { t -> t.album }
            }
        }
    }
}

/** An album on an artist's page: the service's (streamed) or one of yours, with your copy of it (album, songs) if you have one. */
private val ARTIST_ALBUM_SORTS = listOf("order", "created", "created-old", "name", "name-desc")

/** What an artist's page found, kept in memory for a while so the page shows at once the next time. */
private class ArtistSnap(val at: Long, val info: ArtistInfo, val choices: List<org.omarchy.music.ArtistChoice>, val every: List<Track>?, val everyFor: String?,
                         val gChoices: List<org.omarchy.music.ArtistChoice>, val gPick: String?, val gId: String?,
                         val gSongs: List<org.omarchy.music.GeniusRules.Hit>?, val sorted: org.omarchy.music.GeniusArtists.Sorted?,
                         val gAlbums: List<Album>, val feats: org.omarchy.music.FeatureRules.Featured?, val folded: Set<String>,
                         val musicVer: Int, val songs: List<Track>, val ownAlbums: List<Pair<Album, List<Track>>>)
private val artistSnaps = LinkedHashMap<String, ArtistSnap>()

/** Which of each artist page's sections are open (by the page: [LocalPlace]). */
private val artistOpen = HashMap<String, Set<String>>()

/** A section of an artist's page: a row with its name, how many it holds, and an arrow that folds it open or shut,
 *  as a section of Settings. */
@Composable
private fun ArtistFold(title: String, count: String, open: Boolean, onClick: () -> Unit) {
    val bg = if (C.apple && C.isLight) androidx.compose.ui.graphics.Color(0xFFF2F2F7) else C.light
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(R.card).background(bg).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (count.isNotEmpty()) Text(count, style = T.sub, modifier = Modifier.padding(end = 10.dp))
        Text(if (open) "▾" else "▸", style = T.sub)
    }
}

/** (genius: one only Genius lists, added so the discography is whole.) */
private data class ArtistAlbum(val al: Album, val own: Pair<Album, List<Track>>?, val streamed: Boolean, val genius: Boolean = false) {
    /** Your copy's cover (the one in its files), or null. */
    fun ownCover(): Any? = own?.second?.firstOrNull()?.let { artModel(it) }
}

/** The dropdown at the top of an artist's page: everything (streaming and yours) or only your music (downloads and files). */
@Composable
private fun ArtistFromMenu(from: String, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val names = linkedMapOf("all" to "Everything (streaming and yours)", "mine" to "Only my music (downloaded and local)",
        "stream" to "Only streaming (not my music)")
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
    var missing by remember(page) { mutableStateOf<List<org.omarchy.music.MissingSong>>(emptyList()) }
    var picking by remember(page) { mutableStateOf(false) }
    // …each found on your streaming service, so it plays, queues and can be picked like any streamed song
    var streams by remember(page) { mutableStateOf<List<Pair<org.omarchy.music.MissingSong, Track>>>(emptyList()) }
    val artist = page.tracks?.firstOrNull()?.artist?.let { org.omarchy.music.splitArtists(it).firstOrNull() ?: it } ?: ""
    LaunchedEffect(page, missing) {
        streams = emptyList()
        val want = missing
        if (want.isNotEmpty()) Music.bg({ org.omarchy.music.AlbumMissing.streams(artist, page.album.title, want) }) { got, _ -> if (missing == want) streams = got.orEmpty() }
    }
    // An EP of yours (as the EPs tab sorts it) says EP in its missing section, and is looked for as an EP
    val kind = remember(page) {
        page.tracks?.takeIf { page.source == "local" && it.isNotEmpty() }
            ?.let { runCatching { Music.releaseKind(page.album.title, it, page.album.id.startsWith("single:")) }.getOrNull() } ?: "album"
    }
    // Refresh (the pill under its name) reads it again; an album of yours also reads itself again as your music
    // changes, so a missing song you download comes in at its place.
    var reload by remember(page) { mutableStateOf(0) }
    var shown by remember(page) { mutableStateOf(page.album) }   // the album as it is named now (its tags may change while its page is open)
    var said by remember(page) { mutableStateOf(0) }   // the last Refresh that was answered ("… is up to date")
    val local = page.source == "local" && page.tracks != null
    LaunchedEffect(page, reload, if (local) Music.musicVersion else 0) {
        val was = page.tracks
        val asked = reload
        fun done() { if (asked > said) { said = asked; Music.toast("“${page.album.title}” is up to date") } }
        val src = Music.sources[page.source]
        if (was == null || (!local && asked > said && src != null)) {   // a service's album: its songs from the service, in its order
            Music.bg({ src?.album(page.album.id) ?: emptyList() }) { res, err ->
                tracks = res ?: was ?: emptyList()
                if (err != null) Music.toast("Couldn't open the album: ${err.message?.take(80)}") else done()
            }
            return@LaunchedEffect
        }
        if (tracks == null) tracks = was
        if (!local) { done(); return@LaunchedEffect }
        // Yours: its songs as they are now, by track number; then what's missing from it
        val reread = asked > said
        Music.bg({
            val (al, now) = org.omarchy.music.AlbumSongs.current(page.album.id, was, reread = reread)
            // (an album of your unreleased music: no "missing" songs looked for, nor counted)
            Triple(al, now, if (now.isEmpty() || now.any { runCatching { org.omarchy.music.Unreleased.has(it) }.getOrDefault(false) }) emptyList() else runCatching { Music.albumMissing(al?.title ?: page.album.title, now.firstOrNull()?.artist?.let { org.omarchy.music.splitArtists(it).firstOrNull() ?: it } ?: artist, now, kind) }.getOrDefault(emptyList()))
        }) { res, _ ->
            if (res != null) { res.first?.let { shown = it }; tracks = res.second; missing = res.third
                // kept for the Albums tab: what of it is here and what you'd still only stream
                if (res.second.isNotEmpty() && org.omarchy.music.AlbumMissing.listed(res.first?.title ?: page.album.title, artist))
                    org.omarchy.music.ReleaseCounts.put(org.omarchy.music.ReleaseCounts.albumKey(page.album.title, artist), res.second.distinctBy { org.omarchy.music.songKey(it) }.size, res.third.size) }
            done()
        }
    }
    PlaceColumn(Modifier.fillMaxSize()) {
        item { BackRow(shown.title) { ui.pages.removeAt(ui.pages.lastIndex) } }
        item {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(page.album.thumb.ifEmpty { null } ?: (tracks ?: page.tracks)?.firstOrNull()?.let { artModel(it) }, 96.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(shown.title, style = T.title.copy(fontSize = T.title.fontSize * 1.2f))
                    if (shown.sub.isNotEmpty()) Text(shown.sub, style = T.sub)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                Pill("↻  Refresh", quiet = true) { reload++ }
            }
        }
        val t = tracks
        if (t == null) item { Section("LOADING…") } else {
            item { ListActions(t, onDj = { ui.djFrom(Dj.From("songs", name = shown.title, songs = t)) },
                pin = if (page.source.isEmpty()) null else org.omarchy.music.Pin("album", shown.id, shown.title, shown.sub, shown.thumb, page.source)) }
            tracks(t, ui, "album")
            if (missing.isNotEmpty()) {
                item { Section("MISSING FROM THIS ${if (kind == "ep") "EP" else "ALBUM"} · ${missing.size}" + if (streams.isNotEmpty()) " · TAP ONE TO STREAM IT" else "") }
                // The ones found on your streaming service: rows like any streamed song's (play, queue, pick, playlists)
                val found = streams.map { it.second }
                if (found.isNotEmpty()) tracks(found, ui, "album", section = "missing")
                // …and the ones not found there (or not looked up yet): named, as before
                val rest = missing.filter { m -> streams.none { it.first == m } }
                items(rest.size) { i ->
                    val m = rest[i]
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).graphicsLayer(alpha = 0.55f),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.n}", style = T.sub, modifier = Modifier.width(28.dp))
                        Text(m.title, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (m.secs > 0) Text(org.omarchy.music.fmtTime(m.secs.toDouble()), style = T.sub)
                    }
                }
                item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Download missing tracks…") { picking = true }
                    // To hear them first: on Listen later, where Review downloads the ones to keep
                    if (found.isNotEmpty()) Pill("Listen later (%,d)".format(found.size), quiet = true) { org.omarchy.music.ListenLater.add(found) }
                } }
            }
        }
    }
    if (picking) MissingPicker(page.album.title, missing, { picking = false }) { want -> picking = false; Music.downloadMissing(artist, page.album.title, want) }
}

/** Download missing tracks…: each song Download or Leave alone. */
@Composable
private fun MissingPicker(album: String, missing: List<org.omarchy.music.MissingSong>, close: () -> Unit, go: (List<org.omarchy.music.MissingSong>) -> Unit) {
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
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // The bell: what the app's own daily look found (Settings › Playback › New releases by themselves), with how
            // many are new since it was last opened. Tap it again to go back to the tabs
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.TopEnd) {
                IconButton(Ico.BELL, color = if (nr.bell || nr.unseen > 0) C.accent else C.fg, pad = 6.dp,
                    desc = if (nr.unseen > 0) "${nr.unseen} new releases found for you" else "New releases found for you") { nr.openBell(!nr.bell) }
                if (nr.unseen > 0) Text(if (nr.unseen > 99) "99+" else "${nr.unseen}",
                    style = T.sub.copy(color = C.darker, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    modifier = Modifier.background(C.accent, androidx.compose.foundation.shape.CircleShape).padding(horizontal = 4.dp))
            }
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
                if (nr.picked.isNotEmpty()) Pill("Hide these artists", quiet = true) {
                    val names = nr.picked.values.map { it.second }.filter { it.isNotBlank() }.distinct()
                    ui.confirm = "Hide ${names.size} artist${if (names.size != 1) "s" else ""} from New releases? They're unfollowed (Utilities › Followed artists)." to {
                        org.omarchy.music.Follows.unfollow(names); nr.picked.clear(); nr.selecting = false }
                }
                if (nr.picked.isNotEmpty()) Pill("Clear", quiet = true) { nr.picked.clear() }
            }
            Section(if (nr.picked.isEmpty()) "PICK RELEASES OR ARTISTS, ON ANY TAB" else "${nr.picked.size} PICKED (ON ANY TAB)")
        }
        nr.status?.let { Section(it.uppercase()) }
    }
    if (nr.scanned <= 0 && !nr.bell) {
        item { Empty("New releases", "${TAP.cap()} Refresh… to look at every artist with songs on your $DEVICE for what they've put out in the last " +
            "week, month or year. Nothing is checked on its own.") }
        return
    }
    if (!nr.bell) item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("album" to "Albums", "single" to "Singles", "ep" to "EPs", "artists" to "Artists").forEach { (k, l) -> Chip(l, nr.kind == k) { nr.kind = k } }
            // Only the ones you can play (on Tidal or YouTube Music), or the Deezer-only ones too
            Chip("Hide Deezer-only", nr.hideDeezer()) { nr.setHideDeezer(!nr.hideDeezer()) }
        }
    }
    val whenText = mapOf("week" to "THE LAST WEEK", "month" to "THE LAST MONTH", "year" to "THE LAST YEAR")[nr.range] ?: ""
    // The newest looked up ahead (Tidal first, then YouTube Music), so each says where it plays from and starts at once
    // (With Hide Deezer-only on, every one is looked up, so all those only on Deezer go)
    val hideDeezer = nr.hideDeezer()
    item { LaunchedEffect(nr.found, hideDeezer) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { org.omarchy.music.ReleaseLookup.warm(nr.found.sortedByDescending { it.date }, if (hideDeezer) nr.found.size else 20) } } } }
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
            if (!nr.selecting) "Select" to { nr.selecting = true; nr.toggle(key, pick) } else null,
            // Not an artist of yours (a name a scan once got wrong, someone you're done with): unfollowed, their releases gone
            pick.second.takeIf { it.isNotBlank() }?.let { a -> "Hide $a (unfollow)" to {
                ui.confirm = "Hide $a from New releases? They're unfollowed (Utilities › Followed artists brings them back)." to { org.omarchy.music.Follows.unfollow(listOf(a)) } } })
    }
    if (nr.bell) {
        val told = nr.told.filter { fits(ui, it.artist, it.title) && nr.shown(it) }
        val kinds = mapOf("album" to "Album", "single" to "Single", "ep" to "EP")
        item {
            Section(if (told.isEmpty()) "THE BELL · NOTHING NEW FOUND YET" else "THE BELL · ${told.size} FOUND FOR YOU · NEWEST FIRST")
            if (told.isEmpty()) Empty("Nothing under the bell yet", "With New releases by themselves switched on (Settings › Playback), the app looks once a day, " +
                "while it is open, at what your artists put out in the last week: the artists of the songs you have as files or downloads, and of the songs in " +
                "your Radon Player playlists. What it finds is listed here, each release once, and the bell shows how many are new. ${TAP.cap()} the bell again for the tabs.")
            else Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) { Pill("Clear the bell", quiet = true) { nr.clearBell() } }
        }
        items(told.size, key = { "nrb-${nr.key(told[it])}-$it" }) { i ->
            val r = told[i]
            PlaylistRow(Album(r.link, r.title, "${r.artist} · ${kinds[r.type] ?: "Album"} · ${r.date}" + releaseWhere(r), r.cover), onLongClick = { menu(r.title, listOf(r), nr.key(r), r to r.artist) },
                checked = if (nr.selecting) nr.key(r) in nr.picked else null) { find(r) }
        }
        return
    }
    if (nr.kind == "artists") {
        val by = nr.found.filter { fits(ui, it.artist, it.title) && nr.shown(it) }.groupBy { it.artist }.toList()
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
    val rows = nr.found.filter { it.type == nr.kind && fits(ui, it.artist, it.title) && nr.shown(it) }
    val name = mapOf("album" to "ALBUMS", "single" to "SINGLES", "ep" to "EPS")[nr.kind] ?: ""
    item { Section("${rows.size} ${if (rows.size == 1) name.dropLast(1) else name} · $whenText · NEWEST FIRST") }
    if (rows.isEmpty()) item { Empty("None in this time", "Refresh… a longer time, or look at the other tabs.") }
    laidOutItems(rows, ui, key = { "nr-${rows[it].link}-${rows[it].title}-$it" }, names = { rows[it].title }, otherNames = listOf({ rows[it].artist }), row = { i ->
        val r = rows[i]
        PlaylistRow(Album(r.link, r.title, "${r.artist} · ${r.date}" + releaseWhere(r), r.cover), onLongClick = { menu(r.title, listOf(r), nr.key(r), r to r.artist) },
            checked = if (nr.selecting) nr.key(r) in nr.picked else null) { find(r) }
    }) { i, mod, compact ->
        val r = rows[i]
        val on = nr.selecting && nr.key(r) in nr.picked
        Tile(r.cover.ifEmpty { null }, r.title, r.artist, mod, compact, extra = r.date + releaseWhere(r), ico = Ico.DISC, lit = on,
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
                    checked = if (picking) pl.id in picked else null,
                    badge = org.omarchy.music.Sync.mark(pl), badgeOn = pl.synced == true) {
                    if (picking) { if (pl.id in picked) picked.remove(pl.id) else picked[pl.id] = pl.tracks } else ui.openMine = pl.id
                }
            }
        }
    }
}

/** Repeats: the songs you let play again with Repeat one (RepeatLoopRules), the most repeated first. */
fun androidx.compose.foundation.lazy.LazyListScope.repeatsView(ui: Ui) {
    val RR = org.omarchy.music.RepeatLoopRules
    org.omarchy.music.RepeatLoops.version   // read, so the list is made again when a song repeats
    TopArt.tick   // …and when a cover is found online
    org.omarchy.music.StatHides.version
    val periods = listOf("all" to ("All time" to 0), "year" to ("The last year" to 365), "month" to ("The last 30 days" to 30), "week" to ("The last 7 days" to 7))
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            periods.forEach { (k, l) -> Chip(l.first, Music.repeatsPeriod == k) { Music.repeatsPeriod = k } }
            Chip("Collage…", false) { ui.collageFor = "repeats" }   // as on Most played: the covers in a grid, each with its repeats
        }
    }
    val days = periods.firstOrNull { it.first == Music.repeatsPeriod }?.second?.second ?: 0
    val since = org.omarchy.music.RepeatLoops.since(days)
    val saved = org.omarchy.music.RepeatLoops.all()
    val top = RR.top(saved, since, 200)
    // The repeats your imported history and scrobbles show, and the two things said about repeats
    item {
        val RL = org.omarchy.music.RepeatLoops; val P = org.omarchy.music.Prefs
        var notes by remember { mutableStateOf(P.on(RL.NOTES, true)) }
        var nudge by remember { mutableStateOf(P.on(RL.NUDGE, true)) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (RL.historyBusy) "Looking through your history…" else "Look through my history again", false) { if (!RL.historyBusy) Music.bg({ RL.fromHistory() }) }
                Chip("Notes at milestones", notes) { notes = !notes; P.set(RL.NOTES, notes) }
                Chip("DJ offers a change after a long run", nudge) { nudge = !nudge; P.set(RL.NUDGE, nudge) }
            }
            Text(RL.historyText.ifEmpty { "What you played here before, your Last.fm scrobbles and your imported history are looked through by themselves (after an import, or when Last.fm is connected) for a song played again the moment it ended, and what is found is kept. Nothing recorded that Repeat one was on, so those are a good guess; from now on every repeat is counted as it happens" },
                style = T.sub, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (top.isEmpty()) item { Empty(if (days == 0) "No repeats yet" else "No repeats in this time",
        "Put Repeat one on (the repeat button, twice) and let a song play again: each time it starts over by itself is counted here. " +
        "Only Repeat one counts, and they are counted from this version on.") }
    else {
        // your own copy of a song the history named
        // Your copy of each song as it is today. A song counted here is kept as it was on the day (the stream it was
        // then, or a file where it was then); once it is downloaded, moved or tagged again that is no longer a song of
        // yours, and it showed with no cover and as not on this device though its name matched your file exactly.
        // A song that isn't yours has no picture here (a history gives none): its cover is looked up, as on Most played.
        // (one you hid from the stats lists isn't shown: by the name it has here, or the one your file of it has)
        val rows = top.filter { !org.omarchy.music.StatHides.song(it.first.track) }
            .mapIndexed { i, p -> TopArt.withCover(Music.mineOf(p.first.track)) to RR.line(i + 1, p.second, p.first.best) }
            .filter { !org.omarchy.music.StatHides.song(it.first) }
        item {
            Section(RR.summary(top.size, RR.total(saved, since)).uppercase())
            LaunchedEffect(top.size, Music.repeatsPeriod, Music.musicVersion) { TopArt.lookUp { rows.map { it.first } } }
        }
        val lines = rows.associate { it.first.id to it.second }
        tracks(rows.map { it.first }, ui, "repeats") { t -> lines[t.id] ?: "" }
    }
}

/** Milestones: which ones show (all, songs or artists) and in what order (newest first, or biggest first). */
private var milestonesShow by mutableStateOf("all")
private var milestonesBiggest by mutableStateOf(false)

/** Milestones (Statistics): every milestone you've reached, a song or an artist getting to 100, 250, 500, 1,000…
 *  plays, each dated by the play that got it there (Most played shows the last month's five). Songs, artists or both;
 *  newest first (by year) or biggest first (by how many plays). A song of yours plays when tapped; an artist opens. */
fun androidx.compose.foundation.lazy.LazyListScope.milestonesView(ui: Ui) {
    val all = org.omarchy.music.Stats.milestones
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("All", milestonesShow == "all") { milestonesShow = "all" }
            Chip("Songs", milestonesShow == "songs") { milestonesShow = "songs" }
            Chip("Artists", milestonesShow == "artists") { milestonesShow = "artists" }
            Spacer(Modifier.width(8.dp))
            Chip("Newest first", !milestonesBiggest) { milestonesBiggest = false }
            Chip("Biggest first", milestonesBiggest) { milestonesBiggest = true }
        }
    }
    if (all.isEmpty()) { item { Empty("No milestones yet", "A song or an artist reaching 100 plays is the first. They're counted from your stats " +
        "(Last.fm and your imported history) and the plays here, as Most played is.") }; return }
    val rows = all.filter { when (milestonesShow) { "songs" -> it.what == "song"; "artists" -> it.what == "artist"; else -> true } }
        .sortedWith(if (milestonesBiggest) compareBy({ -it.plays }, { -it.at }) else compareBy({ -it.at }, { -it.plays }))
    item {
        val songs = all.count { it.what == "song" }; val artists = all.size - songs
        val top = all.maxWithOrNull(compareBy({ it.plays }, { it.what == "artist" }))
        Text("%,d milestones: %,d for songs, %,d for artists".format(all.size, songs, artists) +
            (top?.let { ". The biggest: %,d plays of %s".format(it.plays, if (it.what == "song") "“${it.name}”" else it.name) } ?: "") +
            ". Each is dated by the play that got it there; one from plays with no date (an all-time count from an import) is undated.",
            style = T.sub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    if (rows.isEmpty()) { item { Empty("None of these yet", "Try All.") }; return }
    // In sections: each year (newest first), or each number of plays (biggest first)
    val zone = java.time.ZoneId.systemDefault()
    var head = ""
    for (m in rows) {
        val h = if (milestonesBiggest) "%,d PLAYS".format(m.plays) else if (m.at <= 0) "UNDATED" else java.time.Instant.ofEpochSecond(m.at).atZone(zone).year.toString()
        if (h != head) { head = h; item { Section(h) } }
        item { MilestoneRow(ui, m) }
    }
}

/** One milestone: ✦, how many plays of what, and when. */
@Composable
private fun MilestoneRow(ui: Ui, m: org.omarchy.music.Stats.Milestone) {
    val song = m.what == "song"
    val day = if (m.at <= 0) "undated" else java.time.Instant.ofEpochSecond(m.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    Row(Modifier.fillMaxWidth().clickable {
            if (song) {
                val mine = Music.mineOf(Track("", title = m.name, artist = m.artist))
                if (mine.isFile) Music.playList(listOf(mine)) else Music.toast("“${m.name}” isn't on this device")
            } else ui.pages += Ui.Page.Artist(m.name, null)
        }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", style = T.title.copy(color = C.accent), modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text("%,d plays · %s".format(m.plays, if (song) m.name else m.name), style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((if (song) (m.artist.ifEmpty { "Song" }) else "Artist") + " · " + day, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Unplayed and neglected: the songs of yours you have never played, and the ones you haven't played in a long time
 *  (RestedRules; the counts and times are your stats' and the plays here). */
fun androidx.compose.foundation.lazy.LazyListScope.neglectedView(ui: Ui) {
    val N = org.omarchy.music.Neglected; val RR = org.omarchy.music.RestedRules
    org.omarchy.music.StatHides.version
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Never played", N.show == "unplayed") { N.show = "unplayed" }
            RR.SPANS.forEach { (k, l, _) -> Chip("Not in ${l.replaceFirstChar { it.lowercase() }}", N.show == k) { N.show = k } }
        }
    }
    val all = N.rowsNow()
    if (all == null) { item { Section("ADDING UP YOUR PLAYS…") }; return }
    val nowS = System.currentTimeMillis() / 1000
    val rows = N.shown(all, N.show, nowS).filter { !org.omarchy.music.StatHides.song(it.track) }
    val never = N.show == "unplayed"
    item {
        Text("Counted from your stats (Last.fm and your imported history) and the plays here: a song you played somewhere else that your stats know of isn't unplayed. " +
            "A song with plays but no date (an all-time count from an import) is in neither list. Settings › Playback › Shuffle › Unheard and neglected makes every shuffle out of these.",
            style = T.sub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        if (rows.isNotEmpty()) Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Play") { Music.playList(rows.map { it.track }) }
            Pill("Shuffle", quiet = true) { Music.playList(rows.map { it.track }, shuffled = true) }
        }
    }
    if (rows.isEmpty()) { item { Empty(if (never) "Nothing unplayed" else "Nothing neglected that long",
        if (never) "Every song of yours has been played at least once." else "No song of yours has gone that long without a play. Try a shorter time.") }; return }
    val span = RR.SPANS.firstOrNull { it.first == N.show }?.second?.uppercase().orEmpty()
    item { Section(if (never) "%,d OF YOUR %,d SONGS NEVER PLAYED".format(rows.size, all.size)
        else "%,d OF YOUR %,d SONGS NOT PLAYED IN $span · LONGEST AGO FIRST".format(rows.size, all.size)) }
    val lines = rows.associate { it.track.id to RR.line(it.plays, it.last, nowS) }
    tracks(rows.map { it.track }, ui, "neglected") { t -> lines[t.id] ?: "" }
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

/** On this day (Most played, under Milestones): what you were listening to this week one, two, three… years ago, from
 *  your listening history, each year with a Play button. The DJ's "On this day" set is made from the same songs. */
@Composable
fun OnThisDay() {
    val years by androidx.compose.runtime.produceState<List<Pair<Int, List<Track>>>>(emptyList(), Music.musicVersion) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { org.omarchy.music.TasteService.get().dayAgo.entries.sortedBy { it.key }.map { it.key to it.value.distinctBy { t -> t.id }.take(30) }
                .filter { it.second.size >= 3 } }.getOrDefault(emptyList()) }
    }
    if (years.isEmpty()) return
    Section("ON THIS DAY")
    years.forEach { (ago, songs) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (ago == 1) "A year ago this week" else "$ago years ago this week", style = T.body.copy(color = C.accent))
                Text(songs.take(3).joinToString(" · ") { it.title } + (if (songs.size > 3) " · and %,d more".format(songs.size - 3) else ""), style = T.sub, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Pill("Play", quiet = true) { Music.playFrom(songs.map { Music.mineOf(it) }, 0, false) }
        }
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

    /** A song that isn't yours and has no picture, with the cover looked up for it (else its artist's picture) once
     *  there is one: the row is then drawn again with it. */
    fun withCover(t: Track): Track = if (yours(t) || t.thumb.isNotEmpty()) t
        else org.omarchy.music.SongCovers.shown(t.title, t.artist).let { if (it.isEmpty()) t else t.copy(thumb = it) }

    /** The covers of songs that aren't yours and have no picture, looked up online one by one (each shows as it's
     *  found and is remembered, so never looked up again). Run from a list's LaunchedEffect: leaving the list stops it. */
    suspend fun lookUp(make: () -> List<Track>) {
        val todo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { make() }.getOrDefault(emptyList())
                .filter { !yours(it) && it.thumb.isEmpty() && !org.omarchy.music.SongCovers.settled(it.title, it.artist) }
                .distinctBy { org.omarchy.music.songPickKey(it.title, it.artist) }.take(150)
        }
        for (t in todo) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {
                // Nothing has the song's cover (a demo, a song no shop sells): its artist's picture stands in, so that
                // is looked up too when it never was
                if (org.omarchy.music.SongCovers.look(t).isEmpty()) {
                    val who = org.omarchy.music.ownArtists(t.artist).firstOrNull() ?: t.artist
                    if (who.isNotBlank() && !org.omarchy.music.Pictures.settled(org.omarchy.music.Pictures.known(who))) org.omarchy.music.Pictures.artist(who)
                }
            } }
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
        val artists = t.artists.filter { fits(ui, it.first) && !org.omarchy.music.StatHides.artist(it.first) }
        item {
            Section("${artists.size} ARTISTS · LISTENED TO LONGEST FIRST")
            LaunchedEffect(artists.size, Music.topPeriod) { Music.lookUpArtists(artists.take(400).map { it.first }) }
        }
        laidOutItems(artists, ui, key = { "tta-" + artists[it].first }, names = { artists[it].first }, row = { i ->
            val (n, v) = artists[i]
            PlaylistRow(Album(n, n, fmtHours(v)), artistArt(n, ""), onLongClick = { artistMenu(ui, n, "") }) { ui.pages += Ui.Page.Artist(n) }
        }) { i, mod, compact ->
            val (n, v) = artists[i]
            Tile(artistArt(n, ""), n, fmtHours(v), mod, compact, round = true, ico = Ico.PERSON, onLongClick = { artistMenu(ui, n, "") }) { ui.pages += Ui.Page.Artist(n) }
        }
        return
    }
    val list = timeItemsOf(what, t).filter { fits(ui, it.title, it.artist) &&
        !(if (what == "songs") org.omarchy.music.StatHides.song(it.tracks.first().copy(title = it.title, artist = it.artist)) else org.omarchy.music.StatHides.album(it.title, it.artist)) }
    fun hide(x: TimeItem) {
        if (what == "songs") ui.choices = x.title to listOf("Hide from stats" to {
            org.omarchy.music.StatHides.hideSongs(listOf(x.tracks.first().copy(title = x.title, artist = x.artist))); Music.toast("${x.title} is hidden from the stats lists. Settings › Hidden from stats brings it back") })
        else albumStatMenu(ui, x.title, x.artist)
    }
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
    laidOutItems(list, ui, key = { "tt-$what-" + list[it].key }, names = { list[it].title }, row = { i ->
        val x = list[i]
        Music.savedVersion   // (a song downloaded since loses its mark)
        val streams = what == "songs" && !onThisDevice(Music.localOf(x.tracks.first()))   // a song that isn't on this device says so
        PlaylistRow(Album(x.key, x.title, "${x.artist} · ${fmtHours(x.secs)}"), topArt(cover(x)), onLongClick = { hide(x) },
            badge = if (streams) STREAMING else null, badgeOn = false) { open(i) }
    }) { i, mod, compact ->
        val x = list[i]
        Music.savedVersion
        val streams = what == "songs" && !onThisDevice(Music.localOf(x.tracks.first()))
        Tile(topArt(cover(x), tilePx(compact)), x.title, x.artist, mod, compact, extra = fmtHours(x.secs),
            ico = if (what == "songs") Ico.NOTE else Ico.DISC, onLongClick = { hide(x) },
            over = { if (streams) StreamCorner(compact) }) { open(i) }
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
    laidOutItems(list, ui, key = { "mpl-" + list[it].mood }, names = { list[it].mood }, row = { i ->
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
    val albums = runCatching { Music.topAlbums(Music.topPeriod) }.getOrDefault(emptyList()).filter { fits(ui, it.title, it.artist) && !org.omarchy.music.StatHides.album(it.title, it.artist) }
    item {
        Section(if (albums.isEmpty()) "NO ALBUMS PLAYED IN THIS TIME" else "${albums.size} ALBUMS · MOST PLAYED FIRST")
        LaunchedEffect(albums.size, Music.topPeriod) { TopArt.lookUp { albums.map { TopArt.album(it.title, it.artist, it.tracks) } } }
    }
    fun open(al: org.omarchy.music.MostPlayed.TopAlbum) {
        ui.pages += Ui.Page.AlbumPage(Album("top-" + al.key, al.title, al.artist, al.tracks.first().thumb), "local", al.tracks)
    }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    laidOutItems(albums, ui, key = { "tal-" + albums[it].key }, names = { albums[it].title }, row = { i ->
        val al = albums[i]
        PlaylistRow(Album(al.key, al.title, "${al.artist} · ${plays(al.plays)}"), topArt(TopArt.album(al.title, al.artist, al.tracks)),
            onLongClick = { albumStatMenu(ui, al.title, al.artist) }) { open(al) }
    }) { i, mod, compact ->
        val al = albums[i]
        Tile(topArt(TopArt.album(al.title, al.artist, al.tracks), tilePx(compact)), al.title, al.artist, mod, compact, extra = plays(al.plays), ico = Ico.DISC,
            onLongClick = { albumStatMenu(ui, al.title, al.artist) }) { open(al) }
    }
}


/** Most played artists: every play of theirs over the period; an artist opens their page. */
fun androidx.compose.foundation.lazy.LazyListScope.topArtistsItems(ui: Ui) {
    val artists = runCatching { Music.topArtists(Music.topPeriod) }.getOrDefault(emptyList()).filter { fits(ui, it.name) && !org.omarchy.music.StatHides.artist(it.name) }
    item {
        Section(if (artists.isEmpty()) "NO ARTISTS PLAYED IN THIS TIME" else "${artists.size} ARTISTS · MOST PLAYED FIRST")
        LaunchedEffect(artists.size, Music.topPeriod) { Music.lookUpArtists(artists.take(400).map { it.name }) }
    }
    fun plays(n: Int) = "%,d play%s".format(n, if (n != 1) "s" else "")
    laidOutItems(artists, ui, key = { "tar-" + artists[it].name }, names = { artists[it].name }, row = { i ->
        val a = artists[i]
        PlaylistRow(Album(a.name, a.name, plays(a.plays)), artistArt(a.name, a.thumb), onLongClick = { artistMenu(ui, a.name, a.thumb) }) { ui.pages += Ui.Page.Artist(a.name) }
    }) { i, mod, compact ->
        val a = artists[i]
        Tile(artistArt(a.name, a.thumb), a.name, plays(a.plays), mod, compact, round = true, ico = Ico.PERSON, onLongClick = { artistMenu(ui, a.name, a.thumb) }) { ui.pages += Ui.Page.Artist(a.name) }
    }
}

/** Where a new release plays from, after its date: " · Tidal", " · YouTube Music", " · Deezer only: can't play yet", or
 *  nothing before it's been looked up. Read in Compose: it shows as soon as it's known. */
internal fun releaseWhere(r: org.omarchy.music.NewReleases.Release): String {
    org.omarchy.music.ReleaseLookup.version
    return org.omarchy.music.ReleaseLookup.whereLabel(r).let { if (it.isEmpty()) "" else " · $it" }
}
