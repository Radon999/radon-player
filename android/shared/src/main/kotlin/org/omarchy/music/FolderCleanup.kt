package org.omarchy.music

// Tidying after songs are deleted or moved: the folders they were in, left with no music (only a cover, lyrics or
// nothing), go too, up to the artist's folder or the music folder (never the music folder itself). Only the deciding
// is here; listing the disk and removing folders is the caller's (FolderCleanup in the app).

/** Song files, by extension (lower case, no dot). Any of these anywhere in a folder keeps it. */
val CLEANUP_AUDIO_EXTS = setOf("mp3", "m4a", "m4b", "mp4", "aac", "flac", "wav", "aif", "aiff", "aifc", "ogg", "oga", "opus",
    "wma", "alac", "ape", "wv", "dsf", "dff", "mka", "webm", "tta", "mpc", "spx", "3gp", "amr", "caf", "w64")

/** What a folder of music leaves behind (by extension): fine to remove with the folder. */
val CLEANUP_LEFTOVER_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "lrc", "txt", "nfo", "cue", "log",
    "m3u", "m3u8", "sfv", "md5")

/** Files the PC and Mac drop in folders by themselves (whole names, lower case). */
val CLEANUP_LEFTOVER_NAMES = setOf("desktop.ini", "thumbs.db", ".ds_store")

private fun cleanupName(path: String) = path.replace('\\', '/').substringAfterLast('/')

/** Whether a file is a song, by its extension (any case). */
fun isAudioName(path: String): Boolean = cleanupName(path).substringAfterLast('.', "").lowercase() in CLEANUP_AUDIO_EXTS

/** Whether a file is a leftover (a picture, lyrics, a playlist, desktop.ini…). Anything else, known or not, isn't. */
fun isCleanupLeftover(path: String): Boolean {
    val name = cleanupName(path).lowercase()
    if (name in CLEANUP_LEFTOVER_NAMES) return true
    val ext = name.substringAfterLast('.', "")
    return ext.isNotEmpty() && name != ".$ext" && ext in CLEANUP_LEFTOVER_EXTS
}

/** Whether a folder may go: every file in it and below (paths, any depth) is a leftover, none a song or unknown. */
fun noAudioLeft(files: Collection<String>): Boolean = files.all { !isAudioName(it) && isCleanupLeftover(it) }

/** A path compared: forward slashes, no slash at the end. */
private fun cleanupDir(p: String): String = p.replace('\\', '/').trimEnd('/').ifEmpty { if (p.startsWith("/") || p.startsWith("\\")) "/" else "" }

/** Paths are compared ignoring case (Windows doesn't care, and on the phone it only makes us keep more). */
private fun cleanupSame(a: String, b: String) = a.equals(b, ignoreCase = true)
private fun cleanupInside(path: String, folder: String) = folder.isNotEmpty() &&
    (if (folder == "/") path.startsWith("/") && path != "/" else path.startsWith("$folder/", ignoreCase = true))

/**
 * The folders that may be tidied after a song left folder: folder itself, then each one above it, nearest first, up to
 * the artist's folder (…/Artists/<Artist>, included) or the music folder it's in (one of roots, not included),
 * whichever comes first. Empty when folder isn't inside any of roots (we never tidy elsewhere). The list stops before
 * a folder inside one of skip (Folders to leave alone) or holding one of them or another root.
 */
fun cleanupChain(folder: String, roots: Collection<String>, skip: Collection<String> = emptyList()): List<String> {
    val f = cleanupDir(folder)
    if (f.isEmpty()) return emptyList()
    val rs = roots.map(::cleanupDir).filter { it.isNotEmpty() }
    val root = rs.filter { cleanupInside(f, it) }.maxByOrNull { it.length } ?: return emptyList()
    val artist = artistFolder(f)?.let(::cleanupDir)?.takeIf { cleanupSame(it, f) || cleanupInside(f, it) }
    val sk = skip.map(::cleanupDir).filter { it.isNotEmpty() }
    val out = ArrayList<String>()
    var cur = f
    while (cleanupInside(cur, root)) {
        // Folders to leave alone, and other music folders, are never removed, nor anything holding one
        if (sk.any { cleanupSame(cur, it) || cleanupInside(cur, it) || cleanupInside(it, cur) }) break
        if (rs.any { cleanupSame(cur, it) || cleanupInside(it, cur) }) break
        out += cur
        if (artist != null && cleanupSame(cur, artist)) break
        val up = cur.substringBeforeLast('/', "")
        if (up.isEmpty() || up == cur) break
        cur = up
    }
    return out
}

/**
 * The folder to remove from chain (cleanupChain's, nearest first): the highest one with no music left in it and
 * below, going up only while each one has none; null if the nearest still has some. filesUnder lists every file in a
 * folder and below; null when it can't be read (or isn't there), which stops it.
 */
fun cleanupPick(chain: List<String>, filesUnder: (String) -> Collection<String>?): String? {
    var pick: String? = null
    for (d in chain) {
        val files = filesUnder(d) ?: break
        if (!noAudioLeft(files)) break
        pick = d
    }
    return pick
}
