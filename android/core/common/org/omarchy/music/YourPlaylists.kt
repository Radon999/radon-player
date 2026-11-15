package org.omarchy.music

/** Your playlists and the folders you keep them in: making, naming, filling and deleting them, copying a service's
 *  playlist into yours, and downloading whole playlists. The playlists themselves are kept by [Playlists] and the
 *  folders by [PlaylistFolders]; this is what the screens ask for, with what it tells you. [Music] keeps the names
 *  the screens call and hands on. */
object YourPlaylists {
    private val lists get() = Music.lists
    private val folders get() = Music.playlistFolders
    private fun toast(text: String) = Music.toast(text)
    private fun changed() { Music.musicVersion++ }

    // ---- your playlists ----
    fun create(name: String, synced: Boolean = false): String {
        val id = lists.create(name, synced = synced).id
        toast(if (synced) "Made the synced playlist “$name”: it's on the ${if (Remote.paired) "PC" else "other device"} too after the next sync" else "Made “$name”")
        changed()
        return id
    }

    /** What adding songs to a playlist says: asked is how many you added, new how many of them weren't in it. */
    fun addedText(name: String, asked: Int, new: Int): String = when {
        new == 0 -> "Already in “$name”"
        asked == 1 -> "Added to “$name”"
        else -> "$new songs added to “$name”" + if (new < asked) " (${asked - new} were in it)" else ""
    }

    fun add(id: String, tracks: List<Track>) {
        val pl = lists.get(id) ?: return
        if (tracks.any { it.source == SoundCloud.KEY }) toast("SoundCloud songs can't go in your playlists: like them on SoundCloud instead")
        val ok = tracks.filter { it.source != SoundCloud.KEY }
        if (ok.isEmpty()) return
        val n = lists.add(id, ok)
        toast(addedText(pl.name, ok.size, n))
        changed()
    }

    fun remove(id: String, ids: Set<String>) { lists.remove(id, ids); changed() }
    fun rename(id: String, name: String) { lists.rename(id, name); changed() }

    /** Settings a playlist to sync with the other device (Sync), or not. */
    fun setSynced(id: String, on: Boolean) {
        lists.setSynced(id, on); changed()
        toast(if (on) "Synced: it's the same on your phone and PC after each sync" else "Not synced any more: each device keeps its own copy")
        if (on) Sync.soon()
    }

    /** A playlist of yours deleted: out of every folder too. */
    fun delete(id: String) {
        lists.get(id)?.let { toast("Deleted “${it.name}”") }
        folders.forget("mine::$id")
        lists.delete(id)
        changed()
    }

    // ---- folders of playlists ----
    fun newFolder(name: String, item: FolderItem? = null) {
        val f = folders.create(name)
        if (item != null) { folders.add(f.id, item); toast("Put “${item.title}” in the new folder “$name”") }
        changed()
    }

    fun renameFolder(id: String, name: String) { folders.rename(id, name); changed() }

    fun deleteFolder(id: String) {
        folders.get(id)?.let { toast("Deleted the folder “${it.name}”") }
        folders.delete(id)
        changed()
    }

    fun addToFolder(id: String, item: FolderItem) {
        val f = folders.get(id) ?: return
        toast(if (folders.add(id, item)) "Put “${item.title}” in “${f.name}”" else "“${item.title}” is in “${f.name}” already")
        changed()
    }

    fun removeFromFolder(id: String, item: FolderItem) { folders.remove(id, item.key); changed() }

    /** The playlists that are in a folder (their [FolderItem.key]s): they show in their folder only. */
    fun inFolders(): Set<String> = folders.items.flatMapTo(HashSet()) { f -> f.playlists.map { it.key } }

    /** Of your playlists, the ones in no folder: the list beside the folders shows these. */
    fun outsideFolders(mine: List<Playlist>): List<Playlist> = inFolders().let { inside -> mine.filter { FolderItem.mine(it).key !in inside } }

    /** Of a service's playlists, the ones in no folder. */
    fun outsideFolders(theirs: List<Album>, source: String): List<Album> =
        inFolders().let { inside -> theirs.filter { FolderItem.service(it, source).key !in inside } }

    /** The songs of your playlists in the folder, each once (a service's playlists in it aren't read for this). */
    fun folderSongs(f: PlaylistFolder): List<Track> =
        f.playlists.filter { it.kind == "mine" }.flatMap { lists.get(it.id)?.tracks ?: emptyList() }.distinctBy { it.id }

