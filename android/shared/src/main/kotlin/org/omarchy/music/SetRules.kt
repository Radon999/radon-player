package org.omarchy.music

import java.time.LocalDateTime
import kotlin.random.Random

/** The DJ's rules for a set that need nothing but the songs: which kind has its turn, which songs are picked, what
 *  order they go in, and when a line no longer fits its songs. (What each kind of set is made of stays with the DJ:
 *  it needs your listening and the services.) */
object SetRules {
    /** How much energy a mood has (1 calm … 10 hype), for a set's arc. */
    val ENERGY = mapOf("Calm" to 1, "Sad" to 2, "Dreamy" to 3, "Chill" to 3, "Focus" to 4, "Romantic" to 4, "Dark" to 5,
        "Happy" to 6, "Energetic" to 8, "Intense" to 8, "Party" to 9, "Hype" to 10)

    private fun lead(t: Track) = t.artist.split(",")[0].trim()

    /** The set's songs after its first (the one its line names) in an arc by their moods: building up from a calm
     *  first song, easing down from a loud one, so it flows. Unchanged when few of them have a mood. */
    fun arc(songs: List<Track>, moodOf: (Track) -> String?): List<Track> {
        if (songs.size < 3) return songs
        val e = songs.associate { songKey(it) to moodOf(it)?.let { m -> ENERGY[m] } }
        if (e.values.count { it != null } < 3) return songs
        val known = e.values.filterNotNull().sorted()
        val mid = known[known.size / 2]
        val start = e[songKey(songs[0])] ?: mid
        val rest = songs.drop(1).sortedBy { (e[songKey(it)] ?: mid) * if (start >= 6) -1 else 1 }
        return listOf(songs[0]) + rest
    }

    /** No artist twice in a row, and (cap: but for an artist's own sets) at most two of an artist in a set. The first
     *  song stays first. */
    fun spread(songs: List<Track>, cap: Boolean): List<Track> {
        if (songs.size < 3) return songs
        val counts = HashMap<String, Int>()
        val kept = songs.filter { t -> val a = norm(lead(t)); val n = (counts[a] ?: 0) + 1; counts[a] = n; !cap || n <= 2 }
            .let { if (it.size >= minOf(4, songs.size)) it else songs }   // too few left: the set as it was
        val out = ArrayList<Track>(); val left = kept.toMutableList()
        out += left.removeAt(0)
        while (left.isNotEmpty()) {
            val prev = norm(lead(out.last()))
            val i = left.indexOfFirst { norm(lead(it)) != prev }.takeIf { it >= 0 } ?: 0
            out += left.removeAt(i)
        }
        return out
    }

    /** songs without the ones out picks, for a line already written for them: null when the line names one of those
     *  (its title, or its artist) or fewer than three are left, so a line never names a song its set hasn't got. */
    fun without(songs: List<Track>, line: String, out: (Track) -> Boolean): List<Track>? {
        val gone = songs.filter(out)
        if (gone.isEmpty()) return songs
        if (gone.any { t -> t.title in line || lead(t).let { a -> a.isNotEmpty() && a in line } }) return null
        return songs.filter { it !in gone }.takeIf { it.size >= 3 }
    }

    /** n songs of pool at random, the ones nearer the top likelier, none twice and none in used
     *  (song keys of what the DJ played already). */
    fun pick(pool: List<Track>, n: Int, used: Set<Pair<String, String>>, rng: Random = Random.Default): List<Track> {
        val seen = HashSet(used)
        val out = ArrayList<Track>()
        val weighted = pool.mapIndexed { i, t -> Math.pow(rng.nextDouble(), 1 + 8.0 * i / maxOf(1, pool.size)) to t }
        for ((_, t) in weighted.sortedByDescending { it.first }) {
            if (!seen.add(songKey(t))) continue
            out += t
            if (out.size == n) break
        }
        return out
    }

    /** How many of the DJ's last songs are kept out now: all of gap, or playing from a few songs (a playlist of
     *  scopeSize), fewer, so there are always some to pick (the ones played longest ago come back first). */
    fun window(scopeSize: Int?, gap: Int, set: Int): Int = scopeSize?.let { minOf(gap, maxOf(0, it - 2 * set)) } ?: gap

    /** A kind as the history keeps it: playing from a playlist, "Your favourites" is the same kind as from everything. */
    fun canon(k: String) = if (k == "loved" || k == "favorites") "mostplayed" else k

    /** The kinds of set to try next, best first: the one played longest ago first (history: the kinds played, oldest
     *  first), so none comes back until every other kind has had its turn. A kind never played yet takes a place at
     *  random among the rest (new kinds don't all come at once). Kinds that are dead (couldn't make a set lately) after
     *  them, and avoid (the kind playing now) last of all. */
    fun kindsToTry(order: List<String>, history: List<String>, dead: (String) -> Boolean, avoid: String?, rng: Random = Random.Default): List<String> {
        val kinds = order.distinct()
        val rank = kinds.associateWith { k -> history.lastIndexOf(canon(k)).let { if (it >= 0) it.toDouble() else rng.nextDouble(-1.0, history.size.toDouble()) } }
        val usable = kinds.filter { !dead(it) }
        var tries = usable.shuffled(rng).sortedBy { rank.getValue(it) } + kinds.filter { it !in usable }.sortedBy { rank.getValue(it) }
        if (avoid != null) tries = tries.filter { it != avoid } + tries.filter { it == avoid }
        return tries
    }

    /** The day and the part of it (morning, afternoon, evening, night: as the hellos split it). */
    fun timeKey(n: LocalDateTime): String {
        val h = n.hour
        return "${n.dayOfWeek} " + if (h in 5..11) "morning" else if (h in 12..16) "afternoon" else if (h in 17..22) "evening" else "night"
    }
}
