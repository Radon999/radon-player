package org.omarchy.music

/** Your copy of a song: when two names are the same song for certain, so the one you have is always the one played
 *  and it is never downloaded a second time. The album doesn't come into it: a single moved into its album (or
 *  retagged any other way) is still the same song. */
object CopyRules {
    /** The words in a title that make it another version ("live", "remix", "acoustic"…). */
    fun versionWords(title: String): Set<String> = SONG_VERSION.findAll(title).map { it.value.lowercase() }.toSet()

    /** The same song: the same title and artist as Most played matches them (the lead artist, featurings and
     *  punctuation aside) and the same kind of version. "Song (Live)" is not your "Song". */
    fun sameSong(titleA: String, artistA: String, titleB: String, artistB: String): Boolean =
        statsKey(titleA, artistA) == statsKey(titleB, artistB) && versionWords(titleA) == versionWords(titleB)

    /** Of songs asked to be downloaded: the ones to download, and the ones you have already (owned says so). With
     *  keepBoth (Settings: a download you have already › keep both, or you said so when asked) all are downloaded. */
    fun <T> toDownload(asked: List<T>, keepBoth: Boolean, owned: (T) -> Boolean): Pair<List<T>, List<T>> =
        if (keepBoth) asked to emptyList() else asked.partition { !owned(it) }

    /** What is said about the ones you have already. */
    fun haveText(titles: List<String>): String = when (titles.size) {
        0 -> ""
        1 -> "You have “${titles[0]}” already: your copy plays, nothing was downloaded again"
        else -> "You have ${titles.size} of those already: your copies play, they weren't downloaded again"
    }
}
