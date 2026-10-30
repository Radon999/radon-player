package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.io.File

/** Every play added up, from wherever you played it: every scrobble on Last.fm (all time), plus
 *  every play in your Spotify and Apple Music imports, and without Last.fm, the plays here. Most played, the Artists tab and the artist pages show these; the desktop adds
 *  them up the same way. Added up again in the background when they're older than [FOR]
 *  seconds or what goes into them changed, and kept between starts. */
object Stats {
    @Serializable
    data class Song(val title: String, val artist: String, val album: String = "", val thumb: String = "",
                    val plays: Int = 0, val lastfm: Int = 0, val spotify: Int = 0, val here: Int = 0, val apple: Int = 0,
                    val deezer: Int = 0)

    @Serializable
    data class ArtistPlays(val name: String, val plays: Int = 0, val lastfm: Int = 0, val spotify: Int = 0,
                           val here: Int = 0, val thumb: String = "", val apple: Int = 0, val deezer: Int = 0)

    /** Listening time over a period: the desktop's ListenTime. */
    @Serializable
    data class Time(val secs: Long = 0, val hours: List<Long> = List(24) { 0L }, val days: List<Long> = List(7) { 0L },
                    val artists: List<Pair<String, Long>> = emptyList(), val albums: List<Triple<String, String, Long>> = emptyList(),
                    val albumKeys: List<String> = emptyList(), val songs: List<SongTime> = emptyList())

    /** A song's listening time (Most played › Songs time). */
    @Serializable
    data class SongTime(val title: String, val artist: String, val album: String = "", val secs: Long = 0)

    /** A song or artist reaching 100, 250, 500, 1,000… plays (at: when it was seen; 0: it already had, at the first count). */
    @Serializable
    data class Milestone(val what: String, val key: String, val name: String, val artist: String, val plays: Int, val at: Long)

    @Serializable
    private data class Saved(val songs: List<Song> = emptyList(), val artists: List<ArtistPlays> = emptyList(),
                             val built: Double = 0.0, val sig: String = "",
                             val periods: Map<String, List<Song>> = emptyMap(),
                             val time: Map<String, Time> = emptyMap(), val milestones: List<Milestone> = emptyList())

    /** Most played over the last so many days (and all time): the desktop's STATS_PERIODS. */
    val PERIODS = linkedMapOf("week" to 7, "month" to 30, "year" to 365)

    const val FOR = 600          // seconds the counts are good for

    var songs by mutableStateOf(listOf<Song>())            // most played first
        private set
    var artists by mutableStateOf(listOf<ArtistPlays>())   // most played first
        private set
    /** "week", "month", "year" -> songs as [songs], from those days' plays (the ones with a time). */
    var periods by mutableStateOf(mapOf<String, List<Song>>())
        private set
    var time by mutableStateOf(mapOf<String, Time>())   // "week", "month", "year", "all" -> listening time
        private set
    var milestones by mutableStateOf(listOf<Milestone>())   // newest first
        private set
    var busy by mutableStateOf(false)
        private set
    private var built = 0.0
    private var sig = ""
    private lateinit var ctx: Ctx
    private val file get() = File(ctx.filesDir, "stats.json")

    fun init(c: Ctx) {
        ctx = appCtx(c)
        Music.bg({ readText(file)?.let { runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } }) { s, _ ->
            if (s != null && songs.isEmpty()) { songs = s.songs; artists = s.artists; periods = s.periods; built = s.built; sig = s.sig
                time = s.time; milestones = s.milestones }
        }
    }

    private fun inputs() = listOf("v7", runCatching { Music.artistPicks.toSortedMap().toString() }.getOrDefault(""),
        runCatching { Music.albumPicks.toSortedMap().toString() }.getOrDefault(""),
        runCatching { Music.songPicks.toSortedMap().toString() }.getOrDefault(""), if (LastFm.connected) LastFm.user else "", SpotifyHistory.count, DeezerHistory.count, AppleHistory.count,
        if (LastFm.connected) 0 else Music.plays.items.values.sumOf { it.plays }).joinToString("|")

