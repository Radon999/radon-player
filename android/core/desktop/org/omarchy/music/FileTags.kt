package org.omarchy.music

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/** Your songs' files, for the tools that fix them (Fix albums and artists, Live in song names, Empty folders): their
 *  tags read and written (any format, jaudiotagger: as mutagen on the Python app), moved, and empty folders found. */
object FileTags {
    /** On the PC every file of yours can be fixed, moved and tidied. */
    const val canMove = true
    const val canListFolders = true

    private val KEYS = mapOf("title" to FieldKey.TITLE, "artist" to FieldKey.ARTIST, "albumartist" to FieldKey.ALBUM_ARTIST,
        "album" to FieldKey.ALBUM, "tracknumber" to FieldKey.TRACK, "date" to FieldKey.YEAR, "genre" to FieldKey.GENRE)

    private fun file(uri: String) = File(if (uri.startsWith("file:")) java.net.URI(uri).path else uri)

    fun canWrite(uri: String) = file(uri).let { it.isFile && it.canWrite() }

    /** The file's tags (title, artist, albumartist, album, tracknumber, date), or null if unreadable. */
    fun read(ctx: Ctx, uri: String): Map<String, String>? = runCatching {
        val tag = AudioFileIO.read(file(uri)).tag ?: return@runCatching emptyMap()
        KEYS.mapValues { (_, k) -> runCatching { tag.getAll(k).filter { it.isNotBlank() }.joinToString(", ") }.getOrDefault("") }
    }.getOrNull()

    /** Write changes ("" clears a field); true if it worked. */
    fun write(ctx: Ctx, uri: String, changes: Map<String, String>): Boolean = runCatching {
        val af = AudioFileIO.read(file(uri))
        val tag = af.tagOrCreateAndSetDefault
        for ((k, v) in changes) {
            val key = KEYS[k] ?: continue
            if (v.isEmpty()) tag.deleteField(key) else tag.setField(key, v)
        }
        af.commit(); true
    }.getOrDefault(false)

    fun cover(ctx: Ctx, uri: String): ByteArray? = embeddedPicture(ctx, uri)

    fun hasCover(ctx: Ctx, uri: String) = cover(ctx, uri) != null

    fun writeCover(ctx: Ctx, uri: String, jpeg: ByteArray): Boolean = runCatching {
        val af = AudioFileIO.read(file(uri))
        val tag = af.tagOrCreateAndSetDefault
        val art = org.jaudiotagger.tag.images.ArtworkFactory.getNew().apply { binaryData = jpeg; mimeType = "image/jpeg"; pictureType = 3 }
        tag.deleteArtworkField(); tag.setField(art)
        af.commit(); true
    }.getOrDefault(false)

    /** Move a song to dest (its folders made); the new path, or null. A lyrics file beside it goes with it. */
    fun move(ctx: Ctx, src: String, dest: String): String? = runCatching {
        val from = file(src); val to = File(dest)
        to.parentFile?.mkdirs()
        if (to.exists()) error("${to.name} is there already")
        java.nio.file.Files.move(from.toPath(), to.toPath())
        for (ext in listOf("lrc", "txt")) File(from.parentFile, from.nameWithoutExtension + ".$ext").takeIf { it.isFile }
            ?.let { l -> runCatching { java.nio.file.Files.move(l.toPath(), File(to.parentFile, to.nameWithoutExtension + ".$ext").toPath()) } }
        to.path
    }.getOrNull()

    fun exists(path: String) = File(path).exists()
    fun subfolders(folder: String): List<String> = File(folder).listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    /** Every file under the roots with its size, and every folder (for Empty folders). */
    fun listTree(roots: List<String>): Pair<Map<String, Long>, List<String>> {
        val files = LinkedHashMap<String, Long>(); val dirs = ArrayList<String>()
        for (r in roots) File(r).walkTopDown().forEach { f ->
            val p = f.path.replace('\\', '/')
            if (f.isDirectory) dirs += p else files[p] = f.length()
        }
        return files to dirs
    }

    /** A folder and what's left in it, into the Recycle Bin (or deleted where there's none). */
    fun removeFolder(path: String): Boolean = runCatching {
        val f = File(path)
        val d = java.awt.Desktop.getDesktop()
        if (java.awt.Desktop.isDesktopSupported() && d.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) d.moveToTrash(f) else f.deleteRecursively()
    }.getOrDefault(false)
}
