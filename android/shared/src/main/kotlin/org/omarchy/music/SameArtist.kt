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
    /** tracks by their number on the album (no gives it; anything outside 1..9998 is "none"): the numbered ones first
     *  in order, songs with the same number and the ones with none in the order they came. */
    fun sorted(tracks: List<Track>, no: (Track) -> Int): List<Track> =
        tracks.map { it to (no(it).takeIf { n -> n in 1..9998 } ?: Int.MAX_VALUE) }.sortedBy { it.second }.map { it.first }
}
