package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.decodeToSequence
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/** What you played on Spotify, for Wrapped: from the data download Spotify gives you
 *  (the zip from your account's privacy page, the extended streaming history or the
 *  past year's), and from your last plays through the Spotify connection, picked up
 *  each time the app starts. Spotify's API only remembers the last 50, so the zip is
 *  what brings the years back. A play counts from 30 seconds, as Spotify counts streams;
 *  podcasts and videos are left out. Nothing goes to Last.fm: it takes nothing older than
 *  two weeks. Wrapped puts these together with your scrobbles, each play once. */
object SpotifyHistory {
    @Serializable
    data class Play(val t: Long, val artist: String, val title: String, val album: String = "", val ms: Long = 0,
                    val how: Int = 0)   // how it ended, where the history says: 1 heard to its end, 2 skipped; 0 not said

    /** skips: the songs you skipped before thirty seconds were up. They aren't plays (a play counts from thirty
     *  seconds), but they are skips, and the extended history says so. */
    @Serializable
    private data class Saved(val plays: List<Play> = emptyList(), val api_after: Long = 0, val skips: List<Play> = emptyList())

    private lateinit var file: File
    private var saved: Saved? = null

    /** What's kept, read from its file the first time (blocking). */
    @Synchronized private fun stored(): Saved = saved ?: (readText(file)?.let {
        runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } ?: Saved()).also { saved = it; count = it.plays.size }

    val plays: List<Play> get() = stored().plays
    var count by mutableStateOf(0)           // for Settings
        private set
    var busy by mutableStateOf<String?>(null)
        private set

    fun init(c: Ctx) {
        file = File(c.filesDir, "spotify-history.json")
        Music.bg({ stored() })
    }

    private fun keep(s: Saved) {
        saved = s
        HistoryLengths.forget()
        writeAtomic(file, JSON.encodeToString(Saved.serializer(), s))
        count = s.plays.size
    }

    private val Play.end get() = t + ms / 1000

    /** Add plays, but not those you have already: the same song starting or stopping within
     *  two minutes (the API knows when a song stopped, not for how long it played). */
    @Synchronized private fun add(new: List<Play>, apiAfter: Long? = null, newSkips: List<Play> = emptyList()): Int {
        val old = stored()
        val have = HashMap<String, MutableList<Play>>()
        for (p in old.plays) have.getOrPut(cleanTitle(p.title)) { ArrayList() } += p
        // A play you have already that is read again with how it ended (a history imported before the app kept
        // that): it learns it, so importing your Spotify zip once more brings the skips of plays long since counted.
        val learnt = HashMap<Play, Int>()
        val reads = newSkips.isNotEmpty() || new.any { it.how != 0 }   // this import says how plays ended (the extended history)
        val fresh = new.filter { p ->
            val same = have.getOrPut(cleanTitle(p.title)) { ArrayList() }
            val twin = same.firstOrNull { Math.abs(it.t - p.t) < 120 || Math.abs(it.end - p.end) < 120 }
            if (twin != null && twin.how != p.how && reads) learnt[twin] = p.how   // (and puts right what an earlier import made of it)
            (twin == null).also { if (it) same += p }
        }
        val plays = (if (learnt.isEmpty()) old.plays else old.plays.map { p -> learnt[p]?.let { p.copy(how = it) } ?: p }) + fresh
        // The short skips of the stretch of time this import covers are the import's (an earlier one kept more as skips)
        val skips = if (!reads) old.skips else {
            val from = new.minOfOrNull { it.t } ?: Long.MAX_VALUE; val to = new.maxOfOrNull { it.t } ?: Long.MIN_VALUE
            (old.skips.filter { it.t < from || it.t > to } + newSkips).distinctBy { it.t to cleanTitle(it.title) }.sortedBy { it.t }
        }
        val after = apiAfter ?: old.api_after
        if (fresh.isNotEmpty() || learnt.isNotEmpty() || skips != old.skips || after != old.api_after)
            keep(Saved(plays.sortedBy { it.t }, after, skips))
        lastLearnt = learnt.size + kotlin.math.abs(skips.size - old.skips.size)
        return fresh.size
    }
    /** How many skips and endings the last import brought for plays already here (for what it says when no play was new). */
    @Volatile var lastLearnt = 0
        private set

