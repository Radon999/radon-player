package org.omarchy.music

/** One artist under two names: the name you gave them in your files ("Forrest") and the one the services credit
 *  ("Forrest Frank", "Forrest Frank, Connor Price"). For telling whether a service's song is one you have already
 *  (Discovery, Missing from this album): the song's name must be the same too, so a loose match here is safe. */
object SameArtist {
    private fun words(s: String) = norm(s).split(' ').filter { it.isNotEmpty() }

    /** short is the first words of long ("forrest" of "forrest frank"), and long enough not to be chance ("a", "the"). */
    private fun firstWordsOf(short: List<String>, long: List<String>) =
        short.isNotEmpty() && short.size < long.size && long.subList(0, short.size) == short && short.sumOf { it.length } >= 4

    /** Whether the two credits share an artist ([sameArtist]: "Sigrid" and "Sigrid, Bring Me The Horizon", "K.Flay" and
     *  "K Flay"), or an artist of one is the first words of an artist of the other ("Forrest" and "Forrest Frank"). */
    fun alias(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (sameArtist(a, b)) return true
        val x = splitArtists(a).map(::words); val y = splitArtists(b).map(::words)
        return x.any { p -> y.any { q -> firstWordsOf(p, q) || firstWordsOf(q, p) } }
    }

    /** A song's name as songs are told apart by it (no version, "feat." or punctuation): [songKey]'s first half. */
    fun title(s: String) = norm(cleanTitle(s))
}

/** An album's songs in its own order. */
object AlbumOrder {
    /** tracks by their disc, then their number on it (disc and no give them; a number outside 1..9998 is "none", a
     *  disc below 1 is disc 1, as a one-disc album's songs say nothing): the numbered ones first in order, songs
     *  with the same place and the ones with no number in the order they came. */
    fun sorted(tracks: List<Track>, disc: (Track) -> Int = { 1 }, no: (Track) -> Int): List<Track> =
        tracks.map { t ->
            val n = no(t).takeIf { it in 1..9998 }
            t to if (n == null) Long.MAX_VALUE else disc(t).coerceIn(1, 999) * 10_000L + n
        }.sortedBy { it.second }.map { it.first }
}

/** The Unreleased tab: which of your folders are in it, and what each is called there. */
object UnreleasedRules {
    private fun parts(folder: String) = folder.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }

    /** Where the unreleased part of folder begins: the number of path parts up to and with a folder named
     *  "Unreleased" (any case), or one of the folders you chose (extra: it and everything in it); -1 when the folder
     *  isn't in the tab. */
    private fun rootEnd(folder: String, extra: Collection<String>): Int {
        val p = parts(folder)
        val named = p.indexOfFirst { it.equals("unreleased", ignoreCase = true) }
        if (named >= 0) return named + 1
        for (e in extra) {
            val r = parts(e)
            if (r.isNotEmpty() && p.size >= r.size && p.subList(0, r.size).zip(r).all { (a, b) -> a.equals(b, ignoreCase = true) }) return r.size
        }
        return -1
    }

    /** Whether a song in folder belongs in the tab. */
    fun has(folder: String, extra: Collection<String> = emptyList()): Boolean = rootEnd(folder, extra) >= 0

    /** What the folder is called in the tab: its path below the unreleased folder ("Coldplay", "castle comer / lone
     *  survivor"); the unreleased folder's own name for songs right in it. */
    fun nameIn(folder: String, extra: Collection<String> = emptyList()): String {
        val p = parts(folder); val at = rootEnd(folder, extra)
        if (at < 0) return p.lastOrNull().orEmpty()
        return p.drop(at).joinToString(" / ").ifEmpty { p.getOrNull(at - 1).orEmpty() }
    }
}