    /** Where the counts come from, for the label over Most played. */
    val sources: List<String> get() {
        val top = songs.take(200)
        return listOfNotNull("Last.fm".takeIf { top.any { it.lastfm > 0 } }, "Spotify".takeIf { top.any { it.spotify > 0 } },
            "Deezer".takeIf { top.any { it.deezer > 0 } },
            "Apple Music".takeIf { top.any { it.apple > 0 } }, "here".takeIf { top.any { it.here > 0 } })
    }

    /** Add them up again, off the main thread, if they're stale. */
    fun refresh(force: Boolean = false) {
        if (!::ctx.isInitialized || busy || !(force || sig != inputs() || now() - built > FOR)) return
        busy = true
        Music.bg({ build() }) { s, err ->
            busy = false
            if (s != null) {
                songs = s.songs; artists = s.artists; periods = s.periods; built = s.built; sig = s.sig; time = s.time; milestones = s.milestones
                runCatching { Music.prunePictures() }
            }
            else {
                built = now() - FOR + 120   // try again in two minutes (not straight away)
                sig = inputs()
                logWarn("stats", err)
            }
            Music.musicVersion++
        }
    }

    private fun build(): Saved {
        val sig = inputs()
        val fm = LastFm.connected
        val scrobbles = if (fm) Wrapped.allScrobbles(ctx).map { SpotifyHistory.Play(it.t, it.artist, it.title, it.album) } else emptyList()
        val here = if (fm) emptyList() else Music.plays.items.values.map { it.track to it.plays }
        // Spotify's and Apple Music's imports: every play in them (from 30 seconds, as Spotify counts a
        // stream), added to Last.fm's, even where Spotify scrobbled to Last.fm too.
        val spotify = SpotifyHistory.plays; val apple = AppleHistory.timed(); val deezer = DeezerHistory.plays
        val (s, a) = combine(scrobbles, spotify, here, apple, AppleHistory.rows.filter { it.t <= 0 }, deezer)
        // This week's, month's and year's: the plays with a time (scrobbles, Spotify's, Apple Music's timed ones).
        val thumbs = s.associate { statsKey(it.title, it.artist) to it.thumb }
        val periods = PERIODS.mapValues { (_, days) ->
            val start = (now() - days * 86400L).toLong()
            combine(scrobbles.filter { it.t >= start }, spotify.filter { it.t >= start }, emptyList(), apple.filter { it.t >= start },
                deezer = deezer.filter { it.t >= start }).first
                .take(5000).map { if (it.thumb.isEmpty()) it.copy(thumb = thumbs[statsKey(it.title, it.artist)] ?: "") else it }
        }
        val lengths = runCatching { Music.mySongs().filter { it.duration > 0 }.associate { statsKey(it.title, it.artist) to it.duration } }.getOrDefault(emptyMap())
        val time = listenTime(scrobbles + spotify + apple + deezer, lengths)
        // Each milestone dated by the play that got it there (Wrapped shows a year's).
        val timedPlays = scrobbles + spotify + apple + deezer
        val songTimes = timedPlays.groupBy({ statsKey(it.title, it.artist) }, { it.t })
        val artistTimes = timedPlays.groupBy({ artistKey(leadArtist(it.artist)) }, { it.t })
        val out = Saved(s, a, now(), sig, periods, time, newMilestones(s, a, milestones, songTimes, artistTimes))
        writeAtomic(file, JSON.encodeToString(Saved.serializer(), out))
        return out
    }

    private class Counts(val title: String, val artist: String, var album: String, var thumb: String) {
        var lastfm = 0; var spotify = 0; var here = 0; var apple = 0; var deezer = 0
        val plays get() = lastfm + spotify + here + apple + deezer
    }

    val MILESTONES = listOf(100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000, 100000)

    /** When a play count got to level: times are the plays with a time; the rest of total (plays with no time) count
     *  as before them all (0). Null: not yet. */
    internal fun reachedAt(total: Int, times: List<Long>, level: Int): Long? {
        if (total < level) return null
        var got = total - times.size
        if (got >= level) return 0
        for (t in times.sorted()) { got++; if (got >= level) return t }
        return 0
    }

