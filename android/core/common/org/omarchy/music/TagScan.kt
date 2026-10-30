package org.omarchy.music


/** The tag scanner (Settings › Tagger › Scan…): the desktop's tag_scan_plan. Your songs' tags checked against
 *  Deezer's catalogue; what's missing or different is offered an album at a time, each Replace or Keep, and an album
 *  with no cover (or a small one) gets up to three covers to pick from, or Keep. Here only the FLACs the app
 *  downloaded can be written (Android keeps other apps' files, your own songs, to themselves). */
object TagScan {
    val ALBUM_FIELDS = listOf("album" to "Album", "albumartist" to "Album artist", "date" to "Year", "genre" to "Genre")
    val SONG_FIELDS = listOf("title" to "Title", "artist" to "Artist", "tracknumber" to "Track")
    val FIELDS = (ALBUM_FIELDS + SONG_FIELDS).toMap()
    const val SMALL_COVER = 500

    data class Found(val id: Long, val title: String, val artist: String, val albumId: Long?, val album: String)
    data class AlbumInfo(val title: String, val artist: String, val date: String, val genre: String, val cover: String,
                         val positions: Map<Long, Int>)
    /** A change to a tag: what the file has, what was found, and whether it's replaced unless you say otherwise. */
    data class Change(val old: String, val new: String, val replace: Boolean)
    data class Song(val uri: String, val title: String, val changes: Map<String, Change>)
    data class Group(val key: String, val album: String, val artist: String, val uris: List<String>, val songs: List<Song>,
                     val covers: List<String>, val coverNote: String)

    private fun lead(a: String) = a.split(Regex(",|;| feat\\.? | ft\\.? | featuring | with ", RegexOption.IGNORE_CASE)).first().trim()

    /** Whether a tag the file has really differs from Deezer's (not only in case, punctuation or "feat."). */
    fun differs(field: String, old: String, new: String): Boolean = when (field) {
        "title" -> norm(cleanTitle(old)) != norm(cleanTitle(new))
        "artist", "albumartist" -> norm(lead(old)) != norm(lead(new))
        "album" -> norm(bareTitle(old)) != norm(bareTitle(new))
        "date" -> old.take(4) != new.take(4)
        "tracknumber" -> old.trimStart('0') != new.trimStart('0')
        else -> false   // a genre the file has is kept
    }

    // ---- Deezer (about nine requests a second at most) ----
    private fun get(url: String): kotlinx.serialization.json.JsonElement {
        Pictures.deezerPace()   // one pace with the artist pictures' asking: together they went over Deezer's limit
        val d = httpJson(url)
        if (d["error"]["code"].str == "4") { Thread.sleep(5000); Pictures.deezerPace(); return httpJson(url) }
        return d
    }

    fun findTrack(artist: String, title: String, album: String): Found? {
        val wantT = norm(cleanTitle(title)); val wantA = norm(lead(artist)); val wantAl = norm(bareTitle(album))
        val queries = (if (artist.isNotEmpty()) listOf("artist:\"${lead(artist)}\" track:\"${bareTitle(title).ifEmpty { title }}\"") else emptyList()) +
            "${lead(artist)} ${bareTitle(title).ifEmpty { title }}".trim()
        for (q in queries) {
            var best: kotlinx.serialization.json.JsonElement? = null; var score = 0.0
            for (d in get("https://api.deezer.com/search?q=${enc(q)}&limit=15")["data"].list) {
                var s = similar(norm(cleanTitle(d["title"].str ?: "")), wantT)
                if (artist.isNotEmpty()) s = minOf(s, maxOf(similar(norm(lead(d["artist"]["name"].str ?: "")), wantA),
                    similar(norm(d["artist"]["name"].str ?: ""), norm(artist))))
                if (wantAl.isNotEmpty() && similar(norm(bareTitle(d["album"]["title"].str ?: "")), wantAl) > 0.85) s += 0.05
                if (s > score) { best = d; score = s }
            }
            val b = best
            if (b != null && score >= (if (artist.isNotEmpty()) 0.85 else 0.92))
                return Found(b["id"].str?.toLongOrNull() ?: 0, b["title"].str ?: "", b["artist"]["name"].str ?: "",
                    b["album"]["id"].str?.toLongOrNull(), b["album"]["title"].str ?: "")
        }
        return null
    }

