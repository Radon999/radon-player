package org.omarchy.music

import kotlinx.serialization.Serializable

/** Repeats: how often a song was let play again with Repeat one on. Only Repeat one counts, and only a song that
 *  played to its end and started over (not a song picked again, not Repeat all going round the queue). Each time is
 *  one repeat for that song; kept with the day it happened (for "this week", "this month") and the longest run in one
 *  go (the same song over and over, nothing else between). One song across its copies is one song ([statsKey]).
 *
 *  Before the app counted them (and on other services) nothing recorded repeats; the ones an imported history shows
 *  ([RepeatFactsRules.inferred]) are kept beside the counted ones ([Saved.imports]) and only for days before the app's
 *  own count began ([Saved.since]), so a repeat is never counted twice. */
object RepeatLoopRules {
    @Serializable
    data class Loop(val track: Track, val n: Int = 0, val best: Int = 0, val last: Long = 0, val days: Map<String, Int> = emptyMap(),
                    val bestDay: String = "")

    /** songs: counted here. imports: worked out from your imported history and scrobbles. hours: repeats by month and
     *  hour of the day ("2026-11|21"). since: the day counting began here ("": not yet). */
    @Serializable
    data class Saved(val songs: Map<String, Loop> = emptyMap(), val lastKey: String = "", val run: Int = 0,
                     val imports: Map<String, Loop> = emptyMap(), val hours: Map<String, Int> = emptyMap(), val since: String = "")

    fun key(t: Track): String = statsKey(t.title, t.artist).let { it.first + "|" + it.second }

    /** A song has just played again by itself. day: "2026-11-18"; hour: of the day (-1: not kept). */
    fun add(s: Saved, t: Track, day: String, now: Long, hour: Int = -1): Saved {
        val k = key(t)
        val run = if (s.lastKey == k) s.run + 1 else 1
        val old = s.songs[k]
        val days = (old?.days ?: emptyMap()) + (day to ((old?.days?.get(day) ?: 0) + 1))
        val better = run > (old?.best ?: 0)
        val loop = Loop(t.slim(), (old?.n ?: 0) + 1, maxOf(old?.best ?: 0, run), now, days, if (better) day else old?.bestDay ?: "")
        val hk = day.take(7) + "|" + hour
        return s.copy(songs = s.songs + (k to loop), lastKey = k, run = run,
            hours = if (hour in 0..23) s.hours + (hk to ((s.hours[hk] ?: 0) + 1)) else s.hours, since = s.since.ifEmpty { day })
    }

    /** Another song began (or the same one was started by hand): the run in one go is over. */
    fun broke(s: Saved): Saved = if (s.run == 0) s else s.copy(lastKey = "", run = 0)

    /** The run a song is on now (0: none). */
    fun runOf(s: Saved, t: Track): Int = if (s.lastKey == key(t)) s.run else 0

    /** The counted ones and the ones from your history as one: a song in both has its days and counts added (they
     *  never share a day), its longest run the longer of the two. */
    fun all(s: Saved): Saved {
        if (s.imports.isEmpty()) return s
        val out = HashMap(s.imports)
        for ((k, l) in s.songs) out[k] = out[k]?.let { i ->
            Loop(l.track, l.n + i.n, maxOf(l.best, i.best), maxOf(l.last, i.last), i.days + l.days, if (l.best >= i.best) l.bestDay else i.bestDay) } ?: l
        return s.copy(songs = out, imports = emptyMap())
    }

    /** The repeats a history shows, as [Saved.imports]. sources: each service's plays on their own, in order of time
     *  (the same play in two of them, Spotify's and its Last.fm scrobble, is then one repeat, not two: a song's count
     *  for a day is the highest any one of them shows). dayOf: a time's day; before: only days before this one ("":
     *  all). hours are added to [into]. */
    fun fromHistory(sources: List<List<RepeatFactsRules.Play>>, dayOf: (Long) -> String, hourOf: (Long) -> Int, before: String): Pair<Map<String, Loop>, Map<String, Int>> {
        class Got(val title: String, val artist: String) { val days = HashMap<String, Int>(); var best = 0; var bestDay = ""; var last = 0L }
        val out = HashMap<String, Got>(); val hours = HashMap<String, Int>()
        var first = true
        for (plays in sources) {
            val mine = HashMap<String, HashMap<String, Int>>()
            for ((p, run) in RepeatFactsRules.inferred(plays)) {
                val day = dayOf(p.t)
                if (before.isNotEmpty() && day >= before) continue
                val k = key(Track("", title = p.title, artist = p.artist))
                val g = out.getOrPut(k) { Got(p.title, p.artist) }
                mine.getOrPut(k) { HashMap() }.merge(day, 1, Int::plus)
                if (run > g.best) { g.best = run; g.bestDay = day }
                g.last = maxOf(g.last, p.t * 1000)
                if (first) hours.merge(day.take(7) + "|" + hourOf(p.t), 1, Int::plus)   // the hours from one source are enough
            }
            for ((k, days) in mine) for ((d, n) in days) out.getValue(k).days.merge(d, n) { a, b -> maxOf(a, b) }
            if (mine.isNotEmpty()) first = false
        }
        return out.filterValues { it.days.isNotEmpty() }.mapValues { (k, g) ->
            Loop(Track("repeat:$k", title = g.title, artist = g.artist), g.days.values.sum(), g.best, g.last, g.days, g.bestDay) } to hours
    }

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
