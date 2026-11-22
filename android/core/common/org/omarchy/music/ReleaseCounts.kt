package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.launch
import java.io.File

/** How much of an artist's songs, or of an album's, you have on this device and how much you still only stream, for
 *  the Artists and Albums tabs. An artist's: from their page's Every song (each song once, however many releases it is
 *  on; the ones you hid off their page not counted), kept each time the page is opened. An album's: from its tracklist
 *  (its page's Missing from this album), kept when its page is opened, and looked up for the rest a few at a time.
 *  Kept on this device (release-counts.json). */
object ReleaseCounts {
    private val ser = MapSerializer(String.serializer(), ListSerializer(Int.serializer()))
    private var counts: HashMap<String, List<Int>>? = null
    var version by mutableStateOf(0)
        private set
    private fun file() = File(Music.ctx.filesDir, "release-counts.json")
    private fun all(): HashMap<String, List<Int>> = synchronized(this) {
        counts ?: (readText(file())?.let { runCatching { HashMap(JSON.decodeFromString(ser, it)) }.getOrNull() } ?: HashMap()).also { counts = it }
    }

    fun artistKey(name: String) = "ar:" + artistKey(name)
    fun albumKey(title: String, artist: String) = "al:" + artistKey(artist) + "|" + albumBase(title)

    /** (on this device, not downloaded), or null when not counted yet. */
    fun of(key: String): Pair<Int, Int>? = version.let { all()[key]?.takeIf { it.size == 2 }?.let { it[0] to it[1] } }

    fun put(key: String, here: Int, missing: Int) {
        val now = listOf(here, missing)
        synchronized(this) { if (all()[key] == now) return; all()[key] = now }
        runCatching { writeAtomic(file(), synchronized(this) { JSON.encodeToString(ser, all()) }) }
        Music.scope.launch { version++ }
    }

    /** "98 on this device · 44 not downloaded" (or just the first when nothing is missing). */
    fun text(c: Pair<Int, Int>): String = "%,d on this device".format(c.first) + if (c.second > 0) " · %,d not downloaded".format(c.second) else " · all of it"

    // ---- the albums not counted yet: looked up a few at a time, in the background ----
    @Volatile private var filling = false
    private val tried = java.util.Collections.synchronizedSet(HashSet<String>())

    /** The albums (title, artist, your songs of it, its kind) not counted yet: their tracklists looked up, one at a time. */
    fun fill(albums: List<Triple<String, String, List<Track>>>, kind: String) {
        if (filling) return
        val todo = albums.filter { (t, a, _) -> albumKey(t, a).let { k -> of(k) == null && k !in tried } }.take(400)
        if (todo.isEmpty()) return
        filling = true
        Music.bg({
            for ((title, artist, songs) in todo) {
                val k = albumKey(title, artist)
                tried += k
                runCatching {
                    Pictures.deezerPace()
                    val missing = AlbumMissing.find(title, artist, songs, kind)
                    // (each song once: two copies of one song of it are one)
                    if (AlbumMissing.listed(title, artist)) put(k, songs.distinctBy { songKey(it) }.size, missing.size)
                }
            }
        }) { _, _ -> filling = false }
    }
}
