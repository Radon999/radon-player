package org.omarchy.music

import java.io.File

/** Lyrics into the songs' own files, so they stay with them: a FLAC's LYRICS comment, an MP3's
 *  ID3 lyrics frame (USLT). Only the words go in (no annotations, no timings). A file that has
 *  lyrics already is left as it is. Other kinds of files (M4A, OGG…) are left alone. [writeTags] (the
 *  lyrics scanner, the words' mood) also does WAVs and, on the PC, M4As, and timed lyrics. */
object LyricsTags {
    /** The words of what was found: the plain lyrics, else the timed ones without their timings. */
    fun words(g: Lyrics.Got): String? =
        g.plain?.trim()?.takeIf { it.isNotEmpty() }
            ?: g.synced?.let { s -> Lyrics.parseLrc(s).joinToString("\n") { it.second }.trim().takeIf { it.isNotEmpty() } }

    /** Write text as the lyrics of the file at uri, if it has none (blocking). Returns whether it wrote. */
    fun write(ctx: Ctx, uri: String, text: String): Boolean {
        val cr = ctx
        val head = cr.openIn(uri)!!.use { s -> ByteArray(10).also { b -> var n = 0; while (n < 10) { val r = s.read(b, n, 10 - n); if (r < 0) break; n += r } } }
        return when {
            String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" -> flac(ctx, uri)?.let { has -> if (has) false else { Tags.write(ctx, uri, mapOf("lyrics" to text)); true } } ?: false
            String(head, 0, 3, Charsets.ISO_8859_1) == "ID3" || (head[0].toInt() and 0xFF == 0xFF && head[1].toInt() and 0xE0 == 0xE0) -> mp3(ctx, uri, text)
            else -> false
        }
    }

    /** Lyrics (null: leave them as they are) and named tags of ours (MOOD_LYRICS; "" takes one off) written into the
     *  file at uri, replacing what's there (blocking): a FLAC's comments, an MP3's ID3 frames (USLT, TXXX), a WAV's ID3
     *  chunk (the PC), others (M4A…) through the PC's tag library. Returns whether it wrote; throws if the write failed. */
    fun writeTags(ctx: Ctx, uri: String, lyrics: String?, named: Map<String, String>): Boolean {
        if (lyrics == null && named.isEmpty()) return false
        val head = ctx.openIn(uri)!!.use { s -> ByteArray(12).also { b -> var n = 0; while (n < 12) { val r = s.read(b, n, 12 - n); if (r < 0) break; n += r } } }
        val magic = String(head, 0, 4, Charsets.ISO_8859_1)
        return when {
            magic == "fLaC" -> {
                val changes = LinkedHashMap<String, String>()
                if (lyrics != null) changes["lyrics"] = lyrics
                for ((k, v) in named) changes[k.lowercase()] = v
                Tags.write(ctx, uri, changes); true
            }
            magic.startsWith("ID3") || (head[0].toInt() and 0xFF == 0xFF && head[1].toInt() and 0xE0 == 0xE0 && head[1].toInt() and 0x06 != 0) -> {
                val all = ctx.openIn(uri)!!.use { it.readBytes() }
                val out = TagWrite.mp3(all, lyrics, named) ?: return false   // a tag we leave alone (ID3v2.2…)
                replace(ctx, uri, all, out, ".mp3"); true
            }
            magic == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WAVE" ->
                !uri.startsWith("content:") && TagWrite.wav(java.io.File(if (uri.startsWith("file:")) java.net.URI(uri).path else uri), lyrics, named)
            else -> ExtraTags.write(ctx, uri, lyrics, named)
        }
    }

    /** The file's bytes swapped for out, through a copy in the cache: a write that fails half way puts the old ones back. */
    private fun replace(ctx: Ctx, uri: String, old: ByteArray, out: ByteArray, ext: String) {
        val tmp = File.createTempFile("retag", ext, ctx.cacheDir).apply { writeBytes(old) }
        try {
            ctx.openOut(uri)!!.use { it.write(out) }
        } catch (e: Exception) {
            runCatching { ctx.openOut(uri)!!.use { it.write(tmp.readBytes()) } }
            throw e
        } finally {
            tmp.delete()
        }
    }

    /** The lyrics to put in a file: the timed lines (LRC) when there are some, so players that follow along can, else
     *  the plain words. */
    fun forFile(g: Lyrics.Got): String? =
        g.synced?.trim()?.takeIf { Lyrics.parseLrc(it).isNotEmpty() } ?: words(g)

