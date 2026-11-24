package org.omarchy.music

/** The song the DJ names first is the song that plays first. A set's line is written before its songs are put in
 *  their playing order (by mood, no artist twice in a row), so a line saying a song "leads", "gets us going" or that
 *  "we start with" it could be followed by another song. The first song the line names by its title goes to the front;
 *  a line that names no title but artists ("…Noah Kahan, Mt. Joy and Hozier…"): a song of the first artist it names. */
object LeadRules {
    /** Which of the set's songs (by their titles, in playing order) the line names first, as the DJ writes a title
     *  (“Title”); -1 when it names none of them. */
    fun named(titles: List<String>, line: String): Int {
        var best = -1
        var at = Int.MAX_VALUE
        titles.forEachIndexed { i, t ->
            if (t.isBlank()) return@forEachIndexed
            val p = line.indexOf("“$t”")
            if (p in 0 until at) { at = p; best = i }
        }
        return best
    }

    /** Which of the set's songs (by their lead artists, in playing order) is the first by the artist the line names
     *  first, as a whole name (not inside another word); -1 when it names none of them. */
    fun namedArtist(artists: List<String>, line: String): Int {
        var best = -1
        var at = Int.MAX_VALUE
        artists.forEachIndexed { i, a ->
            if (a.isBlank()) return@forEachIndexed
            val m = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(a) + "(?![\\p{L}\\p{N}])").find(line) ?: return@forEachIndexed
            if (m.range.first < at) { at = m.range.first; best = i }
        }
        return best
    }

    /** The set with the song its line names first moved to the front (by title, else by artist); as it was when the
     *  line names none (or it leads already). */
    fun <T> leadFirst(songs: List<T>, line: String, artist: (T) -> String = { "" }, title: (T) -> String): List<T> {
        val i = named(songs.map(title), line).takeIf { it >= 0 } ?: namedArtist(songs.map(artist), line)
        return if (i <= 0) songs else listOf(songs[i]) + songs.filterIndexed { j, _ -> j != i }
    }
}
