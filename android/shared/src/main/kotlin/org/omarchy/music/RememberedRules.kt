package org.omarchy.music

/** What the app remembers between starts, checked as it's read: a choice the app no longer knows (an older version's,
 *  or a file edited by hand) becomes the usual one, so a screen never gets a choice it can't show. */
object RememberedRules {
    val PERIODS = setOf("week", "month", "year", "all")
    val TOP_KINDS = setOf("songs", "albums", "artists", "time_songs", "time_albums", "time_artists")
    val COLLAGE_SIZES = listOf(5, 10, 15, 20, 25)
    val TABS = setOf("album", "ep", "single")

    /** Most played's and Skips' time: a week, a month, a year, or all of it. */
    fun period(kept: String): String = if (kept in PERIODS) kept else "all"

    /** Most played's kind. "time" was the one list by listening time there used to be: the artists' now. */
    fun topKind(kept: String): String = (if (kept == "time") "time_artists" else kept).let { if (it in TOP_KINDS) it else "songs" }

    /** Collage: covers across. */
    fun collage(kept: Int): Int = if (kept in COLLAGE_SIZES) kept else 25

    fun skipOrder(kept: String): String = if (kept in setOf("most", "least", "heard")) kept else "most"

    /** A download you have already: ask, keep both, or replace. */
    fun dupeAction(kept: String): String = if (kept in setOf("ask", "keep", "replace")) kept else "ask"

    /** The tab each song was put in by hand (Tagger › Tab): only the tabs there are. */
    fun tabPicks(kept: Map<String, String>): Map<String, String> = kept.filterValues { it in TABS }

    /** The downloads folder is Music itself (3.0.1 moved those to Music/Artists/, once). */
    fun musicItself(dir: String): Boolean = dir.trim().trimEnd('/', '\\').let { it.isNotEmpty() && it.equals("Music", true) }
}
