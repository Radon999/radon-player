package org.omarchy.music

/** A song's plays counted by its album too (the owner's wish: the album is always part of it): Imagine Dragons'
 *  "Yesterday" on Evolve, and the other "Yesterday" from the It's Time EP sessions, each with its own plays.
 *
 *  A song's plays are counted by its name and lead artist ([statsKey]), which put two different songs of one name
 *  together. Now, where your copies have one name and artist on more than one album, each album is a "version" with
 *  its own plays, a play counted for the album it was on. What stays one: an album's editions (Deluxe, Remastered…:
 *  [albumBase]), and a single named after the song (it goes with the song). A play with no album, or on an album you
 *  haven't got, is the one played the most's (the main one, which keeps the song's own key, so everything else finds
 *  it as before). "Plays on singles and albums" (Settings) still puts a single's or an EP's plays on the album you pick. */
object PlaysApart {
    /** The songs told apart: statsKey -> its versions (each the album keys of one length), the main one first.
     *  Set by the stats as they're added up (and read back with them). */
    @Volatile var split: Map<Pair<String, String>, List<List<String>>> = emptyMap()

    fun albumKey(album: String): String = norm(albumBase(album)).ifEmpty { norm(album) }

    /** From your songs: each name and artist on more than one album of yours, with its versions (in no order yet). */
    fun distinct(songs: List<Track>): Map<Pair<String, String>, List<List<String>>> {
        val out = HashMap<Pair<String, String>, List<List<String>>>()
        for ((key, same) in songs.filter { it.album.isNotBlank() && it.title.isNotBlank() }.groupBy { statsKey(it.title, it.artist) }) {
            if (same.size < 2) continue
            val single = norm(cleanTitle(same[0].title))
            val albums = same.map { albumKey(it.album) }.filter { it.isNotEmpty() && it != single }.distinct().sorted()
            if (albums.size < 2) continue
            out[key] = albums.map { listOf(it) }   // each album its own (a list: one version can hold more one day)
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
