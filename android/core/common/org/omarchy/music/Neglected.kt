package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.io.File

/** Unplayed and neglected ([RestedRules]): the tab's songs, and the shuffle that goes round the songs you haven't
 *  heard in a long time or at all. When a song was last heard is your stats' time (Last.fm, your imports) or the play
 *  here, whichever is later; how often, your stats' count (the plays here only where the stats know nothing of it). */
object Neglected {
    // ---- how Shuffle picks ----
    fun mode(): String = RestedRules.mode(Prefs.text(RestedRules.MODE), Prefs.on(Prefs.SMART_SHUFFLE))
    /** The old Smart shuffle switch is kept in step, for what still reads it. */
    fun setMode(m: String) { Prefs.setText(RestedRules.MODE, m); Prefs.set(Prefs.SMART_SHUFFLE, m == RestedRules.SMART) }

    /** floor: when the first round began. released: a song's key -> when its round last began again. */
    @Serializable
    private data class Saved(val floor: Long = 0, val released: Map<String, Long> = emptyMap())
    private var loaded: Saved? = null
    private fun file(): File? = runCatching { File(Music.ctx.filesDir, "rested-shuffle.json") }.getOrNull()
    @Synchronized private fun saved(): Saved = loaded ?: (runCatching {
        file()?.takeIf { it.isFile }?.readText()?.let { JSON.decodeFromString(Saved.serializer(), it) } }.getOrNull() ?: Saved()).also { loaded = it }
    @Synchronized private fun keep(s: Saved) {
        loaded = s
        runCatching { file()?.writeText(JSON.encodeToString(Saved.serializer(), s)) }
    }

    private fun key(t: Track): Pair<String, String> = Stats.statsKey(t.title, t.artist)
    private fun name(k: Pair<String, String>) = k.first + "|" + k.second

    /** When each song was last heard (seconds), from what is at hand now: never waited for (a shuffle starts at once). */
    private fun lastNow(): Map<Pair<String, String>, Long> {
        val last = HashMap<Pair<String, String>, Long>()
        runCatching { TasteService.current?.last }.getOrNull()?.let { last.putAll(it) }
        for (e in runCatching { Music.plays.items.values.toList() }.getOrDefault(emptyList())) {
            val k = key(e.track); val t = e.last.toLong()
            if (t > (last[k] ?: 0L)) last[k] = t
        }
        return last
    }

    /** The shuffle of Unheard and neglected, or null: Shuffle is set another way. */
    fun orderer(): ((List<Track>, kotlin.random.Random) -> List<Track>)? {
        if (mode() != RestedRules.RESTED) return null
        return { tracks, rng -> order(tracks, rng) }
    }

    /** tracks without the ones heard in their round, the never played first, then the ones heard longest ago, loosely. */
    fun order(tracks: List<Track>, rng: kotlin.random.Random): List<Track> {
        if (tracks.size < 2) return tracks
        val nowS = System.currentTimeMillis() / 1000
        var s = saved()
        if (s.floor <= 0) { s = s.copy(floor = nowS - RestedRules.RECENT); keep(s) }
        val last = lastNow()
        val keys = tracks.map { key(it) }
        val got = RestedRules.order(tracks.size, { last[keys[it]] ?: 0L },
            { RestedRules.heard(last[keys[it]] ?: 0L, s.released[name(keys[it])] ?: 0L, s.floor) }, rng)
        // Every one of them had been heard: their round begins again now
        if (got.restarted) keep(s.copy(released = s.released + keys.associate { name(it) to nowS }))
        return got.order.map { tracks[it] }
    }

    // ---- the tab ----
    class Row(val track: Track, val plays: Int, val last: Long)
    var show by mutableStateOf("unplayed")     // the chip: unplayed, or a key of RestedRules.SPANS
    var rows by mutableStateOf<List<Row>?>(null)
        private set
    private var busy = false
    private var madeAt = 0L
    private var madeFor = -1

    /** Your songs with how often and when last you played each (null: being added up). Made again when your music
     *  changes, and every couple of minutes while the tab is looked at. */
    fun rowsNow(): List<Row>? {
        val t = System.currentTimeMillis()
        val v = Music.musicVersion
        if (!busy && (rows == null || madeFor != v || t - madeAt > 120_000L)) {
            busy = true
            Music.bg({ build() }) { r, _ -> busy = false; madeAt = System.currentTimeMillis(); madeFor = v; if (r != null) rows = r }
        }
        return rows
    }

    /** (blocking) */
    private fun build(): List<Row> {
        val counts = runCatching { PlaySorts.counts() }.getOrDefault(emptyMap())
        val last = HashMap(lastNow())
        runCatching { TasteService.lastHeard() }.getOrNull()?.forEach { (k, t) -> if (t > (last[k] ?: 0L)) last[k] = t }
        val seen = HashSet<Pair<String, String>>()
        return Music.mySongs().filter { seen.add(songKey(it)) }.map { Row(it, counts[songKey(it)] ?: it.plays, last[key(it)] ?: 0L) }
    }

    /** The chip's songs: never played (in name order), or not played for its time (the longest ago first). */
    fun shown(all: List<Row>, show: String, nowS: Long): List<Row> {
        val days = RestedRules.SPANS.firstOrNull { it.first == show }?.third
        return if (days == null) all.filter { RestedRules.unplayed(it.plays, it.last) }.sortedBy { it.track.artist.lowercase() + "\u0000" + it.track.title.lowercase() }
            else all.filter { RestedRules.neglected(it.last, nowS, days) }.sortedBy { it.last }
    }
}
