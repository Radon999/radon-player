package org.omarchy.music

/** Unplayed and neglected: the songs of yours you have never played, and the ones you haven't played in a long time;
 *  and the shuffle made of them (Settings › Playback › Shuffle › Unheard and neglected).
 *
 *  That shuffle goes round your songs like one long queue that is never closed. Every shuffle starts with the songs you
 *  haven't heard at all, then the ones heard longest ago, in a loosely shuffled order (never strictly by date). A song
 *  you have heard since its round began is left out of every shuffle, of any list, until all the others of that list
 *  have been heard too: then the round begins again for that list, the ones heard longest ago first. Nothing is kept
 *  but times: when each song was last heard (your stats' and the plays here), and when its round began. */
object RestedRules {
    /** The first round: a song heard in the days before the shuffle was first used counts as heard already. */
    const val RECENT = 3 * 86_400L

    /** Heard in its round: played (last, seconds; 0: never) after the round began for it (released: when its list last
     *  went round; floor: when the first round began). */
    fun heard(last: Long, released: Long, floor: Long): Boolean = last > 0 && last > maxOf(released, floor)

    /** order: the places (in the list given) to play, in order. restarted: every song had been heard, so the round
     *  begins again and all of them are in it (the caller notes the time as their new round). */
    data class Order(val order: List<Int>, val restarted: Boolean)

    /** n songs: last(i) when song i was last heard (0: never), heard(i) whether it was heard in its round. */
    fun order(n: Int, last: (Int) -> Long, heard: (Int) -> Boolean, rng: kotlin.random.Random): Order {
        var pool = (0 until n).filter { !heard(it) }
        val restarted = pool.isEmpty() && n > 0
        if (restarted) pool = (0 until n).toList()
        // Longest ago first (never played before all), songs of the same time in chance order…
        val byAge = pool.shuffled(rng).sortedBy { last(it) }
        // …then loosened: each song may move up to about an eighth of the list from its place
        val reach = maxOf(6.0, byAge.size * 0.12)
        val loose = byAge.mapIndexed { place, i -> i to place + rng.nextDouble() * reach }.sortedBy { it.second }.map { it.first }
        return Order(loose, restarted)
    }

    // ---- the tab ----
    /** The times the Neglected chips offer: (key, its words, days). */
    val SPANS = listOf(Triple("3m", "3 months", 91), Triple("6m", "6 months", 183), Triple("1y", "A year", 365), Triple("2y", "2 years", 730))

    fun unplayed(plays: Int, last: Long): Boolean = plays <= 0 && last <= 0
    /** Played before, and not for at least days. (Played, but nobody knows when: neither unplayed nor neglected.) */
    fun neglected(last: Long, now: Long, days: Int): Boolean = last > 0 && now - last >= days * 86_400L

    /** "3 days", "5 weeks", "7 months", "2 years": how long ago, roughly. */
    fun ago(last: Long, now: Long): String {
        val days = maxOf(0L, (now - last) / 86_400L)
        fun said(n: Long, unit: String) = "$n $unit" + if (n == 1L) "" else "s"
        return when {
            days < 14 -> said(maxOf(1L, days), "day")
            days < 61 -> said(days / 7, "week")
            days < 730 -> said(days / 30, "month")
            else -> said(days / 365, "year")
        }
    }

    /** A song's line in the tab: "Never played", or "Last played 7 months ago · 23 plays". */
    fun line(plays: Int, last: Long, now: Long): String = when {
        unplayed(plays, last) -> "Never played"
        last <= 0 -> "%,d play%s · when isn't known".format(plays, if (plays == 1) "" else "s")
        else -> "Last played ${ago(last, now)} ago" + if (plays > 0) " · %,d play%s".format(plays, if (plays == 1) "" else "s") else ""
    }

    // ---- the three ways to shuffle ----
    const val MODE = "shuffle_mode"
    const val RANDOM = "random"; const val SMART = "smart"; const val RESTED = "rested"
    val MODES = listOf(RANDOM to "Random", SMART to "Smart", RESTED to "Unheard and neglected")
    /** The way kept, else what the old Smart shuffle switch says. */
    fun mode(kept: String, smartSwitch: Boolean): String = kept.takeIf { k -> MODES.any { it.first == k } } ?: if (smartSwitch) SMART else RANDOM
}
