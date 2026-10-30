package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** The Python app's tools on your songs' files, on both: Fix albums and artists, Live in song names, Empty folders and
 *  the folders they all leave alone. The logic is in Fixup.kt, LiveNames.kt and EmptyFolders.kt; this reads the files,
 *  asks Deezer, and writes, moves and tidies. On the phone only the app's own downloads can change (FileTags). */
object MusicTools {
    /** Folders no scan here touches or moves songs into (Settings › Your music › Folders to leave alone). */
    var skip by mutableStateOf(listOf<String>())
        private set
    /** The live songs you chose to keep as they are. */
    private var liveKept = setOf<String>()
    /** The soundtrack-named songs you chose to keep as they are. */
    private var movieKept = setOf<String>()

    fun init() {
        skip = Prefs.text("fixup_skip").split('\n').filter { it.isNotBlank() }
        liveKept = Prefs.text("live_kept").split('\n').filter { it.isNotBlank() }.toSet()
        movieKept = Prefs.text("movie_kept").split('\n').filter { it.isNotBlank() }.toSet()
    }

    fun chooseSkip(folders: List<String>) {
        skip = fixupSkipList(folders.map { n(it) })
        Prefs.setText("fixup_skip", skip.joinToString("\n"))
    }

    /** A path as the tools compare them: forward slashes. */
    private fun n(p: String) = p.replace('\\', '/')

    /** Your music folders as full paths. */
    fun roots(): List<String> = Music.folders.map { n(org.omarchy.music.ui.fullFolderPath(it.trimEnd('/'))) }

