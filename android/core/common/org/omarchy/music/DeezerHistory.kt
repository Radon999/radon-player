package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/** What you played on Deezer, for Most played, Artists and Wrapped, as the Spotify and Apple Music imports: from the
 *  file Deezer's privacy settings give you (My account › Privacy settings › My personal data), an .xlsx whose
 *  "10_listeningHistory" sheet lists every play (or the zip it came in). A play counts from 30 seconds; the same play
 *  isn't counted twice. The desktop's DeezerHistory. */
object DeezerHistory {
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
        private set

    fun init(c: Ctx) {
        file = File(c.filesDir, "deezer-history.json")
        Music.bg({ stored() })
    }

    private fun keep(s: Saved) {
        saved = s
        writeAtomic(file, JSON.encodeToString(Saved.serializer(), s))
        count = s.plays.size
    }

    private val SpotifyHistory.Play.end get() = t + ms / 1000

    @Synchronized private fun add(new: List<SpotifyHistory.Play>): Int {
        val old = stored()
        val have = HashMap<String, MutableList<SpotifyHistory.Play>>()
        for (p in old.plays) have.getOrPut(cleanTitle(p.title)) { ArrayList() } += p
        val fresh = new.filter { p ->
            val same = have.getOrPut(cleanTitle(p.title)) { ArrayList() }
            same.none { Math.abs(it.t - p.t) < 120 || Math.abs(it.end - p.end) < 120 }.also { if (it) same += p }
        }
        if (fresh.isNotEmpty()) keep(Saved((old.plays + fresh).sortedBy { it.t }))
        return fresh.size
    }

    @Synchronized fun clear() = keep(Saved())

    private val TITLE = setOf("song title", "titre", "titre du morceau", "titre de la chanson", "song", "track", "track title",
        "track name", "song name", "title")
    private val ARTIST = setOf("artist", "artiste", "artists", "artistes", "artist name", "nom de l'artiste")
    private val ALBUM = setOf("album title", "album", "titre de l'album", "album name")
    private val WHEN = setOf("date", "date d'écoute", "date de l'écoute", "date d'ecoute", "date de l'ecoute", "listening date",
        "timestamp", "played at", "time", "datetime", "date and time")
    private val LENGTH = setOf("listening time", "temps d'écoute", "durée d'écoute", "duree d'ecoute", "écoute", "duration",
        "listening duration", "listening time (s)", "listening time (sec)", "seconds played", "durée")
    private val STAMPS = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm",
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy HH:mm", "dd.MM.yyyy HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss").map { DateTimeFormatter.ofPattern(it) }

    /** "2024-05-01 18:02:11" (your time, as Deezer writes it, or another common way), a spreadsheet's day number or a
     *  Unix time, as seconds; 0 if none. */
    internal fun whenOf(text: String): Long {
        val s = text.trim().removeSuffix("Z").substringBefore('.').let { if (it.length > 19 && it[19] in "+-") it.take(19) else it }
        for (f in STAMPS) runCatching { return LocalDateTime.parse(s, f).atZone(ZoneId.systemDefault()).toEpochSecond() }
        runCatching { return LocalDate.parse(s.take(10)).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() }
        val n = text.trim().toDoubleOrNull() ?: return 0
        if (n > 1e11) return (n / 1000).toLong()   // Unix time in milliseconds
        if (n > 1e9) return n.toLong()             // in seconds
        if (n < 20000) return 0
        val base = LocalDate.of(1899, 12, 30).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        return base + Math.round(n * 86400)
    }

    internal fun secondsOf(text: String): Double {
        val s = text.trim().replace(',', '.')
        s.toDoubleOrNull()?.let { return it }
        val parts = s.split(':').map { it.toDoubleOrNull() ?: return 0.0 }
        return when (parts.size) { 2 -> parts[0] * 60 + parts[1]; 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; else -> 0.0 }
    }

