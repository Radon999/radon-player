package org.omarchy.music.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import org.omarchy.music.Album
import org.omarchy.music.FolderItem
import org.omarchy.music.Music
import org.omarchy.music.Remote
import org.omarchy.music.Track
import org.omarchy.music.fmtTime
import org.omarchy.music.splitArtists

/** Where a song row sits, for its menu: a playlist of yours, the queue, the library… */
data class RowPlace(val group: List<Track>, val index: Int, val kind: String = "", val playlistId: String? = null,
                    val queueIndex: Int = -1, val radio: Boolean = false)

/** What the screens share: the tab, the open pages, what's selected, the open sheets. */
class Ui {
    companion object {
        /** The window's Ui (the PC's keyboard shortcuts reach the screens through it). */
        var current: Ui? = null
    }
    var albumsLow by mutableStateOf(false)             // Albums tab: the ones with six songs or fewer
    var tab by mutableStateOf("player")                // what's in front: the player, or a list (from the ☰ menu)
    var player by mutableStateOf(false)                // (kept for the screens that close it: the player is a tab now)
    var drawer by mutableStateOf(false)                // the ☰ menu is out
    var showQueues by mutableStateOf(false)            // the queues switcher is open
    var lyricsSearch by mutableStateOf(false)          // the lyrics' search box is open
    var lyricsEditAsk by mutableStateOf(false)         // the phone's Lyrics widget's ✎: open the Lyrics tab's Edit
    var lyricsCard by mutableStateOf<Pair<org.omarchy.music.Track, org.omarchy.music.Lyrics.Got>?>(null)   // Card: making one of this song's
    var rearrange by mutableStateOf<RearrangeWhat?>(null)       // Rearrange: your playlists, or a playlist's songs, being put in order
    var playlistCoverFor by mutableStateOf<String?>(null)       // a playlist's menu › Set a cover…: the playlist a picture is being picked for
    var songClip by mutableStateOf<org.omarchy.music.Track?>(null)        // a song's Options › Share a clip with sound: the song
    var presetEdit by mutableStateOf<List<String>?>(null)                  // Personalization's Presets menu › Edit: the ready-made presets being edited
    var songCard by mutableStateOf<org.omarchy.music.Track?>(null)        // a song's Options › Share as a picture card: the song
    var creditsFor by mutableStateOf<org.omarchy.music.Track?>(null)      // a song's Options › Credits: the song
    var makerEdit by mutableStateOf<String?>(null)    // the look, layout or visualizer of yours being changed (null: a new one)
    var pane by mutableStateOf("art")                  // what the player shows: art, lyrics, visualizer, queue
    var musicView by mutableStateOf("all")
    var openMine by mutableStateOf<String?>(null)
    var openFolder by mutableStateOf<String?>(null)   // a folder of playlists, open in the Library or Music › Playlists
    var folderFor by mutableStateOf<FolderItem?>(null)   // a playlist being put in a folder
    var openList by mutableStateOf<Pair<Album, List<Track>?>?>(null)
    val pages = mutableStateListOf<Page>()            // artist and album pages, newest last
    var selecting by mutableStateOf(false)
    // Advanced multi-select: what you pick stays picked as you go from list to list, and the
    // Options can be used one after another on it (unless "close after an option" is on).
    val selected = androidx.compose.runtime.mutableStateMapOf<String, Track>()
    var selectPool by mutableStateOf(listOf<Track>())  // the list in front, to select from
    var closeAfter by mutableStateOf(org.omarchy.music.Prefs.on("close_after", true))   // kept, as the PC app does
    var showOptions by mutableStateOf(false)
    var selectPlace by mutableStateOf<RowPlace?>(null)
    var query by mutableStateOf("")
    var found by mutableStateOf<org.omarchy.music.Found?>(null)
    var foundIn by mutableStateOf("")                   // where it was searched: tidal, ytm, local…
    var searchSeq = 0
    var searched = ""   // the last query searched for (searching as you type skips it)
    var resultsAreList by mutableStateOf(false)
    var searching by mutableStateOf(false)
    var sheet by mutableStateOf<String?>(null)          // "accounts", "eq", "tidal-login", "spotify-login"…
    /** Settings' sections unfolded: none each time Settings opens (openSettings), or the one it was opened for. */
    val settingsOpen = androidx.compose.runtime.mutableStateListOf<String>()
    fun openSettings(section: String? = null) { settingsOpen.clear(); section?.let { settingsOpen += it }; sheet = "accounts" }
    var pickPlaylistFor by mutableStateOf<List<Track>?>(null)
    var editTagsFor by mutableStateOf<List<Track>?>(null)
    var taggerFor by mutableStateOf<List<Track>?>(null)
    var jointArtists by mutableStateOf(false)   // Settings › Joint artists…
    var similarArtists by mutableStateOf(false)   // Settings › Similar artists…
    var duplicates by mutableStateOf(false)   // Settings › Duplicates in Most played…
    var statHides by mutableStateOf(false)    // Settings › Hidden from stats…
    var identifyFor by mutableStateOf<org.omarchy.music.Track?>(null)   // Identify by sound…
    var guide by mutableStateOf(false)   // Features
    var changelog by mutableStateOf(false)   // Changelog
    var tool by mutableStateOf<String?>(null)   // Settings › Your music: "fixup", "live", "empty" or "skip"
    var cardFor by mutableStateOf<String?>(null)     // Share card…: "songs", "albums", "artists" or "time"
    var collageFor by mutableStateOf<String?>(null)   // Collage…: "songs", "albums" or "artists"   // Tagger…: tags, cover, the files' details, lyrics
    var deleteFor by mutableStateOf<List<Track>?>(null)
    var speedFor by mutableStateOf<List<Track>?>(null)
    var volumeFor by mutableStateOf<List<Track>?>(null)     // Volume…: a song's own boost
    var songEqFor by mutableStateOf<List<Track>?>(null)     // Custom EQ…: a song's own EQ on this output
    var upgradeFor by mutableStateOf<List<Track>?>(null)    // Upgrade…: these songs (empty: all of yours)
    var damagedOpen by mutableStateOf(false)                // Storage and damaged files: choosing what to do with each
    var exportFor by mutableStateOf<Pair<String, List<Track>>?>(null)   // Export…: a playlist as .m3u8
    val dupeRemove = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()   // Duplicates: Remove (true) or Keep, by copy
    var coverFor by mutableStateOf<List<List<Track>>?>(null)   // the albums still to pick a cover for
    var artistPictureFor by mutableStateOf<String?>(null)
    var mergeArtist by mutableStateOf<String?>(null)   // Merge into…: this artist
    var mySongFor by mutableStateOf<Track?>(null)       // This is a song of mine…: this song
    var filter by mutableStateOf<String?>(null)        // the page's own search (null: closed)
    /** Each place's search, kept while you are further in: Back comes back to what you searched for there. */
    val filters = HashMap<String, String>()
    var filterPlace = ""                                // the place [filter] is for

