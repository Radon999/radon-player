package org.omarchy.music

/** When something about your listening is worth the DJ telling you between sets: each fact's own bar (enough plays,
 *  long enough ago, a big enough share), needing nothing but the numbers. What the DJ says, and which fact's turn it
 *  is, stay with the DJ. Times are in seconds. */
object FactRules {
    const val DAY = 86_400L
    const val YEAR = 365 * DAY

    /** Your five most played artists of all time, by name in small letters: each one's place (1 the first) and plays. */
    fun artistRanks(artistAll: Map<String, Int>): Map<String, Pair<Int, Int>> =
        artistAll.entries.sortedByDescending { it.value }.take(5).withIndex().associate { it.value.key.lowercase() to (it.index + 1 to it.value.value) }

    /** An artist's place is told from twenty plays up. */
    fun rankTold(plays: Int): Boolean = plays >= 20

    /** The mood you've played most this month, when it has fifteen plays or more. */
    fun topMood(moodMonth: Map<String, Int>): String? = moodMonth.maxByOrNull { it.value }?.takeIf { it.value >= 15 }?.key

    /** "Lately it's been mostly … songs": the month's top mood and its percent, when the month has twenty plays with
     *  a mood and that mood is a quarter of them or more. */
    fun moodShare(moodMonth: Map<String, Int>): Pair<String, Int>? {
        val plays = moodMonth.values.sum()
        return moodMonth.maxByOrNull { it.value }?.takeIf { plays >= 20 && it.value * 4 >= plays }?.let { it.key to it.value * 100 / plays }
    }

    /** An artist of a song coming up: "since" when you first played them more than three years ago, "new" when it
     *  was within the month and you've played them ten times or more this month; else null. */
    fun artistSince(now: Long, first: Long, monthPlays: Int): String? =
        if (now - first > 3 * YEAR) "since" else if (now - first < 30 * DAY && monthPlays >= 10) "new" else null

    /** The time you've spent with a song (from eight plays up, its length known): "hours" and how many when it's an
     *  hour or more, else "minutes" and how many. */
    fun timeWith(plays: Int, seconds: Double): Pair<String, Double>? {
        if (plays < 8 || seconds <= 0) return null
        val h = plays * seconds / 3600
        return if (h >= 1) "hours" to h else "minutes" to plays * seconds / 60
    }

    /** A song's first play: "first" when it was more than two years ago, "crush" when it was one to twenty-one days
     *  ago and you've played it five times or more; else null. */
    fun firstPlay(now: Long, first: Long, plays: Int): String? {
        val days = ((now - first) / DAY).toInt()
        return if (now - first > 2 * YEAR) "first" else if (days in 1..21 && plays >= 5) "crush" else null
    }

    /** A song's skips: "never" when it was heard out eight times or more and never skipped, "skipped" when it was
     *  skipped four times or more and more often than heard out; else null. */
    fun skipsOf(skips: Int, heardOut: Int): String? =
        if (heardOut >= 8 && skips == 0) "never" else if (skips >= 4 && skips > heardOut) "skipped" else null

    /** The hour you listen most at, when it has fifty plays or more. */
    fun peakHour(hours: IntArray): Int? = hours.withIndex().maxByOrNull { it.value }?.takeIf { it.value >= 50 }?.index

    /** The percent of your listening after midnight (hours 0 to 4), told from ten percent, with 200 plays or more. */
    fun nightShare(hours: IntArray, total: Int): Int? = if (total < 200) null else ((0..4).sumOf { hours[it] } * 100 / total).takeIf { it >= 10 }

    /** The percent of your listening before 10 AM (hours 5 to 9), told from a quarter, with 200 plays or more. */
    fun morningShare(hours: IntArray, total: Int): Int? = if (total < 200) null else ((5..9).sumOf { hours[it] } * 100 / total).takeIf { it >= 25 }

    /** How many years your listening goes back, told from two (since: your first play, 0 unknown). */
    fun yearsBack(now: Long, since: Long): Int? = ((now - since) / YEAR).takeIf { since > 0 && it >= 2 }?.toInt()

    /** Your biggest year for music and its plays: with two years or more to compare, and 500 plays or more in it. */
    fun busiestYear(yearPlays: Map<Int, Int>): Pair<Int, Int>? =
        yearPlays.takeIf { it.size >= 2 }?.maxByOrNull { it.value }?.takeIf { it.value >= 500 }?.let { it.key to it.value }

    /** The percent of songs you hear all the way through, told from a hundred songs skipped or heard out. */
    fun heardOutPercent(skips: Int, heardOut: Int): Int? = if (skips + heardOut >= 100) heardOut * 100 / (skips + heardOut) else null

    /** A song coming up that you haven't played for half a year (last: 0 unknown). */
    fun longUnheard(now: Long, last: Long): Boolean = last > 0 && now - last > 180 * DAY
}
