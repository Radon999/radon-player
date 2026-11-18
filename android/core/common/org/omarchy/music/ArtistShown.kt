package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What you hid from an artist's page yourself and what you put back on it (kept in the settings, under the artist's
 *  name). The rules are [ArtistShownRules]. */
object ArtistShown {
    /** Goes up at every change, so an artist's page that is open redraws at once: what you hide is gone as you hide it. */
    var version by mutableStateOf(0)

    fun marks(artist: String): Map<String, Char> = ArtistShownRules.read(Prefs.text(ArtistShownRules.prefKey(artist)))

    private fun mark(artist: String, keys: Collection<String>, mark: Char?) {
        Prefs.setText(ArtistShownRules.prefKey(artist), ArtistShownRules.write(ArtistShownRules.marked(marks(artist), keys, mark)))
        version++
        Music.artistsVersion++
    }

    /** Hide songs from the artist's page (they stay in your music and everywhere else), or put them on it. */
    fun songs(artist: String, songs: List<Track>, hide: Boolean) {
        mark(artist, songs.map { ArtistShownRules.songKey(it.title) }, if (hide) ArtistShownRules.HIDE else ArtistShownRules.KEEP)
        Music.toast(if (hide) "Hidden from $artist's page · “Show what's left out” at its end brings ${if (songs.size == 1) "it" else "them"} back"
            else "On $artist's page again")
    }

    /** Hide releases from the artist's page, or put them back on it: all at once, and it says how many. */
    fun albums(artist: String, titles: List<String>, hide: Boolean) {
        if (titles.isEmpty()) return
        mark(artist, titles.map { ArtistShownRules.albumKey(it) }, if (hide) ArtistShownRules.HIDE else ArtistShownRules.KEEP)
        val n = titles.size
        Music.toast(if (hide) "%,d release%s hidden from %s's page · “Show what's left out” at its end brings %s back".format(n, if (n != 1) "s" else "", artist, if (n == 1) "it" else "them")
            else "%,d release%s on %s's page again".format(n, if (n != 1) "s" else "", artist))
    }
}
