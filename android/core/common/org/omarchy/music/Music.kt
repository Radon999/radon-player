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
            if (v != null) pendingLoad?.let { pendingLoad = null; it() }
        }

    var castHost = ""   // the cast device's address (the phone's files are served to it)

    private fun castMime(t: Track, uri: String): String {
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
    private var pendingLoad: (() -> Unit)? = null

    // ---- what the screens show ----
    var queue by mutableStateOf(listOf<Track>())
    // Multiple queues: playing from another list (a folder, an album, an artist, a playlist…) starts a queue
    // of its own; the one you were in is kept, where you were in it, to go back to.
    var queues by mutableStateOf(listOf<SavedQueue>())   // the other queues, the last one used first
    var queueName by mutableStateOf("")
    var queueId = ""
    var multiQueue by mutableStateOf(true)
    var listName = ""   // where songs are picked from now (the screen's name): a new queue is called that
    var resumeAt = 0.0
    // Lyrics into the files: what's found for a song goes into its own tags once it's done playing.
    var lyricsToFiles by mutableStateOf(true)
    private var lastLoaded: Track? = null
    private var lyricsWarned = false
    var index by mutableStateOf(-1)
    var paused by mutableStateOf(true)
    var position by mutableStateOf(0.0)
    var nowFormat by mutableStateOf("")     // what the player says it's playing: "FLAC 24-bit/96 kHz"
    var nowFormatOf = ""                    // …for this song
    var micAsked = false                    // the bars' permission was asked for once (Now playing)
    var duration by mutableStateOf(0.0)
    var repeat by mutableStateOf("off")
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
    var shuffled = false   // the queue was shuffled: repeat-all shuffles it again
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
    fun chooseTopMineOnly(on: Boolean) { topMineOnly = on; mostPlayedFor = null; musicVersion++; saveState() }
    var skipPeriod by mutableStateOf("all")    // …and over which time: week, month, year, all
    var bitPerfect by mutableStateOf(false)    // the DAC gets each song as it is: no EQ, no song volume, no speed
    var replayGain by mutableStateOf("off")    // off, track or album: the files' ReplayGain tags (not in bit-perfect)
    var dacNote by mutableStateOf("")          // what bit-perfect is doing now, in words

    /** Bit-perfect is in force: on the PC whenever it's on; on the phone only while a USB DAC is the output (on the
     *  speaker, Bluetooth or in the car the EQ and the rest go on working). */
    fun bitPerfectNow() = bitPerfect && (platformName != "android" || outputNow.kind == "usb")

    fun chooseBitPerfect(on: Boolean) {
        bitPerfect = on
        eqProcessor.bypass = bitPerfectNow()
        if (on) eqProcessor.rgGain = 1.0
        current()?.let { t -> playbackOf(t).let { (sp, pi) -> player?.setSpeed(sp, pi) } }
        val rate = player?.audioRate ?: 0; val bits = player?.audioBits ?: 0
        bg({ dacNote = AudioQuality.applyBitPerfect(ctx, on, rate, bits) })
        saveState()
    }

    fun chooseReplayGain(mode: String) { replayGain = mode; saveState(); toast(if (mode == "off") "ReplayGain off" else "ReplayGain: by $mode, from the next song") }


    var downloadDir by mutableStateOf("")      // a folder of yours downloads go to ("Music/Mine/"); "": your first music folder
    /** Where downloads go: the folder you picked, else the first folder the Music tab reads. */
    fun downloadFolder(): String = downloadDir.ifEmpty { folders.firstOrNull() ?: "Music/" }
    var dupeAction by mutableStateOf("ask")    // a download you have already: ask, keep (both) or replace
    var dupePrompt by mutableStateOf<Pair<List<Track>, List<Pair<Track, List<Track>>>>?>(null)   // asking about copies
    var trashJob by mutableStateOf<TrashJob?>(null)   // your files Android is asked to put in its trash
    var dupes by mutableStateOf<List<List<Track>>?>(null)   // the Duplicates view's last look: copies, best first
    var dupesBusy by mutableStateOf(false)
    var upgrading by mutableStateOf<String?>(null)   // Upgrade running: how far
    var upgradeKept by mutableStateOf<List<Pair<Track, String>>?>(null)   // kept as good already: replace anyway?
    @Volatile var upgradeStop = false
    private val replaceAfter = HashMap<String, List<Track>>()   // a download's id -> your copies to put away once it's in
    private var finishing = false    // the song playing ran out on its own (not a skip)
    private var goingBack = false    // Previous: going back isn't skipping

    /** Your files to put in Android's trash (it asks you), and the songs that take their places. */
    data class TrashJob(val tracks: List<Track>, val replacements: Map<String, Track> = emptyMap(), val what: String = "")
    private var searchBeforeLocal = "tidal"     // where the search looked before
    var importing by mutableStateOf<String?>(null)   // an .m3u8 being read: how far
    var importReport by mutableStateOf<ImportReport?>(null)   // what the last one didn't find, to look for
    var streaming by mutableStateOf(false)
    var downloads by mutableStateOf(listOf<Track>())
    var syncing by mutableStateOf(false)
    var syncTracks by mutableStateOf(listOf<Track>())
    val toasts = Channel<String>(Channel.BUFFERED)
    /** Notes with a button: (what it says, the button, what the button does). */
    val offers = Channel<Triple<String, String, () -> Unit>>(Channel.BUFFERED)
    var shared by mutableStateOf<String?>(null)   // a link shared to the app, for Search
    var dj by mutableStateOf(Dj.State())          // the DJ: on, its sets' kinds and lines, the one shown
    var djNew by mutableStateOf(true)             // the DJ brings new songs too, or only yours

    /** Only my songs (off), or new ones too (on): the DJ starts again with it. */
    fun chooseDjNew(on: Boolean) {
        djNew = on; Dj.withNew = on; saveState()
        val src = dj.from?.let { if (on && it.kind == "mine") Dj.From("all") else it }
        if (dj.on) djStart(src ?: Dj.From("all")) else dj = dj.copy(from = src)
    }
    var djBusy by mutableStateOf<String?>(null)   // the DJ picking a set: what it's doing

    val sources = mutableStateMapOf<String, Source>()
    val sourceErrors = mutableStateMapOf<String, String>()
    private val likes = HashMap<String, Set<String>>()

    private var radioFrom: String? = null
    private var context = 0
    private var loadSeq = 0
    private var streamRetry = -1
    private var ended = false
    private var restorePending = false
    private var downloading = false
    var downloadNote by mutableStateOf("")   // "Downloading in FLAC · 3 of 40" while a Download runs
        private set
    private var syncAgain = false

    private class Listen(val id: String, val track: Track, var played: Double = 0.0, var counted: Boolean = false,
                         var sent: Boolean = true, val start: Double = now())
    private var listen: Listen? = null

    fun init(c: Ctx) {
        if (::ctx.isInitialized) return
        ctx = appCtx(c)
        Prefs.init(ctx)
        loadDownloadQuality()
        MusicTools.init()
        QualityCheck.init(ctx)
        TagScan.Done.init(ctx)
        eqProcessor.crossfeed = Prefs.on(Prefs.CROSSFEED)
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
        dl.folder = { downloadFolder() }
        dl.onSaved = { id -> scope.launch { afterSaved(id); Tagger.start(listOf(id)) } }
        loadState()
        scope.launch { kotlinx.coroutines.delay(10_000); if (tidal.connected) Tagger.start() }
        if (tidal.connected) sources["tidal"] = tidal
        bg({ lib.prune() }) { _, _ -> savedVersion++ }
        rescan()
        // Headphones coming and going: their correction comes with them (see outputChanged).
        watchOutputs(ctx) { outputChanged() }
        scope.launch {
            while (true) {   // the position, and what counts as played
                val p = player
                if (p != null && p.isPlaying) {
                    val now = p.positionMs / 1000.0
                    val step = now - position
                    position = now
                    if (step in 0.0..1.5) listened(step)
                    p.durationMs.takeIf { it > 0 }?.let { duration = it / 1000.0 }
                    maybeCrossfade(p)
                }
                delay(500)
            }
        }
    }

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
    )

    private val stateFile get() = File(ctx.filesDir, "state.json")

    private fun loadState() {
        val st = readText(stateFile)?.let { runCatching { JSON.decodeFromString(State.serializer(), it) }.getOrNull() } ?: State()
        queue = st.queue
        index = st.index.coerceAtMost(queue.size - 1)
        radioFrom = st.radio_from
        repeat = st.repeat.takeIf { it in setOf("off", "all", "one") } ?: "off"
        source = st.source.takeIf { it in SOURCES } ?: "ytm"
        // Nothing downloads on its own until you switch a service's sync on.
        SOURCES.keys.forEach { syncSources[it] = st.sync_sources[it] ?: false }
        lyricsMode = st.lyrics_mode
        folders = st.folders
        artColors = st.art_colors
        keepPlayed = st.keep_played
        musicSort = st.music_sort
        searchIn = st.search_in.takeIf { it in SEARCH_IN } ?: "tidal"
        dj = st.dj
        apiKeys = st.api_keys.filterKeys { it in API_KEYS }
        shuffled = st.shuffled
        playlistSort = st.playlist_sort.takeIf { it in PLAYLIST_SORTS } ?: "order"
        browseLocal = st.browse_local
        searchBeforeLocal = st.search_before_local.takeIf { it in SEARCH_IN && it != "local" } ?: "tidal"
        // The Apple Music look came with 1.3: everyone starts on it (dark if the phone is), and can pick any other after.
        // 1.4 keeps Light, Dark and Now playing: any other look becomes Light or Dark, as the phone is.
        look = st.look.takeIf { st.apple_ui && org.omarchy.music.ui.Looks.known(it) } ?: "nowplaying"   // 2.0: a first start is on Now playing
        npFallback = st.np_fallback.takeIf { it == "apple" || it == "apple-dark" } ?: "apple-dark"
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
        replayGain = st.replay_gain.takeIf { it in setOf("off", "track", "album") } ?: "off"
        eqProcessor.bypass = bitPerfectNow()
        layouts.clear(); layouts.putAll(st.layouts.filterValues { it in LAYOUTS })
        autoplay = st.autoplay
        applyLook()
        visPreset = st.vis_preset
        wrappedStyle = st.wrapped_style
        skipOrder = st.skip_order.takeIf { it in setOf("most", "least", "heard") } ?: "most"
        downloadDir = st.download_dir
        dupeAction = st.dupe_action.takeIf { it in setOf("ask", "keep", "replace") } ?: "ask"
        eq = st.eq
        applyEq()
        queues = st.queues
        queueName = st.queue_name
        queueId = st.queue_id
        multiQueue = st.multi_queue
        lyricsToFiles = st.lyrics_to_files
        val t = current()
        if (t != null && (lib.has(t.id) || t.isFile)) loadCurrent(autoplay = false)
        else if (t != null) restorePending = true   // it has to come from Tidal: wait for the player and the login
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
        return State(queue, index, radioFrom, repeat, eq, source, syncSources.toMap(), lyricsMode, folders, artColors, keepPlayed, musicSort, searchIn, dj, apiKeys, shuffled, playlistSort,
            browseLocal, searchBeforeLocal, look, visPreset, wrappedStyle, skipOrder, downloadDir, dupeAction,
            queues, queueName, queueId, multiQueue, true, lyricsToFiles, true, autoplay, npFallback, drawerFolds.toMap(), pins.toList(), layouts.toMap(), micAsked, skipPeriod,
            bitPerfect, replayGain, djNew, topMineOnly, topPeriod, topKind, collageSize, artistPicks, similarKept, albumPicks, songPicks, singlesKept.toList(), notDupes.toList())
    }

    fun saveApiKeys(keys: Map<String, String>) {
        apiKeys = keys.mapValues { it.value.trim() }.filter { it.key in API_KEYS && it.value.isNotEmpty() }
        saveState()
    }

    /** Called once the playback service is up: a song restored from Tidal can load now. */
    fun playerReady() {
        if (restorePending) {
            restorePending = false
            loadCurrent(autoplay = false)
        }
    }

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

    /** A song's genre, as its file says (songs without one: Unknown genre). */
    fun genreOf(t: Track): String = LocalFiles.idOf(t.file ?: lib.items[t.id]?.file)?.let { local.genres[it] } ?: "Unknown genre"

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
            val t = fmSong(s.title, s.artist, s.thumb, mine, s.album).copy(plays = s.plays)
            if (seen.add(songKey(t))) out += t
        }
        val here = plays.items.values.associate { songKey(it.track) to it.plays }
        for (t in songsBy(name)) if (seen.add(songKey(t))) out += t.copy(plays = here[songKey(t)] ?: 0)
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
    private var mostPlayedFor: List<Stats.Song>? = null
    private var mostPlayedMade = listOf<Track>()

    /** The Music tab's Most played: every play added up (see [Stats]): Last.fm's, your Spotify
     *  history's and, without Last.fm, the plays here. Until the counts are in, the plays here stand in. */
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

    data class TopArtist(val name: String, val plays: Int, val thumb: String)

    /** Most played artists over a period: every play of theirs (every source with a time), by lead artist. All time: the Artists tab's. */
    fun topArtists(period: String = "all"): List<TopArtist> {
        if (period == "all") return Stats.artists.map { TopArtist(it.name, it.plays, it.thumb) }
        val out = LinkedHashMap<String, TopArtist>()
        val called = HashMap<String, HashMap<String, Int>>()
        val resolve = runCatching { artistResolver() }.getOrDefault { c: String -> c }
        for (s in Stats.periods[period].orEmpty()) {
            val lead = resolve(s.artist).ifEmpty { splitArtists(s.artist).firstOrNull() ?: s.artist }
            val k = resolvedKey(lead)
            val x = out[k]
            out[k] = if (x == null) TopArtist(lead, s.plays, s.thumb) else x.copy(plays = x.plays + s.plays, thumb = x.thumb.ifEmpty { s.thumb })
            called.getOrPut(k) { HashMap() }.merge(lead, s.plays, Int::plus)
        }
        // "Quinn XCII & Jeremy Zucker" is Quinn XCII's: named as they are on their own.
        return out.map { (k, x) -> x.copy(name = called[k]?.maxByOrNull { it.value }?.key ?: x.name) }.sortedByDescending { it.plays }
    }

    data class TopAlbum(val key: String, val title: String, val artist: String, val plays: Int, val tracks: List<Track>)

    /** Most played albums: Most played's songs (the same period, the same "only songs on this phone") added up by album. */
    fun topAlbums(period: String = "all"): List<TopAlbum> {
        // An album and its deluxe, extended or anniversary edition are one, under the edition's name; an album split
        // by featured artists ("half•alive & The Walters") is one; a single's plays count for the album it's on, when
        // you have that album's copy of the song. The desktop's top_albums.
        class G(val key: String) { val names = HashMap<String, Int>(); val credits = HashMap<String, Int>(); var plays = 0
            val tracks = LinkedHashMap<Pair<String, String>, Track>() }
        val songs = mostPlayed(period)
        if (songs.isNotEmpty() && localCopies.isEmpty()) songIndex()
        val groups = LinkedHashMap<String, G>()
        for (t in songs) {
            var album = t.album.trim(); var credit = t.artist
            if (album.isEmpty()) continue
            val sk = Stats.statsKey(t.title, t.artist)
            if (isSingle(album, t.title)) localCopies[sk].orEmpty().firstOrNull { it.album.isNotBlank() && !isSingle(it.album, it.title) }?.let {
                album = it.album.trim(); credit = it.artist
            }
            val (base, edition) = albumEdition(album)
            val key = norm(cleanTitle(albumWithoutPart(base))) + "|" + artistKey(credit)   // its parts (Pt. 1, Pt. 2) one too
            val g = groups.getOrPut(key) { G(key) }
            g.names.merge(album, 1 + if (edition.isNotEmpty()) 1000 else 0, Int::plus)
            g.credits.merge(splitArtists(credit).firstOrNull()?.ifEmpty { null } ?: credit, 1, Int::plus)
            g.plays += t.plays
            g.tracks[sk] = g.tracks[sk]?.let { it.copy(plays = it.plays + t.plays) } ?: t   // the same song on both editions: one
        }
        return groups.values.map { g ->
            TopAlbum(g.key, g.names.maxByOrNull { it.value }!!.key, soloName(g.credits), g.plays, g.tracks.values.toList())
        }.sortedByDescending { it.plays }
    }
    var topPeriod by mutableStateOf("all")      // Most played over: week, month, year, all
    fun chooseTopPeriod(p: String) { topPeriod = p; mostPlayedFor = null; musicVersion++; saveState() }

    fun mostPlayed(period: String = "all"): List<Track> {
        val counts = if (period == "all") Stats.songs else Stats.periods[period] ?: emptyList()
        if (Stats.songs.isEmpty()) return if (period == "all") plays.top(Int.MAX_VALUE) else emptyList()   // Most played: every song
        if (counts !== mostPlayedFor) {
            val mine = songIndex()
            // Two names for one song of yours (a remaster's, a feat.'s): one row, its plays added up.
            val at = LinkedHashMap<String, Track>()
            for (c in counts) {
                val t = fmSong(c.title, c.artist, c.thumb, mine, c.album)
                at[t.id] = at[t.id]?.let { it.copy(plays = it.plays + c.plays) } ?: t.copy(plays = c.plays)
            }
            mostPlayedMade = at.values.filter { !topMineOnly || it.isFile || lib.has(it.id) }.sortedByDescending { it.plays }
            mostPlayedFor = counts
        }
        return mostPlayedMade
    }
    val mostPlayedFromStats get() = Stats.songs.isNotEmpty() && (LastFm.connected || SpotifyHistory.count > 0 || OtherPlays.count > 0 || AppleHistory.count > 0)

    /** Song key to your copy of it: saved, in your folders, or played here. */
    fun songIndex(): Map<Pair<String, String>, Track> {
        val mine = HashMap<Pair<String, String>, Track>()
        val copies = HashMap<Pair<String, String>, MutableList<Track>>()
        val byTitle = HashMap<String, MutableList<Track>>()
        for (t in plays.items.values.map { it.track }) { mine[songKey(t)] = t; mine[Stats.statsKey(t.title, t.artist)] = t }
        for (t in mySongs()) {   // yours on the phone win over ones only played (streamed)
            val k = Stats.statsKey(t.title, t.artist)
            mine[songKey(t)] = t
            mine[k] = t
            copies.getOrPut(k) { ArrayList() } += t
            byTitle.getOrPut(k.first) { ArrayList() } += t
        }
        localCopies = copies
        localByTitle = byTitle
        return mine
    }
    @Volatile private var localCopies: Map<Pair<String, String>, List<Track>> = emptyMap()
    @Volatile private var localByTitle: Map<String, List<Track>> = emptyMap()

    private fun bare(a: String) = norm(a).replace(Regex("[\\W_]+"), "")

    /** Of your copies of a song, the one on the album asked for, then the best file (FLAC first). */
    private fun bestCopy(copies: List<Track>, album: String): Track {
        val want = norm(cleanTitle(album))
        val rank = mapOf("flac" to 0, "wav" to 1, "alac" to 1, "m4a" to 2, "ogg" to 3, "opus" to 3, "mp3" to 4)
        return copies.minWith(compareBy<Track>({ !(want.isNotEmpty() && norm(cleanTitle(it.album)) == want) },
            { rank[lib.items[it.id]?.format ?: it.file?.substringAfterLast('.')?.lowercase()] ?: 5 }))
    }

    /** A song Last.fm (or Spotify, or Apple Music) named: your file of it when you have one (the same song name
     *  and artist; the one on the same album first), else one played here, else one to be found on Tidal or
     *  YouTube Music when it plays. Call [songIndex] first: it reads your files. */
    fun fmSong(name: String, artist: String, image: String, mine: Map<Pair<String, String>, Track>, album: String = ""): Track {
        val k = Stats.statsKey(name, artist)
        var copies = localCopies[k].orEmpty()
        if (copies.isEmpty()) {   // the artist written differently: one of the song's artists is one of your file's
            val theirs = splitArtists(artist).map { bare(it) }.filter { it.isNotEmpty() }.toSet()
            copies = localByTitle[k.first].orEmpty().filter { t -> splitArtists(t.artist).any { bare(it) in theirs } }
        }
        if (copies.isEmpty() && album.isNotBlank()) {   // the artist tagged another way still: the same song name on the same album
            val want = norm(cleanTitle(album))
            copies = localByTitle[k.first].orEmpty().filter { norm(cleanTitle(it.album)) == want }
        }
        if (copies.isNotEmpty()) return bestCopy(copies, album)
        return mine[songKey(Track("", title = name, artist = artist))] ?: mine[k] ?: run {
            val sha = java.security.MessageDigest.getInstance("SHA-1").digest("${norm(artist)}|${norm(name)}".toByteArray())
            Track("lfm-" + sha.joinToString("") { "%02x".format(it) }.take(16), "lastfm", name, artist,
                album = album, thumb = image.takeIf { LASTFM_BLANK !in it } ?: "")
        }
    }

    // ---- the DJ ----
    /** What the DJ screen shows: the phone's DJ, or the desktop's while it's the remote. */
    fun shownDj(): Dj.Shown {
        if (Remote.active) return Remote.status.dj
        val shown = dj.sets[dj.shown].takeIf { dj.on }
        return Dj.Shown(dj.on, djBusy ?: "", shown?.kind ?: "", shown?.line ?: "", dj.sets.mapValues { it.value.kind }, djFromName(dj.from))
    }

    /** What the DJ plays from, as [Dj.From] keeps it: (name, songs), or null if it's gone. */
    fun djSource(f: Dj.From?): Pair<String, List<Track>>? = when (f?.kind) {
        "playlist" -> lists.get(f.id)?.let { it.name to it.tracks }
        "folder" -> djFromName(f) to mySongs().filter { (local.paths[it.file] ?: "").startsWith(f.path) }
        "mine" -> "your music" to mySongs()
        "songs" -> djFromName(f) to f.songs
        else -> null
    }

    fun djFromName(f: Dj.From?): String = when (f?.kind) {
        null, "all" -> ""
        "playlist" -> lists.get(f.id)?.name ?: "a playlist that's gone"
        "folder" -> f.path.trimEnd('/').substringAfterLast('/').ifEmpty { f.path }
        "mine" -> "your music"
        else -> f.name.ifEmpty { "these songs" }
    }

    /** The DJ playing from what dj.from says (after a restart, say). */
    private fun djReady() {
        if (dj.from == null || Dj.scope != null) return
        djSource(dj.from)?.takeIf { it.second.isNotEmpty() }?.let { Dj.playFrom(it.first, it.second) }
    }

    private fun coming() = queue.drop(maxOf(0, index)).map { songKey(it) }.toSet()

    /** Start the DJ: a first set of what you've had on repeat (or whatever it can make). from: what it
     *  plays from; Dj.From("all") for everything; null for what it played from last. */
    fun djStart(from: Dj.From? = null) {
        if (Remote.active) { Remote.djStart(from); return }
        if (djBusy != null) return
        Dj.withNew = djNew
        val src = (from ?: dj.from)?.takeIf { it.kind != "all" } ?: if (!djNew) Dj.From("mine") else null   // only yours: all you have
        if (src != null) {
            val got = djSource(src)
            if (got == null || got.second.isEmpty()) {
                toast("There are no songs in ${got?.first ?: "that"} for the DJ to play")
                return
            }
            Dj.playFrom(got.first, got.second)
        } else Dj.playFrom(null, null)
        djBusy = Dj.scope?.let { "Picking your first set from ${it.first}…" } ?: "Picking your first set…"
        bg({ Dj.build(first = true) }) { res, err ->
            djBusy = null
            if (res == null) {
                toast(if (err == null) "The DJ couldn't find songs yet. Play a few, or connect Last.fm." else "The DJ couldn't start: ${err.message?.take(100)}")
                return@bg
            }
            dj = Dj.State(from = src)
            playFrom(djTag(res), 0, radio = false)
            dj = dj.copy(on = true)
            djAnnounce(current())
        }
    }

    fun djStop() {
        if (Remote.active) { Remote.cmd("dj", "do" to "stop"); return }
        dj = dj.copy(on = false)
        saveState()
    }

    /** The set's songs marked as set n of the DJ's (n: the next number), with its line. */
    private fun djTag(b: Dj.Built): List<Track> {
        val n = (dj.sets.keys.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1
        var sets = dj.sets + (n.toString() to Dj.Info(b.kind, b.line))
        if (sets.size > 30) sets = sets - sets.keys.minBy { it.toInt() }
        dj = dj.copy(sets = sets)
        Dj.used += b.songs.map { songKey(it) }
        return b.songs.map { it.copy(dj = n) }
    }

    /** A set begins: its line, where you see it. */
    private fun djAnnounce(t: Track?) {
        val n = t?.dj?.toString() ?: return
        val set = dj.sets[n]
        if (!dj.on || set == null || n == dj.shown) return
        dj = dj.copy(shown = n)
        toast("DJ · ${set.line}")
    }

    /** Near the end of the queue: the DJ's next set after it. */
    private fun djMore() {
        if (djBusy != null) return
        djReady()
        djBusy = "Picking the next set…"
        val ctxNow = context
        val avoid = djKind()
        val coming = coming()
        bg({ Dj.build(avoid = avoid, coming = coming) }) { res, _ ->
            djBusy = null
            if (res != null && ctxNow == context && dj.on) {
                queue = queue + djTag(res)
                afterQueueChange()
            }
        }
    }

    private fun djKind() = dj.sets[current()?.dj.toString()]?.kind

    /** Switch it up (or play what you asked for): a new set now, in place of the rest of this one. */
    fun djSwitch(ask: String? = null) {
        if (Remote.active) { if (ask != null) Remote.cmd("dj", "do" to "ask", "text" to ask) else Remote.cmd("dj", "do" to "switch"); return }
        if (djBusy != null) return
        if (!dj.on && ask == null) { djStart(); return }
        djReady()
        djBusy = if (ask != null) "Looking for ${ask.trim()}…" else "Switching it up…"
        val avoid = djKind()
        val coming = coming()
        bg({ if (ask != null) Dj.ask(ask) else Dj.build(avoid = avoid, coming = coming) }) { res, err ->
            djBusy = null
            if (res == null) {
                toast(if (ask != null) "The DJ found nothing for “${ask.trim()}”. Try an artist, a genre, a decade or a vibe."
                      else "The DJ has nothing else right now" + (err?.let { ": ${it.message?.take(80)}" } ?: "."))
                return@bg
            }
            if (!dj.on) dj = Dj.State(on = true, from = dj.from)
            val songs = djTag(res)
            val queued = queue.drop(index + 1).filter { it.queued }
            val keep = if (current() != null) queue.take(index + 1) else emptyList()
            queue = keep + queued + songs
            index = keep.size + queued.size
            radioFrom = null
            context++
            loadCurrent()
        }
    }

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
        SOURCES.filter { (k, _) -> sources[k]?.let { k != "tidal" || it.signedIn } == true }.toList()

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
            if (res == null) { toast("Couldn't import it: ${err?.message?.take(100)}"); return@bg }
            val (name, tracks, missed) = res
            val id = lists.create(name).id
            lists.add(id, tracks)
            musicVersion++
            missed.forEach { logWarn("import $name: not found: ${it.name} (${it.why})") }
            toast("Imported “$name”: ${tracks.size} songs" + if (missed.isNotEmpty()) " (${missed.size} not found)" else "")
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
            accountsVersion++
            QualityCheck.scan()   // Loudness check: the songs new or changed since
        }
    }

    /** Whether a song file's address is one this device can play (not the other device's, from a backup). */
    fun fileHere(t: Track): Boolean {
        val f = t.file ?: return true
        if (!BackupMerge.fileHere(f, platformName == "android")) return false
        return platformName == "android" || f.startsWith("content:") || File(f).exists()
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
    private fun standIn(t: Track, autoplay: Boolean) {
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
    val discovering = androidx.compose.runtime.mutableStateListOf<String>()   // artists whose list is being made

    /** (artist, the discovery playlist, the discovered one) of every artist you're discovering. */
    fun discoveryLists(): List<Triple<String, Playlist?, Playlist?>> =
        lists.all().filter { it.discovery != null }.groupBy { norm(it.discovery!!.artist) }.values.map { ps ->
            Triple(ps.first().discovery!!.artist, ps.firstOrNull { it.discovery!!.role == "discover" },
                ps.firstOrNull { it.discovery!!.role == "discovered" })
        }

    /** A discovery playlist for artist: every song of theirs the services have that you don't, album by
     *  album ("Artist Discovery playlist"), and one for the songs you hear to the end ("Artist discovered"). */
    fun makeDiscovery(artist0: String) {
        val artist = artist0.split(",")[0].trim()
        if (artist.isEmpty() || norm(artist) in discovering) return
        discovering += norm(artist)
        toast("Finding $artist's songs you haven't got…")
        bg({
            val info = artistInfo(artist, null) ?: error("no service knows $artist")
            val src = sources[info.source]
            val have = HashSet(songIndex().keys)
            discoveryLists().firstOrNull { norm(it.first) == norm(artist) }?.let { (_, d, f) ->
                (d?.tracks.orEmpty() + f?.tracks.orEmpty()).forEach { have += songKey(it) } }
            val seen = HashSet<Pair<String, String>>()
            val out = ArrayList<Track>()
            for (al in info.albums) {
                val songs = runCatching { src?.album(al.id) ?: emptyList() }.getOrDefault(emptyList())
                for (t in songs) { val k = songKey(t); if (k !in have && seen.add(k)) out += if (t.album.isEmpty()) t.copy(album = al.title) else t }
            }
            for (t in info.top) { val k = songKey(t); if (k !in have && seen.add(k)) out += t }
            info.name.ifEmpty { artist } to out
        }) { res, err ->
            discovering.remove(norm(artist))
            if (res == null) { toast("Couldn't make it: ${err?.message?.take(100)}"); return@bg }
            val (name, songs) = res
            val pair = discoveryLists().firstOrNull { norm(it.first) == norm(name) }
            val disc = pair?.second ?: lists.create("$name Discovery playlist", discovery = Discovery(name, "discover"))
            if (pair?.third == null) lists.create("$name discovered", discovery = Discovery(name, "discovered"))
            val n = lists.add(disc.id, songs)
            toast(if (n > 0) "“${disc.name}”: $n song${if (n != 1) "s" else ""} of $name's you haven't got" else "You have every song of $name's the services list")
            musicVersion++
        }
    }

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

    /** A discovery playlist from New releases you picked: their songs you haven't got ("New releases
     *  Discovery playlist"), and "New releases discovered" for the ones you hear to the end, to download
     *  or let go, as an artist's discovery playlist works. */
    // ---- An album's songs you haven't got (its page's bottom) ----
    data class Missing(val n: Int, val title: String, val short: String, val secs: Int)
    private val albumTracksSeen = HashMap<Pair<String, String>, List<Missing>>()

    /** (blocking) The songs of your album you haven't got, from its tracklist on Deezer, in order ([] if none or not found). */
    fun albumMissing(album: String, artist: String, tracks: List<Track>): List<Missing> {
        val k = norm(artist) to norm(album)
        val all = albumTracksSeen.getOrPut(k) {
            val data = httpJson("https://api.deezer.com/search/album?q=" + enc("artist:\"$artist\" album:\"$album\"") + "&limit=5")["data"]
            val best = (data as? kotlinx.serialization.json.JsonArray)?.firstOrNull { norm(it["title"].str) == norm(album) }
            if (best == null) emptyList() else {
                val ts = httpJson("https://api.deezer.com/album/${best["id"].long}/tracks?limit=200")["data"] as? kotlinx.serialization.json.JsonArray
                ts.orEmpty().mapIndexed { i, t -> Missing((t["track_position"].long ?: (i + 1).toLong()).toInt(), t["title"].str ?: "",
                    t["title_short"].str ?: "", (t["duration"].long ?: 0L).toInt()) }
            }
        }
        val have = tracks.flatMap { listOf(norm(cleanTitle(it.title)), norm(bareTitle(it.title))) }.toSet()
        return all.filter { norm(cleanTitle(it.title)) !in have && norm(cleanTitle(it.short)) !in have && norm(bareTitle(it.title)) !in have }
    }

    /** Download missing tracks…: the ones picked, found on Tidal (or YouTube Music) and saved. */
    fun downloadMissing(artist: String, album: String, want: List<Missing>) {
        val key = if (tidal.connected) "tidal" else "ytm"
        val src = sources[key] ?: run { toast("Connect Tidal or YouTube Music in Settings to download"); return }
        toast("Finding ${want.size} song${if (want.size != 1) "s" else ""}…")
        bg({ want.mapNotNull { m -> runCatching { matchSong(src, artist, m.title, m.secs).first }.getOrNull()?.let { it.copy(album = it.album.ifEmpty { album }) } } }) { got, err ->
            val found = got.orEmpty()
            if (found.isNotEmpty()) download(found)
            toast(if (err != null) "Couldn't download: ${err.message?.take(100)}" else "Downloading ${found.size} of ${want.size}" +
                if (found.size < want.size) " (${want.size - found.size} not found)" else "")
        }
    }

    /** A release's discovery playlist's name: “Persona (Extended) from half•alive”. */
    fun releaseName(r: NewReleases.Release) = "${r.title} from ${r.artist}"

    /** A discovery playlist (and its discovered one) for each release, with the songs picked. They work as an
     *  artist's do: a song heard to the end moves to the discovered one. */
    fun makeReleaseDiscoveries(chosen: List<Pair<NewReleases.Release, List<Track>>>, quiet: Boolean = false): List<String> {
        val made = ArrayList<String>()
        for ((r, songs) in chosen) {
            val name = releaseName(r)
            val pair = discoveryLists().firstOrNull { norm(it.first) == norm(name) }
            val disc = pair?.second ?: lists.create("$name Discovery playlist", discovery = Discovery(name, "discover"))
            if (pair?.third == null) lists.create("$name discovered", discovery = Discovery(name, "discovered"))
            lists.add(disc.id, songs)
            made += name
        }
        if (made.isEmpty()) return made
        if (!quiet) toast(if (made.size == 1) "Made “${made[0]} Discovery playlist”" else "Made ${made.size} discovery playlists")
        musicVersion++
        return made
    }

    /** A song heard to the end: out of its discovery playlist, into the discovered one. */
    fun discovered(t: Track) {
        for ((_, d, f) in discoveryLists()) if (d != null && f != null && d.tracks.any { it.id == t.id }) {
            lists.remove(d.id, setOf(t.id))
            lists.add(f.id, listOf(t))
            musicVersion++
            return
        }
    }

    /** The discovered list's review: download some songs, take some out. */
    fun reviewDiscovered(id: String, download: Set<String>, remove: Set<String>) {
        val pl = lists.get(id) ?: return
        pl.tracks.filter { it.id in download }.takeIf { it.isNotEmpty() }?.let { download(it) }
        if (remove.isNotEmpty()) lists.remove(id, remove)
        musicVersion++
    }

    /** Copy a service's playlist (Tidal, YouTube Music, Deezer, Apple Music, Spotify) into your playlists here, its
     *  songs streaming as they are until you download them. A playlist of yours with the same name gets the songs it
     *  hasn't got (importing again brings in what was added there since). */
    fun importServicePlaylist(key: String, pl: Album, quiet: Boolean = false, done: ((Int) -> Unit)? = null) {
        val src = sources[key] ?: run { toast("Sign in to ${SOURCES[key]} first"); return }
        if (!quiet) toast("Importing “${pl.title}”…")
        bg({ src.playlist(pl.id) }) { tracks, err ->
            if (tracks == null) { if (!quiet) toast("Couldn't import “${pl.title}”: ${err?.message?.take(100)}"); done?.invoke(-1); return@bg }
            val name = pl.title.ifBlank { "${SOURCES[key]} playlist" }
            val mine = lists.all().firstOrNull { it.name.equals(name, ignoreCase = true) }
            val id = mine?.id ?: lists.create(name).id
            val n = lists.add(id, tracks)
            musicVersion++
            if (!quiet) toast(if (mine == null) "Imported “$name”: $n songs. Open it in your playlists to download it"
                else "“$name” was imported already: $n new song${if (n != 1) "s" else ""} added")
            done?.invoke(n)
        }
    }

    /** Every playlist of yours on that service imported, one after another. */
    fun importAllServicePlaylists(key: String, lists: List<Album>) {
        val todo = lists
        if (todo.isEmpty()) return
        toast("Importing ${todo.size} ${SOURCES[key]} playlists…")
        var i = 0; var songs = 0; var failed = 0
        fun next() {
            if (i >= todo.size) { toast("Imported ${todo.size - failed} playlists ($songs songs)" + if (failed > 0) ", $failed couldn't be read" else ""); return }
            importServicePlaylist(key, todo[i++], quiet = true) { n -> if (n < 0) failed++ else songs += n; next() }
        }
        next()
    }

    /** Download every song of these playlists that isn't on this device yet: your song files are skipped, and so are
     *  songs downloaded already (and Copies decides about songs you have another copy of). */
    fun downloadPlaylists(ids: Collection<String>) {
        val tracks = ids.flatMap { lists.get(it)?.tracks ?: emptyList() }.distinctBy { it.id }
        if (tracks.none { !it.isFile && !lib.has(it.id) }) { toast("Every song there is downloaded or one of your files already"); return }
        download(tracks)
    }

    fun newPlaylist(name: String): String {
        val id = lists.create(name).id
        toast("Made “$name”")
        musicVersion++
        return id
    }

    fun addToPlaylist(id: String, tracks: List<Track>) {
        val pl = lists.get(id) ?: return
        if (tracks.isEmpty()) return
        val n = lists.add(id, tracks)
        toast(when {
            n == 0 -> "Already in “${pl.name}”"
            tracks.size == 1 -> "Added to “${pl.name}”"
            else -> "$n songs added to “${pl.name}”" + if (n < tracks.size) " (${tracks.size - n} were in it)" else ""
        })
        musicVersion++
    }

    fun removeFromPlaylist(id: String, ids: Set<String>) { lists.remove(id, ids); musicVersion++ }
    fun renamePlaylist(id: String, name: String) { lists.rename(id, name); musicVersion++ }
    // ---- folders of playlists ----
    fun newFolder(name: String, item: FolderItem? = null) {
        val f = playlistFolders.create(name)
        if (item != null) { playlistFolders.add(f.id, item); toast("Put “${item.title}” in the new folder “$name”") }
        musicVersion++
    }

    fun renameFolder(id: String, name: String) { playlistFolders.rename(id, name); musicVersion++ }

    fun deleteFolder(id: String) {
        playlistFolders.get(id)?.let { toast("Deleted the folder “${it.name}”") }
        playlistFolders.delete(id)
        musicVersion++
    }

    fun addToFolder(id: String, item: FolderItem) {
        val f = playlistFolders.get(id) ?: return
        toast(if (playlistFolders.add(id, item)) "Put “${item.title}” in “${f.name}”" else "“${item.title}” is in “${f.name}” already")
        musicVersion++
    }

    fun removeFromFolder(id: String, item: FolderItem) { playlistFolders.remove(id, item.key); musicVersion++ }

    fun deletePlaylist(id: String) {
        lists.get(id)?.let { toast("Deleted “${it.name}”") }
        playlistFolders.forget("mine::$id")
        lists.delete(id)
        musicVersion++
    }

    fun playList(tracks: List<Track>, shuffled: Boolean = false) {
        val t = if (shuffled) shuffleSongs(tracks) else tracks
        if (t.isNotEmpty()) { playFrom(t, 0, radio = false); this.shuffled = shuffled }
    }

    // ---- downloads ----
    fun download(tracks: List<Track>, checked: Boolean = false) {
        val waiting = downloads.map { it.id }.toSet()
        val new = tracks.filter { !it.isFile && !lib.has(it.id) && it.id !in waiting }
        if (new.isEmpty()) { toast("Those are downloaded already"); return }
        if (!checked && dupeAction != "keep") {
            val dupes = new.mapNotNull { t -> copiesOf(t).takeIf { it.isNotEmpty() }?.let { t to it } }
            if (dupes.isNotEmpty()) {
                if (dupeAction == "replace") dupes.forEach { (t, c) -> replaceAfter[t.id] = c }
                else { dupePrompt = new to dupes; return }
            }
        }
        downloads = downloads + new
        toast("Downloading ${new.size} song${if (new.size != 1) "s" else ""} ${downloadQualityNote()}" + if (Remote.active) " to this phone" else "")
        savedVersion++
        if (!downloading) downloadNext()
    }

    private fun downloadNext() {
        if (downloads.isEmpty()) {
            if (downloading) Tagger.resume()   // the songs that came in are tagged together now
            downloading = false; downloadNote = ""; savedVersion++; musicVersion++; return
        }
        if (!downloading) Tagger.pause()
        downloading = true
        val batch = downloads
        downloadNote = "Downloading · 0 of ${batch.size}"
        bg({ dl.sync(batch, force = true, workers = Downloader.SONGS_AT_ONCE) { n -> scope.launch { savedVersion++; downloadNote = "Downloading · $n of ${batch.size}" } } }) { out, err ->
            downloads = downloads.filter { d -> batch.none { it.id == d.id } }
            toast(out?.summary() ?: "Couldn't download: ${err?.message?.take(90)}")
            downloadNext()
        }
    }

    fun saveCurrent() { current()?.let { download(listOf(it)) } }

    /** Settings › Downloads › Quality: "flac24", "flac16" or "mp3" (320 kbps). */
    var downloadQuality by mutableStateOf("flac24")
        private set
    fun chooseDownloadQuality(q: String) { downloadQuality = q; Prefs.setText(Prefs.DL_QUALITY, q) }
    fun loadDownloadQuality() { downloadQuality = Prefs.text(Prefs.DL_QUALITY, "flac24").ifEmpty { "flac24" } }

    /** How downloads come now, for a toast: "in FLAC (hi-res where Tidal has it)", "from YouTube"… */
    fun downloadQualityNote(): String = if (!tidal.connected) "from YouTube (Tidal isn't connected)" else when (downloadQuality) {
        "flac16" -> "in FLAC 16-bit"
        "mp3" -> if (CAN_MP3) "in MP3 320 kbps" else "at 320 kbps (AAC)"
        else -> "in FLAC (hi-res where Tidal has it)"
    }

    // ---- copies of a song ----
    private var copyIndex: Pair<Triple<Int, Int, Double>, Map<String, List<Track>>>? = null

    /** Your songs on the phone by the first letters of their title (squashed): kept until your songs change. */
    private fun copyPool(t: Track): List<Track> {
        val sig = Triple(lib.items.size, local.files.size, local.scanned)
        val idx = copyIndex?.takeIf { it.first == sig }?.second ?: mySongs().groupBy { squashKey(cleanTitle(it.title)).take(4) }
            .also { copyIndex = sig to it }
        return idx[squashKey(cleanTitle(t.title)).take(4)] ?: emptyList()
    }

    /** Your songs on the phone that are likely t (close is enough: it's asked about), t itself left out. */
    fun copiesOf(t: Track): List<Track> = copyPool(t).filter { it.id != t.id && maybeSame(t, it) && (it.isFile || lib.has(it.id)) }

    /** Whether a song may be saved without asking (Keep, or a service's sync): yes unless you have it
     *  already and Copies says Ask (then it streams; Download asks). With Replace, your copy goes once it's in. */
    fun autoSaveOk(t: Track): Boolean {
        if (dupeAction == "keep" || lib.has(t.id)) return true
        val copies = copiesOf(t)
        if (copies.isEmpty()) return true
        if (dupeAction == "replace") { synchronized(replaceAfter) { replaceAfter[t.id] = copies }; return true }
        return false
    }

    /** A download is in: your copies of it go, if you said to replace them. */
    private fun afterSaved(id: String) {
        val old = synchronized(replaceAfter) { replaceAfter.remove(id) } ?: return
        val e = lib.items[id] ?: return
        replaceCopies(old, e.track(), "copies of “${e.title}”")
    }

    /** Put old (copies of new) away: downloads at once, your files in Android's trash once you say yes. */
    fun replaceCopies(old: List<Track>, new: Track, what: String) {
        val playing = current()?.id
        val mine = old.filter { it.id != playing }
        val downloads = mine.filter { lib.has(it.id) && lib.items[it.id]?.file != lib.items[new.id]?.file }
        downloads.forEach { lib.remove(it.id); retarget(listOf(it.id), new) }
        val files = mine.filter { it.isFile && Music.phoneFile(it) != null }
        if (files.isNotEmpty()) queueTrash(TrashJob(files, files.associate { it.id to new }, what))
        else if (downloads.isNotEmpty()) toast("Replaced ${downloads.size} ${if (downloads.size == 1) "copy" else "copies"} of “${new.title}”")
        musicVersion++; savedVersion++
    }

    private val trashQueue = ArrayDeque<TrashJob>()

    /** Ask Android to put files in its trash (one ask at a time; Android 10 can't: they stay). */
    fun queueTrash(job: TrashJob) {
        if (!sdkAtLeast(30)) { toast("Android 10 can't put other apps' files in the trash: your old files stay"); return }
        if (trashJob == null) trashJob = job else trashQueue.addLast(job)
    }

    /** Android said yes (ok) or no to the trash job. */
    fun trashed(job: TrashJob, ok: Boolean) {
        trashJob = trashQueue.removeFirstOrNull()
        if (!ok) { toast("Your old files stay where they are"); return }
        val ids = job.tracks.map { it.id }.toSet()
        local.files = local.files.filter { it.id !in ids }
        for (t in job.tracks) {
            val new = job.replacements[t.id]
            if (new != null) retarget(listOf(t.id), new)
            else lists.all().filter { p -> p.tracks.any { it.id == t.id } }.forEach { lists.remove(it.id, setOf(t.id)) }
        }
        unqueueMany(queue.indices.filter { queue[it].id in ids && job.replacements[queue[it].id] == null })
        dupes = dupes?.map { g -> g.filter { it.id !in ids } }?.filter { it.size > 1 }
        toast(if (job.replacements.isNotEmpty()) "Replaced ${job.tracks.size} file${if (job.tracks.size != 1) "s" else ""} (in Android's trash)"
              else "${job.tracks.size} file${if (job.tracks.size != 1) "s" else ""} in Android's trash")
        musicVersion++; savedVersion++
    }

    /** Songs going away for new: your playlists and the queue get new in their place, their plays go with it. */
    fun retarget(oldIds: List<String>, new: Track) {
        val gone = oldIds.toSet() - new.id
        if (gone.isEmpty()) return
        val keep = new.slim()
        for (p in lists.all()) if (p.tracks.any { it.id in gone }) {
            val have = HashSet<String>()
            val out = p.tracks.map { if (it.id in gone) keep else it }.filter { have.add(it.id) }
            lists.replaceTracks(p.id, out)
        }
        queue = queue.mapIndexed { i, t -> if (t.id in gone && i != index) keep.copy(queued = t.queued) else t }
        plays.merge(gone, keep)
        saveState()
    }

    /** Download the songs you were asked about: r is "keep", "replace" or "skip" (only the new ones). */
    fun answerDupes(r: String) {
        val (new, dupes) = dupePrompt ?: return
        dupePrompt = null
        if (r == "cancel") return
        val have = dupes.map { it.first.id }.toSet()
        if (r == "replace") dupes.forEach { (t, c) -> synchronized(replaceAfter) { replaceAfter[t.id] = c } }
        download(new.filter { r != "skip" || it.id !in have }, checked = true)
    }

    // ---- Duplicates ----
    fun findDupes() {
        if (dupesBusy) return
        dupesBusy = true
        bg({
            mySongs().filter { it.isFile || lib.has(it.id) }.groupBy { looseKey(it) }.filter { (k, v) -> v.size > 1 && k.first.isNotEmpty() }
                .values.map { g -> g.distinctBy { lib.items[it.id]?.file ?: it.file }.sortedByDescending { copyRank(it) } }
                .filter { it.size > 1 && dupeKey(it) !in notDupes }.sortedBy { norm(it[0].artist) + "|" + norm(it[0].title) }
        }) { res, err ->
            dupesBusy = false
            dupes = res ?: emptyList()
            if (err != null) toast("Couldn't look for duplicates: ${err.message?.take(80)}")
        }
    }

    /** Which copy is best to keep: a FLAC first, a download (24-bit) over a file, then the longer one. */
    fun copyRank(t: Track): Double {
        val e = lib.items[t.id]
        val q = e?.quality ?: ""
        val name = (e?.file ?: t.file ?: "").lowercase()
        val flac = e?.format == "flac" || name.endsWith(".flac") || copyInfo(t).startsWith("FLAC")
        return (if (flac) 1000.0 else 0.0) + (if (q.startsWith("24")) 100.0 else 0.0) + t.duration / 1000
    }

    /** What a copy is: its format and quality, as far as the phone says. */
    fun copyInfo(t: Track): String {
        lib.items[t.id]?.let { e -> return listOf(e.format.uppercase().ifEmpty { "?" }, e.quality).filter { it.isNotEmpty() }.joinToString(" · ") }
        val f = t.file ?: return ""
        return runCatching {
            val (mime, size) = ctx.typeAndSize(f) ?: return@runCatching ""
            listOf(mime.substringAfter('/').uppercase().replace("X-", "").replace("MPEG", "MP3"), "%.1f MB".format(size / 1e6)).joinToString(" · ")
        }.getOrDefault("")
    }

    /** Where a song is, for the Duplicates list. */
    fun whereIs(t: Track): String = lib.items[t.id]?.let { "Downloaded here" } ?: local.paths[t.file ?: ""]?.trimEnd('/') ?: ""

    /** Delete one copy of a song in Duplicates: the copy kept takes its place in playlists. */
    fun deleteDupe(group: List<Track>, t: Track) {
        val keep = group.firstOrNull { it.id != t.id } ?: return
        if (current()?.id == t.id) { toast("That copy is playing: skip it first"); return }
        if (lib.has(t.id)) {
            lib.remove(t.id); retarget(listOf(t.id), keep)
            dupes = dupes?.map { g -> g.filter { it.id != t.id } }?.filter { it.size > 1 }
            toast("Deleted the downloaded copy of “${t.title}”"); savedVersion++; musicVersion++
        } else queueTrash(TrashJob(listOf(t), mapOf(t.id to keep), "a copy of “${t.title}”"))
    }

    /** Duplicates' Keep / Remove: in each group the copies picked go (downloads at once, files to the bin together),
     *  the first one kept taking their place in playlists. A group with every copy picked keeps its best. */
    fun removeDupes(picks: List<Pair<List<Track>, Set<String>>>) {
        val playing = current()?.id
        val files = ArrayList<Track>(); val to = HashMap<String, Track>(); val gone = HashSet<String>()
        var skipped = false
        for ((g, rm) in picks) {
            val keep = g.firstOrNull { it.id !in rm } ?: g.first()
            for (t in g) {
                if (t.id !in rm || t.id == keep.id) continue
                if (t.id == playing) { skipped = true; continue }
                if (lib.has(t.id)) { lib.remove(t.id); retarget(listOf(t.id), keep); gone += t.id }
                else { files += t; to[t.id] = keep }
            }
        }
        if (gone.isNotEmpty()) {
            dupes = dupes?.map { g -> g.filter { it.id !in gone } }?.filter { it.size > 1 }
            savedVersion++; musicVersion++
        }
        if (files.isNotEmpty()) queueTrash(TrashJob(files, to, "${files.size} duplicate cop${if (files.size != 1) "ies" else "y"}"))
        val n = gone.size + files.size
        toast((if (n > 0) "Removing $n cop${if (n != 1) "ies" else "y"}" else "Nothing to remove") + if (skipped) " · the playing one is left: skip it first" else "")
    }

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
        val v = gains.saved(tracks[0], output) ?: 0.0
        val what = if (tracks.size == 1) "“${tracks[0].title}”" else "${tracks.size} songs"
        val where = if (output == null) "" else " on ${outLabel(output)}"
        toast(if (v == 0.0) "$what back to normal volume$where" else "$what at ${"%+.1f".format(v)} dB$where")
        musicVersion++
    }

    /** Settings › Sound quality › Song volume changes: off, every song plays at its normal volume (the changes kept). */
    val songVolumesOn get() = Prefs.on(SONG_VOLUMES, true)
    const val SONG_VOLUMES = "song_volumes"
    fun setSongVolumesOn(on: Boolean) {
        Prefs.set(SONG_VOLUMES, on)
        current()?.let { eqProcessor.songGain = gainOf(it) }
        musicVersion++
    }

    /** The change t plays with here and now (dB): its own for this output, else its every-output one; 0 with the switch off. */
    fun gainNow(t: Track): Double = if (!songVolumesOn) 0.0 else gains.get(t, outputNow.key)

    private fun gainOf(t: Track) = Math.pow(10.0, gainNow(t) / 20)



    fun chooseLook(l: String) { look = l; coverRgb = null; applyLook(); saveState() }
    fun chooseNpFallback(l: String) { npFallback = l; if (look == "nowplaying") applyLook(); saveState() }
    /** The playing song's cover colour, for the Now playing look (null: none yet). */
    var coverRgb: Int? = null
    fun applyLook() {
        if (look == "nowplaying") org.omarchy.music.ui.Looks.applyCover(coverRgb, npFallback)
        else org.omarchy.music.ui.Looks.apply(look)
    }
    fun chooseVisPreset(p: String) { visPreset = p; saveState() }
    fun chooseWrappedStyle(s: String) { wrappedStyle = s; saveState() }
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
                if (e.id == playing || e.format != "flac" || !Tags.writable(e.file)) continue
                try { Covers.write(ctx, e.file, jpeg); written++ } catch (x: Exception) { failed += "${e.title}: ${x.message}" }
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

    private fun playbackOf(t: Track): Pair<Float, Float> = if (bitPerfect) 1f to 1f else speeds.get(t).let { s ->
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

    /** Write changes {field: value} into the tags of tracks' downloaded FLACs, and show the new
     *  names wherever the songs are. The playing song is left alone: its file is being read. */
    fun editTags(tracks: List<Track>, changes: Map<String, String>, renameFiles: Boolean = false) {
        val playing = current()?.id
        val todo = tracks.filter { Tags.editableAny(it) != null && it.id != playing }
        if (todo.size < tracks.count { Tags.editableAny(it) != null }) toast("The playing song is left as it is: edit it once it's over")
        if (todo.isEmpty() || changes.isEmpty()) return
        bg({
            val renamed = HashMap<String, Triple<String, String, String>>()
            val failed = ArrayList<String>()
            val moves = ArrayList<Pair<String, String>>()
            for (t in todo) {
                val e = lib.items[t.id]
                val file = e?.file ?: Tags.editableAny(t) ?: continue   // a download, or (PC) a file of yours
                val c = try { Tags.write(ctx, file, changes) } catch (x: Exception) { failed += "${t.title}: ${x.message}"; continue }
                val title = c["TITLE"]?.firstOrNull() ?: e?.title ?: t.title
                if (renameFiles && FileTags.canMove && title != (e?.title ?: t.title))
                    renamedFile(file, e?.title ?: t.title, title)?.let { moves += file.replace('\\', '/') to it.replace('\\', '/') }
                val artist = c["ARTIST"]?.joinToString(", ")?.ifEmpty { null } ?: "Unknown artist"
                val album = c["ALBUM"]?.firstOrNull() ?: ""
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
                else if (n > 0) scope.launch(kotlinx.coroutines.Dispatchers.Main) { toast("Renamed $n file${if (n != 1) "s" else ""}") }
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
        val srcs = sources.filter { (k, s) -> s.signedIn && syncSources[k] == true }.values.toList()
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
    private fun beginListen(t: Track) {
        listen = Listen(t.id, t, sent = !LastFm.connected)
        if (LastFm.connected) bg({ LastFm.nowPlaying(t) })
    }

    /** A song counts as played (Recent, Most played) after PLAY_AFTER seconds; Last.fm gets
     *  it once you've listened to half of it (or four minutes). */
    private fun listened(seconds: Double) {
        val ls = listen ?: return
        if (ls.sent && ls.counted) return
        ls.played += seconds
        val dur = duration.takeIf { it > 0 } ?: ls.track.duration
        if (!ls.counted && ls.played >= (if (dur > 0) dur / 2 else PLAY_AFTER.toDouble())) {   // a play: half the song heard
            ls.counted = true
            plays.add(ls.track)
            lib.played(ls.id)
            musicVersion++
        }
        if (!ls.sent && dur > 30 && ls.played >= minOf(dur / 2, 240.0)) {
            ls.sent = true
            val start = ls.start
            bg({ LastFm.scrobble(ls.track, start) })
        }
    }

    // ---- the queue ----
    fun current(): Track? = queue.getOrNull(index)
    fun isRadio() = radioFrom != null

    // What the screens show as playing: the phone's, or the desktop's while it's the remote.
    fun shownQueue() = if (Remote.active) Remote.status.queue else queue
    fun shownIndex() = if (Remote.active) Remote.status.index else index
    fun shownTrack() = if (Remote.active) Remote.status.track else current()
    fun shownRadio() = if (Remote.active) Remote.status.radio else isRadio()
    fun livePosition(): Double = if (Remote.active) Remote.livePosition() else player?.let { it.positionMs / 1000.0 } ?: position

    /** Play tracks[i], then the rest of tracks; the songs you queued come first. */
    fun playFrom(tracks: List<Track>, i: Int, radio: Boolean) {
        if (Remote.active) { Remote.playFrom(tracks, i, radio); return }
        val newQueue = multiQueue && listName.isNotEmpty() && listName != queueName
        if (multiQueue) startQueue(listName)
        val queued = if (newQueue) emptyList() else queue.drop(index + 1).filter { it.queued }
        queue = tracks.take(i + 1) + queued + tracks.drop(i + 1)
        index = i
        radioFrom = null
        shuffled = false
        if (dj.on) dj = dj.copy(on = false)   // you picked other songs: the DJ stops
        context++
        loadCurrent()
        if (radio) extendRadio(queue[i], keepUntil = i)
    }

    /** Songs are played from the list called name: if it isn't the queue you're in, that one is kept to come back to. */
    private fun startQueue(name0: String) {
        val name = name0.ifEmpty { queueName.ifEmpty { "Queue" } }
        if (name == queueName && queueId.isNotEmpty()) return
        if (queue.isNotEmpty() && index >= 0 && queueName.isNotEmpty()) {
            val keep = SavedQueue(queueId.ifEmpty { randomHex(6) }, queueName, queue.map { it.copy(queued = false) }, index, livePosition(), now())
            queues = (listOf(keep) + queues.filter { it.id != keep.id && it.name != keep.name }).take(MAX_QUEUES)
        }
        queueId = queues.firstOrNull { it.name == name }?.id ?: randomHex(6)
        queues = queues.filter { it.name != name }
        queueName = name
    }

    /** Go back to one of your other queues, where you left it. */
    fun switchQueue(id: String) {
        val q = queues.firstOrNull { it.id == id } ?: return
        if (Remote.active) { Remote.playFrom(q.tracks, q.index.coerceIn(0, maxOf(0, q.tracks.size - 1)), false); return }
        if (queue.isNotEmpty() && index >= 0) {
            val keep = SavedQueue(queueId.ifEmpty { randomHex(6) }, queueName.ifEmpty { "Queue" }, queue.map { it.copy(queued = false) }, index, livePosition(), now())
            // The queue you leave takes the place of the one you go to: the order you put them in stays.
            queues = queues.filter { it.id != keep.id }.map { if (it.id == id) keep else it }
        } else queues = queues.filter { it.id != id }
        queue = q.tracks
        index = q.index.coerceIn(0, maxOf(0, q.tracks.size - 1))
        queueName = q.name
        queueId = q.id
        radioFrom = null
        shuffled = false
        if (dj.on) dj = dj.copy(on = false)
        context++
        resumeAt = q.position
        loadCurrent()
        toast("Back to “${q.name}”")
    }

    /** Move one of your other queues up (-1) or down (+1). */
    fun moveQueue(id: String, step: Int) {
        val l = queues.toMutableList()
        val k = l.indexOfFirst { it.id == id }
        if (k < 0 || k + step !in l.indices) return
        l[k] = l[k + step].also { l[k + step] = l[k] }
        queues = l
        saveState()
    }

    fun renameQueue(id: String, name: String) {
        val n = name.trim().ifEmpty { return }
        if (id == queueId || id.isEmpty()) queueName = n else queues = queues.map { if (it.id == id) it.copy(name = n) else it }
        saveState()
    }

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

    fun deleteQueue(id: String) { queues = queues.filter { it.id != id }; saveState() }
    fun removeOtherQueues() { queues = emptyList(); saveState(); toast("Only this queue is left") }
    @JvmName("chooseMultiQueue")
    fun setMultiQueue(on: Boolean) { multiQueue = on; saveState() }

    /** "next" plays the songs right after this one, "queue" after the songs you queued before. */
    fun enqueue(tracks: List<Track>, how: String = "queue") {
        if (Remote.active) { Remote.enqueue(tracks, how); return }
        val ts = tracks.map { it.copy(queued = true) }
        if (ts.isEmpty()) return
        if (current() == null) { playFrom(ts, 0, false); return }
        var pos = index + 1
        if (how != "next") while (pos < queue.size && queue[pos].queued) pos++
        queue = queue.take(pos) + ts + queue.drop(pos)
        afterQueueChange()
        val many = if (ts.size > 1) "${ts.size} songs" else "“${ts[0].title}”"
        toast(if (ts.size > 1) "$many ${if (how == "next") "play next" else "added to the queue"}"
              else "${if (how == "next") "Playing next" else "Added to the queue"}: $many")
        if (ended) next()
    }

    fun unqueueMany(indices: Collection<Int>) {
        if (Remote.active) { Remote.cmd("unqueue", "indices" to indices.toList()); return }
        val drop = indices.filter { it in queue.indices && it != index }.toSet()
        val before = drop.count { it < index }
        queue = queue.filterIndexed { i, _ -> i !in drop }
        index -= before
        afterQueueChange()
    }

    private fun radioTracks(t: Track): List<Track> {
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

    private fun extendRadio(t: Track, keepUntil: Int? = null) {
        radioFrom = t.id
        val ctxNow = context
        bg({ radioTracks(t) }) { res, err ->
            if (ctxNow != context || err != null || res.isNullOrEmpty()) { if (ended) doneAdvancing(); return@bg }
            var q = queue
            if (keepUntil != null) {
                // A song picked from results: its radio replaces the rest of the results (not your queue).
                val cut = maxOf(keepUntil, index)
                q = q.take(cut + 1) + q.drop(cut + 1).filter { it.queued }
            }
            val ids = q.map { it.id }.toMutableSet()
            val songs = q.map { songKey(it) }.toMutableSet()
            q = q + res.filter { ids.add(it.id) && songs.add(songKey(it)) }
            queue = q
            afterQueueChange()
            if (ended && index + 1 < queue.size) next()   // it had stopped at the end: on it goes
            else if (ended) doneAdvancing()
        }
    }

    /** ∞: a couple of songs before the end, songs like the last one, so there's no gap. */
    private fun autoplaySoon() {
        if (autoplay && repeat == "off" && queue.isNotEmpty() && queue.size - index <= 2 && radioFrom != queue.last().id)
            extendRadio(queue.last())
    }

    @JvmName("chooseAutoplay")
    fun setAutoplay(on: Boolean) { autoplay = on; saveState(); if (on) autoplaySoon() }

    /** The queue's Clear: the songs you queued by hand go (what plays on from the list stays). */
    fun clearQueued() {
        if (Remote.active) { toast("Clear the queue on the desktop"); return }
        queue = queue.take(index + 1) + queue.drop(index + 1).filter { !it.queued }
        afterQueueChange()
    }

    /** The song at i, dragged to j (both after the one playing). */
    fun moveInQueue(i: Int, j: Int) {
        if (Remote.active || i == j || i !in queue.indices || j !in queue.indices || i <= index || j <= index) return
        val q = queue.toMutableList()
        val t = q.removeAt(i)
        q.add(j, t)
        queue = q
        afterQueueChange()
    }

    fun playIndex(i: Int) {
        if (Remote.active) { Remote.cmd("play_index", "index" to i); return }
        if (i in queue.indices) { index = i; loadCurrent() }
    }

    fun next() {
        if (Remote.active) { Remote.cmd("next"); return }
        when {
            index + 1 < queue.size -> { index++; loadCurrent(); autoplaySoon() }
            repeat == "all" && queue.isNotEmpty() -> {
                if (shuffled) { queue = shuffleSongs(queue, after = current()); afterQueueChange() }   // round again in a new order
                index = 0; loadCurrent()
            }
            repeat == "off" && nextQueue() != null -> switchQueue(nextQueue()!!.id)   // this queue is done: your next one goes on
            else -> {
                ended = true
                val more = autoplay && repeat == "off" && current() != null
                if (more && advancing) extendRadio(current()!!)   // ∞: songs like it, and on it goes (it stays "playing" meanwhile)
                else {
                    doneAdvancing(); player?.pause()
                    if (more) current()?.let { extendRadio(it) }
                }
            }
        }
    }

    /** The next of your other queues with songs still to play (the last one used first), or null. */
    fun nextQueue(): SavedQueue? = if (!multiQueue) null else queues.firstOrNull { it.id != queueId && it.index < it.tracks.size - 1 }

    fun previous() {
        if (Remote.active) { Remote.cmd("prev"); return }
        if (position >= 10 || index <= 0) {   // ⏮: the start of the song; in its first 10 seconds, the song before
            player?.seekTo(0)
            val t = current()
            if (t != null && listen?.let { it.sent || it.counted } == true) beginListen(t)   // listening again counts again
        } else { index--; goingBack = true; loadCurrent() }
    }

    /** The song playing is about to change: count it as skipped, unless it ran out on its own or you went back. */
    private fun noteLeaving() {
        val ls = listen
        val fin = finishing; val back = goingBack
        finishing = false; goingBack = false
        if (ls == null || fin || back) return
        val dur = duration.takeIf { it > 0 } ?: ls.track.duration
        if (ls.played >= 1 && dur > SongStats.SKIP_LEFT * 2 && position < dur - SongStats.SKIP_LEFT) {
            bg({ songStats.skipped(ls.track) }) { _, _ -> musicVersion++ }
        }
    }

    /** A song ran out and the next one is on its way: to the notification (and Android) it's still
     *  playing, so the app stays a foreground music player between songs. Were it "ended" for that
     *  moment, Android would take the app out of the foreground, and with the screen off it can't come
     *  back: a minute later Android closed it (the app "crashing" every couple of songs). */
    @Volatile var advancing = false
        private set

    /** Done moving on (playing the next song, or stopped): the notification gets the player's real state. */
    private fun doneAdvancing() {
        if (!advancing) return
        advancing = false
        val p = player ?: return
        if (p.state == Engine.ENDED) p.pause()   // nothing came: it stops, and Android hears it
    }

    // ---- 2.0: song crossfade ----
    private var fadingId: String? = null
    private var fadeIn = 0.0
    private var fadeJob: kotlinx.coroutines.Job? = null

    private var tail: Engine? = null   // the song ending, ready to carry on fading in a player of its own
    private var tailFor: String? = null

    private fun dropTail() {
        tail?.let { runCatching { it.release() } }
        tail = null; tailFor = null
    }

    /** Crossfade (Equalizer › Crossfade): the last seconds of a song fade out while the next fades in. A few seconds
     *  before, a second player gets the song ending ready, paused at the same place; at the fade it takes over from
     *  the same moment (only once it's really playing does this one move on), so there's no gap: the ending song
     *  gets quieter while the next one comes up from quiet, both at once. Not with bit-perfect, Repeat one, streams,
     *  casting, or (Smart transitions) between songs of one album, which flow into each other. */
    private fun maybeCrossfade(p: Engine) {
        val t = current()
        if (t == null || !Prefs.on(Prefs.CROSSFADE) || bitPerfect || repeat == "one" || Remote.active || cast != null || streaming) {
            if (tail != null && tailFor != fadingId) dropTail()
            return
        }
        if (fadingId == t.id) return
        if (tailFor != null && tailFor != t.id) dropTail()   // moved on (skipped) before it faded
        val n = Prefs.num(Prefs.CROSSFADE_SECS, 6f).toDouble()
        if (duration < n * 3) return
        val nxt = queue.getOrNull(index + 1) ?: return
        if (Prefs.on(Prefs.SMART_TRANSITIONS) && sameAlbum(t, nxt)) return
        val fadeAt = duration - n
        // Get the ending ready early: a player of its own, this song, paused near where the fade starts.
        if (tail == null && position >= fadeAt - 5 && position < fadeAt) {
            val uri = p.currentUri ?: return
            runCatching {
                val x = p.tail(uri, (fadeAt * 1000).toLong()) ?: return
                tail = x; tailFor = t.id
            }
            return
        }
        val x = tail ?: return
        if (position < fadeAt) return
        if (x.state != Engine.READY) {   // not ready in time: no crossfade this once, just the usual change
            if (position > duration - 1) dropTail()
            return
        }
        // Take over: the second player plays on from this moment, at this volume; once it's really playing, this one
        // moves on to the next song (coming up from quiet), and the ending fades out over the same seconds.
        fadingId = t.id
        val from = p.volume.coerceAtLeast(0.05f)
        val left = maxOf(1.0, minOf(n, duration - position))
        x.seekTo(p.positionMs + 40)
        // Silent until it's really playing, then the two swap in one go: before, both played the ending at full
        // volume for the moment the second took to start, a doubled (6 dB louder) blip at the start of every crossfade.
        x.volume = 0f
        x.onceSounding {
            if (fadingId == t.id && current()?.id == t.id) {
                x.volume = from
                player?.volume = 0f   // the second player has it now: no doubled sound while the next one loads
                fadeIn = n
                trackEnded()
                scope.launch {
                    val steps = (left * 20).toInt().coerceAtLeast(1)
                    for (k in 1..steps) {
                        delay(50)
                        x.volume = from * kotlin.math.cos(Math.PI / 2 * k / steps).toFloat()   // an even crossfade: as this goes down…
                    }
                    runCatching { x.release() }
                }
            } else runCatching { x.release() }
        }
        x.play()
        tail = null; tailFor = null
        scope.launch {   // if it never got going, move on anyway
            delay(1500)
            if (fadingId == t.id && current()?.id == t.id && !x.isPlaying) { fadeIn = 0.0; runCatching { x.release() }; trackEnded() }
        }
    }

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
        eqProcessor.balance = Prefs.num(Prefs.BALANCE, 0f).toDouble()
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
        if (key == Prefs.CLIP_GUARD || key == Prefs.DITHER || key == Prefs.LOUDNESS || key == Prefs.DETAIL) applySound()
        if (key == Prefs.MONO) { eqProcessor.mono = on || AutoMono.active; if (on && bitPerfect) toast("Mono isn't heard in bit-perfect: turn bit-perfect off for it") }
    }

    /** Songs of one album, one after the other: Smart transitions leaves them to flow into each other. */
    fun sameAlbum(a: Track, b: Track) = a.album.isNotBlank() && norm(albumEdition(a.album).first) == norm(albumEdition(b.album).first) &&
        artistKey(a.artist) == artistKey(b.artist)

    fun trackEnded() {
        advancing = true
        if (streaming && duration > 0 && position < duration - 5) { streamBroke(); return }   // the network went away mid-song
        current()?.let { t -> bg({ songStats.heardOut(t) }); discovered(t) }
        finishing = true
        if (repeat == "one") loadCurrent() else next()
        if (ended && !(autoplay && repeat == "off")) doneAdvancing()
    }

    fun cycleRepeat() {
        if (Remote.active) { Remote.cmd("repeat"); return }
        repeat = mapOf("off" to "all", "all" to "one", "one" to "off")[repeat]!!
        saveState()
    }

    /** Shuffle what's coming up; the songs you queued stay first, in your order. */
    fun shuffle() {
        if (Remote.active) { Remote.cmd("shuffle"); return }
        val rest = queue.drop(index + 1)
        queue = queue.take(index + 1) + rest.filter { it.queued } + shuffleSongs(rest.filter { !it.queued })
        shuffled = true
        afterQueueChange()
    }

    fun togglePause() {
        if (Remote.active) { Remote.cmd(if (Remote.status.paused) "play" else "pause"); return }
        val p = player ?: return
        if (current() == null) return
        if (p.isPlaying) p.pause() else {
            if (p.state == Engine.IDLE || !p.hasItem) loadCurrent() else p.play()
        }
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

    private fun afterQueueChange() {
        saveState()
        prefetch()
    }

    fun loadCurrent(autoplay: Boolean = true) {
        val t0 = current() ?: return
        val t = t0.copy(queued = false)   // playing now, not waiting in your queue
        if (t0.queued) queue = queue.toMutableList().also { it[index] = t }
        if (t.isFile && !fileHere(t)) { standIn(t, autoplay); return }   // the other device's song file (a backup's)
        if (autoplay) noteLeaving()
        finishing = false; goingBack = false
        restorePending = false
        ended = false
        listen = null
        // The song before this one is done: its lyrics can go into its file now (never into the one playing).
        lastLoaded?.let { prev -> if (prev.id != t.id) lyricsIntoFile(prev) }
        lastLoaded = t
        if (lyricsToFiles && !Remote.active) bg({ Lyrics.get(ctx, t) })   // found while it plays, kept for later
        val seq = ++loadSeq
        val at = resumeAt
        resumeAt = 0.0
        position = at
        duration = t.duration
        val onDisk = lib.has(t.id) || t.isFile
        streaming = !(onDisk || (keepPlayed && autoSaveOk(t)))
        status = if (onDisk) "" else if (keepPlayed) "Downloading FLAC…" else "Loading the stream…"
        val work: () -> Downloader.Ready = if (streaming || onDisk) ({ dl.stream(t) })
            else ({ try { dl.download(t, youtubeToo = false) } catch (_: Exception) {}; dl.stream(t) })   // no FLAC to keep (or saving failed): stream what there is
        bg(work) { ready, err ->
            if (seq != loadSeq) return@bg   // you moved on while it loaded
            status = ""
            if (err != null || ready == null) {
                toast(if (err is NoLosslessMatch) "Skipped “${t.title}”: ${err.message}" else "Couldn't get “${t.title}”: ${err?.message}")
                if (autoplay) scope.launch { delay(1500); if (seq == loadSeq) next() } else { paused = true; doneAdvancing() }
                return@bg
            }
            if (!ready.onDisk) streaming = true   // Keep found no FLAC to save: it streams
            val start = { play(t, ready, autoplay, at) }
            if (player == null) pendingLoad = start else start()
            if (autoplay) beginListen(t)
            savedVersion++
            prefetch()
        }
        saveState()
        // Keep a radio going: near the end of the queue, ask for more like the last song.
        if (radioFrom != null && index >= queue.size - 2) extendRadio(queue.last())
        if (dj.on) {
            djAnnounce(t)
            if (index >= queue.size - 2) djMore()
        }
    }

    private fun play(t: Track, ready: Downloader.Ready, autoplay: Boolean, startAt: Double = 0.0) {
        val p = player ?: return
        val art = lib.items[t.id]?.art?.takeIf { it.isNotEmpty() && cast == null }
            ?: t.thumb.takeIf { it.isNotEmpty() }?.let { sizedThumb(it, 544) }
        // Casting: the device fetches the song itself: a stream as it is, a file of the phone's from here over the network.
        val uri = if (cast != null && !ready.uri.startsWith("http")) PhoneShare.forCast(t, castHost) ?: ready.uri else ready.uri
        val item = PlayItem(uri, t.id, if (ready.isDash) "application/dash+xml" else if (cast != null) castMime(t, uri) else null,
            t.title, t.artist, t.album, art)
        p.load(item, (startAt * 1000).toLong())
        playbackOf(t).let { (sp, pi) -> p.setSpeed(sp, pi) }
        eqProcessor.songGain = gainOf(t)
        applyEq()   // the song's own EQ on this output, or the usual one
        p.playWhenReady = autoplay
        advancing = false   // buffering for real now
        fadeJob?.cancel()
        if (fadeIn > 0) {   // crossfading: this one comes up as the last one fades away
            val n = fadeIn; fadeIn = 0.0
            p.volume = 0f
            fadeJob = scope.launch {
                val steps = (n * 20).toInt().coerceAtLeast(1)
                for (i in 1..steps) { delay(50); p.volume = kotlin.math.sin(Math.PI / 2 * i / steps).toFloat() }   // …the next comes up
                p.volume = 1f
            }
        } else p.volume = 1f
    }

    private fun prefetch() {
        val nxt = queue.getOrNull(index + 1) ?: return
        if (lib.has(nxt.id) || nxt.isFile) return
        if (keepPlayed && autoSaveOk(nxt)) bg({ dl.download(nxt, youtubeToo = false) }) { _, _ -> savedVersion++ } else bg({ dl.stream(nxt) })
    }

    /** Tidal's stream stopped short: look it up again and carry on from the same place, once per song. */
    fun streamBroke() {
        val t = current() ?: return
        if (!streaming) return
        val seq = loadSeq
        val pos = position
        val wasPlaying = player?.playWhenReady == true
        if (streamRetry == seq) {
            toast("Lost the stream of “${t.title}”")
            if (wasPlaying) next()
            return
        }
        streamRetry = seq
        dl.forgetStream(t.id)
        bg({ dl.stream(t) }) { ready, err ->
            if (seq != loadSeq) return@bg
            if (err != null || ready == null) { toast("Couldn't get “${t.title}” back: ${err?.message}"); return@bg }
            play(t, ready, wasPlaying, pos)
        }
    }

    // ---- the equalizer ----
    fun setEq(f: (EqSettings) -> EqSettings) {
        eq = f(eq)
        applyEq()
        saveState()
    }

    /** The playing song's own EQ on this output (Custom EQ…), if it has one: then it's what's heard. */
    fun songEqNow(t: Track? = current()): OutputEq? = t?.let { if (::songEqs.isInitialized) songEqs.get(it, outputNow.key) else null }
    var songEqActive by mutableStateOf(false)   // the playing song's own EQ is in effect (Now playing's C EQ, red)
        private set

    /** What the EQ plays: this output's, or the playing song's own for this output (its correction, Sound and Boost; the
     *  bass and treble, crossfeed, dither and the rest stay as they are). */
    private fun applyEq() {
        val s = songEqNow()
        songEqActive = s != null
        eqProcessor.update(if (s == null) eq else eq.copy(on = true, headphones = s.headphones, preset = s.preset, bands = s.bands,
            custom = s.custom, count = s.count, boost = s.boost))
    }

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
            if (eq.devices[dev] == null || eq.headphones == "off") setEqHeadphones(keys[0])
            toast("${got[0].first.name}: ${got.size} correction${if (got.size != 1) "s" else ""} collected (" +
                got.joinToString(", ") { it.first.source.ifEmpty { "AutoEq" } } + ")")
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

    /** The sound goes somewhere else: use the correction you used there last time, or say AutoEq has one. */
    fun outputChanged() {
        switchEqOutput()
        val dev = AutoEq.output(ctx)
        if (dev == output) return
        output = dev
        if (dev.isEmpty()) return
        val known = eq.devices[dev]
        if (known != null && known in Eq.headphones(eq)) {
            if (known != eq.headphones) { setEqHeadphones(known, remember = false); toast("$dev: using ${Eq.headphones(eq)[known]?.first}") }
            return
        }
        // New headphones: every correction AutoEq has for them, collected (the closest one used)
        collectCorrections(dev, quiet = true)
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
            val saved = e.outputs + (e.output.ifEmpty { "speaker" } to e.toOutput(e.outputs[e.output]?.label ?: outLabel(e.output)))
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
            val saved = (if (was.isNotEmpty()) e.outputs + (was to e.toOutput(e.outputs[was]?.label ?: outLabel(was))) else e.outputs)
            val mine = saved[o.key] ?: OutputEq(o.label, count = e.count, bands = List(e.count) { 0.0 }, custom = List(e.count) { 0.0 })
            e.copy(outputs = saved + (o.key to mine.copy(label = o.label))).withOutput(o.key, mine)
        }
        if (isNew && o.key == CAR) setEqPreset("colorado")   // the Chevy starts on the Colorado's tuning
        if (isNew) addDevicePreset(o)   // its own Sound preset to start from
        if (was.isNotEmpty()) toast("${o.label}: its own equalizer" + if (eq.on) "" else " (off)")
    }
    /** Boost: dB louder than normal (0-12) on everything, kept for this output. */
    fun setEqBoost(db: Double) = setEq { it.copy(boost = (kotlin.math.round(db.coerceIn(0.0, Eq.BOOST_MAX) * 2) / 2)) }

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
