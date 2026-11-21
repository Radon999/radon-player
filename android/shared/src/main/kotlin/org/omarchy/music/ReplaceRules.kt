package org.omarchy.music

import kotlin.math.abs
import kotlin.math.roundToInt

/** A song about to be put in another's place (a damaged file's new copy): what is different about it, so a wrong song,
 *  another version or another album shows before anything is downloaded. */
object ReplaceRules {
    /** In what the new song differs from yours, in words; empty: the same song, album and length (within 3 seconds). */
    fun differs(title: String, artist: String, album: String, seconds: Double,
                newTitle: String, newArtist: String, newAlbum: String, newSeconds: Double): List<String> {
        val out = ArrayList<String>()
        val a = statsKey(title, artist); val b = statsKey(newTitle, newArtist)
        if (a.first != b.first) out += "another title"
        if (a.second != b.second) out += "another artist"
        if (album.isNotBlank() && norm(albumBase(album)) != norm(albumBase(newAlbum))) out += "another album"
        if (seconds > 0 && newSeconds > 0 && abs(seconds - newSeconds) > 3.0) {
            val d = (newSeconds - seconds).roundToInt()
            out += "${abs(d)} s ${if (d > 0) "longer" else "shorter"}"
        }
        return out
    }
}
