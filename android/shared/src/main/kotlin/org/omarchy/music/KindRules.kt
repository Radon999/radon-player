package org.omarchy.music

/** What makes a song (or an artist, an album, a decade) fit one kind of DJ set: each kind's own rule, needing nothing
 *  but what it is told. (Where the songs and your listening come from stays with the DJ.) */
object KindRules {
    const val DAY = 86_400L

    private fun lead(t: Track) = t.artist.split(",")[0].trim()

    /** Rediscover: a song you've played three times or more, not lately, and not for three months (last: when you
     *  last played it, 0 when that isn't known; now: both in seconds). */
    fun shelved(plays: Int, last: Long, now: Long, playedLately: Boolean): Boolean = plays >= 3 && !playedLately && now - last > 90 * DAY

    /** A throwback: a song of that year you haven't played for two months. */
    fun longUnplayed(last: Long, now: Long): Boolean = now - last > 60 * DAY

    /** Not skipped: of rows (a song, its skips, the times it was heard out) over a while, the ones heard out and
     *  never skipped. */
    fun <T> heardOut(rows: List<Triple<T, Int, Int>>): List<Triple<T, Int, Int>> = rows.filter { it.second == 0 && it.third >= 1 }

    /** Not skipped: how likely a song comes up: the more often it was heard out, and the less you've ever skipped it
     *  (skipRate: 0 never … 1 always), the likelier. */
    fun heardWeight(full: Int, skipRate: Double): Double = full * (0.5 + 0.5 * (1 - skipRate))

    /** Second chances: of rows (a song, its skips, the times it was heard out), the songs skipped three times or
     *  more and at least as often as they were heard out. */
    fun <T> secondChances(rows: List<Triple<T, Int, Int>>): List<T> = rows.filter { (_, sk, full) -> sk >= 3 && sk >= full }.map { it.first }

    /** One-hit wonders: the songs of the artists you have only a song or two of. */
    fun fewOfAnArtist(songs: List<Track>): List<Track> = songs.groupBy { norm(lead(it)) }.values.filter { it.size in 1..2 }.flatten()

    /** The decade of a release year (1985: 1980), null for a year that can't be one. */
    fun decadeOf(year: Int): Int? = year.takeIf { it in 1950..2100 }?.let { it / 10 * 10 }

    /** A decade as the DJ says it: "the 80s", "the 2000s". */
    fun decadeName(decade: Int): String = if (decade < 2000) "the ${decade % 100}s" else "the ${decade}s"

    /** Decade trip: songs (each with its decade) by decade, only the decades with eight songs or more. */
    fun <T> decades(sample: List<Pair<T, Int>>): Map<Int, List<T>> = sample.groupBy({ it.second }, { it.first }).filter { it.value.size >= 8 }

    /** Album moment: an album's songs (each with its track number, 0 unknown) in the album's order, the ones without
     *  a number left out; null when fewer than three have one. */
    fun <T> albumOrder(songs: List<Pair<T, Int>>): List<T>? {
        val numbered = songs.filter { it.second > 0 }
        if (numbered.size < 3) return null
        return numbered.sortedBy { it.second }.map { it.first }
    }

    /** Album moment: every three in a row of an album in order, but for the runs with a song that's out. */
    fun <T> runsOfThree(ordered: List<T>, out: (T) -> Boolean): List<List<T>> =
        (0..ordered.size - 3).map { i -> ordered.subList(i, i + 3) }.filter { r -> r.none(out) }

    /** Artist spotlight: the artists that had the spotlight lately (history: oldest first) and wait their turn: all
     *  but three of the artists there are, forty at most. */
    fun spotlightWaits(history: List<String>, artists: Int): Set<String> = history.takeLast(maxOf(0, minOf(40, artists - 3))).toSet()

    /** The unreleased corner: a file in a folder (or with a name) that says unreleased. */
    fun unreleased(path: String): Boolean = path.contains("unreleased", ignoreCase = true)
}