    /** The plays in the sheet's rows (the header row first, in English or French). */
    internal fun parseRows(rows: Sequence<List<String>>): List<SpotifyHistory.Play> {
        val out = ArrayList<SpotifyHistory.Play>()
        var cols: Map<String, Int?>? = null
        for (row in rows) {
            val c = cols
            if (c == null) {
                val low = row.map { it.trim().trimStart('\uFEFF').lowercase().replace(Regex("\\s+"), " ") }
                fun pick(names: Set<String>) = low.indexOfFirst { it in names }.takeIf { it >= 0 }
                val found = mapOf("title" to pick(TITLE), "artist" to pick(ARTIST), "album" to pick(ALBUM),
                    "when" to pick(WHEN), "length" to pick(LENGTH))
                if (found["title"] != null && found["artist"] != null && found["when"] != null) cols = found
                continue
            }
            fun cell(k: String) = c[k]?.let { row.getOrNull(it) }.orEmpty().trim()
            val title = cell("title"); val artist = cell("artist")
            val t = whenOf(cell("when"))
            val secs = if (c["length"] != null) secondsOf(cell("length")) else 30.0
            if (title.isNotEmpty() && artist.isNotEmpty() && t > 0 && secs >= 30)
                out += SpotifyHistory.Play(t, artist, title, cell("album"), Math.round(secs * 1000))
        }
        return out
    }

