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

    /** The folder a song's file is in, as a full path ("/storage/emulated/0/Music/…"), asked of Android before the
     *  file goes; null if unknown. */
    fun folderOf(ctx: Ctx, uri: String): String? = runCatching {
        if (uri.startsWith("/")) return@runCatching java.io.File(uri).parent
        if (!uri.startsWith("content:")) return@runCatching null
        val col = android.provider.MediaStore.MediaColumns.RELATIVE_PATH
        ctx.contentResolver.query(android.net.Uri.parse(uri), arrayOf(col), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
        }?.let { org.omarchy.music.ui.fullFolderPath(it.trimEnd('/')) }
    }.getOrNull()

    /** Every file in a folder and below (paths inside it), or null if it isn't there or can't be read. */
    fun filesUnder(folder: String): List<String>? = runCatching {
        val top = java.io.File(folder)
        if (!top.isDirectory) return null
        val out = ArrayList<String>()
        fun walk(d: java.io.File, rel: String): Boolean {
            val kids = d.listFiles() ?: return false
            for (k in kids) {
                val p = if (rel.isEmpty()) k.name else "$rel/${k.name}"
                if (k.isDirectory) { if (!walk(k, p)) return false } else out += p
            }
            return true
        }
        if (walk(top, "")) out else null
    }.getOrNull()

    /** A folder with nothing left in it, deleted (the phone has no Recycle Bin for folders): only empty folders go,
     *  deepest first, so no file is ever deleted here, and Android refuses what the app may not remove. True if it went. */
    fun cleanFolder(path: String): Boolean = runCatching {
        fun rm(d: java.io.File): Boolean {
            d.listFiles()?.forEach { if (it.isDirectory) rm(it) }
            return d.delete()   // fails while anything is left in it
        }
        val top = java.io.File(path)
        top.isDirectory && rm(top)
    }.getOrDefault(false)
}