    /** Where you were when you went to Now playing (a search's results, an artist's page, a playlist…): Back on Now
     *  playing goes back there, as you left it. null: you weren't anywhere (the app opened on Now playing), and Back
     *  opens the menu as it always did. Forgotten once you've gone back, or gone to another tab yourself. */
    class Place(val tab: String, val view: String, val pages: List<Page>, val openMine: String?, val openFolder: String?,
                val openList: Pair<Album, List<Track>?>?, val filters: Map<String, String>)
    var cameFrom by mutableStateOf<Place?>(null)

    fun rememberPlace() {
        val f = HashMap(filters)
        filter?.takeIf { it.isNotBlank() && filterPlace.isNotEmpty() }?.let { f[filterPlace] = it }
        cameFrom = Place(tab, musicView, pages.toList(), openMine, openFolder, openList, f)
    }

    /** Back to where you were before Now playing. */
    fun goBackToPlace() {
        val p = cameFrom ?: return
        cameFrom = null
        pages.clear(); pages.addAll(p.pages)
        openMine = p.openMine; openFolder = p.openFolder; openList = p.openList
        filter = null; filters.clear(); filters.putAll(p.filters); filterPlace = ""
        musicView = p.view
        tab = p.tab
        drawer = false
    }
    var reviewFor by mutableStateOf<org.omarchy.music.Playlist?>(null)
    var castPick by mutableStateOf(false)               // Cast…: the devices to play on
    var releasePick by mutableStateOf<List<org.omarchy.music.NewReleases.Release>?>(null)   // releases to make discovery playlists of   // a discovered list, being reviewed
    var tagPreview by mutableStateOf<Pair<List<String>, Boolean>?>(null)   // Tag now…: these songs, from scratch or not
    var width by mutableStateOf(392f)                  // the lists' width, in dp: how many covers fit across
    var choices by mutableStateOf<Pair<String, List<Pair<String, () -> Unit>>>?>(null)   // a long-press's options

