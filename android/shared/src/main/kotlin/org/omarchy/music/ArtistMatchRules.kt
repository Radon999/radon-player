package org.omarchy.music

/** New releases: which of the artists of one name on Deezer is yours. Two artists can share a name ("Forrest", a lofi
 *  producer, and "Forrest" who made Standn Still): the one whose releases and songs share titles with your own songs and
 *  albums by that name is yours. None sharing any, with enough of yours to tell (a few songs or albums), is none of
 *  them: a stranger's releases aren't shown (and hiding him can't hide yours). Too little of yours to tell (an artist you
 *  follow without songs of theirs, or a single song): the name alone, the first of them. */
object ArtistMatchRules {
    /** How many of your titles it takes to say "none of these is yours". */
    const val ENOUGH = 3

    /** candidates: each artist of the name (its id, and the titles of its releases and songs, already made plain);
     *  mine: the titles of your songs and albums by that name, made plain. The id of yours, or 0 for none. */
    fun pick(candidates: List<Pair<Long, Set<String>>>, mine: Set<String>): Long {
        if (candidates.isEmpty()) return 0L
        if (mine.isEmpty()) return candidates.first().first
        val scored = candidates.map { (id, titles) -> id to titles.count { it in mine } }
        val best = scored.maxByOrNull { it.second }!!
        return when {
            best.second > 0 -> best.first
            mine.size >= ENOUGH -> 0L
            else -> candidates.first().first
        }
    }
}
