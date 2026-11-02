package org.omarchy.music

/** Settings › Albums, EPs and singles: each release of yours looked up on Deezer (else Apple Music) to learn what it
 *  really is, an album, an EP or a single, and its name made to say so (" - Single", " - EP"; an album's name without
 *  them), so nothing shows in two of the three tabs. Releases no service knows are asked about (unsure). */
object AlbumTruth {
    /** A release of yours: what it's called and is now, what a service says it is, the name it'd get. */
    data class Row(val album: String, val artist: String, val songs: List<Track>, val now: String, val truth: String?,
                   val source: String, val tracks: Int?) {
        val unsure get() = truth == null
    }

    private val SUFFIX = Regex("\\s*[-–]\\s*(?:single|ep)\\s*$|\\s*[(\\[](?:single|ep)[)\\]]\\s*$", RegexOption.IGNORE_CASE)

    /** What a name says a release is. */
    fun kindOf(name: String, title: String = ""): String = when {
        isEp(name) -> "ep"
        isSingle(name, title) -> "single"
        else -> "album"
    }

    /** The name a release gets as kind: its own name, " - Single" or " - EP" after it. */
    fun named(album: String, kind: String): String {
        val base = SUFFIX.replace(album, "").trim().ifEmpty { album.trim() }
        return when (kind) { "single" -> "$base - Single"; "ep" -> "$base - EP"; else -> base }
    }

    private fun lead(a: String) = a.split(Regex(",|;| feat\\.? | ft\\.? | featuring | with | & ", RegexOption.IGNORE_CASE)).first().trim()

    private fun deezer(url: String) = runCatching { Pictures.deezerPace(); httpJson(url) }.getOrNull()

    /** (kind, tracks, source) from Deezer, else Apple Music; null if neither knows it. */
    fun lookUp(album: String, artist: String): Triple<String, Int?, String>? {
        val bare = bareTitle(SUFFIX.replace(album, "")).ifEmpty { album }
        val q = "artist:\"${lead(artist)}\" album:\"$bare\""
        deezer("https://api.deezer.com/search/album?q=${enc(q)}&limit=10")?.get("data")?.list?.firstOrNull {
            similar(norm(bareTitle(it["title"].str ?: "")), norm(bare)) > 0.9 && similar(norm(lead(it["artist"]["name"].str ?: "")), norm(lead(artist))) > 0.85
        }?.let { d ->
            val kind = when (d["record_type"].str) { "single" -> "single"; "ep" -> "ep"; "album", "compile" -> "album"; else -> null }
            if (kind != null) return Triple(kind, d["nb_tracks"].long?.toInt(), "Deezer")
        }
        val res = runCatching { httpJson("https://itunes.apple.com/search?term=${enc("${lead(artist)} $bare")}&entity=album&limit=15") }.getOrNull()
        res?.get("results")?.list?.firstOrNull {
            similar(norm(bareTitle(SUFFIX.replace(it["collectionName"].str ?: "", ""))), norm(bare)) > 0.9 &&
                similar(norm(lead(it["artistName"].str ?: "")), norm(lead(artist))) > 0.85
        }?.let { r ->
            val name = r["collectionName"].str ?: ""
            val kind = when { Regex("\\s-\\sSingle$").containsMatchIn(name) -> "single"; Regex("\\s-\\sEP$").containsMatchIn(name) -> "ep"; else -> "album" }
            return Triple(kind, r["trackCount"].long?.toInt(), "Apple Music")
        }
        return null
    }

    /** Your releases (the files the app can change), each looked up (blocking): the ones whose name doesn't match what
     *  they are, and the ones no service knows. */
    fun scan(progress: (String) -> Unit = {}): List<Row> {
        val songs = Music.mySongs().filter { it.album.isNotBlank() && Tags.editableAny(it) != null }
        val groups = songs.groupBy { Covers.albumKey(it) }.values.toList()
        val out = ArrayList<Row>()
        groups.forEachIndexed { i, g ->
            progress("Looking up your releases… ${i + 1} of ${groups.size}")
            val first = g.first()
            val now = kindOf(first.album, first.title)
            val found = runCatching { lookUp(first.album, first.artist) }.getOrNull()
            // A service's single with more songs than one here, or an album you have one song of, is left to the name.
            val truth = found?.first
            if (truth == null) { if (now != "album" || g.size == 1) out += Row(first.album, first.artist, g, now, null, "", null); return@forEachIndexed }
            if (named(first.album, truth) != first.album) out += Row(first.album, first.artist, g, now, truth, found.third, found.second)
        }
        return out.sortedWith(compareBy({ norm(it.artist) }, { norm(it.album) }))
    }
}
