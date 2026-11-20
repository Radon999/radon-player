package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Hide from stats ([StatHideRules]): the entries you hid from the stats lists, each with the name it was hidden
 *  under, kept in stat-hides.json on this device. */
object StatHides {
    private val ser = MapSerializer(String.serializer(), String.serializer())
    private var file: File? = null
    @Volatile private var hidden: Map<String, String> = emptyMap()

    /** Goes up at every change, so the lists are made again. */
    var version by mutableStateOf(0)
        private set

    fun init(c: Ctx) {
        val f = File(appCtx(c).filesDir, "stat-hides.json")
        file = f
        hidden = readText(f)?.let { runCatching { JSON.decodeFromString(ser, it) }.getOrNull() } ?: emptyMap()
    }

    private fun keep(now: Map<String, String>) {
        hidden = now
        version++
        val f = file ?: return
        Music.bg({ writeAtomic(f, JSON.encodeToString(ser, now)) })
    }

    val any: Boolean get() = hidden.isNotEmpty()
    fun song(t: Track): Boolean = hidden.isNotEmpty() && StatHideRules.song(t.title, t.artist) in hidden
    fun artist(name: String): Boolean = hidden.isNotEmpty() && StatHideRules.artist(name) in hidden
    fun album(title: String, artist: String): Boolean = hidden.isNotEmpty() && StatHideRules.album(title, artist) in hidden

    fun hideSongs(tracks: List<Track>) = keep(hidden + tracks.associate { StatHideRules.song(it.title, it.artist) to "${it.title} · ${it.artist}" })
    fun hideArtist(name: String) = keep(hidden + (StatHideRules.artist(name) to name))
    fun hideAlbum(title: String, artist: String) = keep(hidden + (StatHideRules.album(title, artist) to "$title · $artist"))

    /** Everything hidden: its key and its name, by kind then name. */
    fun all(): List<Pair<String, String>> = hidden.toList().sortedWith(compareBy({ it.first.take(2) }, { norm(it.second) }))
    fun show(key: String) = keep(hidden - key)
    fun showAll() = keep(emptyMap())
}