    /** Milestones: a song or artist reaching 100, 250, 500, 1,000… plays, each with when it got there (from the plays'
     *  own times, so Wrapped can show a year's). One reached by plays with no time is dated now, or 0 on the first count. */
    internal fun newMilestones(songs: List<Song>, artists: List<ArtistPlays>, old: List<Milestone>,
                               songTimes: Map<Pair<String, String>, List<Long>> = emptyMap(),
                               artistTimes: Map<String, List<Long>> = emptyMap()): List<Milestone> {
        val seen = old.associateBy { Triple(it.what, it.key, it.plays) }
        val first = old.isEmpty()
        val nowS = now().toLong()
        val out = ArrayList<Milestone>()
        fun check(what: String, key: String, name: String, artist: String, plays: Int, times: List<Long>) {
            for (level in MILESTONES) {
                var at = reachedAt(plays, times, level) ?: return
                if (at == 0L && !first) at = seen[Triple(what, key, level)]?.at ?: nowS
                out += Milestone(what, key, name, artist, level, at)
            }
        }
        songs.take(3000).forEach {
            val k = statsKey(it.title, it.artist)
            check("song", k.toList().joinToString("|"), it.title, it.artist, it.plays, songTimes[k].orEmpty())
        }
        artists.take(1000).forEach { check("artist", artistKey(it.name), it.name, "", it.plays, artistTimes[artistKey(it.name)].orEmpty()) }
        return out.sortedWith(compareBy({ -it.at }, { -it.plays })).take(6000)
    }

    /** The milestones reached from start to end (start 0: ever), the highest of each song's and artist's, biggest first. */
    fun periodMilestones(start: Long, end: Long, n: Int = 6): List<Milestone> =
        milestones.filter { if (start == 0L) true else it.at in start until end }
            .groupBy { it.what to it.key }.values.map { g -> g.maxBy { it.plays } }
            .sortedWith(compareBy({ -it.plays }, { it.what != "artist" }, { -it.at })).take(n)

    /** Listening time over each period (and all time): a play's length is what Spotify or Apple say it played, else the
     *  song's own length (your copy), else 3½ minutes. */
    internal fun listenTime(plays: List<SpotifyHistory.Play>, lengths: Map<Pair<String, String>, Double>): Map<String, Time> {
        val resolve = resolver()
        val since = PERIODS.mapValues { (_, d) -> now() - d * 86400L } + ("all" to 0.0)
        class Acc { var secs = 0.0; val hours = DoubleArray(24); val days = DoubleArray(7)
            val artists = HashMap<String, Double>(); val albums = HashMap<String, Double>(); val names = HashMap<String, Any>()
            val songs = HashMap<Pair<String, String>, Double>(); val songNames = HashMap<Pair<String, String>, SongTime>()
            val called = HashMap<String, HashMap<String, Int>>() }
        val acc = since.mapValues { Acc() }
        for (p0 in plays) {
            if (p0.t <= 0) continue
            val p = sameSong(p0.title, p0.artist).let { (t, a) -> p0.copy(title = t, artist = a, album = onto(p0.title, p0.artist, p0.album)) }
            val secs = if (p.ms > 30_000) p.ms / 1000.0 else lengths[statsKey(p.title, p.artist)] ?: 210.0
            val lead = resolve(p.artist).ifEmpty { splitArtists(p.artist).firstOrNull() ?: p.artist }; val ak = resolvedKey(lead)
            val (base, ed) = albumEdition(p.album)
            val bk = if (p.album.isNotBlank()) norm(cleanTitle(base)) + "|" + ak else ""
            val d = java.time.Instant.ofEpochSecond(p.t).atZone(java.time.ZoneId.systemDefault())
            for ((k, start) in since) {
                if (p.t < start) continue
                val x = acc[k]!!
                x.secs += secs; x.hours[d.hour] += secs; x.days[d.dayOfWeek.value - 1] += secs
                x.artists.merge(ak, secs, Double::plus); x.names.putIfAbsent(ak, lead)
                x.called.getOrPut(ak) { HashMap() }.merge(lead, 1, Int::plus)
                val sk = statsKey(p.title, p.artist)
                x.songs.merge(sk, secs, Double::plus); x.songNames.putIfAbsent(sk, SongTime(p.title, p.artist, p.album))
                if (bk.isNotEmpty()) {
                    x.albums.merge(bk, secs, Double::plus)
                    if (ed.isNotEmpty() || bk !in x.names) x.names[bk] = p.album to lead
                }
            }
        }
        for (x in acc.values) for ((ak, names) in x.called) x.names[ak] = names.maxByOrNull { it.value }?.key ?: x.names[ak] as String
        return acc.mapValues { (_, x) ->
            Time(x.secs.toLong(), x.hours.map { it.toLong() }, x.days.map { it.toLong() },
                x.artists.entries.sortedByDescending { it.value }.take(300).map { (x.names[it.key] as String) to it.value.toLong() },
                x.albums.entries.sortedByDescending { it.value }.take(300).map { e ->
                    val n = x.names[e.key] as Pair<*, *>; Triple(n.first as String, n.second as String, e.value.toLong()) },
                x.albums.entries.sortedByDescending { it.value }.take(300).map { it.key },
                x.songs.entries.sortedByDescending { it.value }.take(500).map { x.songNames[it.key]!!.copy(secs = it.value.toLong()) })
        }
    }