    /** (blocking) Your songs' files the tools may change, with their tags. */
    fun songs(progress: (String) -> Unit = {}): List<FixupSong> {
        val files = Music.mySongs().mapNotNull { it.file ?: Music.lib.items[it.id]?.file }.filter { FileTags.canWrite(it) }.distinct()
        return files.mapIndexedNotNull { i, f ->
            if (i % 50 == 0) progress("Reading your songs' tags… ${i + 1} of ${files.size}")
            val t = FileTags.read(Music.ctx, f) ?: return@mapIndexedNotNull null
            FixupSong(n(f), t["title"].orEmpty().ifEmpty { f.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.') },
                t["artist"].orEmpty().ifEmpty { "Unknown artist" }, t["albumartist"].orEmpty(), t["album"].orEmpty(),
                t["tracknumber"].orEmpty(), t["date"].orEmpty())
        }
    }

    private fun deezer(url: String) = runCatching { Pictures.deezerPace(); httpJson(url) }.getOrNull()

    /** An album's songs on Deezer as {clean title: track number}, or null if it isn't there. */
    private fun deezerTracklist(artist: String, album: String): Map<String, Int>? {
        val q = "artist:\"$artist\" album:\"$album\""
        val best = deezer("https://api.deezer.com/search/album?q=${enc(q)}&limit=5")["data"].list
            .firstOrNull { norm(it["title"].str ?: "") == norm(album) } ?: return null
        val tracks = deezer("https://api.deezer.com/album/${best["id"].long}/tracks?limit=200")["data"].list
        val out = LinkedHashMap<String, Int>()
        tracks.forEachIndexed { i, tr ->
            val k = tr["track_position"].long?.toInt() ?: (i + 1)
            out.putIfAbsent(norm(cleanTitle(tr["title"].str ?: "")), k)
            out.putIfAbsent(norm(cleanTitle(tr["title_short"].str ?: "")), k)
        }
        out.remove("")
        return out.ifEmpty { null }
    }

    private fun lead(a: String) = a.split(Regex(",|;| feat\\.? | ft\\.? | featuring | with | & ")).firstOrNull()?.trim().orEmpty()

    /** Every album on Deezer the song is on (its single too), best match first. */
    private fun deezerSongAlbums(artist: String, title: String): List<String> {
        val q = "artist:\"${lead(artist).ifEmpty { artist }}\" track:\"${bareTitle(title).ifEmpty { title }}\""
        val out = ArrayList<String>()
        for (d in deezer("https://api.deezer.com/search?q=${enc(q)}&limit=25")["data"].list) {
            val a = d["artist"]["name"].str ?: ""
            val s = minOf(similar(norm(cleanTitle(d["title"].str ?: "")), norm(cleanTitle(title))),
                maxOf(similar(norm(lead(a)), norm(lead(artist))), similar(norm(a), norm(artist))))
            val name = d["album"]["title"].str ?: ""
            if (s >= 0.85 && name.isNotEmpty() && name !in out) out += name
        }
        return out
    }

    /** A picture's look as 64 bits (a difference hash at 9×8, in grey): covers that look alike have bits nearly the same. */
    private fun pictureHash(data: ByteArray): Long? = runCatching {
        val img = org.omarchy.music.ui.decodeImage(data) ?: return null
        val (px, w, h) = org.omarchy.music.ui.pixelsOf(img)
        val grey = Array(8) { y -> IntArray(9) { x ->
            val p = px[(y * h / 8).coerceAtMost(h - 1) * w + (x * w / 9).coerceAtMost(w - 1)]
            ((p shr 16) and 0xff) * 30 + ((p shr 8) and 0xff) * 59 + (p and 0xff) * 11
        } }
        val flat = grey.flatMap { it.toList() }
        val mean = flat.average()
        if (flat.sumOf { (it - mean) * (it - mean) } / flat.size < 600.0 * 600.0) return null   // nearly blank
        var bits = 0L
        for (y in 0 until 8) for (x in 0 until 8) bits = (bits shl 1) or (if (grey[y][x] > grey[y][x + 1]) 1L else 0L)
        bits
    }.getOrNull()

    /** (blocking) What Fix albums and artists would change, and your songs; how many were left alone. */
    fun fixupScan(progress: (String) -> Unit): Triple<List<FixupChange>, List<FixupSong>, Int> {
        val songs = songs(progress)
        val looked = HashMap<Pair<String, String>, Map<String, Int>?>()
        val found = HashMap<Pair<String, String>, List<String>?>()
        progress("Looking for split albums, editions and singles…")
        val plan = fixupPlan(songs,
            tracklist = { a, al -> looked.getOrPut(norm(a) to norm(al)) { progress("Looking up “$al”…"); runCatching { deezerTracklist(a, al) }.getOrNull() } },
            findAlbum = { a, t -> found.getOrPut(norm(a) to norm(t)) { progress("Looking up “$t”…"); runCatching { deezerSongAlbums(a, t) }.getOrNull() } },
            coverOf = { f -> FileTags.cover(Music.ctx, f)?.let { pictureHash(it) } })
        val (kept, skipped) = fixupKept(plan, skip)
        return Triple(kept, songs, skipped)
    }

    /** (blocking) Write the changes picked into the files; a single with no cover of its own gets its album's. Lines on what failed. */
    fun fixupApply(changes: List<FixupChange>): Pair<Int, List<String>> {
        var done = 0; val failed = ArrayList<String>()
        for (c in changes) {
            if (FileTags.write(Music.ctx, c.file, c.new)) done++ else failed += "${c.file.substringAfterLast('/')}: couldn't write its tags"
            c.cover?.let { from ->
                if (!FileTags.hasCover(Music.ctx, c.file)) FileTags.cover(Music.ctx, from)?.let { FileTags.writeCover(Music.ctx, c.file, Covers.jpeg(it)) }
            }
        }
        return done to failed
    }

    /** Where the changed songs' files would go (the PC: Artist/Album folders as yours are). */
    fun fixupMovesFor(changes: List<FixupChange>, songs: List<FixupSong>) =
        if (!FileTags.canMove) emptyList() else fixupMoves(changes, songs, roots(), skip, { FileTags.exists(it) }, { FileTags.subfolders(it) })

    /** (blocking) Move songs, then keep them where they were: the queue and playlists. (moved, what failed). */
    fun moveFiles(moves: List<Pair<String, String>>): Pair<Int, List<String>> {
        val moved = HashMap<String, String>(); val failed = ArrayList<String>()
        for ((src, dest) in moves) {
            val to = FileTags.move(Music.ctx, src, dest)
            if (to != null) moved[n(src)] = to else failed += "${src.substringAfterLast('/')}: couldn't move it"
        }
        if (moved.isNotEmpty()) Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { Music.filesMoved(moved) }
        return moved.size to failed
    }

    fun liveFound(songs: List<FixupSong>) = liveNamesFound(songs, skip, liveKept)

    /** (blocking) The live names picked made plain; the others remembered as kept. */
    fun liveApply(found: List<LiveName>, picked: List<FixupPick>): Int {
        val (renames, keep) = liveNamesApply(found, picked)
        var done = 0
        for ((f, title) in renames) if (FileTags.write(Music.ctx, f, mapOf("title" to title))) done++
        liveKept = liveKept + keep
        Prefs.setText("live_kept", liveKept.joinToString("\n"))
        return done
    }

    fun movieFound(songs: List<FixupSong>) = namesFound(songs, skip, movieKept, ::movieName)

    /** (blocking) The soundtrack notes picked taken off the names; the others remembered as kept. */
    fun movieApply(found: List<LiveName>, picked: List<FixupPick>): Int {
        val (renames, keep) = liveNamesApply(found, picked)
        var done = 0
        for ((f, title) in renames) if (FileTags.write(Music.ctx, f, mapOf("title" to title))) done++
        movieKept = movieKept + keep
        Prefs.setText("movie_kept", movieKept.joinToString("\n"))
        return done
    }

    /** (blocking) Your songs that show no cover (no saved or picked cover, no service picture, none in the file),
     *  by album, the artists' order. */
    fun noCovers(progress: (String) -> Unit = {}): List<List<Track>> {
        val songs = Music.mySongs()
        val bare = songs.filterIndexed { i, t ->
            if (i % 50 == 0) progress("Looking at your covers… ${i + 1} of ${songs.size}")
            val saved = Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() && java.io.File(it).exists() } != null
            val picked = Pictures.cover(t.artist, t.album.ifEmpty { t.title }).isNotEmpty()
            !saved && !picked && t.thumb.isEmpty() && (t.file == null || !runCatching { FileTags.hasCover(Music.ctx, t.file!!) }.getOrDefault(false))
        }
        return bare.groupBy { Covers.albumKey(it) }.values.sortedWith(compareBy({ norm(it[0].artist) }, { norm(it[0].album) }))
    }

    /** (blocking) Folders in your music folders with no songs left in them. */
    fun emptyScan(): List<EmptyFolder> {
        if (!FileTags.canListFolders) return emptyList()
        val (files, dirs) = FileTags.listTree(roots())
        return emptyFoldersOffered(emptyFolders(roots(), files, dirs), skip)
    }

    /** (blocking) The folders picked, into the Recycle Bin, if they still have no songs. How many went. */
    fun emptyRemove(folders: List<EmptyFolder>): Int = folders.count { f ->
        val (now, _) = FileTags.listTree(listOf(f.folder))
        !hasMusic(now.keys.map { it.substringAfterLast('/') }) && FileTags.removeFolder(f.folder)
    }
}
