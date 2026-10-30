package org.omarchy.music

import kotlinx.serialization.json.JsonElement

/** Singles › Single scan: where each of your singles belongs (the album or EP it's on), from Deezer, else Apple Music,
 *  as the desktop's single_home. Written only into the app's own downloaded FLACs (Android keeps your own files to
 *  other apps), like the tag scanner. */
object SingleScan {
    data class Home(val album: String, val artist: String, val kind: String, val n: Int?, val date: String,
                    val cover: String, val source: String)

    data class Row(val t: Track, val uri: String, val home: Home, val releases: List<String>)

    private fun lead(a: String) = a.split(Regex(",|;| feat\\.? | ft\\.? | featuring | with ", RegexOption.IGNORE_CASE)).first().trim()

    private val LIVE_ALBUM = Regex("\\blive (?:at|from|in|on)\\b|\\b(?:unplugged|in concert)\\b", RegexOption.IGNORE_CASE)
    private val BEST_OF = Regex("\\bsessions?\\b|\\bgreatest hits\\b|\\bbest of\\b", RegexOption.IGNORE_CASE)

    /** The best home of found: an album over an EP, its deluxe or extended edition over the plain one, the first one out
     *  over a reissue; never a single, a compilation, a best-of or sessions record, or a live album for a studio song. */
    fun pick(title: String, found: List<Home>): Home? {
        val good = found.mapNotNull { h ->
            val name = h.album.trim()
            // A release named as the song is still its home when the service calls it an EP or album ("Bangs" on the Bangs EP).
            val sameName = isSingle(name, title) && !Regex("\\bsingles?\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)
            if (name.isEmpty() || h.kind == "single" || h.kind == "compile" || (isSingle(name, title) && !(sameName && h.kind in setOf("ep", "album") && h.source == "Deezer")))
                return@mapNotNull null
            if ((hasLive(name) || LIVE_ALBUM.containsMatchIn(name)) && !hasLive(title)) return@mapNotNull null
            val k = when {
                h.kind == "ep" || isEp(name) -> 1
                BEST_OF.containsMatchIn(name) -> return@mapNotNull null
                else -> albumKind(name, title)
            }
            k to h
        }
        val best = good.maxWithOrNull(compareBy<Pair<Int, Home>>({ it.first },
            { -(it.second.date.filter { c -> c.isDigit() }.take(8).toLongOrNull() ?: 99999999L) }))?.second ?: return null
        return best.copy(kind = if (best.kind == "ep" || isEp(best.album)) "ep" else "album")
    }

    private fun deezer(url: String): JsonElement {
        Pictures.deezerPace()
        val d = httpJson(url)
        if (d["error"]["code"].str == "4") { Thread.sleep(5000); Pictures.deezerPace(); return httpJson(url) }
        return d
    }

    private fun matches(t: String, a: String, title: String, artist: String) =
        minOf(similar(norm(cleanTitle(t)), norm(cleanTitle(title))),
            maxOf(similar(norm(lead(a)), norm(lead(artist))), similar(norm(a), norm(artist)))) >= 0.85

    fun deezerHomes(artist: String, title: String): List<Home> {
        val q = "artist:\"${lead(artist)}\" track:\"${bareTitle(title).ifEmpty { title }}\""
        val res = deezer("https://api.deezer.com/search?q=${enc(q)}&limit=25")
        if (res["error"] != null) error("Deezer: ${res["error"]}")
        val seen = HashSet<Long>(); val out = ArrayList<Home>()
        for (d in res["data"].list) {
            val aid = d["album"]["id"].long ?: continue
            if (!matches(d["title"].str ?: "", d["artist"]["name"].str ?: "", title, artist) || aid in seen ||
                Regex("\\bsingles?\\b", RegexOption.IGNORE_CASE).containsMatchIn(d["album"]["title"].str ?: "")) continue
            seen += aid
            if (seen.size > 8) break
            val al = deezer("https://api.deezer.com/album/$aid")
            if (al["id"] == null) continue
            var tracks = al["tracks"]["data"].list
            if ((al["nb_tracks"].long ?: 0) > tracks.size) tracks = deezer("https://api.deezer.com/album/$aid/tracks?limit=300")["data"].list
            val n = tracks.indexOfFirst { it["id"].long == d["id"].long }.takeIf { it >= 0 }?.let { i -> tracks[i]["track_position"].long?.toInt() ?: (i + 1) }
            out += Home(al["title"].str ?: "", al["artist"]["name"].str ?: artist, al["record_type"].str ?: "", n,
                al["release_date"].str ?: "", al["cover_xl"].str ?: al["cover_big"].str ?: "", "Deezer")
        }
        return out
    }

    fun appleHomes(artist: String, title: String): List<Home> {
        val res = httpJson("https://itunes.apple.com/search?term=${enc("${lead(artist)} ${bareTitle(title).ifEmpty { title }}")}&entity=song&limit=25")
        val seen = HashSet<String>(); val out = ArrayList<Home>()
        for (r in res["results"].list) {
            val name = r["collectionName"].str ?: continue
            if (!matches(r["trackName"].str ?: "", r["artistName"].str ?: "", title, artist) || !seen.add(name)) continue
            val various = norm(r["collectionArtistName"].str ?: "") in setOf("various artists", "various", "va")
            val kind = when {
                various -> "compile"
                Regex("\\s-\\sSingle$").containsMatchIn(name) -> "single"
                Regex("\\s-\\sEP$").containsMatchIn(name) -> "ep"
                else -> "album"
            }
            out += Home(name, r["collectionArtistName"].str ?: r["artistName"].str ?: artist, kind, r["trackNumber"].long?.toInt(),
                (r["releaseDate"].str ?: "").take(10), (r["artworkUrl100"].str ?: "").replace("100x100bb", "1200x1200bb"), "Apple Music")
        }
        return out
    }

    /** Where a single belongs, from Deezer, else Apple Music; null if neither has an album or EP. Throws only if both
     *  couldn't be asked. */
    fun home(artist: String, title: String): Home? {
        val errors = ArrayList<Throwable>()
        for (look in listOf<(String, String) -> List<Home>>({ a, t -> deezerHomes(a, t) }, { a, t -> appleHomes(a, t) })) {
            val h = try { pick(title, look(artist, title)) } catch (e: Exception) { errors += e; continue }
            if (h != null) return h
        }
        if (errors.size == 2) throw errors[0]
        return null
    }

    /** An artist's albums and EPs on Deezer, for the dropdown. */
    fun releases(artist: String): List<String> = runCatching {
        val want = norm(lead(artist))
        val hit = deezer("https://api.deezer.com/search/artist?q=${enc(lead(artist))}&limit=5")["data"].list
            .firstOrNull { norm(it["name"].str ?: "") == want } ?: return emptyList()
        deezer("https://api.deezer.com/artist/${hit["id"].long}/albums?limit=200")["data"].list
            .filter { it["record_type"].str in setOf("album", "ep") }.mapNotNull { it["title"].str }.distinct()
    }.getOrDefault(emptyList())

    /** Your singles (the Singles tab's songs) the scan can change: the app's own FLACs, not left alone before. */
    fun songs(): List<Pair<String, Track>> = Music.mySongs().mapNotNull { t -> Tags.editable(t)?.let { it to t } }
        .distinctBy { it.first }.filter { (u, t) -> (t.album.isEmpty() || isSingle(t.album, t.title)) && u !in Music.singlesKept }

    /** Look them all up (blocking): the ones on an album or EP, and how many couldn't be looked up. */
    fun scan(): Pair<List<Row>, Int> {
        val rows = ArrayList<Row>(); var failed = 0; val rel = HashMap<String, List<String>>()
        for ((u, t) in songs()) {
            val artist = lead(t.artist)
            val h = try { home(artist, t.title) } catch (e: Exception) { failed++; continue } ?: continue
            if (norm(h.album) == norm(t.album)) continue
            rows += Row(t, u, h, rel.getOrPut(norm(artist)) { releases(artist) })
        }
        return rows to failed
    }

    /** The Tagger's Auto collect (blocking): each song put on the release it belongs to (a deluxe edition over the
     *  album, the album over an EP, an EP over a single). A line for each. */
    fun collect(songs: List<Track>): List<String> {
        val todo = ArrayList<Pair<Row, String>>(); val out = ArrayList<String>()
        for (t in songs) {
            val uri = Tags.editableAny(t) ?: continue
            val h = try { home(lead(t.artist), t.title) } catch (e: Exception) { out += "${t.title}: couldn't look it up (${e.message})"; continue }
            when {
                h == null -> out += "${t.title}: no album or EP found for it"
                norm(h.album) == norm(t.album) -> out += "${t.title}: already on “${h.album}”"
                else -> todo += Row(t, uri, h, emptyList()) to h.album
            }
        }
        return out + apply(todo)
    }

    /** Put each on its album (blocking): its album, album artist, track number, year and the album's cover; a typed or
     *  picked album (Other album) gets its number and cover from Deezer when it's there. A line for each. */
    fun apply(todo: List<Pair<Row, String>>): List<String> {
        val out = ArrayList<String>()
        for ((r, to) in todo) {
            var h = r.home
            if (to != h.album) h = runCatching {
                val q = "artist:\"${lead(r.t.artist)}\" album:\"${bareTitle(to).ifEmpty { to }}\""
                val al = deezer("https://api.deezer.com/search/album?q=${enc(q)}&limit=5")["data"].list
                    .firstOrNull { similar(norm(bareTitle(it["title"].str ?: "")), norm(bareTitle(to))) > 0.85 }
                val tracks = al?.let { deezer("https://api.deezer.com/album/${it["id"].long}/tracks?limit=300")["data"].list } ?: emptyList()
                val i = tracks.indexOfFirst { similar(norm(cleanTitle(it["title"].str ?: "")), norm(cleanTitle(r.t.title))) > 0.9 }
                Home(to, al["artist"]["name"].str ?: lead(r.t.artist), "album",
                    if (i >= 0) tracks[i]["track_position"].long?.toInt() ?: (i + 1) else null, "",
                    al?.let { it["cover_xl"].str ?: it["cover_big"].str } ?: "", "")
            }.getOrDefault(Home(to, lead(r.t.artist), "album", null, "", "", ""))
            val ch = LinkedHashMap<String, String>()
            ch["album"] = h.album; ch["albumartist"] = h.artist
            h.n?.let { ch["tracknumber"] = it.toString() }
            if (h.date.length >= 4) ch["date"] = h.date.take(4)
            val c = try { Tags.write(Music.ctx, r.uri, ch) } catch (e: Exception) { out += "Couldn't change ${r.t.title}: ${e.message}"; continue }
            val album = c["ALBUM"]?.firstOrNull() ?: h.album
            Music.lib.items.values.filter { it.file == r.uri }.forEach { e -> Music.lib.update(e.id) { it.copy(album = album) } }
            var line = "${r.t.title}: “${r.t.album.ifEmpty { "(no album)" }}” → “${h.album}”"
            if (h.cover.isNotEmpty()) runCatching { Covers.write(Music.ctx, r.uri, Covers.jpeg(httpBytes(h.cover))) }
                .onFailure { line += " (its cover couldn't be changed)" }
            out += line
        }
        return out
    }
}
