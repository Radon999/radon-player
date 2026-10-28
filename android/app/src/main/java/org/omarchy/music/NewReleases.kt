package org.omarchy.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

/** New releases of the artists in your songs, from Deezer's catalogue (free, no account; it says
 *  when each came out and whether it's an album, a single or an EP), as the desktop does it.
 *  Nothing is checked on its own: Scan looks, for the last week, month or year, when you press it. */
object NewReleases {
    @Serializable
    data class Release(val title: String, val artist: String, val date: String, val type: String, val cover: String = "", val link: String = "")

    @Serializable
    private data class Saved(val artists: List<String> = emptyList(), val ids: Map<String, Long> = emptyMap(),
                             val found: List<Release> = emptyList(), val scanned: Double = 0.0, val range: String = "month")

    val RANGES = linkedMapOf("week" to 7L, "month" to 30L, "year" to 365L)
    var artists by mutableStateOf(listOf<String>()); private set
    var found by mutableStateOf(listOf<Release>()); private set
    var scanned by mutableStateOf(0.0); private set
    var range by mutableStateOf("month"); private set
    var status by mutableStateOf<String?>(null); private set   // the scan's progress (null: not scanning)
    var kind by mutableStateOf("album")   // the tab: album, single, ep, artists
    @Volatile var stop = false
    private val ids = HashMap<String, Long>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var file: File

    fun init(c: Context) {
        file = File(c.filesDir, "new-releases.json")
        Music.bg({ readText(file)?.let { runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } }) { s, _ ->
            if (s != null) { artists = s.artists; found = s.found; scanned = s.scanned; range = s.range; ids.putAll(s.ids) }
        }
    }

    private fun save() = writeAtomic(file, JSON.encodeToString(Saved.serializer(), Saved(artists, HashMap(ids), found, scanned, range)))

    /** The artists to look for: the lead artist of every song of yours, the ones you have most songs of first. */
    fun readArtists(songs: List<Track>): Int {
        artists = songs.mapNotNull { splitArtists(it.artist).firstOrNull()?.trim() }
            .filter { it.isNotEmpty() && norm(it) !in setOf("unknown artist", "various artists") }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        Music.bg({ save() })
        return artists.size
    }

    private fun kindOf(t: String?) = when (t?.lowercase()) { "ep" -> "ep"; "single" -> "single"; else -> "album" }

    private fun artistId(name: String): Long {
        val k = norm(name)
        ids[k]?.let { return it }
        val got = httpJson("https://api.deezer.com/search/artist?q=${enc(name)}&limit=5")
        val hit = got["data"].list.firstOrNull { norm(it["name"].str) == k }
        return (hit?.get("id").long ?: 0L).also { ids[k] = it }
    }

    /** Look at each artist's releases from the last week, month or year (in the background). */
    fun scan(period: String) {
        if (status != null) return
        if (artists.isEmpty()) readArtists(Music.mySongs())
        status = "Looking…"
        stop = false
        val since = LocalDate.now().minusDays(RANGES[period] ?: 30).toString()
        val names = artists
        Music.bg({
            val out = ArrayList<Release>()
            for ((i, name) in names.withIndex()) {
                if (stop) break
                val line = "Looking at $name · ${i + 1} of ${names.size}"
                main.post { status = line }
                val aid = runCatching { artistId(name) }.getOrDefault(0L)
                if (aid == 0L) continue
                val got = runCatching { httpJson("https://api.deezer.com/artist/$aid/albums?limit=50") }.getOrNull() ?: continue
                for (al in got["data"].list) {
                    val date = al["release_date"].str ?: ""
                    if (date >= since) out += Release(al["title"].str ?: "", name, date, kindOf(al["record_type"].str),
                        al["cover_medium"].str ?: al["cover"].str ?: "", al["link"].str ?: "")
                }
                Thread.sleep(120)   // Deezer takes 50 asks every 5 seconds
            }
            out.sortedByDescending { it.date }
        }) { res, err ->
            status = null
            if (res == null) { Music.toast("Couldn't look: ${err?.message?.take(100)}"); return@bg }
            found = res; scanned = now(); range = period
            Music.bg({ save() })
            Music.toast("${res.size} new release${if (res.size != 1) "s" else ""}")
        }
    }
}
