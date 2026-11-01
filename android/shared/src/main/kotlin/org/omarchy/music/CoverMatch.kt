package org.omarchy.music

// Finding a cover for a song that isn't yours: which of a service's search results is the song asked for.

private fun coverName(n: String) = norm(n).replace(Regex("[\\W_]+"), "").ifEmpty { norm(n) }

/** How well a service's song (title, artist) fits the one a cover is wanted for: 2 the same song name, 1 the same
 *  name without its brackets or " - Demo"-style tail (another version of it), 0 not it or by someone else. */
fun coverFit(wantTitle: String, wantArtist: String, title: String?, artist: String?): Int {
    if (title.isNullOrBlank() || artist.isNullOrBlank()) return 0
    val lead = statsKey(wantTitle, wantArtist).second
    val theirs = splitArtists(artist).map { coverName(it) } + coverName(artist)
    if (lead.isEmpty() || theirs.none { it.isNotEmpty() && (it == lead || similar(it, lead) >= 0.85) }) return 0
    if (norm(cleanTitle(title)) == norm(cleanTitle(wantTitle))) return 2
    val a = norm(bareTitle(title)); val b = norm(bareTitle(wantTitle))
    return if (a.isNotEmpty() && a == b) 1 else 0
}

/** Of a service's results (title, artist, album, cover), the cover of the one that fits best: the same song name
 *  first, then the one on the same album; "" if none fits or the one that fits has no cover. */
fun bestCover(wantTitle: String, wantArtist: String, wantAlbum: String, found: List<List<String?>>): String {
    val album = norm(cleanTitle(wantAlbum))
    return found.mapNotNull { r ->
        val fit = coverFit(wantTitle, wantArtist, r.getOrNull(0), r.getOrNull(1))
        val cover = r.getOrNull(3).orEmpty()
        if (fit == 0 || cover.isEmpty()) null
        else Triple(fit, album.isNotEmpty() && norm(cleanTitle(r.getOrNull(2).orEmpty())) == album, cover)
    }.sortedWith(compareBy({ -it.first }, { !it.second })).firstOrNull()?.third ?: ""
}

/** An iTunes cover address at 600×600 (the search gives 100×100). */
fun itunesBig(url: String): String = url.replace(Regex("/\\d+x\\d+(bb)?\\.(jpg|png|webp)$"), "/600x600bb.jpg")