    /** What your Spotify history says of how you took each song: skipped, or heard to its end (the extended history
     *  only; the past year's doesn't say). One entry a song, for [SongStats]; made once per history. */
    fun skipEntries(): List<SkipEntry> {
        val s = stored()
        skipsMade?.takeIf { it.first === s }?.let { return it.second }
        val made = (s.plays.asSequence().filter { it.how != 0 } + s.skips.asSequence().map { if (it.how == 0) it.copy(how = 2) else it })
            .groupBy { statsKey(it.title, it.artist) }.map { (k, l) ->
                val sk = l.filter { it.how == 2 }.map { it.t }; val fu = l.filter { it.how == 1 }.map { it.t }
                val p = l.last()
                SkipEntry(sk.size, fu.size, (sk.maxOrNull() ?: 0L).toDouble(), Track("sp-skip:${k.first}|${k.second}", "lastfm", p.title, p.artist, album = p.album), sk, fu)
            }
        skipsMade = s to made
        return made
    }
    @Volatile private var skipsMade: Pair<Saved, List<SkipEntry>>? = null

    @Synchronized fun clear() = keep(Saved(api_after = stored().api_after))

    // ---- the data download ----

    private val BASIC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** A play in a history file. The extended history: ts (when it stopped, UTC), ms_played,
     *  master_metadata_*; the past year's: endTime ("2024-01-31 18:02", UTC), artistName, trackName, msPlayed. */
    @Serializable
    private data class Entry(
        val ts: String? = null, val ms_played: Long? = null, val master_metadata_track_name: String? = null,
        val master_metadata_album_artist_name: String? = null, val master_metadata_album_album_name: String? = null,
        val endTime: String? = null, val msPlayed: Long? = null, val trackName: String? = null, val artistName: String? = null,
        val spotify_track_uri: String? = null,
        val skipped: Boolean? = null, val reason_end: String? = null,   // the extended history: how the play ended
    )

    /** How a play ended, as the extended history says: 2 skipped (its "skipped", or the forward button), 1 heard to the
     *  end ("trackdone"), 0 when it doesn't say (the past year's history never does). */
    internal fun ending(skipped: Boolean?, reasonEnd: String?): Int = when {
        // Only the forward button is a skip. Going back ("backbtn"), picking another song ("clickrow"), the app closing
        // and the rest end a play too, and Spotify marks many of them "skipped": they aren't you turning a song down.
        reasonEnd == "fwdbtn" -> 2
        reasonEnd == "trackdone" -> 1
        reasonEnd.isNullOrEmpty() && skipped == true -> 2   // (a history that says only "skipped")
        else -> 0
    }

    /** A local file's play keeps its names in its address when its name fields are empty:
     *  spotify:local:Artist:Album:Title:seconds (each part URL-encoded, + for a space). (artist, album, title), or null. */
    internal fun localNames(uri: String?): Triple<String, String, String>? {
        if (uri == null || !uri.startsWith("spotify:local:")) return null
        val parts = uri.removePrefix("spotify:local:").split(':')
        if (parts.size < 3) return null
        fun dec(x: String) = runCatching { java.net.URLDecoder.decode(x, "UTF-8") }.getOrDefault(x).trim()
        val (artist, album, title) = Triple(dec(parts[0]), dec(parts[1]), dec(parts[2]))
        return if (title.isEmpty() || artist.isEmpty()) null else Triple(artist, album, title)
    }

