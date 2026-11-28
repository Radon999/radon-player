package org.omarchy.music

/** Every song by its album too (the owner's rule: a play is a song's only when its title, its artist and its album are
 *  the song's). Imagine Dragons' "Yesterday" on Evolve and the other "Yesterday" from the It's Time EP sessions are two
 *  songs, each with its own plays; so are a song's single, its album's copy and a compilation's.
 *
 *  The plays are counted by name and lead artist ([statsKey]) and album ([albumKey]: an album's editions, Deluxe,
 *  Remastered…, are one). For each song the stats keep its albums ([split]), the one played most first: that one keeps
 *  the song's own key (with the plays that came with no album), the others are marked with their album. A song of
 *  yours on an album none of its plays were on is a song of its own, not played yet. "Plays on singles and albums"
 *  (Settings) still puts a single's or an EP's plays on the album you pick. */
object PlaysApart {
    /** Each song's albums: statsKey -> its albums (each a list, of one), the one played most first. Set by the stats
     *  as they're added up (and read back with them). */
    @Volatile var split: Map<Pair<String, String>, List<List<String>>> = emptyMap()
        set(v) { field = v; keys.clear() }   // (each song's key worked out again, by the new albums)

    // Worked out once each and kept (a list's every row, and every comparison of a sort by plays, asks): each takes a few
    // regexes, and asked afresh every time they made long lists and playlists slow to open
    private val albumKeys = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val keys = java.util.concurrent.ConcurrentHashMap<Triple<String, String, String>, Pair<String, String>>()

    /** Words an album's name can have or leave out and stay the same album ("It's Time EP Sessions", "It's Time Sessions"). */
    private val FILLER = setOf("ep", "lp", "the", "a", "album", "record")

    fun albumKey(album: String): String {
        if (album.isEmpty()) return ""
        albumKeys[album]?.let { return it }
        if (albumKeys.size > 50_000) albumKeys.clear()
        return (norm(albumBase(album)).ifEmpty { norm(album) }).also { albumKeys[album] = it }
    }

    /** Which of a song's albums album is: the same album (its editions as one), else one written a little another way
     *  (one name holding the other, mostly the same: "It's Time EP Sessions" and "It's Time Sessions"; never "Night
     *  Visions" for "Night Visions Live"), one only; else null (an album none of its plays were on). */
    fun versionOf(album: String, versions: List<List<String>>): String? {
        val a = albumKey(album)
        if (a.isEmpty()) return null
        versions.firstOrNull { a in it }?.let { return it[0] }
        if (a.length < 4) return null
        // every word of the shorter name in the longer, and the rest only words like "EP" (never "Live", "Acoustic")
        fun words(x: String) = x.split(' ').filter { it.isNotEmpty() }.toSet()
        val aw = words(a)
        fun near(x: String): Boolean {
            if (x.length < 4) return false
            val xw = words(x)
            val (small, big) = if (xw.size <= aw.size) xw to aw else aw to xw
            return small.size >= 2 && big.containsAll(small) && (big - small).all { it in FILLER }
        }
        return versions.filter { v -> v.any { near(it) } }.singleOrNull()?.get(0)
    }

    /** The key a song's plays are kept under: base (its key as the caller keeps it) for its main album (or no album),
     *  else base marked with its album: one of its other albums, or one none of its plays were on (none counted). */
    fun key(base: Pair<String, String>, title: String, artist: String, album: String,
            split: Map<Pair<String, String>, List<List<String>>> = this.split): Pair<String, String> {
        if (split.isEmpty()) return base
        val versions = split[statsKey(title, artist)] ?: return base
        val a = albumKey(album)
        if (a.isEmpty()) return base
        val v = versionOf(album, versions) ?: a
        return if (v == versions[0][0]) base else base.first to base.second + "\u0001" + v
    }

    /** A song's key in the play counts (Music.playCounts): [statsKey], as the stats count it (the lead artist, whatever
     *  else is credited: "Quinn XCII & Jeremy Zucker" is Quinn XCII's), told apart where it's another song of that
     *  name. (It was [songKey], which splits only at commas: your file's "A & B" missed the plays counted under A.) */
    fun playKey(t: Track): Pair<String, String> {
        val k = Triple(t.title, t.artist, t.album)
        keys[k]?.let { return it }
        if (keys.size > 100_000) keys.clear()
        return key(statsKey(t.title, t.artist), t.title, t.artist, t.album).also { keys[k] = it }
    }
}
