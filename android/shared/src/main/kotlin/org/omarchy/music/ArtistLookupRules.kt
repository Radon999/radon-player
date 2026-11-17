package org.omarchy.music

/** The rules of looking an artist up: which names are tried, which services are asked and in what order. */
object ArtistLookupRules {
    /** The names tried for an artist's page: the name itself, then (for a pair, "A & B") the first of the two. */
    fun names(name: String): List<String> = listOf(name) + (if (" & " in name) listOf(name.split(" & ")[0]) else emptyList())

    /** The services asked for an artist's page, in order, each once: the one asked for, the one you browse, then
     *  YouTube Music, Tidal, Apple Music and Spotify. */
    fun order(prefer: String?, browsing: String): List<String> =
        listOfNotNull(prefer, browsing, "ytm", "tidal", "apple", "spotify").distinct()

    /** Whether a service is asked: Tidal and Spotify only when you are signed in to them. */
    fun asked(key: String, signedIn: Boolean): Boolean = key !in setOf("tidal", "spotify") || signedIn

    /** Of the names, the ones to look up: each artist once (same is how two names are told to be one artist). */
    fun <K> once(names: List<String>, same: (String) -> K): List<String> = names.distinctBy(same)

    // ---- two bands with one name ----
    /** What tells one of them from the other: a song it is known for. */
    fun knownFor(song: String?): String = if (song.isNullOrBlank()) "" else "known for “$song”"

    /** Where the one you picked is kept (Prefs), by the name as it is compared. */
    fun pickKey(name: String): String = "artist_is:" + norm(name)

    /** A pick as it is kept ("tidal:12345") and read back: the service and its id for the artist; null for none. */
    fun pick(source: String, id: String): String = "$source:$id"
    fun readPick(kept: String?): Pair<String, String>? =
        kept?.takeIf { ":" in it }?.let { it.substringBefore(':') to it.substringAfter(':') }?.takeIf { it.first.isNotBlank() && it.second.isNotBlank() }

    // ---- every song of an artist ----
    /** Every song on an artist's releases, each once: release by release in the order given, a song that comes again
     *  (the single, then the album; clean and explicit) kept the first time. A song with no album named takes its
     *  release's. mine says whether a song is the artist's at all (a compilation has other people's songs). */
    fun everySong(releases: List<Pair<Album, List<Track>>>, mine: (Track) -> Boolean): List<Track> {
        val seen = HashSet<Pair<Pair<String, String>, Set<String>>>()
        val out = ArrayList<Track>()
        for ((al, songs) in releases) for (t in songs) {
            if (!mine(t)) continue
            if (!seen.add(statsKey(t.title, t.artist) to CopyRules.versionWords(t.title))) continue
            out += if (t.album.isBlank()) t.copy(album = al.title) else t
        }
        return out
    }
}
