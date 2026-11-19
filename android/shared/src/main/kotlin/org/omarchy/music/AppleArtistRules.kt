package org.omarchy.music

/** Apple's Play Activity file is the one file of its download that says exactly when each play began, which is what
 *  Repeats needs. But in many downloads its rows name the song and leave the artist out. The download's other files
 *  (a song's plays per day, the track list) do name the artist with the title, so a play's artist is found there by
 *  its title: the artist that title was played most by. */
object AppleArtistRules {
    fun key(title: String): String = norm(cleanTitle(title))

    /** known: (title, artist, plays) from the files that name both. A title's artist: the one with the most plays of it. */
    fun byTitle(known: Iterable<Triple<String, String, Int>>): Map<String, String> {
        val counts = HashMap<String, HashMap<String, Int>>()
        for ((title, artist, n) in known) {
            if (title.isBlank() || artist.isBlank()) continue
            counts.getOrPut(key(title)) { HashMap() }.merge(artist.trim(), maxOf(n, 1), Int::plus)
        }
        return counts.filterKeys { it.isNotEmpty() }.mapValues { (_, by) -> by.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first().key }
    }

    /** The artist for a play that came without one ("": nobody is known to have a song of that name). */
    fun artist(byTitle: Map<String, String>, title: String): String = byTitle[key(title)].orEmpty()
}
