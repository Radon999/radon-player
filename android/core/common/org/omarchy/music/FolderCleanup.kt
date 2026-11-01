package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * After songs are deleted or moved (Delete, Remove download, Duplicates, Replace, Upgrade, Fix albums moves, Empty
 * folders), the folders they were in go too once no music is left in them (only covers, lyrics and the like), up to
 * the artist's folder or your music folder, never your music folder itself nor Folders to leave alone. The deciding
 * is in shared FolderCleanup.kt (cleanupChain, cleanupPick).
 * The PC puts them in the Recycle Bin. The phone removes only folders left with nothing in them, in its download
 * folder (Android doesn't let the app remove other apps' folders or files).
 */
object FolderCleanup {
    private val pending = LinkedHashSet<String>()
    private var scheduled = false

    /** Tidy these folders (where songs were) soon, off the main thread. Several calls close together are done at once. */
    fun after(folders: Collection<String?>) {
        val fs = folders.filterNotNull().filter { it.isNotBlank() }
        if (fs.isEmpty()) return
        synchronized(pending) {
            pending += fs
            if (scheduled) return
            scheduled = true
        }
        Music.scope.launch(Dispatchers.IO) {
            delay(1500)   // the rest of a batch of deletes comes in meanwhile
            // A download may be making its folder right now: wait for downloads to finish (up to half an hour)
            var waited = 0
            while (runCatching { Music.dl.busyNow() }.getOrDefault(false) && waited < 1800) { delay(5000); waited += 5 }
            val todo = synchronized(pending) { scheduled = false; pending.toList().also { pending.clear() } }
            val n = runCatching { tidy(todo) }.onFailure { Crashes.caught(it, "Tidying empty folders") }.getOrDefault(0)
            if (n > 0) Music.toast(if (FileTags.canListFolders) "Moved $n empty folder${if (n != 1) "s" else ""} to the Recycle Bin"
                else "Removed $n empty folder${if (n != 1) "s" else ""}")
        }
    }

    /** Tidy the folders the songs were in (call before their files go, or right after on the PC). */
    fun afterSongs(tracks: Collection<Track>) = after(tracks.mapNotNull { folderOfSong(it) })

    /** A song's folder as a full path: its file's folder, or for one of the phone's (content://) the folder it was
     *  last seen in. Null if unknown. */
    private fun folderOfSong(t: Track): String? = runCatching {
        val f = t.file ?: Music.lib.items[t.id]?.file ?: return null
        if (f.startsWith("content:")) {
            FileTags.folderOf(Music.ctx, f) ?: Music.folderOf(t).takeIf { it.isNotBlank() }
                ?.let { org.omarchy.music.ui.fullFolderPath(it.trimEnd('/')) }
        } else FileTags.folderOf(Music.ctx, f)
    }.getOrNull()?.replace('\\', '/')

    /** The folders tidying stays inside of (each one kept itself): the PC's music folders and download folder; on
     *  the phone, only the download folders (the app's own). */
    private fun roots(): List<String> {
        val dl = listOf(Music.downloadFolder(), LocalFiles.DOWNLOADS)
        val all = if (FileTags.canListFolders) Music.folders + dl + "Music/" else dl
        return all.filter { it.isNotBlank() }.map { org.omarchy.music.ui.fullFolderPath(it.trimEnd('/')).replace('\\', '/') }.distinct()
    }

    /** (blocking) How many folders went. */
    private fun tidy(folders: List<String>): Int {
        val roots = roots()
        val skip = MusicTools.skip.map { org.omarchy.music.ui.fullFolderPath(it.trimEnd('/')) }
        var n = 0
        // Deepest first, so a folder emptied by one inside it is seen as it is now
        for (f in folders.map { it.replace('\\', '/').trimEnd('/') }.distinct().sortedByDescending { it.count { c -> c == '/' } }) {
            val pick = cleanupPick(cleanupChain(f, roots, skip)) { FileTags.filesUnder(it) } ?: continue
            if (FileTags.cleanFolder(pick)) n++
        }
        return n
    }
}
