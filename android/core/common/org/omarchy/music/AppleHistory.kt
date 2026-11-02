package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/** What you played on Apple Music, as the desktop reads it: the zip from Apple's privacy page
 *  (Apple Media Services information), one of its .csv files, or a library exported from the
 *  Music app (File › Library › Export Library…). A row is a play (Play Activity: it counts from
 *  half the song), a day's plays of a song (Play History Daily Tracks: t is the day, ms the day
 *  long), or a song's all-time count from a library (t 0). Most played, the Artists tab and
 *  Wrapped count these with your scrobbles and Spotify's, each play once. */
object AppleHistory {
    @Serializable
    data class Row(val t: Long, val artist: String, val title: String, val album: String = "", val ms: Long = 0, val count: Int = 1)

    @Serializable
    private data class Saved(val rows: List<Row> = emptyList())

    private lateinit var file: File
    private var saved: Saved? = null

    @Synchronized private fun stored(): Saved = saved ?: (readText(file)?.let {
        runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } ?: Saved()).also { saved = it; count = it.rows.sumOf { r -> r.count } }

    val rows: List<Row> get() = stored().rows
    var count by mutableStateOf(0)             // plays, for Settings
        private set
    var busy by mutableStateOf<String?>(null)
        private set

    fun init(c: Ctx) {
        file = File(c.filesDir, "apple-history.json")
        Music.bg({ stored() })
    }

    /** Each play with its time, as Spotify's are (a day's plays: that many, over the day). */
    fun timed(): List<SpotifyHistory.Play> = rows.filter { it.t > 0 }.flatMap { r ->
        List(r.count) { SpotifyHistory.Play(r.t, r.artist, r.title, r.album, r.ms) }
    }

    /** Add rows; one you have already (the same song at the same time) keeps the higher count. How many plays were new. */
    @Synchronized private fun add(new: List<Row>): Int {
        val old = stored()
        val have = LinkedHashMap<Triple<Long, String, String>, Row>()
        for (r in old.rows) have[Triple(r.t, norm(r.artist), norm(cleanTitle(r.title)))] = r
        for (r in new) {
            val k = Triple(r.t, norm(r.artist), norm(cleanTitle(r.title)))
            val o = have[k]
            if (o == null || r.count > o.count) have[k] = if (o == null) r else o.copy(count = r.count, album = o.album.ifEmpty { r.album })
        }
        val before = old.rows.sumOf { it.count }
        val s = Saved(have.values.sortedBy { it.t })
        saved = s
        writeAtomic(file, JSON.encodeToString(Saved.serializer(), s))
        count = s.rows.sumOf { it.count }
        return count - before
    }

    @Synchronized fun clear() {
        saved = Saved()
        file.delete()
        count = 0
    }

    // ---- reading Apple's files ----

