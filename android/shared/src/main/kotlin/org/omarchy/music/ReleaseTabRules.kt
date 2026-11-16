package org.omarchy.music

/** The rules of which tab a release of yours is in (Albums, EPs or Singles), and how your genres are listed. */
object ReleaseTabRules {
    /** A song's key for Tagger › Tab: its file's address (else its id), written one way. */
    fun key(address: String): String = address.replace('\\', '/').lowercase()

    /** The tab a release was put in by hand (Tagger › Tab): only when more than half of its songs were put somewhere,
     *  and then where most of those are; else null (its folder and tags decide). picks: the tab of each song that
     *  has one; songs: how many songs the release has. */
    fun byHand(picks: List<String>, songs: Int): String? = picks.takeIf { it.size * 2 > songs }?.let(::most)

    /** The one most often among kinds, or null of none. */
    fun most(kinds: List<String>): String? = kinds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** What a release's album tag says it is: "ep", "single" (also when it is known to be one), or null (says nothing). */
    fun ofTitle(title: String, single: Boolean): String? =
        if (isEp(title)) "ep" else if (single || isSingleRelease(title)) "single" else null

    /** What one song's album tag says: as [ofTitle]; and a song with no album at all, in a plainly named folder, is a single. */
    fun ofSong(album: String, folder: String?): String? = when {
        isEp(album) -> "ep"
        isSingleRelease(album) -> "single"
        album.isBlank() && folder == null -> "single"
        else -> null
    }

    /** What Tagger › Tab says once it has moved songs (null: back to the usual way). */
    fun movedText(kind: String?): String =
        if (kind == null) "Back where their tags put them" else "Moved to ${mapOf("album" to "Albums", "ep" to "EPs", "single" to "Singles")[kind]}"

    const val UNKNOWN_GENRE = "Unknown genre"

    /** Genres in the order they are listed: the most songs first, Unknown genre last. */
    fun <T> genreOrder(groups: List<Pair<String, List<T>>>): List<Pair<String, List<T>>> =
        groups.sortedWith(compareBy<Pair<String, List<T>>>({ it.first == UNKNOWN_GENRE }, { -it.second.size }))
}
