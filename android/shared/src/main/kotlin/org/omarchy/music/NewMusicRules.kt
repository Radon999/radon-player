package org.omarchy.music

import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/** How new music is chosen to fit you: which of your songs to look for new ones from (the ones that show most that you
 *  like them), the order a set's songs go in (the way you play one song after another), and whose new releases are
 *  looked for first. Only rules here; what you played and said comes from the app. */
object NewMusicRules {
    /** A song you might find new music from, with what shows you like it. lately: among what you play lately (else a
     *  long-time favourite); plays: all your plays of it; full / skips: how often you heard it to the end and skipped
     *  it; up: you said More like this; fav: a favourite; loops: how often you let it play again (Repeat one); phase:
     *  its artist's phase in your listening ([HabitRules.NEW_LOVE] …, "" none). */
    class SeedCand<T>(val item: T, val lately: Boolean, val plays: Int = 0, val full: Int = 0, val skips: Int = 0,
                      val up: Boolean = false, val fav: Boolean = false, val loops: Int = 0, val phase: String = "")

    /** How plainly you like a song (about 0 … 3): played a lot, heard to the end, More like this, a favourite, on
     *  repeat, by an artist you've lately fallen for or play more and more; less for one you mostly skip, or by an
     *  artist you've left behind. */
    fun liking(c: SeedCand<*>): Double {
        var s = 0.5 * (ln(1.0 + c.plays) / ln(100.0)).coerceAtMost(1.0)
        s += 0.4 * (ln(1.0 + c.full) / ln(20.0)).coerceAtMost(1.0)
        if (c.up) s += 0.6
        if (c.fav) s += 0.5
        s += 0.4 * (ln(1.0 + c.loops) / ln(10.0)).coerceAtMost(1.0)
        s += when (c.phase) { HabitRules.NEW_LOVE, HabitRules.RISING -> 0.4; HabitRules.LONG_TIME -> 0.2; HabitRules.FADED -> -0.3; else -> 0.0 }
        val tried = c.skips + c.full
        if (tried >= 3) s -= 0.6 * c.skips / tried
        return s
    }

    /** n seeds: taken in turns from what you play lately and from your long-time favourites (lately first), so new
     *  music comes from both and not only from this month. In each, the songs you like best are likelier, but not
     *  certain (the same way [SetRules.pick] picks), so it isn't the same seeds every time. One side running out: the
     *  other fills in. */
    fun <T> seeds(cands: List<SeedCand<T>>, n: Int, rng: Random = Random.Default): List<T> {
        fun likelyFirst(l: List<SeedCand<T>>): ArrayDeque<T> {
            val best = l.sortedByDescending { liking(it) }
            return ArrayDeque(best.mapIndexed { i, c -> rng.nextDouble().pow(1 + 8.0 * i / maxOf(1, best.size)) to c.item }
                .sortedByDescending { it.first }.map { it.second })
        }
        val lately = likelyFirst(cands.filter { it.lately }); val long = likelyFirst(cands.filter { !it.lately })
        val out = ArrayList<T>()
        while (out.size < n && (lately.isNotEmpty() || long.isNotEmpty())) {
            val from = if (out.size % 2 == 0) lately.ifEmpty { long } else long.ifEmpty { lately }
            val t = from.removeFirst()
            if (t !in out) out += t
        }
        return out
    }

    /** A set's songs in the order you'd play them yourself. The first stays first; each one after is the one you most
     *  often play straight after the one before ([Habits.after]); for songs that habit knows nothing of (new ones), the
     *  artist you most often play after that artist ([Habits.afterArtist]) or in the same sittings ([Habits.near]).
     *  Never the same artist twice in a row while another is left; where nothing tells them apart, the order they came
     *  in (the mood arc) is kept. */
    fun flow(songs: List<Track>, habits: Habits?): List<Track> {
        if (habits == null || songs.size < 3) return songs
        val n = songs.size
        val keys = songs.map { SignalRules.songKey(it.title, it.artist) }
        val leads = songs.map { statsKey("", it.artist).second }
        val score = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 0.0 else follows(habits, keys[i], leads[i], keys[j], leads[j]) } }
        if (score.all { row -> row.all { it == 0.0 } }) return songs   // nothing known of any of them: as they came
        val out = arrayListOf(0)
        val left = (1 until n).toMutableList()
        while (left.isNotEmpty()) {
            val prev = out.last()
            val others = left.filter { leads[it] != leads[prev] }.ifEmpty { left }
            var best = others[0]
            for (j in others) if (score[prev][j] > score[prev][best]) best = j   // a tie keeps the earlier one
            out += best; left.remove(best)
        }
        return out.map { songs[it] }
    }

    /** How well song b follows song a in your listening (0: nothing known). Playing b straight after a yourself counts
     *  most (1 and up); then b's artist after a's, and the two artists in the same sittings. */
    fun follows(h: Habits, keyA: String, leadA: String, keyB: String, leadB: String): Double {
        val song = h.after[keyA]?.firstOrNull { it.first == keyB }?.second ?: 0
        val artist = if (leadA == leadB) 0 else h.afterArtist[leadA]?.firstOrNull { it.first == leadB }?.second ?: 0
        val near = if (leadA == leadB) 0.0 else h.near[leadA]?.firstOrNull { it.first == leadB }?.second ?: 0.0
        var s = 0.0
        if (song > 0) s += 1.0 + 0.5 * (ln(1.0 + song) / ln(6.0)).coerceAtMost(1.0)
        s += 0.5 * (ln(1.0 + artist) / ln(11.0)).coerceAtMost(1.0)
        s += 0.4 * near
        return s
    }

    /** New releases: whose to look for first. The artists you play most first (plays: all your plays of one), the ones
     *  you've lately fallen for or play more and more moved up, the ones you've left behind moved down; then the ones
     *  you have most songs of (songs: how many, as given). phase: an artist's phase, or null. */
    fun releaseOrder(artists: List<Pair<String, Int>>, plays: (String) -> Int, phase: (String) -> String?): List<String> =
        artists.map { (a, songs) ->
            val bonus = when (phase(a)) { HabitRules.NEW_LOVE, HabitRules.RISING -> 2.0; HabitRules.LONG_TIME -> 1.0; HabitRules.FADED -> -1.5; else -> 0.0 }
            a to ln(1.0 + plays(a)) + bonus + 0.3 * ln(1.0 + songs)
        }.sortedByDescending { it.second }.map { it.first }
}
