package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/** Plays imported from a service's own data file (YouTube Music's from Google Takeout, Tidal's from its privacy
 *  request), for Most played, Artists and Wrapped, as the Spotify, Apple Music and Deezer imports: kept in their own
 *  file, the same play never counted twice. */
abstract class PlaysImport(private val fileName: String, val label: String) {
    @Serializable
    private data class Saved(val plays: List<SpotifyHistory.Play> = emptyList())

    private lateinit var file: File
    private var saved: Saved? = null

    @Synchronized private fun stored(): Saved = saved ?: (readText(file)?.let {
        runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } ?: Saved()).also { saved = it; count = it.plays.size }

    val plays: List<SpotifyHistory.Play> get() = if (::file.isInitialized) stored().plays else emptyList()
    var count by mutableStateOf(0)
        private set
    var busy by mutableStateOf<String?>(null)
        protected set

    fun init(c: Ctx) {
        file = File(c.filesDir, fileName)
        Music.bg({ stored() })
    }

    private fun keep(s: Saved) {
        saved = s
        writeAtomic(file, JSON.encodeToString(Saved.serializer(), s))
        count = s.plays.size
    }

    /** The new plays added (one within two minutes of a play of the same song already here is that play); how many. */
    @Synchronized protected fun add(new: List<SpotifyHistory.Play>): Int {
        val old = stored()
        val have = HashMap<String, MutableList<SpotifyHistory.Play>>()
        for (p in old.plays) have.getOrPut(cleanTitle(p.title)) { ArrayList() } += p
        val fresh = new.filter { p ->
            val same = have.getOrPut(cleanTitle(p.title)) { ArrayList() }
            same.none { Math.abs(it.t - p.t) < 120 }.also { if (it) same += p }
        }
        if (fresh.isNotEmpty()) keep(Saved((old.plays + fresh).sortedBy { it.t }))
        return fresh.size
    }

    @Synchronized fun clear() = keep(Saved())

    /** (blocking) Read the file at uri; how many plays were new. */
    fun importFile(ctx: Ctx, uri: String): Int {
        busy = "Reading your $label history…"
        try {
            val out = ArrayList<SpotifyHistory.Play>()
            val head = ctx.openIn(uri)?.use { it.readNBytesCompat(4) } ?: error("couldn't open that file")
            if (head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {   // a zip: every file in it that fits
                ZipInputStream(ctx.openIn(uri)!!.buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (!e.isDirectory && wanted(e.name)) out += parse(e.name, z.readBytes().toString(Charsets.UTF_8))
                    }
                }
            } else out += parse(ctx.displayName(uri) ?: "file", ctx.openIn(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) })
            if (out.isEmpty()) error(emptyMessage)
            busy = "Adding the plays…"
            return add(out)
        } finally {
            busy = null
        }
    }

    protected abstract fun wanted(name: String): Boolean
    protected abstract fun parse(name: String, text: String): List<SpotifyHistory.Play>
    protected abstract val emptyMessage: String
}

private fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val b = ByteArray(n); var got = 0
    while (got < n) { val r = read(b, got, n - got); if (r < 0) break; got += r }
    return b.copyOf(got)
}

/** A moment as Takeout and Tidal write it ("2024-05-01T18:02:11.123Z", "2024-05-01 18:02:11", seconds or ms since
 *  1970), as seconds; 0 if it isn't one. */
