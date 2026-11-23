package org.omarchy.music

/** Search history ([SearchHistoryRules]): each place's last ten searches, kept apart (the Search tab, Albums, Artists,
 *  a playlist…), one a line in a setting of its own. */
object SearchHistory {
    private fun pref(place: String) = "search_history_$place"

    /** The place's searches, newest first. */
    fun of(place: String): List<String> = Prefs.text(pref(place)).lines().filter { it.isNotBlank() }

    fun add(place: String, q: String) {
        if (place.isEmpty() || q.trim().startsWith("http", ignoreCase = true)) return   // (a pasted link isn't a search to go back to)
        val now = of(place)
        val next = SearchHistoryRules.add(now, q)
        if (next != now) Prefs.setText(pref(place), next.joinToString("\n"))
    }

    fun remove(place: String, q: String) = Prefs.setText(pref(place), of(place).filter { it != q }.joinToString("\n"))
    fun clear(place: String) = Prefs.setText(pref(place), "")
}
