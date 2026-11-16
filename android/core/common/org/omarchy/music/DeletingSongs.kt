package org.omarchy.music

/** Deleting songs: the app's own downloads (deleted at once), and your own files (Android, or the PC's Recycle Bin,
 *  does it once you say yes; then they go out of your music, the queue and your playlists). The song playing is
 *  never deleted. [Music] keeps the names the screens call and hands on. */
object DeletingSongs {
    private fun toast(text: String) = Music.toast(text)

    /** A file of yours the app may ask to have deleted: its address, or null (not a file, one of the app's own
     *  downloads, or one that can't be changed). On the PC: a file of yours it may change (it goes to the Recycle Bin). */
    fun ownFile(t: Track): String? =
        t.file?.takeIf { t.isFile && !Music.lib.has(t.id) &&
            ((it.startsWith("content://media/") && sdkAtLeast(30)) || (!it.startsWith("content:") && FileTags.canWrite(it))) }

    fun deletable(t: Track) = Music.lib.has(t.id) || ownFile(t) != null

    /** Of tracks, the downloads to delete (their ids): the ones that are downloads (has), all but the song playing;
     *  and whether the playing one was among them (then it is said, and left). */
    fun downloadsToDelete(tracks: List<Track>, has: (String) -> Boolean, playing: String?): Pair<List<String>, Boolean> =
        tracks.filter { has(it.id) && it.id != playing }.map { it.id } to tracks.any { it.id == playing && has(it.id) }

    /** What deleting says at the end: n things of a kind ("download", "song"). */
    fun deletedText(n: Int, kind: String): String = "Deleted $n $kind${if (n != 1) "s" else ""}"

    /** Delete the downloads among tracks (the app's own files, no asking); your own files are deleted by Android (or
     *  the PC) once you say yes to it, then [filesDeleted]. */
    fun downloads(tracks: List<Track>) {
        val lib = Music.lib
        val (ids, playing) = downloadsToDelete(tracks, { lib.has(it) }, Music.current()?.id)
        if (playing) toast("The playing song is left as it is: skip it first")
        if (ids.isEmpty()) return
        Music.bg({ ids.forEach { lib.remove(it) } }) { _, err ->
            toast(if (err != null) "Couldn't delete them: ${err.message}" else deletedText(ids.size, "download"))
            Music.savedVersion++; Music.musicVersion++
        }
    }

    /** Your own files (tracks) were deleted: out of your music, the queue and your playlists. */
    fun filesDeleted(tracks: List<Track>) {
        FolderCleanup.afterSongs(tracks)   // their folders, if no music is left in them
        val ids = tracks.map { it.id }.toSet()
        Music.local.files = Music.local.files.filter { it.id !in ids }
        Music.unqueueMany(Music.queue.indices.filter { Music.queue[it].id in ids })
        Music.lists.all().filter { p -> p.tracks.any { it.id in ids } }.forEach { Music.lists.remove(it.id, ids) }
        toast(deletedText(ids.size, "song"))
        Music.musicVersion++
    }

    /** One download deleted (Downloads' own row): not while it plays. */
    fun download(id: String) {
        if (Music.current()?.id == id) { toast("That song is playing; skip it first"); return }
        Music.bg({ Music.lib.remove(id) }) { _, _ -> Music.savedVersion++; Music.musicVersion++ }
    }
}
