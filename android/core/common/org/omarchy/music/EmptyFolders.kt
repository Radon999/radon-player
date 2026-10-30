package org.omarchy.music

// Empty folders (the desktop's Settings › Empty folders and empty_folders): the folders in your music folders with no
// songs left in them (only pictures, lyrics and the like, or nothing), to delete. Only the deciding is here, over a
// listing of the folders the caller reads from the disk; deleting them is the caller's.

/** The extensions of songs (the desktop's AUDIO_EXTS). The rule for empty folders doesn't need them: anything that
 *  isn't a leftover keeps a folder, a song or not. */
val AUDIO_EXTS = setOf(".flac", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".aif", ".aiff", ".wv", ".ape", ".alac",
    ".dsf", ".dff", ".tta", ".mka", ".webm", ".wma", ".mpc")

/** What a folder of music leaves behind: a folder with only these in it has no music. */
val LEFTOVER_EXTS = setOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".lrc", ".txt", ".nfo", ".m3u", ".m3u8", ".cue",
    ".log", ".sfv", ".md5", ".ini", ".db", ".ds_store")

/** A folder with no music left in it: its path, its files' bytes, and its files (as paths inside it). */
data class EmptyFolder(val folder: String, val bytes: Long, val files: List<String>)

/** Whether a file is something a folder of music leaves behind (a picture, lyrics, a playlist…), by its extension.
 *  ".DS_Store" isn't one: it has no extension, as Python's pathlib sees it. */
fun isLeftover(name: String): Boolean = fixupSuffix(name).lowercase() in LEFTOVER_EXTS

/** Whether a folder still has music in it, from the files in it and below (the desktop checks again before deleting):
 *  anything that isn't a leftover. */
fun hasMusic(files: Collection<String>): Boolean = files.any { !isLeftover(it) }

/**
 * Folders in roots with no music left in them (only pictures, lyrics and the like, or nothing), deepest first. A
 * folder holding anything else isn't empty, nor one with a folder in it that isn't. The roots themselves are never
 * in it, and a folder inside one that's going too is shown once, with its parent. files: every file under the roots,
 * {path: bytes}; dirs: the folders (empty ones too; folders that hold files needn't be listed). Paths with "/".
 */
fun emptyFolders(roots: List<String>, files: Map<String, Long>, dirs: Collection<String> = emptyList()): List<EmptyFolder> {
    val folderSet = LinkedHashSet<String>()
    for (d in dirs) folderSet += fixupNormPath(d)
    val fileSizes = LinkedHashMap<String, Long>()
    for ((p, n) in files) fileSizes[fixupNormPath(p)] = n
    val out = ArrayList<EmptyFolder>()
    for (root0 in roots) {
        val root = fixupNormPath(root0)
        val prefix = root.trimEnd('/') + "/"
        // The folder tree under root: each folder's folders and files.
        val subdirs = HashMap<String, MutableSet<String>>()
        val names = HashMap<String, MutableList<String>>()
        fun addDir(d: String) {
            var cur = d
            while (cur != root && cur.startsWith(prefix)) {
                val parent = cur.substringBeforeLast('/').ifEmpty { "/" }
                if (subdirs.getOrPut(parent) { sortedSetOf() }.add(cur.substringAfterLast('/')).not()) break
                cur = parent
            }
        }
        for (d in folderSet) if (d.startsWith(prefix)) addDir(d)
        for (f in fileSizes.keys) if (f.startsWith(prefix)) {
            val parent = f.substringBeforeLast('/').ifEmpty { "/" }
            names.getOrPut(parent) { ArrayList() } += f.substringAfterLast('/')
            addDir(parent)
        }
        if (root !in folderSet && subdirs[root] == null && names[root] == null) continue   // not a folder
        fun child(d: String, n: String) = if (d.endsWith("/")) d + n else "$d/$n"

        val keep = HashMap<String, Boolean>()   // folder -> has something that isn't a leftover (in it or below)
        // Every file in a folder and below, as paths inside it, the folder's own first.
        fun walkFiles(d: String, rel: String, acc: MutableList<String>) {
            for (n in (names[d] ?: emptyList<String>()).sorted()) acc += if (rel.isEmpty()) n else "$rel/$n"
            for (s in subdirs[d] ?: emptySet<String>()) walkFiles(child(d, s), if (rel.isEmpty()) s else "$rel/$s", acc)
        }
        fun walk(d: String) {   // deepest first, as os.walk(topdown=False)
            val ds = subdirs[d] ?: emptySet<String>()
            for (s in ds) walk(child(d, s))
            val real = (names[d] ?: emptyList<String>()).any { !isLeftover(it) } || ds.any { keep[child(d, it)] ?: true }
            keep[d] = real
            if (!real && d != root) {
                val inside = ArrayList<String>()
                walkFiles(d, "", inside)
                val size = inside.sumOf { fileSizes[child(d, it)] ?: 0L }
                out += EmptyFolder(d, size, inside)
            }
        }
        walk(root)
    }
    // A folder inside one that's going too is shown once, with its parent.
    val gone = out.map { it.folder }.toSet()
    return out.filter { it.folder.substringBeforeLast('/').ifEmpty { "/" } !in gone }
}

/** emptyFolders' folders with those in the folders left alone (Fix albums and artists' skip) taken out. */
fun emptyFoldersOffered(found: List<EmptyFolder>, skip: Collection<String>): List<EmptyFolder> =
    found.filter { !inFolders(it.folder, skip) }
