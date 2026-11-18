package org.omarchy.music

import kotlinx.serialization.Serializable

/** Repeats: how often a song was let play again with Repeat one on. Only Repeat one counts, and only a song that
 *  played to its end and started over (not a song picked again, not Repeat all going round the queue). Each time is
 *  one repeat for that song; kept with the day it happened (for "this week", "this month") and the longest run in one
 *  go (the same song over and over, nothing else between). One song across its copies is one song ([statsKey]). */
object RepeatLoopRules {
    @Serializable
    data class Loop(val track: Track, val n: Int = 0, val best: Int = 0, val last: Long = 0, val days: Map<String, Int> = emptyMap())

    @Serializable
    data class Saved(val songs: Map<String, Loop> = emptyMap(), val lastKey: String = "", val run: Int = 0)

    fun key(t: Track): String = statsKey(t.title, t.artist).let { it.first + "|" + it.second }

    /** A song has just played again by itself. day: "2026-11-18". */
    fun add(s: Saved, t: Track, day: String, now: Long): Saved {
        val k = key(t)
        val run = if (s.lastKey == k) s.run + 1 else 1
        val old = s.songs[k]
        val days = (old?.days ?: emptyMap()) + (day to ((old?.days?.get(day) ?: 0) + 1))
        return Saved(s.songs + (k to Loop(t.slim(), (old?.n ?: 0) + 1, maxOf(old?.best ?: 0, run), now, days)), k, run)
    }

    /** Another song began (or the same one was started by hand): the run in one go is over. */
    fun broke(s: Saved): Saved = if (s.run == 0) s else s.copy(lastKey = "", run = 0)

    /** A song's repeats since a day ("": ever). */
    fun count(l: Loop, since: String): Int = if (since.isEmpty()) l.n else l.days.filterKeys { it >= since }.values.sum()

    /** The songs repeated since a day, the most repeated first (then the one repeated last), each with its count. */
    fun top(s: Saved, since: String = "", limit: Int = Int.MAX_VALUE): List<Pair<Loop, Int>> =
        s.songs.values.map { it to count(it, since) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Loop, Int>> { it.second }.thenByDescending { it.first.last }).take(limit)

    fun total(s: Saved, since: String = ""): Int = s.songs.values.sumOf { count(it, since) }

    /** "repeated 50 times", "repeated once". */
    fun times(n: Int): String = when (n) { 1 -> "repeated once"; 2 -> "repeated twice"; else -> "repeated %,d times".format(n) }

    /** A song's line in the Repeats list: its place, its count, and its longest run when that is worth saying. */
    fun line(rank: Int, count: Int, best: Int): String = "#$rank · " + times(count) + if (best >= 3) " · $best in a row at most" else ""

    /** What the top of the Repeats tab says. */
    fun summary(songs: Int, total: Int): String =
        if (songs == 0) "" else "%,d song%s let play again %,d time%s with Repeat one".format(songs, if (songs != 1) "s" else "", total, if (total != 1) "s" else "")

    /** A song's plays as a card says them: "132 plays" (nothing for none). */
    fun plays(n: Int): String = if (n <= 0) "" else "%,d play%s".format(n, if (n != 1) "s" else "")
}
