package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Changing a song's tags and cover by hand (Edit tags, the Tagger, Cover...): the changes written into the files, the
 *  new names shown wherever the song is, its file moved to its new artist's and album's folder, and what is asked for
 *  the song playing kept until its file is free. [Music] keeps the names the screens call and hands on. */
object TagEdits {
    private val lib get() = Music.lib
    private val ctx get() = Music.ctx
    private val lists get() = Music.lists
    private val local get() = Music.local
    private val scope get() = Music.scope
    private fun toast(text: String) = Music.toast(text)
    private fun <T> bg(work: () -> T, done: (T?, Throwable?) -> Unit = { _, _ -> }) = Music.bg(work, done)
    private fun current() = Music.current()
    private fun mySongs() = Music.mySongs()
    private fun downloadFolder() = Music.downloadFolder()
    private fun noteGenre(file: String, g: String) = Music.noteGenre(file, g)
    private var queue: List<Track> get() = Music.queue; set(v) { Music.queue = v }
    private var musicVersion: Int get() = Music.musicVersion; set(v) { Music.musicVersion = v }
    private var savedVersion: Int get() = Music.savedVersion; set(v) { Music.savedVersion = v }

    /** Make source (a picture's web address, or a content:// one you picked) the cover of tracks' album:
     *  into its downloaded FLACs (all but the playing one's), and shown for all of its songs. */
    /** Covers you picked for the song playing, waiting for its file to be free (written when another song starts). */
    private val coversWaiting = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private fun coverLater(file: String, jpeg: ByteArray) { coversWaiting[file] = jpeg }
    /** A song (t) is being loaded: covers picked while their song played go in now, once the file that was playing is free. */
    internal fun waitingCoversSoon(t: Track) {
        if (coversWaiting.isNotEmpty()) { val nowFile = lib.items[t.id]?.file ?: t.file
            scope.launch(Dispatchers.Main) { delay(1500); writeWaitingCovers(nowFile) } }
    }
    private fun writeWaitingCovers(except: String?) {
        val todo = coversWaiting.keys.filter { it != except }
        if (todo.isEmpty()) return
        bg({
            for (f in todo) {
                val jpeg = coversWaiting.remove(f) ?: continue
                val ok = CoverGuard.byHand {
                    if (lib.items.values.any { it.file == f && it.format == "flac" }) runCatching { Covers.write(ctx, f, jpeg); true }.getOrDefault(false)
                    else FileTags.writeCover(ctx, f, jpeg)
                }
                if (ok) TagMatch.touched(f) else logWarn("cover: couldn't put the picked cover into $f")
            }
        }) { _, _ -> }
    }

    fun setCover(tracks: List<Track>, source: String, wholeAlbum: Boolean = true, done: () -> Unit = {}) {
        val t = tracks.firstOrNull() ?: return
        val playing = current()?.id
        bg({
            val raw = if (!source.startsWith("http")) ctx.openIn(source)!!.use { it.readBytes() }
                else httpBytes(source)
            val jpeg = Covers.jpeg(raw)
            val keys = tracks.map { Covers.albumKey(it) }.toSet()   // every album of the songs: all of each one's songs
            val saved = File(File(ctx.filesDir, "covers").apply { mkdirs() }, "${randomHex(8)}.jpg").apply { writeBytes(jpeg) }
            val ids = tracks.map { x -> x.id }.toSet()
            val album = lib.items.values.filter { (wholeAlbum && Covers.albumKey(it.track()) in keys) || it.id in ids }
            var written = 0
            val failed = ArrayList<String>()
            val forSync = ArrayList<Pair<String, String>>()   // the songs it went into: the same picture for your other device's copies
            for (e in album.distinctBy { it.file }) {
                forSync += e.title to e.artist
                if (e.id == playing) { coverLater(e.file, jpeg); written++; continue }   // its file is playing: right after it
                if (e.format == "flac" && Tags.writable(e.file)) {
                    try { CoverGuard.byHand { Covers.write(ctx, e.file, jpeg) }; written++ } catch (x: Exception) { failed += "${e.title}: ${x.message}" }
                } else if (FileTags.canWrite(e.file)) {   // a download in another format (MP3, M4A…): in its own way
                    if (CoverGuard.byHand { FileTags.writeCover(ctx, e.file, jpeg) }) written++ else failed += "${e.title}: couldn't put the cover in"
                }
            }
            // Your own files of the album too (MP3, M4A, WAV, FLAC…): the cover goes into them, so it travels with the
            // files to your other device (picked only in the app, it stayed on this one)
            val curFile = current()?.let { lib.items[it.id]?.file ?: it.file }
            for (x in runCatching { mySongs() }.getOrDefault(emptyList())) {
                val f = x.file?.takeIf { x.isFile && !lib.has(x.id) } ?: continue
                if (!((wholeAlbum && Covers.albumKey(x) in keys) || x.id in ids)) continue
                forSync += x.title to x.artist
                if (!FileTags.canWrite(f)) { failed += "${x.title}: the app can't change its file"; continue }
                if (f == curFile) { coverLater(f, jpeg); written++; continue }   // playing: written as soon as it stops
                if (CoverGuard.byHand { FileTags.writeCover(ctx, f, jpeg) }) { written++; TagMatch.touched(f) } else failed += "${x.title}: couldn't put the cover in"
            }
            runCatching { TagSync.noteCover(forSync, jpeg) }   // goes to your other device at the next sync
            for (e in album) {   // a new file each time: the old picture stays in the image cache under the old name
                val art = File(lib.artDir, "${safeName(e.id)}-${randomHex(4)}.jpg").apply { writeBytes(jpeg) }
                val old = e.art
                lib.update(e.id) { it.copy(art = art.path) }
                if (old.isNotEmpty() && lib.items.values.none { it.art == old }) File(old).delete()
            }
            for (x in tracks.distinctBy { Covers.albumKey(it) })   // your own songs' albums show it too
                if (x.album.isNotEmpty()) Pictures.chooseCover(x.artist, x.album, saved.path)
            for (x in tracks.filter { it.album.isEmpty() }) Pictures.chooseCover(x.artist, x.title, saved.path)
            written to failed
        }) { res, err ->
            if (err != null || res?.second?.isNotEmpty() == true) toast("Couldn't use that cover: ${(err?.message ?: res!!.second[0]).take(100)}")
            if (res != null) toast("New cover for “${t.album.ifEmpty { t.title }}”" + if (res.first > 0) " in ${res.first} file${if (res.first != 1) "s" else ""}" else "")
            savedVersion++; musicVersion++
            done()
        }
    }