    /** A song across Last.fm, Spotify, Apple Music and here: its name and lead artist, tidied
     *  (feat., & and x credits, remaster notes left out). The desktop's stats_key. */
    fun statsKey(title: String, artist: String): Pair<String, String> = org.omarchy.music.statsKey(title, artist)

    private fun MutableMap<Pair<String, String>, Counts>.count(title: String, artist: String, album: String, thumb: String,
                                                                 n: Int, source: String, key: Pair<String, String>? = null) {
        if (title.isBlank() || artist.isBlank() || n <= 0) return
        val c = getOrPut(key ?: statsKey(title, artist)) { Counts(title, artist, album, thumb) }
        when (source) { "lastfm" -> c.lastfm += n; "spotify" -> c.spotify += n; "apple" -> c.apple += n; "deezer" -> c.deezer += n; else -> c.here += n }
        if (c.album.isEmpty()) c.album = album
        if (c.thumb.isEmpty()) c.thumb = thumb
    }

    /** The counts from each source, put together: (songs, artists), most played first. */
    internal fun combine(scrobbles: List<SpotifyHistory.Play>, spotify: List<SpotifyHistory.Play>, here: List<Pair<Track, Int>>,
                         apple: List<SpotifyHistory.Play> = emptyList(),
                         appleTotals: List<AppleHistory.Row> = emptyList(),
                         deezer: List<SpotifyHistory.Play> = emptyList()): Pair<List<Song>, List<ArtistPlays>> {
        val songs = LinkedHashMap<Pair<String, String>, Counts>()
        // An artist you merged into another (Merge into…, Similar artists): their songs' plays go with them too.
        val picks = runCatching { Music.artistPicks }.getOrDefault(emptyMap())
        fun moved(a: String) = if (picks.isEmpty()) a else picks[pairKey(a)]?.ifEmpty { null } ?: a
        // A song you merged into another (Duplicates in Most played): counted as that one.
        fun add(title: String, artist: String, album: String, thumb: String, n: Int, source: String) {
            val (t, a) = sameSong(title, artist)
            songs.count(t, moved(a), onto(title, artist, album), thumb, n, source)
        }
        for (p in scrobbles) add(p.title, p.artist, p.album, "", 1, "lastfm")
        for ((t, n) in here) add(t.title, t.artist, t.album, t.thumb, n, "here")
        for (p in spotify) add(p.title, p.artist, p.album, "", 1, "spotify")
        for (p in apple) add(p.title, p.artist, p.album, "", 1, "apple")
        for (p in deezer) add(p.title, p.artist, p.album, "", 1, "deezer")
        // A library's counts: Apple's all-time total for a song, past the Apple plays counted already.
        for (r in appleTotals) { val (t, a) = sameSong(r.title, r.artist)
            add(r.title, r.artist, r.album, "", r.count - (songs[statsKey(t, moved(a))]?.apple ?: 0), "apple") }
        val artists = LinkedHashMap<Pair<String, String>, Counts>()
        val called = HashMap<Pair<String, String>, HashMap<String, Int>>()
        val resolve = resolver()
        for (s in songs.values) {
            val lead = resolve(s.artist).ifEmpty { leadArtist(s.artist).ifEmpty { s.artist } }
            artists.count(lead, lead, "", s.thumb, s.lastfm, "lastfm", artistsKey(lead))
            artists.count(lead, lead, "", s.thumb, s.spotify, "spotify", artistsKey(lead))
            artists.count(lead, lead, "", s.thumb, s.here, "here", artistsKey(lead))
            artists.count(lead, lead, "", s.thumb, s.apple, "apple", artistsKey(lead))
            artists.count(lead, lead, "", s.thumb, s.deezer, "deezer", artistsKey(lead))
            artists[artistsKey(lead)]?.let { if (it.thumb.isEmpty()) it.thumb = s.thumb }
            called.getOrPut(artistsKey(lead)) { HashMap() }.merge(lead, s.plays, Int::plus)
        }
        // "Quinn XCII & Jeremy Zucker" is Quinn XCII's: named as they are on their own.
        return songs.values.sortedByDescending { it.plays }.map { Song(it.title, it.artist, it.album, it.thumb, it.plays, it.lastfm, it.spotify, it.here, it.apple, it.deezer) } to
            artists.entries.sortedByDescending { it.value.plays }.map { (k, it) ->
                ArtistPlays(called[k]?.maxByOrNull { e -> e.value }?.key ?: it.title, it.plays, it.lastfm, it.spotify, it.here, it.thumb, it.apple, it.deezer) }
    }

