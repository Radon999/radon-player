package org.omarchy.music

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A Deezer song (from Deezer's public API, or its website's own answers) as our Track. Its audio comes from Tidal (by
 *  ISRC, in FLAC) or YouTube, as Spotify's and Apple Music's do: Deezer's own is locked to its apps. */
fun trackFromDeezer(j: JsonElement): Track {
    val gw = j["SNG_ID"] != null   // the website's own shape
    val id = if (gw) j["SNG_ID"].str else j["id"].str
    val pic = if (gw) j["ALB_PICTURE"].str?.let { "https://e-cdns-images.dzcdn.net/images/cover/$it/250x250-000000-80-0-0.jpg" }
        else j.path("album", "cover_medium").str ?: j["md5_image"].str?.let { "https://e-cdns-images.dzcdn.net/images/cover/$it/250x250-000000-80-0-0.jpg" }
    return Track(id = "dz$id", source = "deezer",
        title = (if (gw) listOfNotNull(j["SNG_TITLE"].str, j["VERSION"].str?.takeIf { it.isNotBlank() }).joinToString(" ") else j["title"].str) ?: "Unknown",
        artist = (if (gw) j["ARTISTS"].list.mapNotNull { it["ART_NAME"].str }.ifEmpty { listOfNotNull(j["ART_NAME"].str) }.joinToString(", ")
            else j.path("artist", "name").str) ?: "Unknown artist",
        album = (if (gw) j["ALB_TITLE"].str else j.path("album", "title").str) ?: "",
        duration = ((if (gw) j["DURATION"] else j["duration"]).long ?: 0).toDouble(),
        isrc = if (gw) j["ISRC"].str else j["isrc"].str,
        thumb = pic ?: "")
}

/** Deezer: search, artists and albums from its public API; signed in (the "arl" cookie of a Deezer sign-in), your
 *  favourite tracks and playlists, and liking songs. Songs play and download from Tidal or YouTube. */
class Deezer(private val arl: String?) : Source {
    override val key = "deezer"
    override val name = "Deezer"
    override val hint = "Sign in to Deezer in Settings (⚙)\nto see your favourites and playlists."
    private val api = "https://api.deezer.com/"
    private var token = ""
    var user: Long = 0
        private set
    var userName = ""
        private set
    override val signedIn get() = user > 0

    init {
        if (!arl.isNullOrBlank()) runCatching {
            val d = gw("deezer.getUserData", null)
            user = d.path("USER", "USER_ID").long ?: 0
            userName = d.path("USER", "BLOG_NAME").str ?: ""
            token = d["checkForm"].str ?: ""
        }
    }

    /** A call to Deezer's website's own API, as the signed-in you. */
    private fun gw(method: String, body: JsonElement?): JsonElement {
        val url = "https://www.deezer.com/ajax/gw-light.php?method=$method&input=3&api_version=1.0&api_token=${enc(token)}"
        val r = httpJson(url, mapOf("Cookie" to "arl=$arl", "User-Agent" to DESKTOP_UA), jsonBody(body ?: buildJsonObject {}), "POST")
        val err = r["error"]
        if (err is kotlinx.serialization.json.JsonObject && err.isNotEmpty()) error("Deezer: ${err.values.firstOrNull()?.let { (it as? JsonPrimitive)?.content ?: it.toString() }}")
        return r["results"] ?: error("Deezer sent nothing")
    }

    private fun pub(path: String): JsonElement = httpJson(api + path, mapOf("User-Agent" to DESKTOP_UA)).also {
        if (it["error"] != null) error("Deezer: ${it.path("error", "message").str ?: "error"}")
    }

    override fun search(q: String): List<Track> = pub("search?q=${enc(q)}&limit=30")["data"].list.map { trackFromDeezer(it) }

    override fun find(q: String): Found {
        val songs = search(q)
        val artists = runCatching { pub("search/artist?q=${enc(q)}&limit=6")["data"].list.map {
            Album("dzartist:${it["id"].str}", it["name"].str ?: "", "Artist", it["picture_medium"].str ?: "") } }.getOrDefault(emptyList())
        val albums = runCatching { pub("search/album?q=${enc(q)}&limit=12")["data"].list.map {
            Album(it["id"].str ?: "", it["title"].str ?: "", it.path("artist", "name").str ?: "", it["cover_medium"].str ?: "", it["nb_tracks"].str ?: "") } }
            .getOrDefault(emptyList())
        return Found(songs, artists, albums)
    }