    /** Whether the FLAC has lyrics already (null if unreadable). */
    private fun flac(ctx: Ctx, uri: String): Boolean? = runCatching {
        ctx.openIn(uri)!!.buffered().use { val c = Flac.comments(Flac.readHeader(it))
            !c["LYRICS"].isNullOrEmpty() || !c["UNSYNCEDLYRICS"].isNullOrEmpty() }
    }.getOrNull()

    /** An MP3: lyrics added to its ID3v2 tag (a new one if it has none), unless it has some already. Through
     *  [TagWrite]: only the lyrics frame is added; every other frame (title, artist, album, album artist…) is kept. */
    private fun mp3(ctx: Ctx, uri: String, text: String): Boolean {
        val all = ctx.openIn(uri)!!.use { it.readBytes() }
        if (all.size >= 3 && String(all, 0, 3, Charsets.ISO_8859_1) == "ID3") {
            val (tag, _) = TagWrite.parseId3(all) ?: return false   // a tag we leave alone (ID3v2.2…)
            if (tag.frames.any { it.first == "USLT" || it.first == "SYLT" }) return false   // it has lyrics already
        }
        val out = TagWrite.mp3(all, text, emptyMap()) ?: return false
        replace(ctx, uri, all, out, ".mp3")
        return true
    }

    /** A song's mood (one, or several joined with ";"; "" takes it off) written into its file's MOOD tag, and nothing
     *  else (blocking): a FLAC's MOOD comment, an MP3's or WAV's ID3 mood frame, the tag library's MOOD for the rest
     *  (M4A, Ogg… on the PC). Every other tag stays exactly as it was, so a mood write never changes a song's title,
     *  artist, album or album artist. Returns whether it wrote; throws if the write failed. */
    fun writeMood(ctx: Ctx, uri: String, mood: String): Boolean {
        val head = ctx.openIn(uri)!!.use { s -> ByteArray(12).also { b -> var n = 0; while (n < 12) { val r = s.read(b, n, 12 - n); if (r < 0) break; n += r } } }
        val magic = String(head, 0, 4, Charsets.ISO_8859_1)
        return when {
            magic == "fLaC" -> { Tags.write(ctx, uri, mapOf("mood" to mood)); true }
            magic.startsWith("ID3") && isFlacBehindId3(ctx, uri) -> FileTags.write(ctx, uri, mapOf("mood" to mood))
            magic.startsWith("ID3") || (head[0].toInt() and 0xFF == 0xFF && head[1].toInt() and 0xE0 == 0xE0 && head[1].toInt() and 0x06 != 0) -> {
                val all = ctx.openIn(uri)!!.use { it.readBytes() }
                val out = TagWrite.mp3(all, null, emptyMap(), mood) ?: return false   // a tag we leave alone (ID3v2.2…)
                replace(ctx, uri, all, out, ".mp3"); true
            }
            magic == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WAVE" ->
                !uri.startsWith("content:") && TagWrite.wav(java.io.File(if (uri.startsWith("file:")) java.net.URI(uri).path else uri), null, emptyMap(), mood)
            else -> FileTags.write(ctx, uri, mapOf("mood" to mood))
        }
    }

    /** Whether the file is a FLAC with an ID3 tag stuck in front of it (some taggers do that): its tags are the FLAC's. */
    private fun isFlacBehindId3(ctx: Ctx, uri: String): Boolean = runCatching {
        ctx.openIn(uri)!!.use { s ->
            val h = ByteArray(10); var n = 0
            while (n < 10) { val r = s.read(h, n, 10 - n); if (r < 0) break; n += r }
            if (n < 10) return@use false
            val size = ((h[6].toInt() and 0x7F) shl 21) or ((h[7].toInt() and 0x7F) shl 14) or ((h[8].toInt() and 0x7F) shl 7) or (h[9].toInt() and 0x7F)
            var skip = size.toLong() + (if (h[5].toInt() and 0x10 != 0) 10 else 0)
            while (skip > 0) { val k = s.skip(skip); if (k <= 0) break; skip -= k }
            val m = ByteArray(4); var got = 0
            while (got < 4) { val r = s.read(m, got, 4 - got); if (r < 0) break; got += r }
            got == 4 && String(m, Charsets.ISO_8859_1) == "fLaC"
        }
    }.getOrDefault(false)
}
