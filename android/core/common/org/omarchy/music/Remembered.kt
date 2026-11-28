package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

/** What the app remembers between starts (state.json): the queue and where you were in it, and the choices made in
 *  the app (the look, the sorts, the picks in the stats, the folders…). Read once at the start, written whenever one
 *  of them changes. The values themselves are [Music]'s, which the screens read; [Music] keeps saveState and hands on. */
object Remembered {
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
        val dj_new: Boolean = true, val top_mine_only: Boolean = false,   // (Most played's "Only songs on this device", taken out in 5.3.1: kept so older files still read)
        val top_period: String = "all",
        val top_kind: String = "songs", val collage_size: Int = 25, val artist_picks: Map<String, String> = emptyMap(),
        val similar_kept: List<String> = emptyList(), val album_picks: Map<String, String> = emptyMap(),
        val song_picks: Map<String, String> = emptyMap(), val not_dupes: List<String> = emptyList(),
        val tab_sorts: Map<String, String> = emptyMap(), val tab_picks: Map<String, String> = emptyMap(),
    )

    internal val file get() = File(Music.ctx.filesDir, "state.json")

    /** What was kept, put back (once, at the start). Anything the file holds that the app no longer knows becomes
     *  its usual choice. */
    internal fun load() = with(Music) {
        val st = readText(file)?.let { runCatching { JSON.decodeFromString(State.serializer(), it) }.getOrNull() } ?: State()
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
        tabPicks.clear(); tabPicks.putAll(RememberedRules.tabPicks(st.tab_picks))
        browseLocal = st.browse_local
        searchBeforeLocal = st.search_before_local.takeIf { it in SEARCH_IN && it != "local" } ?: "tidal"
        // The Apple Music look came with 1.3: everyone starts on it (dark if the phone is), and can pick any other after.
        // 1.4 keeps Light, Dark and Now playing: any other look becomes Light or Dark, as the phone is.
        look = st.look.takeIf { st.apple_ui && org.omarchy.music.ui.Looks.known(it) } ?: "nowplaying"   // 2.0: a first start is on Now playing
        npFallback = st.np_fallback.takeIf { org.omarchy.music.ui.Looks.knownFallback(it) } ?: "apple-dark"
        drawerFolds.clear(); drawerFolds.putAll(st.drawer_folds)
        pins.clear(); pins.addAll(st.pins)
        micAsked = st.mic_asked
        skipPeriod = RememberedRules.period(st.skip_period)
        bitPerfect = st.bit_perfect
        djNew = st.dj_new
        topPeriod = RememberedRules.period(st.top_period)
        topKind = RememberedRules.topKind(st.top_kind)
        collageSize = RememberedRules.collage(st.collage_size)
        artistPicks = st.artist_picks
        similarKept = st.similar_kept
        albumPicks = st.album_picks
        songPicks = st.song_picks
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
        skipOrder = RememberedRules.skipOrder(st.skip_order)
        downloadDir = st.download_dir
        // 3.0.1: downloads that went straight into Music/ go to Music/Artists/ from now on (once; pick another after)
        if (!Prefs.on("downloads_to_artists")) {
            Prefs.set("downloads_to_artists", true)
            if (RememberedRules.musicItself(downloadDir)) {
                downloadDir = ""
                scope.launch(Dispatchers.Main) { save() }   // kept: the state file says Music/Artists/ from now on
            }
        }
        dupeAction = RememberedRules.dupeAction(st.dupe_action)
        eq = st.eq
        applyEq()
        lyricsToFiles = st.lyrics_to_files
        PlaybackCore.restored()   // the song from last time into the player (not playing), or waiting for the player and the login
    }

    /** Written now, not in the background (a backup is being made of it). */
    fun saveNow() { if (!Music.restoring) writeAtomic(file, JSON.encodeToString(State.serializer(), snapshot())) }

    /** Written in the background: what it holds is taken now, on the main thread. Not while a backup goes back in. */
    fun save() {
        if (Music.restoring) return
        val st = snapshot()
        Music.bg({ writeAtomic(file, JSON.encodeToString(State.serializer(), st)) })
    }

    private fun snapshot(): State = with(Music) {
        val q = PlaybackCore.saved()   // the queue's part
        State(q.queue, q.index, q.radioFrom, q.repeat, eq, source, syncSources.toMap(), lyricsMode, folders, artColors, keepPlayed, musicSort, searchIn, dj, apiKeys, q.shuffled, playlistSort,
            browseLocal, searchBeforeLocal, look, visPreset, wrappedStyle, skipOrder, downloadDir, dupeAction,
            q.queues, q.queueName, q.queueId, q.multiQueue, true, lyricsToFiles, true, autoplay, npFallback, drawerFolds.toMap(), pins.toList(), layouts.toMap(), micAsked, skipPeriod,
            bitPerfect, replayGain, djNew, false, topPeriod, topKind, collageSize, artistPicks, similarKept, albumPicks, songPicks, notDupes.toList(), tabSorts.toMap(), tabPicks.toMap())
    }
}