    /** Cover… for tracks: one album at a time. */
    fun pickCovers(tracks: List<Track>) {
        coverFor = tracks.groupBy { org.omarchy.music.Covers.albumKey(it) }.values.toList().ifEmpty { null }
    }
    var askName by mutableStateOf<Triple<String, String, (String) -> Unit>?>(null)
    var confirm by mutableStateOf<Pair<String, () -> Unit>?>(null)
    var playlistsBySource = mutableStateListOf<Pair<String, List<Album>?>>()

    sealed class Page {
        data class Artist(val name: String, val prefer: String? = null) : Page()
        data class AlbumPage(val album: Album, val source: String, val tracks: List<Track>? = null) : Page()
        /** A list of songs with a name: a genre, a folder. */
        data class Songs(val title: String, val tracks: List<Track>, val kind: String = "", val id: String = "") : Page()
    }

    fun startSelecting(pool: List<Track>, place: RowPlace?, first: Track?) {
        if (!selecting) selected.clear()
        selecting = true
        selectPool = pool
        selectPlace = place
        first?.let { selected[pickKey(it, place)] = it }
    }

    /** An option was used on the songs picked: the selection closes, unless you keep it for more. */
    fun afterOption() { if (closeAfter) stopSelecting() }

    /** What a picked row is known by: the song, and in the queue its place too (the same song can wait there twice:
     *  each is its own row, see [org.omarchy.music.SelectRules]). */
    fun pickKey(t: Track, place: RowPlace?) = org.omarchy.music.SelectRules.key(t.id, place?.kind ?: "", place?.queueIndex ?: -1)
    private fun poolKey(i: Int) = org.omarchy.music.SelectRules.key(selectPool[i].id, selectPlace?.kind ?: "", if (selectPlace?.kind == "queue") i else -1)
    fun picked(t: Track, place: RowPlace?) = pickKey(t, place) in selected
    fun pick(t: Track, place: RowPlace?) { selected[pickKey(t, place)] = t }
    fun selectAllHere() { selectPool.indices.forEach { selected[poolKey(it)] = selectPool[it] } }
    fun deselectAllHere() { selectPool.indices.forEach { selected.remove(poolKey(it)) } }
    fun invertHere() { selectPool.indices.forEach { val k = poolKey(it); if (k in selected) selected.remove(k) else selected[k] = selectPool[it] } }
    /** The songs between the first and the last picked one in the list in front. */
    fun selectBetween() {
        val at = selectPool.indices.filter { poolKey(it) in selected }
        if (at.size < 2) return
        for (i in at.first()..at.last()) selected[poolKey(i)] = selectPool[i]
    }

    /** Start the DJ on f (see [org.omarchy.music.Dj.From]) and show it. */
    fun djFrom(f: org.omarchy.music.Dj.From) {
        Music.djStart(f)
        stopSelecting()
        tab = "music"; pages.clear(); openMine = null; musicView = "dj"; player = false
    }

    fun stopSelecting() {
        selecting = false
        selected.clear()
        albumsPicked.clear()
        pickingAlbums = false
    }

