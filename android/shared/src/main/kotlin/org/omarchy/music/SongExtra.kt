package org.omarchy.music

import kotlinx.serialization.Serializable

/** What the read of your music keeps of a song file besides its title, artist and album: its album's artist (the
 *  tag that makes a compilation one album), and its number and disc on the album. */
@Serializable
data class SongExtra(val albumArtist: String = "", val track: Int = 0, val disc: Int = 0)

/** Pure: how those are read and used. */
object SongExtraRules {
    /** "3", "03/12", " 3 " -> 3; nothing readable -> 0. */
    fun number(text: String?): Int = text?.trim()?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it in 1..9998 } ?: 0

    /** Android's one number for both (disc × 1000 + track): (track, disc). */
    fun fromAndroid(n: Int): Pair<Int, Int> = if (n <= 0) 0 to 0 else (n % 1000) to (n / 1000)

    /** Whose album a song is on: its album's artist when its file names one, else its own lead artist. An album whose
     *  songs are by different artists (a compilation, a soundtrack) is one album under its album artist. */
    fun albumOwner(albumArtist: String?, artist: String): String =
        albumArtist?.trim()?.takeIf { it.isNotEmpty() } ?: artist.split(",")[0].trim()
}
