package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Smart playlists (☰ › Smart playlists): playlists that fill themselves from your music by rules ([SmartRules]).
 *  They're kept with your other playlists (playlists.json, so backups carry them) as a [Playlist] with [Smart] set.
 *  A live one's songs are worked out in the background and kept here until your music, its moods or its formats
 *  change; one that isn't live keeps the songs it had when it was made or last refreshed, in its tracks. */
object SmartLists {
    /** Bumps when new results are in. */
    var version by mutableStateOf(0)
        private set
    private val results = mutableStateMapOf<String, List<Track>>()   // playlist id -> its songs now
    private val madeFor = HashMap<String, Any>()                    // …and what they were worked out for
    private var busy = false
    private var again = false

    /** What a live playlist's songs depend on: your music (plays and favorites too), moods, formats. */
    private fun stamp(): Any = Triple(Music.musicVersion, Moods.version, FlacDepths.version)

    /** A smart playlist's songs as last worked out (null: not yet). */
    fun songs(pl: Playlist): List<Track>? = if (pl.smart?.live == false) pl.tracks else results[pl.id]

    /** A random order's seed: the same all day for a playlist, so it doesn't jump about as you play, new tomorrow. */
    fun seedFor(id: String): Long = id.hashCode().toLong() * 1_000_003L + (now() / 86_400).toLong()

    /** Work out again (in the background) the live smart playlists whose songs may have changed. */
    fun refresh() {
        val st = stamp()
        val todo = Music.lists.smart().filter { it.smart!!.live && madeFor[it.id] != (st to it.smart) }
        if (todo.isEmpty()) return
        if (busy) { again = true; return }
        busy = true
        Music.bg({ todo.associate { pl -> val sm = pl.smart!!; pl.id to ((st to sm) to evaluate(sm, seedFor(pl.id))) } }) { res, err ->
            busy = false
            res?.forEach { (id, r) -> madeFor[id] = r.first; results[id] = r.second }
            if (err != null) logWarn("smart playlists", err)
            version++
            if (again) { again = false; refresh() }
        }
    }

    /** (blocking) The songs of yours these rules pick now. */
    fun evaluate(sm: Smart, seed: Long = 0): List<Track> {
        val rules = sm.allRules()
        val need = SmartRules.needs(rules, sm.order)
        val songs = Music.mySongs()
        val counts = if ("plays" in need) runCatching { Music.playCounts() }.getOrDefault(emptyMap()) else emptyMap()
        val heard = if ("lastplayed" in need) runCatching { Dj.lastHeard() }.getOrDefault(emptyMap()) else emptyMap()
        val here = if ("lastplayed" in need) runCatching {
            Music.plays.items.values.toList().associate { songKey(it.track) to it.last.toLong() } }.getOrDefault(emptyMap()) else emptyMap()
        fun facts(t: Track): SongFacts {
            val (skips, full) = if ("skips" in need) Music.songStats.get(t) else (0 to 0)
            return SongFacts(
                title = t.title, artist = t.artist, album = t.album,
                genre = if ("genre" in need) Music.genreOf(t).takeIf { it != "Unknown genre" } ?: "" else "",
                moods = if ("mood" in need) runCatching { Moods.all(t) }.getOrDefault(emptyList()) else emptyList(),
                release = if ("release" in need) Music.tabOf(t) else null,
                format = if ("format" in need) FlacDepths.badge(t.file ?: Music.lib.items[t.id]?.file) else null,
                downloaded = Music.lib.has(t.id), myFile = t.isFile && !Music.lib.has(t.id),
                favorite = "favorite" in need && Music.favorites.has(t),
                plays = counts[songKey(t)] ?: 0,
                lastPlayed = maxOf(heard[Stats.statsKey(t.title, t.artist)] ?: 0L, here[songKey(t)] ?: 0L),
                added = t.added, skips = skips, heardOut = full, length = t.duration,
                folder = if ("folder" in need) Music.folderOf(t) else "",
            )
        }
        return SmartRules.pick(songs, ::facts, rules, sm.any, sm.order, sm.limit, now().toLong(), seed)
    }

    /** Save a smart playlist: a new one (id null) or changes to one. One that isn't live takes its songs now. */
    fun save(id: String?, name: String, sm: Smart, done: (String) -> Unit = {}) {
        val clean = name.trim().ifEmpty { "Smart playlist" }
        Music.bg({ if (sm.live) emptyList() else evaluate(sm, seedFor(id ?: clean)) }) { songs, err ->
            if (err != null) { Music.toast("Couldn't save it: ${err.message?.take(80)}"); return@bg }
            val tracks = songs.orEmpty()
            val pl = if (id == null) Music.lists.createSmart(clean, sm, tracks) else { Music.lists.setSmart(id, clean, sm, tracks); Music.lists.get(id) }
            if (pl == null) return@bg
            Music.toast(if (id == null) "Made “$clean”" else "Saved “$clean”")
            Music.musicVersion++
            refresh()
            done(pl.id)
        }
    }

    /** A smart playlist that isn't live: take its songs again, as its rules pick them now. */
    fun takeAgain(pl: Playlist) {
        val sm = pl.smart ?: return
        Music.bg({ evaluate(sm, seedFor(pl.id) + 1) }) { songs, err ->
            if (songs == null) { Music.toast("Couldn't refresh it: ${err?.message?.take(80)}"); return@bg }
            Music.lists.setSmart(pl.id, pl.name, sm, songs)
            Music.musicVersion++
            Music.toast("“${pl.name}”: ${songs.size} song${if (songs.size != 1) "s" else ""} now")
        }
    }

    /** One of the suggested smart playlists, made with one tap. */
    fun addIdea(idea: SmartRules.Idea, done: (String) -> Unit = {}) =
        save(null, idea.name, Smart(rules = idea.rules, any = idea.any, order = idea.order, limit = idea.limit), done)

    /** (blocking) How many songs these rules would pick now (the editor's preview). */
    fun count(sm: Smart): Int = evaluate(sm).size
}
