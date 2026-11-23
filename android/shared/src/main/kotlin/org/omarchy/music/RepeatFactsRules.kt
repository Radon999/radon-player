package org.omarchy.music

import org.omarchy.music.RepeatLoopRules.Loop
import org.omarchy.music.RepeatLoopRules.Saved

/** What the DJ and Wrapped make of your repeats ([RepeatLoopRules]): when a song starting over is worth a word, what a
 *  stretch of time's repeats add up to, and the repeats that an imported history holds without saying so. */
object RepeatFactsRules {
    /** A song's repeats worth telling as it reaches them. */
    val MILESTONES = setOf(10, 25, 50, 100, 250, 500, 1000, 2500, 5000)

    /** As a song starts over: what there is to say, or null. run: how many times in a row now; total: its repeats ever
     *  (this one counted); bestBefore: its longest run before this one began. "milestone" (it reached 10, 25, 50…),
     *  "record" (this run has just passed its longest, said once), "nudge" (a long run: something else is offered, only
     *  with [nudge]), "run" (three, then five in a row). */
    fun remark(run: Int, total: Int, bestBefore: Int, nudge: Boolean): String? = when {
        total in MILESTONES -> "milestone"
        bestBefore >= 3 && run == bestBefore + 1 -> "record"
        nudge && run in setOf(8, 12, 20, 30) -> "nudge"
        run == 3 || run == 5 -> "run"
        else -> null
    }

    /** A stretch of time's repeats, for Wrapped. */
    data class Summary(
        val total: Int, val songs: Int, val secs: Double,
        val top: List<Pair<Loop, Int>>,                 // the most repeated songs, each with its count
        val bestRun: Pair<Loop, Int>?,                  // the longest run in one go, and the song
        val artists: List<Pair<String, Int>>,           // repeats added up by artist
        val topDay: Triple<String, Int, String>?,       // the day with the most, how many, and its most repeated song
        val weekdays: List<Int>, val hours: List<Int>,  // Monday first; the hours of the day
        val months: List<Triple<String, Loop, Int>>,    // each month ("2026-11"), its most repeated song and how often
        val type: Pair<String, String>?,                // the kind of repeater you are, and why
    )

    private fun lead(artist: String) = artist.split(",")[0].trim()
    private fun inside(day: String, from: String, to: String) = (from.isEmpty() || day >= from) && (to.isEmpty() || day < to)

    /** The repeats on days from [from] up to (not including) [to] ("2026-01-01"; "": open). limit: how many songs the
     *  list holds (as many as Wrapped's other lists). hidden: songs left out (hidden from stats), so the next ones take
     *  their places. null: none in that time. */
    fun summary(s: Saved, from: String, to: String, limit: Int, hidden: (Loop) -> Boolean = { false }): Summary? {
        val counts = s.songs.values.filter { !hidden(it) }.map { l -> l to l.days.filterKeys { inside(it, from, to) }.values.sum() }.filter { it.second > 0 }
        if (counts.isEmpty()) return null
        val top = counts.sortedWith(compareByDescending<Pair<Loop, Int>> { it.second }.thenByDescending { it.first.last })
        val total = counts.sumOf { it.second }
        val byDay = HashMap<String, Int>(); val daySong = HashMap<String, Pair<String, Int>>(); val weekdays = IntArray(7)
        val monthSong = HashMap<String, Pair<Loop, Int>>(); val monthCount = HashMap<Pair<String, String>, Int>()
        for ((l, _) in counts) for ((day, n) in l.days) {
            if (!inside(day, from, to)) continue
            byDay.merge(day, n, Int::plus)
            if (n > (daySong[day]?.second ?: 0)) daySong[day] = l.track.title to n
            runCatching { weekdays[java.time.LocalDate.parse(day).dayOfWeek.value - 1] += n }
            val m = day.take(7); val k = m to RepeatLoopRules.key(l.track)
            val c = monthCount.merge(k, n, Int::plus)!!
            if (c > (monthSong[m]?.second ?: 0)) monthSong[m] = l to c
        }
        val hours = IntArray(24)
        for ((k, n) in s.hours) { val (m, h) = k.split("|").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1)?.toIntOrNull() ?: -1) }
            if (h in 0..23 && (from.isEmpty() || m >= from.take(7)) && (to.isEmpty() || m <= to.take(7))) hours[h] += n }
        val best = counts.map { it.first }.filter { it.best >= 2 && (it.bestDay.isEmpty() || inside(it.bestDay, from, to)) }.maxByOrNull { it.best }
        val artists = counts.groupBy { lead(it.first.track.artist) }.map { (a, ls) -> a to ls.sumOf { it.second } }.sortedByDescending { it.second }
        val day = byDay.maxByOrNull { it.value }
        return Summary(total, counts.size, counts.sumOf { it.first.track.duration * it.second }, top.take(limit), best?.let { it to it.best },
            artists.take(limit), day?.let { Triple(it.key, it.value, daySong[it.key]?.first ?: "") }, weekdays.toList(), hours.toList(),
            monthSong.entries.sortedBy { it.key }.map { Triple(it.key, it.value.first, it.value.second) },
            type(total, counts.size, best?.best ?: 0, top.first().second))
    }

    /** The kind of repeater you are: its name and why. */
    fun type(total: Int, songs: Int, bestRun: Int, topCount: Int): Pair<String, String>? = when {
        total < 5 -> null
        bestRun >= 10 -> "The Looper" to "Once is never enough: $bestRun times in a row at your most."
        topCount * 2 >= total && total >= 10 -> "The Devotee" to "One song had more than half of all your repeats."
        songs >= 10 && total < songs * 3 -> "The Sampler" to "You let $songs different songs play again, a little of each."
        songs <= 3 -> "The Faithful" to "A few songs, again and again."
        else -> "One more time" to "When a song lands, you let it go round again."
    }

    // ---- repeats an imported history holds ----

    /** A play of an imported history: the song (title and artist), when it began (seconds), and how long it was played
     *  for (0: not known). */
    data class Play(val title: String, val artist: String, val t: Long, val secs: Double = 0.0)

    /** No service's history says "this was on Repeat one". What it does show is the same song played again the moment
     *  it ended: the next play is the same song, it began within [GAP] seconds of where the one before stopped (or,
     *  when the history doesn't say how long a play was, within ten minutes of it), and the one before was played long
     *  enough to have been heard out (over half a minute). Each such play is taken for one repeat. It is a good guess,
     *  not a record: a song queued twice in a row looks the same.
     *  plays: in order of time. Returns each repeat as (the play that repeated, its run so far). */
    const val GAP = 20L
    fun inferred(plays: List<Play>): List<Pair<Play, Int>> {
        val out = ArrayList<Pair<Play, Int>>()
        var prev: Play? = null; var inARow = 0
        for (p in plays) {
            val before = prev
            var again = false
            if (before != null && p.t > before.t && statsKey(before.title, before.artist) == statsKey(p.title, p.artist)) {
                val gap = p.t - before.t
                // a history keeps either when each play began or when it stopped (Spotify's): either way round
                again = if (before.secs > 0 || p.secs > 0)
                            (before.secs >= 30 && (gap - before.secs.toLong()) in -GAP..GAP) || (before.secs >= 30 && p.secs >= 30 && (gap - p.secs.toLong()) in -GAP..GAP)
                        else gap in 45L..600L
            }
            inARow = if (again) inARow + 1 else 0
            if (again) out += p to inARow
            prev = p
        }
        return out
    }
}
