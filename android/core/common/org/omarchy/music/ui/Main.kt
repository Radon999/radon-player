package org.omarchy.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omarchy.music.LAYOUTS
import org.omarchy.music.Music
import org.omarchy.music.Remote
import org.omarchy.music.SEARCH_IN
import org.omarchy.music.SOURCES
import org.omarchy.music.Track
import org.omarchy.music.liveSearchWait

// The layout, after Stellio: the player is the main screen. ☰ (top left) slides out the menu of
// everything in your library (My music, Albums, Artists, Genres, Playlists, Folders, your queues…);
// the gear (top right) opens Settings, full screen. A list keeps a floating mini player at the
// bottom: a tap on it goes back to the player. Long-press or ⋮ on a song for its menu, or pick
// several (from as many lists as you like) for the Options.

@Composable
fun App() {
    val ui = remember { Ui() }
    Ui.current = ui
    val snack = remember { SnackbarHostState() }
    // Shuffle presets: a different Personalization preset every 15 minutes
    LaunchedEffect(Unit) { while (true) { org.omarchy.music.Backup.tick(); org.omarchy.music.NewReleases.tick(); org.omarchy.music.WakeUp.tick(); MyPresets.todTick(); MyPresets.shuffleTick(); org.omarchy.music.RepeatLoops.tick(); kotlinx.coroutines.delay(30_000) } }
    LaunchedEffect(Unit) { for (t in Music.toasts) {
        if (System.currentTimeMillis() - Music.toastAt > 6_000) continue   // made while the app was away: not shown late
        snack.currentSnackbarData?.dismiss(); snack.showSnackbar(t) } }
    LaunchedEffect(Unit) {   // a note with a button (Continue where you left off's Play)
        for ((text, label, act) in Music.offers) {
            snack.currentSnackbarData?.dismiss()
            if (snack.showSnackbar(text, actionLabel = label, duration = androidx.compose.material3.SnackbarDuration.Long) ==
                androidx.compose.material3.SnackbarResult.ActionPerformed) act()
        }
    }

    // Dark icons on the system bars over a light look.
    SystemBars(C.isLight)

    // The Now playing look: every screen in the colours of what's playing's cover.
    val cur = if (Remote.active) Remote.status.track else Music.current()
    val tint: Pair<Color, Color>? = null
    LaunchedEffect(cur?.id, Music.savedVersion, Music.look, Music.npFallback) {
        if (Music.look != "nowplaying") return@LaunchedEffect
        val rgb = if (cur == null) null else Tint.rgbOf(if (Remote.active) Remote.artModel(cur) else artModel(cur))
        val old = Music.coverRgb
        val same = rgb != null && old != null && kotlin.math.abs(Rgb.red(rgb) - Rgb.red(old)) +
            kotlin.math.abs(Rgb.green(rgb) - Rgb.green(old)) +
            kotlin.math.abs(Rgb.blue(rgb) - Rgb.blue(old)) < 18
        if (!same) { Music.coverRgb = rgb; Music.applyLook() }
    }
    // The playing song's cover colour, for "Album cover's colour" (a visualizer in it under any look)
    LaunchedEffect(cur?.id, Music.savedVersion) {
        CoverColour.rgb = if (cur == null) null else runCatching { Tint.rgbOf(if (Remote.active) Remote.artModel(cur) else artModel(cur)) }.getOrNull()
        // ... and its colours top to bottom, for the preset Follows the cover (its backdrop is the cover blurred right out)
        CoverColour.bands = if (cur == null) null else runCatching { Tint.bandsOf(if (Remote.active) Remote.artModel(cur) else artModel(cur)) }.getOrNull()
        if (CoverScene.on()) Music.applyLook()
    }
    // A link shared from another app: search it on the service it's from.
    LaunchedEffect(Music.shared) {
        val text = Music.shared ?: return@LaunchedEffect
        Music.shared = null
        val link = Regex("https?://\\S+").find(text)?.value ?: text
        listOf("spotify" to "spotify.com", "apple" to "music.apple.com", "tidal" to "tidal.com", "ytm" to "youtu")
            .firstOrNull { (k, host) -> host in link && Music.sources.containsKey(k) }?.let { Music.setSource(it.first) }
        ui.pages.clear(); ui.tab = "search"; ui.query = link
        runSearch(ui)
    }
    val bg by animateColorAsState(tint?.first ?: C.bg, tween(700), label = "bg")

    // The ☰ menu: out only when you tap ☰ (a swipe on the player is for the cover).
    val drawer = rememberDrawerState(DrawerValue.Closed)
    // ☰ asks again each tap (drawerAsk), so the menu still opens if a half-done open or close left the two out of step.
    LaunchedEffect(ui.drawer, ui.drawerAsk) { if (ui.drawer) drawer.open() else drawer.close() }
    LaunchedEffect(drawer.currentValue) { ui.drawer = drawer.currentValue == DrawerValue.Open }

    // A new queue takes the name of the list its songs came from.
    val page = ui.pages.lastOrNull()
    val title = titleOf(ui, page)
    // The page's own search is the place's: going further in (a folder, an album, a playlist) it is put by, and Back
    // brings you back to what you searched for; a place you come to anew has none. (Going to another tab forgets them.)
    val place = listOf(ui.tab, ui.musicView, ui.pages.size, page?.let { System.identityHashCode(it) }, ui.openMine, ui.openList?.first?.id, ui.openFolder).joinToString("|")
    LaunchedEffect(place) {
        if (ui.filterPlace.isNotEmpty() && ui.filterPlace != place) {
            val was = ui.filter
            if (was.isNullOrBlank()) ui.filters.remove(ui.filterPlace) else ui.filters[ui.filterPlace] = was
        }
        if (ui.filterPlace != place) ui.filter = ui.filters.remove(place)
        ui.filterPlace = place
    }
    // Picking albums, artists… ends as you go to another tab (it's for the list it started on).
    LaunchedEffect(ui.tab, ui.musicView) { if (ui.pickingAlbums) { ui.albumsPicked.clear(); ui.pickingAlbums = false } }
    // (one of your playlists open: its own name, not the tab's, so its plays are known to be that playlist's)
    val openPlaylist = if (page == null && ui.tab == "music" && ui.musicView == "playlists") ui.openMine?.let { id ->
        runCatching { Music.lists.all().firstOrNull { it.id == id }?.name }.getOrNull() } else null
    LaunchedEffect(title, ui.query, openPlaylist) {
        Music.listName = if (ui.tab == "search" && page == null) "Search: ${ui.query.trim()}" else openPlaylist ?: title }

    // The phone's Back on Now playing opens the ☰ menu; Back with that menu open (over Now playing) leaves the app.
    val atPlayer = ui.tab == "player" && page == null
    val leaveNow = onPhone && atPlayer && ui.drawer && !ui.selecting && ui.pane == "art"
    val smartEditing = SmartEdit.draft != null && ui.tab == "music" && ui.musicView == "smartlists" && page == null   // Back leaves the editor
    PlatformBack(enabled = !leaveNow && (ui.drawer || ui.selecting || ui.pickingAlbums || ui.pane != "art" || page != null || ui.openMine != null || smartEditing ||
        ui.openList != null || ui.openFolder != null || ui.tab != "player" || (onPhone && atPlayer) || (atPlayer && ui.cameFrom != null))) {
        when {
            ui.drawer -> ui.drawer = false
            ui.selecting -> ui.stopSelecting()
            ui.pickingAlbums -> { ui.albumsPicked.clear(); ui.pickingAlbums = false }
            ui.tab == "player" && ui.pane != "art" -> ui.pane = "art"
            ui.filter != null -> ui.filter = null   // a search showing: Back closes it first, then goes back
            ui.pages.isNotEmpty() -> ui.pages.removeAt(ui.pages.lastIndex)
            smartEditing -> SmartEdit.close()
            ui.openMine != null -> ui.openMine = null
            ui.openList != null -> ui.openList = null
            ui.openFolder != null -> ui.openFolder = null
            // On Now playing, having come from a list (a search, an artist's page, a playlist): back to it, as it was.
            // Only when you weren't anywhere before does Back open the menu.
            atPlayer && ui.cameFrom != null -> ui.goBackToPlace()
            onPhone && atPlayer -> ui.openDrawer()
            else -> goTo(ui, "player")
        }
    }

    // Everything that needs leave is asked for as the app starts, at every start until it is allowed (the phone: your
    // music, the microphone for the visualizer, notifications, All files access; the PC needs none), and a folder of
    // music when none is chosen: so each thing works the first time it is used.
    StartupPermissions()
    AskForMusicFolder()

    val lay = UiLayouts.current
    // The ☰ menu slides in from the right in a right-handed layout: the drawer is laid out right to left, its insides
    // (and the screens) left to right as always.
    val ltr = androidx.compose.ui.platform.LocalLayoutDirection.current
    val drawerDir = if (lay.menuRight) androidx.compose.ui.unit.LayoutDirection.Rtl else ltr
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides drawerDir) {
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = ui.drawer, scrimColor = Color.Black.copy(alpha = 0.35f),
        drawerContent = { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides ltr) { DrawerSheet(ui, lay.menuRight) } }) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides ltr) {
        Box(Modifier.fillMaxSize().background(bg).then(C.gradient?.let { Modifier.background(Brush.linearGradient(it)) } ?: Modifier)) {
            if (CoverScene.glassNow) CoverGlassBackdrop()   // Glass cover: the cover blurred behind every screen
            if (C.stars) Stars()
            LookEffects(LookFx.behind(C.fx))   // a look of yours: its effects (rain and snow go over everything, below)
            val screens: @Composable (Modifier) -> Unit = { m -> Box(m) {
            if (ui.tab == "player" && page == null) {
                ApplePlayer(ui)
                if (ui.selecting) Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { SelectBar(ui) }
            } else Column(Modifier.fillMaxSize().statusBarsPadding()) {
                ScreenHeader(ui, page, title)
                if (lay.mini == MiniKind.TOP && !ui.selecting && !(ui.pickingAlbums && page == null)) MiniPlayer(ui, lay.mini)
                val density = androidx.compose.ui.platform.LocalDensity.current
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { ui.width = it.width / density.density }) {
                    // The screen in front, for its list to keep its place under (PlaceColumn): a page is itself (the
                    // very one, at its depth: opened afresh it starts at its top), a tab is the tab and what's open in it
                    val placeKey = if (page != null) "page:${ui.pages.size}:${System.identityHashCode(page)}"
                        else "tab:${ui.tab}:${ui.musicView}:${ui.openMine}:${ui.openFolder}:${ui.openList?.first?.id}"
                    androidx.compose.runtime.CompositionLocalProvider(LocalPlace provides placeKey) {
                    if (page != null) PageScreen(ui, page)
                    else when (ui.tab) {
                        "search" -> SearchTab(ui)
                        "library" -> if (Music.browseLocal) MusicTab(ui) else LibraryTab(ui)   // My music has no service library
                        "music" -> MusicTab(ui)
                        "artists" -> ArtistsTab(ui)
                        "albums" -> AlbumsTab(ui)
                        "eps" -> AlbumsTab(ui, kind = "eps")
                        "singles" -> AlbumsTab(ui, kind = "singles")
                        "genres" -> GenresTab(ui)
                        "moods" -> MoodsTab(ui)
                        "folders" -> FoldersTab(ui)
                        "unreleased" -> UnreleasedTab(ui)
                        "downloads" -> DownloadsTab(ui)
                        "flac24" -> FlacTab(ui, 24)
                        "flac32" -> FlacTab(ui, 32)
                        "type-flac", "type-mp3", "type-m4a", "type-wav", "type-ogg", "type-other" -> FileTypeTab(ui, ui.tab)
                        "queue" -> QueueTab(ui)
                        "lyrics" -> LyricsTab(ui)
                        "visualizer" -> VisualizerTab()
                    }
                    }
                }
                Box(Modifier.navigationBarsPadding()) {
                    when {
                        ui.selecting -> SelectBar(ui)
                        // (an artist's page picks its releases on the page itself; every other list picks with no page open)
                        ui.pickingAlbums && (page == null) != (ui.pickWhere == "artist-albums") -> PickDock(ui)
                        lay.mini != MiniKind.TOP && lay.mini != MiniKind.NONE -> MiniPlayer(ui, lay.mini)
                    }
                }
            } } }
            // The layout's way around: the ☰ menu alone, a bar along the bottom, a rail down the side or tabs along the top.
            when (lay.nav) {
                NavKind.BOTTOM -> Column(Modifier.fillMaxSize()) {
                    screens(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars))
                    BottomNav(ui, lay)
                }
                NavKind.RAIL -> Row(Modifier.fillMaxSize()) {
                    val right = lay.x("railSide") == "right"
                    if (!right) SideRail(ui, lay)
                    screens(Modifier.weight(1f).fillMaxHeight())
                    if (right) SideRail(ui, lay)
                }
                NavKind.TOP -> Column(Modifier.fillMaxSize()) {
                    TopTabs(ui, lay)
                    screens(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars))
                }
                NavKind.DRAWER -> screens(Modifier.fillMaxSize())
            }
            // Rain and snow fall over the screens, the seek bar and the buttons (a plain Canvas: taps go through to them)
            LookEffects(LookFx.over(C.fx))
            // The app's notes (the white messages). They can sit over the play buttons, so a tap or a swipe to either
            // side sends one away at once: no waiting for it to go by itself.
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp)) { data ->
                var dx by remember(data) { androidx.compose.runtime.mutableFloatStateOf(0f) }
                androidx.compose.material3.Snackbar(data, Modifier
                    .graphicsLayer(translationX = dx, alpha = (1f - kotlin.math.abs(dx) / 500f).coerceIn(0.15f, 1f))
                    .pointerInput(data) {
                        detectHorizontalDragGestures(
                            onDragEnd = { if (kotlin.math.abs(dx) > 70f) data.dismiss() else dx = 0f },
                            onDragCancel = { dx = 0f }) { _, d -> dx += d }
                    }
                    .pointerInput(data) { detectTapGestures { data.dismiss() } })
            }
            DesktopVolume(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))
            Sheets(ui)
            if (ui.showOptions) OptionsSheet(ui)
            if (ui.showQueues) QueuesDialog(ui)
            ChoicesDialog(ui)
            ui.reviewFor?.let { ReviewDialog(ui, it) }
            ui.rearrange?.let { RearrangeScreen(ui, it) }
            ui.releasePick?.let { ReleasePicker(ui, it) }
            if (ui.castPick) CastPicker(ui)
            ui.tagPreview?.let { (ids, full) -> TagPreviewDialog(ui, ids, full) }
            ui.lyricsCard?.let { (t, g) -> LyricsCardDialog(ui, t, g) }
            ui.songCard?.let { SongCardDialog(ui, it) }
            ui.presetEdit?.let { PresetEditDialog(ui, it) }
            ui.songClip?.let { SongClipDialog(ui, it) }
            // A playlist's Set a cover…: the picture picker, opened when a playlist asks for one
            val coverPick = rememberOpenFile { uri -> ui.playlistCoverFor?.let { id -> if (uri != null) PlaylistCovers.set(id, uri) }; ui.playlistCoverFor = null }
            LaunchedEffect(ui.playlistCoverFor) { if (ui.playlistCoverFor != null) coverPick.launch(arrayOf("image/*")) }
        }
        }
    }
    }
}

