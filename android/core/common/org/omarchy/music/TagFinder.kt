package org.omarchy.music

/** Find tags (the Tagger): a song's title, artist, album, year, track number and genre looked up online (Apple Music's
 *  catalogue, then Deezer's for what Apple hasn't got, the genre as downloads get it). Moods aren't touched. */
object TagFinder {
    /** (blocking) The tags found for the song now tagged title / artist / album: {field: value} in Tags.FIELDS' names
     *  ("title", "artist", "album", "date", "tracknumber", "genre"); empty if it wasn't found. */
    fun find(title: String, artist: String, album: String): Map<String, String> {
        val lead = splitArtists(artist).firstOrNull()?.trim()?.ifEmpty { null } ?: artist.trim()
        val out = LinkedHashMap<String, String>()
        runCatching { apple(lead, title, album) }.getOrNull()?.let { out.putAll(it) }
        if (out.size < 6) runCatching { deezer(lead, title, album) }.getOrNull()?.forEach { (k, v) -> out.putIfAbsent(k, v) }
        if (out.isNotEmpty() && "genre" !in out) runCatching { GenreLookup.of(out["artist"] ?: artist, out["title"] ?: title, out["album"] ?: album) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { out["genre"] = it }
        return out.filterValues { it.isNotBlank() }
    }

    private fun same(a: String?, b: String): Boolean {
        val x = norm(cleanTitle(a ?: "")); val y = norm(cleanTitle(b))
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.contains(y) || y.contains(x) || similar(x, y) > 0.85)
    }

    /** The best of a service's hits: the same title and artist, the album you have first, then an album over a single. */
    private fun <T> pick(hits: List<T>, album: String, albumOf: (T) -> String?): T? =
        hits.sortedWith(compareBy<T> { if (album.isNotBlank() && norm(albumOf(it) ?: "") == norm(album)) 0 else 1 }
            .thenBy { val a = albumOf(it) ?: ""; if (isSingleRelease(a) || isEp(a)) 1 else 0 }).firstOrNull()

    private fun apple(artist: String, title: String, album: String): Map<String, String>? {
        val res = httpJson("https://itunes.apple.com/search?term=${enc("$artist ${bareTitle(title).ifEmpty { title }}")}&media=music&entity=song&limit=25")
        val hits = res["results"].list.filter { same(it["trackName"].str, title) && same(it["artistName"].str, artist) }
        val h = pick(hits, album) { it["collectionName"].str } ?: return null
        return linkedMapOf(
            "title" to (h["trackName"].str ?: ""), "artist" to (h["artistName"].str ?: ""),
            "album" to (h["collectionName"].str ?: ""), "date" to (h["releaseDate"].str ?: "").take(4),
            "tracknumber" to (h["trackNumber"].long?.takeIf { it > 0 }?.toString() ?: ""),
            "genre" to (h["primaryGenreName"].str?.takeUnless { it.equals("Music", true) } ?: ""),
        ).filterValues { it.isNotBlank() }
    }

    private fun deezer(artist: String, title: String, album: String): Map<String, String>? {
        Pictures.deezerPace()
        val hits = httpJson("https://api.deezer.com/search/track?q=${enc("artist:\"$artist\" track:\"${cleanTitle(title)}\"")}&limit=15")["data"].list
            .filter { same(it["title"].str, title) && same(it.path("artist", "name").str, artist) }
        val h = pick(hits, album) { it.path("album", "title").str } ?: return null
        val out = linkedMapOf("title" to (h["title"].str ?: ""), "artist" to (h.path("artist", "name").str ?: ""),
            "album" to (h.path("album", "title").str ?: ""))
        runCatching {
            Pictures.deezerPace()
            val t = httpJson("https://api.deezer.com/track/${h["id"].long}")
            t["track_position"].long?.takeIf { it > 0 }?.let { out["tracknumber"] = it.toString() }
            (t.path("album", "release_date").str ?: t["release_date"].str)?.take(4)?.let { out["date"] = it }
        }
        return out.filterValues { it.isNotBlank() }
    }
}
