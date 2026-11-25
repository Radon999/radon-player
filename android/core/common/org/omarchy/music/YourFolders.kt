package org.omarchy.music

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Your music folders: choosing them, reading them, and keeping the queue, your playlists and favorites pointing at
 *  the right song when a file moves, is replaced, or belongs to your other device (a backup's). The rules are in
 *  [MusicFolderRules]. [Music] keeps the names the screens call and hands on. */
object YourFolders {
    private val lib get() = Music.lib
    private val local get() = Music.local
    private val lists get() = Music.lists
    private val favorites get() = Music.favorites
    private val ctx get() = Music.ctx
    private fun toast(text: String) = Music.toast(text)

    private var rescanAgain = false

    /** Read your folders again; then everything that follows from new or changed songs. */
    fun rescan() {
        if (local.scanning) { rescanAgain = true; return }   // the folders changed mid-scan: read again after
        local.scanning = true
        Music.bg({ local.scan(Music.folders, lib.items.values.mapNotNullTo(HashSet()) { LocalFiles.mediaId(it.file) }) }) { changed, err ->
            local.scanning = false
            if (rescanAgain) { rescanAgain = false; rescan() }
            if (err is SecurityException) Unit   // no permission yet: the Music tab asks
            else if (err != null) toast("Couldn't read your music: ${err.message?.take(80)}")
            if (changed == true) Music.musicVersion++
            adoptFiles()
            tidyGone()   // songs you moved: your playlists follow them; songs gone for good: out of them
            YourPlaylists.localFirst()   // streaming songs in your playlists that you have: your own files in their place
            fillDownloadGenres()   // downloads saved without a genre: one looked up and written in
            Music.accountsVersion++
            QualityCheck.scan()   // Loudness check: the songs new or changed since
            Music.bg({ FlacDepths.scan() })   // each new song's format, for the mark on its row ("FLAC 24", "MP3")
            runCatching { Moods.auto() }   // Moods: read the songs new or changed since, in the background
            TagMatch.refreshSoon()   // Tags & covers match: what the new or changed songs say
        }
    }

    fun rescanSoon() { if (now() - local.scanned > 120) rescan() }

    /** Your folders become list; one inside another is dropped, since the outer one has its songs. */
    fun choose(list: List<String>) {
        val clean = MusicFolderRules.clean(list)
        if (clean == Music.folders) return
        Music.folders = clean
        Music.saveState()
        rescan()
    }

    fun remove(path: String) = choose(Music.folders - path)

    /** Whether a song file's address is one this device can play (not the other device's, from a backup). */
    fun fileHere(t: Track): Boolean {
        val f = t.file ?: return true
        if (!BackupMerge.fileHere(f, platformName == "android")) return false
        if (lib.has(t.id)) return true   // a download (its own checks)
        return if (f.startsWith("content:")) runCatching { uriExists(ctx, f) }.getOrDefault(true) else File(f).exists()
    }

    /** The song playing couldn't be opened (its file was moved, renamed or deleted): your copy of the same song (the
     *  same title and artist) is linked in its place everywhere (the queue, playlists, favorites) and played. False if
     *  there's none (the usual "couldn't play"). */
    fun fileGone(): Boolean {
        val t = Music.current() ?: return false
        if (!t.isFile) return false
        val n = hereCopy(t)?.takeIf { it.file != t.file } ?: return false
        relink(mapOf(t.id to n))
        toast("“${t.title}” had moved: found it and linked it")
        Music.loadCurrent(autoplay = true)
        return true
    }

    /** Downloads with no genre in their tags (saved before downloads had one): each looked up once (Apple, Deezer,
     *  Last.fm) and written into the file, so they leave Unknown genre. */
    private var genresBusy = false
    private fun fillDownloadGenres() {
        if (genresBusy) return
        genresBusy = true
        Music.bg({
            val tried = Prefs.text("genres_tried").split(',').filter { it.isNotEmpty() }.toMutableSet()
            var done = 0
            val read = Prefs.text("genres_read").split(',').filter { it.isNotEmpty() }.toMutableSet()
            for (e in lib.items.values.toList()) {
                if (e.genre.isNotBlank() || (e.id in tried && e.id in read)) continue
                read += e.id
                val has = runCatching { Tags.read(ctx, listOf(e.file))["genre"] }.getOrNull()
                if (!has.isNullOrBlank()) { lib.update(e.id) { it.copy(genre = has.trim()) }; done++; continue }   // tagged: keep it
                if (e.id in tried || e.format == "m4a" || !Tags.writable(e.file)) continue
                tried += e.id
                val g = runCatching { GenreLookup.of(e.artist, e.title, e.album) }.getOrDefault("")
                // Not sent to your other device: it only fills a missing genre here, never changes one it has
                var wrote = false
                if (g.isNotEmpty()) TagSync.quietly { wrote = runCatching { Tags.write(ctx, e.file, mapOf("genre" to g)) }.isSuccess }
                if (wrote) {
                    lib.update(e.id) { it.copy(genre = g) }; done++
                }
            }
            Prefs.setText("genres_tried", tried.joinToString(","))
            Prefs.setText("genres_read", read.joinToString(","))
            done
        }) { n, _ ->
            genresBusy = false
            if ((n ?: 0) > 0) { toast(MusicFolderRules.genresText(n ?: 0)); rescan() }
        }
    }

    /** Clean and explicit versions: song old replaced by new (just downloaded): the new one takes its place in the
     *  queue, your playlists and favorites, and the old one is out of your music. */
    fun versionReplaced(old: Track, new: Track) {
        relink(mapOf(old.id to new))
        local.files = local.files.filter { it.id != old.id }
        // Its plays here go to the new one (Most played already counts both as one song: their names agree)
        Music.bg({ Music.plays.merge(setOf(old.id), new.slim()) })
        Music.savedVersion++; Music.musicVersion++
    }

    /** Songs whose files moved: each old one (by id) becomes the new one in the queue, your playlists and favorites. */
    fun relink(moved: Map<String, Track>) {
        if (moved.isEmpty()) return
        fun fix(x: Track): Track = moved[x.id]?.slim()?.copy(queued = x.queued) ?: x
        Music.queue = Music.queue.map(::fix)
        lists.remap(::fix)
        favorites.remap(::fix)
        Music.saveState(); Music.musicVersion++
    }

    /** (after your folders are read) Songs of yours in your playlists, the queue and favorites whose file is gone from
     *  this device: the same song found where it is now (you moved, renamed or tagged it anew: [hereCopy]) is linked
     *  in its place, so nothing has to be made again; else the same song to stream (as a song not on this device plays);
     *  else, playable nowhere, it is taken out of them, so a song you can't play isn't shown anywhere. The other
     *  device's files (a synced playlist's, a backup's) are left to [adoptFiles]; and when many of this device's files
     *  are gone at once ([MusicFolderRules.tooManyGone]: a card out, a drive not plugged in) only the copies are linked. */
    private var goneBusy = false
    private fun tidyGone() {
        if (goneBusy || local.files.isEmpty()) return   // your folders not read (no permission yet): nothing is called gone
        val android = platformName == "android"
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        val songs = (Music.queue + lists.items.flatMap { it.tracks } + favorites.tracks())
            .filter { it.isFile && !lib.has(it.id) && it.file?.let { f -> MusicFolderRules.thisDevices(f, android, windows) } == true }
            .distinctBy { it.id }
        if (songs.isEmpty()) return
        val sources = Music.sources
        val src = sources[MusicFolderRules.streamFrom(Music.searchIn, sources["tidal"]?.signedIn == true)] ?: sources["ytm"]
        goneBusy = true
        Music.bg({
            val gone = songs.filter { !fileHere(it) }
            val find = copyFinder()
            val moved = HashMap<String, Track>()
            val left = ArrayList<Track>()
            for (t in gone) find(t)?.takeIf { it.file != t.file && fileHere(it) }?.let { moved[t.id] = it } ?: left.add(t)
            val streams = HashMap<String, Track>()
            val nowhere = ArrayList<Track>()
            if (left.isNotEmpty() && src != null && !MusicFolderRules.tooManyGone(left.size, songs.size)) for (t in left) {
                if (noArtistNamed(t.artist)) { nowhere += t; continue }   // a file with no tags can't be looked for
                // null: can't tell now (no connection): it stays, to be looked for at the next read of your folders
                val found = runCatching { Music.matchSong(src, t.artist, withoutTrackNumber(t.title), t.duration.toInt()) }.getOrNull() ?: continue
                found.first?.let { streams[t.id] = it } ?: nowhere.add(t)
            } else if (left.isNotEmpty()) Crashes.note("Songs whose files are gone: ${left.size} of ${songs.size} left as they are " +
                if (src == null) "(the streaming services weren't ready)" else "(too many at once: a folder that isn't there for now?)")
            Triple(moved, streams, nowhere)
        }) { res, _ ->
            goneBusy = false
            val (moved, streams, nowhere) = res ?: return@bg
            if (moved.isEmpty() && streams.isEmpty() && nowhere.isEmpty()) return@bg
            relink(moved + streams)
            moved.forEach { (old, new) -> Music.bg({ Music.plays.merge(setOf(old), new.slim()) }) }   // its plays go with it
            if (nowhere.isNotEmpty()) {
                val ids = nowhere.mapTo(HashSet()) { it.id }
                lists.all().filter { p -> p.tracks.any { it.id in ids } }.forEach { lists.remove(it.id, ids) }
                lists.smart().filter { p -> p.tracks.any { it.id in ids } }.forEach { lists.remove(it.id, ids) }
                favorites.set(favorites.tracks().filter { it.id in ids }, false)
                Music.unqueueMany(Music.queue.indices.filter { Music.queue[it].id in ids && it != Music.index })
                Crashes.note("Taken out of your playlists, queue and favorites (not on this device, not found to stream): " +
                    nowhere.take(50).joinToString("; ") { "${it.title} · ${it.artist}" } + if (nowhere.size > 50) " and ${nowhere.size - 50} more" else "")
                Music.saveState(); Music.musicVersion++
            }
            toast(MusicFolderRules.goneText(moved.size, streams.size, nowhere.size))
        }
    }

    /** The same song of yours on this device (a file or a download) for a song file that isn't here: the other
     *  device's, or one gone from here ([MusicFolderRules.copyFinder]: a track number, signs or a missing artist aside). */
    fun hereCopy(t: Track): Track? = copyFinder()(t)

    private var finder: Pair<Int, (Track) -> Track?>? = null
    /** [MusicFolderRules.copyFinder] over your songs as they are now (made again when your music changes). */
    @Synchronized private fun copyFinder(): (Track) -> Track? {
        val v = Music.musicVersion * 31 + Music.local.files.size * 7 + lib.items.size
        finder?.takeIf { it.first == v }?.let { return it.second }
        return MusicFolderRules.copyFinder(Music.mySongs().filter { it.isFile || lib.has(it.id) }).also { finder = v to it }
    }

    /** After a backup from the other device: its song files in your queue, playlists and favorites become the same
     *  songs of yours here, once this device has read its folders (until none are left to find). */
    private fun adoptFiles() {
        if (!Prefs.on("adopt_files") || local.files.isEmpty()) return
        val android = platformName == "android"
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        val find = copyFinder()
        var found = 0
        var left = 0
        fun fix(t: Track): Track {
            // (a Linux PC's /home/… is the other device's on the phone too: BackupMerge.fileHere took it for the phone's)
            if (!t.isFile || MusicFolderRules.thisDevices(t.file ?: return t, android, windows)) return t
            val h = find(t) ?: run { left++; return t }
            found++
            return h.slim().copy(queued = t.queued)
        }
        Music.queue = Music.queue.map(::fix)
        lists.remap(::fix)
        favorites.remap(::fix)
        if (found > 0) { Music.saveState(); Music.musicVersion++; toast(MusicFolderRules.matchedText(found)) }
        if (left == 0) Prefs.set("adopt_files", false)
    }

    /** A song file that isn't on this device (from the other one's backup, or since gone) in the queue: the same
     *  song of yours here plays in its place, else the service's. */
    internal fun standIn(t: Track, autoplay: Boolean) {
        val at = Music.index
        fun swap(n: Track) {
            if (Music.index != at || Music.current()?.id != t.id) return
            Music.queue = Music.queue.toMutableList().also { it[at] = n.copy(queued = false) }
            Music.loadCurrent(autoplay)
        }
        hereCopy(t)?.let { swap(it); return }
        val sources = Music.sources
        val src = sources[MusicFolderRules.streamFrom(Music.searchIn, sources["tidal"]?.signedIn == true)] ?: sources["ytm"]
        if (src == null) { toast("“${t.title}” isn't on this ${org.omarchy.music.ui.DEVICE}: still connecting to the services"); return }
        Music.status = "Finding “${t.title}”…"
        Music.bg({ Music.matchSong(src, t.artist, t.title, t.duration.toInt()).first }) { found, _ ->
            if (Music.index != at) return@bg
            Music.status = ""
            if (found != null) swap(found)
            else {
                toast("“${t.title}” isn't on this ${org.omarchy.music.ui.DEVICE} and wasn't found to stream")
                if (autoplay) Music.scope.launch { delay(1500); if (Music.index == at) Music.next() }
            }
        }
    }

    /** Songs' files moved (Fix albums and artists): the queue and the playlists follow them; then a look at the folders. */
    fun filesMoved(moved: Map<String, String>) {
        fun fix(t: Track): Track = MusicFolderRules.moved(t, moved)
        Music.queue = Music.queue.map(::fix)
        lists.remap(::fix)
        lib.items.values.filter { it.file.replace('\\', '/') in moved }.forEach { e -> lib.update(e.id) { it.copy(file = moved[e.file.replace('\\', '/')]!!) } }
        Music.saveState()
        rescan()
    }
}
