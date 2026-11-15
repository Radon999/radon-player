package org.omarchy.music

/** A song's cover from its folder, for a song of yours with no picture inside its file: the picture beside it that
 *  [FolderCoverRules.pick] says is the cover (cover.jpg, folder.jpg…, or the folder's only picture). The same on the
 *  phone and the PC. On the phone the folder can only be read with All files access (Settings › Tagger asks for it). */
object FolderCover {
    /** (blocking) The picture's bytes, or null: no such picture, or the folder can't be read. album: the song's
     *  release, for a folder that holds several (each with its own "cover - <album>.jpg"). */
    fun bytes(song: String, album: String = ""): ByteArray? = runCatching {
        if (song.startsWith("http")) return@runCatching null
        file(songFolder(song) ?: return@runCatching null, album)?.readBytes()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** The cover picture in dir (for album, if given), or null. */
    fun file(dir: java.io.File, album: String = ""): java.io.File? {
        val files = dir.listFiles()?.filter { it.isFile } ?: return null
        val name = FolderCoverRules.pick(files.map { it.name }, album) ?: return null
        return files.firstOrNull { it.name == name }
    }
}
