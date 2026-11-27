package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
                             val found: List<Release> = emptyList(), val scanned: Double = 0.0, val range: String = "month",
                             val told: List<Release> = emptyList(), val unseen: Int = 0)

    val RANGES = linkedMapOf("week" to 7L, "month" to 30L, "year" to 365L)
    var artists by mutableStateOf(listOf<String>()); private set
    var found by mutableStateOf(listOf<Release>()); private set
    var scanned by mutableStateOf(0.0); private set
    var range by mutableStateOf("month"); private set
    var status by mutableStateOf<String?>(null); private set   // the scan's progress (null: not scanning)
    var kind by mutableStateOf("album")   // the tab: album, single, ep, artists
    // The bell: what the app's own daily look found that it hadn't told you about, newest first (the last 300), and how
    // many of them came since the bell was last opened. (Until 4.0.17 these were a notification on the phone.)
    var told by mutableStateOf(listOf<Release>()); private set
    var unseen by mutableStateOf(0); private set
    var bell by mutableStateOf(false); private set
    /** The bell opened (its count is then seen) or closed. */
    fun openBell(on: Boolean) { bell = on; if (on && unseen > 0) { unseen = 0; Music.bg({ save() }) } }
    fun clearBell() { told = emptyList(); unseen = 0; Music.bg({ save() }) }
    @Volatile var stop = false
    private val ids = HashMap<String, Long>()
    private lateinit var file: File

    fun init(c: Ctx) {
        file = File(c.filesDir, "new-releases.json")
        Music.bg({ readText(file)?.let { runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } }) { s, _ ->
            if (s != null) { artists = s.artists; found = s.found; scanned = s.scanned; range = s.range; ids.putAll(s.ids); told = s.told; unseen = s.unseen }
        }
    }

    private fun save() = writeAtomic(file, JSON.encodeToString(Saved.serializer(), Saved(artists, HashMap(ids), found, scanned, range, told, unseen)))

    /** The artists to look for: the lead artist of every song of yours, the ones you have most songs of first. */
    fun readArtists(songs: List<Track>): Int {
        val counted = songs.mapNotNull { splitArtists(it.artist).firstOrNull()?.trim() }
            .filter { it.isNotEmpty() && norm(it) !in setOf("unknown artist", "various artists") }
            .groupingBy { it }.eachCount()
        songCounts = counted
        artists = counted.entries.sortedByDescending { it.value }.map { it.key }
        Music.bg({ save() })
        return artists.size
    }
    /** How many songs of each artist you have, as last read (for the order a look goes in). */
    @Volatile private var songCounts: Map<String, Int> = emptyMap()

    private fun kindOf(t: String?) = when (t?.lowercase()) { "ep" -> "ep"; "single" -> "single"; else -> "album" }

    /** The same artist, written either way: "half•alive" and "half alive", "K.Flay" and "KFlay". */
    fun sameName(a: String?, b: String?) = bare(a).let { it.isNotEmpty() && it == bare(b) }

    /** New releases' Select: what's picked (across Albums, Singles, EPs and Artists): key -> a release, or an artist's name. */
    var selecting by mutableStateOf(false)
    val picked = androidx.compose.runtime.mutableStateMapOf<String, Pair<Release?, String>>()
    fun key(r: Release) = "r:" + norm(r.artist) + "|" + r.link.ifEmpty { norm(r.title) }
    fun artistKey(name: String) = "a:" + norm(name)
    fun toggle(key: String, what: Pair<Release?, String>) { if (picked.remove(key) == null) picked[key] = what }

    /** The releases picked (an artist picked: all of theirs found), each once. */
    fun pickedReleases(): List<Release> {
        val out = LinkedHashMap<String, Release>()
        for ((r, artist) in picked.values) (if (r != null) listOf(r) else found.filter { it.artist == artist }).forEach { out.putIfAbsent(key(it), it) }
        return out.values.toList()
    }

    private fun bare(a: String?) = norm((a ?: "").replace(Regex("^the ", RegexOption.IGNORE_CASE), "")).replace(Regex("[\\W_]+"), "")

    /** Deezer's answer; when it says too many asks at once, wait and ask again. */
    private fun deezer(url: String): kotlinx.serialization.json.JsonElement {
        var got = httpJson(url)
        for (wait in listOf(5_000L, 10_000L)) {
            if (got["error"]["code"].long != 4L) return got   // 4: quota (50 asks every 5 seconds)
            Thread.sleep(wait)
            got = httpJson(url)
        }
        return got
    }

    private fun artistId(name: String): Long {
        val k = norm(name)
        ids[k]?.takeIf { it != 0L }?.let { return it }   // not found before: look again
        val got = deezer("https://api.deezer.com/search/artist?q=${enc(name)}&limit=10")
        val hit = got["data"].list.firstOrNull { bare(it["name"].str).let { b -> b.isNotEmpty() && b == bare(name) } }
        return (hit?.get("id").long ?: 0L).also { ids[k] = it }
    }

    /** Look at each artist's releases from the last week, month or year (in the background). */
    // ---- Look by itself: once a day while the app is open, and say when there is something new ----
    const val AUTO = "new_releases_auto"
    private const val AUTO_AT = "new_releases_auto_at"
    private const val SEEN = "new_releases_seen"
    /** Called every so often while the app is open: a look at the last week, once a day, in the background. */
    fun tick() {
        if (!Prefs.on(AUTO) || status != null) return
        val t = System.currentTimeMillis() / 1000
        if (t - (Prefs.text(AUTO_AT).toLongOrNull() ?: 0L) < 86_400L) return
        Prefs.setText(AUTO_AT, t.toString())
        scan("week", quiet = true)
    }
    /** After a look by itself: the releases not told about before go under the bell on the New releases screen, which
     *  shows how many are new. No notification is sent. */
    private fun announce(res: List<Release>) {
        val seen = Prefs.text(SEEN).split('\n').filter { it.isNotBlank() }
        val new = NewReleaseRules.fresh(res.map { key(it) to (it.title to it.artist) }, seen.toSet())
        if (new.isEmpty()) return
        Prefs.setText(SEEN, (new.map { it.first } + seen).take(600).joinToString("\n"))
        val keys = new.map { it.first }.toSet()
        told = (res.filter { key(it) in keys } + told).distinctBy { key(it) }.sortedByDescending { it.date }.take(300)
        unseen = if (bell) 0 else unseen + new.size
        Music.bg({ save() })
    }

    /** Releases of these artists taken out of what was found (they were unfollowed). */
    fun dropArtists(names: List<String>) {
        val out = names.mapTo(HashSet()) { FollowRules.key(it) }
        fun keep(r: Release) = FollowRules.key(r.artist) !in out
        found = found.filter(::keep); told = told.filter(::keep)
        Music.bg({ save() })
    }

    /** Whose releases are looked for: the artists of the songs you have as files (yours and downloads) and of the songs in
     *  the playlists you keep in Radon Player (streamed ones too). A playlist that only exists on a streaming service, and
     *  the playlists the app makes itself (discovery, New releases, smart ones), don't count. */
    private fun yours(): List<Track> = Music.mySongs() +
        runCatching { Music.lists.all().filter { it.smart == null && it.discovery == null }.flatMap { it.tracks } }.getOrDefault(emptyList())

    fun scan(period: String, quiet: Boolean = false) {
        if (status != null) return
        readArtists(yours())   // read again each time (how many songs of each: the order they're looked at in)
        // Whose releases: the artists you follow (yours followed by themselves, with Follow artists by themselves on;
        // anyone you followed; never anyone you unfollowed)
        Follows.refreshNow()
        artists = Follows.list().sortedByDescending { a -> songCounts[a] ?: songCounts.entries.firstOrNull { FollowRules.key(it.key) == FollowRules.key(a) }?.value ?: 0 }
        status = "Looking…"
        stop = false
        val since = LocalDate.now().minusDays(RANGES[period] ?: 30).toString()
        val songsOf = artists.associateWith { songCounts[it] ?: 0 }   // (most songs first, as read)
        Music.bg({
            // The artists you play most, and the ones you've lately fallen for, are looked at first: a look stopped
            // halfway (or the daily one) has found theirs
            val names = byTaste(songsOf)
            val out = ArrayList<Release>()
            for ((i, name) in names.withIndex()) {
                if (stop) break
                val line = "Looking at $name · ${i + 1} of ${names.size}"
                Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { status = line }
                val aid = runCatching { artistId(name) }.getOrDefault(0L)
                if (aid == 0L) continue
                val got = runCatching { deezer("https://api.deezer.com/artist/$aid/albums?limit=100") }.getOrNull() ?: continue
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
            if (res == null) { if (!quiet) Music.toast("Couldn't look: ${err?.message?.take(100)}"); return@bg }
            found = res; scanned = now(); range = period
            Music.bg({ save() })
            if (quiet) announce(res) else Music.toast("${res.size} new release${if (res.size != 1) "s" else ""}")
        }
    }

    /** (blocking) The artists (each with how many songs of theirs you have) in the order to look: [NewMusicRules.releaseOrder]
     *  by your plays of each and their phase in your listening, as the last picture of it has them (none yet: as given). */
    private fun byTaste(songsOf: Map<String, Int>): List<String> {
        val taste = TasteService.current ?: return songsOf.keys.toList()
        val all = taste.facts.artistAll
        val byNorm = HashMap<String, Int>().also { m -> all.forEach { (a, n) -> m.merge(norm(a), n, Int::plus) } }
        val phase = taste.habits?.phase.orEmpty()
        return NewMusicRules.releaseOrder(songsOf.toList(), { a -> all[a] ?: byNorm[norm(a)] ?: 0 }, { a -> phase[statsKey("", a).second] })
    }
}
