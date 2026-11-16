package org.omarchy.music

/** What your listening adds up to (the plan's TasteService): every play there is (Last.fm's scrobbles and your imports;
 *  the plays here only without any) counted once into one picture that is never changed after it's made, and kept:
 *  given as it is for ten minutes, then still given at once while a new one is made behind; only past half an hour
 *  (or the first time) does the asker wait. The DJ makes its sets and facts from it; sorting by Recently played,
 *  smart playlists and the mood pages read it too. The counting itself is [TasteRules]. */
object TasteService {
    /** all: your most played songs of all time; recent: of the last 30 days; hour: what you play around this
     *  hour; years: each year's most played; last: when you last played each song (stats key); artists: the
     *  ones you've played most lately; moods: the moods (Moods) of what you played
     *  in the last 30 days, and moodHour of what you play around this hour, each with how many plays. */
    class Taste(val mine: Map<Pair<String, String>, Track>, val recent: List<Track>, val all: List<Track>,
                val artists: List<String>, val last: Map<Pair<String, String>, Long>,
                val hour: List<Track> = emptyList(), val years: Map<Int, List<Track>> = emptyMap(),
                val facts: Facts = Facts(),
                val moods: Map<String, Int> = emptyMap(), val moodHour: Map<String, Int> = emptyMap(),
                val moodPlays: List<MoodPlay> = emptyList(),
                val dayAgo: Map<Int, List<Track>> = emptyMap(), val streak: List<Pair<Track, Int>> = emptyList())

    /** What the DJ knows to tell you about: every song's plays and first play, your artists all told and this month,
     *  the hours you listen at, how many plays in all, each year's number one and plays, the moods of this month's plays. */
    class Facts(val count: Map<Pair<String, String>, Int> = emptyMap(), val first: Map<Pair<String, String>, Long> = emptyMap(),
                val artistAll: Map<String, Int> = emptyMap(), val artistMonth: Map<String, Int> = emptyMap(),
                val hours: IntArray = IntArray(24), val total: Int = 0, val yearTop: Map<Int, Pair<Heard, Int>> = emptyMap(),
                val since: Long = 0, val moodMonth: Map<String, Int> = emptyMap(), val yearPlays: Map<Int, Int> = emptyMap(),
                val artistFirst: Map<String, Long> = emptyMap(), val monthPlays: Int = 0)

    private val kept = KeptFresh(600.0, 1800.0, inBackground = { work -> kotlin.concurrent.thread(isDaemon = true, name = "dj-taste") { work() } }) { build() }

    /** The picture there is now, without waiting for one (null before the first was made). */
    val current: Taste? get() = kept.current

    /** (blocking) What you play, read again every ten minutes. */
    fun get(): Taste = kept.get()

    /** In a test: made again at the next asking (after the plays were changed). */
    fun clear() = kept.clear()

    /** In a test: the plays it goes by, in place of Last.fm, the imports and the plays here (null in the app). */
    @Volatile var historyStandIn: (() -> List<Heard>)? = null

    /** (blocking) Every play it goes by: your Last.fm scrobbles and your imports; the plays here only without any. */
    private fun history(): List<Heard> {
        historyStandIn?.let { return it() }
        // Last.fm's scrobbles and your imports (Spotify, Apple Music, Deezer, YouTube Music, Tidal): what every count,
        // set and fact is made from. The plays here only when there's none of those at all (a fallback).
        val out = ArrayList<Heard>()
        if (LastFm.connected) runCatching { Wrapped.allScrobbles(Music.ctx).forEach { out += Heard(it.t, it.artist, it.title) } }
        for (p in SpotifyHistory.plays + OtherPlays.all + AppleHistory.timed()) out += Heard(p.t, p.artist, p.title)
        if (out.isEmpty()) out += herePlays()
        return out
    }

    /** The plays here (the app's own diary of when you played what). */
    private fun herePlays(): List<Heard> = runCatching {
        Music.plays.items.values.toList().flatMap { e -> e.at.map { Heard(it, e.track.artist, e.track.title) } }
    }.getOrDefault(emptyList())

    /** (blocking) Every play (Last.fm, your imports, the plays here) of a song of yours whose mood is known, oldest
     *  first, read again every ten minutes. See [MoodPlays]. */
    fun moodPlays(): List<MoodPlay> = get().moodPlays

    /** Your copy of the song a [MoodPlay.song] names (title and artist key), or null. */
    fun moodSong(song: String): Track? {
        val parts = song.split("\t")
        if (parts.size != 2) return null
        return (current ?: get()).mine[parts[0] to parts[1]]
    }

