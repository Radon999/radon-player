package org.omarchy.music

import android.content.Context
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
                    val plays: Int = 0, val lastfm: Int = 0, val spotify: Int = 0, val here: Int = 0, val apple: Int = 0)

    @Serializable
    data class ArtistPlays(val name: String, val plays: Int = 0, val lastfm: Int = 0, val spotify: Int = 0,
                           val here: Int = 0, val thumb: String = "", val apple: Int = 0)

    @Serializable
    private data class Saved(val songs: List<Song> = emptyList(), val artists: List<ArtistPlays> = emptyList(),
                             val built: Double = 0.0, val sig: String = "",
                             val periods: Map<String, List<Song>> = emptyMap())

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
    var busy by mutableStateOf(false)
        private set
    private var built = 0.0
    private var sig = ""
    private lateinit var ctx: Context
    private val file get() = File(ctx.filesDir, "stats.json")

    fun init(c: Context) {
        ctx = c.applicationContext
        Music.bg({ readText(file)?.let { runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } }) { s, _ ->
            if (s != null && songs.isEmpty()) { songs = s.songs; artists = s.artists; periods = s.periods; built = s.built; sig = s.sig }
        }
    }

    private fun inputs() = listOf("v3", if (LastFm.connected) LastFm.user else "", SpotifyHistory.count, AppleHistory.count,
        if (LastFm.connected) 0 else Music.plays.items.values.sumOf { it.plays }).joinToString("|")

    /** Where the counts come from, for the label over Most played. */
    val sources: List<String> get() {
        val top = songs.take(200)
        return listOfNotNull("Last.fm".takeIf { top.any { it.lastfm > 0 } }, "Spotify".takeIf { top.any { it.spotify > 0 } },
            "Apple Music".takeIf { top.any { it.apple > 0 } }, "here".takeIf { top.any { it.here > 0 } })
    }

    /** Add them up again, off the main thread, if they're stale. */
    fun refresh(force: Boolean = false) {
        if (!::ctx.isInitialized || busy || !(force || sig != inputs() || now() - built > FOR)) return
        busy = true
        Music.bg({ build() }) { s, err ->
            busy = false
            if (s != null) { songs = s.songs; artists = s.artists; periods = s.periods; built = s.built; sig = s.sig }
            else {
                built = now() - FOR + 120   // try again in two minutes (not straight away)
                sig = inputs()
                android.util.Log.w("omarchy-music", "stats", err)
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
        val spotify = SpotifyHistory.plays; val apple = AppleHistory.timed()
        val (s, a) = combine(scrobbles, spotify, here, apple, AppleHistory.rows.filter { it.t <= 0 })
        // This week's, month's and year's: the plays with a time (scrobbles, Spotify's, Apple Music's timed ones).
        val thumbs = s.associate { statsKey(it.title, it.artist) to it.thumb }
        val periods = PERIODS.mapValues { (_, days) ->
            val start = (now() - days * 86400L).toLong()
            combine(scrobbles.filter { it.t >= start }, spotify.filter { it.t >= start }, emptyList(), apple.filter { it.t >= start }).first
                .take(5000).map { if (it.thumb.isEmpty()) it.copy(thumb = thumbs[statsKey(it.title, it.artist)] ?: "") else it }
        }
        val out = Saved(s, a, now(), sig, periods)
        writeAtomic(file, JSON.encodeToString(Saved.serializer(), out))
        return out
    }

    private class Counts(val title: String, val artist: String, var album: String, var thumb: String) {
        var lastfm = 0; var spotify = 0; var here = 0; var apple = 0
        val plays get() = lastfm + spotify + here + apple
    }

    /** A song across Last.fm, Spotify, Apple Music and here: its name and lead artist, tidied
     *  (feat., & and x credits, remaster notes left out). The desktop's stats_key. */
    fun statsKey(title: String, artist: String): Pair<String, String> =
        norm(cleanTitle(title)) to norm(artist.lowercase().split(Regex(",|;| & | x | feat\\.? | ft\\.? | featuring | with "))[0])
            .let { n -> n.replace(Regex("[\\W_]+"), "").ifEmpty { n } }   // K.Flay = kflay

    private fun MutableMap<Pair<String, String>, Counts>.count(title: String, artist: String, album: String, thumb: String,
                                                                 n: Int, source: String) {
        if (title.isBlank() || artist.isBlank() || n <= 0) return
        val c = getOrPut(statsKey(title, artist)) { Counts(title, artist, album, thumb) }
        when (source) { "lastfm" -> c.lastfm += n; "spotify" -> c.spotify += n; "apple" -> c.apple += n; else -> c.here += n }
        if (c.album.isEmpty()) c.album = album
        if (c.thumb.isEmpty()) c.thumb = thumb
    }

    /** The counts from each source, put together: (songs, artists), most played first. */
    internal fun combine(scrobbles: List<SpotifyHistory.Play>, spotify: List<SpotifyHistory.Play>, here: List<Pair<Track, Int>>,
                         apple: List<SpotifyHistory.Play> = emptyList(),
                         appleTotals: List<AppleHistory.Row> = emptyList()): Pair<List<Song>, List<ArtistPlays>> {
        val songs = LinkedHashMap<Pair<String, String>, Counts>()
        for (p in scrobbles) songs.count(p.title, p.artist, p.album, "", 1, "lastfm")
        for ((t, n) in here) songs.count(t.title, t.artist, t.album, t.thumb, n, "here")
        for (p in spotify) songs.count(p.title, p.artist, p.album, "", 1, "spotify")
        for (p in apple) songs.count(p.title, p.artist, p.album, "", 1, "apple")
        // A library's counts: Apple's all-time total for a song, past the Apple plays counted already.
        for (r in appleTotals) songs.count(r.title, r.artist, r.album, "", r.count - (songs[statsKey(r.title, r.artist)]?.apple ?: 0), "apple")
        val artists = LinkedHashMap<Pair<String, String>, Counts>()
        for (s in songs.values) {
            val lead = leadArtist(s.artist).ifEmpty { s.artist }
            artists.count(lead, lead, "", s.thumb, s.lastfm, "lastfm")
            artists.count(lead, lead, "", s.thumb, s.spotify, "spotify")
            artists.count(lead, lead, "", s.thumb, s.here, "here")
            artists.count(lead, lead, "", s.thumb, s.apple, "apple")
            artists[statsKey(lead, lead)]?.let { if (it.thumb.isEmpty()) it.thumb = s.thumb }
        }
        return songs.values.sortedByDescending { it.plays }.map { Song(it.title, it.artist, it.album, it.thumb, it.plays, it.lastfm, it.spotify, it.here, it.apple) } to
            artists.values.sortedByDescending { it.plays }.map { ArtistPlays(it.title, it.plays, it.lastfm, it.spotify, it.here, it.thumb, it.apple) }
    }

    private fun leadArtist(a: String) = a.split(Regex(",|;| feat\\.? | ft\\.? | featuring | with ")).map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.equals("unknown artist", true) } ?: ""

    /** Artist name's songs (theirs or with them), most played first. */
    fun artistSongs(name: String): List<Song> {
        val n = norm(name)
        return songs.filter { s -> s.artist.split(Regex(",|&| x | feat\\.? | ft\\.? ")).any { norm(it) == n } }
    }

    fun artist(name: String): ArtistPlays? = norm(name).let { n -> artists.firstOrNull { norm(it.name) == n } }
}
