package org.omarchy.music

/** Settings › Scanner › Your files › Storage and damaged files. Two things worked out from one pass over your song
 *  files: what takes the space (by kind of file, by artist, by album, and the biggest songs), and which files are
 *  damaged in a way that can be seen without playing them: empty, not a music file at all, or far too small for how
 *  long the song is said to be (cut short while copying or downloading). A file that is whole but has a fault
 *  somewhere inside it can only be found by playing it; this doesn't claim to find those. Nothing is changed or
 *  deleted: it only tells you. */
object FileCheckRules {
    class Song(val title: String, val artist: String, val album: String, val kind: String, val bytes: Long)
    class Summary(val total: Long, val songs: Int, val byKind: List<Pair<String, Long>>, val artists: List<Pair<String, Long>>,
                  val albums: List<Pair<String, Long>>, val biggest: List<Pair<String, Long>>)

    /** What takes the space: each list biggest first, the top [n] of each. */
    fun summary(songs: List<Song>, n: Int = 15): Summary {
        fun top(key: (Song) -> String, name: (List<Song>) -> String) = songs.groupBy(key).values
            .map { name(it) to it.sumOf { s -> s.bytes } }.sortedByDescending { it.second }.take(n)
        fun lead(s: Song) = s.artist.substringBefore(",").trim().ifEmpty { "Unknown artist" }
        return Summary(songs.sumOf { it.bytes }, songs.size,
            top({ it.kind.ifEmpty { "Other" } }) { it[0].kind.ifEmpty { "Other" } },
            top({ statsKey("", it.artist).second }) { lead(it[0]) },   // one artist however the name is written
            top({ norm(it.album) + "|" + statsKey("", it.artist).second }) { if (it[0].album.isBlank()) "(no album) · " + lead(it[0]) else it[0].album + " · " + lead(it[0]) },
            songs.sortedByDescending { it.bytes }.take(n).map { PlaylistSendRules.songName(it.title, it.artist) to it.bytes })
    }

    /** What is wrong with a file that can be told from its first bytes, its size and the song's length (seconds; 0: not
     *  known), or null: nothing seen. Under 2 KB for each second (16 kbit/s) is less than any music file needs. */
    fun damage(head: ByteArray, bytes: Long, seconds: Double): String? = when {
        bytes <= 0L -> "the file is empty"
        MoodWriteRules.kindOf(head).isEmpty() -> "it isn't a music file the app knows (its first bytes aren't any format's)"
        seconds > 20.0 && bytes < seconds * 2000.0 -> "it is far too small for a song of ${fmtTime(seconds)}: probably cut short"
        else -> null
    }

    /** What the check says when it is done. */
    fun said(songs: Int, total: Long, damaged: Int, unread: Int): String =
        "%,d songs take %s. ".format(songs, SupportRules.size(total)) +
            (if (damaged == 0) "No damaged files found" else "%,d damaged file%s found".format(damaged, if (damaged != 1) "s" else "")) +
            (if (unread > 0) " (%,d couldn't be opened to look)".format(unread) else "")
}
