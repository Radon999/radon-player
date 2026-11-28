package org.omarchy.music

/** The downloads that didn't come (Downloads › Didn't download): which are kept, and which go when a song is tried
 *  again or is here after all. Pure. */
object DownloadQueueRules {
    /** The most kept: the newest. */
    const val KEEP = 300

    /** The list after a batch: the new failures first, each song once (its newest reason), without the ones saved since
     *  (here), the newest [KEEP]. */
    fun <T> failedNow(before: List<Pair<T, String>>, new: List<Pair<T, String>>, here: (T) -> Boolean, id: (T) -> String): List<Pair<T, String>> {
        val seen = HashSet<String>()
        return (new + before).filter { (t, _) -> !here(t) && seen.add(id(t)) }.take(KEEP)
    }

    /** The reason, short enough for a row; a song no service had says so. */
    fun why(reason: String?): String = reason?.trim()?.replace(Regex("\\s+"), " ")?.take(120).orEmpty().ifEmpty { "it couldn't be downloaded" }
    const val NOT_FOUND = "not found on Tidal or YouTube"

    /** What's said when the app starts with downloads left from last time. */
    fun carryOn(n: Int): String = "Carrying on with $n download${if (n != 1) "s" else ""} from last time"
}