/** A few stars over the galaxy looks: drawn once, light. */
@Composable
internal fun Stars() {
    val stars = remember {
        val r = java.util.Random(7)
        List(110) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    val col = Color.White
    Canvas(Modifier.fillMaxSize()) {
        for ((x, y, k) in stars) drawCircle(col.copy(alpha = 0.25f + k * 0.6f), radius = (0.6f + k * 1.4f) * density,
            center = Offset(x * size.width, y * size.height))
    }
}

fun goTo(ui: Ui, tab: String, view: String? = null) {
    // To Now playing from somewhere: that place is kept, for Back to return to. To any other tab: there's nowhere to go back to.
    if (tab == "player" && ui.tab != "player") ui.rememberPlace() else if (tab != "player") ui.cameFrom = null
    ui.pages.clear(); ui.openMine = null; ui.openList = null; ui.openFolder = null   // what you've picked stays picked
    ui.filter = null; ui.filters.clear()   // another tab: no search carried over, none left to come back to
    if (view != null) ui.musicView = view
    ui.tab = tab
    ui.drawer = false
}

// ───────────────────────────── The ☰ menu ─────────────────────────────

private class Entry(val label: String, val ico: Ico, val tab: String, val view: String? = null)

/** The menu: a few on their own at the top, then three sections that fold open, then Settings and the
 *  Equalizer (the same as the desktop's; it has a mini player there too). */
private fun topEntries(): List<Entry> = listOf(Entry("Pinned", Ico.PIN, "music", "pinned"), Entry("Now playing", Ico.PLAY, "player"),
    Entry("Search", Ico.SEARCH, "search"), Entry("Up next & queues", Ico.QUEUE, "queue"))

private fun sections(): List<Triple<String, String, List<Entry>>> = listOf(
    Triple("music", "Music", buildList {
        add(Entry("My music", Ico.NOTE, "music", "all"))
        add(Entry("Albums", Ico.DISC, "albums"))
        add(Entry("EPs", Ico.DISC, "eps"))
        add(Entry("Singles", Ico.DISC, "singles"))
        add(Entry("Artists", Ico.PERSON, "artists"))
        add(Entry("Genres", Ico.GENRE, "genres"))
        add(Entry("Moods", Ico.MOOD, "moods"))
        add(Entry("Playlists", Ico.PLAYLIST, "music", "playlists"))
        add(Entry("Smart playlists", Ico.SPARKLE, "music", "smartlists"))
        add(Entry("Folders", Ico.FOLDER, "folders"))
        add(Entry("Unreleased", Ico.FOLDER, "unreleased"))
        add(Entry("Downloads", Ico.DOWNLOAD, "downloads"))
        add(Entry("Recently played", Ico.CLOCK, "music", "recent"))
        if (!Music.browseLocal) add(Entry("${SOURCES[Music.source] ?: "Service"} library", Ico.CLOUD, "library"))
        add(Entry("New releases", Ico.SPARKLE, "music", "newreleases"))
        add(Entry("Discovery", Ico.COMPASS, "music", "discovery"))
        add(Entry("Listen later", Ico.CLOCK, "music", "later"))   // what you put aside to hear another time
    }),
    // Your songs by the kind of file they are: to look at one kind when it has a fault or needs tagging
    Triple("filetypes", "File type", buildList {
        add(Entry("FLACs", Ico.NOTE, "type-flac"))
        add(Entry("24-bit FLACs", Ico.NOTE, "flac24"))
        add(Entry("32-bit FLACs", Ico.NOTE, "flac32"))
        org.omarchy.music.FlacDepths.TYPES.drop(1).forEach { add(Entry(it.second, Ico.NOTE, it.first)) }
    }),
    Triple("stats", "Statistics and favorites", listOf(Entry("Most played", Ico.CHART, "music", "top"),
        Entry("Skips", Ico.SKIP, "music", "skips"), Entry("Favorites", Ico.STAR, "music", "favorites"),
        Entry("Not favorited", Ico.STAR_LINE, "music", "unfavorited"), Entry("On repeat", Ico.REPEAT, "music", "repeat"),
        Entry("Repeats", Ico.REPEAT_ONE, "music", "repeats"), Entry("Unplayed and neglected", Ico.CLOCK, "music", "neglected"),
        Entry("Milestones", Ico.SPARKLE, "music", "milestones"), Entry("Taste", Ico.HEART, "music", "taste"))),
    Triple("utilities", "Utilities", listOf(Entry("Visualizer", Ico.VIS, "visualizer"), Entry("Wrapped", Ico.SPARKLE, "wrapped"),
        Entry("DJ", Ico.DISC, "music", "dj"), Entry("Duplicates", Ico.COPY, "music", "dupes"), Entry("Queued mood edits", Ico.MOOD, "music", "moodqueue"),
        Entry("Queued clean/explicit changes", Ico.REFRESH, "music", "versionqueue"))),
)

/** The ☰ menu's entries you chose to hide (Settings › Personalization › Menu entries): a screen you never use needn't
 *  be in the menu. A hidden one is still there to reach from Search's results, a bar button of your layout, or by
 *  showing it again. Now playing can't be hidden (a layout with no bar would have no way to it). */
object MenuHidden {
    private const val PREF = "menu_hidden"
    private var version by androidx.compose.runtime.mutableStateOf(0)
    fun all(): Set<String> = version.let { org.omarchy.music.Prefs.text(PREF).split(';').filter { it.isNotBlank() }.toSet() }
    fun toggle(key: String) {
        val now = all(); org.omarchy.music.Prefs.setText(PREF, (if (key in now) now - key else now + key).joinToString(";")); version++
    }
}
private fun entryKey(e: Entry) = e.tab + "|" + (e.view ?: "")
private fun shownInMenu(e: Entry) = e.tab == "player" || entryKey(e) !in MenuHidden.all()

/** Every entry of the ☰ menu, for Settings' list of them: (its section's name, its key, its name, whether it can be hidden). */
internal fun menuEntryList(): List<List<String>> =
    (listOf(Triple("top", "At the top", topEntries())) + sections()).flatMap { (_, name, list) -> list.map { listOf(name, entryKey(it), it.label, if (it.tab == "player") "" else "y") } }

@Composable
private fun DrawerSheet(ui: Ui, right: Boolean = false) {
    ModalDrawerSheet(drawerContainerColor = if (C.apple) (if (C.gradient != null) C.darker else C.bg) else C.light,
        drawerShape = if (right) androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            else androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.width(300.dp)) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Where you browse: your own music on the phone, or a service.
            Column(Modifier.fillMaxWidth().clip(R.card).background(C.bg).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (Music.browseLocal) Ico.NOTE else Ico.CLOUD, C.accent, 22.dp)
                    Text(if (Music.browseLocal) DEVICE_NAME else SOURCES[Music.source] ?: "", style = T.title.copy(fontSize = 17.sp),
                        modifier = Modifier.padding(start = 12.dp))
                }
                SourceChips(ui, Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            val here = ui.pages.isEmpty()
            @Composable fun item(e: Entry) {
                val on = here && ui.tab == e.tab && (e.view == null || e.view == ui.musicView)
                DrawerItem(e.label, e.ico, on) {
                    if (e.tab == "wrapped") { ui.drawer = false; ui.sheet = "wrapped" }
                    else { goTo(ui, e.tab, e.view); if (e.tab == "player") ui.pane = "art" }
                }
            }
            topEntries().filter { shownInMenu(it) }.forEach { item(it) }
            sections().map { Triple(it.first, it.second, it.third.filter { e -> shownInMenu(e) }) }.filter { it.third.isNotEmpty() }.forEach { (key, name, list) ->
                val open = Music.drawerFolds[key] == true
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(R.card).clickable { Music.foldSection(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name.uppercase(), style = T.section, modifier = Modifier.weight(1f))
                    Text(if (open) "▾" else "▸", style = T.section)
                }
                if (open) list.forEach { item(it) }
            }
            androidx.compose.material3.HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 8.dp))
            DrawerItem("Settings", Ico.SETTINGS, false, quiet = true) { ui.drawer = false; ui.openSettings() }
            DrawerItem("Equalizer", Ico.EQ, false, quiet = true) { ui.drawer = false; ui.sheet = "eq" }
            DrawerItem("Changelog", Ico.CLOCK, false, quiet = true) { ui.drawer = false; ui.changelog = true }
            DrawerItem("Features", Ico.LYRICS, false, quiet = true) { ui.drawer = false; ui.guide = true }
            if (hasMiniPlayer) {
                val act = currentActivity()
                DrawerItem("Mini player", Ico.VIS, false, quiet = true) { ui.drawer = false; enterMiniPlayer(act) }
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, ico: Ico, on: Boolean, quiet: Boolean = false, onClick: () -> Unit) {
    val onCol = if (C.apple) C.fg else if (C.isLight) Color.White else C.darker
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)
        .then(if (on && !C.apple) Modifier.shadow(8.dp, R.card, ambientColor = C.accent, spotColor = C.accent) else Modifier)
        .clip(R.card).background(if (on) (if (C.apple) C.fg.copy(alpha = 0.08f) else C.accent) else Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(ico, if (on && !C.apple) onCol else if (quiet) C.muted else C.accent, 22.dp)
        Text(label, style = T.title.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (on) onCol else if (quiet) C.muted else C.fg),
            modifier = Modifier.padding(start = 18.dp))
    }
}