    override fun myPlaylists(): List<Album> {
        if (!signedIn) return emptyList()
        val out = arrayListOf(Album("loved", "Favourite tracks", "Deezer", ""))
        val tab = gw("deezer.pageProfile", buildJsonObject { put("USER_ID", user.toString()); put("tab", "playlists"); put("nb", 500) })
        for (p in tab.path("TAB", "playlists", "data").list) {
            val id = p["PLAYLIST_ID"].str ?: continue
            if (p["TYPE"].str == "4") continue   // the loved tracks playlist: Favourite tracks above
            out += Album(id, p["TITLE"].str ?: "", "${p["NB_SONG"].str ?: "?"} songs",
                p["PLAYLIST_PICTURE"].str?.let { "https://e-cdns-images.dzcdn.net/images/playlist/$it/250x250-000000-80-0-0.jpg" } ?: "")
        }
        return out
    }

    override fun playlist(id: String): List<Track> {
        if (id == "loved") return gw("deezer.pageProfile", buildJsonObject { put("USER_ID", user.toString()); put("tab", "loved"); put("nb", 10000) })
            .path("TAB", "loved", "data").list.map { trackFromDeezer(it) }
        return if (signedIn) gw("deezer.pagePlaylist", buildJsonObject { put("PLAYLIST_ID", id); put("nb", 2000); put("start", 0); put("lang", "en") })
            .path("SONGS", "data").list.map { trackFromDeezer(it) }
        else pub("playlist/$id/tracks?limit=2000")["data"].list.map { trackFromDeezer(it) }
    }

    override fun likedIds(): Set<String> = if (!signedIn) emptySet() else
        gw("song.getFavoriteIds", buildJsonObject { put("nb", 10000); put("start", 0) })["data"].list.mapNotNull { it["SNG_ID"].str?.let { s -> "dz$s" } }.toSet()

    override fun rate(t: Track, like: Boolean) {
        if (!signedIn || t.source != "deezer") return
        val id = t.id.removePrefix("dz")
        if (like) gw("favorite_song.add", buildJsonObject { put("SNG_ID", id) })
        else gw("favorite_song.remove", buildJsonObject { put("SNG_IDS", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(id)))) })
    }

    override fun artist(name: String): ArtistInfo? {
        val a = if (name.startsWith("dzartist:")) pub("artist/${name.removePrefix("dzartist:")}")
            else pub("search/artist?q=${enc(name)}&limit=1")["data"].list.firstOrNull() ?: return null
        val id = a["id"].str ?: return null
        val top = runCatching { pub("artist/$id/top?limit=20")["data"].list.map { trackFromDeezer(it) } }.getOrDefault(emptyList())
        val albums = runCatching { pub("artist/$id/albums?limit=100")["data"].list.map {
            Album(it["id"].str ?: "", it["title"].str ?: "", it["release_date"].str?.take(4) ?: "", it["cover_medium"].str ?: "") } }.getOrDefault(emptyList())
        return ArtistInfo(a["name"].str ?: name, a["picture_big"].str ?: a["picture_medium"].str ?: "", top, albums, "deezer", "deezer")
    }

    override fun album(id: String): List<Track> {
        val al = pub("album/$id")
        return al.path("tracks", "data").list.map { tr ->
            trackFromDeezer(tr).let { t -> t.copy(album = al["title"].str ?: t.album, thumb = al["cover_medium"].str ?: t.thumb) }
        }
    }

    override fun radio(t: Track): List<Track> {
        val aid = if (t.source == "deezer") pub("track/${t.id.removePrefix("dz")}").path("artist", "id").str
            else pub("search/artist?q=${enc(t.artist.split(",")[0])}&limit=1")["data"].list.firstOrNull()?.get("id").str
        return aid?.let { pub("artist/$it/radio?limit=50")["data"].list.map { x -> trackFromDeezer(x) } } ?: emptyList()
    }

    override fun mixFor(q: String): List<Track> = runCatching {
        val p = pub("search/playlist?q=${enc(q)}&limit=1")["data"].list.firstOrNull()?.get("id").str ?: return emptyList()
        pub("playlist/$p/tracks?limit=100")["data"].list.map { trackFromDeezer(it) }
    }.getOrDefault(emptyList())
}
