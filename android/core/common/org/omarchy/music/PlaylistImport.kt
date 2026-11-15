package org.omarchy.music

import org.omarchy.music.Music.ImportReport

/** Importing an .m3u or .m3u8 playlist: each line found as a song of yours (where the playlist says it is, else one
 *  by the same name, else the same song in your music), else on Tidal (YouTube Music without it); a playlist of
 *  yours made of them; the ones not found kept to look for after (the import's report). How far it has got
 *  ([Music.importing]) and the report ([Music.importReport]) stay where the screens read them. [Music] keeps the
 *  names the screens call and hands on. */
object PlaylistImport {
    private val ctx get() = Music.ctx
    private val local get() = Music.local
    private val lists get() = Music.lists
    private val sources get() = Music.sources
    private fun mySongs() = Music.mySongs()
    private fun toast(text: String) = Music.toast(text)
    private fun <T> bg(work: () -> T, done: (T?, Throwable?) -> Unit = { _, _ -> }) = Music.bg(work, done)
    private var importing: String? get() = Music.importing; set(v) { Music.importing = v }
    private var importReport: ImportReport? get() = Music.importReport; set(v) { Music.importReport = v }
    private var musicVersion: Int get() = Music.musicVersion; set(v) { Music.musicVersion = v }

    /** (blocking) The songs of the .m3u/.m3u8 playlist at uri: (name, songs, those not found).
     *  Each is a file on the phone (where the playlist says, else one by the same name in the
     *  same folder), else the same song in your music, else the best match on Tidal (YouTube
     *  Music without it). */
    fun importM3u(uri: String): Triple<String, List<Track>, List<Missed>> {
        val fileName = ctx.displayName(uri) ?: uri.substringBefore('?').substringAfterLast('/').ifEmpty { "Playlist" }
        val entries = readM3u(ctx.openIn(uri)?.use { it.readBytes() } ?: error("couldn't open that file"))
        if (entries.isEmpty()) error("no songs in $fileName")
        // The playlist's own folder, for the paths in it that start from there ("../Album/01.flac").
        val here = ctx.folderOf(uri)
        val phone = runCatching { local.everywhere() }.getOrDefault(emptyMap())
        val byPath = phone.mapKeys { it.key.lowercase() }
        val tails = HashMap<String, Track>()
        for ((p, t) in phone) {
            val parts = p.lowercase().split('/').filter { it.isNotEmpty() }
            tails.putIfAbsent(parts.takeLast(2).joinToString("/"), t)
            tails.putIfAbsent(parts.last(), t)
        }
        val mine = mySongs()
        val byKey = HashMap<Pair<String, String>, Track>()
        for (t in phone.values + mine) byKey.putIfAbsent(songKey(t), t)
        val src = sources["tidal"]?.takeIf { it.signedIn } ?: sources["ytm"]
        val out = ArrayList<Track>()
        val missed = ArrayList<Missed>()
        for ((i, e) in entries.withIndex()) {
            importing = "Finding the songs of $fileName… ${i + 1} of ${entries.size}"
            var t: Track? = null
            var why = "not found"
            var artist = e.artist
            var title = e.title
            if (Regex("^https?://").containsMatchIn(e.loc)) {
                val from = linkSource(e.loc)?.let { sources[it] }
                if (from != null) t = try { from.search(e.loc).firstOrNull() } catch (x: Exception) {
                    why = "its link didn't open (${x.message?.take(60)})"; null
                }
            } else {
                val where = M3uPaths.clean(e.loc)
                val full = if (M3uPaths.whole(where)) where
                    else java.io.File("/$here", where).normalize().path.removePrefix("/")
                t = byPath[full.lowercase()]
                if (t == null) {
                    val parts = where.lowercase().split('/').filter { it.isNotEmpty() }
                    t = tails[parts.takeLast(2).joinToString("/")] ?: tails[parts.lastOrNull() ?: ""]
                }
                if (title.isEmpty()) { val (a, b) = namesFromFile(where); artist = a; title = b }   // no #EXTINF
            }
            if (t == null && title.isNotEmpty()) t = byKey[songKey(Track("", title = title, artist = artist))]
            if (t == null && title.isNotEmpty() && src != null) {
                t = try { matchSong(src, artist, title, e.seconds).first } catch (x: Exception) {
                    why = "${src.name} didn't answer (${x.message?.take(60)})"; null
                }
            }
            if (t != null) out += t.slim()
            else missed += Missed(i, out.size, artist, title.ifEmpty { e.loc.substringAfterLast('/').substringBeforeLast('.').ifEmpty { e.loc } },
                e.seconds, e.loc, if (title.isNotEmpty()) why else "the playlist doesn't name it")
        }
        return Triple(fileName.substringBeforeLast('.'), out, missed)
    }

    /** (blocking) The song on src: (its best match or null, what the search found). Throws when src can't be reached. */
    fun matchSong(src: Source, artist: String, title: String, seconds: Int): Pair<Track?, List<Track>> {
        val hits = src.search("$artist ${bareTitle(title)}".trim()).take(8)
        return bestMatch(hits, artist, title, seconds) to hits
    }

    /** The services a song an import didn't find can be looked for on: [(key, name)]. */
    fun reportSources(): List<Pair<String, String>> =
        SOURCES.filter { (k, _) -> k != SoundCloud.KEY && sources[k]?.let { k != "tidal" || it.signedIn } == true }.toList()

    /** Put t, found for m, back where m was in the playlist id; placed: the ones put back already (m joins them). */
    fun placeMissed(id: String, m: Missed, t: Track, placed: MutableList<Missed>): Boolean {
        if (lists.add(id, listOf(t), at = missedAt(m, placed)) == 0) return false
        placed += m
        musicVersion++
        return true
    }

    /** Make a playlist of yours from the .m3u8 at uri, named after the file. */
    fun importPlaylist(uri: String) {
        if (importing != null) { toast("Still importing the last one…"); return }
        importing = "Reading the playlist…"
        bg({ importM3u(uri) }) { res, err ->
            importing = null
            if (res == null) { val why = err?.let { ImportNotes.why(it) } ?: "nothing could be read from it"
                ImportNotes.failed("playlists", RuntimeException("the playlist file: $why")); toast("Import failed: $why"); return@bg }
            val (name, tracks, missed) = res
            val id = lists.create(name).id
            lists.add(id, tracks)
            musicVersion++
            missed.forEach { logWarn("import $name: not found: ${it.name} (${it.why})") }
            ("Imported “$name”: ${tracks.size} songs" + if (missed.isNotEmpty()) " (${missed.size} not found)" else "").let {
                ImportNotes.ok("playlists", it); toast("Import worked: $it") }
            if (missed.isNotEmpty()) importReport = ImportReport(id, name, missed)
        }
    }
}
