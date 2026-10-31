package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Each of your song files' format: its kind (FLAC, MP3, M4A, WAV…), and for FLAC and WAV its bit depth and sample rate,
 *  read from the start of the file once and kept, so only new songs are read again. For the mark at the end of each
 *  song's row ("FLAC 24", "MP3") and for 24-bit FLACs and 32-bit FLACs (☰ menu). */
object FlacDepths {
    /** A song file's format: its kind ("flac", "mp3"…; "" unknown), bits per sample and sample rate (0: not known). */
    data class Format(val bits: Int, val rate: Int, val kind: String = "flac")

    private val known = ConcurrentHashMap<String, Format>()
    private var loaded = false
    private val file get() = File(Music.ctx.filesDir, "song-formats.json")
    private val ser = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    var version by mutableStateOf(0)   // changes as songs are read
        private set
    var reading by mutableStateOf<Pair<Int, Int>?>(null)   // (read, of) while reading
        private set

    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        runCatching { JSON.decodeFromString(ser, file.readText()) }.getOrNull()?.forEach { (k, v) ->
            if (v.size == 3) known[k] = Format(v[0].toIntOrNull() ?: 0, v[1].toIntOrNull() ?: 0, v[2])
        }
    }

    /** "24-bit · 96 kHz" */
    fun label(f: Format): String = "${f.bits}-bit · ${"%.1f".format(java.util.Locale.ROOT, f.rate / 1000.0).removeSuffix(".0")} kHz"

    /** The mark for a song file of yours: "FLAC 24", "WAV 16", "MP3", "M4A"; null while it hasn't been read. */
    fun badge(uri: String?): String? {
        load()
        val f = known[uri ?: return null] ?: return null
        if (f.kind.isEmpty()) return null
        return f.kind.uppercase() + if (f.bits > 0 && f.kind in setOf("flac", "wav", "aiff", "aif")) " ${f.bits}" else ""
    }

    /** Your FLACs of bits per sample (24 or 32), with their formats, the highest sample rate first. */
    fun songs(bits: Int): List<Pair<Track, Format>> {
        load()
        return Music.mySongs().mapNotNull { t -> known[t.file ?: return@mapNotNull null]?.takeIf { it.kind == "flac" && it.bits == bits }?.let { t to it } }
            .sortedWith(compareByDescending<Pair<Track, Format>> { it.second.rate }.thenBy { norm(it.first.artist) }.thenBy { norm(it.first.title) })
    }

    private val MIME = mapOf("flac" to "flac", "mpeg" to "mp3", "mp3" to "mp3", "mp4" to "m4a", "x-m4a" to "m4a", "aac" to "aac",
        "wav" to "wav", "x-wav" to "wav", "ogg" to "ogg", "opus" to "opus", "aiff" to "aiff", "x-aiff" to "aiff", "x-ms-wma" to "wma")

    /** The kind of a file, by its name (or, on the phone, Android's type for it). */
    private fun kindOf(uri: String): String {
        val name = runCatching { Music.ctx.displayName(uri) }.getOrNull() ?: uri
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 }
        if (ext != null) return if (ext == "mp4") "m4a" else ext
        val mime = runCatching { Music.ctx.mimeOf(uri) }.getOrNull()?.substringAfter('/')?.lowercase() ?: return ""
        return MIME[mime] ?: ""
    }

    /** (bits, rate) from a FLAC's or a WAV's first bytes, or null. */
    private fun head(uri: String): Pair<Int, Int>? = runCatching {
        Music.ctx.openIn(uri)?.use { s ->
            val b = ByteArray(64)
            var n = 0
            while (n < b.size) { val r = s.read(b, n, b.size - n); if (r <= 0) break; n += r }
            when {
                n >= 42 && String(b, 0, 4, Charsets.ISO_8859_1) == "fLaC" -> Flac.streamFormat(b.inputStream())
                n >= 36 && String(b, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(b, 12, 4, Charsets.ISO_8859_1) == "fmt " -> {
                    fun le(i: Int, k: Int) = (0 until k).sumOf { (b[i + it].toInt() and 0xff) shl (8 * it) }
                    le(34, 2) to le(24, 4)
                }
                else -> null
            }
        }
    }.getOrNull()

    /** (blocking) Read the songs not read yet. */
    fun scan() {
        load()
        val todo = (Music.mySongs().mapNotNull { it.file } + Music.lib.items.values.map { it.file }).distinct()
            .filter { !known.containsKey(it) && !it.startsWith("http") }
        if (todo.isEmpty()) return
        var n = 0
        reading = 0 to todo.size
        try {
            for (f in todo) {
                val kind = kindOf(f)
                val (bits, rate) = if (kind in setOf("flac", "wav") || kind.isEmpty()) head(f) ?: (0 to 0) else 0 to 0
                known[f] = Format(bits, rate, kind.ifEmpty { if (bits > 0) "flac" else "" })
                n++
                if (n % 50 == 0) { reading = n to todo.size; version++ }
            }
        } finally {
            runCatching { writeAtomic(file, JSON.encodeToString(ser, known.mapValues { listOf(it.value.bits.toString(), it.value.rate.toString(), it.value.kind) })) }
            reading = null
            version++
        }
    }
}
