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

    internal fun castMime(t: Track, uri: String): String = Casting.mime(t, uri)

    /** A cast device connected: what's playing goes there, from where it was. */
    fun castStarted(p: Engine, name: String) = Casting.started(p, name)

    /** Back to this phone, where the cast left off. */
    fun castStopped() = Casting.stopped()

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
    var artistSort by mutableStateOf("album")   // an artist's page: their songs by album (unless you pick another: kept), most played, least, title, newest
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
    fun replayForBitPerfect() = SoundSwitches.replayForBitPerfect()

    fun chooseBitPerfect(on: Boolean) = SoundSwitches.chooseBitPerfect(on)

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

    /** Your files to put in Android's trash (it asks you), and the songs that take their places. */
    data class TrashJob(val tracks: List<Track>, val replacements: Map<String, Track> = emptyMap(), val what: String = "")
    internal var searchBeforeLocal = "tidal"     // where the search looked before
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
    internal val likes = HashMap<String, Set<String>>()   // each service's liked songs (MusicServices fills it)

    var downloadNote by mutableStateOf("")   // "Downloading in FLAC · 3 of 40" while a Download runs
        internal set   // Downloads' to write


    fun init(c: Ctx) {
        if (::ctx.isInitialized) return
        ctx = appCtx(c)
        Prefs.init(ctx)
        org.omarchy.music.ui.MyLooks.hook()   // the looks you made, known to the Look menu before the saved look is checked
        bg({ SoundCloud(ctx) }) { src, err -> sourceReady(SoundCloud.KEY, src, err) }   // its own service: streams only
        // The theme (Settings › Personalization › Theme) is read from the settings, as Compose state, so a change redraws
        org.omarchy.music.ui.Themes.source = { Prefs.text(org.omarchy.music.ui.Themes.PREF, org.omarchy.music.ui.Themes.STANDARD) }
        loadDownloadQuality()
        artistSort = Prefs.text("artist_sort", "album").takeIf { it in setOf("album", "most", "least", "title", "added") } ?: "album"
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
        Remembered.load()
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
    // What the app remembers between starts (state.json: read at the start, written at each change) is Remembered's own: these hand on.
    var restoring = false   // a backup is going back in: nothing saved over it

    /** The state written now, not in the background (a backup is being made of it). */
    fun saveStateNow() = Remembered.saveNow()
    fun saveState() = Remembered.save()

    fun saveApiKeys(keys: Map<String, String>) {
        apiKeys = keys.mapValues { it.value.trim() }.filter { it.key in API_KEYS && it.value.isNotEmpty() }
        saveState()
    }

    /** Called once the playback service is up: a song restored from Tidal can load now. */
    fun playerReady() = PlaybackCore.playerReady()

    // ---- your music ----
    // Your music as the tabs show it (by album, genre, folder, artist; which tab a release is in) is YourMusic's own: these hand on.
    /** Every song you have: downloaded here (once each) and in your folders. */
    fun mySongs(): List<Track> = YourMusic.songs()
    fun albums(): List<Pair<Album, List<Track>>> = YourMusic.albums()
    /** Which tab a release of yours is in: "album", "ep" or "single". */
    fun releaseKind(title: String, songs: List<Track>, single: Boolean = false, byCount: Boolean = true): String = YourMusic.releaseKind(title, songs, single)
    fun tabKey(t: Track): String = YourMusic.tabKey(t)
    fun tabOf(t: Track): String? = YourMusic.tabOf(t)
    fun setTab(tracks: List<Track>, kind: String?) = YourMusic.setTab(tracks, kind)
    fun genreOf(t: Track): String = YourMusic.genreOf(t)
    internal fun noteGenre(file: String, g: String) = YourMusic.noteGenre(file, g)
    fun genres(): List<Pair<String, List<Track>>> = YourMusic.genres()
    fun folderOf(t: Track): String = YourMusic.folderOf(t)
    fun folderSongs(): List<Pair<String, List<Track>>> = YourMusic.folderSongs()
    fun songsBy(name: String): List<Track> = YourMusic.songsBy(name)
    fun artistSongs(name: String): List<Track> = YourMusic.artistSongs(name)

    var artistsVersion by mutableStateOf(0)   // bumps when an artist's picture or bio comes in
        internal set   // ArtistLookups' to bump

    // Looking artists up (pictures, bios, an artist's page) is ArtistLookups' own: these hand on.
    fun lookUpArtists(names: List<String>) = ArtistLookups.lookUp(names)
    fun prunePictures() = ArtistLookups.prunePictures()
    fun setArtistPicture(name: String, source: String) = ArtistLookups.setPicture(name, source)
    /** (blocking) The artist's page from the first service that knows them. */
    fun artistInfo(name: String, prefer: String?): ArtistInfo? = ArtistLookups.info(name, prefer)

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
    fun dupeKey(g: List<Track>) = StatPicks.dupeKey(g)
    fun markNotDupe(g: List<Track>) = StatPicks.markNotDupe(g)
    var singlesKept: Set<String> = emptySet()   // Singles › Single scan: songs left alone (their files)
    // Your picks in the stats (what they do when you make one) are StatPicks' own: these hand on. The picks are kept here.
    fun keepSingles(more: Collection<String>) = StatPicks.keepSingles(more)
    fun addSongPicks(more: Map<String, String>) = StatPicks.addSongPicks(more)
    fun addAlbumPicks(more: Map<String, String>) = StatPicks.addAlbumPicks(more)
    fun keepApart(from: String, to: String) = StatPicks.keepApart(from, to)
    fun setArtistPick(credit: String, name: String?) = StatPicks.setArtistPick(credit, name)
    fun mergeArtist(name: String, to: String) = StatPicks.mergeArtist(name, to)

    /** artistResolver for your songs and your Joint artists picks, kept until either changes. */
    fun artistResolver(): (String) -> String = StatPicks.artistResolver()
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

    // ---- an artist's own songs and albums; Create artist playlist ----
    /** Prefs key: Settings › Your music › Artist playlists include songs you don't have. */
    const val ARTIST_LISTS_ALL = "artist_playlist_all"
    /** Prefs key: an artist page's songs, "all" (streaming and yours) or "mine" (only your downloads and files). */
    const val ARTIST_PAGE_FROM = "artist_page_from"

    // What is an artist's among your music, and Create artist playlist, are ArtistSongs' own: these hand on.
    fun creditsArtist(t: Track, name: String): Boolean = ArtistSongs.credits(t, name)
    fun artistOwnSongs(name: String): List<Track> = ArtistSongs.ownSongs(name)
    fun artistOwnAlbums(name: String): List<Pair<Album, List<Track>>> = ArtistSongs.ownAlbums(name)
    fun makeArtistPlaylist(name0: String) = ArtistSongs.makePlaylist(name0)

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

    /** Play from the headphones, the car, the lock screen or the notification: what the app's own Play does, so the DJ
     *  is never left stuck (talking that never came, a set it was still picking, a set it hadn't introduced yet). */
    fun sessionPlay() = PlayButtons.play()

    /** The DJ stops talking (Play, Pause, a skip): the music goes on. */
    fun djHush() = DjDirector.djHush()

    /** Now playing's set button: on to the DJ's next set now, skipping the rest of this one. */
    fun djNextSet() = DjDirector.djNextSet()

    /** Switch it up (or play what you asked for): a new set now, in place of the rest of this one. */
    fun djSwitch(ask: String? = null) = DjDirector.djSwitch(ask)

    // ---- importing an .m3u8 playlist ----
    // Reading an .m3u8, finding its songs and making the playlist are PlaylistImport's own: these hand on.
    fun importM3u(uri: String): Triple<String, List<Track>, List<Missed>> = PlaylistImport.importM3u(uri)

    /** What an import didn't find, into the playlist id (see ImportReportDialog). */
    data class ImportReport(val id: String, val name: String, val missed: List<Missed>)

    fun matchSong(src: Source, artist: String, title: String, seconds: Int): Pair<Track?, List<Track>> = PlaylistImport.matchSong(src, artist, title, seconds)
    fun reportSources(): List<Pair<String, String>> = PlaylistImport.reportSources()
    fun placeMissed(id: String, m: Missed, t: Track, placed: MutableList<Missed>): Boolean = PlaylistImport.placeMissed(id, m, t, placed)
    fun importPlaylist(uri: String) = PlaylistImport.importPlaylist(uri)

    // ---- your music folders: YourFolders ----
    fun rescan() = YourFolders.rescan()
    fun rescanSoon() = YourFolders.rescanSoon()
    @JvmName("chooseFolders")
    fun setFolders(list: List<String>) = YourFolders.choose(list)
    fun removeFolder(path: String) = YourFolders.remove(path)
    fun fileHere(t: Track): Boolean = YourFolders.fileHere(t)
    fun fileGone(): Boolean = YourFolders.fileGone()
    fun versionReplaced(old: Track, new: Track) = YourFolders.versionReplaced(old, new)
    fun hereCopy(t: Track): Track? = YourFolders.hereCopy(t)
    internal fun standIn(t: Track, autoplay: Boolean) = YourFolders.standIn(t, autoplay)
    fun filesMoved(moved: Map<String, String>) = YourFolders.filesMoved(moved)

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
    // Where a new release is on the streaming services is ReleaseLookup's own: these hand on.
    fun releaseFind(r: NewReleases.Release): Triple<String, Album?, List<Track>>? = ReleaseLookup.find(r)
    fun releaseTracks(r: NewReleases.Release): List<Track> = ReleaseLookup.tracks(r)

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
    fun upgradeSongs(tracks: List<Track>? = null, force: Boolean = false) = Upgrades.songs(tracks, force)


    // ---- favorites, a song's volume, moods by hand, looks ----
    // A favorite and a song's own volume are SongMarks' own: these hand on.
    fun setFavorite(tracks: List<Track>, on: Boolean? = null) = SongMarks.favorite(tracks, on)
    fun setGain(tracks: List<Track>, db: Double, output: String? = outputNow.key) = SongMarks.gain(tracks, db, output)
    fun refreshSongVolume() = SongMarks.refreshVolume()
    fun gainNow(t: Track): Double = SongMarks.gainNow(t)
    internal fun gainOf(t: Track) = SongMarks.gainOf(t)



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
    // Downloads' Upgrade (the ones from YouTube got again from Tidal) and Refresh are Upgrades' own: these hand on.
    fun youtubeDownloads() = Upgrades.youtubeDownloads()
    var flacing by mutableStateOf<String?>(null)
        internal set   // Upgrades' to write
    fun flacDownloads() = Upgrades.toFlac()

    fun refreshDownloads() = Upgrades.refresh()

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
    fun exportM3u8(uri: String, tracks: List<Track>) = YourPlaylists.exportM3u8(uri, tracks)


    // A cover picked by hand (and the ones waiting for the song playing to end) is TagEdits' own: these hand on.
    internal fun waitingCoversSoon(t: Track) = TagEdits.waitingCoversSoon(t)
    fun setCover(tracks: List<Track>, source: String, wholeAlbum: Boolean = true, done: () -> Unit = {}) = TagEdits.setCover(tracks, source, wholeAlbum, done)

    internal fun playbackOf(t: Track): Pair<Float, Float> = SongMarks.playback(t)

    /** Play tracks at speed from now on (the playing one too, at once). */
    fun setSpeed(tracks: List<Track>, speed: Double, pitch: Boolean) = SongMarks.speed(tracks, speed, pitch)

    // Tag changes by hand (and the ones waiting for the song playing to end) are TagEdits' own: these hand on.
    internal fun heldTagsSoon() = TagEdits.heldTagsSoon()
    fun editTags(tracks: List<Track>, changes: Map<String, String>, renameFiles: Boolean = false) = TagEdits.editTags(tracks, changes, renameFiles)

    // Deleting songs (the app's downloads at once, your own files once you say yes) is DeletingSongs' own: these hand on.
    fun phoneFile(t: Track): String? = DeletingSongs.ownFile(t)
    fun deletable(t: Track) = DeletingSongs.deletable(t)
    fun deleteDownloads(tracks: List<Track>) = DeletingSongs.downloads(tracks)
    fun phoneFilesDeleted(tracks: List<Track>) = DeletingSongs.filesDeleted(tracks)
    fun removeDownload(id: String) = DeletingSongs.download(id)

    @JvmName("chooseKeepPlayed")
    fun setKeepPlayed(on: Boolean) { keepPlayed = on; saveState() }

    // ---- services ----
    @JvmName("chooseSource")
    fun setSource(key: String) { source = key; saveState() }

    // Which service you browse, a service coming ready, and the library sync are MusicServices' own: these hand on.
    fun browse(key: String) = MusicServices.browse(key)

    @JvmName("chooseSearchIn")
    fun setSearchIn(key: String) { searchIn = key; saveState() }

    fun setSyncSource(key: String, on: Boolean) = MusicServices.setSync(key, on)
    fun sourceReady(key: String, src: Source?, err: Throwable?) = MusicServices.ready(key, src, err)
    fun tidalLoggedIn(redirect: String) = MusicServices.tidalLoggedIn(redirect)
    fun tidalDisconnect() = MusicServices.tidalDisconnect()
    fun syncLibrary() = MusicServices.syncLibrary()

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
    fun lyricsIntoFile(t: Track, force: Boolean = false) = TagEdits.lyricsIntoFile(t, force)

    fun deleteQueue(id: String) = PlaybackCore.deleteQueue(id)
    fun removeOtherQueues() = PlaybackCore.removeOtherQueues()
    @JvmName("chooseMultiQueue")
    fun setMultiQueue(on: Boolean) = PlaybackCore.chooseMultiQueue(on)

    /** "next" plays the songs right after this one, "queue" after the songs you queued before. */
    fun enqueue(tracks: List<Track>, how: String = "queue") = PlaybackCore.enqueue(tracks, how)

    fun unqueueMany(indices: Collection<Int>) = PlaybackCore.unqueueMany(indices)

    internal fun radioTracks(t: Track): List<Track> = SongRadio.tracks(t)

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
    fun sessionNext() = PlayButtons.next()


    fun next() = PlaybackCore.next()

    /** The next of your other queues with songs still to play (the last one used first), or null. */
    fun nextQueue(): SavedQueue? = PlaybackCore.nextQueue()

    fun previous() = PlaybackCore.previous()

    /** A song ran out and the next one is on its way: to the notification (and Android) it's still
     *  playing, so the app stays a foreground music player between songs. Were it "ended" for that
     *  moment, Android would take the app out of the foreground, and with the screen off it can't come
     *  back: a minute later Android closed it (the app "crashing" every couple of songs). */
    var advancing: Boolean get() = PlaybackCore.advancing; private set(v) { PlaybackCore.advancing = v }

    fun shuffleWeight(): ((Track) -> Double)? = PlaySorts.shuffleWeight()

    /** Settings › Sound's switches and numbers into the EQ processor. */
    fun applySound() = SoundSwitches.apply()

    fun setSoundNum(key: String, v: Float) = SoundSwitches.setNum(key, v)

    /** One of 2.0's switches (Prefs), with what it takes to take effect. */
    fun setOpt(key: String, on: Boolean) = SoundSwitches.setOpt(key, on)

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

    fun togglePause() = PlayButtons.togglePause()

    fun seek(seconds: Double) = PlayButtons.seek(seconds)

    fun toggleLike() = PlayButtons.toggleLike()

    fun loadCurrent(autoplay: Boolean = true) = PlaybackCore.loadCurrent(autoplay)

    /** The cover the lock screen, the notification, Android Auto and Windows' media controls show (NowArt's own). */
    internal fun nowArt(t: Track): String? = NowArt.of(t)

    /** Tidal's stream stopped short: look it up again and carry on from the same place, once per song. */
    fun streamBroke() = PlaybackCore.streamBroke()

    // ---- the equalizer ----
    // What the equalizer plays, each output's settings and the switch between outputs are EqControl's own: these
    // hand on. The settings (eq) and what the screens read (the output now, a song's own EQ in effect) stay here.
    fun setEq(f: (EqSettings) -> EqSettings) = EqControl.set(f)
    fun songEqNow(t: Track? = current()): OutputEq? = EqControl.songEqNow(t)
    var songEqActive by mutableStateOf(false)   // the playing song's own EQ is in effect (Now playing's C EQ, red)
        internal set
    internal val songEqsReady get() = ::songEqs.isInitialized
    internal fun applyEq() = EqControl.apply()
    fun dualVolume() = EqControl.dualVolume()
    fun setDualVolume(on: Boolean) = EqControl.setDualVolume(on)
    fun setSongEq(tracks: List<Track>, e: OutputEq?) = EqControl.setSongEq(tracks, e)
    fun setEqOn(on: Boolean) = EqControl.setOn(on)
    fun setEqHeadphones(key: String, remember: Boolean = true) = EqControl.setHeadphones(key, remember)
    fun importEqFile(uri: String) = SoundPresets.importEqFile(uri)
    fun rgFactor(db: Double, peak: Double? = null): Double = EqControl.rgFactor(db, peak)

    // Sound presets of yours and headphones corrections (saved, imported, exported, collected) are SoundPresets' own.
    fun savePreset(name: String, bands: List<Double> = eq.bands, count: Int = eq.count) = SoundPresets.savePreset(name, bands, count)
    fun deletePreset(key: String) = SoundPresets.deletePreset(key)
    fun importPresetFile(uri: String) = SoundPresets.importPresetFile(uri)
    fun exportPreset(uri: String) = SoundPresets.exportPreset(uri)
    fun exportCorrection(uri: String) = SoundPresets.exportCorrection(uri)
    fun collectCorrections(dev: String, quiet: Boolean = false, done: (Int) -> Unit = {}) = SoundPresets.collectCorrections(dev, quiet, done)
    fun addAutoEq(e: AutoEq.Entry, filters: List<Filter>) = SoundPresets.addAutoEq(e, filters)

    var output by mutableStateOf("")   // the headphones the sound goes to now ("" for the phone's own speaker)
        internal set
    fun outputChanged() = EqControl.outputChanged()
    fun setEqPreset(key: String) = EqControl.setPreset(key)
    fun setEqBand(i: Int, gain: Double) = EqControl.setBand(i, gain)
    fun setEqCount(n: Int) = EqControl.setCount(n)
    fun setEqPerOutput(on: Boolean) = EqControl.setPerOutput(on)
    fun editEqOutput(key: String) = EqControl.editOutput(key)
    /** The Chevy (Android Auto, wired or wireless): always in the Equalizer's list on the phone, to set up any time. */
    const val CAR = "car:Chevrolet"
    fun forgetEqOutput(key: String) = EqControl.forgetOutput(key)
    fun outLabel(key: String) = EqControl.outLabel(key)
    var outputNow by mutableStateOf(Outputs.Out("speaker", "Built-in speaker", "speaker"))
        internal set
    fun setEqBoost(db: Double) = EqControl.setBoost(db)
    /** Bit-perfect, but the equalizer's Bass and Treble heard anyway (Equalizer): the rest stays untouched. */
    var bpTones by mutableStateOf(false)
    fun chooseBpTones(on: Boolean) = EqControl.chooseBpTones(on)
    fun bpTonesNow() = EqControl.bpTonesNow()
    fun setEqTone(name: String, gain: Double) = EqControl.setTone(name, gain)

    @JvmName("chooseArtColors")
    fun setArtColors(on: Boolean) { artColors = on; saveState() }
    @JvmName("chooseLyricsMode")
    fun setLyricsMode(mode: String) { lyricsMode = mode; saveState() }
    @JvmName("chooseMusicSort")
    fun setMusicSort(sort: String) { musicSort = sort; saveState() }
    fun pickPlaylistSort(sort: String) { playlistSort = sort; saveState() }

    // Sorting by how you listen (each tab's sort, play counts, Smart shuffle's weights) is PlaySorts' own: these hand on.
    fun sortOf(tab: String, default: String, allowed: List<String>) = PlaySorts.of(tab, default, allowed)
    fun setSort(tab: String, sort: String) = PlaySorts.set(tab, sort)
    fun sortData(sort: String): SortData = PlaySorts.data(sort)
    fun playCounts(): Map<Pair<String, String>, Int> = PlaySorts.counts()
    fun sortedPlaylist(tracks: List<Track>) = PlaySorts.playlist(tracks)
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
