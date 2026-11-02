package org.omarchy.music

/** A song's genre for a download (Tidal doesn't give one): Apple's (iTunes search, no key), else the album's on Deezer,
 *  else the artist's top tag on Last.fm. "" if none of them knows it. */
object GenreLookup {
    private val cache = HashMap<String, String>()

    /** (blocking) The genre of title by artist (album: the release it's from, to pick the right one). */
    fun of(artist: String, title: String, album: String = ""): String {
        val lead = splitArtists(artist).firstOrNull()?.trim()?.ifEmpty { null } ?: artist.trim()
        val key = norm(lead) + "|" + norm(cleanTitle(title))
        synchronized(cache) { cache[key]?.let { return it } }
        val g = (runCatching { itunes(lead, title, album) }.getOrNull()?.ifEmpty { null }
            ?: runCatching { deezer(lead, title) }.getOrNull()?.ifEmpty { null }
            ?: runCatching { lastFm(lead) }.getOrNull()).orEmpty().trim()
        synchronized(cache) { cache[key] = g }
        return g
    }

    private fun same(a: String?, b: String) = norm(a ?: "").let { it.isNotEmpty() && (it == norm(b) || it.contains(norm(b)) || norm(b).contains(it)) }

    private fun itunes(artist: String, title: String, album: String): String {
        val res = httpJson("https://itunes.apple.com/search?term=${enc("$artist ${bareTitle(title).ifEmpty { title }}")}&media=music&entity=song&limit=15")
        val hits = res["results"].list.filter { same(it["artistName"].str, artist) && same(cleanTitle(it["trackName"].str ?: ""), cleanTitle(title)) }
        val best = hits.firstOrNull { album.isNotBlank() && norm(it["collectionName"].str ?: "") == norm(album) } ?: hits.firstOrNull()
        return best?.get("primaryGenreName").str?.takeUnless { it.equals("Music", true) }.orEmpty()
    }

    private fun deezer(artist: String, title: String): String {
        Pictures.deezerPace()
        val hit = httpJson("https://api.deezer.com/search/track?q=${enc("artist:\"$artist\" track:\"${cleanTitle(title)}\"")}&limit=5")["data"].list
            .firstOrNull { same(it.path("artist", "name").str, artist) } ?: return ""
        val id = hit.path("album", "id").long ?: return ""
        Pictures.deezerPace()
        return httpJson("https://api.deezer.com/album/$id").path("genres", "data")[0]["name"].str.orEmpty()
    }

    private fun lastFm(artist: String): String {
        if (LastFm.key.isEmpty()) return ""
        val tag = LastFm.call("artist.getTopTags", params = mapOf("artist" to artist, "autocorrect" to 1)).path("toptags", "tag")[0]["name"].str ?: return ""
        // "hip hop" -> "Hip Hop", as genres are written
        return tag.split(' ', '-').joinToString(if ('-' in tag) "-" else " ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
