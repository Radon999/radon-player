package org.omarchy.music

import java.io.File

/** The cover the lock screen, the notification, Android Auto and Windows' media controls show for the song playing.
 *  [Music] keeps the name the playback service calls and hands on. */
object NowArt {
    /** The same cover the app shows (the download's, one you picked in the Tagger or Pick a cover, the service's),
     *  else the file's own (FLAC, MP3, M4A, WAV) or its folder's cover.jpg / folder.jpg, saved as a picture in the
     *  cache once per song. */
    fun of(t: Track): String? {
        Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() && File(it).exists() }?.let { return it }
        val name = t.album.ifEmpty { t.title }
        // A cover from the web: its copy on disk once it's been loaded (kept the first time), not downloaded again.
        fun web(u: String): String = if (!u.startsWith("http")) u else CoverCache.local(u)?.path ?: u.also { CoverCache.keepLater(it) }
        Pictures.cover(t.artist, name).takeIf { it.isNotEmpty() }?.let { return web(it) }
        if (t.thumb.isNotEmpty()) return web(sizedThumb(t.thumb, 544))
        if (t.file == null) SongCovers.shown(t.title, t.artist).takeIf { it.isNotEmpty() }?.let { return web(sizedThumb(it, 544)) }
        val old = Pictures.oldCover(t.artist, name).ifEmpty { null }
        return t.file?.let { fileArt(it) } ?: old?.let { web(it) }
    }

    /** The name of a file's cover in the cache: by its whole address (SHA-1), not its hashCode: two songs whose
     *  addresses share a hashCode would share a cover. */
    fun cacheName(file: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(file.toByteArray()).joinToString("") { "%02x".format(it) }.take(24) + ".jpg"

    private val fileArts = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun fileArt(file: String): String? {
        fileArts[file]?.let { return it.ifEmpty { null } }
        val ctx = Music.ctx
        val bytes = runCatching { embeddedPicture(ctx, file) }.getOrNull() ?: FolderCover.bytes(file)
        val out = bytes?.let { b -> runCatching {
            val dir = File(ctx.cacheDir, "now-art").apply { mkdirs() }
            File(dir, cacheName(file)).apply { writeBytes(b) }.path
        }.getOrNull() }
        fileArts[file] = out ?: ""
        return out
    }
}
