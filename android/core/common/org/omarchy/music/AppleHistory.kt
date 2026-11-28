package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
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
        exactFile = File(c.filesDir, "apple-plays-exact.json")
        Music.bg({ stored(); exactStored(); readLengths() })
    }

    // ---- each play with its own time, for Repeats ----
    // The Play Activity file's plays: when each began and how long it ran. Many downloads leave the artist out of that
    // file, so its rows were skipped and Most played fell back to the per-day file, which can't show a repeat. They are
    // kept here on their own (the artist found by the song's title, AppleArtistRules) and only Repeats reads them: Most
    // played, Artists and Wrapped go on counting the rows above, so nothing is counted twice.
    private lateinit var exactFile: File
    private var exactKept: Saved? = null
    @Synchronized private fun exactStored(): Saved = exactKept ?: (readText(exactFile)?.let {
        runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } ?: Saved()).also { exactKept = it; exactCount = it.rows.size }
    var exactCount by mutableStateOf(0)        // plays with their own time, for Settings
        private set

    /** The plays Repeats can look through: the Play Activity file's when there are any, else the rows above that have
     *  a play's own time and length (not a day's count, not an all-time count). */
    fun forRepeats(): List<SpotifyHistory.Play> =
        exactStored().rows.ifEmpty { rows.filter { it.t > 0 && it.count == 1 && it.ms in 1 until 86_400_000L } }
            .map { SpotifyHistory.Play(it.t, it.artist, it.title, it.album, it.ms) }

    /** How long a song is, in seconds, by the plays here that have their own length (0 if none do): tells your file of
     *  a song named here under another artist ([SameLengthRules]). No reading: made when the history is read. */
    fun lengthOf(title: String, artist: String): Double = lengths[statsKey(title, artist)] ?: 0.0
    @Volatile private var lengths: Map<Pair<String, String>, Double> = emptyMap()
    @Volatile private var lengthsRead = false
    /** (blocking, off the main thread) The lengths read, if they weren't yet: the stats are added up with them. */
    fun ensureLengths() { if (!lengthsRead) readLengths() }
    private fun readLengths() {
        lengths = runCatching {
            exactStored().rows.ifEmpty { rows.filter { it.t > 0 && it.count == 1 } }
                .groupBy({ statsKey(it.title, it.artist) }, { it.ms })
                .mapValues { SameLengthRules.usual(it.value) }.filterValues { it > 0 }.also { lengthsRead = true }
        }.getOrDefault(emptyMap())
    }

    @Synchronized private fun keepExact(plays: List<Row>) {
        val s = Saved(plays.distinctBy { Triple(it.t, norm(it.artist), norm(cleanTitle(it.title))) }.sortedBy { it.t })
        exactKept = s
        writeAtomic(exactFile, JSON.encodeToString(Saved.serializer(), s))
        exactCount = s.rows.size
        readLengths()
    }

    /** Each play with its time, as Spotify's are (a day's plays: that many, over the day). */
    fun timed(): List<SpotifyHistory.Play> = rows.filter { it.t > 0 }.flatMap { r ->
        // (a day's count has the whole day as its "length": that isn't how long the song played, so it carries none.
        // Counted as a length, your Apple Music days made Wrapped's minutes and the listening times many times too big.)
        val ms = if (r.count > 1 || r.ms >= 86_400_000L) 0L else r.ms
        if (r.ms >= 86_400_000L) {
            // A day's count ("Daily Tracks": the day only, kept as its midnight in UTC): on that day where you are, and
            // spread over it. They had all been at UTC midnight: the evening before west of London (the wrong day for the
            // busiest day, a streak, New Year's), and every one in a single hour (making you a Night owl)
            val day = java.time.Instant.ofEpochSecond(r.t).atZone(ZoneOffset.UTC).toLocalDate()
            val from = day.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
            List(r.count) { i -> SpotifyHistory.Play(from + ((i + 0.5) * 86_400 / r.count).toLong(), r.artist, r.title, r.album, ms) }
        } else List(r.count) { SpotifyHistory.Play(r.t, r.artist, r.title, r.album, ms) }
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
        exactKept = Saved()
        exactFile.delete()
        exactCount = 0
        lengths = emptyMap()
    }

    // ---- reading Apple's files ----

    /** The rows of a CSV (quoted fields with commas and line breaks in them). */
    internal fun csv(text: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        csvRows(java.io.StringReader(text)) { out += it }
        return out
    }

    /** Each row of a CSV read from r, one at a time, so a file of hundreds of megabytes (Apple's Play Activity) is never
     *  held in memory whole: the whole file read at once ran the phone out of memory. */
    internal fun csvRows(r: java.io.Reader, each: (List<String>) -> Unit) {
        val inp = if (r is java.io.BufferedReader) r else java.io.BufferedReader(r, 1 shl 16)
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var first = true
        var c = inp.read()
        while (c >= 0) {
            val ch = c.toChar()
            if (first) { first = false; if (ch == '\uFEFF') { c = inp.read(); continue } }
            when {
                quoted && ch == '"' -> {
                    val nx = inp.read()
                    if (nx == '"'.code) cell.append('"') else { quoted = false; c = nx; continue }
                }
                // A quote opens a quoted field only at its start: one inside a field (a 12" mix) is just a character.
                // Without this, one stray quote swallowed the rest of the file into a single cell, which on a big
                // download ran the phone out of memory.
                ch == '"' && !quoted && cell.isEmpty() -> quoted = true
                quoted && cell.length > 200_000 -> { quoted = false; continue }   // a quote never closed: give up on it
                !quoted && ch == ',' -> { row += cell.toString(); cell.setLength(0) }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    row += cell.toString(); cell.setLength(0)
                    if (row.any { it.isNotEmpty() }) each(row)
                    row = ArrayList()
                    if (ch == '\r') { val nx = inp.read(); if (nx != '\n'.code) { c = nx; continue } }
                }
                else -> cell.append(ch)
            }
            c = inp.read()
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); if (row.any { it.isNotEmpty() }) each(row) }
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
    internal fun parseCsv(name: String, text: String): List<Row> = parseCsv(name, java.io.StringReader(text))

    /** The same, read a row at a time from input (it isn't closed). */
    internal fun parseCsv(name: String, input: java.io.Reader): List<Row> {
        var head: List<String>? = null
        fun List<String>.col(vararg keys: String): String {
            val h = head ?: return ""
            for (k in keys) { val i = h.indexOf(k); if (i >= 0 && i < size && this[i].isNotBlank()) return this[i].trim() }
            return ""
        }
        val low = name.lowercase()
        val out = ArrayList<Row>()
        csvRows(input) { r -> if (head == null) { head = r.map { it.trim() }; return@csvRows }
        when {
            "play activity" in low -> {
                val title = r.col("Song Name", "Content Name"); val artist = r.col("Artist Name", "Container Artist Name")
                val played = r.col("Play Duration Milliseconds").toDoubleOrNull()?.toLong() ?: 0
                val length = r.col("Media Duration In Milliseconds").toDoubleOrNull()?.toLong() ?: 0
                // A row without its artist is kept (artist ""): many downloads leave it out of this file, and it is found by the title afterwards
                if (title.isEmpty() || played <= 0) return@csvRows
                if (if (length > 0) played < length / 2 else played < 30_000) return@csvRows   // a play counts from half the song
                val t = stamp(r.col("Event Start Timestamp", "Event Received Timestamp", "Event End Timestamp"))
                if (t > 0) out += Row(t, artist, title, r.col("Album Name", "Container Album Name"), played, 1)
            }
            "daily tracks" in low -> {
                val (artist, title) = split(r.col("Track Description"))
                val n = r.col("Play Count").toDoubleOrNull()?.toInt() ?: 0
                val day = runCatching { LocalDate.parse(r.col("Date Played"), DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay().toEpochSecond(ZoneOffset.UTC) }.getOrNull() ?: return@csvRows
                if (artist.isNotEmpty() && title.isNotEmpty() && n > 0) out += Row(day, artist, title, "", 86_400_000L, n)
            }
            "track play history" in low -> {
                val (artist, title) = split(r.col("Track Name", "Track Description"))
                val t = stamp(r.col("Last Played Date", "Play Date"))
                if (artist.isNotEmpty() && title.isNotEmpty() && t > 0) out += Row(t, artist, title, "", 0, 1)
            }
        } }
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
            // Read straight from the zip, a row at a time (never the whole file in memory)
            if (interesting(name)) { busy = "Reading $name…"; found[name] = parseCsv(name, java.io.InputStreamReader(z, Charsets.UTF_8)) }
            else if (name.lowercase().endsWith(".zip")) readZip(z, found)   // Apple's zips hold zips: read the inner one in place
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
                    else -> found[name] = parseCsv(name, java.io.InputStreamReader(s, Charsets.UTF_8))
                }
            }
            // The most detailed file wins: each play over each day's over the list. (A play that came without its artist
            // can't be counted here: it is only kept for Repeats, below.)
            val rows = listOf("play activity", "daily tracks", "track play history")
                .firstNotNullOfOrNull { k -> found.filterKeys { k in it.lowercase() }.values.flatten().filter { it.artist.isNotEmpty() }.ifEmpty { null } }
                ?: found.values.flatten().filter { it.artist.isNotEmpty() }
            // For Repeats: every play of the Play Activity file, its artist found by the title where the file left it out
            // (from this download's other files, and from what was imported before)
            val activity = found.filterKeys { "play activity" in it.lowercase() }.values.flatten()
            if (activity.isNotEmpty()) {
                busy = "Reading when each play began…"
                val named = found.filterKeys { "play activity" !in it.lowercase() }.values.flatten() + activity.filter { it.artist.isNotEmpty() } + stored().rows
                val by = AppleArtistRules.byTitle(named.map { Triple(it.title, it.artist, it.count) })
                val exact = activity.mapNotNull { r -> if (r.artist.isNotEmpty()) r else AppleArtistRules.artist(by, r.title).takeIf { it.isNotEmpty() }?.let { r.copy(artist = it) } }
                if (exact.isNotEmpty()) keepExact(exact)
            }
            if (rows.isEmpty()) error("no plays in it. Apple's download has Apple Music Play Activity.csv (or Play History Daily " +
                "Tracks.csv); or export your library from the Music app (File › Library › Export Library…)")
            busy = "Adding ${"%,d".format(rows.sumOf { it.count })} plays…"
            return add(rows)
        } finally {
            busy = null
        }
    }
}
