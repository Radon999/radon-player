package org.omarchy.music.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omarchy.music.Album
import org.omarchy.music.Music
import org.omarchy.music.Pin
import org.omarchy.music.Track
import org.omarchy.music.norm

// 1.4: list, grid and compact grid layouts, searching the page you're on, and Pinned.

/** The pages that can be a list, a grid or a compact grid (and searched on their own). */
private val LAID_OUT = setOf("all", "recent", "favorites", "unfavorited", "top", "skips", "playlists", "playlist", "pinned",
    "library", "albums", "eps", "singles", "artists", "genres", "folders", "flac24", "flac32", "artist-page", "album-page", "songs-page", "repeat", "newreleases")

/** Which page is in front, for its layout. */
fun layoutKey(ui: Ui): String = when (ui.pages.lastOrNull()) {
    is Ui.Page.Artist -> "artist-page"
    is Ui.Page.AlbumPage -> "album-page"
    is Ui.Page.Songs -> "songs-page"
    null -> when (ui.tab) {
        "music" -> if (ui.openMine != null) "playlist" else if (ui.openFolder != null) "playlists" else ui.musicView
        "library" -> if (Music.browseLocal) "all" else if (ui.openList != null) "playlist" else "library"
        else -> ui.tab
    }
}

/** The page in front has a layout to pick, and its own search. */
fun laidOut(ui: Ui) = layoutKey(ui) in LAID_OUT

/** The page in front's layout: list, grid or compact. */
fun layoutNow(ui: Ui): String = layoutKey(ui).let { if (it in LAID_OUT) Music.layoutOf(it) else "list" }

/** How many covers fit across, for a grid: small, as they were, bigger (two across a phone), biggest (one). */
private fun columns(ui: Ui, layout: String): Int = when (layout) {
    "compact" -> maxOf(3, ((ui.width - 16f) / 94f).toInt())
    "large" -> maxOf(2, ((ui.width - 16f) / 170f).toInt())
    "huge" -> maxOf(1, ((ui.width - 16f) / 300f).toInt())
    else -> maxOf(2, ((ui.width - 16f) / 128f).toInt())
}

/** The grid being drawn (for the size of its covers and names). */
val LocalTileLayout = androidx.compose.runtime.compositionLocalOf { "grid" }

/** How big a cover to fetch for a tile. */
@Composable
fun tilePx(compact: Boolean): Int = when (LocalTileLayout.current) { "huge" -> 1080; "large" -> 640; else -> if (compact) 226 else 400 }

/** What's typed in the page's search fits one of these. */
fun fits(ui: Ui, vararg text: String): Boolean {
    val q = ui.filter?.trim().orEmpty()
    if (q.isEmpty()) return true
    val words = norm(q).split(" ").filter { it.isNotEmpty() }
    val all = text.joinToString(" ") { norm(it) }
    return words.all { it in all }
}

fun trackFits(ui: Ui, t: Track) = fits(ui, t.title, t.artist, t.album)