    /** One history file's plays, read a play at a time: the extended history's files run to 12 MB. */
    @OptIn(ExperimentalSerializationApi::class)
    internal fun parse(input: InputStream, out: MutableList<Play>, skips: MutableList<Play>? = null) {
        for (e in JSON.decodeToSequence(input, Entry.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)) {
            val end = e.ts?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
                ?: e.endTime?.let { runCatching { LocalDateTime.parse(it, BASIC_TIME).toEpochSecond(ZoneOffset.UTC) }.getOrNull() } ?: continue
            val ms = e.ms_played ?: e.msPlayed ?: 0
            val local = if ((e.master_metadata_track_name ?: e.trackName).isNullOrBlank()) localNames(e.spotify_track_uri) else null
            val title = local?.third ?: e.master_metadata_track_name ?: e.trackName
            val artist = local?.first ?: e.master_metadata_album_artist_name ?: e.artistName
            if (title.isNullOrBlank() || artist.isNullOrBlank() || artist == "Unknown Artist") continue
            val how = ending(e.skipped, e.reason_end)
            val album = local?.second ?: e.master_metadata_album_album_name ?: ""
            if (ms >= 30_000) out += Play(end - ms / 1000, artist, title, album, ms, how)
            // (skipped before thirty seconds: not a play, but kept as a skip, from a second in, so a mis-tap isn't one)
            else if (how == 2 && ms >= 1000) skips?.add(Play(end - ms / 1000, artist, title, album, ms, 2))
        }
    }

    internal fun wanted(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return name.endsWith(".json") && (name.startsWith("Streaming_History_Audio") ||
            (name.startsWith("StreamingHistory") && "podcast" !in name.lowercase() && "video" !in name.lowercase()))
    }

    /** Read the zip (or one of its .json files) at uri; how many plays were new (blocking). */
    fun importFile(ctx: Ctx, uri: String): Int {
        val out = ArrayList<Play>()
        val skips = ArrayList<Play>()
        busy = "Reading your Spotify history…"
        try {
            val stream = BufferedInputStream(ctx.openIn(uri) ?: error("couldn't open that file"))
            stream.use { s ->
                s.mark(4)
                val zip = ByteArray(2).let { s.read(it) == 2 && it[0] == 'P'.code.toByte() && it[1] == 'K'.code.toByte() }
                s.reset()
                if (zip) {
                    val z = ZipInputStream(s)
                    var files = 0
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (!e.isDirectory && wanted(e.name)) {
                            files++
                            busy = "Reading ${e.name.substringAfterLast('/')}…"
                            parse(object : java.io.FilterInputStream(z) { override fun close() {} }, out, skips)
                        }
                    }
                    if (files == 0) error("there's no streaming history in that zip. Spotify's download has Streaming_History_Audio files (or StreamingHistory_music)")
                } else parse(s, out, skips)
            }
            if (out.isEmpty()) error("no songs played for 30 seconds or more in it")
            busy = "Adding ${"%,d".format(out.size)} plays…"
            return add(out, newSkips = skips)
        } finally {
            busy = null
        }
    }

    // ---- the Spotify connection ----

    /** Your last plays since the last look (blocking); how many were new. 0 if Spotify isn't connected. */
    fun sync(): Int {
        val sp = Music.sources["spotify"] as? Spotify ?: return 0
        if (!sp.signedIn) return 0
        val after = stored().api_after
        val items = sp.recentlyPlayed(after)
        var newest = after
        val got = items.mapNotNull { it ->
            val at = runCatching { Instant.parse(it["played_at"].str) }.getOrNull() ?: return@mapNotNull null
            newest = maxOf(newest, at.toEpochMilli())
            val t = trackFromSpotify(it["track"]) ?: return@mapNotNull null
            val ms = Math.round(t.duration * 1000)
            Play(at.epochSecond - ms / 1000, t.artist.split(", ")[0], t.title, t.album, ms)   // played_at is when it ended
        }
        return add(got, newest)
    }
}
