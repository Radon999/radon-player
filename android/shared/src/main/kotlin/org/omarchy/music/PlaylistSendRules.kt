package org.omarchy.music

/** Send a playlist to your other device: a copy of it made there once, when you ask. It is not syncing: the two
 *  playlists go their own ways afterwards. The other device adds the songs it can play (the ones that stream, and its
 *  own copy of each of your files); a song that is only a file on the sending device, with no copy there, is left
 *  out, and named, so you know what is missing. */
object PlaylistSendRules {
    /** A song as the report names it. */
    fun songName(title: String, artist: String): String = if (artist.isBlank()) title else "$title – $artist"

    private fun songs(n: Int) = "%,d song%s".format(n, if (n != 1) "s" else "")

    /** What is said when a playlist has gone across. sent: said on the device it was sent from (false: on the one it
     *  came to). other: the other device, as it is called ("the PC"). here: the device the songs were added on. */
    fun said(sent: Boolean, name: String, other: String, here: String, added: Int, had: Int, missing: List<String>): String {
        val head = if (sent) "“$name” sent to $other" else "“$name” came from $other"
        val what = when {
            added == 0 && had > 0 -> "all ${songs(had)} were in it already"
            added == 0 -> "no songs added"
            else -> songs(added) + " added" + if (had > 0) ", %,d in it already".format(had) else ""
        }
        if (missing.isEmpty()) return "$head: $what"
        val few = missing.take(3).joinToString("; ") + if (missing.size > 3) "; and %,d more".format(missing.size - 3) else ""
        return "$head: $what. ${songs(missing.size)} left out, not on $here: $few"
    }

    /** The whole list of what was left out, for Settings (every one, a line each). */
    fun missingList(missing: List<String>): String = missing.joinToString("\n")
}