    /** Each entry of the zip at uri, read one after another (the .xlsx is a zip). */
    private fun eachEntry(ctx: Ctx, uri: String, use: (String, InputStream) -> Unit) {
        val input = ctx.openIn(uri) ?: error("couldn't open that file")
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) use(e.name, object : java.io.FilterInputStream(z) { override fun close() {} })
            }
        }
    }

    private fun parser(input: InputStream): XmlPullParser = xmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(input, null) }

    /** A tag's name without its prefix ("x:row" → "row"): some spreadsheet writers put one on every tag. */
    private val XmlPullParser.local: String get() = name.substringAfter(':')
    private fun XmlPullParser.attr(n: String): String? =
        (0 until attributeCount).firstOrNull { getAttributeName(it).substringAfter(':') == n }?.let { getAttributeValue(it) }

    /** The .xlsx's sheets as rows of cell texts, the listening history first (by its name); null if it isn't an .xlsx. */
    private fun xlsxSheets(ctx: Ctx, uri: String): List<List<List<String>>>? {
        // First look: the shared strings, the sheets' names and their files.
        val shared = ArrayList<String>()
        val named = ArrayList<Pair<String, String>>()   // name, r:id
        val rels = HashMap<String, String>()
        val sheetFiles = ArrayList<String>()
        eachEntry(ctx, uri) { name, s ->
            when {
                name == "xl/sharedStrings.xml" -> {
                    val p = parser(s); val sb = StringBuilder(); var inSi = false; var inT = false; var inPh = false
                    while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
                        XmlPullParser.START_TAG -> when (p.local) { "si" -> { inSi = true; sb.setLength(0) }; "t" -> inT = inSi && !inPh; "rPh" -> inPh = true }
                        XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                        XmlPullParser.END_TAG -> when (p.local) { "t" -> inT = false; "rPh" -> inPh = false; "si" -> { shared += sb.toString(); inSi = false } }
                    }
                }
                name == "xl/workbook.xml" -> {
                    val p = parser(s)
                    while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.local == "sheet")
                        named += p.attr("name").orEmpty() to p.attr("id").orEmpty()
                }
                name == "xl/_rels/workbook.xml.rels" -> {
                    val p = parser(s)
                    while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.local == "Relationship")
                        rels[p.attr("Id").orEmpty()] = p.attr("Target").orEmpty()
                }
                name.startsWith("xl/worksheets/") && name.endsWith(".xml") -> sheetFiles += name
            }
        }
        if (sheetFiles.isEmpty()) return null
        fun fileOf(id: String) = rels[id]?.trimStart('/')?.let { if (it.startsWith("xl/")) it else "xl/$it" }
        // The listening history's sheet first ("10_listeningHistory", or any name about listening), then the rest
        val first = named.filter { (n, _) -> n.lowercase().let { "listening" in it || "ecoute" in it || "écoute" in it || "history" in it } }
            .mapNotNull { fileOf(it.second) }
        val order = (first + sheetFiles).distinct()
        // Second look: each sheet, a row at a time.
        val sheets = HashMap<String, List<List<String>>>()
        eachEntry(ctx, uri) { name, s ->
            if (name !in order) return@eachEntry
            val rows = ArrayList<List<String>>()
            val p = parser(s)
            var row = HashMap<Int, String>(); var col = 0; var type = ""; var inV = false; val v = StringBuilder()
            while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.local) {
                    "row" -> row = HashMap()
                    "c" -> {
                        val ref = p.attr("r").orEmpty().takeWhile { it.isLetter() }.uppercase()
                        col = if (ref.isEmpty()) row.size else ref.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1
                        type = p.attr("t").orEmpty(); v.setLength(0)
                    }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) v.append(p.text)
                XmlPullParser.END_TAG -> when (p.local) {
                    "v", "t" -> inV = false
                    "c" -> row[col] = if (type == "s") shared.getOrElse(v.toString().trim().toIntOrNull() ?: -1) { "" } else v.toString()
                    "row" -> if (row.isNotEmpty()) rows += (0..row.keys.max()).map { row[it].orEmpty() }
                }
            }
            sheets[name] = rows
        }
        return order.mapNotNull { sheets[it] }
    }

    /** A .csv (or tab- or semicolon-separated) export's rows. */
    private fun csvRows(ctx: Ctx, uri: String): List<List<String>> {
        val text = (ctx.openIn(uri) ?: error("couldn't open that file")).use { it.readBytes().toString(Charsets.UTF_8) }
        val head = text.lineSequence().firstOrNull().orEmpty()
        val sep = listOf('\t', ';', ',').maxBy { c -> head.count { it == c } }
        val rows = ArrayList<List<String>>(); var cell = StringBuilder(); var row = ArrayList<String>(); var quoted = false; var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == sep -> { row += cell.toString(); cell = StringBuilder() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell = StringBuilder(); if (row.any { it.isNotBlank() }) rows += row; row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row += cell.toString(); if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    /** Whether the file at uri starts as a zip does (an .xlsx is one). */
    private fun isZip(ctx: Ctx, uri: String): Boolean = runCatching {
        ctx.openIn(uri)!!.use { val b = ByteArray(2); it.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
    }.getOrDefault(false)

    /** The plays in the first of these sheets that has a listening history's columns. */
    private fun playsIn(sheets: List<List<List<String>>>, where: String = ""): List<SpotifyHistory.Play>? {
        sheets.forEachIndexed { i, rows -> found += explain(rows, "$where sheet ${i + 1}".trim()) }
        return sheets.asSequence().map { parseRows(it.asSequence()) }.firstOrNull { it.isNotEmpty() }
    }

    /** What was read while importing, for the reason it failed: each sheet's columns and why its rows didn't count. */
    private val found = ArrayList<String>()
    /** The listening history's sheet, when one was found: its plays, those under 30 seconds and those with a date not understood. */
    private var history: Triple<Int, Int, Int>? = null

    /** Why rows gave no plays: no header row with a title, an artist and a date; or dates not understood; or plays too short. */
    internal fun explain(rows: List<List<String>>, what: String): String {
        if (rows.isEmpty()) return "$what: empty"
        var cols: Map<String, Int?>? = null
        var headerRow: List<String>? = null
        var good = 0; var badDate = 0; var short = 0; var empty = 0; var sampleDate = ""
        for (row in rows) {
            if (cols == null) {
                val low = row.map { it.trim().trimStart('\uFEFF').lowercase().replace(Regex("\\s+"), " ") }
                fun pick(names: Set<String>) = low.indexOfFirst { it in names }.takeIf { it >= 0 }
                val f = mapOf("title" to pick(TITLE), "artist" to pick(ARTIST), "when" to pick(WHEN), "length" to pick(LENGTH))
                if (f["title"] != null && f["artist"] != null && f["when"] != null) { cols = f; headerRow = row }
                continue
            }
            val c = cols!!
            fun cell(k: String) = c[k]?.let { row.getOrNull(it) }.orEmpty().trim()
            when {
                cell("title").isEmpty() || cell("artist").isEmpty() -> empty++
                whenOf(cell("when")) <= 0 -> { badDate++; if (sampleDate.isEmpty()) sampleDate = cell("when") }
                c["length"] != null && secondsOf(cell("length")) < 30 -> short++
                else -> good++
            }
        }
        if (cols == null) {
            val head = rows.firstOrNull { r -> r.count { it.isNotBlank() } >= 3 } ?: rows.first()
            return "$what (${rows.size} rows): no columns for a song's title, artist and date; its first row says " +
                head.filter { it.isNotBlank() }.take(8).joinToString(", ") { "“${it.take(30)}”" }
        }
        if (history == null) history = Triple(good + short + badDate + empty, short, badDate)
        return "$what (${rows.size} rows, columns ${headerRow.orEmpty().filter { it.isNotBlank() }.take(9).joinToString(", ")}): " +
            "$good plays, $short under 30 seconds, $badDate with a date not understood" +
            (if (sampleDate.isNotEmpty()) " (like “${sampleDate.take(40)}”)" else "") + ", $empty without a title or artist"
    }

    /** Read Deezer's .xlsx (or the zip it came in, or a .csv) at uri; how many plays were new (blocking). */
    fun importFile(ctx: Ctx, uri: String): Int {
        busy = "Reading your Deezer history…"
        found.clear(); history = null
        try {
            var out: List<SpotifyHistory.Play>? = null
            if (isZip(ctx, uri)) {
                out = xlsxSheets(ctx, uri)?.let { playsIn(it) }
                if (out == null) {   // the download's zip, with the .xlsx (or a .csv) in it
                    val tmp = File(ctx.cacheDir, "deezer-import.tmp")
                    try {
                        val inner = ArrayList<String>()
                        val names = ArrayList<String>()
                        eachEntry(ctx, uri) { name, _ -> names += name; val n = name.lowercase(); if (n.endsWith(".xlsx") || n.endsWith(".csv")) inner += name }
                        if (inner.isEmpty()) found += "the zip has no spreadsheet in it, only " + names.take(8).joinToString(", ") { it.substringAfterLast('/') }
                        for (want in inner.sortedByDescending { "listen" in it.lowercase() || "history" in it.lowercase() }) {
                            var got = false
                            eachEntry(ctx, uri) { name, s -> if (!got && name == want) { tmp.outputStream().use { s.copyTo(it) }; got = true } }
                            if (!got) continue
                            out = if (want.lowercase().endsWith(".csv")) csvRows(ctx, tmp.path).let { r -> found += explain(r, want.substringAfterLast('/')); parseRows(r.asSequence()).takeIf { it.isNotEmpty() } }
                                else xlsxSheets(ctx, tmp.path)?.let { playsIn(it, want.substringAfterLast('/')) }
                            if (out != null) break
                        }
                    } finally { tmp.delete() }
                }
            } else {
                val r = runCatching { csvRows(ctx, uri) }.getOrElse { error("couldn't read that file (${it.message ?: it::class.simpleName})") }
                found += "it isn't an .xlsx or a zip; read as text: " + explain(r, "the file")
                out = parseRows(r.asSequence()).takeIf { it.isNotEmpty() }
            }
            busy = "Adding the plays…"
            // The history was there but held nothing that counts: say so plainly (the file is right, it's what Deezer put in it)
            history?.let { (n, short, bad) -> if (out == null && bad == 0) error(
                if (n == 0) "the file is the right one, but its listening history is empty: Deezer didn't put any plays in it"
                else "the file is the right one, but Deezer put only $n play${if (n == 1) "" else "s"} in its listening history" +
                    (if (short > 0) ", ${if (short == n) (if (n == 1) "and it was" else "all") else "$short of them"} under 30 seconds (shorter plays don't count, as on Spotify)" else "") +
                    ". Radon Player doesn't report its plays to Deezer, so songs streamed from Deezer here aren't in that history (they're already in your stats)") }
            out ?: error("no listening history found in it. " + found.take(6).joinToString(". ").ifEmpty { "Nothing could be read" } +
                ". Pick the .xlsx (or the zip) from Deezer's “My personal data” download; only plays of 30 seconds or more count")
            return add(out)
        } finally {
            busy = null
        }
    }
}