    /** The folder a song's file belongs in once its artist or album changed (Artists/<Artist>/<Album>/, EPs and singles in
     *  their own folders, as downloads are), or null to leave it where it is. Downloads always go there (under the download
     *  folder); a file of yours only when it sat where its old artist and album put it, in an Artists folder (so folders you
     *  arranged yourself, like unreleased songs, stay as they are). On the phone only the app's own downloads can move. */
    internal fun releaseFolder(file: String, download: Boolean, oldArtist: String, oldAlbum: String, artist: String, album: String): String? {
        if (artist.isBlank()) return null
        val f = file.replace('\\', '/')
        if (f.startsWith("content:")) return if (download) downloadPlace(downloadFolder(), artist, album, null, null, "x").first else null
        if (!FileTags.canMove) return null
        val root = if (download) org.omarchy.music.ui.fullFolderPath(downloadFolder().trimEnd('/')).replace('\\', '/')
        else {
            val parts = f.split('/'); val i = parts.lastIndexOf(ARTISTS)
            if (i < 0) return null
            val r = parts.take(i + 1).joinToString("/")
            val was = downloadPlace(r, oldArtist, oldAlbum, null, null, "x").first
            if (!f.substringBeforeLast('/').plus("/").equals(was, ignoreCase = true)) return null
            r
        }
        if (!download && MusicTools.skip.any { inFolders(f, listOf(it)) }) return null
        return downloadPlace(root, artist, album, null, null, "x").first
    }

    /** Tag changes to the song that was playing, by its id: written once another song is on (or none). */
    private val heldTags = LinkedHashMap<String, Pair<Track, LinkedHashMap<String, String>>>()
    /** A song is being loaded: the song that was playing gets its tag changes, once its file is free. */
    internal fun heldTagsSoon() { if (heldTags.isNotEmpty()) scope.launch(Dispatchers.Main) { delay(1500); writeHeldTags() } }
    private fun writeHeldTags() {
        if (heldTags.isEmpty()) return
        val cur = current()?.id
        val ready = heldTags.filterKeys { it != cur }
        ready.keys.forEach { heldTags.remove(it) }
        for ((t, ch) in ready.values) editTags(listOf(t), ch)
    }

