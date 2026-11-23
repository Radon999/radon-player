package org.omarchy.music

/** Add to playlist…: which of your playlists it offers. Not the ones the app makes and fills itself, where an added
 *  song would make no sense or be wiped: the discovery pairs (an artist's songs you don't have, and the ones you
 *  found from them), artist playlists (Create artist playlist: every song of one artist, made again from your music),
 *  and the Weekly mix (its songs replaced each week). */
object AddToRules {
    /** name: the playlist's; discovery: it's one of a discovery pair; artists: its songs' artists. */
    fun offered(name: String, discovery: Boolean, artists: List<String>): Boolean {
        if (discovery) return false
        if (name.trim().equals(WeeklyMixRules.NAME, ignoreCase = true)) return false
        return !artistPlaylist(name, artists)
    }

    /** An artist playlist: named after one artist, and (nearly) every song in it theirs. A playlist of yours that
     *  happens to share an artist's name but holds other songs too is a playlist like any other. */
    fun artistPlaylist(name: String, artists: List<String>): Boolean {
        if (artists.isEmpty()) return false
        val who = statsKey("", name).second
        if (who.isEmpty()) return false
        val theirs = artists.count { a -> statsKey("", a).second == who || a.split(',', '&').any { statsKey("", it).second == who } }
        return theirs >= artists.size * 0.9
    }
}
