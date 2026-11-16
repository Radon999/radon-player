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

    /** Artists for you: the artists you might like, best first, each with the ones of yours it's like. The artists
     *  you play most (allTime: the top twelve, thisMonth: the top six, counted double; otherwise: your artists when
     *  there are no counts) each bring their similar artists (similarTo: a name and how alike, 0 to 1), added up; the
     *  ones you play already (known: names as [norm] makes them) are left out. */
    fun artistsForYou(allTime: Map<String, Int>, thisMonth: Map<String, Int>, otherwise: List<String>, known: Set<String>,
                      similarTo: (String) -> List<Pair<String, Double>>): List<Pair<String, List<String>>> {
        val weight = HashMap<String, Double>()
        allTime.entries.sortedByDescending { it.value }.take(12).forEach { (a, n) -> weight.merge(a, Math.log(1.0 + n), Double::plus) }
        thisMonth.entries.sortedByDescending { it.value }.take(6).forEach { (a, n) -> weight.merge(a, 2 * Math.log(1.0 + n), Double::plus) }
        if (weight.isEmpty()) otherwise.take(10).forEach { weight[it] = 1.0 }
        val score = HashMap<String, Double>(); val because = HashMap<String, MutableList<String>>(); val shown = HashMap<String, String>()
        for ((seed, w) in weight.entries.sortedByDescending { it.value }.take(10)) for ((name, match) in similarTo(seed)) {
            val k = norm(name)
            if (k.isEmpty() || k in known) continue
            score.merge(k, w * match, Double::plus); shown.putIfAbsent(k, name)
            because.getOrPut(k) { ArrayList() }.let { if (seed !in it) it += seed }
        }
        return score.entries.sortedByDescending { it.value }.map { (k, _) -> shown.getValue(k) to because[k].orEmpty() }
    }

    /** A mood's set with new songs in it: songs you know, with a new one third and fifth (known, known, new, known,
     *  new, known…), most songs at most; when the known ones run out the new ones follow, and the other way round. */
    fun <T> withNewOnes(known: List<T>, news: List<T>, most: Int): List<T> {
        val songs = ArrayList<T>()
        val ki = known.iterator(); val ni = news.iterator()
        var i = 0
        while ((ki.hasNext() || ni.hasNext()) && songs.size < most) {
            if ((i == 2 || i == 4) && ni.hasNext()) songs += ni.next() else if (ki.hasNext()) songs += ki.next() else songs += ni.next()
            i++
        }
        return songs
    }

    /** The unreleased corner: a file in a folder (or with a name) that says unreleased. */
    fun unreleased(path: String): Boolean = path.contains("unreleased", ignoreCase = true)

    /** Your usual for this hour: how the DJ names the time ("this morning", "tonight") and the part of the day
     *  ("morning", "evening"). */
    fun dayPart(hour: Int): Pair<String, String> = when (hour) {
        in 5..11 -> "this morning" to "morning"
        in 12..16 -> "this afternoon" to "afternoon"
        in 17..22 -> "tonight" to "evening"
        else -> "at this hour" to "late night"
    }

    /** A mood's set: the moods to pick from, each with its weight, the heaviest first: the plays of each mood lately,
     *  and the ones around this hour counted double; the top three, and only moods with a weight of three or more. */
    fun moodsToPick(lately: Map<String, Int>, thisHour: Map<String, Int>): List<Pair<String, Int>> {
        val weight = HashMap<String, Int>()
        lately.forEach { (m, n) -> weight.merge(m, n, Int::plus) }
        thisHour.forEach { (m, n) -> weight.merge(m, 2 * n, Int::plus) }
        return weight.entries.sortedByDescending { it.value }.take(3).filter { it.value >= 3 }.map { it.key to it.value }
    }

    /** One of a few things, the heavier likelier: roll is a number from 0 up to (not with) the weights' sum. */
    fun <T> weighted(choices: List<Pair<T, Int>>, roll: Int): T {
        var r = roll
        return choices.first { r -= it.second; r < 0 }.first
    }

    /** A mood is said to be this hour's (not just lately's) when its plays around this hour, doubled, reach its
     *  plays lately. */
    fun moodByHour(mood: String, lately: Map<String, Int>, thisHour: Map<String, Int>): Boolean = (thisHour[mood] ?: 0) * 2 >= (lately[mood] ?: 0)

    /** New for you: what each seed song brought, taken in turns (the first of each, then the second of each…), so no
     *  one seed fills the set. */
    fun <T> interleave(lists: List<List<T>>): List<T> =
        (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }

    /** A word about the day before a set: which one fits now ("t-fri": Friday evening, "t-sat": Saturday, "t-sun":
     *  Sunday morning, "t-mon": Monday morning, "t-late": past midnight, "t-early": very early), null for none. */
    fun timeFlavor(day: java.time.DayOfWeek, hour: Int): String? = when {
        day == java.time.DayOfWeek.FRIDAY && hour >= 17 -> "t-fri"
        day == java.time.DayOfWeek.SATURDAY -> "t-sat"
        day == java.time.DayOfWeek.SUNDAY && hour < 13 -> "t-sun"
        day == java.time.DayOfWeek.MONDAY && hour in 5..11 -> "t-mon"
        hour in 0..3 -> "t-late"
        hour in 5..7 -> "t-early"
        else -> null
    }

    /** A genre set from Last.fm: how much each tag weighs, from the tags of the artists you play most (each
     *  artist's first six, in small letters, added up), the tags that say nothing about the music (junk) left out. */
    fun tagWeights(tagsOfArtists: List<List<Pair<String, Int>>>, junk: Set<String>): Map<String, Int> {
        val weight = HashMap<String, Int>()
        for (tags in tagsOfArtists) for ((tag, n) in tags.take(6)) if (tag.lowercase() !in junk) weight.merge(tag.lowercase(), n, Int::plus)
        return weight
    }

    /** A genre set: the three heaviest of the genres (or tags) to choose among. */
    fun <K> topThree(weight: Map<K, Int>): List<K> = weight.entries.sortedByDescending { it.value }.take(3).map { it.key }

    /** A tag's songs: the ones by artists you play (yours: names as [norm] makes them) first, the order kept
     *  otherwise. */
    fun yoursFirst(pool: List<Track>, yours: Set<String>): List<Track> = pool.sortedBy { norm(it.artist.split(",")[0]) !in yours }

    /** The plays of a song the DJ marks ("This is your 100th play of…"). */
    val MILESTONES = listOf(25, 50, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000)
    fun milestone(play: Int): Boolean = play in MILESTONES

    /** Before a song: it's in your all time top ten (place: 0 the first) and you've played it ten times or more. */
    fun topTen(place: Int, plays: Int): Boolean = place in 0..9 && plays >= 10

    /** Before a song: never skipped, and heard out eight times or more. */
    fun neverSkipped(skips: Int, heardOut: Int): Boolean = skips == 0 && heardOut >= 8

    /** Before a song: a new obsession, found in the last three weeks and played five times or more already. */
    fun newObsession(daysSinceFirst: Int, plays: Int): Boolean = daysSinceFirst in 1..21 && plays >= 5

    /** Before a song: an old friend, first played two years ago or more. */
    fun oldFriend(daysSinceFirst: Int): Boolean = daysSinceFirst >= 730
}
