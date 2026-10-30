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

    private val TITLE = setOf("song title", "titre", "titre du morceau", "titre de la chanson")
    private val ARTIST = setOf("artist", "artiste", "artists", "artistes")
    private val ALBUM = setOf("album title", "album", "titre de l'album")
    private val WHEN = setOf("date", "date d'écoute", "date de l'écoute", "date d'ecoute", "date de l'ecoute")
    private val LENGTH = setOf("listening time", "temps d'écoute", "durée d'écoute", "duree d'ecoute", "écoute")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val STAMP_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** "2024-05-01 18:02:11" (your time, as Deezer writes it) or a spreadsheet's day number, as seconds; 0 if neither. */
    internal fun whenOf(text: String): Long {
        val s = text.trim()
        runCatching { return LocalDateTime.parse(s.take(19), STAMP).atZone(ZoneId.systemDefault()).toEpochSecond() }
        runCatching { return LocalDateTime.parse(s.take(16), STAMP_MIN).atZone(ZoneId.systemDefault()).toEpochSecond() }
        val days = s.toDoubleOrNull() ?: return 0
        if (days < 20000) return 0
        val base = LocalDate.of(1899, 12, 30).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        return base + Math.round(days * 86400)
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
                val low = row.map { it.trim().lowercase() }
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

    /** The .xlsx's listening history, as rows of cell texts; null if it has no such sheet. */
    private fun xlsxRows(ctx: Ctx, uri: String): List<List<String>>? {
        // First look: the shared strings, and which file the "…_listeningHistory" sheet is.
        val shared = ArrayList<String>()
        var sheetId: String? = null
        val rels = HashMap<String, String>()
        eachEntry(ctx, uri) { name, s ->
            when (name) {
                "xl/sharedStrings.xml" -> {
                    val p = parser(s); val sb = StringBuilder(); var inSi = false; var inT = false
                    while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
                        XmlPullParser.START_TAG -> when (p.name) { "si" -> { inSi = true; sb.setLength(0) }; "t" -> inT = inSi }
                        XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                        XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> { shared += sb.toString(); inSi = false } }
                    }
                }
                "xl/workbook.xml" -> {
                    val p = parser(s)
                    while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                        val n = p.getAttributeValue(null, "name").orEmpty()
                        if (n.lowercase().endsWith("_listeninghistory")) sheetId = p.getAttributeValue(null, "r:id")
                    }
                }
                "xl/_rels/workbook.xml.rels" -> {
                    val p = parser(s)
                    while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship")
                        rels[p.getAttributeValue(null, "Id").orEmpty()] = p.getAttributeValue(null, "Target").orEmpty()
                }
            }
        }
        val target = sheetId?.let { rels[it] }?.trimStart('/')?.let { if (it.startsWith("xl/")) it else "xl/$it" } ?: return null
        // Second look: the sheet itself, a row at a time.
        val rows = ArrayList<List<String>>()
        eachEntry(ctx, uri) { name, s ->
            if (name != target) return@eachEntry
            val p = parser(s)
            var row = HashMap<Int, String>(); var col = 0; var type = ""; var inV = false; val v = StringBuilder()
            while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = HashMap()
                    "c" -> {
                        val ref = p.getAttributeValue(null, "r").orEmpty().takeWhile { it.isLetter() }
                        col = if (ref.isEmpty()) row.size else ref.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1
                        type = p.getAttributeValue(null, "t").orEmpty(); v.setLength(0)
                    }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) v.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inV = false
                    "c" -> row[col] = if (type == "s") shared.getOrElse(v.toString().trim().toIntOrNull() ?: -1) { "" } else v.toString()
                    "row" -> if (row.isNotEmpty()) rows += (0..row.keys.max()).map { row[it].orEmpty() }
                }
            }
        }
        return rows
    }

    /** Read Deezer's .xlsx (or the zip it came in) at uri; how many plays were new (blocking). */
    fun importFile(ctx: Ctx, uri: String): Int {
        busy = "Reading your Deezer history…"
        try {
            var rows = xlsxRows(ctx, uri)
            if (rows == null) {   // the download's zip, with the .xlsx in it
                val tmp = File(ctx.cacheDir, "deezer-import.xlsx")
                var found = false
                eachEntry(ctx, uri) { name, s -> if (!found && name.lowercase().endsWith(".xlsx")) { tmp.outputStream().use { s.copyTo(it) }; found = true } }
                if (found) try { rows = xlsxRows(ctx, tmp.path) } finally { tmp.delete() }
            }
            rows ?: error("that isn't Deezer's personal data file: it's an .xlsx with a “10_listeningHistory” sheet")
            busy = "Adding the plays…"
            val out = parseRows(rows.asSequence())
            if (out.isEmpty()) error("no songs played for 30 seconds or more in it")
            return add(out)
        } finally {
            busy = null
        }
    }
}