    /** The song a song's plays count as: the one you merged it into (Duplicates in Most played), else itself. */
    private fun sameSong(title: String, artist: String): Pair<String, String> {
        val picks = runCatching { Music.songPicks }.getOrDefault(emptyMap())
        var t = title; var a = artist
        repeat(5) {
            val p = picks[statsKey(t, a).let { "${it.first}|${it.second}" }]?.takeIf { it.isNotEmpty() } ?: return t to a
            val (pt, pa) = p.split("\u0000").let { (it.getOrNull(0) ?: t) to (it.getOrNull(1) ?: a) }
            t = pt; a = pa
        }
        return t to a
    }

    /** The album a song's plays count for: the one you put a single's (an EP's) plays on (Plays on singles and albums). */
    private fun onto(title: String, artist: String, album: String): String {
        if (album.isBlank()) return album
        val picks = runCatching { Music.albumPicks }.getOrDefault(emptyMap())
        return picks[albumPlayKey(title, artist, album)]?.ifEmpty { null } ?: album
    }

    /** Whose plays a credit's are (Music.artistResolver; in the JVM tests, one made from no songs). */
    private fun resolver(): (String) -> String = runCatching { Music.artistResolver() }.getOrElse { artistResolver(emptyList(), emptyMap()) }

    /** The key an artist's plays are added up under in combine's count (a pair kept as a pair is its own). */
    private fun artistsKey(name: String) = "" to resolvedKey(name)

    private fun leadArtist(a: String) = a.split(Regex(",|;| feat\\.? | ft\\.? | featuring | with ")).map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.equals("unknown artist", true) } ?: ""

    /** Artist name's songs (theirs or with them), most played first. */
    fun artistSongs(name: String): List<Song> {
        val n = norm(name)
        return songs.filter { s -> s.artist.split(Regex(",|&| x | feat\\.? | ft\\.? ")).any { norm(it) == n } }
    }

    fun artist(name: String): ArtistPlays? = norm(name).let { n -> artists.firstOrNull { norm(it.name) == n } }
}
