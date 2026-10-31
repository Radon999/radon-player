package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 24-bit FLACs and 32-bit FLACs (☰ menu): each of your songs' bit depth and sample rate, read from the start of its
 *  file (FLAC's STREAMINFO) once and kept, so only new songs are read again. */
object FlacDepths {
    /** A song file's format: bits per sample and sample rate (0, 0: not a FLAC). */
    data class Format(val bits: Int, val rate: Int)

    private val known = ConcurrentHashMap<String, Format>()
    private var loaded = false
    private val file get() = File(Music.ctx.filesDir, "flac-depths.json")
    private val ser = MapSerializer(String.serializer(), ListSerializer(Int.serializer()))

    var version by mutableStateOf(0)   // changes as songs are read
        private set
    var reading by mutableStateOf<Pair<Int, Int>?>(null)   // (read, of) while reading
        private set

    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        runCatching { JSON.decodeFromString(ser, file.readText()) }.getOrNull()
            ?.forEach { (k, v) -> if (v.size == 2) known[k] = Format(v[0], v[1]) }
    }

    /** "24-bit · 96 kHz" */
    fun label(f: Format): String = "${f.bits}-bit · ${"%.1f".format(java.util.Locale.ROOT, f.rate / 1000.0).removeSuffix(".0")} kHz"

    /** Your FLACs of bits per sample (24 or 32), with their formats, the highest sample rate first. */
    fun songs(bits: Int): List<Pair<Track, Format>> {
        load()
        return Music.mySongs().mapNotNull { t -> known[t.file ?: return@mapNotNull null]?.takeIf { it.bits == bits }?.let { t to it } }
            .sortedWith(compareByDescending<Pair<Track, Format>> { it.second.rate }.thenBy { norm(it.first.artist) }.thenBy { norm(it.first.title) })
    }

    /** (blocking) Read the songs not read yet. */
    fun scan() {
        load()
        val todo = Music.mySongs().mapNotNull { it.file }.distinct().filter { !known.containsKey(it) && !it.startsWith("http") }
        if (todo.isEmpty()) return
        var n = 0
        reading = 0 to todo.size
        try {
            for (f in todo) {
                val looks = f.startsWith("content:") || f.endsWith(".flac", ignoreCase = true)   // the PC: only .flac files
                known[f] = if (!looks) Format(0, 0) else runCatching {
                    Music.ctx.openIn(f)?.use { Flac.streamFormat(it) }?.let { (b, r) -> Format(b, r) }
                }.getOrNull() ?: Format(0, 0)
                n++
                if (n % 50 == 0) { reading = n to todo.size; version++ }
            }
        } finally {
            runCatching { writeAtomic(file, JSON.encodeToString(ser, known.mapValues { listOf(it.value.bits, it.value.rate) })) }
            reading = null
            version++
        }
    }
}
