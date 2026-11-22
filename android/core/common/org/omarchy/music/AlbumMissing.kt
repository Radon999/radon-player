package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** An album's songs you haven't got (the bottom of its page): its tracklist read from Deezer, the ones not among
 *  yours ([MissingSongs]), and Download missing tracks…. [Music] keeps the names the screens call and hands on. */
object AlbumMissing {
    /** Tracklists read already, by (artist, album): read by several album pages at once, off the main thread. */
    private val seen = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, List<MissingSong>>()

    /** (blocking) The songs of your album (or EP: kind "ep") you haven't got, from its tracklist on Deezer, in order
     *  ([] if none or not found). */
    fun find(album: String, artist: String, tracks: List<Track>, kind: String = "album"): List<MissingSong> {
        val k = norm(artist) to norm(album)
        val all = seen[k] ?: run {
            val best = deezerRelease(album, artist, kind)
            val read = if (best == null) emptyList() else {
                val ts = httpJson("https://api.deezer.com/album/${best["id"].long}/tracks?limit=200")["data"] as? JsonArray
                ts.orEmpty().mapIndexed { i, t -> MissingSong((t["track_position"].long ?: (i + 1).toLong()).toInt(), t["title"].str ?: "",
                    t["title_short"].str ?: "", (t["duration"].long ?: 0L).toInt()) }
            }
            seen[k] = read
            read
        }
        // Yours: the album's songs here, and the artist's songs under any other album name (they aren't missing), or
        // under another name of the artist's (your "Forrest" is the album's "Forrest Frank").
        val yours = tracks + Music.mySongs().filter { SameArtist.alias(it.artist, artist) }
        return MissingSongs.from(all, tracks, yours)
    }

    /** Whether the album's tracklist was found (after [find]): an album not found has no count of what's missing. */
    fun listed(album: String, artist: String): Boolean = seen[norm(artist) to norm(album)]?.isNotEmpty() == true

    /** (blocking) The release on Deezer by its name and artist, or null. Its name is compared without its kind on the end
     *  (Deezer lists an EP as "Name" or "Name - EP"; see [MissingSongs.releaseName]); tried by the album search, then a looser search,
     *  then the artist's own list (albums, EPs and singles). Of those named so: the same full name first, then the kind wanted. */
    private fun deezerRelease(album: String, artist: String, kind: String): JsonElement? {
        val base = MissingSongs.releaseName(album)
        val want = norm(base)
        val lead = norm(artist)
        fun named(data: JsonElement?) =
            (data as? JsonArray).orEmpty().filter { norm(MissingSongs.releaseName(it["title"].str ?: "")) == want }
        fun best(list: List<JsonElement>) = list.sortedWith(compareBy(
            { if (norm(it["title"].str) == norm(album)) 0 else 1 },
            { if (it["record_type"].str.equals(kind, true)) 0 else 1 })).firstOrNull()
        fun byArtist(a: String?) = norm(a).let { lead.isEmpty() || (it.isNotEmpty() && (it == lead || lead in it || it in lead)) }
        if (want.isEmpty()) return null
        best(named(httpJson("https://api.deezer.com/search/album?q=" + enc("artist:\"$artist\" album:\"$base\"") + "&limit=10")["data"]))
            ?.let { return it }
        best(named(httpJson("https://api.deezer.com/search/album?q=" + enc("$artist $base") + "&limit=25")["data"])
            .filter { byArtist(it["artist"]["name"].str) })?.let { return it }
        if (lead.isEmpty()) return null
        val who = (httpJson("https://api.deezer.com/search/artist?q=" + enc(artist) + "&limit=5")["data"] as? JsonArray)
            .orEmpty().firstOrNull { norm(it["name"].str) == lead } ?: return null
        return best(named(httpJson("https://api.deezer.com/artist/${who["id"].long}/albums?limit=200")["data"]))
    }

