package org.omarchy.music

/** A song's two keys, together (the plan's rule R-M1). A song is known by its own key ([songKey]: how your library
 *  names it) and by Most played's ([statsKey]: its lead artist, featurings and punctuation aside, as Last.fm and the
 *  imports name it). "Is this that song" must look at both; with the two in one place neither is forgotten. */
data class TrackKey(val song: Pair<String, String>, val stats: Pair<String, String>) {
    /** Either of the song's keys is among keys. */
    infix fun inAny(keys: Set<Pair<String, String>>): Boolean = song in keys || stats in keys

    /** As the DJ keeps a song it played ("title|artist<tab>title|artist", see [SetRules.recentEntry]). */
    fun entry(): String = SetRules.recentEntry(song, stats)

    companion object {
        fun of(t: Track): TrackKey = TrackKey(songKey(t), statsKey(t.title, t.artist))
        fun of(title: String, artist: String): TrackKey = of(Track("", title = title, artist = artist))
    }
}

/** Songs by either of their keys: has answers "is this song among them" for a song, whichever key the others were
 *  kept under. */
class KeySet(keys: Collection<Pair<String, String>> = emptyList()) {
    private val keys = HashSet(keys)
    val size: Int get() = keys.size

    fun add(t: Track) { TrackKey.of(t).let { keys += it.song; keys += it.stats } }
    fun has(t: Track): Boolean = TrackKey.of(t) inAny keys
    fun has(k: TrackKey): Boolean = k inAny keys

    companion object {
        fun of(tracks: Iterable<Track>): KeySet = KeySet().also { s -> tracks.forEach { s.add(it) } }
    }
}
