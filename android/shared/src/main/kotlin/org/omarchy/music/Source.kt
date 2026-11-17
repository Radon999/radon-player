package org.omarchy.music

/** A music service you browse: search, your playlists and favourites, likes, artists,
 *  albums and radio. Calls block (run them off the main thread). The audio itself
 *  always comes from Tidal. */
interface Source {
    val key: String
    val name: String
    /** What the Library tab says when you aren't signed in. */
    val hint: String
    val signedIn: Boolean

    fun search(q: String): List<Track>
    /** Songs, artists and albums called q, or with q in their names. */
    fun find(q: String): Found = Found(search(q))
    fun myPlaylists(): List<Album>
    fun playlist(id: String): List<Track>
    fun likedIds(): Set<String>
    fun rate(t: Track, like: Boolean)
    fun artist(name: String): ArtistInfo?
    /** The artists the service has under exactly this name, when there's more than one (two bands called the same),
     *  each with something to tell them apart by; empty when there's one or none, or the service can't say. */
    fun artistsNamed(name: String): List<ArtistChoice> = emptyList()
    /** The page of one of those, by the id [artistsNamed] gave; null when the service has no pages by id. */
    fun artistById(id: String): ArtistInfo? = null
    fun album(id: String): List<Track>
    fun radio(t: Track): List<Track> = emptyList()
    /** The songs of the service's playlist for q ("chill", "2010s hits"), for the DJ; empty if none. */
    fun mixFor(q: String): List<Track> = emptyList()

    /** Every song in your favourites and playlists, each once (for Sync). */
    fun libraryTracks(): List<Track> {
        val seen = HashSet<String>()
        val out = ArrayList<Track>()
        for (pl in myPlaylists()) {
            val tracks = runCatching { playlist(pl.id) }.getOrNull() ?: continue
            for (t in tracks) if (seen.add(t.id)) out += t
        }
        return out
    }
}
