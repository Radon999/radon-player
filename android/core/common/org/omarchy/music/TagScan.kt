package org.omarchy.music

/** A FLAC's main tags and its cover, read straight from the file (the phone reads its downloads' tags this way). What is
 *  left of the old tag scanner (Settings › Tagger › Scan…), whose screen went with 3.0. */
object TagScan {
    /** The tags read: the album's and the song's. */
    val FIELDS = listOf("album", "albumartist", "date", "genre", "title", "artist", "tracknumber")

    /** A FLAC's tags, and its cover (null if none). */
    fun read(uri: String): Pair<Map<String, String>, ByteArray?> = runCatching {
        Music.ctx.openIn(uri)!!.buffered().use { s ->
            val blocks = Flac.readHeader(s)
            val c = Flac.comments(blocks)
            FIELDS.associateWith { k ->
                val v = c[k.uppercase()] ?: emptyList()
                (if (k == "artist") v.joinToString(", ") else v.firstOrNull() ?: "").trim()
            }.toMutableMap().also { it["tracknumber"] = it["tracknumber"].orEmpty().substringBefore('/').trim() } to Flac.picture(blocks)
        }
    }.getOrDefault(FIELDS.associateWith { "" } to null)
}