/** Where you browse: a service, or your own music on the phone. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun SourceChips(ui: Ui, modifier: Modifier = Modifier) {
    // On the phone one row you swipe; on the PC (a mouse wheel doesn't scroll sideways) they wrap, so every service shows.
    val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (onPhone) Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        else androidx.compose.foundation.layout.FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
    wrap {
        Chip(DEVICE_NAME, Music.browseLocal) {   // your own songs: no service library, the search looks in them
            Music.browse("local")
            if (ui.tab == "library") goTo(ui, "music", "all")
        }
        SOURCES.forEach { (k, name) ->
            Chip(if (k == "ytm") "YouTube" else name.removeSuffix(" Music"), Music.source == k && !Music.browseLocal) {
                if (Music.source != k || Music.browseLocal) {
                    Music.browse(k)
                    ui.openList = null
                }
            }
        }
    }
}

// ───────────────────────────── A list's screen ─────────────────────────────

/** ☰ (or ‹ inside something), the screen's name, search and the gear. */
@Composable
private fun ScreenHeader(ui: Ui, page: Ui.Page?, title: String) {
    val inside = page != null || ui.openMine != null || ui.openList != null || ui.openFolder != null
    val lay = UiLayouts.current
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (inside) IconButton(Ico.BACK) {
                when {
                    ui.pages.isNotEmpty() -> ui.pages.removeAt(ui.pages.lastIndex)
                    ui.openMine != null -> ui.openMine = null
                    ui.openList != null -> ui.openList = null
                    else -> ui.openFolder = null
                }
            } else if (lay.headerMenu && !lay.menuRight) IconButton(Ico.MENU) { ui.openDrawer() }
            else Spacer(Modifier.width(12.dp))
            Text(title, style = T.head.copy(fontWeight = FontWeight.Medium).let { st ->
                    when (lay.x("titleSize")) { "big" -> st.copy(fontSize = st.fontSize * 1.35f, fontWeight = FontWeight.Bold); "small" -> st.copy(fontSize = st.fontSize * 0.82f); else -> st } },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (lay.titleCenter) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.weight(1f).padding(start = 4.dp))
            // Refresh, on every tab with something to read again (your files, your listening, your downloads…)
            org.omarchy.music.RefreshRules.kind(ui.tab, ui.musicView, inside, Music.browseLocal)?.let { kind ->
                IconButton(Ico.REFRESH, desc = "Refresh $title") { refreshTab(kind) }
            }
            if (laidOut(ui)) {
                // On a list, search looks through that list; Search in the menu searches the services.
                IconButton(Ico.SEARCH, if (ui.filter != null) C.accent else C.fg, desc = "Search $title") {
                    ui.filter = if (ui.filter == null) "" else null
                }
                val key = layoutKey(ui)
                val now = Music.layoutOf(key)
                IconButton(when (now) { "grid" -> Ico.GRID; "compact" -> Ico.GRID_SMALL; "large" -> Ico.GRID_LARGE; "huge" -> Ico.GRID_HUGE; else -> Ico.LIST },
                    desc = "Layout: " + when (now) { "grid" -> "grid"; "compact" -> "small grid"; "large" -> "big grid"; "huge" -> "biggest grid"; else -> "list" }) {
                    Music.setLayout(key, LAYOUTS[(LAYOUTS.indexOf(now) + 1) % LAYOUTS.size])
                }
            } else if (ui.tab != "search" || page != null) IconButton(Ico.SEARCH) { goTo(ui, "search") }
            IconButton(Ico.SETTINGS) { ui.openSettings() }
            if (lay.headerMenu && lay.menuRight && !inside) IconButton(Ico.MENU) { ui.openDrawer() }
        }
        if (ui.tab == "search" && page == null) SearchField(ui)
        else if (ui.filter != null && laidOut(ui)) FilterField(ui, title)
        if (ui.tab == "library" && page == null && !Music.browseLocal) SourceChips(ui, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

/** No folder of music is chosen (My music would be empty): asked at each start until one is. On the PC a folder
 *  picker opens; on the phone the folders with music on it, to tick. "Not now" closes it until the next start. */
@Composable
private fun AskForMusicFolder() {
    var later by remember { androidx.compose.runtime.mutableStateOf(false) }
    var browsing by remember { androidx.compose.runtime.mutableStateOf(false) }
    val pick = rememberFolderPick { path -> if (path != null) Music.setFolders(Music.folders + path) }
    if (browsing) { FolderBrowser { browsing = false }; return }
    if (later || Music.folders.isNotEmpty()) return
    androidx.compose.material3.AlertDialog(onDismissRequest = { later = true }, containerColor = C.light,
        title = { Text("Where is your music?", style = T.head) },
        text = { Text("No folder of music is chosen, so My music, Albums and the rest are empty. Choose the folder your songs are in " +
            "(everything inside it comes too). You can add more, or change it, in Settings.", style = T.body) },
        confirmButton = { androidx.compose.material3.TextButton({ if (pick != null) pick() else browsing = true }) { Text("Choose a folder…", color = C.accent) } },
        dismissButton = { androidx.compose.material3.TextButton({ later = true }) { Text("Not now", color = C.muted) } })
}

/** The header's Refresh: what the tab shows is read again (RefreshRules says which reading a tab needs). */
private fun refreshTab(kind: String) {
    val rules = org.omarchy.music.RefreshRules
    when (kind) {
        rules.FILES -> { Music.toast(rules.text(kind)); Music.rescan() }
        rules.STATS -> { Music.toast(rules.text(kind)); org.omarchy.music.Stats.refresh(force = true); Music.musicVersion++ }
        rules.DOWNLOADS -> Music.refreshDownloads()   // says what it found itself
        rules.SMART -> { Music.toast(rules.text(kind)); org.omarchy.music.SmartLists.refresh(); Music.musicVersion++ }
        rules.MOODLISTS -> org.omarchy.music.MoodLists.refresh(org.omarchy.music.Moods.ALL)   // says what changed itself
        rules.DUPES -> { Music.toast(rules.text(kind)); Music.findDupes() }
        else -> { Music.musicVersion++; Music.savedVersion++; Music.toast(rules.text(kind)) }
    }
}

private fun titleOf(ui: Ui, page: Ui.Page?): String = when (page) {
    is Ui.Page.Artist -> page.name
    is Ui.Page.AlbumPage -> page.album.title
    is Ui.Page.Songs -> page.title
    null -> when (ui.tab) {
        "player" -> "Now playing"
        "search" -> "Search"
        "library" -> if (Music.browseLocal) "My music" else "${SOURCES[Music.source] ?: "Service"} library"
        "artists" -> "Artists"
        "albums" -> "Albums"
        "eps" -> "EPs"
        "singles" -> "Singles"
        "genres" -> "Genres"
        "moods" -> "Moods"
        "folders" -> "Folders"
        "unreleased" -> "Unreleased"
        "downloads" -> "Downloads"
        "flac24" -> "24-bit FLACs"
        "flac32" -> "32-bit FLACs"
        "type-flac", "type-mp3", "type-m4a", "type-wav", "type-ogg", "type-other" -> org.omarchy.music.FlacDepths.typeName(ui.tab)
        "queue" -> "Up next"
        "lyrics" -> "Lyrics"
        "visualizer" -> "Visualizer"
        // (Other device's songs is opened from Settings › Remote & sync, not from the menu)
        "music" -> if (ui.musicView == "theirs") "Other device's songs"
            else (topEntries() + sections().flatMap { it.third }).firstOrNull { it.tab == "music" && it.view == ui.musicView }?.label ?: "My music"
        else -> "Library"
    }
}

@Composable
private fun SearchField(ui: Ui) {
    val focus = LocalFocusManager.current
    var menu by remember { mutableStateOf(false) }
    // Search as you type: once you stop typing for a moment (a new letter starts the wait again).
    LaunchedEffect(ui.query, Music.searchIn) {
        val q = ui.query.trim()
        val wait = liveSearchWait(q, Music.searchIn) ?: return@LaunchedEffect
        kotlinx.coroutines.delay(wait.toLong())
        if (q != ui.searched) { ui.pages.clear(); runSearch(ui, quiet = true) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(R.pill)
        .background(if (C.isLight) C.light else C.fg.copy(alpha = 0.07f)), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ico.SEARCH, C.muted, 18.dp, Modifier.padding(start = 16.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp, top = 13.dp, bottom = 13.dp)) {
            if (ui.query.isEmpty()) Text("Songs, artists, albums or a link", style = T.body.copy(color = C.muted), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            BasicTextField(ui.query, { ui.query = it }, singleLine = true,
                textStyle = T.body, cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); ui.pages.clear(); runSearch(ui) }),
                modifier = Modifier.fillMaxWidth().typing())
        }
        if (ui.query.isNotEmpty()) IconButton(Ico.CLOSE, C.muted, 16.dp, 8.dp) { ui.query = "" }
        MicButton { said -> ui.query = said; focus.clearFocus(); ui.pages.clear(); runSearch(ui) }
        // Where to search: Tidal, YouTube Music or your own songs.
        Box {
            Text("${if (Music.searchIn == "ytm") "YouTube" else SEARCH_IN[Music.searchIn]}  ▾", style = T.tab.copy(color = C.accent),
                modifier = Modifier.clip(R.pill).clickable { menu = true }.padding(horizontal = 14.dp, vertical = 12.dp))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SEARCH_IN.forEach { (k, name) ->
                    DropdownMenuItem(text = { Text(name, style = T.body.copy(color = if (k == Music.searchIn) C.accent else C.fg)) }, onClick = {
                        menu = false
                        if (k != Music.searchIn) {
                            Music.setSearchIn(k)
                            if (ui.query.isNotBlank() && ui.tab == "search") { ui.pages.clear(); runSearch(ui) }
                        }
                    })
                }
            }
        }
    }
}

