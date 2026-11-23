package org.omarchy.music

/** Search history: the last searches of each place, kept apart (the Search tab's, Albums', Artists', a playlist's…),
 *  newest first, so a search made often is a tap away. */
object SearchHistoryRules {
    /** How many each place keeps. */
    const val MOST = 10

    /** The history with q searched now: put first (an earlier one the same, whatever its capitals, taken out), and when
     *  it carries on the newest one (typed further: "tay" then "taylor swift") it takes that one's place, so the steps of
     *  one search typed aren't each kept. Blank or one letter: not kept. */
    fun add(list: List<String>, q: String): List<String> {
        val t = q.trim().replace(Regex("\\s+"), " ")
        if (t.length < 2) return list
        val low = t.lowercase()
        val newest = list.firstOrNull()?.lowercase()
        val rest = list.filterIndexed { i, s -> s.lowercase() != low && !(i == 0 && newest != null && low.startsWith(newest)) }
        return (listOf(t) + rest).take(MOST)
    }
}