    /** The rows of a CSV (quoted fields with commas and line breaks in them). */
    internal fun csv(text: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            when {
                quoted && c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += cell.toString(); cell.setLength(0) }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    if (row.any { it.isNotEmpty() }) out += row
                    row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); if (row.any { it.isNotEmpty() }) out += row }
        return out
    }

    private fun stamp(v: String): Long {
        val x = v.trim()
        x.toLongOrNull()?.let { return if (it > 100_000_000_000L) it / 1000 else it }
        return runCatching { Instant.parse(x).epochSecond }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(x).toEpochSecond() }.getOrNull() ?: 0
    }

    /** "Artist - Title" (Apple's Track Description) into (artist, title). */
    internal fun split(d: String): Pair<String, String> {
        val i = d.indexOf(" - ")
        return if (i < 0) "" to d.trim() else d.substring(0, i).trim() to d.substring(i + 3).trim()
    }

    /** The rows in one of Apple's CSV files, by its name; none for files without plays. */
    internal fun parseCsv(name: String, text: String): List<Row> {
        val table = csv(text)
        if (table.isEmpty()) return emptyList()
        val head = table[0].map { it.trim() }
        fun List<String>.col(vararg keys: String): String {
            for (k in keys) { val i = head.indexOf(k); if (i >= 0 && i < size && this[i].isNotBlank()) return this[i].trim() }
            return ""
        }
        val low = name.lowercase()
        val out = ArrayList<Row>()
        for (r in table.drop(1)) when {
            "play activity" in low -> {
                val title = r.col("Song Name", "Content Name"); val artist = r.col("Artist Name", "Container Artist Name")
                val played = r.col("Play Duration Milliseconds").toDoubleOrNull()?.toLong() ?: 0
                val length = r.col("Media Duration In Milliseconds").toDoubleOrNull()?.toLong() ?: 0
                if (title.isEmpty() || artist.isEmpty() || played <= 0) continue
                if (if (length > 0) played < length / 2 else played < 30_000) continue   // a play counts from half the song
                val t = stamp(r.col("Event Start Timestamp", "Event Received Timestamp", "Event End Timestamp"))
                if (t > 0) out += Row(t, artist, title, r.col("Album Name", "Container Album Name"), played, 1)
            }
            "daily tracks" in low -> {
                val (artist, title) = split(r.col("Track Description"))
                val n = r.col("Play Count").toDoubleOrNull()?.toInt() ?: 0
                val day = runCatching { LocalDate.parse(r.col("Date Played"), DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay().toEpochSecond(ZoneOffset.UTC) }.getOrNull() ?: continue
                if (artist.isNotEmpty() && title.isNotEmpty() && n > 0) out += Row(day, artist, title, "", 86_400_000L, n)
            }
            "track play history" in low -> {
                val (artist, title) = split(r.col("Track Name", "Track Description"))
                val t = stamp(r.col("Last Played Date", "Play Date"))
                if (artist.isNotEmpty() && title.isNotEmpty() && t > 0) out += Row(t, artist, title, "", 0, 1)
            }
        }
        return out
    }

    /** A library the Music app exported (.xml, a plist): each song's all-time Play Count. */
    internal fun parseLibrary(input: InputStream): List<Row> {
        val p = xmlParser()
        p.setInput(input, null)
        val out = ArrayList<Row>()
        var depth = 0; var key = ""; var song = HashMap<String, String>(); var inSong = false
        while (p.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    if (p.name == "dict") { depth++; if (depth == 3) { inSong = true; song = HashMap() } }
                    else if (inSong && depth == 3) {
                        val tag = p.name
                        val text = if (tag == "true" || tag == "false") tag else p.nextText()
                        if (tag == "key") key = text else song[key] = text
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> if (p.name == "dict") {
                    if (depth == 3 && inSong) {
                        val n = song["Play Count"]?.toIntOrNull() ?: 0
                        val title = song["Name"].orEmpty(); val artist = song["Artist"].orEmpty()
                        if (n > 0 && title.isNotEmpty() && artist.isNotEmpty())
                            out += Row(0, artist, title, song["Album"].orEmpty(), song["Total Time"]?.toLongOrNull() ?: 0, n)
                        inSong = false
                    }
                    depth--
                }
            }
        }
        return out
    }

    private fun interesting(name: String) = name.lowercase().let { n ->
        n.endsWith(".csv") && ("play activity" in n || "daily tracks" in n || "track play history" in n)
    }

    private fun readZip(input: InputStream, found: HashMap<String, List<Row>>) {
        val z = ZipInputStream(input)
        while (true) {
            val e = z.nextEntry ?: break
            if (e.isDirectory) continue
            val name = e.name.substringAfterLast('/')
            if (interesting(name)) { busy = "Reading $name…"; found[name] = parseCsv(name, z.readBytes().toString(Charsets.UTF_8)) }
            else if (name.lowercase().endsWith(".zip")) readZip(ByteArrayInputStream(z.readBytes()), found)   // Apple's zips hold zips
        }
    }

    /** Read Apple's zip, one of its .csv files, or a library .xml at uri; how many plays were new (blocking). */
    fun importFile(ctx: Ctx, uri: String): Int {
        busy = "Reading your Apple Music history…"
        try {
            val found = HashMap<String, List<Row>>()
            val name = ctx.displayName(uri) ?: uri.substringBefore('?').substringAfterLast('/')
            BufferedInputStream(ctx.openIn(uri) ?: error("couldn't open that file")).use { s ->
                s.mark(4)
                val head = ByteArray(2).let { if (s.read(it) == 2) it else ByteArray(2) }
                s.reset()
                when {
                    head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> readZip(s, found)
                    name.lowercase().endsWith(".xml") || head[0] == '<'.code.toByte() -> found[name] = parseLibrary(s)
                    else -> found[name] = parseCsv(name, s.readBytes().toString(Charsets.UTF_8))
                }
            }
            // The most detailed file wins: each play over each day's over the list.
            val rows = listOf("play activity", "daily tracks", "track play history")
                .firstNotNullOfOrNull { k -> found.filterKeys { k in it.lowercase() }.values.flatten().ifEmpty { null } }
                ?: found.values.flatten()
            if (rows.isEmpty()) error("no plays in it. Apple's download has Apple Music Play Activity.csv (or Play History Daily " +
                "Tracks.csv); or export your library from the Music app (File › Library › Export Library…)")
            busy = "Adding ${"%,d".format(rows.sumOf { it.count })} plays…"
            return add(rows)
        } finally {
            busy = null
        }
    }
}
