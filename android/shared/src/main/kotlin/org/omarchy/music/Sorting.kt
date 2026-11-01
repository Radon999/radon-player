package org.omarchy.music

// Sorting for every list: songs (My music, Downloads, Favorites…) and groups of songs (albums, artists, genres,
// folders, playlists). Each tab offers the sorts that make sense for it; the one you pick is kept per tab.

/** Every sort's name, as the Sort menu shows it. */
val SORT_NAMES = linkedMapOf(
    "order" to "Default",
    "name" to "Name (A–Z)", "name-desc" to "Name (Z–A)",
    "title" to "Title (A–Z)", "title-desc" to "Title (Z–A)",
    "artist" to "Artist (A–Z)", "artist-desc" to "Artist (Z–A)", "album" to "Album (A–Z)",
    "added" to "Recently added", "added-old" to "Oldest added",
    "plays" to "Most played", "plays-least" to "Least played",
    "played" to "Recently played", "played-old" to "Not played in a while",
    "songs" to "Most songs", "songs-least" to "Fewest songs",
    "length" to "Longest", "length-short" to "Shortest",
    "rate" to "Highest sample rate", "rate-low" to "Lowest sample rate",
    "created" to "Newest", "created-old" to "Oldest", "updated" to "Recently changed",
    "random" to "Shuffled",
)

/** The sorts a list of songs has (order: the list's own, when it has one). */
fun songSorts(withOrder: Boolean, extra: List<String> = emptyList()) =
    (if (withOrder) listOf("order") else emptyList()) + listOf("title", "title-desc", "artist", "artist-desc", "album",
        "added", "added-old", "plays", "plays-least", "played", "played-old", "length", "length-short") + extra + "random"

/** The sorts of a list of groups (albums, genres…): which of them fit is the tab's to say. */
val GROUP_SORTS = listOf("name", "name-desc", "artist", "artist-desc", "added", "added-old", "plays", "plays-least",
    "played", "played-old", "songs", "songs-least", "length", "length-short", "random")

/** Does this sort need your plays (counted, or when last) worked out first? Those take a moment. */
fun sortNeedsPlays(sort: String) = sort.startsWith("plays") || sort.startsWith("played")

/** What a sort may need to know about your songs: how often you played each, when last (seconds; 0: never). */
class SortData(val plays: (Track) -> Int = { it.plays }, val last: (Track) -> Long = { 0L }) {
    companion object { val NONE = SortData() }
}

private fun lead(artist: String) = norm(artist.split(",")[0]).ifEmpty { "~" }   // no artist: last

/** tracks sorted by sort (a key of SORT_NAMES; "order" and unknown ones leave them be). rate: a song's sample rate. */
fun sortTracks(tracks: List<Track>, sort: String, d: SortData = SortData.NONE, rate: (Track) -> Int = { 0 }): List<Track> {
    val byTitle = compareBy<Track>({ norm(it.title) }, { lead(it.artist) })
    return when (sort) {
        "title" -> tracks.sortedWith(byTitle)
        "title-desc" -> tracks.sortedWith(byTitle.reversed())
        "artist" -> tracks.sortedWith(compareBy<Track>({ lead(it.artist) }, { norm(it.album).ifEmpty { "~" } }).then(byTitle))
        "artist-desc" -> tracks.sortedWith(compareByDescending<Track> { lead(it.artist) }.then(compareBy { norm(it.album) }).then(byTitle))
        "album" -> tracks.sortedWith(compareBy<Track>({ norm(it.album).ifEmpty { "~" } }, { lead(it.artist) }).then(byTitle))
        "added" -> tracks.sortedWith(compareByDescending<Track> { it.added }.then(byTitle))
        "added-old" -> tracks.sortedWith(compareBy<Track> { if (it.added > 0) it.added else Double.MAX_VALUE }.then(byTitle))
        "plays", "plays-least" -> {
            val n = tracks.associateWith { d.plays(it) }
            tracks.sortedWith((if (sort == "plays") compareByDescending<Track> { n[it] } else compareBy { n[it] }).then(byTitle))
        }
        "played", "played-old" -> {
            val at = tracks.associateWith { d.last(it) }
            tracks.sortedWith((if (sort == "played") compareByDescending<Track> { at[it] } else compareBy { at[it] }).then(byTitle))
        }
        "length" -> tracks.sortedWith(compareByDescending<Track> { it.duration }.then(byTitle))
        "length-short" -> tracks.sortedWith(compareBy<Track> { if (it.duration > 0) it.duration else Double.MAX_VALUE }.then(byTitle))
        "rate" -> tracks.sortedWith(compareByDescending<Track> { rate(it) }.then(byTitle))
        "rate-low" -> tracks.sortedWith(compareBy<Track> { rate(it) }.then(byTitle))
        "random" -> tracks.shuffled(SHUFFLE_RANDOM)
        else -> tracks
    }
}

