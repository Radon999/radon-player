package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.io.File

/** The app: your queue and what's playing, your music, the services and the settings.
 *  The phone's counterpart of the desktop's MusicApp, with the same rules (what plays
 *  next, what counts as played, what downloads when). Lives as long as the process;
 *  everything here runs on the main thread, slow work goes to [bg]. */
object Music {
    lateinit var ctx: Ctx
        private set
    // An error in one of the app's jobs is noted (Settings › Problems) and the app keeps going, music and all.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> Crashes.caught(e) })

    // The DJ, as the playback service (PlaybackCore) sees it: all it knows of the DJ (DjDirector answers)
    init {
        PlaybackCore.djSide = DjDirector.side
    }

    lateinit var lib: Library
    lateinit var plays: Plays
    lateinit var lists: Playlists
    lateinit var playlistFolders: PlaylistFolders   // Library folders of playlists
    lateinit var speeds: SongSpeeds
    lateinit var favorites: Favorites   // the songs you starred
    lateinit var songStats: SongStats   // skips, and songs heard to the end
    lateinit var gains: SongGains       // songs' own volume boosts
    lateinit var songEqs: SongEqs       // songs' own EQ (Custom EQ…), by output
    lateinit var local: LocalFiles
    lateinit var tidal: Tidal
    lateinit var dl: Downloader
    val eqProcessor = EqProcessor()

    /** The player, while the playback service runs. */
    /** The player that plays: the cast device's while casting (Cast), else this phone's (ExoPlayer). */
    var localPlayer: Engine? = null
    var cast by mutableStateOf<Engine?>(null)
    var castName by mutableStateOf("")
    var player: Engine?
        get() = cast ?: localPlayer
        set(v) {
            localPlayer = v
            if (v != null) PlaybackCore.pendingLoad?.let { PlaybackCore.pendingLoad = null; it() }
        }

    var castHost = ""   // the cast device's address (the phone's files are served to it)

    internal fun castMime(t: Track, uri: String): String {
        val name = (t.file ?: lib.items[t.id]?.file ?: uri).substringBefore('?').lowercase()
        return when {
            name.endsWith(".mp3") -> "audio/mpeg"; name.endsWith(".m4a") || name.endsWith(".aac") -> "audio/mp4"
            name.endsWith(".ogg") || name.endsWith(".opus") -> "audio/ogg"; name.endsWith(".wav") -> "audio/wav"
            else -> "audio/flac"
        }
    }

    /** A cast device connected: what's playing goes there, from where it was. */
    fun castStarted(p: Engine, name: String) {
        val at = position
        val wasPlaying = !paused
        localPlayer?.pause()
        cast = p; castName = name
        toast("Casting to $name")
        if (current() != null) { resumeAt = at; loadCurrent(autoplay = wasPlaying) }
    }

    /** Back to this phone, where the cast left off. */
    fun castStopped() {
        if (cast == null) return
        val at = position
        val wasPlaying = !paused
        cast = null; castName = ""
        toast(if (platformName == "android") "Playing on this phone" else "Playing on this PC")
        if (current() != null) { resumeAt = at; loadCurrent(autoplay = wasPlaying) }
    }

    // ---- what the screens show ----
    // The queue's own state is the playback service's (PlaybackCore); these names hand on to it.
    var queue: List<Track> get() = PlaybackCore.queue; set(v) { PlaybackCore.queue = v }
    // Multiple queues: playing from another list (a folder, an album, an artist, a playlist…) starts a queue
    // of its own; the one you were in is kept, where you were in it, to go back to.
    var queues: List<SavedQueue> get() = PlaybackCore.queues; set(v) { PlaybackCore.queues = v }   // the other queues, the last one used first
    var queueName: String get() = PlaybackCore.queueName; set(v) { PlaybackCore.queueName = v }
    var queueId: String get() = PlaybackCore.queueId; set(v) { PlaybackCore.queueId = v }
    var multiQueue: Boolean get() = PlaybackCore.multiQueue; set(v) { PlaybackCore.multiQueue = v }
    var listName: String get() = PlaybackCore.listName; set(v) { PlaybackCore.listName = v }   // where songs are picked from now (the screen's name): a new queue is called that
    var resumeAt: Double get() = PlaybackCore.resumeAt; set(v) { PlaybackCore.resumeAt = v }
    // Lyrics into the files: what's found for a song goes into its own tags once it's done playing.
    var lyricsToFiles by mutableStateOf(true)
    private var lyricsWarned = false
    var index: Int get() = PlaybackCore.index; set(v) { PlaybackCore.index = v }
    var paused: Boolean get() = PlaybackCore.paused; set(v) { PlaybackCore.paused = v }
    var position: Double get() = PlaybackCore.position; set(v) { PlaybackCore.position = v }
    var nowFormat by mutableStateOf("")     // what the player says it's playing: "FLAC 24-bit/96 kHz"
    var nowFormatOf = ""                    // …for this song
    var micAsked = false                    // the bars' permission was asked for once (Now playing)
    var duration: Double get() = PlaybackCore.duration; set(v) { PlaybackCore.duration = v }
    var repeat: String get() = PlaybackCore.repeat; set(v) { PlaybackCore.repeat = v }
    var source by mutableStateOf("ytm")
    var status by mutableStateOf("")
    var liked by mutableStateOf(setOf<String>())
    var musicVersion by mutableStateOf(0)      // bumps when your songs, playlists or plays change
    var savedVersion by mutableStateOf(0)      // bumps when downloads change
    var accountsVersion by mutableStateOf(0)   // bumps when a sign-in changes
    var resumed by mutableStateOf(0)           // bumps when the app comes back on screen (from Android's settings, say)

    /** Whether Android shows the app's notifications (the player in the notification shade). */
    fun notificationsOn(): Boolean = notificationsOn(ctx)
    var syncSources = mutableStateMapOf<String, Boolean>()
    var keepPlayed by mutableStateOf(false)
    var artColors by mutableStateOf(true)
    var folders by mutableStateOf(listOf("Music/"))
    var eq by mutableStateOf(EqSettings())
    var lyricsMode by mutableStateOf("genius")
    var musicSort by mutableStateOf("title")   // how the Music tab's All is sorted: title, artist, album, added
    var searchIn by mutableStateOf("tidal")    // where the search bar searches: tidal, ytm, local
    var apiKeys by mutableStateOf(mapOf<String, String>())   // Settings: keys of yours (see API_KEYS)
    var shuffled: Boolean get() = PlaybackCore.shuffled; set(v) { PlaybackCore.shuffled = v }   // the queue was shuffled: repeat-all shuffles it again
    val tabSorts = androidx.compose.runtime.mutableStateMapOf<String, String>()   // each tab's sort (sortOf)
    val tabPicks = androidx.compose.runtime.mutableStateMapOf<String, String>()   // song file -> the tab you put it in (Tagger › Tab)
    var playlistSort by mutableStateOf("order")   // how a playlist's songs are listed (PLAYLIST_SORTS)
    var browseLocal by mutableStateOf(false)   // Local picked in the source bar: your own songs, no Library tab
    var look by mutableStateOf("nowplaying")   // the app's colours: Light ("apple"), Dark ("apple-dark") or Now playing ("nowplaying")
    var npFallback by mutableStateOf("apple-dark")   // the Now playing look while nothing is playing
    val drawerFolds = androidx.compose.runtime.mutableStateMapOf("music" to true)   // the menu's sections that are open
    fun foldSection(key: String) { drawerFolds[key] = drawerFolds[key] != true; saveState() }
    val pins = androidx.compose.runtime.mutableStateListOf<Pin>()   // Pinned: the newest first
    fun pinned(p: Pin) = pins.any { it.key == p.key }
    /** Pin it (to the top of Pinned), or unpin it if it's pinned. */
    fun togglePin(p: Pin) {
        if (pinned(p)) { pins.removeAll { it.key == p.key }; toast("Unpinned ${p.title}") }
        else { pins.add(0, p); toast("Pinned ${p.title}: it's in Pinned, at the top of the menu (☰)") }
        saveState()
    }
    val layouts = androidx.compose.runtime.mutableStateMapOf<String, String>()   // a page's layout: list, grid or compact
    fun layoutOf(key: String) = layouts[key] ?: if (key in GRID_FIRST) "grid" else "list"
    fun setLayout(key: String, l: String) { layouts[key] = l; saveState() }
    var visPreset by mutableStateOf("bars")    // the Visualizer tab's look
    var autoplay by mutableStateOf(true)       // ∞: when the queue runs out, songs like the last one play on
    var wrappedStyle by mutableStateOf("classic")   // Wrapped's look
    var artistSort by mutableStateOf("most")   // an artist's page: their songs by most played, least, title, album, newest
    var skipOrder by mutableStateOf("most")    // how Skips is sorted: most, least, heard
    var topMineOnly by mutableStateOf(true)   // Most played: only the songs on the phone (plays from everywhere)
    fun chooseTopMineOnly(on: Boolean) { topMineOnly = on; MostPlayed.forget(); musicVersion++; saveState() }
    var skipPeriod by mutableStateOf("all")    // …and over which time: week, month, year, all
    var bitPerfect by mutableStateOf(false)    // the DAC gets each song as it is: no EQ, no song volume, no speed
    var replayGain by mutableStateOf("off")    // off, track or album: the files' ReplayGain tags (not in bit-perfect)
    var dacNote by mutableStateOf("")          // what bit-perfect is doing now, in words

    /** Bit-perfect is in force: on the PC whenever it's on; on the phone only while a USB DAC is the output (on the
     *  speaker, Bluetooth or in the car the EQ and the rest go on working). */
    fun bitPerfectNow() = bitPerfect && (platformName != "android" || outputNow.kind == "usb" || usbDirect)

    /** The phone streams straight to the USB DAC itself (UsbDirect), around Android: Android may no longer list the DAC as
     *  the output then, but bit-perfect is still in force. */
    @Volatile var usbDirect = false

    /** The song playing, loaded again where it is: bit-perfect turned on (or the DAC allowed) takes effect at once. */
    fun replayForBitPerfect() {
        if (current() == null || Remote.active || cast != null) return
        val at = position; val wasPlaying = !paused
        resumeAt = at; loadCurrent(autoplay = wasPlaying)
    }

    fun chooseBitPerfect(on: Boolean) {
        bitPerfect = on
        eqProcessor.bypass = bitPerfectNow()
        if (on) eqProcessor.rgGain = 1.0
        current()?.let { t -> playbackOf(t).let { (sp, pi) -> player?.setSpeed(sp, pi) } }
        val rate = player?.audioRate ?: 0; val bits = player?.audioBits ?: 0
        bg({ dacNote = AudioQuality.applyBitPerfect(ctx, on, rate, bits) }) { _, _ ->
            if (platformName == "android") replayForBitPerfect()   // the song goes (or stops going) straight to the DAC now
        }
        saveState()
    }

    fun chooseReplayGain(mode: String) { replayGain = mode; saveState(); toast(if (mode == "off") "ReplayGain off" else "ReplayGain: by $mode, from the next song") }


    var downloadDir by mutableStateOf("")      // a folder of yours downloads go to ("Music/Mine/"); "": Music/Artists/
    /** Where downloads go: the folder you picked, else the first folder the Music tab reads. */
    /** Where downloads go: each in its artist's folder there (Music/Artists/Sody/…) unless you picked another. */
    fun downloadFolder(): String = downloadDir.ifEmpty { DEFAULT_DOWNLOADS }
    const val DEFAULT_DOWNLOADS = "Music/Artists/"
    var dupeAction by mutableStateOf("ask")    // a download you have already: ask, keep (both) or replace
    var dupePrompt by mutableStateOf<Pair<List<Track>, List<Pair<Track, List<Track>>>>?>(null)   // asking about copies
    var trashJob by mutableStateOf<TrashJob?>(null)   // your files Android is asked to put in its trash
    var dupes by mutableStateOf<List<List<Track>>?>(null)   // the Duplicates view's last look: copies, best first
    var dupesBusy by mutableStateOf(false)
    var upgrading by mutableStateOf<String?>(null)   // Upgrade running: how far
    var upgradeKept by mutableStateOf<List<Pair<Track, String>>?>(null)   // kept as good already: replace anyway?
    @Volatile var upgradeStop = false
    private var goingBack: Boolean get() = PlaybackCore.goingBack; set(v) { PlaybackCore.goingBack = v }    // Previous: going back isn't skipping

    /** Your files to put in Android's trash (it asks you), and the songs that take their places. */
    data class TrashJob(val tracks: List<Track>, val replacements: Map<String, Track> = emptyMap(), val what: String = "")
    private var searchBeforeLocal = "tidal"     // where the search looked before
    var importing by mutableStateOf<String?>(null)   // an .m3u8 being read: how far
    var importReport by mutableStateOf<ImportReport?>(null)   // what the last one didn't find, to look for
    var streaming: Boolean get() = PlaybackCore.streaming; set(v) { PlaybackCore.streaming = v }
    var downloads by mutableStateOf(listOf<Track>())
    var syncing by mutableStateOf(false)
    var syncTracks by mutableStateOf(listOf<Track>())
    val toasts = Channel<String>(Channel.BUFFERED)
    /** Notes with a button: (what it says, the button, what the button does). */
    val offers = Channel<Triple<String, String, () -> Unit>>(Channel.BUFFERED)
    var shared by mutableStateOf<String?>(null)   // a link shared to the app, for Search
    var dj: Dj.State get() = DjDirector.dj; set(v) { DjDirector.dj = v }   // the DJ: on, its sets' kinds and lines, the one shown (DjDirector has the DJ's own part)
    var djNew: Boolean get() = DjDirector.djNew; set(v) { DjDirector.djNew = v }   // the DJ brings new songs too, or only yours

    /** Only my songs (off), or new ones too (on): the DJ starts again with it. */
    fun chooseDjNew(on: Boolean) = DjDirector.chooseDjNew(on)
    var djBusy: String? get() = DjDirector.djBusy; set(v) { DjDirector.djBusy = v }   // the DJ picking a set: what it's doing

    val sources = mutableStateMapOf<String, Source>()
    val sourceErrors = mutableStateMapOf<String, String>()
    private val likes = HashMap<String, Set<String>>()

    private var radioFrom: String? get() = PlaybackCore.radioFrom; set(v) { PlaybackCore.radioFrom = v }
    private var context: Int get() = PlaybackCore.context; set(v) { PlaybackCore.context = v }
    var downloadNote by mutableStateOf("")   // "Downloading in FLAC · 3 of 40" while a Download runs
        internal set   // Downloads' to write
    private var syncAgain = false


    fun init(c: Ctx) {
        if (::ctx.isInitialized) return
        ctx = appCtx(c)
        Prefs.init(ctx)
        org.omarchy.music.ui.MyLooks.hook()   // the looks you made, known to the Look menu before the saved look is checked
        bg({ SoundCloud(ctx) }) { src, err -> sourceReady(SoundCloud.KEY, src, err) }   // its own service: streams only
        // The theme (Settings › Personalization › Theme) is read from the settings, as Compose state, so a change redraws
        org.omarchy.music.ui.Themes.source = { Prefs.text(org.omarchy.music.ui.Themes.PREF, org.omarchy.music.ui.Themes.STANDARD) }
        loadDownloadQuality()
        MusicTools.init()
        QualityCheck.init(ctx)
        TagScan.Done.init(ctx)
        eqProcessor.crossfeed = Prefs.on(Prefs.CROSSFEED)
        bpTones = Prefs.on(Prefs.BP_TONES)
        eqProcessor.mono = Prefs.on(Prefs.MONO) || AutoMono.active
        applySound()
        lib = Library(ctx)
        plays = Plays(ctx)
        lists = Playlists(ctx)
        playlistFolders = PlaylistFolders(File(ctx.filesDir, "playlist-folders.json"))
        speeds = SongSpeeds(File(ctx.filesDir, "song-speeds.json"))
        favorites = Favorites(File(ctx.filesDir, "favorites.json"))
        songStats = SongStats(File(ctx.filesDir, "skips.json"))
        gains = SongGains(File(ctx.filesDir, "song-volume.json"), File(ctx.filesDir, "song-volume-outputs.json"))
        songEqs = SongEqs(File(ctx.filesDir, "song-eq.json"))
        local = LocalFiles(ctx)
        tidal = Tidal(ctx)
        dl = Downloader(ctx, lib, tidal)
        dl.streamQuality = { streamQuality }
        dl.folder = { downloadFolder() }
        dl.onSaved = { id -> scope.launch { Copies.afterSaved(id); Tagger.start(listOf(id)) } }
        loadState()
        scope.launch { kotlinx.coroutines.delay(10_000); if (tidal.connected) Tagger.start() }
        if (tidal.connected) sources["tidal"] = tidal
        bg({ lib.prune() }) { _, _ -> savedVersion++ }
        rescan()
        // Headphones coming and going: their correction comes with them (see outputChanged).
        watchOutputs(ctx) { outputChanged() }
        // The DJ's first set made ahead while it's off (and made again when it goes stale), so it starts at once
        scope.launch { delay(20_000); while (true) { runCatching { djWarm() }; delay(10 * 60_000L) } }
        PlaybackCore.start()   // the clock of what's playing: the position, what counts as played, crossfade, the watches
    }


