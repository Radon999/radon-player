package org.omarchy.music

/** The songs an artist is featured on: another artist's song that credits them. An artist's page lists these apart
 *  from their own discography (Featured on), so everything they made is there, as a list of songs: one feature never
 *  brings in the other artist's whole album. Only when every song of a release features them is it shown as that
 *  release. A song they lead, with or without guests, is their own and stays in their discography. */
object FeatureRules {
    /** An artist's name as leads are compared (the first one credited, punctuation aside: K.Flay = KFlay). */
    fun lead(artist: String): String = statsKey("", artist).second

    /** Whether the artist leads the song (theirs), going by its artist line. */
    fun leads(songArtist: String, artist: String): Boolean = lead(artist).isNotEmpty() && lead(songArtist) == lead(artist)

    /** A feature: it credits them (credits says so), and someone else leads it. */
    fun isFeature(t: Track, artist: String, credits: (Track) -> Boolean): Boolean = credits(t) && !leads(t.artist, artist)

    class Featured(val albums: List<Pair<Album, List<Track>>>, val songs: List<Track>)

    /** What Featured on shows, from the releases they appear on (each with its songs) and other songs found for them
     *  (a search, a list): the releases where every song (two or more) features them, as releases; every other
     *  feature as a song, each once, with its release's name. */
    fun featured(artist: String, releases: List<Pair<Album, List<Track>>>, more: List<Track>, credits: (Track) -> Boolean): Featured {
        val whole = ArrayList<Pair<Album, List<Track>>>()
        val songs = ArrayList<Track>()
        val seenAlbums = HashSet<String>()
        for ((al, tracks) in releases) {
            val feats = tracks.filter { isFeature(it, artist, credits) }
            if (feats.isEmpty()) continue
            if (tracks.size >= 2 && feats.size == tracks.size) { if (seenAlbums.add(albumBase(al.title))) whole += al to tracks }
            else songs += feats.map { if (it.album.isEmpty()) it.copy(album = al.title) else it }
        }
        songs += more.filter { isFeature(it, artist, credits) }
        val onWhole = whole.flatMapTo(HashSet()) { (_, ts) -> ts.map { key(it) } }
        val seen = HashSet<Pair<Pair<String, String>, Set<String>>>()
        return Featured(whole, songs.filter { key(it) !in onWhole && seen.add(key(it)) })
    }

    private fun key(t: Track) = statsKey(t.title, t.artist) to ReleaseRules.songKey(t.title).second
}