    /** The missing songs found on a service already, to play as streams: by (artist, album, title). */
    private val streamed = java.util.concurrent.ConcurrentHashMap<String, Track>()

    /** (blocking) The missing songs as songs to stream: each found on Tidal (YouTube Music when Tidal isn't connected),
     *  in the album's order; one that isn't found is left out. They play, queue and go on playlists like any streamed
     *  song, so the rest of an album can be heard before any of it is downloaded. */
    fun streams(artist: String, album: String, missing: List<MissingSong>): List<Pair<MissingSong, Track>> {
        val src = Music.sources[if (Music.tidal.connected) "tidal" else "ytm"] ?: return emptyList()
        return missing.mapNotNull { m ->
            val k = norm(artist) + "|" + norm(album) + "|" + norm(m.title)
            (streamed[k] ?: runCatching { Music.matchSong(src, artist, m.title, m.secs).first }.getOrNull()
                ?.let { it.copy(album = it.album.ifEmpty { album }) }?.also { streamed[k] = it })?.let { m to it }
        }
    }

    /** (blocking) The songs of t's album (or EP) you haven't got, to stream; [] when it has no album or nothing is missing. */
    fun restOf(t: Track): List<Track> {
        val album = t.album.trim()
        if (album.isEmpty()) return emptyList()
        val artist = splitArtists(t.artist).firstOrNull() ?: t.artist
        val key = Covers.albumKey(t)
        val yours = Music.mySongs().filter { Covers.albumKey(it) == key }
        val kind = runCatching { if (yours.isEmpty()) "album" else Music.releaseKind(album, yours) }.getOrDefault("album")
        return streams(artist, album, find(album, artist, yours, kind)).map { it.second }
    }

    /** Listen later, only the songs you haven't got: for each album (or EP) among the songs picked, its songs that
     *  aren't yours go on Listen later, to hear and then download or leave (Listen later › Review). A small Discovery
     *  of one album. */
    fun laterRest(picked: List<Track>) {
        val albums = picked.filter { it.album.isNotBlank() }.distinctBy { Covers.albumKey(it) }.take(20)
        if (albums.isEmpty()) { Music.toast("That song has no album in its tags"); return }
        Music.toast(if (albums.size == 1) "Looking for the songs of “${albums[0].album}” you haven't got…" else "Looking through ${albums.size} albums for songs you haven't got…")
        Music.bg({ albums.flatMap { runCatching { restOf(it) }.getOrDefault(emptyList()) }.distinctBy { it.id } }) { got, err ->
            val found = got.orEmpty()
            when {
                err != null -> Music.toast("Couldn't look them up: ${err.message?.take(100)}")
                found.isEmpty() -> Music.toast(if (albums.size == 1) "You have every song of “${albums[0].album}” (or it wasn't found)" else "Nothing is missing from them (or they weren't found)")
                else -> ListenLater.add(found)
            }
        }
    }

    /** Download missing tracks…: the ones picked, found on Tidal (or YouTube Music) and saved. */
    fun download(artist: String, album: String, want: List<MissingSong>) {
        val key = if (Music.tidal.connected) "tidal" else "ytm"
        val src = Music.sources[key] ?: run { Music.toast("Connect Tidal or YouTube Music in Settings to download"); return }
        Music.toast("Finding ${want.size} song${if (want.size != 1) "s" else ""}…")
        Music.bg({ want.mapNotNull { m -> runCatching { Music.matchSong(src, artist, m.title, m.secs).first }.getOrNull()?.let { it.copy(album = it.album.ifEmpty { album }) } } }) { got, err ->
            val found = got.orEmpty()
            if (found.isNotEmpty()) Music.download(found)
            Music.toast(if (err != null) "Couldn't download: ${err.message?.take(100)}" else "Downloading ${found.size} of ${want.size}" +
                if (found.size < want.size) " (${want.size - found.size} not found)" else "")
        }
    }
}
