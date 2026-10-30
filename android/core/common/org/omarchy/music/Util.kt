package org.omarchy.music


/** tracks in a new random order; after (the song that just played) isn't first. */
fun shuffleSongs(tracks: List<Track>, after: Track? = null, rng: kotlin.random.Random = SHUFFLE_RANDOM,
                 weight: ((Track) -> Double)? = Music.shuffleWeight()): List<Track> {
    // Smart shuffle: a weighted draw, each song's chance by its weight; else plain chance.
    val out = (if (weight != null) tracks.sortedByDescending { Math.pow(rng.nextDouble(), 1.0 / maxOf(0.01, weight(it))) }
        else tracks.shuffled(rng)).toMutableList()
    if (after != null && out.size > 1 && songKey(out[0]) == songKey(after)) {
        val j = rng.nextInt(1, out.size)
        out[0] = out[j].also { out[j] = out[0] }
    }
    return out
}

/** The artists known on their own (as soloKey keys them): in your songs, or among names played with a credit of their own. */
fun soloArtists(extra: Collection<String> = emptyList()): Set<String> {
    val mine = runCatching { Music.mySongs().flatMap { ownArtists(it.artist) } }.getOrDefault(emptyList())
    return (mine + extra).filter { it.isNotBlank() && !Regex(" & | x | X | and |,|;").containsMatchIn(it) }.map(::soloKey).toSet()
}

/** A song in Most played that may be another counted twice: [to] (title, artist, plays) is the one it'd count as. */
data class DuplicateSong(val title: String, val artist: String, val plays: Int, val to: Triple<String, String, Int>,
                         val others: List<String>, val version: Boolean)

/** What marks a version as a song of its own in Duplicates in Most played. */
private val OWN_VERSION = Regex("\\b(stripped|acoustic sessions?|remix(es)?|rmx|orchestra(l)?|live)\\b", RegexOption.IGNORE_CASE)

/** Songs that may be one song counted twice (the same name but for what's after it, or its punctuation), or versions of
 *  one (acoustic, live, remix): each onto its group's most played one that isn't a version. The desktop's duplicate_songs_plan. */
fun duplicateSongsPlan(songs: List<Stats.Song>, picks: Map<String, String>): List<DuplicateSong> {
    val groups = LinkedHashMap<Pair<String, String>, MutableList<Stats.Song>>()
    for (s in songs) {
        val lt = looseTitle(s.title)
        if (lt.length >= 2 && s.artist.isNotBlank()) groups.getOrPut(lt to Stats.statsKey("", s.artist).second) { ArrayList() } += s
    }
    fun extra(t: String) = Regex("^[^(\\[\\-–—]*").replace(t, "")
    // Versions marked in brackets or after a dash ("Song (Stripped)", "Song - Live", "Song [Remix]") are songs of their
    // own, not the song counted twice: left out. A plain word in the name ("Live Forever") isn't a mark.
    fun ownVersion(t: String) = OWN_VERSION.containsMatchIn(extra(t))
    val out = ArrayList<DuplicateSong>()
    for (g0 in groups.values) {
        val g = g0.filter { !ownVersion(it.title) }
        if (g.size < 2) continue
        val to = g.maxWithOrNull(compareBy<Stats.Song>({ !SONG_VERSION.containsMatchIn(extra(it.title)) }, { it.plays }))!!
        for (x in g) {
            if (x === to || songPickKey(x.title, x.artist) in picks) continue
            out += DuplicateSong(x.title, x.artist, x.plays, Triple(to.title, to.artist, to.plays),
                g.filter { it !== x && it !== to }.map { it.title }, SONG_VERSION.containsMatchIn(extra(x.title)))
        }
    }
    return out.sortedWith(compareBy({ norm(it.artist) }, { norm(it.to.first) }, { -it.plays }))
}