    /** (blocking) When you last played each song (seconds), by Stats.statsKey: for Sort › Recently played. */
    fun lastHeard(): Map<Pair<String, String>, Long> = get().last

    private fun build(): Taste {
        val mine = Music.songIndex()
        val nowS = now().toLong()
        val zone = java.time.ZoneId.systemDefault()
        val h = java.time.LocalTime.now().hour
        val c = TasteRules.count(history(), nowS, h, java.time.LocalDate.now(zone), zone) { k -> mine[k]?.let { runCatching { Moods.of(it) }.getOrNull() } }
        val names = c.names; val last = c.last; val recent = c.recent; val hour = c.hour; val years = c.years; val artists = c.artists
        val count = c.count; val first = c.first; val artistAll = c.artistAll; val artistMonth = c.artistMonth; val hours = c.hours
        val total = c.total; val since = c.since; val moodPlays = c.moodPlays; val moodMonth = c.moodMonth; val moodHour = c.moodHour
        val dayAgo = c.dayAgo; val days = c.days; val artistFirst = c.artistFirst; val monthPlays = c.monthPlays
        // All time, an artist's and a song's plays are Most played's (Stats): Last.fm and your Spotify, Apple Music,
        // Deezer, YouTube Music and Tidal imports (with or without a time), an artist's under one name. The plays here
        // only count when there's nothing from those at all.
        val anyOutside = Stats.artists.any { it.plays > it.here }
        fun outside(all: Int, here: Int) = TasteRules.outside(all, here, anyOutside)
        if (Stats.artists.isNotEmpty()) { artistAll.clear(); Stats.artists.forEach { val n = outside(it.plays, it.here); if (n > 0) artistAll.merge(it.name, n, Int::plus) } }
        val statSongs = HashMap<Pair<String, String>, Int>()
        Stats.songs.forEach { statSongs.merge(Stats.statsKey(it.title, it.artist), outside(it.plays, it.here), Int::plus) }
        statSongs.forEach { (k, n) -> if (n > (count[k] ?: 0)) count[k] = n }
        // When you last played a song: the plays here too (not counted anywhere), so one you played a minute ago,
        // before Last.fm has it, isn't picked again straight away
        for (p in herePlays()) { val k = Stats.statsKey(p.title, p.artist); if (p.t > (last[k] ?: 0L)) last[k] = p.t }
        val yearTop = years.mapNotNull { (y, c) -> c.maxByOrNull { it.value }?.let { (k, n) -> y to (names.getValue(k) to n) } }.toMap()
        val facts = Facts(count, first, artistAll, artistMonth, hours, total, yearTop, if (since == Long.MAX_VALUE) 0 else since, moodMonth,
            years.mapValues { it.value.values.sum() }, artistFirst, monthPlays)
        fun songs(c: Map<Pair<String, String>, Int>, n: Int) = c.entries.sortedByDescending { it.value }.take(n)
            .map { (k, v) -> val p = names.getValue(k); Music.fmSong(p.title, p.artist, "", mine).copy(plays = v) }
        // Every song: all you've played, most played first, then every song of yours you haven't played yet
        var all = runCatching { Music.mostPlayed() }.getOrDefault(emptyList())
        if (all.isEmpty()) all = Music.plays.items.values.sortedByDescending { it.plays }.map { it.track.copy(plays = it.plays) }
        val inAll = all.mapTo(HashSet()) { songKey(it) }
        all = all + runCatching { Music.mySongs() }.getOrDefault(emptyList()).filter { inAll.add(songKey(it)) }
        var top = artists.entries.sortedByDescending { it.value }.map { it.key }.filter { it.isNotEmpty() }.take(20)
        val rec = songs(recent, 60)
        if (top.isEmpty()) top = (rec + all).map { it.artist.split(",")[0].trim() }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key }
        val streak = days.filter { it.value.size >= 4 }.entries.sortedByDescending { it.value.size * 1000 + (recent[it.key] ?: 0) }.take(40)
            .map { (k, ds) -> val p = names.getValue(k); Music.fmSong(p.title, p.artist, "", mine).copy(plays = count[k] ?: 0) to ds.size }
        return Taste(mine, rec, all, top, last, songs(hour, 120),
            years.filter { it.value.values.sum() >= 30 }.mapValues { songs(it.value, 60) }, facts, moodMonth, moodHour,
            moodPlays.sortedBy { it.t }, dayAgo.filter { it.value.values.sum() >= 8 }.mapValues { songs(it.value, 40) }, streak)
    }
}