/** A group's facts for sorting: its name, whose (an album's artist), its songs; plays/added/when override
 *  what its songs add up to (an artist's plays from your stats, a playlist's dates). */
class GroupFacts(val name: String, val artist: String = "", val songs: List<Track> = emptyList(), val plays: Int? = null,
                 val added: Double? = null, val created: Double = 0.0, val updated: Double = 0.0)

/** items sorted by sort, each one's facts from facts; "order" leaves them be. */
fun <T> sortGroups(items: List<T>, sort: String, d: SortData = SortData.NONE, facts: (T) -> GroupFacts): List<T> {
    if (sort == "order" || sort !in SORT_NAMES) return items
    if (sort == "random") return items.shuffled(SHUFFLE_RANDOM)
    val f = items.associateWith(facts)
    fun n(t: T) = norm(f.getValue(t).name)
    val byName = compareBy<T> { n(it) }
    fun <K : Comparable<K>> desc(key: (GroupFacts) -> K) = compareByDescending<T> { key(f.getValue(it)) }.then(byName)
    fun <K : Comparable<K>> asc(key: (GroupFacts) -> K) = compareBy<T> { key(f.getValue(it)) }.then(byName)
    val added = { g: GroupFacts -> g.added ?: (g.songs.maxOfOrNull { it.added } ?: 0.0) }
    val plays = if (sortNeedsPlays(sort)) items.associateWith { t -> f.getValue(t).let { g -> g.plays ?: g.songs.sumOf { d.plays(it) } } } else emptyMap()
    val last = if (sort.startsWith("played")) items.associateWith { t -> f.getValue(t).songs.maxOfOrNull { d.last(it) } ?: 0L } else emptyMap()
    return when (sort) {
        "name" -> items.sortedWith(byName)
        "name-desc" -> items.sortedWith(byName.reversed())
        "artist" -> items.sortedWith(compareBy<T> { lead(f.getValue(it).artist) }.then(byName))
        "artist-desc" -> items.sortedWith(compareByDescending<T> { lead(f.getValue(it).artist) }.then(byName))
        "added" -> items.sortedWith(desc(added))
        "added-old" -> items.sortedWith(asc { g -> added(g).let { if (it > 0) it else Double.MAX_VALUE } })
        "plays" -> items.sortedWith(compareByDescending<T> { plays[it] }.then(byName))
        "plays-least" -> items.sortedWith(compareBy<T> { plays[it] }.then(byName))
        "played" -> items.sortedWith(compareByDescending<T> { last[it] }.then(byName))
        "played-old" -> items.sortedWith(compareBy<T> { last[it] }.then(byName))
        "songs" -> items.sortedWith(desc { it.songs.size })
        "songs-least" -> items.sortedWith(asc { it.songs.size })
        "length" -> items.sortedWith(desc { g -> g.songs.sumOf { it.duration } })
        "length-short" -> items.sortedWith(asc { g -> g.songs.sumOf { it.duration } })
        "created" -> items.sortedWith(desc { it.created })
        "created-old" -> items.sortedWith(asc { it.created })
        "updated" -> items.sortedWith(desc { it.updated })
        else -> items
    }
}