    /** Albums: picking some (Select), for Options on all their songs. */
    var pickingAlbums by mutableStateOf(false)
    var pickWhere by mutableStateOf("")   // the list picking: albums, eps, singles, genre, folder, artists, playlists
    fun pickingIn(where: String) = pickingAlbums && pickWhere == where
    fun startPick(where: String, id: String? = null, songs: List<Track>? = null) {
        albumsPicked.clear(); pickWhere = where; pickingAlbums = true
        if (id != null && songs != null) albumsPicked[id] = songs
    }
    val albumsPicked = androidx.compose.runtime.mutableStateMapOf<String, List<Track>>()
    /** What the list picking calls them ("albums"), and its own options for them (PickDock). */
    var pickNoun by mutableStateOf("")
    var pickExtra by mutableStateOf<List<Pair<String, () -> Unit>>>(emptyList())

    /** ☰: asks for the menu even if the app thinks it's out already (so a tap always opens it). */
    var drawerAsk by mutableStateOf(0)
    fun openDrawer() { drawer = true; drawerAsk++ }

    /** Options (the sheet) for these songs: an album's, or the albums picked. */
    fun optionsFor(songs: List<Track>) {
        selected.clear()
        songs.forEach { selected[it.id] = it }
        showOptions = true
    }

    /** Picking songs: a long-press is the Options button's sheet for them, this song picked too. */
    fun optionsHere(t: Track, place: RowPlace? = null) {
        selected[pickKey(t, place)] = t
        showOptions = true
    }

    /** One song's options (a long-press or ⋮): the same sheet as a selection's, with that song's own extras. */
    var oneSong by mutableStateOf<Track?>(null)
    fun songMenu(t: Track, place: RowPlace) {
        if (selecting) { optionsHere(t, place); return }
        oneSong = t
        selectPlace = place
        showOptions = true
    }

    fun toggle(t: Track, place: RowPlace? = null) { val k = pickKey(t, place); if (k in selected) selected.remove(k) else selected[k] = t }
    fun selectedTracks() = selected.values.toList()
}

@Composable
fun ArtImage(model: Any?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(if (size >= 120.dp) R.card else R.small).background(C.light), contentAlignment = Alignment.Center) {
        Icon(Ico.NOTE, C.muted.copy(alpha = 0.6f), size * 0.42f)   // under the cover: shows when there's none
        if (model != null) AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size))
    }
}

/** Artists' names; a tap opens the artist's page (a choice of them, for a few). */
@Composable
fun ArtistLinks(text: String, ui: Ui, prefer: String?, style: androidx.compose.ui.text.TextStyle = T.sub, suffix: String = "") {
    val names = splitArtists(text)
    var menu by remember { mutableStateOf(false) }
    // Only the name itself opens the artist: Android would otherwise grow its touch area to 48dp,
    // over the song's title above it, and a tap on the title would open the artist instead of playing.
    val vc = LocalViewConfiguration.current
    val exact = remember(vc) { object : ViewConfiguration by vc { override val minimumTouchTargetSize: DpSize get() = DpSize.Zero } }
    Box {
        CompositionLocalProvider(LocalViewConfiguration provides exact) {
            Text(text + suffix, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (names.isEmpty() || ui.selecting) Modifier else Modifier.clickable {
                    if (names.size == 1) ui.pages += Ui.Page.Artist(names[0], prefer) else menu = true
                })
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            names.forEach { n ->
                DropdownMenuItem(text = { Text(n) }, onClick = { menu = false; ui.pages += Ui.Page.Artist(n, prefer) })
            }
        }
    }
}

