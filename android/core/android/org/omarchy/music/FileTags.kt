package org.omarchy.music

/** Your songs' files, for the tools that fix them: on the phone, the FLACs the app downloaded (Android lets an app
 *  change only its own files without asking for each); moving files and folders is the PC's. */
object FileTags {
    const val canMove = false
    const val canListFolders = false

    fun canWrite(uri: String) = Music.lib.items.values.any { it.file == uri && it.format == "flac" }

    fun read(ctx: Ctx, uri: String): Map<String, String>? = runCatching {
        val tags = TagScan.read(uri).first
        listOf("title", "artist", "albumartist", "album", "tracknumber", "date", "genre").associateWith { tags[it] ?: "" }
    }.getOrNull()

    fun write(ctx: Ctx, uri: String, changes: Map<String, String>): Boolean = runCatching { Tags.write(ctx, uri, changes); true }.getOrDefault(false)

    fun cover(ctx: Ctx, uri: String): ByteArray? = runCatching { TagScan.read(uri).second }.getOrNull()
    fun hasCover(ctx: Ctx, uri: String) = cover(ctx, uri) != null
    fun writeCover(ctx: Ctx, uri: String, jpeg: ByteArray): Boolean = runCatching { Covers.write(ctx, uri, jpeg); true }.getOrDefault(false)

    fun move(ctx: Ctx, src: String, dest: String): String? = null
    fun exists(path: String) = false
    fun subfolders(folder: String): List<String> = emptyList()
    fun listTree(roots: List<String>): Pair<Map<String, Long>, List<String>> = emptyMap<String, Long>() to emptyList()
    fun removeFolder(path: String) = false
}