/** A list laid out as the page's layout: rows, or rows of covers. */
fun <T> LazyListScope.laidOutItems(list: List<T>, ui: Ui, key: (Int) -> String,
                                   row: @Composable (Int) -> Unit, tile: @Composable (Int, Modifier, Boolean) -> Unit) {
    val layout = layoutNow(ui)
    if (layout == "list") { items(list.size, key = key) { row(it) }; return }
    val k = columns(ui, layout)
    val n = list.size
    items((n + k - 1) / k, key = { "$layout$k|" + key(it * k) }) { r ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            for (j in 0 until k) {
                val i = r * k + j
                if (i < n) androidx.compose.runtime.CompositionLocalProvider(LocalTileLayout provides layout) {
                    tile(i, Modifier.weight(1f), layout == "compact")
                } else Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A cover with its name under it (and whose, and a count). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(art: Any?, title: String, sub: String, modifier: Modifier, compact: Boolean, extra: String = "", round: Boolean = false,
         ico: Ico = Ico.NOTE, lit: Boolean = false, onLongClick: (() -> Unit)? = null, over: @Composable BoxScope.() -> Unit = {},
         onClick: () -> Unit) {
    val shape = if (round) CircleShape else R.card
    val big = when (LocalTileLayout.current) { "huge" -> 2; "large" -> 1; else -> 0 }   // bigger covers, bigger names
    Column(modifier.padding(if (compact) 3.dp else 5.dp).clip(R.card)
        .background(if (lit) C.accent.copy(alpha = 0.10f) else Color.Transparent)
        .pressable(onClick = onClick, onLongClick = onLongClick).padding(if (compact) 3.dp else 4.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(C.light), contentAlignment = Alignment.Center) {
            Icon(ico, C.muted.copy(alpha = 0.5f), if (compact) 24.dp else 34.dp)
            if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            over()
        }
        Text(title, style = T.title.copy(fontSize = if (compact) 12.sp else (13.5f + big * 1.5f).sp, color = if (lit) C.accent else C.fg), maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        if (sub.isNotEmpty()) Text(sub, style = T.sub.copy(fontSize = if (compact) 11.sp else (12f + big).sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (extra.isNotEmpty()) Text(extra, style = T.time.copy(fontSize = if (compact) 10.sp else 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A song as a cover: a tap plays it, a long-press has its options; the count (plays, skips) stays under it. */
@Composable
fun TrackTile(t: Track, place: RowPlace, ui: Ui, extra: String, modifier: Modifier, compact: Boolean, onPlay: () -> Unit) {
    val playing = Music.shownTrack()?.id == t.id
    val checked = t.id in ui.selected
    var menu by remember { mutableStateOf(false) }
    if (ui.selecting) androidx.compose.runtime.SideEffect { if (ui.selectPool !== place.group) { ui.selectPool = place.group; ui.selectPlace = place } }
    Box(modifier) {
        Tile(artModel(t, tilePx(compact)), t.title, t.artist, Modifier.fillMaxWidth(), compact, extra, lit = playing || checked,
            onLongClick = { if (ui.selecting) ui.optionsHere(t) else menu = true },
            over = {
                if (playing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(if (Music.paused) Ico.PAUSE else Ico.PLAY, C.accent, if (compact) 20.dp else 26.dp)
                }
                if (ui.selecting) Checkbox(checked, { ui.toggle(t) }, Modifier.align(Alignment.TopStart),
                    colors = CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.darker, uncheckedColor = Color.White))
            }) {
                when {
                    !ui.selecting && (HeldKeys.ctrl || HeldKeys.shift) -> ui.startSelecting(place.group, place, t)
                    ui.selecting && HeldKeys.shift -> { ui.selected[t.id] = t; ui.selectBetween() }
                    ui.selecting -> ui.toggle(t)
                    else -> onPlay()
                }
            }
        TrackMenu(t, place, ui, menu) { menu = false }
    }
}

/** The page's own search, under its name: it looks only through what's on the page. */
@Composable
fun FilterField(ui: Ui, title: String) {
    val focus = LocalFocusManager.current
    val ask = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { ask.requestFocus() } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(R.pill)
        .background(if (C.isLight) C.light else C.fg.copy(alpha = 0.07f)), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ico.SEARCH, C.muted, 18.dp, Modifier.padding(start = 16.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp, top = 12.dp, bottom = 12.dp)) {
            if (ui.filter.isNullOrEmpty()) Text("Search $title", style = T.body.copy(color = C.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(ui.filter.orEmpty(), { ui.filter = it }, singleLine = true, textStyle = T.body, cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(ask).typing())
        }
        Box(Modifier.clip(R.pill).clickable { ui.filter = null; focus.clearFocus() }.padding(12.dp)) { Icon(Ico.CLOSE, C.muted, 16.dp) }
    }
}

/** A short list of things to do with what was long-pressed (Pin, Put in a folder…). */
@Composable
fun ChoicesDialog(ui: Ui) {
    val (title, list) = ui.choices ?: return
    AlertDialog(onDismissRequest = { ui.choices = null }, containerColor = C.light,
        title = { Text(title, style = T.head, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                list.forEach { (label, act) ->
                    Text(label, style = T.body, modifier = Modifier.fillMaxWidth().clip(R.small)
                        .clickable { ui.choices = null; act() }.padding(horizontal = 8.dp, vertical = 13.dp))
                }
            }
        },
        confirmButton = { TextButton({ ui.choices = null }) { Text("Cancel", color = C.accent) } })
}

/** Pin or Unpin, for a long-press's choices. */
fun pinChoice(p: Pin): Pair<String, () -> Unit> = (if (Music.pinned(p)) "Unpin" else "Pin") to { Music.togglePin(p) }

/** Pin or Unpin, as a pill on a page. */
@Composable
fun PinPill(p: Pin) {
    Pill(if (Music.pinned(p)) "Unpin" else "Pin", quiet = true) { Music.togglePin(p) }
}

private val PIN_KINDS = mapOf("artist" to "Artist", "album" to "Album", "mine" to "Playlist", "service" to "Playlist",
    "genre" to "Genre", "folder" to "Folder", "listfolder" to "Folder of playlists")

/** Open what was pinned, where it lives. */
fun openPin(ui: Ui, p: Pin) {
    when (p.kind) {
        "artist" -> ui.pages += Ui.Page.Artist(p.id, p.source.ifEmpty { null })
        "album" -> if (p.source == "local") {
            val found = runCatching { Music.albums() }.getOrDefault(emptyList()).firstOrNull { it.first.id == p.id }
            if (found == null) Music.toast("“${p.title}” isn't in your songs any more") else ui.pages += Ui.Page.AlbumPage(found.first, "local", found.second)
        } else ui.pages += Ui.Page.AlbumPage(Album(p.id, p.title, p.sub, p.thumb), p.source)
        "genre" -> runCatching { Music.genres() }.getOrDefault(emptyList()).firstOrNull { it.first == p.id }
            ?.let { ui.pages += Ui.Page.Songs(p.title, it.second, "genre", p.id) } ?: Music.toast("No songs in “${p.title}” now")
        "folder" -> runCatching { Music.folderSongs() }.getOrDefault(emptyList()).firstOrNull { it.first == p.id }
            ?.let { ui.pages += Ui.Page.Songs(p.title, it.second, "folder", p.id) } ?: Music.toast("No songs in “${p.title}” now")
        "mine" -> if (Music.lists.get(p.id) == null) Music.toast("“${p.title}” was deleted")
            else { ui.pages.clear(); ui.tab = "music"; ui.musicView = "playlists"; ui.openFolder = null; ui.openMine = p.id }
        "listfolder" -> if (Music.playlistFolders.get(p.id) == null) Music.toast("“${p.title}” was deleted")
            else { ui.pages.clear(); ui.tab = "music"; ui.musicView = "playlists"; ui.openMine = null; ui.openFolder = p.id }
        "service" -> openServicePlaylist(ui, p.id, p.title, p.thumb, p.source)
    }
}

/** A service's playlist, open in the Library. */
fun openServicePlaylist(ui: Ui, id: String, title: String, thumb: String, source: String) {
    ui.pages.clear(); ui.openFolder = null
    Music.browse(source)
    ui.tab = "library"
    val al = Album(id, title, "", thumb)
    ui.openList = al to null
    val src = Music.sources[source]
    Music.bg({ src?.playlist(id) ?: emptyList() }) { res, err ->
        if (ui.openList?.first == al) ui.openList = al to (res ?: emptyList())
        if (err != null) Music.toast("Couldn't open it: ${err.message?.take(80)}")
    }
}

/** A pin's picture: the artist's, the album's cover, the first song's. */
@Composable
private fun pinArt(p: Pin): Any? = remember(p.key, Music.musicVersion, Music.artistsVersion) {
    when (p.kind) {
        "artist" -> artistArt(p.id, p.thumb)
        "mine" -> Music.lists.get(p.id)?.tracks?.firstOrNull { it.thumb.isNotEmpty() || it.isFile }?.let { artModel(it) }
        "album" -> if (p.source == "local") runCatching { Music.albums() }.getOrNull()?.firstOrNull { it.first.id == p.id }?.second?.firstOrNull()?.let { artModel(it) }
            else p.thumb.ifEmpty { null }
        "genre" -> runCatching { Music.genres() }.getOrNull()?.firstOrNull { it.first == p.id }?.second?.firstOrNull()?.let { artModel(it) }
        "folder" -> runCatching { Music.folderSongs() }.getOrNull()?.firstOrNull { it.first == p.id }?.second?.firstOrNull()?.let { artModel(it) }
        else -> p.thumb.ifEmpty { null }
    }
}

private fun pinIco(p: Pin) = when (p.kind) {
    "artist" -> Ico.PERSON; "album" -> Ico.DISC; "genre" -> Ico.GENRE; "folder", "listfolder" -> Ico.FOLDER; else -> Ico.PLAYLIST
}

/** Pinned: what you pinned, the newest first; a tap opens it, a long-press unpins it or moves it up. */
fun LazyListScope.pinnedView(ui: Ui) {
    val pins = Music.pins.filter { fits(ui, it.title, it.sub, PIN_KINDS[it.kind] ?: "") }
    if (Music.pins.isEmpty()) { item { Empty("Nothing pinned yet", "Long-press a playlist, artist, album, genre or folder and pick Pin, or tap Pin on its page.") }; return }
    item { Section("${pins.size} PINNED") }
    fun menu(p: Pin) {
        ui.choices = p.title to listOfNotNull(pinChoice(p),
            if (Music.pins.firstOrNull()?.key != p.key) "Move to the top" to { Music.pins.removeAll { it.key == p.key }; Music.pins.add(0, p); Music.saveState() } else null)
    }
    laidOutItems(pins, ui, key = { "pin-" + pins[it].key }, row = { i ->
        val p = pins[i]
        val label = listOf(PIN_KINDS[p.kind] ?: "", p.sub).filter { it.isNotEmpty() }.joinToString(" · ")
        PlaylistRow(Album(p.id, p.title, label, ""), pinArt(p), onLongClick = { menu(p) }) { openPin(ui, p) }
    }) { i, mod, compact ->
        val p = pins[i]
        Tile(pinArt(p), p.title, PIN_KINDS[p.kind] ?: "", mod, compact, round = p.kind == "artist", ico = pinIco(p),
            onLongClick = { menu(p) }) { openPin(ui, p) }
    }
}