    /** Folders deleted together: their playlists stay, and show beside the folders again. */
    fun deleteFolders(ids: Collection<String>) {
        val names = ids.mapNotNull { folders.get(it)?.name }
        ids.forEach { folders.delete(it) }
        if (names.isNotEmpty()) toast(if (names.size == 1) "Deleted the folder “${names[0]}”" else "Deleted ${names.size} folders")
        changed()
    }

    // ---- a service's playlists, copied into yours ----
    /** What importing one playlist says: made new here, or one of yours by that name given the songs it hadn't got. */
    fun importedText(name: String, service: String?, new: Int, wasHere: Boolean): String =
        if (!wasHere) "Imported “$name” from $service: $new songs. Open it in your playlists to download it"
        else "“$name” was imported already: $new new song${if (new != 1) "s" else ""} added"

    /** What importing all of a service's playlists says at the end. */
    fun importedAllText(service: String?, all: Int, failed: Int, songs: Int): String =
        "Imported ${all - failed} of $all $service playlists ($songs songs)" + if (failed > 0) ", $failed couldn't be read" else ""

    /** Copy a service's playlist (Tidal, YouTube Music, Deezer, Apple Music, Spotify) into your playlists here, its
     *  songs streaming as they are until you download them. A playlist of yours with the same name gets the songs it
     *  hasn't got (importing again brings in what was added there since). */
    fun importFrom(key: String, pl: Album, quiet: Boolean = false, done: ((Int) -> Unit)? = null) {
        if (key == SoundCloud.KEY) { if (!quiet) toast(SoundCloud.APART); done?.invoke(-1); return }
        val src = Music.sources[key] ?: run { toast("Sign in to ${SOURCES[key]} first"); return }
        if (!quiet) toast("Importing “${pl.title}”…")
        Music.bg({ src.playlist(pl.id) }) { tracks, err ->
            if (tracks == null) {
                val why = err?.let { ImportNotes.why(it) } ?: "the service sent nothing back"
                if (!quiet) { ImportNotes.failed("playlists", RuntimeException("“${pl.title}” from ${SOURCES[key]}: $why")); toast("Import failed: $why") }
                done?.invoke(-1); return@bg
            }
            val name = pl.title.ifBlank { "${SOURCES[key]} playlist" }
            val mine = lists.all().firstOrNull { it.name.equals(name, ignoreCase = true) }
            val id = mine?.id ?: lists.create(name).id
            val n = lists.add(id, tracks)
            changed()
            if (!quiet) importedText(name, SOURCES[key], n, wasHere = mine != null).let { ImportNotes.ok("playlists", it); toast("Import worked: $it") }
            done?.invoke(n)
        }
    }

    /** Every playlist of yours on that service imported, one after another. */
    fun importAllFrom(key: String, todo: List<Album>) {
        if (key == SoundCloud.KEY) { toast(SoundCloud.APART); return }
        if (todo.isEmpty()) return
        toast("Importing ${todo.size} ${SOURCES[key]} playlists…")
        var i = 0; var songs = 0; var failed = 0
        fun next() {
            if (i >= todo.size) {
                val text = importedAllText(SOURCES[key], todo.size, failed, songs)
                if (failed == todo.size) ImportNotes.failed("playlists", RuntimeException("none of the ${todo.size} ${SOURCES[key]} playlists could be read"))
                else ImportNotes.ok("playlists", text)
                toast(if (failed == todo.size) "Import failed: none of the playlists could be read" else "Import worked: $text"); return
            }
            importFrom(key, todo[i++], quiet = true) { n -> if (n < 0) failed++ else songs += n; next() }
        }
        next()
    }

    /** The songs of these playlists that aren't on this device yet: each once, your song files and the ones
     *  downloaded already left out. */
    fun toDownload(ids: Collection<String>): List<Track> =
        ids.flatMap { lists.get(it)?.tracks ?: emptyList() }.distinctBy { it.id }.filter { !it.isFile && !Music.lib.has(it.id) }

    /** Download every song of these playlists that isn't on this device yet: your song files are skipped, and so are
     *  songs downloaded already (and Copies decides about songs you have another copy of). */
    fun download(ids: Collection<String>) {
        if (toDownload(ids).isEmpty()) { toast("Every song there is downloaded or one of your files already"); return }
        Music.download(ids.flatMap { lists.get(it)?.tracks ?: emptyList() }.distinctBy { it.id })
    }
}
