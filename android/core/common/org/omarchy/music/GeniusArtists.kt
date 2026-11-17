package org.omarchy.music

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Genius as the judge of whose a song is. A streaming service sometimes puts two bands with the same name under one
 *  artist (their songs and albums all mixed together); Genius keeps them as two artists, each with its own list of
 *  songs. You say which Genius artist a name means (an artist's page: "Whose songs?"), and that artist's song list
 *  decides what the page shows: only the songs and releases that are theirs, the rest left out (and shown on
 *  request). The list also gives every song they have, whether a service has it or not. The rules are [GeniusRules]. */
object GeniusArtists {
    private fun key(name: String) = "artist_genius:" + norm(name)
    private val listSer = ListSerializer(ListSerializer(String.serializer()))

    /** The Genius artist picked for this name (its id there), or null: no judge, the page shows all the service has. */
    fun picked(name: String): String? = Prefs.text(key(name)).ifEmpty { null }

    fun pick(name: String, id: String?) {
        Prefs.setText(key(name), id.orEmpty())
        Music.artistsVersion++
    }

    /** (blocking) The artists Genius has under this name ("HOAX", "Hoax (UK)", "HOAX (Techno)"), each with a song it
     *  is known for when there is more than one to choose from. */
    fun choices(name: String): List<ArtistChoice> {
        val hits = httpJson("https://genius.com/api/search/artist?q=${enc(name)}&per_page=20").path("response", "sections", 0, "hits").list
        val named = hits.map { it["result"] }.filter { GeniusRules.sameNamed(name, it["name"].str ?: "") }.take(8)
        return named.mapNotNull { a ->
            val id = a["id"].long?.toString() ?: return@mapNotNull null
            val known = if (named.size < 2) null else runCatching {
                httpJson("https://genius.com/api/artists/$id/songs?per_page=1&page=1&sort=popularity").path("response", "songs").list.firstOrNull()?.get("title").str
            }.getOrNull()
            ArtistChoice(id, a["name"].str ?: name, a["image_url"].str ?: "", ArtistLookupRules.knownFor(known))
        }
    }

    private fun file
(id: String) = File(Music.ctx.filesDir, "genius-artist-${id.filter { it.isLetterOrDigit() }}.json")

    /** (blocking) Every song Genius lists for the artist, each once (Genius has a page for the album's, the single's
     *  and the EP's version of a song: one is enough), without the songs they only feature on. Kept on this device for a day. */
    fun songs(id: String): List<GeniusRules.Hit> {
        val f = file(id)
        if (f.isFile && System.currentTimeMillis() - f.lastModified() < 24 * 3600_000L) runCatching {
            JSON.decodeFromString(listSer, f.readText()).mapNotNull { r -> if (r.size == 3) GeniusRules.Hit(r[0], r[1], r[2]) else null }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        val all = ArrayList<GeniusRules.Hit>()
        var page: Long? = 1
        var pages = 0
        while (page != null && pages++ < 30) {
            val res = httpJson("https://genius.com/api/artists/$id/songs?per_page=50&page=$page&sort=popularity")["response"]
            for (s in res["songs"].list) if (s.path("primary_artist", "id").long?.toString() == id) all += GeniusRules.Hit(s["title"].str ?: "", s.path("primary_artist", "name").str ?: s["artist_names"].str ?: "",
                s["song_art_image_thumbnail_url"].str ?: "")
            page = res["next_page"].long
        }
        val out = GeniusRules.artistSongs(all)
        if (out.isNotEmpty()) runCatching { writeAtomic(f, JSON.encodeToString(listSer, out.map { listOf(it.title, it.artist, it.art) })) }
        return out
    }

    private val albumLists = HashMap<String, List<Album>>()

    /** (blocking) The releases Genius lists for the artist (albums, EPs, singles with a page of their own), the
     *  year under each. Remembered while the app runs. */
    fun albums(id: String): List<Album> {
        synchronized(albumLists) { albumLists[id] }?.let { return it }
        val out = ArrayList<Album>()
        var page: Long? = 1
        var pages = 0
        while (page != null && pages++ < 6) {
            val res = httpJson("https://genius.com/api/artists/$id/albums?per_page=50&page=$page")["response"]
            for (a in res["albums"].list) {
                val aid = a["id"].long ?: continue
                val name = a["name"].str?.takeIf { it.isNotBlank() } ?: continue
                out += Album(aid.toString(), name, a.path("release_date_components", "year").long?.toString() ?: "",
                    (a["cover_art_thumbnail_url"].str ?: "").let { if ("default_cover" in it) "" else it })
            }
            page = res["next_page"].long
        }
        return out.also { synchronized(albumLists) { albumLists[id] = it } }
    }

    /** A service's releases gone through with Genius's list: the releases that aren't theirs (their ids), and the
     *  songs of the ones that are. */
    class Sorted(val notTheirs: Set<String>, val songs: List<Track>)
    private val sorted = HashMap<String, Sorted>()

    /** (blocking) Go through the artist's releases on the service (each asked for in turn: progress says how far)
     *  and tell which are theirs by Genius's list: a release is theirs when half its songs or more are on it. One
     *  that can't be had is left in. Remembered while the app runs. */
    fun sort(info: ArtistInfo, name: String, hits: List<GeniusRules.Hit>, progress: (Int, Int) -> Unit = { _, _ -> }): Sorted {
        val src = Music.sources[info.source] ?: return Sorted(emptySet(), emptyList())
        val releases = info.albums + info.also
        val key = "${info.source}:${info.id}:${norm(name)}:${hits.size}:${releases.size}"
        synchronized(sorted) { sorted[key] }?.let { return it }
        val theirs = GeniusRules.judge(hits)
        val own = info.albums.map { it.id }.toSet()
        val got = ArrayList<Pair<Album, List<Track>>>()
        val out = HashSet<String>()
        for ((i, al) in releases.withIndex()) {
            progress(i, releases.size)
            val tracks = ArtistLookups.releaseSongs(info.source, al.id) ?: continue
            if (al.id in own && !GeniusRules.releaseIsTheirs(tracks.map { it.title }, theirs)) out += al.id else got += al to tracks
        }
        progress(releases.size, releases.size)
        val who = info.name.ifEmpty { name }
        val songs = ArtistLookupRules.everySong(got) { t -> (Music.creditsArtist(t, who) || Music.creditsArtist(t, name)) && theirs(t.title) }
        return Sorted(out, songs).also { synchronized(sorted) { sorted[key] = it } }
    }

    /** (blocking) Every song of theirs: the ones on their releases on the service (your own copy of each one you
     *  have), then the ones only Genius lists (found on Tidal or YouTube when played, if either has it). */
    fun every(found: Sorted, hits: List<GeniusRules.Hit>): List<Track> {
        val mine = Music.songIndex()
        val rest = GeniusRules.missing(hits, found.songs.map { it.title }).map { h -> Music.fmSong(h.title, h.artist, h.art, mine) }
        return (Music.yourCopies(found.songs) + rest).distinctBy { it.id }
    }
}