internal fun whenOfAny(text: String): Long {
    val s = text.trim()
    if (s.isEmpty()) return 0
    runCatching { return Instant.parse(s).epochSecond }
    runCatching { return java.time.OffsetDateTime.parse(s).toEpochSecond() }
    runCatching { return LocalDateTime.parse(s.take(19).replace(' ', 'T')).atZone(ZoneId.systemDefault()).toEpochSecond() }
    runCatching { return LocalDateTime.parse(s.take(16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(ZoneId.systemDefault()).toEpochSecond() }
    val n = s.toLongOrNull() ?: return 0
    return if (n > 100_000_000_000L) n / 1000 else if (n > 1_000_000_000L) n else 0
}

/** YouTube Music's plays, from Google Takeout (YouTube and YouTube Music › history, as JSON): watch-history.json lists
 *  everything watched on YouTube; the entries from YouTube Music are the songs. */
object YouTubeHistory : PlaysImport("youtube-history.json", "YouTube Music") {
    override val emptyMessage = "no YouTube Music plays in it: in Takeout, pick YouTube and YouTube Music, then under " +
        "Multiple formats set history to JSON (watch-history.json is the file)"

    override fun wanted(name: String) = name.lowercase().let { it.endsWith(".json") && ("watch" in it || "history" in it) }

    override fun parse(name: String, text: String): List<SpotifyHistory.Play> {
        val all = runCatching { JSON.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
        return all.mapNotNull { e ->
            if (e["header"].str != "YouTube Music") return@mapNotNull null
            val title = e["title"].str?.replace(Regex("^(Watched|A regardé|Has visto|Hai guardato|Angesehen:|Assistiu a)\\s+"), "")?.trim()
                ?.takeIf { it.isNotEmpty() && !it.startsWith("https://") } ?: return@mapNotNull null
            val artist = e["subtitles"].list.firstOrNull()?.get("name").str?.removeSuffix(" - Topic")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val t = whenOfAny(e["time"].str ?: "").takeIf { it > 0 } ?: return@mapNotNull null
            SpotifyHistory.Play(t, artist, title)
        }
    }
}

/** Tidal's plays, from the copy of your data Tidal sends when you ask for it (a privacy request): its streaming history,
 *  as a CSV or JSON file (in the zip it comes in, or on its own). Read by its column names, whatever their order. */
object TidalHistory : PlaysImport("tidal-history.json", "Tidal") {
    override val emptyMessage = "no plays found in it: import the streaming history file (CSV or JSON) from the data Tidal sent you, or the zip it came in"

    private val TITLE = listOf("track_title", "track title", "trackname", "track name", "title", "track", "song_title", "song title", "song", "name")
    private val ARTIST = listOf("artist_name", "artist name", "artistname", "artists", "artist", "artist_names")
    private val ALBUM = listOf("album_title", "album title", "album_name", "album name", "album")
    private val WHEN = listOf("entry_date", "stream_start", "streamed_at", "played_at", "timestamp", "start_time", "time", "date", "datetime", "played")
    private val MS = listOf("stream_duration_ms", "ms_played", "duration_ms", "played_ms", "stream_duration")

    override fun wanted(name: String) = name.lowercase().let { (it.endsWith(".csv") || it.endsWith(".json")) && "__macosx" !in it }

    override fun parse(name: String, text: String): List<SpotifyHistory.Play> =
        if (name.lowercase().endsWith(".json") || text.trimStart().startsWith("[") || text.trimStart().startsWith("{")) fromJson(text) else fromCsv(text)

    private fun pick(keys: List<String>, names: List<String>): Int = names.firstNotNullOfOrNull { n -> keys.indexOfFirst { it == n }.takeIf { it >= 0 } } ?: -1

    private fun play(title: String?, artist: String?, album: String?, whenText: String?, ms: String?): SpotifyHistory.Play? {
        val ti = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val ar = artist?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val t = whenOfAny(whenText ?: "").takeIf { it > 0 } ?: return null
        val played = ms?.trim()?.toDoubleOrNull()?.toLong() ?: 0
        if (played in 1 until 30_000) return null   // under 30 seconds isn't a play (as Spotify counts)
        return SpotifyHistory.Play(t, ar, ti, album?.trim() ?: "", played)
    }

    private fun fromJson(text: String): List<SpotifyHistory.Play> {
        val root = runCatching { JSON.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val rows = (root as? JsonArray) ?: (root as? JsonObject)?.values?.firstOrNull { it is JsonArray } as? JsonArray ?: return emptyList()
        return rows.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            val low = o.mapKeys { it.key.lowercase() }
            fun f(names: List<String>): String? = names.firstNotNullOfOrNull { n -> low[n]?.let { v ->
                (v as? JsonArray)?.mapNotNull { x -> x.str ?: x["name"].str }?.joinToString(", ") ?: v.str ?: v["name"].str } }
            play(f(TITLE), f(ARTIST), f(ALBUM), f(WHEN), f(MS))
        }
    }

    private fun fromCsv(text: String): List<SpotifyHistory.Play> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return emptyList()
        val first = lines.next()
        val sep = if (first.count { it == ';' } > first.count { it == ',' }) ';' else if ('\t' in first) '\t' else ','
        val head = cells(first, sep).map { it.trim().lowercase().removePrefix("﻿") }
        val ti = pick(head, TITLE); val ar = pick(head, ARTIST); val al = pick(head, ALBUM); val wh = pick(head, WHEN); val ms = pick(head, MS)
        if (ti < 0 || ar < 0 || wh < 0) return emptyList()
        val out = ArrayList<SpotifyHistory.Play>()
        for (l in lines) {
            val c = cells(l, sep)
            play(c.getOrNull(ti), c.getOrNull(ar), if (al >= 0) c.getOrNull(al) else null, c.getOrNull(wh), if (ms >= 0) c.getOrNull(ms) else null)?.let { out += it }
        }
        return out
    }

    /** A CSV line's cells ("quoted, with commas" and "" inside quotes kept together). */
    private fun cells(line: String, sep: Char): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                q && ch == '"' && line.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                ch == sep && !q -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}

/** Every imported play that isn't Spotify's or Apple Music's (they're counted on their own): Deezer's, YouTube Music's
 *  and Tidal's, for Most played, Artists, Wrapped, the DJ and Repeat. */
object OtherPlays {
    val all: List<SpotifyHistory.Play> get() = DeezerHistory.plays + YouTubeHistory.plays + TidalHistory.plays
    val count: Int get() = DeezerHistory.count + YouTubeHistory.count + TidalHistory.count
    /** Which services they came from, for the label over Most played. */
    val names: List<String> get() = listOfNotNull("Deezer".takeIf { DeezerHistory.count > 0 }, "YouTube Music".takeIf { YouTubeHistory.count > 0 },
        "Tidal".takeIf { TidalHistory.count > 0 })
}