    /** Write changes {field: value} into the tags of tracks' downloaded FLACs, and show the new
     *  names wherever the songs are. The playing song is left alone: its file is being read. */
    fun editTags(tracks: List<Track>, changes: Map<String, String>, renameFiles: Boolean = false) {
        val playing = current()?.id
        val todo = tracks.filter { Tags.editableAny(it) != null && it.id != playing }
        // The playing song's file is being read: its changes wait, and are written the moment it ends
        tracks.firstOrNull { it.id == playing && Tags.editableAny(it) != null }?.let { t ->
            heldTags.getOrPut(t.id) { t to LinkedHashMap() }.second.putAll(changes)
            toast("“${t.title}” is playing: its new tags are saved as soon as it ends")
        }
        if (todo.isEmpty() || changes.isEmpty()) return
        bg({
            val renamed = HashMap<String, Triple<String, String, String>>()
            val failed = ArrayList<String>()
            val moves = ArrayList<Pair<String, String>>()
            for (t in todo) {
                val e = lib.items[t.id]
                val file = e?.file ?: Tags.editableAny(t) ?: continue   // a download, or (PC) a file of yours
                val c = try { Tags.write(ctx, file, changes) } catch (x: Exception) { failed += "${t.title}: ${x.message}"; continue }
                if (changes.keys.any { it.equals("genre", true) }) noteGenre(file, c["GENRE"]?.firstOrNull() ?: "")
                val title = c["TITLE"]?.firstOrNull() ?: e?.title ?: t.title
                val artist = c["ARTIST"]?.joinToString(", ")?.ifEmpty { null } ?: "Unknown artist"
                val album = c["ALBUM"]?.firstOrNull() ?: ""
                var dest: String? = if (renameFiles && FileTags.canMove && title != (e?.title ?: t.title))
                    renamedFile(file, e?.title ?: t.title, title) else null
                // A new artist or album: the file goes to that artist's and album's folder (made if it isn't there)
                if (changes.keys.any { it.lowercase() in setOf("artist", "album", "albumartist") }) {
                    val lead = (c["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: artist).split(",")[0].trim()
                    releaseFolder(file, e != null, (e?.artist ?: t.artist).split(",")[0].trim(), e?.album ?: t.album, lead, album)?.let { folder ->
                        dest = if (file.startsWith("content:")) folder else folder + (dest ?: file).replace('\\', '/').substringAfterLast('/')
                    }
                }
                dest?.let { d -> if (!d.replace('\\', '/').equals(file.replace('\\', '/'), ignoreCase = true)) moves += file.replace('\\', '/') to d.replace('\\', '/') }
                if (e == null) { renamed[t.id] = Triple(title, artist, album); continue }
                lib.items.values.filter { it.file == e.file }.forEach { same ->
                    lib.update(same.id) { it.copy(title = title, artist = artist, album = album, genre = c["GENRE"]?.firstOrNull() ?: "") }
                    renamed[same.id] = Triple(title, artist, album)
                }
            }
            lists.retitle(renamed.keys) { x -> renamed[x.id]!!.let { (a, b, c) -> x.copy(title = a, artist = b, album = c) } }
            if (moves.isNotEmpty()) {
                val (n, bad) = MusicTools.moveFiles(moves.distinct())
                if (bad.isNotEmpty()) failed += bad
                else if (n > 0) scope.launch(kotlinx.coroutines.Dispatchers.Main) { toast("$n file${if (n != 1) "s" else ""} moved to match ${if (n != 1) "their" else "its"} tags") }
            }
            renamed to failed
        }) { res, err ->
            val (renamed, failed) = res ?: (emptyMap<String, Triple<String, String, String>>() to listOf(err?.message ?: "?"))
            queue = queue.map { x -> renamed[x.id]?.let { (a, b, c) -> x.copy(title = a, artist = b, album = c) } ?: x }
            if (renamed.keys.any { k -> local.files.any { it.id == k } })
                local.files = local.files.map { x -> renamed[x.id]?.let { (a, b, c) -> x.copy(title = a, artist = b, album = c) } ?: x }
            if (failed.isNotEmpty()) toast("Couldn't write the tags: ${failed[0].take(100)}")
            val n = todo.size - failed.size
            if (n > 0) toast("Tags written to $n song${if (n != 1) "s" else ""}")
            savedVersion++; musicVersion++
        }
    }

    private var lyricsWarned = false

    /** Write the lyrics found for t into its file's tags (if it has none). Your own files need All files access. */
    fun lyricsIntoFile(t: Track, force: Boolean = false) {   // force: asked for (the Tagger), whatever the setting
        if ((!Music.lyricsToFiles && !force) || Remote.active) return
        val uri = (lib.items[t.id]?.file ?: t.file)?.takeIf { Tags.writable(it) } ?: return
        val own = lib.items[t.id] == null
        if (own && !Music.canWriteOwnFiles()) return
        bg({
            val words = Lyrics.get(ctx, t)?.let { LyricsTags.words(it) } ?: return@bg false
            LyricsTags.write(ctx, uri, words)
        }) { wrote, err ->
            if (err is SecurityException && !lyricsWarned) {
                lyricsWarned = true
                toast("Couldn't put the lyrics into “${t.title}”: allow All files access in Settings › Lyrics into files")
            }
            if (wrote == true) savedVersion++
        }
    }
}