    // ---- a song change that took too long, written down (Settings › Problems) ----
    // From a song ending by itself to the next one sounding should take a moment. When it takes more than 2.5 seconds
    // (the DJ on the phone stopped after every song heard to its end: the plan's D16), each step it went through and
    // when (milliseconds from the song's end) is noted, so the step that held it up can be read. The first 12 only.
    /** A step of a song change, from the phone's player (MusicService). */
    fun trace(what: String) = step(what)
    private fun step(what: String) = PlaybackCore.step(what)

    fun toast(text: String) { toasts.trySend(text) }

    /** Run work off the main thread; hand (result, error) to done on it. */
    fun <T> bg(work: () -> T, done: (T?, Throwable?) -> Unit = { _, _ -> }) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(work) }
            done(r.getOrNull(), r.exceptionOrNull())
        }
    }

    // ---- remembering ----
    @Serializable
    private data class State(
        val queue: List<Track> = emptyList(), val index: Int = -1, val radio_from: String? = null,
        val repeat: String = "off", val eq: EqSettings = EqSettings(), val source: String = "ytm",
        val sync_sources: Map<String, Boolean> = emptyMap(), val lyrics_mode: String = "genius",
        val folders: List<String> = listOf("Music/"), val art_colors: Boolean = true, val keep_played: Boolean = false,
        val music_sort: String = "title", val search_in: String = "tidal", val dj: Dj.State = Dj.State(),
        val api_keys: Map<String, String> = emptyMap(), val shuffled: Boolean = false, val playlist_sort: String = "order",
        val browse_local: Boolean = false, val search_before_local: String = "tidal",
        val look: String = "default", val vis_preset: String = "bars", val wrapped_style: String = "classic",
        val skip_order: String = "most", val download_dir: String = "", val dupe_action: String = "ask",
        val queues: List<SavedQueue> = emptyList(), val queue_name: String = "", val queue_id: String = "",
        val multi_queue: Boolean = true, val styled: Boolean = false, val lyrics_to_files: Boolean = true,
        val apple_ui: Boolean = false, val autoplay: Boolean = true, val np_fallback: String = "apple-dark",
        val drawer_folds: Map<String, Boolean> = mapOf("music" to true),
        val pins: List<Pin> = emptyList(), val layouts: Map<String, String> = emptyMap(), val mic_asked: Boolean = false,
        val skip_period: String = "all", val bit_perfect: Boolean = false, val replay_gain: String = "off",
        val dj_new: Boolean = true, val top_mine_only: Boolean = true, val top_period: String = "all",
        val top_kind: String = "songs", val collage_size: Int = 25, val artist_picks: Map<String, String> = emptyMap(),
        val similar_kept: List<String> = emptyList(), val album_picks: Map<String, String> = emptyMap(),
        val song_picks: Map<String, String> = emptyMap(), val singles_kept: List<String> = emptyList(), val not_dupes: List<String> = emptyList(),
        val tab_sorts: Map<String, String> = emptyMap(), val tab_picks: Map<String, String> = emptyMap(),
    )

    private val stateFile get() = File(ctx.filesDir, "state.json")

    private fun loadState() {
        val st = readText(stateFile)?.let { runCatching { JSON.decodeFromString(State.serializer(), it) }.getOrNull() } ?: State()
        // The queue and its place are the playback service's to put back (PlaybackCore); what goes into the player, at the end
        PlaybackCore.restore(PlaybackCore.Saved(st.queue, st.index, st.radio_from, st.repeat, st.shuffled, st.queues, st.queue_name, st.queue_id, st.multi_queue))
        source = st.source.takeIf { it in SOURCES } ?: "ytm"
        // Nothing downloads on its own until you switch a service's sync on.
        SOURCES.keys.forEach { syncSources[it] = st.sync_sources[it] ?: false }
        lyricsMode = st.lyrics_mode
        folders = st.folders
        artColors = st.art_colors
        keepPlayed = false   // Keep (Accounts) was taken out in 3.0.0: songs you play are saved only when you pick Download
        musicSort = st.music_sort
        searchIn = st.search_in.takeIf { it in SEARCH_IN } ?: "tidal"
        dj = st.dj
        apiKeys = st.api_keys.filterKeys { it in API_KEYS }
        playlistSort = st.playlist_sort.takeIf { it in PLAYLIST_SORTS } ?: "order"
        tabSorts.clear(); tabSorts.putAll(st.tab_sorts.filterValues { it in SORT_NAMES })
        tabPicks.clear(); tabPicks.putAll(st.tab_picks.filterValues { it in setOf("album", "ep", "single") })
        browseLocal = st.browse_local
        searchBeforeLocal = st.search_before_local.takeIf { it in SEARCH_IN && it != "local" } ?: "tidal"
        // The Apple Music look came with 1.3: everyone starts on it (dark if the phone is), and can pick any other after.
        // 1.4 keeps Light, Dark and Now playing: any other look becomes Light or Dark, as the phone is.
        look = st.look.takeIf { st.apple_ui && org.omarchy.music.ui.Looks.known(it) } ?: "nowplaying"   // 2.0: a first start is on Now playing
        npFallback = st.np_fallback.takeIf { org.omarchy.music.ui.Looks.knownFallback(it) } ?: "apple-dark"
        drawerFolds.clear(); drawerFolds.putAll(st.drawer_folds)
        pins.clear(); pins.addAll(st.pins)
        micAsked = st.mic_asked
        skipPeriod = st.skip_period.takeIf { it in setOf("week", "month", "year", "all") } ?: "all"
        bitPerfect = st.bit_perfect
        djNew = st.dj_new
        topMineOnly = st.top_mine_only
        topPeriod = st.top_period.takeIf { it in setOf("week", "month", "year", "all") } ?: "all"
        topKind = (if (st.top_kind == "time") "time_artists" else st.top_kind)
            .takeIf { it in setOf("songs", "albums", "artists", "time_songs", "time_albums", "time_artists") } ?: "songs"
        collageSize = st.collage_size.takeIf { it in listOf(5, 10, 15, 20, 25) } ?: 25
        artistPicks = st.artist_picks
        similarKept = st.similar_kept
        albumPicks = st.album_picks
        songPicks = st.song_picks
        singlesKept = st.singles_kept.toSet()
        notDupes = st.not_dupes.toSet()
        Dj.withNew = djNew
        replayGain = "off"   // ReplayGain, Balance and Tilt were taken out in 3.0.0: back to off, centre and neutral
        if (Prefs.num(Prefs.BALANCE, 0f) != 0f) Prefs.setNum(Prefs.BALANCE, 0f)
        if (Prefs.num(Prefs.TILT, 0f) != 0f) Prefs.setNum(Prefs.TILT, 0f)
        eqProcessor.bypass = bitPerfectNow()
        layouts.clear(); layouts.putAll(st.layouts.filterValues { it in LAYOUTS })
        autoplay = st.autoplay
        applyLook()
        visPreset = st.vis_preset
        wrappedStyle = st.wrapped_style
        skipOrder = st.skip_order.takeIf { it in setOf("most", "least", "heard") } ?: "most"
        downloadDir = st.download_dir
        // 3.0.1: downloads that went straight into Music/ go to Music/Artists/ from now on (once; pick another after)
        if (!Prefs.on("downloads_to_artists")) {
            Prefs.set("downloads_to_artists", true)
            if (downloadDir.trim().trimEnd('/', '\\').let { it.isNotEmpty() && it.equals("Music", true) }) {
                downloadDir = ""
                scope.launch(Dispatchers.Main) { saveState() }   // kept: the state file says Music/Artists/ from now on
            }
        }
        dupeAction = st.dupe_action.takeIf { it in setOf("ask", "keep", "replace") } ?: "ask"
        eq = st.eq
        applyEq()
        lyricsToFiles = st.lyrics_to_files
        PlaybackCore.restored()   // the song from last time into the player (not playing), or waiting for the player and the login
    }

    var restoring = false   // a backup is going back in: nothing saved over it

    /** The state written now, not in the background (a backup is being made of it). */
    fun saveStateNow() { if (!restoring) writeAtomic(stateFile, JSON.encodeToString(State.serializer(), stateNow())) }

    fun saveState() {
        if (restoring) return
        val st = stateNow()
        bg({ writeAtomic(stateFile, JSON.encodeToString(State.serializer(), st)) })
    }

    private fun stateNow(): State {
        val q = PlaybackCore.saved()   // the queue's part
        return State(q.queue, q.index, q.radioFrom, q.repeat, eq, source, syncSources.toMap(), lyricsMode, folders, artColors, keepPlayed, musicSort, searchIn, dj, apiKeys, q.shuffled, playlistSort,
            browseLocal, searchBeforeLocal, look, visPreset, wrappedStyle, skipOrder, downloadDir, dupeAction,
            q.queues, q.queueName, q.queueId, q.multiQueue, true, lyricsToFiles, true, autoplay, npFallback, drawerFolds.toMap(), pins.toList(), layouts.toMap(), micAsked, skipPeriod,
            bitPerfect, replayGain, djNew, topMineOnly, topPeriod, topKind, collageSize, artistPicks, similarKept, albumPicks, songPicks, singlesKept.toList(), notDupes.toList(), tabSorts.toMap(), tabPicks.toMap())
    }

    fun saveApiKeys(keys: Map<String, String>) {
        apiKeys = keys.mapValues { it.value.trim() }.filter { it.key in API_KEYS && it.value.isNotEmpty() }
        saveState()
    }

    /** Called once the playback service is up: a song restored from Tidal can load now. */
    fun playerReady() = PlaybackCore.playerReady()

    // ---- your music ----
    /** Every song you have: downloaded here (once each) and in your folders. */
    fun mySongs(): List<Track> {
        val seen = HashSet<String>()
        val saved = lib.items.values.sortedByDescending { it.added }.filter { seen.add(it.file) }.map { it.track() }
        return saved + local.files.filter { it.file !in seen }
    }

    /** Your songs by album, the albums in name order (each with its songs in their order). */
    fun albums(): List<Pair<Album, List<Track>>> = mySongs().groupBy { Covers.albumKey(it) }.values.map { songs ->
        val t = songs.first()
        val artist = songs.groupingBy { it.artist.split(",")[0].trim() }.eachCount().maxByOrNull { it.value }?.key ?: t.artist
        Album("album:" + Covers.albumKey(t), t.album.ifEmpty { "Unknown album" }, artist, count = songs.size.toString()) to songs
    }.sortedBy { norm(it.first.title) }

    /** Which tab a release of yours is in: "album", "ep" or "single", by its album tag as taggers write it ("… - EP",
     *  "… - Single", "… (Single)"); any other is an album, however few of its songs you have (Albums › Low song count
     *  albums). Songs in an Artists folder (Artists/<Artist>/<Title - EP>…) go by their folder; songs with no album at
     *  all (single) are singles. */
    fun releaseKind(title: String, songs: List<Track>, single: Boolean = false, byCount: Boolean = true): String {
        songs.mapNotNull { tabPicks[tabKey(it)] }.takeIf { it.size * 2 > songs.size }?.groupingBy { it }?.eachCount()
            ?.maxByOrNull { it.value }?.let { return it.key }   // put there by hand (Tagger › Tab)
        // Where most of its songs are, then what its album tag says: a folder named as an EP or a single decides, a
        // plainly named one leaves it to the tag (releaseKindOf)
        val folder = songs.mapNotNull { folderKind(folderOf(it)) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return releaseKindOf(folder, if (isEp(title)) "ep" else if (single || isSingleRelease(title)) "single" else null)
    }

    /** A song's key for Tagger › Tab: its file, written one way. */
    fun tabKey(t: Track): String = (t.file ?: lib.items[t.id]?.file ?: t.id).replace('\\', '/').lowercase()

    /** Which tab one song of yours sits in ("album", "ep", "single"), as its release is sorted; null if it isn't yours. */
    fun tabOf(t: Track): String? {
        if (!t.isFile && !lib.has(t.id)) return null
        tabPicks[tabKey(t)]?.let { return it }
        val folder = folderKind(folderOf(t))
        val tag = when { isEp(t.album) -> "ep"; isSingleRelease(t.album) -> "single"; t.album.isBlank() && folder == null -> "single"; else -> null }
        return releaseKindOf(folder, tag)
    }

    /** Tagger › Tab: put the songs in Albums, EPs or Singles whatever their tags say (null: back to the usual way). */
    fun setTab(tracks: List<Track>, kind: String?) {
        for (t in tracks) if (kind == null) tabPicks.remove(tabKey(t)) else tabPicks[tabKey(t)] = kind
        saveState(); musicVersion++
        toast(if (kind == null) "Back where their tags put them" else "Moved to ${mapOf("album" to "Albums", "ep" to "EPs", "single" to "Singles")[kind]}")
    }

    /** A song's genre, as its file says (songs without one: Unknown genre). */
    fun genreOf(t: Track): String {
        val e = lib.items[t.id]
        val id = LocalFiles.idOf(t.file ?: e?.file)
        return id?.let { genreFix[it] }?.takeIf { it.isNotBlank() }   // written here since the last library read
            ?: e?.genre?.takeIf { it.isNotBlank() }                    // a download: its own genre
            ?: id?.let { local.genres[it] }?.takeIf { it.isNotBlank() } ?: "Unknown genre"
    }

    /** Genres written into files here, by file: the phone's media library can take a while to notice. */
    private val genreFix: MutableMap<String, String> by lazy {
        java.util.concurrent.ConcurrentHashMap(runCatching { JSON.decodeFromString(
            GENRE_FIX_SER,
            Prefs.text("genre_fix").ifEmpty { "{}" }) }.getOrDefault(emptyMap()))
    }
    private val GENRE_FIX_SER = kotlinx.serialization.builtins.MapSerializer(String.serializer(), String.serializer())
    private fun noteGenre(file: String, g: String) {
        val id = LocalFiles.idOf(file) ?: return
        genreFix[id] = g
        Prefs.setText("genre_fix", JSON.encodeToString(
            GENRE_FIX_SER,
            genreFix.toMap()))
    }

    /** Your songs by genre, the most songs first. */
    fun genres(): List<Pair<String, List<Track>>> = mySongs().groupBy { genreOf(it) }.toList()
        .sortedWith(compareBy<Pair<String, List<Track>>>({ it.first == "Unknown genre" }, { -it.second.size }))

    /** A song's folder on the phone ("Music/Album/"). */
    fun folderOf(t: Track): String {
        val f = t.file ?: lib.items[t.id]?.file
        return local.paths[f] ?: LocalFiles.idOf(f)?.let { local.allPaths[it] } ?: LocalFiles.DOWNLOADS
    }

    /** Your songs by folder, in folder order. */
    fun folderSongs(): List<Pair<String, List<Track>>> = mySongs().groupBy { folderOf(it) }.toList().sortedBy { it.first.lowercase() }

    /** Your songs by the artist called name: saved, in your folders or playlists, or played. */
    fun songsBy(name: String): List<Track> {
        val want = norm(name)
        val seen = HashSet<Pair<String, String>>()
        return (mySongs() + lists.items.flatMap { it.tracks } + plays.recent(2000)).filter { t ->
            want in splitArtists(t.artist).map { norm(it) } && seen.add(songKey(t))
        }
    }

    /** The artist's songs you played or have, each with how often you played it (every play added
     *  up: see [Stats]): the counted ones, then the rest of yours. */
    fun artistSongs(name: String): List<Track> {
        val mine = songIndex()
        val seen = HashSet<Pair<String, String>>()
        val out = ArrayList<Track>()
        for (s in Stats.artistSongs(name)) {
            val t = fmSong(s.title, s.artist, s.thumb, mine, s.album, sameArtistOnly = true).copy(plays = s.plays)
            if (seen.add(songKey(t))) out += t
        }
        val here = plays.items.values.associate { songKey(it.track) to it.plays }
        for (t0 in songsBy(name)) {
            // A playlist's or a played stream's copy of a song you have: yours instead (its file plays, its cover shows).
            val t = if (t0.isFile || t0.file != null || lib.has(t0.id)) t0
                else fmSong(t0.title, t0.artist, t0.thumb, mine, t0.album, sameArtistOnly = true).takeIf { it.isFile || it.file != null || lib.has(it.id) } ?: t0
            if (seen.add(songKey(t))) out += t.copy(plays = here[songKey(t0)] ?: here[songKey(t)] ?: 0)
        }
        return out
    }

    var artistsVersion by mutableStateOf(0)   // bumps when an artist's picture or bio comes in
        private set
    private val lookingUp = HashSet<String>()

    /** Look up the pictures and bios of the artists named, one after another off the main thread. */
    fun lookUpArtists(names: List<String>) {
        val todo = names.distinctBy { norm(it) }.filter { n -> !Pictures.settled(Pictures.known(n)) &&
            synchronized(lookingUp) { lookingUp.add(norm(n)) } }
        if (todo.isEmpty()) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (n in todo) {
                runCatching { Pictures.artist(n) }
                runCatching { Pictures.keepOnPhone(n) }   // the picture itself kept on the phone: not loaded again
                synchronized(lookingUp) { lookingUp.remove(norm(n)) }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { artistsVersion++ }
            }
        }
        // Ones looked up before pictures were kept on the phone: kept now (no lookup, just the picture)
        val unsaved = names.distinctBy { norm(it) }.filter { n -> Pictures.known(n)?.let { it.saved.isEmpty() && it.image.startsWith("http") } == true }
        if (unsaved.isNotEmpty()) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var got = 0
            for (n in unsaved) if (runCatching { Pictures.keepOnPhone(n) }.getOrNull() != null) got++
            if (got > 0) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { artistsVersion++ }
        }
    }

    /** The pictures of artists gone from your music (no songs of theirs left, and no plays in your stats): forgotten,
     *  their files deleted. */
    fun prunePictures() {
        val songs = runCatching { mySongs() }.getOrDefault(emptyList())
        if (songs.isEmpty()) return
        val keep = (songs.flatMap { ownArtists(it.artist) } + Stats.artists.map { it.name }).map { norm(it) }.toSet()
        bg({ Pictures.prune(keep) })
    }

    /** Keep source (a picture's web address, or a content:// one you picked) as the artist's picture. */
    fun setArtistPicture(name: String, source: String) {
        bg({
            val url = if (source.startsWith("content://")) {
                val f = File(File(ctx.filesDir, "artists").apply { mkdirs() }, "${randomHex(8)}.jpg")
                ctx.openIn(source)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                f.path
            } else source
            Pictures.chooseArtist(name, url)
        }) { _, err ->
            toast(if (err != null) "Couldn't use it: ${err.message?.take(100)}" else "New picture for $name")
            artistsVersion++
        }
    }

    /** (blocking) The artist's page from the first service that knows them. */
    fun artistInfo(name: String, prefer: String?): ArtistInfo? {
        var info: ArtistInfo? = null
        var last: Throwable? = null
        val names = listOf(name) + (if (" & " in name) listOf(name.split(" & ")[0]) else emptyList())
        for (key in listOfNotNull(prefer, source, "ytm", "tidal", "apple", "spotify").distinct()) {
            val src = sources[key] ?: continue
            if (key in setOf("tidal", "spotify") && !src.signedIn) continue
            var found: ArtistInfo? = null
            for (n in names) {
                found = try { src.artist(n) } catch (e: Exception) { last = e; null }
                if (found != null) break
            }
            found ?: continue
            if (info == null) info = found.copy(source = key, topSource = key)
            else if (found.top.isNotEmpty()) info = info.copy(top = found.top, topSource = key)
            if (info.top.isNotEmpty()) break
        }
        if (info == null && last != null) throw last!!
        return info
    }

    // ---- most played ----
    var showDiscoveryLists by mutableStateOf(false)   // Playlists: the discovery playlists unfolded
    var topKind by mutableStateOf("songs")     // Most played: songs, or albums
    fun chooseTopKind(k: String) { topKind = k; saveState() }
    var collageSize by mutableStateOf(25)       // Collage…: covers across (25×25 unless you pick fewer)
    /** Joint artists…: whose plays a pair's are ({pairKey: name, or "" for the pair's own}). */
    var artistPicks by mutableStateOf<Map<String, String>>(emptyMap())
    /** Similar artists…: the pairs you kept apart ("from>to" keys), not asked about again. */
    var similarKept by mutableStateOf<List<String>>(emptyList())
    /** Plays on singles and albums: a song's plays on one album -> the album they count for ("" left as they are). */
    var albumPicks by mutableStateOf<Map<String, String>>(emptyMap())
    /** Duplicates in Most played: a song's key -> "title\u0000artist" it counts as ("" left as it is). */
    var songPicks by mutableStateOf<Map<String, String>>(emptyMap())
    /** Duplicates: groups you marked "Not a duplicate" (their copies' files, sorted, joined): never shown again. */
    var notDupes: Set<String> = emptySet()
    fun dupeKey(g: List<Track>) = g.map { lib.items[it.id]?.file ?: it.file ?: it.id }.sorted().joinToString("\u0001")
    fun markNotDupe(g: List<Track>) {
        notDupes = notDupes + dupeKey(g); saveState()
        dupes = dupes?.filter { it !== g }
        toast("“${g[0].title}”: not a duplicate, remembered")
    }
    var singlesKept: Set<String> = emptySet()   // Singles › Single scan: songs left alone (their files)
    fun keepSingles(more: Collection<String>) { singlesKept = singlesKept + more; saveState() }
    fun addSongPicks(more: Map<String, String>) { songPicks = songPicks + more; saveState(); Stats.refresh(force = true) }
    fun addAlbumPicks(more: Map<String, String>) { albumPicks = albumPicks + more; saveState(); Stats.refresh(force = true) }
    fun keepApart(from: String, to: String) {
        val k = norm(from).replace(Regex("[\\W_]+"), "") + ">" + norm(to).replace(Regex("[\\W_]+"), "")
        if (k !in similarKept) { similarKept = similarKept + k; saveState() }
    }
    fun setArtistPick(credit: String, name: String?) {
        artistPicks = if (name == null) artistPicks - pairKey(credit) else artistPicks + (pairKey(credit) to name)
        saveState()
    }
    /** Merge into…: name's plays count as to's from now on, and name's songs the app downloaded get to in their tags
     *  (name's part of a pair's credit, or the whole credit). The desktop's merge_artist. */
    fun mergeArtist(name: String, to: String) {
        setArtistPick(name, to)
        val k = norm(name).replace(Regex("[\\W_]+"), "")
        fun same(n: String) = norm(n).replace(Regex("[\\W_]+"), "") == k
        fun swap(credit: String): String = if (same(credit)) to
            else credit.split(Regex("(?<=( & | x |, |;))|(?=( & | x |, |;))")).joinToString("") { if (same(it)) to else it }
        bg({
            var n = 0
            for (t in mySongs()) {
                val uri = Tags.editable(t) ?: continue
                val new = swap(t.artist)
                if (new == t.artist) continue
                runCatching { Tags.write(ctx, uri, mapOf("artist" to new, "albumartist" to new)) }.onSuccess { n++ }
            }
            n
        }) { n, _ ->
            toast("$name is $to now" + if ((n ?: 0) > 0) ": $n song${if (n != 1) "s" else ""} retagged" else "")
            Stats.refresh(force = true)
            musicVersion++
        }
    }

    private var resolverCache: Pair<Any, (String) -> String>? = null
    /** artistResolver for your songs and your Joint artists… picks, kept until either changes. */
    fun artistResolver(): (String) -> String {
        val sig = listOf(lib.items.size, local.files.size, musicVersion, artistPicks)
        resolverCache?.let { if (it.first == sig) return it.second }
        val r = artistResolver(runCatching { mySongs() }.getOrDefault(emptyList()), artistPicks)
        resolverCache = sig to r
        return r
    }
    fun chooseCollageSize(n: Int) { if (n != collageSize) { collageSize = n; saveState() } }

    var topPeriod by mutableStateOf("all")      // Most played over: week, month, year, all
    fun chooseTopPeriod(p: String) { topPeriod = p; MostPlayed.forget(); musicVersion++; saveState() }

    // Most played's lists (the songs, artists and albums of a period) are MostPlayed's own: these hand on.
    fun topArtists(period: String = "all"): List<MostPlayed.TopArtist> = MostPlayed.artists(period)
    fun topAlbums(period: String = "all"): List<MostPlayed.TopAlbum> = MostPlayed.albums(period)
    fun mostPlayed(period: String = "all"): List<Track> = MostPlayed.songs(period)
    val mostPlayedFromStats get() = MostPlayed.fromStats

    // Your copy of a song (which of your files a song named anywhere is) is YourCopies' own: these hand on.
    fun songIndex(): Map<Pair<String, String>, Track> = YourCopies.songIndex()
    fun mineOf(t: Track): Track = YourCopies.mineOf(t)
    fun localOf(t: Track): Track = YourCopies.localOf(t)
    fun localsOf(tracks: List<Track>): List<Track> = YourCopies.localsOf(tracks)
    fun fmSong(name: String, artist: String, image: String, mine: Map<Pair<String, String>, Track>, album: String = "",
               sameArtistOnly: Boolean = false): Track = YourCopies.fmSong(name, artist, image, mine, album, sameArtistOnly)
    fun yourCopies(tracks: List<Track>): List<Track> = YourCopies.yourCopies(tracks)
    private fun bare(a: String) = norm(a).replace(Regex("[\\W_]+"), "")

    // ---- an artist's own songs and albums; Create artist playlist ----
    /** Prefs key: Settings › Your music › Artist playlists include songs you don't have. */
    const val ARTIST_LISTS_ALL = "artist_playlist_all"
    /** Prefs key: an artist page's songs, "all" (streaming and yours) or "mine" (only your downloads and files). */
    const val ARTIST_PAGE_FROM = "artist_page_from"

    /** Whether the song is the artist's: one of its credited artists (featured ones too, in the credit or the title's
     *  "feat."), or one merged into them (Merge into…). */
    fun creditsArtist(t: Track, name: String): Boolean {
        val want = bare(name)
        if (want.isEmpty()) return false
        return (splitArtists(t.artist) + ownArtists(t.artist) + featured(t.title)).any { n ->
            bare(n) == want || artistPicks[pairKey(n)]?.let { it.isNotEmpty() && bare(it) == want } == true
        }
    }

    /** Every song of the artist's you have: downloaded or in your folders, the ones they're featured on too. */
    fun artistOwnSongs(name: String): List<Track> = mySongs().filter { creditsArtist(it, name) }

    /** Your albums (as Albums groups them) that are the artist's: billed to them, or most of their songs theirs. */
    fun artistOwnAlbums(name: String): List<Pair<Album, List<Track>>> = albums().filter { (al, songs) ->
        bare(al.sub) == bare(name) || songs.count { creditsArtist(it, name) } * 2 > songs.size
    }

    private val artistListsMaking = HashSet<String>()   // (main thread only) artists whose playlist is being made

    /** Create artist playlist: a playlist named after the artist with every song of theirs you have, by album, then
     *  track number (from the files' tags), then title. With Settings › Artist playlists include songs you don't have,
     *  their songs on the streaming service too (top songs and every album's, ones you have left out by title). A
     *  playlist of that exact name already there is brought up to date instead of made again. */
    fun makeArtistPlaylist(name0: String) {
        val name = name0.trim()
        if (name.isEmpty() || !artistListsMaking.add(norm(name))) return
        val withStreams = Prefs.on(ARTIST_LISTS_ALL)
        if (withStreams) toast("Making the playlist $name: looking up their songs…")
        bg({
            val out = ArrayList<Pair<Track, Int>>()   // each song and its number on its album (9999: none)
            for (t in artistOwnSongs(name)) {
                val f = t.file ?: lib.items[t.id]?.file
                val no = f?.let { runCatching { trackNo(FileTags.read(ctx, it)?.get("tracknumber")) }.getOrNull() } ?: 9999
                out += t to no
            }
            var unreachable = false
            if (withStreams) {
                val have = out.mapTo(HashSet()) { norm(cleanTitle(it.first.title)) }
                fun take(t: Track, no: Int) { if (have.add(norm(cleanTitle(t.title)))) out += t to no }
                val info = try { artistInfo(name, null) } catch (e: Exception) { null }
                if (info == null) unreachable = true
                else {
                    val src = sources[info.source]
                    info.albums.forEachIndexed { i, al ->
                        if (i > 0 && i % 10 == 0) toast("Making the playlist $name: album $i of ${info.albums.size}…")
                        val songs = runCatching { src?.album(al.id).orEmpty() }.getOrDefault(emptyList())
                        songs.forEachIndexed { j, t -> take(if (t.album.isEmpty()) t.copy(album = al.title) else t, j + 1) }
                    }
                    info.top.forEach { take(it, 9999) }
                }
            }
            out.sortedWith(compareBy<Pair<Track, Int>>({ it.first.album.isBlank() }, { norm(it.first.album) }, { it.second },
                { norm(it.first.title) })).map { it.first.slim() }.distinctBy { it.id } to unreachable
        }) { res, err ->
            artistListsMaking.remove(norm(name))
            if (res == null) { toast("Couldn't make the playlist: ${err?.message?.take(100)}"); return@bg }
            val (songs, unreachable) = res
            if (songs.isEmpty()) {
                toast(if (unreachable) "You have no songs of $name's, and the services couldn't be reached" else "You have no songs of $name's yet")
                return@bg
            }
            val old = lists.named(name)
            val pl = old ?: lists.create(name)
            lists.replaceTracks(pl.id, songs)
            musicVersion++
            toast("${if (old != null) "Updated" else "Made"} the playlist $name (${songs.size} song${if (songs.size != 1) "s" else ""})" +
                if (unreachable) ": only yours, the services couldn't be reached" else "")
        }
    }

    // ---- the DJ ----
    // The DJ's own part is DjDirector's (starting and stopping it, its sets, its lines and its voice): these hand on.
    /** What the DJ screen shows: the phone's DJ, or the desktop's while it's the remote. */
    fun shownDj(): Dj.Shown = DjDirector.shownDj()

    /** What the DJ plays from, as [Dj.From] keeps it: (name, songs), or null if it's gone. */
    fun djSource(f: Dj.From?): Pair<String, List<Track>>? = DjDirector.djSource(f)
    fun djFromName(f: Dj.From?): String = DjDirector.djFromName(f)

    /** What the DJ always plays from: every song of yours, in one group (see [librarySongs]). */
    val DJ_LIBRARY get() = DjDirector.DJ_LIBRARY

    /** Every song in your library, for the DJ: your own files, your downloads and the songs you saved, and every song
     *  in your playlists (streams too), each once. */
    fun librarySongs(): List<Track> {
        val seen = HashSet<Pair<String, String>>()
        val mine = mySongs().filter { seen.add(songKey(it)) }
        val listed = runCatching { lists.all().flatMap { it.tracks } }.getOrDefault(emptyList()).filter { seen.add(songKey(it)) }
        return mine + localsOf(listed)
    }

    /** Start the DJ: a first set of what you've had on repeat (or whatever it can make). */
    fun djStart(from: Dj.From? = null) = DjDirector.djStart(from)

    /** Make the first set ahead (when the DJ is off and there's none fresh): from where it played from last. */
    fun djWarm() = DjDirector.djWarm()

    fun djStop() = DjDirector.djStop()

    /** The DJ talks (Settings in the DJ: Voice): it says its line over the music (DjDirector has its voice). */
    val djVoice get() = DjDirector.djVoice
    fun chooseDjVoice(on: Boolean) = DjDirector.chooseDjVoice(on)
    val djTalking: Boolean get() = DjDirector.djTalking
    private var loadedFor: String? get() = PlaybackCore.loadedFor; set(v) { PlaybackCore.loadedFor = v }   // the song the player has in now (its id)

    /** Play from the headphones, the car, the lock screen or the notification: what the app's own Play does, so the DJ
     *  is never left stuck (talking that never came, a set it was still picking, a set it hadn't introduced yet). */
    fun sessionPlay() {
        if (Remote.active) { Remote.cmd("play"); return }
        val p = player ?: return
        if (DjDirector.djPlay(p)) return   // the DJ's part of Play: it is never left stuck
        if (p.state == Engine.IDLE || !p.hasItem) loadCurrent() else p.play()
    }

    /** The DJ stops talking (Play, Pause, a skip): the music goes on. */
    fun djHush() = DjDirector.djHush()

    /** Now playing's set button: on to the DJ's next set now, skipping the rest of this one. */
    fun djNextSet() = DjDirector.djNextSet()

    /** Switch it up (or play what you asked for): a new set now, in place of the rest of this one. */
    fun djSwitch(ask: String? = null) = DjDirector.djSwitch(ask)

    // ---- importing an .m3u8 playlist ----
    /** (blocking) The songs of the .m3u/.m3u8 playlist at uri: (name, songs, those not found).
     *  Each is a file on the phone (where the playlist says, else one by the same name in the
     *  same folder), else the same song in your music, else the best match on Tidal (YouTube
     *  Music without it). */
    fun importM3u(uri: String): Triple<String, List<Track>, List<Missed>> {
        val fileName = ctx.displayName(uri) ?: uri.substringBefore('?').substringAfterLast('/').ifEmpty { "Playlist" }
        val entries = readM3u(ctx.openIn(uri)?.use { it.readBytes() } ?: error("couldn't open that file"))
        if (entries.isEmpty()) error("no songs in $fileName")
        // The playlist's own folder, for the paths in it that start from there ("../Album/01.flac").
        val here = ctx.folderOf(uri)
        val phone = runCatching { local.everywhere() }.getOrDefault(emptyMap())
        val byPath = phone.mapKeys { it.key.lowercase() }
        val tails = HashMap<String, Track>()
        for ((p, t) in phone) {
            val parts = p.lowercase().split('/').filter { it.isNotEmpty() }
            tails.putIfAbsent(parts.takeLast(2).joinToString("/"), t)
            tails.putIfAbsent(parts.last(), t)
        }
        val mine = mySongs()
        val byKey = HashMap<Pair<String, String>, Track>()
        for (t in phone.values + mine) byKey.putIfAbsent(songKey(t), t)
        val src = sources["tidal"]?.takeIf { it.signedIn } ?: sources["ytm"]
        val out = ArrayList<Track>()
        val missed = ArrayList<Missed>()
        for ((i, e) in entries.withIndex()) {
            importing = "Finding the songs of $fileName… ${i + 1} of ${entries.size}"
            var t: Track? = null
            var why = "not found"
            var artist = e.artist
            var title = e.title
            if (Regex("^https?://").containsMatchIn(e.loc)) {
                val from = linkSource(e.loc)?.let { sources[it] }
                if (from != null) t = try { from.search(e.loc).firstOrNull() } catch (x: Exception) {
                    why = "its link didn't open (${x.message?.take(60)})"; null
                }
            } else {
                var where = (if (e.loc.startsWith("file://")) java.net.URLDecoder.decode(e.loc.substring(7).replace("+", "%2B"), "UTF-8") else e.loc).replace('\\', '/')
                where = where.replace(Regex("^(/storage/emulated/\\d+|/sdcard|/storage/self/primary)/"), "")
                val full = if (where.startsWith("/") || Regex("^[A-Za-z]:/").containsMatchIn(where)) where
                    else java.io.File("/$here", where).normalize().path.removePrefix("/")
                t = byPath[full.lowercase()]
                if (t == null) {
                    val parts = where.lowercase().split('/').filter { it.isNotEmpty() }
                    t = tails[parts.takeLast(2).joinToString("/")] ?: tails[parts.lastOrNull() ?: ""]
                }
                if (title.isEmpty()) { val (a, b) = namesFromFile(where); artist = a; title = b }   // no #EXTINF
            }
            if (t == null && title.isNotEmpty()) t = byKey[songKey(Track("", title = title, artist = artist))]
            if (t == null && title.isNotEmpty() && src != null) {
                t = try { matchSong(src, artist, title, e.seconds).first } catch (x: Exception) {
                    why = "${src.name} didn't answer (${x.message?.take(60)})"; null
                }
            }
            if (t != null) out += t.slim()
            else missed += Missed(i, out.size, artist, title.ifEmpty { e.loc.substringAfterLast('/').substringBeforeLast('.').ifEmpty { e.loc } },
                e.seconds, e.loc, if (title.isNotEmpty()) why else "the playlist doesn't name it")
        }
        return Triple(fileName.substringBeforeLast('.'), out, missed)
    }

    /** What an import didn't find, into the playlist id (see ImportReportDialog). */
    data class ImportReport(val id: String, val name: String, val missed: List<Missed>)

    /** (blocking) The song on src: (its best match or null, what the search found). Throws when src can't be reached. */
    fun matchSong(src: Source, artist: String, title: String, seconds: Int): Pair<Track?, List<Track>> {
        val hits = src.search("$artist ${bareTitle(title)}".trim()).take(8)
        return bestMatch(hits, artist, title, seconds) to hits
    }

    /** The services a song an import didn't find can be looked for on: [(key, name)]. */
    fun reportSources(): List<Pair<String, String>> =
        SOURCES.filter { (k, _) -> k != SoundCloud.KEY && sources[k]?.let { k != "tidal" || it.signedIn } == true }.toList()

    /** Put t, found for m, back where m was in the playlist id; placed: the ones put back already (m joins them). */
    fun placeMissed(id: String, m: Missed, t: Track, placed: MutableList<Missed>): Boolean {
        if (lists.add(id, listOf(t), at = missedAt(m, placed)) == 0) return false
        placed += m
        musicVersion++
        return true
    }

    /** Make a playlist of yours from the .m3u8 at uri, named after the file. */
    fun importPlaylist(uri: String) {
        if (importing != null) { toast("Still importing the last one…"); return }
        importing = "Reading the playlist…"
        bg({ importM3u(uri) }) { res, err ->
            importing = null
            if (res == null) { val why = err?.let { ImportNotes.why(it) } ?: "nothing could be read from it"
                ImportNotes.failed("playlists", RuntimeException("the playlist file: $why")); toast("Import failed: $why"); return@bg }
            val (name, tracks, missed) = res
            val id = lists.create(name).id
            lists.add(id, tracks)
            musicVersion++
            missed.forEach { logWarn("import $name: not found: ${it.name} (${it.why})") }
            ("Imported “$name”: ${tracks.size} songs" + if (missed.isNotEmpty()) " (${missed.size} not found)" else "").let {
                ImportNotes.ok("playlists", it); toast("Import worked: $it") }
            if (missed.isNotEmpty()) importReport = ImportReport(id, name, missed)
        }
    }

    private var rescanAgain = false

    fun rescan() {
        if (local.scanning) { rescanAgain = true; return }   // the folders changed mid-scan: read again after
        local.scanning = true
        bg({ local.scan(folders, lib.items.values.mapNotNullTo(HashSet()) { LocalFiles.mediaId(it.file) }) }) { changed, err ->
            local.scanning = false
            if (rescanAgain) { rescanAgain = false; rescan() }
            if (err is SecurityException) Unit   // no permission yet: the Music tab asks
            else if (err != null) toast("Couldn't read your music: ${err.message?.take(80)}")
            if (changed == true) musicVersion++
            adoptFiles()
            relinkMissing()   // songs you moved: your playlists follow them
            fillDownloadGenres()   // downloads saved without a genre: one looked up and written in
            accountsVersion++
            QualityCheck.scan()   // Loudness check: the songs new or changed since
            bg({ FlacDepths.scan() })   // each new song's format, for the mark on its row ("FLAC 24", "MP3")
            runCatching { Moods.auto() }   // Moods: read the songs new or changed since, in the background
            TagMatch.refreshSoon()   // Tags & covers match: what the new or changed songs say
        }
    }

    /** Whether a song file's address is one this device can play (not the other device's, from a backup). */
    fun fileHere(t: Track): Boolean {
        val f = t.file ?: return true
        if (!BackupMerge.fileHere(f, platformName == "android")) return false
        if (lib.has(t.id)) return true   // a download (its own checks)
        return if (f.startsWith("content:")) runCatching { uriExists(ctx, f) }.getOrDefault(true) else File(f).exists()
    }

    /** The song playing couldn't be opened (its file was moved, renamed or deleted): your copy of the same song (the
     *  same title and artist) is linked in its place everywhere (the queue, playlists, favorites) and played. False if
     *  there's none (the usual "couldn't play"). */
    fun fileGone(): Boolean {
        val t = current() ?: return false
        if (!t.isFile) return false
        val n = hereCopy(t)?.takeIf { it.file != t.file } ?: return false
        relink(mapOf(t.id to n))
        toast("“${t.title}” had moved: found it and linked it")
        loadCurrent(autoplay = true)
        return true
    }

    /** Downloads with no genre in their tags (saved before downloads had one): each looked up once (Apple, Deezer,
     *  Last.fm) and written into the file, so they leave Unknown genre. */
    private var genresBusy = false
    private fun fillDownloadGenres() {
        if (genresBusy) return
        genresBusy = true
        bg({
            val tried = Prefs.text("genres_tried").split(',').filter { it.isNotEmpty() }.toMutableSet()
            var done = 0
            val read = Prefs.text("genres_read").split(',').filter { it.isNotEmpty() }.toMutableSet()
            for (e in lib.items.values.toList()) {
                if (e.genre.isNotBlank() || (e.id in tried && e.id in read)) continue
                read += e.id
                val has = runCatching { Tags.read(ctx, listOf(e.file))["genre"] }.getOrNull()
                if (!has.isNullOrBlank()) { lib.update(e.id) { it.copy(genre = has.trim()) }; done++; continue }   // tagged: keep it
                if (e.id in tried || e.format == "m4a" || !Tags.writable(e.file)) continue
                tried += e.id
                val g = runCatching { GenreLookup.of(e.artist, e.title, e.album) }.getOrDefault("")
                // Not sent to your other device: it only fills a missing genre here, never changes one it has
                var wrote = false
                if (g.isNotEmpty()) TagSync.quietly { wrote = runCatching { Tags.write(ctx, e.file, mapOf("genre" to g)) }.isSuccess }
                if (wrote) {
                    lib.update(e.id) { it.copy(genre = g) }; done++
                }
            }
            Prefs.setText("genres_tried", tried.joinToString(","))
            Prefs.setText("genres_read", read.joinToString(","))
            done
        }) { n, _ ->
            genresBusy = false
            if ((n ?: 0) > 0) { toast("Genres added to $n download${if (n != 1) "s" else ""}"); rescan() }
        }
    }

    /** Clean and explicit versions: song old replaced by new (just downloaded): the new one takes its place in the
     *  queue, your playlists and favorites, and the old one is out of your music. */
    fun versionReplaced(old: Track, new: Track) {
        relink(mapOf(old.id to new))
        local.files = local.files.filter { it.id != old.id }
        // Its plays here go to the new one (Most played already counts both as one song: their names agree)
        bg({ plays.merge(setOf(old.id), new.slim()) })
        savedVersion++; musicVersion++
    }

    /** Songs whose files moved: each old one (by id) becomes the new one in the queue, your playlists and favorites. */
    private fun relink(moved: Map<String, Track>) {
        if (moved.isEmpty()) return
        fun fix(x: Track): Track = moved[x.id]?.slim()?.copy(queued = x.queued) ?: x
        queue = queue.map(::fix)
        lists.remap(::fix)
        favorites.remap(::fix)
        saveState(); musicVersion++
    }

    /** (after your folders are read) Songs of yours in your playlists, the queue and favorites whose file is gone: the
     *  same song found where it is now (you moved or renamed a folder) and linked, so nothing has to be made again. */
    private fun relinkMissing() {
        val songs = (queue + lists.items.flatMap { it.tracks } + favorites.tracks()).filter { it.isFile && !lib.has(it.id) }
            .distinctBy { it.id }
        if (songs.isEmpty()) return
        bg({
            val moved = HashMap<String, Track>()
            for (t in songs) if (!fileHere(t)) hereCopy(t)?.takeIf { it.file != t.file && fileHere(it) }?.let { moved[t.id] = it }
            moved
        }) { moved, _ ->
            if (moved.isNullOrEmpty()) return@bg
            relink(moved)
            toast("Found ${moved.size} moved song${if (moved.size != 1) "s" else ""} and linked them in your playlists")
        }
    }

    /** The same song of yours on this device (a file or a download), for one of the other device's song files. */
    fun hereCopy(t: Track): Track? {
        val k = looseKey(t)
        if (k.first.isEmpty()) return null
        return mySongs().firstOrNull { (it.isFile || lib.has(it.id)) && looseKey(it) == k && it.id != t.id }
    }

    /** After a backup from the other device: its song files in your queue, playlists and favorites become the same
     *  songs of yours here, once this device has read its folders (until none are left to find). */
    private fun adoptFiles() {
        if (!Prefs.on("adopt_files") || local.files.isEmpty()) return
        val here = HashMap<Pair<String, String>, Track>()
        for (m in mySongs()) if (m.isFile || lib.has(m.id)) here.putIfAbsent(looseKey(m), m)
        var found = 0
        var left = 0
        fun fix(t: Track): Track {
            if (!t.isFile || BackupMerge.fileHere(t.file ?: return t, platformName == "android")) return t
            val h = here[looseKey(t)] ?: run { left++; return t }
            found++
            return h.slim().copy(queued = t.queued)
        }
        queue = queue.map(::fix)
        lists.remap(::fix)
        favorites.remap(::fix)
        if (found > 0) { saveState(); musicVersion++; toast("Matched $found song${if (found != 1) "s" else ""} from your backup to your music here") }
        if (left == 0) Prefs.set("adopt_files", false)
    }

    /** A song file that isn't on this device (from the other one's backup, or since gone) in the queue: the same
     *  song of yours here plays in its place, else the service's. */
    internal fun standIn(t: Track, autoplay: Boolean) {
        val at = index
        fun swap(n: Track) {
            if (index != at || current()?.id != t.id) return
            queue = queue.toMutableList().also { it[at] = n.copy(queued = false) }
            loadCurrent(autoplay)
        }
        hereCopy(t)?.let { swap(it); return }
        val key = searchIn.takeIf { it != "local" && it != "tidal" || (it == "tidal" && sources["tidal"]?.signedIn == true) } ?: "ytm"
        val src = sources[key] ?: sources["ytm"]
        if (src == null) { toast("“${t.title}” isn't on this ${org.omarchy.music.ui.DEVICE}: still connecting to the services"); return }
        status = "Finding “${t.title}”…"
        bg({ matchSong(src, t.artist, t.title, t.duration.toInt()).first }) { found, _ ->
            if (index != at) return@bg
            status = ""
            if (found != null) swap(found)
            else {
                toast("“${t.title}” isn't on this ${org.omarchy.music.ui.DEVICE} and wasn't found to stream")
                if (autoplay) scope.launch { delay(1500); if (index == at) next() }
            }
        }
    }

    /** Songs' files moved (Fix albums and artists): the queue and the playlists follow them; then a look at the folders. */
    fun filesMoved(moved: Map<String, String>) {
        fun fix(t: Track): Track {
            val f = t.file?.replace('\\', '/') ?: return t
            val to = moved[f] ?: return t
            return t.copy(file = to, id = if (t.source == "file" && t.id.startsWith("f")) "f" + to.hashCode().toUInt().toString(16) else t.id)
        }
        queue = queue.map(::fix)
        lists.remap(::fix)
        lib.items.values.filter { it.file.replace('\\', '/') in moved }.forEach { e -> lib.update(e.id) { it.copy(file = moved[e.file.replace('\\', '/')]!!) } }
        saveState()
        rescan()
    }

    fun rescanSoon() { if (now() - local.scanned > 120) rescan() }

    /** Your folders become list; one inside another is dropped, since the outer one has its songs. */
    @JvmName("chooseFolders")
    fun setFolders(list: List<String>) {
        val clean = list.distinct().filter { f -> list.none { it != f && f.startsWith(it) } }.sorted()
        if (clean == folders) return
        folders = clean
        saveState()
        rescan()
    }

    fun removeFolder(path: String) = setFolders(folders - path)

    // ---- your playlists ----
    /** Your songs' genres, the most common first. */
    fun genresHere(n: Int = 30): List<String> = mySongs().map { genreOf(it) }.filter { it.isNotEmpty() && it != "Unknown genre" }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(n).map { it.key }

    var repeatPeriod by mutableStateOf("week")   // Repeat's chart: week, month, year (not kept)

    // ---- Discovery: an artist's songs you haven't got, heard one by one ----
    // Discovery playlists (the pairs, making them, a song heard to the end) are Discoveries' own: these hand on.
    val discovering get() = Discoveries.making
    fun discoveryLists(): List<Triple<String, Playlist?, Playlist?>> = Discoveries.pairs()
    fun makeDiscovery(artist: String) = Discoveries.make(artist)
    fun releaseName(r: NewReleases.Release) = Discoveries.releaseName(r)
    fun makeReleaseDiscoveries(chosen: List<Pair<NewReleases.Release, List<Track>>>, quiet: Boolean = false): List<String> =
        Discoveries.makeForReleases(chosen, quiet)
    fun discovered(t: Track) = Discoveries.heardOut(t)
    fun reviewDiscovered(id: String, download: Set<String>, remove: Set<String>) = Discoveries.review(id, download, remove)

    // ---- New releases: straight to one, and a discovery playlist from the ones you pick ----
    private val releaseFound = java.util.concurrent.ConcurrentHashMap<String, Triple<String, Album?, List<Track>>>()

    /** (blocking) Where a new release is on the streaming services (never your files): (service, its album,
     *  []) for the album (or single, or EP) by its name and artist, else (service, null, [the song]) for a
     *  single, else null. */
    fun releaseFind(r: NewReleases.Release): Triple<String, Album?, List<Track>>? {
        val ck = NewReleases.key(r)
        releaseFound[ck]?.let { return it }
        val want = norm(cleanTitle(r.title))
        var single: Triple<String, Album?, List<Track>>? = null
        var last: Throwable? = null
        for (key in listOf(source, "tidal", "ytm").distinct()) {
            val src = sources[key] ?: continue
            if (key !in setOf("tidal", "ytm") || (key == "tidal" && !src.signedIn)) continue
            val res = try { src.find("${r.artist} ${r.title}") } catch (e: Exception) { last = e; continue }
            res.albums.firstOrNull { al ->
                norm(cleanTitle(al.title)) == want && (NewReleases.sameName(r.artist, al.sub.substringBefore(" · ").substringBefore(",")) ||
                    norm(r.artist) in norm(al.sub))
            }?.let { al -> return Triple(key, al, emptyList<Track>()).also { releaseFound[ck] = it } }
            if (single == null) res.songs.filter { t ->
                norm(cleanTitle(t.title)) == want && NewReleases.sameName(r.artist, splitArtists(t.artist).firstOrNull() ?: "")
            }.take(1).takeIf { it.isNotEmpty() }?.let { single = Triple(key, null, it) }
        }
        single?.let { releaseFound[ck] = it; return it }
        last?.let { logWarn("release ${r.title}", it) }
        return null
    }

    /** (blocking) A new release's songs, from the services (see [releaseFind]). */
    fun releaseTracks(r: NewReleases.Release): List<Track> {
        val (key, al, songs) = releaseFind(r) ?: return emptyList()
        val got = if (al != null) sources[key]?.album(al.id).orEmpty() else songs
        return got.map { if (it.album.isEmpty()) it.copy(album = r.title) else it }
    }

    // An album's songs you haven't got (its page's bottom) are AlbumMissing's own: these hand on.
    fun albumMissing(album: String, artist: String, tracks: List<Track>, kind: String = "album"): List<MissingSong> =
        AlbumMissing.find(album, artist, tracks, kind)
    fun downloadMissing(artist: String, album: String, want: List<MissingSong>) = AlbumMissing.download(artist, album, want)

    // Copying a service's playlists into yours, and downloading whole playlists, are YourPlaylists' own too.
    fun importServicePlaylist(key: String, pl: Album, quiet: Boolean = false, done: ((Int) -> Unit)? = null) =
        YourPlaylists.importFrom(key, pl, quiet, done)
    fun importAllServicePlaylists(key: String, lists: List<Album>) = YourPlaylists.importAllFrom(key, lists)
    fun downloadPlaylists(ids: Collection<String>) = YourPlaylists.download(ids)

    /** (blocking) Settings › Reset plays: every imported play cleared (Spotify's, Apple Music's, Deezer's, YouTube
     *  Music's, Tidal's), for an import that doubled plays Last.fm had already; Last.fm's scrobbles stay. */
    fun resetImportedPlays() {
        SpotifyHistory.clear(); AppleHistory.clear(); DeezerHistory.clear(); YouTubeHistory.clear(); TidalHistory.clear()
        Stats.refresh(force = true)
    }

    // Your playlists and their folders (making, filling, deleting them) are YourPlaylists' own: these hand on.
    fun newPlaylist(name: String, synced: Boolean = false): String = YourPlaylists.create(name, synced)
    fun addToPlaylist(id: String, tracks: List<Track>) = YourPlaylists.add(id, tracks)
    fun removeFromPlaylist(id: String, ids: Set<String>) = YourPlaylists.remove(id, ids)
    fun renamePlaylist(id: String, name: String) = YourPlaylists.rename(id, name)
    fun setPlaylistSynced(id: String, on: Boolean) = YourPlaylists.setSynced(id, on)
    fun deletePlaylist(id: String) = YourPlaylists.delete(id)
    fun newFolder(name: String, item: FolderItem? = null) = YourPlaylists.newFolder(name, item)
    fun renameFolder(id: String, name: String) = YourPlaylists.renameFolder(id, name)
    fun deleteFolder(id: String) = YourPlaylists.deleteFolder(id)
    fun addToFolder(id: String, item: FolderItem) = YourPlaylists.addToFolder(id, item)
    fun removeFromFolder(id: String, item: FolderItem) = YourPlaylists.removeFromFolder(id, item)

    fun playList(tracks: List<Track>, shuffled: Boolean = false) {
        val t = if (shuffled) shuffleSongs(tracks) else tracks
        if (t.isNotEmpty()) { playFrom(t, 0, radio = false); this.shuffled = shuffled }
    }

    // ---- downloads ----
    // Download (which songs are new, the asking about copies, the songs waiting saved together) is Downloads' own.
    fun download(tracks: List<Track>, checked: Boolean = false) = Downloads.start(tracks, checked)

    /** Your copies of a download, to put away once it's in (Copies: replace). Asked from other threads too. */
    internal fun replaceLater(id: String, copies: List<Track>) = Copies.replaceLater(id, copies)

    fun saveCurrent() { current()?.let { download(listOf(it)) } }

    fun downloadNowNote(): String = Downloads.nowNote()

    /** Settings › Downloads › Quality: "flac24", "flac16" or "mp3" (320 kbps). */
    var downloadQuality by mutableStateOf("flac24")
        private set
    fun chooseDownloadQuality(q: String) { downloadQuality = q; Prefs.setText(Prefs.DL_QUALITY, q) }
    fun loadDownloadQuality() {
        downloadQuality = Prefs.text(Prefs.DL_QUALITY, "flac24").ifEmpty { "flac24" }
        streamQuality = Prefs.text("stream_quality", "flac24").ifEmpty { "flac24" }
    }

    /** Settings › Stream quality: "flac24", "flac16" or "aac" (320 kbps): what Tidal streams in, for every service's
     *  songs (they all play from Tidal when it's connected). YouTube's stay YouTube's best. */
    var streamQuality by mutableStateOf("flac24")
        private set
    fun chooseStreamQuality(q: String) { streamQuality = q; Prefs.setText("stream_quality", q); dl.forgetStreams() }

    fun downloadQualityNote(): String = Downloads.qualityNote()

    // ---- copies of a song ----
    // Copies of a song (a download you have already, your copy put away for it) and Duplicates are Copies' own.
    fun copiesOf(t: Track): List<Track> = Copies.copiesOf(t)
    fun autoSaveOk(t: Track): Boolean = Copies.autoSaveOk(t)
    fun replaceCopies(old: List<Track>, new: Track, what: String) = Copies.replaceCopies(old, new, what)
    fun queueTrash(job: TrashJob) = Copies.queueTrash(job)
    fun trashed(job: TrashJob, ok: Boolean) = Copies.trashed(job, ok)
    fun retarget(oldIds: List<String>, new: Track) = Copies.retarget(oldIds, new)
    fun answerDupes(r: String) = Copies.answerDupes(r)
    fun findDupes() = Copies.findDupes()
    fun copyRank(t: Track): Double = Copies.copyRank(t)
    fun copyInfo(t: Track): String = Copies.copyInfo(t)
    fun whereIs(t: Track): String = Copies.whereIs(t)
    fun deleteDupe(group: List<Track>, t: Track) = Copies.deleteDupe(group, t)
    fun removeDupes(picks: List<Pair<List<Track>, Set<String>>>) = Copies.removeDupes(picks)

    // ---- Upgrade: your files for Tidal's FLAC where it's better ----
    fun upgradeSongs(tracks: List<Track>? = null, force: Boolean = false) {
        if (upgrading != null) return
        if (!tidal.connected) { toast("Connect Tidal in Settings first: the new copies come from there, in FLAC"); return }
        val todo = (tracks ?: local.files).filter { upgradable(it) }
        if (todo.isEmpty()) { toast("No songs of yours to upgrade"); return }
        upgradeStop = false
        upgrading = "Upgrading · 0 of ${todo.size}"
        Tagger.pause()   // the new FLACs are tagged together at the end
        val roots = folders.sortedByDescending { it.length }
        bg({ dl.upgrade(todo, force, { upgradeStop }, { t -> folderOf(t).let { f -> roots.firstOrNull { f.startsWith(it) } } }) { n -> scope.launch { upgrading = "Upgrading · $n of ${todo.size}" } } }) { out, err ->
            upgrading = null
            Tagger.resume()
            if (out == null) { toast("Couldn't upgrade: ${err?.message?.take(90)}"); return@bg }
            val playing = current()?.id
            // The song playing carries on in its new file from the same moment; its old file goes with the rest.
            out.done.firstOrNull { (old, id) -> old.id == playing && lib.has(id) }?.let { (old, id) -> swapPlaying(old, id) }
            val done = out.done.filter { (_, id) -> lib.has(id) }
            if (done.isNotEmpty()) queueTrash(TrashJob(done.map { it.first }, done.associate { (old, id) -> old.id to lib.items[id]!!.track() }, "upgraded songs"))
            toast("Upgraded ${done.size} song${if (done.size != 1) "s" else ""} to FLAC" +
                (if (out.missing.isNotEmpty()) " · ${out.missing.size} not on Tidal" else "") +
                (if (out.kept.isNotEmpty()) " · ${out.kept.size} as good already" else "") +
                (if (out.failed.isNotEmpty()) " · ${out.failed.size} couldn't" else ""))
            if (out.kept.isNotEmpty() && !force) upgradeKept = out.kept
            savedVersion++; musicVersion++
        }
    }

    /** The song playing (old) was upgraded to id: play on in the new file from where you are. */
    private fun swapPlaying(old: Track, id: String) {
        val new = lib.items[id]?.track() ?: return
        val at = player?.positionMs?.let { it / 1000.0 } ?: position
        val wasPaused = paused
        retarget(listOf(old.id), new)
        queue = queue.toMutableList().also { if (index in it.indices) it[index] = new.slim() }
        resumeAt = at
        goingBack = true   // not a skip
        loadCurrent(autoplay = !wasPaused)
    }

    // ---- favorites, a song's volume, moods by hand, looks ----
    fun setFavorite(tracks: List<Track>, on: Boolean? = null) {
        if (tracks.any { it.source == SoundCloud.KEY }) {   // its own service: liked on SoundCloud, not mixed into your favorites
            val sc = tracks.filter { it.source == SoundCloud.KEY }
            val src = sources[SoundCloud.KEY]
            if (sc.size == 1 && src?.signedIn == true) {
                val t0 = sc[0]; val like = t0.id !in liked
                liked = if (like) liked + t0.id else liked - t0.id
                bg({ src.rate(t0, like) }) { _, err -> if (err != null) toast("SoundCloud said: ${err.message?.take(80)}") }
                toast(if (like) "Liked on SoundCloud" else "Not liked on SoundCloud any more")
            } else toast("SoundCloud songs can't go in your favorites: like them on SoundCloud (sign in under Settings › Accounts)")
        }
        @Suppress("NAME_SHADOWING") val tracks = tracks.filter { it.source != SoundCloud.KEY }
        if (tracks.isEmpty()) return
        val star = on ?: !favorites.has(tracks[0])
        favorites.set(tracks, star)
        val what = if (tracks.size == 1) "“${tracks[0].title}”" else "${tracks.size} songs"
        toast(if (star) "$what in your favorites" else "$what out of your favorites")
        musicVersion++
    }

    /** Volume…: db for tracks, on output (the one playing now, by default) or on every output (null). */
    fun setGain(tracks: List<Track>, db: Double, output: String? = outputNow.key) {
        if (tracks.isEmpty()) return
        gains.set(tracks, db, output)
        current()?.let { eqProcessor.songGain = gainOf(it) }
        applyEq()   // Dual volume off: the Boost goes or comes back with the song's own volume
        val v = gains.saved(tracks[0], output) ?: 0.0
        val what = if (tracks.size == 1) "“${tracks[0].title}”" else "${tracks.size} songs"
        val where = if (output == null) "" else " on ${outLabel(output)}"
        toast(if (v == 0.0) "$what back to normal volume$where" else "$what at ${"%+.1f".format(v)} dB$where")
        musicVersion++
    }

    /** Songs' own volumes always apply (the old Song volume changes switch is gone): this re-applies the playing song's. */
    fun refreshSongVolume() {
        current()?.let { eqProcessor.songGain = gainOf(it) }
        applyEq()
        musicVersion++
    }

    /** The change t plays with here and now (dB): its own for this output, else its every-output one (0: none). */
    fun gainNow(t: Track): Double = gains.get(t, outputNow.key)

    internal fun gainOf(t: Track) = Math.pow(10.0, gainNow(t) / 20)



    fun chooseLook(l: String) { look = l; coverRgb = null; applyLook(); saveState() }
    fun chooseNpFallback(l: String) { if (!org.omarchy.music.ui.Looks.knownFallback(l)) return; npFallback = l; if (look == "nowplaying") applyLook(); saveState() }
    /** The playing song's cover colour, for the Now playing look (null: none yet). */
    var coverRgb: Int? = null
    fun applyLook() {
        if (look == "nowplaying") org.omarchy.music.ui.Looks.applyCover(coverRgb, npFallback)
        else org.omarchy.music.ui.Looks.apply(look)
        org.omarchy.music.ui.LookFx.override()   // Personalization › Effects: your own pick over the look's
    }
    fun chooseVisPreset(p: String) { visPreset = p; saveState() }
    fun chooseWrappedStyle(s: String) { wrappedStyle = s; saveState() }
    /** Downloads that came from YouTube (Opus), the ones Tidal has now got again in FLAC: Downloads' Get FLAC. */
    fun youtubeDownloads() = lib.items.values.filter { it.format == "opus" || (it.quality.startsWith("YouTube") && it.id !in flacTried) }
    private val flacTried = HashSet<String>()   // YouTube AAC downloads looked up on Tidal already (this time the app's open)
    var flacing by mutableStateOf<String?>(null)
        private set
    fun flacDownloads() {
        if (flacing != null) return
        val todo = youtubeDownloads().map { it.id }
        if (todo.isEmpty()) return
        flacing = "Getting FLAC… 0 of ${todo.size}"
        bg({
            var got = 0; var none = 0; var i = 0
            for (id in todo) {
                i++
                scope.launch(Dispatchers.Main) { flacing = "Getting FLAC… $i of ${todo.size}" }
                if (runCatching { dl.toFlac(id) }.getOrDefault(false)) got++ else none++
                synchronized(flacTried) { flacTried += id }
            }
            got to none
        }) { r, err ->
            flacing = null
            savedVersion++; musicVersion++
            toast(err?.message?.let { "Couldn't get FLAC: $it" } ?: r?.let { (got, none) ->
                "$got upgraded (FLAC from Tidal, else AAC)" + if (none > 0) " · $none left as they are" else "" } ?: "")
        }
    }

    /** Downloads' Refresh: the list read again (a download that didn't show up comes in). */
    fun refreshDownloads() {
        bg({ lib.refresh() }) { n, _ ->
            savedVersion++; musicVersion++
            toast(if ((n ?: 0) > 0) "Refreshed: $n more download${if (n != 1) "s" else ""}" else "Downloads are up to date")
        }
    }

    fun chooseSkipOrder(s: String) { skipOrder = s; saveState() }
    fun chooseSkipPeriod(s: String) { skipPeriod = s; saveState() }
    /** When Skips counts from: a week, a month or a year ago, or all time (0). */
    fun skipsSince(): Long = when (skipPeriod) { "week" -> 7; "month" -> 30; "year" -> 365; else -> 0 }
        .let { d -> if (d == 0) 0L else now().toLong() - d * 86_400L }
    fun chooseDownloadDir(d: String) {
        downloadDir = d; saveState()
        toast("Downloads go to ${downloadFolder()}, by artist and album")
    }
    fun chooseDupeAction(a: String) { dupeAction = a; saveState() }

    /** Write tracks as an .m3u8 playlist to uri (a file you picked). */
    fun exportM3u8(uri: String, tracks: List<Track>) {
        bg({
            val text = m3u8Text(tracks) { t ->
                val f = lib.items[t.id]?.file ?: t.file
                f?.let { a -> local.paths[a]?.let { p -> "/storage/emulated/0/$p" + (displayName(a) ?: "") } ?: if (a.startsWith("/")) a else null }
            }
            ctx.openOut(uri)!!.use { it.write(text.toByteArray()) }
        }) { _, err -> toast(if (err != null) "Couldn't save the playlist: ${err.message?.take(80)}" else "Playlist saved: ${tracks.size} songs") }
    }

    private fun displayName(uri: String): String? = ctx.displayName(uri)

    /** Make source (a picture's web address, or a content:// one you picked) the cover of tracks' album:
     *  into its downloaded FLACs (all but the playing one's), and shown for all of its songs. */
    /** Covers you picked for the song playing, waiting for its file to be free (written when another song starts). */
    private val coversWaiting = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private fun coverLater(file: String, jpeg: ByteArray) { coversWaiting[file] = jpeg }
    /** A song (t) is being loaded: covers picked while their song played go in now, once the file that was playing is free. */
    internal fun waitingCoversSoon(t: Track) {
        if (coversWaiting.isNotEmpty()) { val nowFile = lib.items[t.id]?.file ?: t.file
            scope.launch(Dispatchers.Main) { delay(1500); writeWaitingCovers(nowFile) } }
    }
    private fun writeWaitingCovers(except: String?) {
        val todo = coversWaiting.keys.filter { it != except }
        if (todo.isEmpty()) return
        bg({
            for (f in todo) {
                val jpeg = coversWaiting.remove(f) ?: continue
                val ok = CoverGuard.byHand {
                    if (lib.items.values.any { it.file == f && it.format == "flac" }) runCatching { Covers.write(ctx, f, jpeg); true }.getOrDefault(false)
                    else FileTags.writeCover(ctx, f, jpeg)
                }
                if (ok) TagMatch.touched(f) else logWarn("cover: couldn't put the picked cover into $f")
            }
        }) { _, _ -> }
    }

    fun setCover(tracks: List<Track>, source: String, wholeAlbum: Boolean = true, done: () -> Unit = {}) {
        val t = tracks.firstOrNull() ?: return
        val playing = current()?.id
        bg({
            val raw = if (!source.startsWith("http")) ctx.openIn(source)!!.use { it.readBytes() }
                else httpBytes(source)
            val jpeg = Covers.jpeg(raw)
            val keys = tracks.map { Covers.albumKey(it) }.toSet()   // every album of the songs: all of each one's songs
            val saved = File(File(ctx.filesDir, "covers").apply { mkdirs() }, "${randomHex(8)}.jpg").apply { writeBytes(jpeg) }
            val ids = tracks.map { x -> x.id }.toSet()
            val album = lib.items.values.filter { (wholeAlbum && Covers.albumKey(it.track()) in keys) || it.id in ids }
            var written = 0
            val failed = ArrayList<String>()
            for (e in album.distinctBy { it.file }) {
                if (e.id == playing) { coverLater(e.file, jpeg); written++; continue }   // its file is playing: right after it
                if (e.format == "flac" && Tags.writable(e.file)) {
                    try { CoverGuard.byHand { Covers.write(ctx, e.file, jpeg) }; written++ } catch (x: Exception) { failed += "${e.title}: ${x.message}" }
                } else if (FileTags.canWrite(e.file)) {   // a download in another format (MP3, M4A…): in its own way
                    if (CoverGuard.byHand { FileTags.writeCover(ctx, e.file, jpeg) }) written++ else failed += "${e.title}: couldn't put the cover in"
                }
            }
            // Your own files of the album too (MP3, M4A, WAV, FLAC…): the cover goes into them, so it travels with the
            // files to your other device (picked only in the app, it stayed on this one)
            val curFile = current()?.let { lib.items[it.id]?.file ?: it.file }
            for (x in runCatching { mySongs() }.getOrDefault(emptyList())) {
                val f = x.file?.takeIf { x.isFile && !lib.has(x.id) } ?: continue
                if (!((wholeAlbum && Covers.albumKey(x) in keys) || x.id in ids)) continue
                if (!FileTags.canWrite(f)) { failed += "${x.title}: the app can't change its file"; continue }
                if (f == curFile) { coverLater(f, jpeg); written++; continue }   // playing: written as soon as it stops
                if (CoverGuard.byHand { FileTags.writeCover(ctx, f, jpeg) }) { written++; TagMatch.touched(f) } else failed += "${x.title}: couldn't put the cover in"
            }
            for (e in album) {   // a new file each time: the old picture stays in the image cache under the old name
                val art = File(lib.artDir, "${safeName(e.id)}-${randomHex(4)}.jpg").apply { writeBytes(jpeg) }
                val old = e.art
                lib.update(e.id) { it.copy(art = art.path) }
                if (old.isNotEmpty() && lib.items.values.none { it.art == old }) File(old).delete()
            }
            for (x in tracks.distinctBy { Covers.albumKey(it) })   // your own songs' albums show it too
                if (x.album.isNotEmpty()) Pictures.chooseCover(x.artist, x.album, saved.path)
            for (x in tracks.filter { it.album.isEmpty() }) Pictures.chooseCover(x.artist, x.title, saved.path)
            written to failed
        }) { res, err ->
            if (err != null || res?.second?.isNotEmpty() == true) toast("Couldn't use that cover: ${(err?.message ?: res!!.second[0]).take(100)}")
            if (res != null) toast("New cover for “${t.album.ifEmpty { t.title }}”" + if (res.first > 0) " in ${res.first} file${if (res.first != 1) "s" else ""}" else "")
            savedVersion++; musicVersion++
            done()
        }
    }

    internal fun playbackOf(t: Track): Pair<Float, Float> = if (bitPerfect) 1f to 1f else speeds.get(t).let { s ->
        s.speed.toFloat() to (if (s.pitch) s.speed.toFloat() else 1f)
    }

    /** Play tracks at speed from now on (the playing one too, at once). */
    fun setSpeed(tracks: List<Track>, speed: Double, pitch: Boolean) {
        if (tracks.isEmpty()) return
        speeds.set(tracks, speed, pitch)
        val cur = current()
        if (cur != null && tracks.any { SongSpeeds.key(it) == SongSpeeds.key(cur) }) playbackOf(cur).let { (sp, pi) -> player?.setSpeed(sp, pi) }
        val s = speeds.get(tracks[0]).speed
        val what = if (tracks.size == 1) "“${tracks[0].title}”" else "${tracks.size} songs"
        toast(if (s == 1.0) "$what back to normal speed" else "$what at ${fmtSpeed(s)}")
        musicVersion++
    }

    /** The folder a song's file belongs in once its artist or album changed (Artists/<Artist>/<Album>/, EPs and singles in
     *  their own folders, as downloads are), or null to leave it where it is. Downloads always go there (under the download
     *  folder); a file of yours only when it sat where its old artist and album put it, in an Artists folder (so folders you
     *  arranged yourself, like unreleased songs, stay as they are). On the phone only the app's own downloads can move. */
    private fun releaseFolder(file: String, download: Boolean, oldArtist: String, oldAlbum: String, artist: String, album: String): String? {
        if (artist.isBlank()) return null
        val f = file.replace('\\', '/')
        if (f.startsWith("content:")) return if (download) downloadPlace(downloadFolder(), artist, album, null, null, "x").first else null
        if (!FileTags.canMove) return null
        val root = if (download) org.omarchy.music.ui.fullFolderPath(downloadFolder().trimEnd('/')).replace('\\', '/')
        else {
            val parts = f.split('/'); val i = parts.lastIndexOf(ARTISTS)
            if (i < 0) return null
            val r = parts.take(i + 1).joinToString("/")
            val was = downloadPlace(r, oldArtist, oldAlbum, null, null, "x").first
            if (!f.substringBeforeLast('/').plus("/").equals(was, ignoreCase = true)) return null
            r
        }
        if (!download && MusicTools.skip.any { inFolders(f, listOf(it)) }) return null
        return downloadPlace(root, artist, album, null, null, "x").first
    }

    /** Tag changes to the song that was playing, by its id: written once another song is on (or none). */
    private val heldTags = LinkedHashMap<String, Pair<Track, LinkedHashMap<String, String>>>()
    /** A song is being loaded: the song that was playing gets its tag changes, once its file is free. */
    internal fun heldTagsSoon() { if (heldTags.isNotEmpty()) scope.launch(Dispatchers.Main) { delay(1500); writeHeldTags() } }
    private fun writeHeldTags() {
        if (heldTags.isEmpty()) return
        val cur = current()?.id
        val ready = heldTags.filterKeys { it != cur }
        ready.keys.forEach { heldTags.remove(it) }
        for ((t, ch) in ready.values) editTags(listOf(t), ch)
    }

    /** Write changes {field: value} into the tags of tracks' downloaded FLACs, and show the new
     *  names wherever the songs are. The playing song is left alone: its file is being read. */
    fun editTags(tracks: List<Track>, changes: Map<String, String>, renameFiles: Boolean = false) {
        val playing = current()?.id
        val todo = tracks.filter { Tags.editableAny(it) != null && it.id != playing }
        // The playing song's file is being read: its changes wait, and are written the moment it ends
        tracks.firstOrNull { it.id == playing && Tags.editableAny(it) != null }?.let { t ->
            heldTags.getOrPut(t.id) { t to LinkedHashMap() }.second.putAll(changes)
            toast("“${t.title}” is playing: its new tags are saved as soon as it ends")
        }
        if (todo.isEmpty() || changes.isEmpty()) return
        bg({
            val renamed = HashMap<String, Triple<String, String, String>>()
            val failed = ArrayList<String>()
            val moves = ArrayList<Pair<String, String>>()
            for (t in todo) {
                val e = lib.items[t.id]
                val file = e?.file ?: Tags.editableAny(t) ?: continue   // a download, or (PC) a file of yours
                val c = try { Tags.write(ctx, file, changes) } catch (x: Exception) { failed += "${t.title}: ${x.message}"; continue }
                if (changes.keys.any { it.equals("genre", true) }) noteGenre(file, c["GENRE"]?.firstOrNull() ?: "")
                val title = c["TITLE"]?.firstOrNull() ?: e?.title ?: t.title
                val artist = c["ARTIST"]?.joinToString(", ")?.ifEmpty { null } ?: "Unknown artist"
                val album = c["ALBUM"]?.firstOrNull() ?: ""
                var dest: String? = if (renameFiles && FileTags.canMove && title != (e?.title ?: t.title))
                    renamedFile(file, e?.title ?: t.title, title) else null
                // A new artist or album: the file goes to that artist's and album's folder (made if it isn't there)
                if (changes.keys.any { it.lowercase() in setOf("artist", "album", "albumartist") }) {
                    val lead = (c["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: artist).split(",")[0].trim()
                    releaseFolder(file, e != null, (e?.artist ?: t.artist).split(",")[0].trim(), e?.album ?: t.album, lead, album)?.let { folder ->
                        dest = if (file.startsWith("content:")) folder else folder + (dest ?: file).replace('\\', '/').substringAfterLast('/')
                    }
                }
                dest?.let { d -> if (!d.replace('\\', '/').equals(file.replace('\\', '/'), ignoreCase = true)) moves += file.replace('\\', '/') to d.replace('\\', '/') }
                if (e == null) { renamed[t.id] = Triple(title, artist, album); continue }
                lib.items.values.filter { it.file == e.file }.forEach { same ->
                    lib.update(same.id) { it.copy(title = title, artist = artist, album = album, genre = c["GENRE"]?.firstOrNull() ?: "") }
                    renamed[same.id] = Triple(title, artist, album)
                }
            }
            lists.retitle(renamed.keys) { x -> renamed[x.id]!!.let { (a, b, c) -> x.copy(title = a, artist = b, album = c) } }
            if (moves.isNotEmpty()) {
                val (n, bad) = MusicTools.moveFiles(moves.distinct())
                if (bad.isNotEmpty()) failed += bad
                else if (n > 0) scope.launch(kotlinx.coroutines.Dispatchers.Main) { toast("$n file${if (n != 1) "s" else ""} moved to match ${if (n != 1) "their" else "its"} tags") }
            }
            renamed to failed
        }) { res, err ->
            val (renamed, failed) = res ?: (emptyMap<String, Triple<String, String, String>>() to listOf(err?.message ?: "?"))
            queue = queue.map { x -> renamed[x.id]?.let { (a, b, c) -> x.copy(title = a, artist = b, album = c) } ?: x }
            if (renamed.keys.any { k -> local.files.any { it.id == k } })
                local.files = local.files.map { x -> renamed[x.id]?.let { (a, b, c) -> x.copy(title = a, artist = b, album = c) } ?: x }
            if (failed.isNotEmpty()) toast("Couldn't write the tags: ${failed[0].take(100)}")
            val n = todo.size - failed.size
            if (n > 0) toast("Tags written to $n song${if (n != 1) "s" else ""}")
            savedVersion++; musicVersion++
        }
    }

    /** A song on the phone of another app's (yours) that Android can be asked to delete: its address, or null. */
    fun phoneFile(t: Track): String? =   // on the PC: a file of yours it may change (it goes to the Recycle Bin)
        t.file?.takeIf { t.isFile && !lib.has(t.id) &&
            ((it.startsWith("content://media/") && sdkAtLeast(30)) || (!it.startsWith("content:") && FileTags.canWrite(it))) }

    fun deletable(t: Track) = lib.has(t.id) || phoneFile(t) != null

    /** Delete the downloads among tracks (the app's own files, no asking Android); the phone's
     *  songs are deleted by Android once you say yes to it, then [phoneFilesDeleted]. */
    fun deleteDownloads(tracks: List<Track>) {
        val playing = current()?.id
        val ids = tracks.filter { lib.has(it.id) && it.id != playing }.map { it.id }
        if (tracks.any { it.id == playing && lib.has(it.id) }) toast("The playing song is left as it is: skip it first")
        if (ids.isEmpty()) return
        bg({ ids.forEach { lib.remove(it) } }) { _, err ->
            toast(if (err != null) "Couldn't delete them: ${err.message}" else "Deleted ${ids.size} download${if (ids.size != 1) "s" else ""}")
            savedVersion++; musicVersion++
        }
    }

    /** The phone's songs tracks were deleted: out of your music, the queue and your playlists. */
    fun phoneFilesDeleted(tracks: List<Track>) {
        FolderCleanup.afterSongs(tracks)   // their folders, if no music is left in them
        val ids = tracks.map { it.id }.toSet()
        local.files = local.files.filter { it.id !in ids }
        unqueueMany(queue.indices.filter { queue[it].id in ids })
        lists.all().filter { p -> p.tracks.any { it.id in ids } }.forEach { lists.remove(it.id, ids) }
        toast("Deleted ${ids.size} song${if (ids.size != 1) "s" else ""}")
        musicVersion++
    }

    fun removeDownload(id: String) {
        if (current()?.id == id) { toast("That song is playing; skip it first"); return }
        bg({ lib.remove(id) }) { _, _ -> savedVersion++; musicVersion++ }
    }

    @JvmName("chooseKeepPlayed")
    fun setKeepPlayed(on: Boolean) { keepPlayed = on; saveState() }

    // ---- services ----
    @JvmName("chooseSource")
    fun setSource(key: String) { source = key; saveState() }

    /** The source bar: a service (its library in the Library tab, its search) or "local": your own
     *  songs, which the search looks in, with the Library tab hidden until you pick a service. */
    fun browse(key: String) {
        when {
            key == "local" -> {
                if (browseLocal) return
                browseLocal = true
                if (searchIn != "local") { searchBeforeLocal = searchIn; searchIn = "local" }
            }
            key in SOURCES -> {
                if (!browseLocal && key == source) return
                if (browseLocal && searchIn == "local") searchIn = searchBeforeLocal
                browseLocal = false
                source = key
            }
            else -> return
        }
        saveState()
    }

    @JvmName("chooseSearchIn")
    fun setSearchIn(key: String) { searchIn = key; saveState() }

    fun setSyncSource(key: String, on: Boolean) {
        syncSources[key] = on
        saveState()
        if (on) syncLibrary()
    }

    fun sourceReady(key: String, src: Source?, err: Throwable?) {
        if (src != null) {
            sources[key] = src
            sourceErrors.remove(key)
            if (src.signedIn) bg({ src.likedIds() }) { ids, _ -> if (ids != null) { likes[key] = ids; liked = likes.values.flatten().toSet() } }
            else if (likes.remove(key) != null) liked = likes.values.flatten().toSet()
        } else if (key !in sources) sourceErrors[key] = err?.message ?: "unavailable"
        accountsVersion++
        if (key == "tidal") playerReady()
        if (key == "spotify" && src?.signedIn == true) bg({ SpotifyHistory.sync() })   // its last plays, for your Wrapped
        syncLibrary()
    }

    fun tidalLoggedIn(redirect: String) {
        bg({ tidal.finishLogin(redirect) }) { _, err ->
            if (err != null) { toast("Tidal didn't accept that: ${err.message?.take(80)}"); return@bg }
            toast("Tidal connected: everything plays in FLAC")
            sourceReady("tidal", tidal, null)
        }
    }

    fun tidalDisconnect() {
        tidal.disconnect()
        sources.remove("tidal")
        accountsVersion++
    }

    /** Download every song in your playlists and favourites, on each service you sync, in FLAC. */
    fun syncLibrary() {
        if (!tidal.connected) return
        val srcs = sources.filter { (k, s) -> s.signedIn && syncSources[k] == true && k != SoundCloud.KEY }.values.toList()   // SoundCloud's are never saved
        if (srcs.isEmpty()) return
        if (syncing) { syncAgain = true; return }
        syncing = true
        syncAgain = false
        bg({
            val tracks = ArrayList<Track>()
            val seen = HashSet<String>()
            for (s in srcs) {
                val found = runCatching { s.libraryTracks() }.getOrNull() ?: continue
                for (t in found) if (seen.add(t.id)) tracks += t
                scope.launch { syncTracks = tracks.toList() }
            }
            dl.sync(tracks.filter { autoSaveOk(it) }) { scope.launch { savedVersion++ } }   // songs you have: not unless Copies says so
        }) { _, err ->
            syncing = false
            if (err != null) toast("FLAC sync stopped: ${err.message?.take(80)}")
            savedVersion++
            musicVersion++
            if (syncAgain) syncLibrary()
        }
    }

    // ---- what counts as played ----

    // ---- the queue ----
    fun current(): Track? = PlaybackCore.current()
    fun isRadio() = PlaybackCore.isRadio()

    // What the screens show as playing: the phone's, or the desktop's while it's the remote.
    fun shownQueue() = if (Remote.active) Remote.status.queue else queue
    fun shownIndex() = if (Remote.active) Remote.status.index else index
    fun shownTrack() = if (Remote.active) Remote.status.track else current()
    fun shownRadio() = if (Remote.active) Remote.status.radio else isRadio()
    fun livePosition(): Double = if (Remote.active) Remote.livePosition() else player?.let { it.positionMs / 1000.0 } ?: position

    /** Play tracks[i], then the rest of tracks; the songs you queued come first. */
    fun playFrom(tracks: List<Track>, i: Int, radio: Boolean) = PlaybackCore.playFrom(tracks, i, radio)

    /** Go back to one of your other queues, where you left it. */
    fun switchQueue(id: String) = PlaybackCore.switchQueue(id)

    /** Move one of your other queues up (-1) or down (+1). */
    fun moveQueue(id: String, step: Int) = PlaybackCore.moveQueue(id, step)

    fun renameQueue(id: String, name: String) = PlaybackCore.renameQueue(id, name)

    @JvmName("chooseLyricsToFiles")
    fun setLyricsToFiles(on: Boolean) { lyricsToFiles = on; saveState() }

    /** Whether the app may write into your own files too (Android 11 and up: All files access). */
    fun canWriteOwnFiles() = canWriteOwnFiles(ctx)

    /** Write the lyrics found for t into its file's tags (if it has none). Your own files need All files access. */
    fun lyricsIntoFile(t: Track, force: Boolean = false) {   // force: asked for (the Tagger), whatever the setting
        if ((!lyricsToFiles && !force) || Remote.active) return
        val uri = (lib.items[t.id]?.file ?: t.file)?.takeIf { Tags.writable(it) } ?: return
        val own = lib.items[t.id] == null
        if (own && !canWriteOwnFiles()) return
        bg({
            val words = Lyrics.get(ctx, t)?.let { LyricsTags.words(it) } ?: return@bg false
            LyricsTags.write(ctx, uri, words)
        }) { wrote, err ->
            if (err is SecurityException && !lyricsWarned) {
                lyricsWarned = true
                toast("Couldn't put the lyrics into “${t.title}”: allow All files access in Settings › Lyrics into files")
            }
            if (wrote == true) savedVersion++
        }
    }

    fun deleteQueue(id: String) = PlaybackCore.deleteQueue(id)
    fun removeOtherQueues() = PlaybackCore.removeOtherQueues()
    @JvmName("chooseMultiQueue")
    fun setMultiQueue(on: Boolean) = PlaybackCore.chooseMultiQueue(on)

    /** "next" plays the songs right after this one, "queue" after the songs you queued before. */
    fun enqueue(tracks: List<Track>, how: String = "queue") = PlaybackCore.enqueue(tracks, how)

    fun unqueueMany(indices: Collection<Int>) = PlaybackCore.unqueueMany(indices)

    internal fun radioTracks(t: Track): List<Track> {
        var last: Throwable? = null
        for (key in listOf(t.source, source, "ytm", "tidal").distinct()) {
            val src = sources[key] ?: continue
            if (key in setOf("apple", "spotify") || (key == "tidal" && !src.signedIn)) continue
            val found = try { src.radio(t) } catch (e: Exception) { last = e; continue }
            if (found.isNotEmpty()) return found
        }
        last?.let { throw it }
        return emptyList()
    }

    /** ∞: a couple of songs before the end, songs like the last one, so there's no gap. */
    private fun autoplaySoon() = PlaybackCore.autoplaySoon()

    @JvmName("chooseAutoplay")
    fun setAutoplay(on: Boolean) {
        autoplay = on; saveState()
        if (dj.on) toast("Autoplay ${if (on) "on" else "off"}: it isn't used while the DJ plays (the DJ makes its own sets), only once you stop the DJ")
        else if (on) autoplaySoon()
    }

    /** The queue's Clear: the songs you queued by hand go (what plays on from the list stays). */
    fun clearQueued() = PlaybackCore.clearQueued()

    /** The song at i, dragged to j (both after the one playing). */
    fun moveInQueue(i: Int, j: Int) = PlaybackCore.moveInQueue(i, j)

    fun playIndex(i: Int) = PlaybackCore.playIndex(i)

    /** Next from your headphones, the car, the lock screen or the PC's media keys. With the DJ on and the music paused
     *  (not while the DJ is talking), it switches it up: a new kind of set, which starts playing at once. Pause with your
     *  headphones, then double tap. Playing, Next skips one song as always. (Two double taps in a row were meant to do
     *  it before, but earbuds wait after each tap to see if more are coming, so the two came too far apart.) */
    fun sessionNext() {
        if (dj.on && !Remote.active && paused && !djTalking && current() != null) {
            djSwitch()
            return
        }
        next()
    }


    fun next() = PlaybackCore.next()

    /** The next of your other queues with songs still to play (the last one used first), or null. */
    fun nextQueue(): SavedQueue? = PlaybackCore.nextQueue()

    fun previous() = PlaybackCore.previous()

    /** A song ran out and the next one is on its way: to the notification (and Android) it's still
     *  playing, so the app stays a foreground music player between songs. Were it "ended" for that
     *  moment, Android would take the app out of the foreground, and with the screen off it can't come
     *  back: a minute later Android closed it (the app "crashing" every couple of songs). */
    var advancing: Boolean get() = PlaybackCore.advancing; private set(v) { PlaybackCore.advancing = v }

    /** Done moving on (playing the next song, or stopped): the notification gets the player's real state. */
    private fun doneAdvancing() = PlaybackCore.doneAdvancing()




    /** Smart shuffle (Settings › Playback): each song's weight, more for the ones you love (play a lot) that you haven't
     *  heard in a while; null (plain chance) with it off. */
    fun shuffleWeight(): ((Track) -> Double)? {
        if (!Prefs.on(Prefs.SMART_SHUFFLE)) return null
        val plays = HashMap<Pair<String, String>, Int>()
        for (st in Stats.songs.take(20000)) plays[Stats.statsKey(st.title, st.artist)] = st.plays
        val last = HashMap<Pair<String, String>, Double>()
        for (e in this.plays.items.values) { val k = Stats.statsKey(e.track.title, e.track.artist); last[k] = maxOf(last[k] ?: 0.0, e.last) }
        val now = System.currentTimeMillis() / 1000.0
        return { t -> Stats.statsKey(t.title, t.artist).let { k -> smartWeight(plays[k] ?: 0, last[k] ?: 0.0, now) } }
    }

    /** Settings › Sound's switches and numbers into the EQ processor. */
    fun applySound() {
        eqProcessor.limiter = Prefs.on(Prefs.CLIP_GUARD)
        eqProcessor.dither = Prefs.on(Prefs.DITHER)
        eqProcessor.smooth = Prefs.on(Prefs.SMOOTH_PEAKS)
        eqProcessor.balance =Prefs.num(Prefs.BALANCE, 0f).toDouble()
        eqProcessor.crossfeedK = Prefs.num(Prefs.CROSSFEED_K, 0.3f).toDouble()
        eqProcessor.tilt = Prefs.num(Prefs.TILT, 0f).toDouble()
        eqProcessor.detail = Prefs.on(Prefs.DETAIL)
        Loudness.apply()
    }

    fun setSoundNum(key: String, v: Float) { Prefs.setNum(key, v); applySound() }

    /** One of 2.0's switches (Prefs), with what it takes to take effect. */
    fun setOpt(key: String, on: Boolean) {
        Prefs.set(key, on)
        if (key == Prefs.CROSSFEED) eqProcessor.crossfeed = on
        if (key == Prefs.AUTO_MONO) AutoMono.check()
        if (key == Prefs.CLIP_GUARD || key == Prefs.DITHER || key == Prefs.LOUDNESS || key == Prefs.DETAIL || key == Prefs.SMOOTH_PEAKS) applySound()
        if (key == Prefs.MONO) { eqProcessor.mono = on || AutoMono.active; if (on && bitPerfect) toast("Mono isn't heard in bit-perfect: turn bit-perfect off for it") }
    }

    /** Songs of one album, one after the other: Smart transitions leaves them to flow into each other. */
    fun sameAlbum(a: Track, b: Track) = a.album.isNotBlank() && norm(albumEdition(a.album).first) == norm(albumEdition(b.album).first) &&
        artistKey(a.artist) == artistKey(b.artist)

    /** The player says its song ended. Ignored while it's already moving on to another song (after a crossfade the
     *  ending one plays on silently in the player while the next loads, and its end skipped the next song unheard). */
    fun engineEnded() = PlaybackCore.engineEnded()

    fun trackEnded() = PlaybackCore.trackEnded()

    fun cycleRepeat() = PlaybackCore.cycleRepeat()

    /** Shuffle what's coming up; the songs you queued stay first, in your order. */
    fun shuffle() = PlaybackCore.shuffle()

    fun togglePause() {
        if (Remote.active) { Remote.cmd(if (Remote.status.paused) "play" else "pause"); return }
        val p = player ?: return
        if (current() == null) return
        // Pause while the DJ talks: she stops, and the set's song waits paused at its start
        if (djTalking) { djHush(); p.pause(); return }
        if (p.isPlaying) p.pause() else sessionPlay()
    }

    fun seek(seconds: Double) {
        if (Remote.active) { Remote.cmd("seek", "position" to seconds); return }
        player?.seekTo((seconds * 1000).toLong())
        position = seconds
    }

    fun toggleLike() {
        if (Remote.active) { Remote.cmd("like"); return }
        val t = current() ?: return
        val src = sources[t.source] ?: return
        if (!src.signedIn) return
        val like = t.id !in liked
        liked = if (like) liked + t.id else liked - t.id
        bg({ src.rate(t, like); if (LastFm.connected) runCatching { LastFm.love(t, like) } }) { _, err ->
            if (err != null) {
                liked = if (like) liked - t.id else liked + t.id
                toast("Couldn't ${if (like) "like" else "unlike"} it: ${err.message?.take(80)}")
            }
        }
    }

    private fun afterQueueChange() = PlaybackCore.afterQueueChange()

    fun loadCurrent(autoplay: Boolean = true) = PlaybackCore.loadCurrent(autoplay)

    /** The cover the lock screen, the notification, Android Auto and Windows' media controls show: the same one the app
     *  shows (the download's, one you picked in the Tagger or Pick a cover, the service's), else the file's own (FLAC, MP3,
     *  M4A, WAV) or its folder's cover.jpg / folder.jpg, saved as a picture in the cache once per song. */
    internal fun nowArt(t: Track): String? {
        lib.items[t.id]?.art?.takeIf { it.isNotEmpty() && java.io.File(it).exists() }?.let { return it }
        val name = t.album.ifEmpty { t.title }
        // A cover from the web: its copy on disk once it's been loaded (kept the first time), not downloaded again.
        fun web(u: String): String = if (!u.startsWith("http")) u else CoverCache.local(u)?.path ?: u.also { CoverCache.keepLater(it) }
        Pictures.cover(t.artist, name).takeIf { it.isNotEmpty() }?.let { return web(it) }
        if (t.thumb.isNotEmpty()) return web(sizedThumb(t.thumb, 544))
        if (t.file == null) SongCovers.shown(t.title, t.artist).takeIf { it.isNotEmpty() }?.let { return web(sizedThumb(it, 544)) }
        val old = Pictures.oldCover(t.artist, name).ifEmpty { null }
        return t.file?.let { fileArt(it) } ?: old?.let { web(it) }
    }
    private val fileArts = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun fileArt(file: String): String? {
        fileArts[file]?.let { return it.ifEmpty { null } }
        val bytes = runCatching { embeddedPicture(ctx, file) }.getOrNull() ?: FolderCover.bytes(file)
        val out = bytes?.let { b -> runCatching {
            val dir = java.io.File(ctx.cacheDir, "now-art").apply { mkdirs() }
            // Named by the whole path (SHA-1), not its hashCode: two songs whose paths share one would share a cover.
            val name = java.security.MessageDigest.getInstance("SHA-1").digest(file.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
            java.io.File(dir, "$name.jpg").apply { writeBytes(b) }.path
        }.getOrNull() }
        fileArts[file] = out ?: ""
        return out
    }

    /** Tidal's stream stopped short: look it up again and carry on from the same place, once per song. */
    fun streamBroke() = PlaybackCore.streamBroke()

    // ---- the equalizer ----
    fun setEq(f: (EqSettings) -> EqSettings) {
        var n = f(eq)
        // The output the settings are for keeps them as they are now, always (not only when the sound moves away), with
        // when they last changed: so a sync carries the latest, and never brings older ones back over yours
        if (n.perOutput && n.output.isNotEmpty()) {
            val old = n.outputs[n.output]
            val mine = n.toOutput(old?.label?.ifEmpty { null } ?: outLabel(n.output))
            if (!mine.sameAs(old)) n = n.copy(outputs = n.outputs + (n.output to mine.copy(at = System.currentTimeMillis())))
        }
        eq = n
        applyEq()
        saveState()
    }

    /** The playing song's own EQ on this output (Custom EQ…), if it has one: then it's what's heard. */
    fun songEqNow(t: Track? = current()): OutputEq? = t?.let { if (::songEqs.isInitialized) songEqs.get(it, outputNow.key) else null }
    var songEqActive by mutableStateOf(false)   // the playing song's own EQ is in effect (Now playing's C EQ, red)
        private set

    /** What the EQ plays: this output's, or the playing song's own for this output (its correction, Sound and Boost; the
     *  bass and treble, crossfeed, dither and the rest stay as they are). */
    internal fun applyEq() {
        val s = songEqNow()
        songEqActive = s != null
        val e = if (s == null) eq else eq.copy(on = true, headphones = s.headphones, preset = s.preset, bands = s.bands,
            custom = s.custom, count = s.count, boost = s.boost)
        // Dual volume off: a song with its own volume (Volume…) plays at that volume alone, without the Boost on top
        eqProcessor.update(if (!dualVolume() && eqProcessor.songGain != 1.0) e.copy(boost = 0.0) else e)
    }

    /** Dual volume (Equalizer): on, the Boost and a song's own volume go together; off, a song with its own volume
     *  plays at just that one (the songs without one keep the Boost). */
    fun dualVolume() = Prefs.on(DUAL_VOLUME, true)
    fun setDualVolume(on: Boolean) { Prefs.set(DUAL_VOLUME, on); applyEq(); musicVersion++ }
    private const val DUAL_VOLUME = "eq_dual_volume"

    /** Custom EQ…: e (null: take it away) for tracks, on the output playing now. */
    fun setSongEq(tracks: List<Track>, e: OutputEq?) {
        if (tracks.isEmpty()) return
        songEqs.set(tracks, outputNow.key, e?.copy(label = outputNow.label))
        applyEq()
        val what = if (tracks.size == 1) "“${tracks[0].title}”" else "${tracks.size} songs"
        toast(if (e == null) "$what back to the usual EQ on ${outputNow.label}" else "$what with its own EQ on ${outputNow.label}")
        musicVersion++
    }

    fun setEqOn(on: Boolean) = setEq { it.copy(on = on) }
    fun setEqHeadphones(key: String, remember: Boolean = true) = setEq {
        val out = output
        it.copy(headphones = key, on = true, devices = if (remember && out.isNotEmpty()) it.devices + (out to key) else it.devices)
    }

    /** Keep an AutoEq profile among your headphones and use it. */
    /** An EQ file of yours (AutoEq, Squiglink, Wavelet, Equalizer APO), picked: among your headphones, and used. */
    fun importEqFile(uri: String) {
        bg({
            val text = ctx.openIn(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            val name = (ctx.displayName(uri) ?: "My EQ")
                .substringBeforeLast('.').replace(Regex("\\s*(ParametricEQ|GraphicEQ)$"), "").trim().ifEmpty { "My EQ" }
            name to AutoEq.parseFile(text)
        }) { r, err ->
            if (r != null) addAutoEq(AutoEq.Entry(r.first, "imported"), r.second)
            else toast("Couldn't import it: ${err?.message?.take(80)}")
        }
    }

    /** ReplayGain's gain for a song (dB from its tags) at the levelling target: never louder than full scale unless
     *  Clip guard is on to limit the peaks. */
    /** ReplayGain's gain for a song tagged db (and its peak, when tagged). Making a quiet song louder stops where its
     *  loudest moment would reach full scale (with Clip guard, a little past it: the limiter catches the odd peak), and at
     *  +6 dB at most when the file doesn't say its peak. Before, a quiet song could get +12 dB and the limiter then
     *  squashed it flat: loud, distorted, the bass pumping. */
    fun rgFactor(db: Double, peak: Double? = null): Double {
        val g = Math.pow(10.0, (db + Prefs.num(Prefs.RG_TARGET, -18f) + 18) / 20)
        if (g <= 1.0) return g
        if (!Prefs.on(Prefs.CLIP_GUARD)) return 1.0
        val most = peak?.let { 1.12 / it } ?: 2.0   // up to 1 dB over the peak, or +6 dB untagged
        return g.coerceAtMost(most.coerceIn(1.0, 2.0))
    }

    // ---- Sound presets of yours: saved, imported, exported; corrections exported; every source collected ----

    /** Save as preset…: the bands as they are now, as a Sound preset of yours named name (used straight away). */
    fun savePreset(name: String, bands: List<Double> = eq.bands, count: Int = eq.count) {
        val n = name.trim().ifEmpty { return }
        val key = norm(n).replace(" ", "-").ifEmpty { randomHex(4) }
        val ten = Eq.resample(bands, Eq.bandsFor(count), 10)
        setEq { it.copy(myPresets = it.myPresets + (key to MyPreset(n, ten))) }
        setEqPreset("my:$key")
        toast("“$n” saved among your Sound presets")
    }

    fun deletePreset(key: String) {
        val k = key.removePrefix("my:")
        val name = eq.myPresets[k]?.name ?: return
        setEq { it.copy(myPresets = it.myPresets - k, preset = if (it.preset == key) "custom" else it.preset) }
        toast("“$name” deleted")
    }

    /** Import a Sound preset: a GraphicEQ (Wavelet, Equalizer APO, AutoEq) or a parametric file, read at the ten bands. */
    fun importPresetFile(uri: String) {
        bg({
            val text = ctx.openIn(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            val name = (ctx.displayName(uri) ?: "My preset").substringBeforeLast('.')
                .replace(Regex("\\s*(ParametricEQ|GraphicEQ)$"), "").trim().ifEmpty { "My preset" }
            name to Eq.curveToBands(AutoEq.parseFile(text))
        }) { r, err ->
            if (r != null) savePreset(r.first, r.second, 10) else toast("Couldn't import it: ${err?.message?.take(80)}")
        }
    }

    /** Export the Sound as it is now (a GraphicEQ file other EQ apps read, and this one imports). */
    fun exportPreset(uri: String) {
        val text = Eq.graphicEqText(Eq.bandsFor(eq.count), eq.bands)
        bg({ ctx.openOut(uri)!!.use { it.write(text.toByteArray()) } }) { _, err ->
            toast(if (err != null) "Couldn't export it: ${err.message?.take(80)}" else "Sound exported")
        }
    }

    /** Export the headphones correction in use (a ParametricEQ file: AutoEq's and Equalizer APO's format). */
    fun exportCorrection(uri: String) {
        val f = Eq.headphones(eq)[eq.headphones]?.second.orEmpty()
        if (f.isEmpty()) { toast("No headphones correction is in use to export"); return }
        val text = Eq.parametricText(f)
        bg({ ctx.openOut(uri)!!.use { it.write(text.toByteArray()) } }) { _, err ->
            toast(if (err != null) "Couldn't export it: ${err.message?.take(80)}" else "Correction exported")
        }
    }

    /** Collect: every source AutoEq has for these headphones (oratory1990, Rtings, crinacle, Super Review…), each added
     *  among your corrections so you can compare them; the closest match used unless you've picked one for them. */
    fun collectCorrections(dev: String, quiet: Boolean = false, done: (Int) -> Unit = {}) {
        val api = apiKeys["autoeq_api"].orEmpty()
        bg({
            val all = if (api.isNotEmpty()) AutoEq.apiSearch(api, dev) else AutoEq.index(ctx)
            val best = AutoEq.matchDevice(listOf(dev), all) ?: (if (api.isEmpty()) AutoEq.search(all, dev, 1).firstOrNull() else all.firstOrNull())
                ?: return@bg emptyList<Pair<AutoEq.Entry, List<Filter>>>()
            val same = all.filter { norm(it.name) == norm(best.name) }.ifEmpty { listOf(best) }
            (listOf(best) + same.filter { it != best }).mapNotNull { e -> runCatching { e to AutoEq.profile(e, api) }.getOrNull() }
        }) { got, err ->
            if (got.isNullOrEmpty()) {
                if (!quiet) toast(if (err != null) "AutoEq couldn't be reached: ${err.message?.take(80)}" else "AutoEq has nothing for “$dev”")
                done(0); return@bg
            }
            val keys = got.map { (e, _) -> "ae:" + norm("${e.name} ${e.source}").replace(" ", "-") }
            setEq { s -> s.copy(profiles = s.profiles + got.zip(keys).map { (p, k) ->
                k to AutoEq.profileOf("${p.first.name} · ${p.first.source.ifEmpty { "AutoEq" }}", p.second) }) }
            // Kept among your corrections, not used: you pick one (under Headphones) if you want it
            toast("${got[0].first.name}: ${got.size} correction${if (got.size != 1) "s" else ""} collected (" +
                got.joinToString(", ") { it.first.source.ifEmpty { "AutoEq" } } + "): pick one under Headphones to use it")
            done(got.size)
        }
    }

    /** A new output's own starting Sound preset, named after it (for every kind of music; tune it as you like). */
    private fun addDevicePreset(o: Outputs.Out) {
        if (o.kind == "speaker" && o.key == "speaker" || o.key == CAR) return   // these have built-in ones
        val key = norm(o.label).replace(" ", "-")
        if (key.isEmpty() || eq.myPresets.containsKey(key) || Eq.PRESETS.values.any { norm(it.first) == norm(o.label) }) return
        setEq { it.copy(myPresets = it.myPresets + (key to MyPreset(o.label, Eq.deviceCurve(o.kind)))) }
    }

    fun addAutoEq(e: AutoEq.Entry, filters: List<Filter>) {
        val key = "ae:" + norm("${e.name} ${e.source}").replace(" ", "-")
        setEq { it.copy(profiles = it.profiles + (key to AutoEq.profileOf("${e.name} · ${e.source.ifEmpty { "AutoEq" }}", filters))) }
        setEqHeadphones(key)
        toast("${e.name}: corrected with AutoEq (${e.source.ifEmpty { "AutoEq" }})")
    }

    var output by mutableStateOf("")   // the headphones the sound goes to now ("" for the phone's own speaker)
        private set

    /** The sound goes somewhere else: that output's own EQ, just as you left it (its headphones correction only if you
     *  picked one for it, its Boost…). A correction is never picked for you: the Equalizer says when AutoEq has one. */
    fun outputChanged() {
        switchEqOutput()
        output = AutoEq.output(ctx)
    }
    fun setEqPreset(key: String) = setEq { it.copy(preset = key, bands = Eq.presetOf(it, key, it.count) ?: it.custom.takeIf { c -> c.size == it.count }
        ?: List(it.count) { 0.0 }, on = true) }
    fun setEqBand(i: Int, gain: Double) = setEq {
        if (i !in it.bands.indices) return@setEq it
        val b = it.bands.toMutableList().also { l -> l[i] = Eq.clamp(gain) }
        it.copy(bands = b, custom = b, preset = "custom", on = true)
    }
    /** 3, 5, 10, 15 or 31 bands: the curve you had, read onto the new ones. */
    fun setEqCount(n: Int) = setEq {
        if (n == it.count || n !in Eq.COUNTS) return@setEq it
        val from = Eq.bandsFor(it.count)
        it.copy(count = n, bands = Eq.resample(it.bands, from, n), custom = Eq.resample(it.custom.takeIf { c -> c.size == it.count } ?: it.bands, from, n))
    }
    /** A separate EQ for each output, or one for all. */
    fun setEqPerOutput(on: Boolean) = setEq { it.copy(perOutput = on) }
    /** Edit (and, if it's the one playing, hear) another output's EQ. */
    fun editEqOutput(key: String) {
        if (key == eq.output) return
        val isNew = eq.outputs[key] == null
        setEq { e ->
            val k = e.output.ifEmpty { "speaker" }
            val saved = e.outputs + (k to e.toOutput(e.outputs[e.output]?.label ?: outLabel(e.output)).copy(at = e.outputs[k]?.at ?: 0))
            e.copy(outputs = saved).withOutput(key, saved[key])
        }
        if (isNew && key == CAR) setEqPreset("colorado")   // the truck, set up before it's been driven with: its tuning
    }
    /** The Chevy (Android Auto, wired or wireless): always in the Equalizer's list on the phone, to set up any time. */
    const val CAR = "car:Chevrolet"
    fun forgetEqOutput(key: String) = setEq { it.copy(outputs = it.outputs - key) }
    fun outLabel(key: String) = when (key) { "speaker", "" -> "Built-in speaker"; "wired" -> "Headphones (jack)"
        "headset" -> "Headset (jack, with mic)"; CAR -> "Chevrolet Colorado · Android Auto"; else -> key.substringAfter(':') }
    var outputNow by mutableStateOf(Outputs.Out("speaker", "Built-in speaker", "speaker"))
        private set

    /** The sound went somewhere else: that output's own EQ comes in (a separate EQ for each output). */
    private fun switchEqOutput() {
        val o = Outputs.now(ctx)
        outputNow = o
        eqProcessor.bypass = bitPerfectNow()
        current()?.let { eqProcessor.songGain = gainOf(it) }   // a song's volume change for this output, if it has one
        applyEq()
        if (!eq.perOutput || o.key == eq.output) {
            if (eq.output.isEmpty()) setEq { it.copy(output = o.key, outputs = it.outputs + (o.key to it.toOutput(o.label))) }
            return
        }
        val was = eq.output
        val isNew = eq.outputs[o.key] == null
        setEq { e ->
            val saved = (if (was.isNotEmpty()) e.outputs + (was to e.toOutput(e.outputs[was]?.label ?: outLabel(was)).copy(at = e.outputs[was]?.at ?: 0)) else e.outputs)
            val mine = saved[o.key] ?: OutputEq(o.label, count = e.count, bands = List(e.count) { 0.0 }, custom = List(e.count) { 0.0 })
            e.copy(outputs = saved + (o.key to mine.copy(label = o.label))).withOutput(o.key, mine)
        }
        if (isNew && o.key == CAR) setEqPreset("colorado")   // the Chevy starts on the Colorado's tuning
        if (isNew) addDevicePreset(o)   // its own Sound preset to start from
        if (was.isNotEmpty()) toast("${o.label}: its own equalizer" + if (eq.on) "" else " (off)")
    }
    /** Boost: dB louder than normal (0-12) on everything, kept for this output. */
    fun setEqBoost(db: Double) = setEq { it.copy(boost = (kotlin.math.round(db.coerceIn(0.0, Eq.BOOST_MAX) * 2) / 2)) }

    /** Bit-perfect, but the equalizer's Bass and Treble heard anyway (Equalizer): the rest stays untouched. */
    var bpTones by mutableStateOf(false)
    fun chooseBpTones(on: Boolean) {
        bpTones = on; Prefs.set(Prefs.BP_TONES, on)
        eqProcessor.update(eq)   // the PC's filters again
        toast(if (on) "Bass and Treble on with bit-perfect (the rest of the sound untouched)" else "Bit-perfect: every sample untouched again")
    }
    fun bpTonesNow() = bitPerfect && bpTones && eq.on && (eq.bass != 0.0 || eq.treble != 0.0)

    fun setEqTone(name: String, gain: Double) = setEq {
        if (name == "bass") it.copy(bass = Eq.clamp(gain), on = true) else it.copy(treble = Eq.clamp(gain), on = true)
    }

    @JvmName("chooseArtColors")
    fun setArtColors(on: Boolean) { artColors = on; saveState() }
    @JvmName("chooseLyricsMode")
    fun setLyricsMode(mode: String) { lyricsMode = mode; saveState() }
    @JvmName("chooseMusicSort")
    fun setMusicSort(sort: String) { musicSort = sort; saveState() }
    fun pickPlaylistSort(sort: String) { playlistSort = sort; saveState() }

    /** Each tab's sort (its Sort menu), kept: tab -> a key of SORT_NAMES. */
    fun sortOf(tab: String, default: String, allowed: List<String>) = tabSorts[tab]?.takeIf { it in allowed } ?: default
    fun setSort(tab: String, sort: String) { tabSorts[tab] = sort; saveState() }

    /** (blocking) What sorting by plays needs: how often you played each song, and when last. */
    fun sortData(sort: String): SortData {
        if (!sortNeedsPlays(sort)) return SortData.NONE
        val counts = runCatching { playCounts() }.getOrDefault(emptyMap())
        val last = if (sort.startsWith("played")) runCatching { Dj.lastHeard() }.getOrDefault(emptyMap()) else emptyMap()
        return SortData(plays = { counts[songKey(it)] ?: it.plays }, last = { last[Stats.statsKey(it.title, it.artist)] ?: 0L })
    }

    /** song key -> how often you played it: the stats (Last.fm and Spotify too), else the plays here. */
    fun playCounts(): Map<Pair<String, String>, Int> {
        val out = HashMap<Pair<String, String>, Int>()
        plays.items.values.forEach { out[songKey(it.track)] = it.plays }
        Stats.songs.forEach { s -> out.merge(songKey(Track("", title = s.title, artist = s.artist)), s.plays) { a, b -> maxOf(a, b) } }
        return out
    }

    /** A playlist's songs as the sort chips say. */
    fun sortedPlaylist(tracks: List<Track>) = sortSongs(tracks, playlistSort, if (playlistSort == "plays") playCounts() else emptyMap())
}

/** Something pinned: an artist, an album, a playlist (yours or a service's), a genre, a folder of songs,
 *  or a folder of playlists. id: what finds it again (an artist's is their name). */
@Serializable
data class Pin(val kind: String, val id: String, val title: String, val sub: String = "", val thumb: String = "", val source: String = "") {
    val key get() = if (kind == "artist") "artist:" + norm(id) else "$kind:$source:$id"
}

/** The layouts a list can have, in the order the button goes through them. */
// The layout button goes round: a list, small covers, the covers as they were, then bigger and biggest (two, then one, across a phone).
val LAYOUTS = listOf("list", "compact", "grid", "large", "huge")
/** The pages that start as a grid of covers. */
val GRID_FIRST = setOf("albums", "artists", "artist-page")
