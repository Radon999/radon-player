package org.omarchy.music

import java.io.File

/** Editing songs' tags by hand (Edit tags…), the desktop's TAG_FIELDS. Only the FLACs the app
 *  downloaded can be written here: they're the app's own in Music/. Songs of other apps'
 *  (your own files) would need Android to ask you for each one, so they're left alone. */
object Tags {
    val FIELDS = listOf("title" to "Title", "artist" to "Artist", "album" to "Album", "albumartist" to "Album artist",
        "genre" to "Genre", "date" to "Year", "tracknumber" to "Track")

    /** Whether the app can change the file: on the phone the FLACs it downloaded (content:// addresses it made), on the
     *  PC any file of yours it may write, as the Python app. */
    fun writable(file: String) = file.startsWith("content://") || FileTags.canWrite(file)

    /** The FLAC of the song's that can be edited, or null: a download of the app's, or (on the PC) a FLAC of your own. */
    fun editable(t: Track): String? {
        Music.lib.items[t.id]?.let { e -> return e.file.takeIf { e.format == "flac" && writable(it) } }
        return t.file?.takeIf { t.isFile && it.substringAfterLast('.').equals("flac", true) && FileTags.canWrite(it) }
    }

    /** What each field says in the files' comments: null where they disagree. */
    internal fun common(all: List<Map<String, List<String>>>): Map<String, String?> = FIELDS.associate { (k, _) ->
        val each = all.map { c -> (c[k.uppercase()] ?: emptyList()).let { if (k == "artist") it.joinToString(", ") else it.firstOrNull() ?: "" } }
        k to each.distinct().singleOrNull()
    }

    /** The comments to write for changes {field: value}: "" takes one off, artists are one comment each. */
    internal fun comments(changes: Map<String, String>): Map<String, List<String>> = changes.entries.associate { (k, v) ->
        k.uppercase() to when {
            v.isBlank() -> emptyList()
            k == "artist" -> v.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> listOf(v.trim())
        }
    }

    fun read(ctx: Ctx, uris: List<String>): Map<String, String?> = common(uris.map { u ->
        runCatching { ctx.openIn(u)!!.buffered().use { Flac.comments(Flac.readHeader(it)) } }
            .getOrDefault(emptyMap())
    })

    /** Write changes into the FLAC at uri (blocking); returns its comments as they are now. */
    fun write(ctx: Ctx, uri: String, changes: Map<String, String>): Map<String, List<String>> {
        val cr = ctx
        val tmp = File.createTempFile("tags", ".flac", ctx.cacheDir)
        try {
            cr.openIn(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            cr.openOut(uri)!!.buffered().use { Flac.retag(tmp, it, comments(changes), null, keepOther = true) }
        } finally {
            tmp.delete()
        }
        return cr.openIn(uri)!!.buffered().use { Flac.comments(Flac.readHeader(it)) }
    }
}

/** A new album cover (Cover…): written into the album's downloaded FLACs, and shown for every
 *  song of the album, the phone's own and streamed ones too (those files are other apps', so
 *  their own covers stay as they are). */
object Covers {
    /** Which album a song is from: its lead artist and album, tidied; a song without an album is one of its own. */
    internal fun albumKey(t: Track): Pair<String, String> =
        norm(t.artist.split(",")[0]) to norm(bareTitle(t.album)).ifEmpty { "#" + t.id }

    internal fun isJpeg(b: ByteArray) = b.size > 3 && b[0] == 0xff.toByte() && b[1] == 0xd8.toByte() && b[2] == 0xff.toByte()

    /** Picture bytes as a JPEG (FLAC covers here are JPEGs). */
    fun jpeg(b: ByteArray): ByteArray {
        if (isJpeg(b)) return b
        return toJpeg(b) ?: error("that isn't a picture")
    }

    /** Put jpeg in the FLAC at uri (one the app made) as its cover, keeping its tags. */
    fun write(ctx: Ctx, uri: String, jpeg: ByteArray) {
        val cr = ctx
        val tmp = File.createTempFile("cover", ".flac", ctx.cacheDir)
        try {
            cr.openIn(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            cr.openOut(uri)!!.buffered().use { Flac.retag(tmp, it, emptyMap(), jpeg, keepOther = true) }
        } finally {
            tmp.delete()
        }
    }
}
