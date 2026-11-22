package org.omarchy.music

/** Tags match: two copies of a song whose names differ a little (a word more or less, "The Dirty Heads" and "Dirty
 *  Heads", punctuation, a fixed album or artist name) are still found to be one song, when nothing surer has matched
 *  them. It is the duplicates' "maybe the same" ([maybeSame]: a title nearly the same by an artist they share), held
 *  tighter: the lengths within a second, the same numbers in the titles (v1 is never v2), and the same kind of version
 *  (a live, remix, demo, rough or snippet is never the studio song). */
object SyncMatchRules {
    private val MORE = Regex("\\b(rough|snippet|leak|unfinished|early|alt|og|clean|explicit)\\b", RegexOption.IGNORE_CASE)

    /** The words that make a title another version of the song. */
    fun kinds(title: String): Set<String> = CopyRules.versionWords(title) + MORE.findAll(title).map { it.value.lowercase() }

    fun same(a: Track, b: Track): Boolean {
        if (a.duration <= 0 || b.duration <= 0 || Math.abs(a.duration - b.duration) > 1.0) return false
        if (kinds(a.title) != kinds(b.title)) return false
        return maybeSame(a, b)
    }
}
