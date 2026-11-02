package org.omarchy.music

/** Which release a download is saved from, when the same recording is on several: the artist's album first (its
 *  deluxe or special edition over the plain one), then an EP, then a single; a compilation of various artists
 *  (a "Game Music", a "Now That's What I Call…") only when it's on nothing else. Hi-res breaks a tie, then the
 *  release you came from. */
object ReleasePick {
    data class Release(val type: String?, val artist: String?, val title: String, val hires: Boolean, val single: Boolean = false)

    private val EDITION = Regex("\\b(deluxe|special|expanded|anniversary|complete|collector'?s|bonus|extended|edition|version)\\b",
        RegexOption.IGNORE_CASE)

    /** A various-artists compilation. */
    fun compilation(r: Release): Boolean = r.type.equals("COMPILATION", true) ||
        (r.artist ?: "").trim().let { it.equals("Various Artists", true) || it.equals("Various", true) || it.equals("VA", true) }

    /** Album 0, EP 1, single 2, compilation 3. */
    fun kind(r: Release): Int = when {
        compilation(r) -> 3
        r.type.equals("ALBUM", true) -> if (r.single) 2 else 0
        r.type.equals("EP", true) -> 1
        r.type.equals("SINGLE", true) -> 2
        r.single || isSingleRelease(r.title) -> 2
        isEp(r.title) -> 1
        else -> 0
    }

    fun edition(r: Release): Boolean = EDITION.containsMatchIn(r.title)

    /** The index of the release to take. from: the release's title you came from ("" if none). */
    fun best(all: List<Release>, from: String = ""): Int {
        if (all.isEmpty()) return -1
        val f = norm(from)
        return all.indices.sortedWith(compareBy<Int> { kind(all[it]) }
            .thenBy { if (kind(all[it]) == 0 && edition(all[it])) 0 else 1 }
            .thenBy { if (all[it].hires) 0 else 1 }
            .thenBy { if (f.isNotEmpty() && norm(all[it].title) == f) 0 else 1 }).first()
    }
}