    fun albumInfo(id: Long): AlbumInfo? {
        val d = get("https://api.deezer.com/album/$id")
        if (d["id"].str == null) return null
        var tracks = d["tracks"]["data"].list
        if ((d["nb_tracks"].str?.toIntOrNull() ?: 0) > tracks.size || tracks.any { it["track_position"].str == null })
            tracks = get("https://api.deezer.com/album/$id/tracks?limit=300")["data"].list.ifEmpty { tracks }
        return AlbumInfo(d["title"].str ?: "", d["artist"]["name"].str ?: "", d["release_date"].str ?: "",
            d["genres"]["data"][0]["name"].str ?: "", d["cover_xl"].str ?: d["cover_big"].str ?: "",
            tracks.mapIndexed { i, t -> (t["id"].str?.toLongOrNull() ?: 0L) to (t["track_position"].str?.toIntOrNull() ?: (i + 1)) }.toMap())
    }

    fun albumCovers(artist: String, album: String): List<String> =
        get("https://api.deezer.com/search/album?q=${enc("artist:\"$artist\" album:\"${bareTitle(album).ifEmpty { album }}\"")}&limit=10")["data"].list
            .filter { similar(norm(bareTitle(it["title"].str ?: "")), norm(bareTitle(album))) > 0.7 }
            .mapNotNull { it["cover_xl"].str ?: it["cover_big"].str }.take(3)

    /** A FLAC's tags as the scanner reads them, and its cover (null if none). */
    fun read(uri: String): Pair<Map<String, String>, ByteArray?> = runCatching {
        Music.ctx.openIn(uri)!!.buffered().use { s ->
            val blocks = Flac.readHeader(s)
            val c = Flac.comments(blocks)
            FIELDS.keys.associateWith { k ->
                val v = c[k.uppercase()] ?: emptyList()
                (if (k == "artist") v.joinToString(", ") else v.firstOrNull() ?: "").trim()
            }.toMutableMap().also { it["tracknumber"] = it["tracknumber"].orEmpty().substringBefore('/').trim() } to Flac.picture(blocks)
        }
    }.getOrDefault(FIELDS.keys.associateWith { "" } to null)

    fun width(b: ByteArray): Int {
        return imageMinSide(b)
    }

    /** What the scanner offers for songs [(uri, the song)]; albums with something to offer only. */
    fun plan(songs: List<Pair<String, Track>>, find: (String, String, String) -> Found?, info: (Long) -> AlbumInfo?,
             covers: (String, String, List<String>) -> List<String>, read: (String) -> Pair<Map<String, String>, ByteArray?> = ::read,
             widthOf: (ByteArray) -> Int = ::width, progress: (Int, Int) -> Unit = { _, _ -> }): List<Group> {
        class G(val key: String, val album: String, val artist: String) {
            val uris = ArrayList<String>(); val songs = ArrayList<Song>(); val found = LinkedHashMap<String, Int>(); var cover: ByteArray? = null; var read = false
        }
        val groups = LinkedHashMap<String, G>()
        val trackCache = HashMap<Pair<String, String>, Found?>(); val albumCache = HashMap<Long, AlbumInfo?>()
        songs.forEachIndexed { i, (uri, t) ->
            progress(i, songs.size)
            val (old, pic) = read(uri)
            val artist = old["artist"].orEmpty().ifEmpty { old["albumartist"].orEmpty() }.ifEmpty { t.artist }
            val title = old["title"].orEmpty().ifEmpty { t.title }
            val album = old["album"].orEmpty().ifEmpty { t.album }
            val m = trackCache.getOrPut(norm(lead(artist)) to norm(cleanTitle(title))) { runCatching { find(artist, title, album) }.getOrNull() }
            val al = m?.albumId?.let { id -> albumCache.getOrPut(id) { runCatching { info(id) }.getOrNull() } }
            val key = album + "|" + norm(lead(old["albumartist"].orEmpty().ifEmpty { artist }))
            val g = groups.getOrPut(norm(bareTitle(album)) + "|" + key.substringAfter('|')) {
                G(key, album, old["albumartist"].orEmpty().ifEmpty { lead(artist) })
            }
            g.uris += uri
            if (!g.read) { g.cover = pic; g.read = true }
            val changes = LinkedHashMap<String, Change>()
            if (m != null) {
                val new = HashMap(mapOf("title" to m.title, "artist" to m.artist))
                // A live recording found for a song that isn't one: its name without "(Live)", and not the live album's tags.
                val live = hasLive(m.title) && !hasLive(title)
                if (live) new["title"] = stripLive(m.title)
                if (al != null && !live) {
                    new += mapOf("album" to al.title, "albumartist" to al.artist, "date" to al.date, "genre" to al.genre,
                        "tracknumber" to (al.positions[m.id]?.toString() ?: ""))
                    if (al.cover.isNotEmpty()) g.found[al.cover] = (g.found[al.cover] ?: 0) + 1
                }
                for (f in FIELDS.keys) {
                    val o = old[f].orEmpty(); val n = new[f].orEmpty().trim()
                    if (n.isEmpty()) continue
                    if (f == "albumartist" && o.isEmpty() && norm(lead(old["artist"].orEmpty())) == norm(n)) continue
                    if (f == "tracknumber" && o.isNotEmpty() && al != null && differs("album", old["album"].orEmpty().ifEmpty { album }, al.title)) continue
                    if (o.isEmpty()) changes[f] = Change(o, n, true) else if (differs(f, o, n)) changes[f] = Change(o, n, false)
                }
            }
            if (changes.isNotEmpty()) g.songs += Song(uri, title, changes)
        }
        return groups.values.mapNotNull { g ->
            val pic = g.cover
            val w = pic?.let(widthOf) ?: 0
            val note = if (pic == null) "No cover in the files" else if (w in 1 until SMALL_COVER) "The cover is small ($w px)" else ""
            val found = g.found.entries.sortedByDescending { it.value }.map { it.key }
            val offered = if (note.isEmpty()) emptyList() else
                (found + runCatching { covers(g.artist, g.album, found) }.getOrDefault(emptyList())).distinct().take(3)
            if (g.songs.isEmpty() && offered.isEmpty()) null
            else Group(g.key, g.album, g.artist, g.uris, g.songs, offered, note)
        }.sortedWith(compareBy({ norm(it.artist) }, { norm(it.album) }))
    }

    /** What to write: ({uri: {field: value}}, {album key: cover url}). picked: {(uri, field): replace?} (missing: the default). */
    fun changes(groups: List<Group>, picked: Map<Pair<String, String>, Boolean>, covers: Map<String, String?>):
            Pair<Map<String, Map<String, String>>, Map<String, String>> {
        val tags = LinkedHashMap<String, MutableMap<String, String>>()
        for (g in groups) for (s in g.songs) for ((f, c) in s.changes)
            if (picked[s.uri to f] ?: c.replace) tags.getOrPut(s.uri) { LinkedHashMap() }[f] = c.new
        return tags to groups.mapNotNull { g -> covers[g.key]?.let { g.key to it } }.toMap()
    }

    /** The songs the scanner can change here: the app's own downloaded FLACs. */
    fun songs(): List<Pair<String, Track>> = Music.mySongs().mapNotNull { t -> Tags.editable(t)?.let { it to t } }.distinctBy { it.first }

    /** Write what was picked (blocking): a line for each thing done. */
    fun apply(tags: Map<String, Map<String, String>>, covers: Map<String, Pair<String, List<String>>>): List<String> {
        val out = ArrayList<String>()
        for ((uri, ch) in tags) {
            val name = java.net.URLDecoder.decode(uri.substringBefore('?').substringAfterLast('/').replace("+", "%2B"), "UTF-8").substringAfterLast('/').substringAfterLast(':')
            runCatching { Tags.write(Music.ctx, uri, ch) }
                .onSuccess { out += "$name: " + ch.entries.joinToString("; ") { (k, v) -> "${FIELDS[k] ?: k} → $v" } }
                .onFailure { out += "Couldn't change $name: ${it.message}" }
        }
        for ((key, pair) in covers) {
            val (url, uris) = pair
            val jpeg = runCatching { Covers.jpeg(httpBytes(url)) }.getOrElse { out += "Couldn't get the cover for ${key.substringBefore('|')}: ${it.message}"; null } ?: continue
            var n = 0
            for (u in uris) runCatching { Covers.write(Music.ctx, u, jpeg); n++ }.onFailure { out += "Couldn't put the cover in a song: ${it.message}" }
            if (n > 0) out += "New cover: $n song${if (n != 1) "s" else ""} of “${key.substringBefore('|')}”"
        }
        return out
    }
}