/** The modifier keys held (the PC's window tells): Ctrl/Shift+click to pick songs. */
object HeldKeys {
    @Volatile var ctrl = false
    @Volatile var shift = false
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(t: Track, place: RowPlace, ui: Ui, extra: String = "", onPlay: () -> Unit) {
    // What the row shows: your own copy of the song (file or download: its cover, tags, format) when you have one, from
    // the first draw. t itself stays the row's song for its menu, picking and removing (a playlist's own entry).
    val remoteQueue = place.kind == "queue" && Remote.active   // the desktop's queue: its songs as they are
    val own = remember(t, remoteQueue, Music.musicVersion, Music.savedVersion) { if (remoteQueue) t else Music.localOf(t) }
    val nowId = Music.shownTrack()?.id
    val playing = nowId != null && (nowId == t.id || nowId == own.id) && (place.kind != "queue" || place.queueIndex == Music.shownIndex())
    var menu by remember { mutableStateOf(false) }
    Music.savedVersion   // redraw when downloads change
    val saved = Music.lib.items[own.id]
    val checked = ui.picked(t, place)
    if (ui.selecting) androidx.compose.runtime.SideEffect { if (ui.selectPool !== place.group) { ui.selectPool = place.group; ui.selectPlace = place } }
    // The theme's spacing and cover size (Compact, Roomy…), and its card behind each row (Cards, Glass, Outline…)
    val art = Themes.art(48.dp)
    val carded = Themes.current.cards != CardStyle.NONE
    Box(Modifier.swipeToAct(t, ui)) {   // (the phone: a swipe to either side does what Settings › Playback › Swipe a song says)
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = Themes.gap(6.dp), vertical = Themes.gap(if (carded) 3.dp else 1.dp))
                .themeCard(R.card)
                .clip(R.card)
                .background(if (playing || checked) C.accent.copy(alpha = if (C.apple) 0.08f else 0.14f)
                    else if (C.isLight && !C.apple) C.light else Color.Transparent)
                .pressable(
                    onClick = {   // on the PC, Ctrl+click picks songs and Shift+click picks a run of them (as the Python app)
                        when {
                            !ui.selecting && (HeldKeys.ctrl || HeldKeys.shift) -> ui.startSelecting(place.group, place, t)
                            ui.selecting && HeldKeys.shift -> { ui.pick(t, place); ui.selectBetween() }
                            ui.selecting -> ui.toggle(t, place)
                            else -> onPlay()
                        }
                    },
                    // Hold (right-click on a computer): picking starts at once, with this song picked. Its own menu is
                    // the ⋮ on the row; while picking, holding a song opens the options for what's picked.
                    onLongClick = { if (ui.selecting) ui.optionsHere(t, place) else ui.startSelecting(place.group, place, t) },
                )
                .padding(horizontal = Themes.gap(8.dp), vertical = Themes.gap(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui.selecting) Checkbox(checked = checked, onCheckedChange = { ui.toggle(t, place) },
                colors = CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.darker, uncheckedColor = C.muted))
            Box(contentAlignment = Alignment.Center) {
                ArtImage(if (remoteQueue) Remote.artModel(t) else artModel(own), art, Modifier.padding(start = 2.dp))
                if (playing) Box(Modifier.padding(start = 2.dp).size(art).clip(R.small).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center) { Icon(if (Music.paused) Ico.PAUSE else Ico.PLAY, C.accent, 20.dp) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(own.title, style = T.title.copy(color = if (playing) C.accent else C.fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val bits = buildList {
                    if (own.duration > 0 && !C.apple) add(fmtTime(own.duration))   // Apple Music's: the length at the end of the row
                    // …and how often you played it, on every list (not the other device's queue: its plays are its own)
                    (if (remoteQueue) extra else ShownPlays.with(extra, own)).takeIf { it.isNotEmpty() }?.let { add(it) }
                }.joinToString(" · ")
                ArtistLinks(own.artist, ui, own.source, suffix = if (bits.isNotEmpty()) "  · $bits" else "")
            }
            if (C.apple && own.duration > 0) Text(fmtTime(own.duration), style = T.time, modifier = Modifier.padding(end = 6.dp))
            // The file's format on every song of yours ("FLAC 24", "MP3"), and ⤓ on the ones downloaded from a service
            org.omarchy.music.FlacDepths.version
            val fmt = if (saved != null) formatBadge(saved.format, saved.quality) else org.omarchy.music.FlacDepths.badge(own.file)
            if (fmt != null) Badge(fmt)
            // ...and where it plays from when there is no format to show: a song that isn't on this device says so
            // (not the other device's queue: its songs are that device's)
            // Other device's songs: a file over there (it's copied, never streamed or downloaded), not "Streaming"
            else if (place.kind == "theirs") Badge(org.omarchy.music.SongCopy.badge, true)
            else if (!remoteQueue) { val here = onThisDevice(own); Badge(if (here) "Local" else STREAMING, here) }
            if (saved != null) Icon(Ico.DOWNLOAD, C.accent, 16.dp, Modifier.padding(start = 4.dp))
            else if (Music.dl.busy(t.id) || Music.downloads.any { it.id == t.id }) Text("…", style = T.time)
            if (!ui.selecting) Box(Modifier.clip(R.pill).clickable { menu = true }.padding(6.dp)) { Icon(Ico.MORE, C.muted, 18.dp) }
        }
        if (C.apple && !playing && !checked && Themes.rowLines) RowLine(
            Modifier.align(Alignment.BottomStart).padding(start = Themes.gap(14.dp) + art + 16.dp, end = 12.dp))   // Apple's thin line between rows
        TrackMenu(t, place, ui, menu) { menu = false }
    }
}

/** How often you played each song, shown on every song's row: the counts the stats keep (Last.fm and your imports;
 *  the plays here only without Last.fm), as sorting by plays uses them. Worked out off the screen's thread, again
 *  whenever your music or the stats change. */
object ShownPlays {
    private var map by mutableStateOf<Map<Pair<String, String>, Int>>(emptyMap())
    private var forVersion = -1
    private var forStats: Any? = null
    private var busy = false
    /** The song's plays, or 0 (none, or not worked out yet). */
    fun of(t: Track): Int {
        val v = Music.musicVersion; val s = org.omarchy.music.Stats.songs
        if ((v != forVersion || s !== forStats) && !busy) {
            busy = true; forVersion = v; forStats = s
            Music.bg({ Music.playCounts() }) { m, _ -> busy = false; if (m != null) map = m }
        }
        return map[org.omarchy.music.PlaysApart.playKey(t)] ?: 0
    }
    /** A list's plays (a playlist's, a genre's, a folder's…): its songs' added up, each song once. */
    fun ofAll(songs: List<Track>): Int = songs.distinctBy { org.omarchy.music.PlaysApart.playKey(it) }.sumOf { of(it) }
    /** "1,204 plays", or "" when none of it has been played. */
    fun allText(songs: List<Track>): String = ofAll(songs).let { n -> if (n <= 0) "" else "%,d play%s".format(n, if (n != 1) "s" else "") }
    /** "24 songs · 1,204 plays" (the plays left off when there are none). */
    fun songsAndPlays(songs: List<Track>): String = listOf("%,d song%s".format(songs.size, if (songs.size != 1) "s" else ""), allText(songs))
        .filter { it.isNotEmpty() }.joinToString(" · ")
    /** "12 plays", or "" when it hasn't been played. */
    fun text(t: Track): String = of(t).let { n -> if (n <= 0) "" else "%,d play%s".format(n, if (n != 1) "s" else "") }
    /** What a row shows: its own words, with its plays (unless they say them already). */
    fun with(extra: String, t: Track): String = if ("play" in extra) extra else text(t).let { p -> listOf(extra, p).filter { it.isNotEmpty() }.joinToString(" · ") }
}

/** The mark of a song that isn't on this device, in every list: it plays as a stream until you download it. */
const val STREAMING = "Streaming"

/** Whether a song (as [Music.localOf] gives it) is on this device, a file of yours or a download; if not it plays as a stream. */
fun onThisDevice(own: Track): Boolean = own.isFile || Music.lib.has(own.id)

/** A download's mark: its kind and, for FLAC, its bit depth from its quality ("24-bit/96 kHz"): "FLAC 24", "M4A". */
fun formatBadge(format: String, quality: String): String? {
    if (format.isBlank()) return null
    val bits = Regex("(\\d+)-bit").find(quality)?.groupValues?.get(1)
    return format.uppercase() + if (bits != null && format.lowercase() in setOf("flac", "wav")) " $bits" else ""
}

/** The album song t is on: yours (Albums) if you have it, else its page on the song's service (or Tidal's, YouTube Music's). */
fun goToAlbum(ui: Ui, t: Track) {
    val album = t.album.trim()
    if (album.isEmpty()) { Music.toast("This song has no album in its tags"); return }
    if (ui.selecting) ui.stopSelecting()
    val want = org.omarchy.music.norm(org.omarchy.music.bareTitle(album))
    val mine = runCatching { Music.albums() }.getOrDefault(emptyList())
    val key = org.omarchy.music.Covers.albumKey(t)
    val names = splitArtists(t.artist).map { org.omarchy.music.norm(it) }.filter { it.isNotEmpty() }.toSet()
    val hit = mine.firstOrNull { (_, songs) -> songs.any { org.omarchy.music.Covers.albumKey(it) == key } }
        ?: mine.filter { org.omarchy.music.norm(org.omarchy.music.bareTitle(it.first.title)) == want }
            .let { same -> same.firstOrNull { org.omarchy.music.norm(it.first.sub) in names } ?: same.singleOrNull() }
    if (hit != null) { ui.pages += Ui.Page.AlbumPage(hit.first, "local", hit.second); return }
    val name = listOf(t.source, "tidal", "ytm").firstOrNull { k -> Music.sources[k]?.let { k != "tidal" || it.signedIn } == true }
    val src = name?.let { Music.sources[it] }
    if (src == null) { Music.toast("“$album” isn't in your music, and no service is connected to look it up"); return }
    val lead = splitArtists(t.artist).firstOrNull().orEmpty()
    Music.toast("Looking for “$album”…")
    Music.bg({ src.find("$album $lead".trim()).albums }) { albums, _ ->
        val same = albums.orEmpty().filter { org.omarchy.music.norm(org.omarchy.music.bareTitle(it.title)) == want }
        val pick = same.firstOrNull { org.omarchy.music.norm(lead) in org.omarchy.music.norm(it.sub) } ?: same.firstOrNull()
        if (pick == null) Music.toast("Couldn't find “$album” on ${org.omarchy.music.SOURCES[name] ?: name}")
        else ui.pages += Ui.Page.AlbumPage(pick, name!!)
    }
}

/** A song's menu (long-press, right-click or ⋮): opens the options sheet for it, the same look as for selected songs. */
@Composable
fun TrackMenu(t: Track, place: RowPlace, ui: Ui, open: Boolean, dismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(open) {
        if (open) { dismiss(); ui.songMenu(t, place) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistRow(p: Album, art: Any? = null, onLongClick: (() -> Unit)? = null, checked: Boolean? = null, badge: String? = null, badgeOn: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(R.card)
        .pressable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (checked != null) Tick(checked, Modifier.padding(end = 10.dp))   // picking: a tick, and a tap ticks it
        ArtImage(art ?: p.thumb.takeIf { it.isNotEmpty() }, 52.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(p.title, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(p.sub, if (p.count.isNotEmpty()) "${p.count} songs" else "").filter { it.isNotEmpty() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = T.sub, maxLines = 1)
            // A mark under it, as a song's "FLAC 24": an artist's release says whether it's yours or streams
            // (and a playlist of yours whether it syncs: lit when it does, dim when it is this device's only)
            if (badge != null) Box(Modifier.padding(top = 3.dp)) { Badge(badge, badgeOn) }
        }
        if (checked == null) Icon(Ico.BACK, C.muted, 16.dp, Modifier.graphicsLayer(rotationZ = 180f))
    }
}

fun LazyListScope.tracks(list: List<Track>, ui: Ui, kind: String = "", playlistId: String? = null, radio: Boolean = false,
                         section: String = kind, extra: (Track) -> String = { "" }) {
    val shown = if (ui.filter.isNullOrBlank() || !laidOut(ui)) list else list.filter { trackFits(ui, it) }
    // Keys unique on the page even when two lists of it hold the same song (an artist's songs of yours and their top songs)
    laidOutItems(shown, ui, key = { "$section:${shown[it].id}#$it" }, names = { shown[it].title },
        otherNames = listOf({ shown[it].artist }, { shown[it].album }), row = { i ->
        val t = shown[i]
        TrackRow(t, RowPlace(shown, i, kind, playlistId, radio = radio), ui, extra(t)) { Music.playFrom(shown, i, radio) }
    }) { i, mod, compact ->
        val t = shown[i]
        TrackTile(t, RowPlace(shown, i, kind, playlistId, radio = radio), ui, extra(t), mod, compact) { Music.playFrom(shown, i, radio) }
    }
}

