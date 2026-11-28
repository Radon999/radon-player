package org.omarchy.music

/** Two different songs with the same name by the same artist (Imagine Dragons' "Yesterday" on Evolve, and the other
 *  "Yesterday" from the It's Time EP sessions): their plays counted apart, by the album each play was on.
 *
 *  A song's plays are counted by its name and lead artist ([statsKey]), so a single's, an album's and a remaster's add
 *  up to one song, as they should. That also put two different songs of one name together, both showing the plays
 *  of the two. Your own copies tell them apart: the same name and artist on different albums, at clearly different
 *  lengths (more than [SAME_SECONDS] apart: the same recording on two albums is within a second or two), are
 *  different songs. Each one is then a "version": the albums of one length together. A play on one of a version's
 *  albums is that version's; a play with no album, or on an album you haven't got, is the one played the most's
 *  (the main one, which keeps the song's own key, so everything else finds it as before).
 *  A single named after the song is left out of the telling apart (its length can be an edit's). */
object PlaysApart {
    const val SAME_SECONDS = 4.0

    /** The songs told apart: statsKey -> its versions (each the album keys of one length), the main one first.
     *  Set by the stats as they're added up (and read back with them). */
    @Volatile var split: Map<Pair<String, String>, List<List<String>>> = emptyMap()

    fun albumKey(album: String): String = norm(albumBase(album)).ifEmpty { norm(album) }

    /** From your songs: each name and artist that's more than one song, with its versions (in no order yet). */
    fun distinct(songs: List<Track>): Map<Pair<String, String>, List<List<String>>> {
        val out = HashMap<Pair<String, String>, List<List<String>>>()
        for ((key, same) in songs.filter { it.album.isNotBlank() && it.title.isNotBlank() }.groupBy { statsKey(it.title, it.artist) }) {
            if (same.size < 2) continue
            // album key -> its length (the longest copy's: a file and its download can differ a little)
            val lengths = same.groupBy { albumKey(it.album) }.mapValues { (_, ts) -> ts.maxOf { it.duration } }
                .filter { (a, d) -> a.isNotEmpty() && d > 0 }
            val single = norm(cleanTitle(same[0].title))
            val albums = lengths.filterKeys { it != single }
            if (albums.size < 2) continue
            // Albums of one length (within SAME_SECONDS of each other) are one song
            val versions = ArrayList<MutableList<Pair<String, Double>>>()
            for ((a, d) in albums.entries.sortedBy { it.value }) {
                val v = versions.lastOrNull()
                if (v != null && d - v.last().second <= SAME_SECONDS) v += a to d else versions += mutableListOf(a to d)
            }
            if (versions.size >= 2) out[key] = versions.map { v -> v.map { it.first }.sorted() }
        }
        return out
    }

    /** Which of the versions a play on album is: the version holding that album, else the one whose album's name holds
     *  it or is held by it (one only), else null (not known: it goes with the main one). Its first album names it. */
    fun versionOf(album: String, versions: List<List<String>>): String? {
        val a = albumKey(album)
        if (a.isEmpty()) return null
        versions.firstOrNull { a in it }?.let { return it[0] }
        if (a.length < 4) return null
        val near = versions.filter { v -> v.any { it.length >= 4 && (it.contains(a) || a.contains(it)) } }
        return near.singleOrNull()?.get(0)
    }

    /** The key a song's plays are kept under: base (its key as the caller keeps it), or base marked with its version
     *  when it's one of the others (not the main one). */
    fun key(base: Pair<String, String>, title: String, artist: String, album: String,
            split: Map<Pair<String, String>, List<List<String>>> = this.split): Pair<String, String> {
        if (split.isEmpty()) return base
        val versions = split[statsKey(title, artist)] ?: return base
        val v = versionOf(album, versions) ?: return base
        return if (v == versions[0][0]) base else base.first to base.second + "\u0001" + v
    }

    /** A song's key in the play counts (Music.playCounts): [songKey], told apart where it's another song of that name. */
    fun playKey(t: Track): Pair<String, String> = key(songKey(t), t.title, t.artist, t.album)
}
