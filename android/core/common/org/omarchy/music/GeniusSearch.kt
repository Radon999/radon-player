package org.omarchy.music

import kotlinx.serialization.json.JsonElement
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Searching Genius (the search bar's menu): Genius lists nearly every song there is, so a song none of the services
 *  has can still be found by name. It finds artists, releases (albums, EPs) and songs, as a service's search does; a
 *  song that is on a release is shown as that release (open it for its songs), and only a song released by itself
 *  shows as a song. What it finds are names only: Genius has no music. A song you have plays from your copy; any other
 *  is looked for on Tidal and YouTube when you play it, and says so if no service has it. The rules: [GeniusRules]. */
object GeniusSearch {
    private const val API = "https://genius.com/api"

    private fun hits(kind: String, q: String, n: Int): List<JsonElement?> =
        httpJson("$API/search/$kind?q=${enc(q)}&per_page=$n").path("response", "sections", 0, "hits").list.map { it["result"] }

    private fun hit(r: JsonElement?) = GeniusRules.Hit(r["title"].str ?: "", r.path("primary_artist", "name").str ?: r["artist_names"].str ?: "",
        r["song_art_image_thumbnail_url"].str ?: r["header_image_thumbnail_url"].str ?: "")

    private fun release(a: JsonElement?): GeniusRules.Release? {
        val id = a["id"].long ?: return null
        val name = a["name"].str?.takeIf { it.isNotBlank() } ?: return null
        return GeniusRules.Release(id.toString(), name, a.path("artist", "name").str ?: a["primary_artist_names"].str ?: "",
            (a["cover_art_thumbnail_url"].str ?: a["cover_art_url"].str ?: "").let { if ("default_cover" in it) "" else it },
            a.path("release_date_components", "year").long?.toString() ?: "")
    }

    /** Several questions asked at once (each answer, or null for one that failed or took too long). */
    private fun <T> together(jobs: List<() -> T>): List<T?> {
        if (jobs.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(minOf(6, jobs.size))
        try {
            val asked = jobs.map { j -> pool.submit(Callable { runCatching(j).getOrNull() }) }
            val until = System.currentTimeMillis() + 25_000
            return asked.map { f -> runCatching { f.get((until - System.currentTimeMillis()).coerceAtLeast(1), TimeUnit.MILLISECONDS) }.getOrNull() }
        } finally { pool.shutdownNow() }
    }

    /** (blocking) What Genius knows for q: artists, releases, and the songs released by themselves, your own copy in
     *  place of each song you have. */
    fun find(q: String): Found {
        val found = together(listOf({ hits("song", q, 20) }, { hits("artist", q, 8) }, { hits("album", q, 12) }))
        if (found.all { it == null }) throw java.io.IOException("Genius didn't answer")
        val ids = HashMap<GeniusRules.Hit, Long>()
        val songs = GeniusRules.songs(found[0].orEmpty().map { r -> hit(r).also { h -> r["id"].long?.let { ids.putIfAbsent(h, it) } } })
        // Each song's release, asked for one by one (the search doesn't say): a song on one shows as that release
        val on = together(songs.map { h -> { ids[h]?.let { release(httpJson("$API/songs/$it").path("response", "song", "album")) } } })
        val (alone, releases) = GeniusRules.fold(songs.mapIndexed { i, h -> h to on[i] }, found[2].orEmpty().mapNotNull { release(it) })
        val mine = Music.songIndex()
        return Found(alone.map { h -> Music.fmSong(h.title, h.artist, h.art, mine) },
            artists = found[1].orEmpty().mapNotNull { a ->
                val name = a["name"].str?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Album(a["id"].long?.toString() ?: name, name, "Artist", (a["image_url"].str ?: "").let { if ("default_avatar" in it) "" else it })
            },
            albums = releases.map { r -> Album(r.id, r.title, listOf(r.artist, r.year).filter { it.isNotEmpty() }.joinToString(" · "), r.art) })
    }

    /** (blocking) A release's songs in its order, your own copy in place of each one you have. */
    fun albumSongs(al: Album): List<Track> {
        val all = ArrayList<GeniusRules.Hit>()
        var page: Long? = 1
        var pages = 0
        while (page != null && pages++ < 6) {
            val res = httpJson("$API/albums/${al.id}/tracks?per_page=50&page=$page")["response"]
            for (t in res["tracks"].list) all += hit(t["song"])
            page = res["next_page"].long
        }
        val mine = Music.songIndex()
        return all.filter { GeniusRules.isSong(it) }.map { h -> Music.fmSong(h.title, h.artist, h.art.ifEmpty { al.thumb }, mine, al.title) }.distinctBy { it.id }
    }
}