// ───────────────────────────── What's playing ─────────────────────────────

class Now(val t: Track?, val paused: Boolean, val pos: Double, val dur: Double, val remote: Boolean)

@Composable
fun now(): Now {
    val remote = Remote.active
    return Now(if (remote) Remote.status.track else Music.current(), if (remote) Remote.status.paused else Music.paused,
        if (remote) Remote.status.position else Music.position, if (remote) Remote.status.duration else Music.duration, remote)
}

/** The mini player on a list: the song, previous, play/pause, next; a tap goes back to the player. The layout picks how it
 *  looks: a floating card, a flat bar along the edge, a small pill, or (MiniKind.TOP) a flat bar under the header. */
@Composable
private fun MiniPlayer(ui: Ui, kind: MiniKind = MiniKind.FLOAT) {
    val n = now()
    val t = n.t ?: return
    val open = { goTo(ui, "player"); ui.pane = "art" }
    val swipe = Modifier.pointerInput(Unit) {
        var dy = 0f
        detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy < -40f) open() }) { _, d -> dy += d }
    }
    val frac = if (n.dur > 0) (n.pos / n.dur).toFloat().coerceIn(0f, 1f) else 0f
    val status = if (n.remote) Remote.status.message else Music.status
    when (kind) {
        MiniKind.NONE -> Unit
        MiniKind.PILL -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.shadow(10.dp, R.pill, ambientColor = C.accent.copy(alpha = 0.3f), spotColor = C.accent.copy(alpha = 0.3f))
                .clip(R.pill).background(C.light).clickable(onClick = open).then(swipe).padding(start = 6.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(R.pill)) { ArtImage(if (n.remote) Remote.artModel(t) else artModel(t, 300), 38.dp) }
                Text(t.title, style = T.title.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp).padding(horizontal = 10.dp))
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(36.dp)) {   // how far it's got, as a ring around play/pause
                        drawArc(C.fg.copy(alpha = 0.12f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f * density))
                        drawArc(C.accent, -90f, 360f * frac, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f * density))
                    }
                    IconButton(if (n.paused) Ico.PLAY else Ico.PAUSE, C.fg, 18.dp, 6.dp) { Music.togglePause() }
                }
                IconButton(Ico.NEXT, C.fg, 18.dp, 8.dp) { Music.next() }
            }
        }
        MiniKind.BAR, MiniKind.TOP -> Column(Modifier.fillMaxWidth().background(C.light.copy(alpha = if (C.isLight) 0.97f else 0.85f))
            .clickable(onClick = open).then(swipe)) {
            if (kind == MiniKind.BAR) Box(Modifier.fillMaxWidth().height(2.dp).background(C.fg.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxWidth(frac).height(2.dp).background(C.accent))
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(if (n.remote) Remote.artModel(t) else artModel(t, 300), 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.title, style = T.title.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status.ifEmpty { t.artist }, style = T.sub.copy(color = if (status.isNotEmpty()) C.accent else C.muted),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(Ico.PREV, C.fg, 18.dp, 8.dp) { Music.previous() }
                IconButton(if (n.paused) Ico.PLAY else Ico.PAUSE, C.accent, 24.dp, 8.dp) { Music.togglePause() }
                IconButton(Ico.NEXT, C.fg, 18.dp, 8.dp) { Music.next() }
            }
            if (kind == MiniKind.TOP) Box(Modifier.fillMaxWidth().height(2.dp).background(C.fg.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxWidth(frac).height(2.dp).background(C.accent))
            }
        }
        MiniKind.FLOAT -> Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Column(Modifier.fillMaxWidth().shadow(14.dp, R.big, ambientColor = C.accent.copy(alpha = 0.3f), spotColor = C.accent.copy(alpha = 0.3f))
                .clip(R.big).background(C.light).clickable(onClick = open).then(swipe)) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(if (n.remote) Remote.artModel(t) else artModel(t, 300), 50.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.title, style = T.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(status.ifEmpty { t.artist }, style = T.sub.copy(color = if (status.isNotEmpty()) C.accent else C.muted),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(Ico.PREV, C.fg, 20.dp, 8.dp) { Music.previous() }
                    if (C.apple) IconButton(if (n.paused) Ico.PLAY else Ico.PAUSE, C.fg, 24.dp, 10.dp) { Music.togglePause() }
                    else Box(Modifier.padding(horizontal = 2.dp).size(46.dp).shadow(8.dp, R.pill, ambientColor = C.accent, spotColor = C.accent)
                        .clip(R.pill).background(C.accent).clickable { Music.togglePause() }, contentAlignment = Alignment.Center) {
                        Icon(if (n.paused) Ico.PLAY else Ico.PAUSE, Color.White, 20.dp)
                    }
                    IconButton(Ico.NEXT, C.fg, 20.dp, 8.dp) { Music.next() }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(C.fg.copy(alpha = 0.06f))) {
                    Box(Modifier.fillMaxWidth(frac).height(2.dp).background(C.accent))
                }
            }
        }
    }
}

/** The desktop's volume for a moment after a volume button changed it (the phone's own bar shows the phone's). */
@Composable
private fun DesktopVolume(modifier: Modifier) {
    val at = Remote.volumeShown
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(at) { if (at != 0L) { visible = true; kotlinx.coroutines.delay(1500); visible = false } }
    if (!visible || !Remote.hasVolume) return
    val v = if (Remote.status.muted) 0 else Remote.status.volume
    Row(modifier.clip(R.pill).background(C.light).border(1.dp, C.line, R.pill).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Desktop volume", style = T.sub, modifier = Modifier.padding(end = 10.dp))
        Box(Modifier.width(120.dp).height(4.dp).clip(R.pill).background(C.line)) {
            Box(Modifier.fillMaxWidth(v / 100f).fillMaxHeight().background(C.accent))
        }
        Text("$v%", style = T.tab.copy(color = C.fg), modifier = Modifier.padding(start = 10.dp).width(40.dp))
    }
}
