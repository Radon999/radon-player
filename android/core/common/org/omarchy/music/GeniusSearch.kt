package org.omarchy.music

/** Searching Genius (the search bar's menu): Genius lists nearly every song there is, so a song none of the services
 *  has can still be found by name. What it finds are names only: Genius has no music. A song you have plays from your
 *  copy; any other is looked for on Tidal and YouTube when you play it, and says so if no service has it (then it's
 *  one to get somewhere else and put in your music folder). */
object GeniusSearch {
    /** (blocking) The songs Genius knows for q, your own copy in place of each one you have. */
    fun find(q: String): Found {
        val res = httpJson("https://genius.com/api/search/song?q=${enc(q)}&per_page=30")
        val hits = res.path("response", "sections", 0, "hits").list.map { it["result"] }.map { r ->
            GeniusRules.Hit(r["title"].str ?: "", r.path("primary_artist", "name").str ?: r["artist_names"].str ?: "",
                r["song_art_image_thumbnail_url"].str ?: r["header_image_thumbnail_url"].str ?: "")
        }
        val mine = Music.songIndex()
        return Found(GeniusRules.songs(hits).map { h -> Music.fmSong(h.title, h.artist, h.art, mine) })
    }
}
