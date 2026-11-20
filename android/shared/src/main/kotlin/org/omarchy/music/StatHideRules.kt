package org.omarchy.music

/** Hide from stats: an entry (a song, an album, an artist) you don't want to see in the stats lists (Most played and
 *  its times, Repeats, Skips, Unplayed and neglected). It is only not shown: its plays are still counted, and
 *  Settings brings it back. Each is known by a key that is the same however the name is written (K.Flay = KFlay). */
object StatHideRules {
    fun song(title: String, artist: String): String = "s|" + songPickKey(title, artist)
    fun artist(name: String): String = "a|" + artistKey(name)
    fun album(title: String, artist: String): String = "b|" + norm(cleanTitle(title)) + "|" + artistKey(artist)

    /** What Settings calls an entry of each kind. */
    fun kind(key: String): String = when (key.take(2)) { "s|" -> "Song"; "a|" -> "Artist"; "b|" -> "Album"; else -> "" }

    /** The list's heading in Settings. */
    fun count(n: Int): String = if (n == 0) "Nothing is hidden" else "%,d hidden".format(n)
}
