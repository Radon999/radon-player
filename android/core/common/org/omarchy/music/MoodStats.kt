package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Most played › Moods: your plays added up by mood over a period (this week, month, year, all time). The plays are
 *  Most played's own (Last.fm, your Spotify, Apple Music, Deezer and other imports, the plays here: [Stats]); each one
 *  is matched to your own copy of the song (same title and artist, else the same title without its extras) and counts
 *  for each of that song's moods (a song that's Happy and Party counts in both, so the moods' plays add up to more than
 *  the plays). Plays of songs you haven't got, or with no mood yet, count for none. No scan: it's only adding
 *  up. Worked out in the background and kept per period until the plays or the moods change. */
object MoodStats {
    /** A mood's plays: how many, about how long (each play as long as your copy of the song), and its songs, most played first. */
    data class Row(val mood: String, val plays: Int, val secs: Long, val songs: List<Pair<Track, Int>>)

    /** period -> its moods, most played first (null: not worked out yet). */
    var rows by mutableStateOf(mapOf<String, List<Row>>())
        private set
    private val stamps = HashMap<String, Any>()
    private val busy = HashSet<String>()

    private fun stamp(period: String): Any = listOf(System.identityHashCode(if (period == "all") Stats.songs else Stats.periods[period]),
        Moods.version, Music.musicVersion)

    /** Work out the period's moods in the background, unless they're up to date or being worked out. */
    fun refresh(period: String) {
        val st = stamp(period)
        if (stamps[period] == st || period in busy) return
        busy += period
        val plays = if (period == "all") Stats.songs else Stats.periods[period].orEmpty()
        Music.bg({ build(plays) }) { r, _ ->
            busy -= period
            if (r != null) { rows = rows + (period to r); stamps[period] = st }
            // Changed again while it was worked out: once more.
            if (stamp(period) != st) refresh(period)
        }
    }

    private fun build(plays: List<Stats.Song>): List<Row> {
        val byKey = HashMap<Pair<String, String>, Track>()
        val byLoose = HashMap<Pair<String, String>, Track>()
        for (t in Music.mySongs()) {
            val k = Stats.statsKey(t.title, t.artist)
            byKey.putIfAbsent(k, t)
            byLoose.putIfAbsent(looseTitle(t.title) to k.second, t)
        }
        if (byKey.isEmpty()) return emptyList()
        class Acc { var plays = 0; var secs = 0.0; val songs = HashMap<String, Pair<Track, Int>>() }
        val acc = HashMap<String, Acc>()
        val moodsOf = HashMap<String, List<String>>()
        for (s in plays) {
            if (s.plays <= 0) continue
            val k = Stats.statsKey(s.title, s.artist)
            val t = byKey[k] ?: byLoose[looseTitle(s.title) to k.second] ?: continue
            // Every mood the song is in, not only its main one.
            for (mood in moodsOf.getOrPut(t.id) { runCatching { Moods.all(t) }.getOrDefault(emptyList()) }) {
                val a = acc.getOrPut(mood) { Acc() }
                a.plays += s.plays
                a.secs += s.plays * (t.duration.takeIf { it > 0 } ?: 210.0)
                a.songs.merge(t.id, t to s.plays) { x, y -> x.first to x.second + y.second }
            }
        }
        return acc.entries.map { (m, a) -> Row(m, a.plays, a.secs.toLong(), a.songs.values.sortedByDescending { it.second }) }
            .sortedByDescending { it.plays }
    }
}
