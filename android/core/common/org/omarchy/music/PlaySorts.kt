package org.omarchy.music

/** Sorting by how you listen: each tab's sort, how often you played each song (the stats and the plays here), a
 *  playlist's songs as its sort says, and Smart shuffle's weights. [Music] keeps the names the screens call and
 *  hands on. */
object PlaySorts {
    /** Each tab's sort (its Sort menu), kept: tab -> a key of SORT_NAMES. */
    fun of(tab: String, default: String, allowed: List<String>): String = pick(Music.tabSorts[tab], default, allowed)
    fun set(tab: String, sort: String) { Music.tabSorts[tab] = sort; Music.saveState() }

    /** The sort a tab uses: the one kept for it when the tab allows it, else the tab's usual one. */
    fun pick(kept: String?, default: String, allowed: List<String>): String = kept?.takeIf { it in allowed } ?: default

    /** (blocking) What sorting by plays needs: how often you played each song, and when last. */
    fun data(sort: String): SortData {
        if (!sortNeedsPlays(sort)) return SortData.NONE
        val counts = runCatching { counts() }.getOrDefault(emptyMap())
        val last = if (sort.startsWith("played")) runCatching { TasteService.lastHeard() }.getOrDefault(emptyMap()) else emptyMap()
        return SortData(plays = { counts[songKey(it)] ?: it.plays }, last = { last[Stats.statsKey(it.title, it.artist)] ?: 0L })
    }

    /** song key -> how often you played it: the stats (Last.fm and Spotify too), else the plays here. */
    fun counts(): Map<Pair<String, String>, Int> = merged(
        Music.plays.items.values.map { songKey(it.track) to it.plays },
        Stats.songs.map { s -> songKey(Track("", title = s.title, artist = s.artist)) to s.plays })

    /** The plays here and the stats' as one count per song: where both know a song, the higher of the two (the
     *  stats hold the plays here too, so they are not added up). */
    fun <K> merged(here: List<Pair<K, Int>>, stats: List<Pair<K, Int>>): Map<K, Int> {
        val out = HashMap<K, Int>()
        here.forEach { (k, n) -> out[k] = n }
        stats.forEach { (k, n) -> out.merge(k, n) { a, b -> maxOf(a, b) } }
        return out
    }

    /** When each song was last heard: the later of the play here and the one heard anywhere (seconds), into here. */
    fun <K> latest(here: MutableMap<K, Double>, anywhere: Map<K, Long>?) {
        anywhere?.forEach { (k, t) -> if (t > (here[k] ?: 0.0)) here[k] = t.toDouble() }
    }

    /** A playlist's songs as the sort chips say. */
    fun playlist(tracks: List<Track>): List<Track> =
        sortSongsAsNow(tracks, Music.playlistSort, if (Music.playlistSort == "plays") counts() else emptyMap()) { asNow(it) }

    private var nowVer = -1
    private var nowById: Map<String, Track> = emptyMap()

    /** A song as its tags stand today: your own copy of it (the file's or the download's tags as they are now), else
     *  itself. Your songs by their ids are gathered once per change to your music. */
    fun asNow(t: Track): Track {
        val v = Music.musicVersion
        if (v != nowVer) { nowById = runCatching { Music.mySongs().associateBy { it.id } }.getOrDefault(emptyMap()); nowVer = v }
        return nowById[t.id] ?: runCatching { Music.localOf(t) }.getOrDefault(t)
    }

    /** Smart shuffle (Settings › Playback): each song's weight, more for the ones you love (play a lot) that you haven't
     *  heard in a while; null (plain chance) with it off. */
    fun shuffleWeight(): ((Track) -> Double)? {
        if (!Prefs.on(Prefs.SMART_SHUFFLE)) return null
        val plays = HashMap<Pair<String, String>, Int>()
        for (st in Stats.songs.take(20000)) plays[Stats.statsKey(st.title, st.artist)] = st.plays
        val last = HashMap<Pair<String, String>, Double>()
        for (e in Music.plays.items.values) { val k = Stats.statsKey(e.track.title, e.track.artist); last[k] = maxOf(last[k] ?: 0.0, e.last) }
        // …and when you last heard it anywhere (Last.fm, your imports), from what TasteService has now: never waited for
        latest(last, TasteService.current?.last)
        val now = System.currentTimeMillis() / 1000.0
        return { t -> Stats.statsKey(t.title, t.artist).let { k -> smartWeight(plays[k] ?: 0, last[k] ?: 0.0, now) } }
    }
}
