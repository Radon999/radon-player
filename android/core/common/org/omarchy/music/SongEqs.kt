package org.omarchy.music

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Custom EQ (a song's menu, or several picked songs): a song's own Headphones correction, Sound preset (or bands) and
 *  Boost, kept for the output it was set on. Playing that song on that output, it's used instead of the output's EQ;
 *  anywhere else the song plays with the usual one. song-eq.json: {output: {song: its EQ}}. */
class SongEqs(private val file: File) {
    private val ser = MapSerializer(String.serializer(), MapSerializer(String.serializer(), OutputEq.serializer()))
    private val items: MutableMap<String, MutableMap<String, OutputEq>> =
        (readText(file)?.let { runCatching { JSON.decodeFromString(ser, it) }.getOrNull() } ?: emptyMap())
            .mapValues { it.value.toMutableMap() }.toMutableMap()

    private fun id(t: Track) = songKey(t).let { "${it.first}|${it.second}" }

    fun get(t: Track, output: String): OutputEq? = items[output]?.get(id(t))
    fun has(t: Track): Boolean { val k = id(t); return items.values.any { k in it } }
    /** The outputs t has its own EQ on (their keys). */
    fun outputsOf(t: Track): List<String> { val k = id(t); return items.filterValues { k in it }.keys.toList() }

    @Synchronized
    fun set(tracks: List<Track>, output: String, e: OutputEq?) {
        val m = items.getOrPut(output) { mutableMapOf() }
        for (t in tracks) if (e == null) m.remove(id(t)) else m[id(t)] = e
        items.entries.removeAll { it.value.isEmpty() }
        writeAtomic(file, JSON.encodeToString(ser, items.mapValues { it.value.toMap() }))
    }
}
