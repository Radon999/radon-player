package org.omarchy.music

/** A song's cover from its folder, for a song of yours with no picture inside its file: the picture beside it that
 *  [FolderCoverRules.pick] says is the cover (cover.jpg, folder.jpg…, or the folder's only picture). The same on the
 *  phone and the PC. On the phone the folder can only be read with All files access (Settings › Tagger asks for it). */
object FolderCover {
    /** (blocking) The picture's bytes, or null: no such picture, or the folder can't be read. */
    fun bytes(song: String): ByteArray? = runCatching {
        if (song.startsWith("http")) return@runCatching null
        val dir = songFolder(song) ?: return@runCatching null
        val files = dir.listFiles()?.filter { it.isFile } ?: return@runCatching null
        val name = FolderCoverRules.pick(files.map { it.name }) ?: return@runCatching null
        files.first { it.name == name }.readBytes().takeIf { it.isNotEmpty() }
    }.getOrNull()
}
