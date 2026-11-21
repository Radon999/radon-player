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

    /** A file that plays for far less time than the music it holds: its sound is still in it but a player stops early
     *  (“New Wave”, 21 Nov 2026: the size of a 2:48 song, 10 seconds long in every player). sound: its bytes of sound;
     *  atMost: the most bytes a second of its kind can take (an MP3: 320 kbit/s; a WAV: what its header says), so
     *  sound / atMost is the least it can last. Said only when it plays under half of that and holds over a minute.
     *  seconds 0 (not known): nothing said. */
    fun stopsEarly(sound: Long, atMost: Long, seconds: Double): String? {
        if (seconds <= 0.0 || sound <= 0L || atMost <= 0L) return null
        val least = sound.toDouble() / atMost
        return if (least > 60.0 && seconds < least / 2)
            "it plays for ${fmtTime(seconds)} but holds at least ${fmtTime(least)} of music: it was damaged inside (it needs replacing from a backup, or downloading again)"
        else null
    }

    /** An MP3's bytes of sound from its first bytes and size (what follows its ID3 tag), and a WAV's with the bytes a
     *  second takes from its header (first 32 bytes): (sound, the most a second takes), or null for another kind. */
    fun soundOf(head: ByteArray, bytes: Long): Pair<Long, Long>? = when (MoodWriteRules.kindOf(head)) {
        "MP3" -> {
            val tag = if (head.size >= 10 && String(head, 0, 3, Charsets.ISO_8859_1) == "ID3")
                10L + (((head[6].toInt() and 0x7f) shl 21) or ((head[7].toInt() and 0x7f) shl 14) or ((head[8].toInt() and 0x7f) shl 7) or (head[9].toInt() and 0x7f)) else 0L
            (bytes - tag) to 40_000L   // 320 kbit/s: the most an MP3 has
        }
        "WAV" -> if (head.size >= 32 && String(head, 12, 4, Charsets.ISO_8859_1) == "fmt ") {
            val rate = (head[28].toLong() and 0xff) or ((head[29].toLong() and 0xff) shl 8) or ((head[30].toLong() and 0xff) shl 16) or ((head[31].toLong() and 0xff) shl 24)
            if (rate > 0) bytes to rate else null
        } else null
        else -> null
    }

    /** A song you told the check to leave out, known the same on the phone and the PC however its file is named or
     *  where it is kept: its title and lead artist (tidied, as the stats know a song) and its album. */
    fun leftKey(title: String, artist: String, album: String): String = songPickKey(title, artist) + "|" + norm(albumBase(album))

    /** Where in the song the damage is, in seconds, from what the check said of it ("…about 1:02 in…"); null when it
     *  doesn't say. */
    fun spot(why: String): Double? = Regex("about (\\d+):(\\d\\d) in").find(why)?.let { m -> m.groupValues[1].toInt() * 60.0 + m.groupValues[2].toInt() }

    /** Where listening to a damaged song starts: a few seconds before the damage (the song's start when the damage is
     *  in its first seconds, or isn't placed). */
    fun listenFrom(why: String): Double = spot(why)?.let { if (it > 8.0) it - 6.0 else 0.0 } ?: 0.0

    /** What the check says when it is done. */
    fun said(songs: Int, total: Long, damaged: Int, unread: Int): String =
        "%,d songs take %s. ".format(songs, SupportRules.size(total)) +
            (if (damaged == 0) "No damaged files found" else "%,d damaged file%s found".format(damaged, if (damaged != 1) "s" else "")) +
            (if (unread > 0) " (%,d couldn't be opened to look)".format(unread) else "")
}
