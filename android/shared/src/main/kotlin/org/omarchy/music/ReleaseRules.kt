package org.omarchy.music

/** Singles that are on an album or an EP already. A service lists a song's single beside the release it is on, so
 *  a discography is twice as long as it need be and the song is in two places. On a service (an artist's page, a
 *  search) the single is left out and the song is found on its release. Never among your own files: a single you
 *  have without its album is all you have of it. An EP is never left out, whether or not an album has its songs. */
object ReleaseRules {
    /** A song as releases are compared: its name, and the kind of version (the single's, the album's and the EP's
     *  version are one; a live one or a remix is its own). */
    fun songKey(title: String): Pair<String, Set<String>> {
        val t = GeniusRules.plainTitle(title)
        return looseTitle(t).ifEmpty { norm(t) } to CopyRules.versionWords(t)
    }

    /** Of an artist's releases (each with its songs): the ids of the singles (one song) whose song is on a release
     *  of two songs or more. */
    fun foldedSingles(releases: List<Pair<Album, List<Track>>>): Set<String> {
        val held = releases.filter { it.second.size >= 2 }.flatMapTo(HashSet()) { (_, songs) -> songs.map { songKey(it.title) } }
        return releases.filter { (_, songs) -> songs.size == 1 && songKey(songs[0].title) in held }.mapTo(HashSet()) { it.first.id }
    }

    /** What an artist's release says about where its songs are, with the release's songs on the service (null: not
     *  known yet) and the ones of it you have: LOCAL when you have them all (or it isn't known how many there are),
     *  STREAMING when you have none, and when you have some, both with how many of each. */
    fun whereMark(release: List<String>?, yours: List<String>): String {
        if (yours.isEmpty()) return "STREAMING"
        if (release.isNullOrEmpty()) return "LOCAL"
        val have = yours.mapTo(HashSet()) { songKey(it) }
        val local = release.count { songKey(it) in have }
        val streaming = release.size - local
        return if (streaming <= 0) "LOCAL" else if (local == 0) "LOCAL" else "BOTH · $local LOCAL · $streaming STREAMING"
    }

    private fun onARelease(t: Track) = t.album.isNotBlank() && !GeniusRules.isSingleOf(t.title, t.album)

    /** A service's search: of a song found in its single's version and in its album's or EP's, only the release's;
     *  and the single itself is not listed among the albums. What isn't on a release is left as it is. */
    fun search(found: Found): Found {
        val held = found.songs.filter { onARelease(it) }.mapTo(HashSet()) { statsKey(it.title, it.artist) to songKey(it.title).second }
        if (held.isEmpty()) return found
        fun key(t: Track) = statsKey(t.title, t.artist) to songKey(t.title).second
        val songs = found.songs.filter { onARelease(it) || key(it) !in held }
        val names = found.songs.filter { onARelease(it) }.map { it.title to statsKey(it.title, it.artist).second }
        val albums = found.albums.filter { al ->
            names.none { (title, artist) -> GeniusRules.isSingleOf(title, al.title) && albumBase(al.title) == albumBase(title) &&
                (al.sub.isBlank() || artist.isEmpty() || norm(al.sub).replace(" ", "").contains(artist)) }
        }
        return found.copy(songs = songs, albums = albums)
    }
}
