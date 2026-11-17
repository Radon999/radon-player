package org.omarchy.music

/** What you hid from an artist's page yourself and what you put back on it (kept in the settings, under the artist's
 *  name). The rules are [ArtistShownRules]. */
object ArtistShown {
    fun marks(artist: String): Map<String, Char> = ArtistShownRules.read(Prefs.text(ArtistShownRules.prefKey(artist)))

    private fun mark(artist: String, keys: Collection<String>, mark: Char?) {
        Prefs.setText(ArtistShownRules.prefKey(artist), ArtistShownRules.write(ArtistShownRules.marked(marks(artist), keys, mark)))
        Music.artistsVersion++
    }

    /** Hide songs from the artist's page (they stay in your music and everywhere else), or put them on it. */
    fun songs(artist: String, songs: List<Track>, hide: Boolean) {
        mark(artist, songs.map { ArtistShownRules.songKey(it.title) }, if (hide) ArtistShownRules.HIDE else ArtistShownRules.KEEP)
        Music.toast(if (hide) "Hidden from $artist's page · “Show what's left out” at its end brings ${if (songs.size == 1) "it" else "them"} back"
            else "On $artist's page again")
    }

    fun album(artist: String, title: String, hide: Boolean) {
        mark(artist, listOf(ArtistShownRules.albumKey(title)), if (hide) ArtistShownRules.HIDE else ArtistShownRules.KEEP)
    }
}
