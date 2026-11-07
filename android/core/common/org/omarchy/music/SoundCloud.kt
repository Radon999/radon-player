package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/** A SoundCloud track as our Track: "sc" and its number; the uploader (or the artist name it gives) as the artist. */
fun trackFromSoundCloud(j: JsonElement?): Track? {
    if (j == null || (j["kind"].str ?: "track") != "track") return null
    val id = j["id"].str ?: j["urn"].str?.substringAfterLast(':') ?: return null
    if (j["access"].str == "blocked") return null   // the uploader keeps it off other apps
    val art = (j["artwork_url"].str ?: j.path("user", "avatar_url").str)?.replace("-large.", "-t500x500.") ?: ""
    return Track(id = "sc$id", source = SoundCloud.KEY,
        title = j["title"].str ?: "Unknown",
        artist = j["metadata_artist"].str?.takeIf { it.isNotBlank() } ?: j.path("user", "username").str ?: "Unknown artist",
        album = "", duration = Math.round((j["duration"].dbl ?: 0.0) / 1000).toDouble(), thumb = art,
        isrc = j["isrc"].str?.takeIf { it.isNotBlank() })
}

/** SoundCloud, as its own service: search, your likes and playlists, artists, related tracks and liking, through a
 *  SoundCloud app of yours (its Client ID and secret). Its songs stream from SoundCloud itself, in AAC (about 160 kbps),
 *  credited to the uploader and SoundCloud. SoundCloud's rules for apps like this one: no downloads or offline copies of
 *  any kind, and its songs can't be mixed with other services' in one queue or playlist, so they play in a queue of
 *  their own and are never saved. */
class SoundCloud(ctx: Ctx) : Source {
    override val key = KEY
    override val name = "SoundCloud"
    override val hint = "Connect SoundCloud in Settings (⚙).\nIt needs a SoundCloud app of yours."

    @Serializable
    private data class Auth(val client_id: String = "", val secret: String = "", val user: String = "", val access_token: String = "",
                            val refresh_token: String = "", val expires: Double = 0.0,
                            val app_token: String = "", val app_refresh: String = "", val app_expires: Double = 0.0)

    private val file = File(ctx.filesDir, "soundcloud.json")
    @Volatile private var auth = readText(file)?.let { runCatching { JSON.decodeFromString(Auth.serializer(), it) }.getOrNull() } ?: Auth()
    private var verifier = ""
    private var state = ""

    val clientId get() = auth.client_id
    val user get() = auth.user
    /** Your app is set up: search and playing work (signed in or not). */
    val ready get() = auth.client_id.isNotEmpty() && auth.secret.isNotEmpty()
    override val signedIn get() = auth.refresh_token.isNotEmpty()

    private fun save() = writeAtomic(file, JSON.encodeToString(Auth.serializer(), auth))

    fun disconnect() {
        runCatching { if (auth.access_token.isNotEmpty()) httpJson("$API/disconnect", mapOf("Authorization" to "OAuth ${auth.access_token}"), form(), "POST") }
        auth = Auth(client_id = auth.client_id, secret = auth.secret); save()
    }

    /** Remember your app's Client ID and secret; false if either is missing. */
    fun setClient(id: String, secret: String): Boolean {
        val cid = id.trim(); val sec = secret.trim()
        if (cid.length < 10 || sec.length < 10) return false
        if (cid != auth.client_id || sec != auth.secret) { auth = Auth(client_id = cid, secret = sec); save() }
        return true
    }

    /** SoundCloud's consent page (OAuth 2.1 with PKCE). */
    fun loginUrl(): String {
        verifier = b64url(ByteArray(48).also { SecureRandom().nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        state = randomHex(12)
        return "https://secure.soundcloud.com/authorize?client_id=${enc(clientId)}&redirect_uri=${enc(REDIRECT)}&response_type=code" +
            "&code_challenge=$challenge&code_challenge_method=S256&state=$state&display=popup"
    }

    /** Swap the code the consent page came back with for a sign-in (blocking). */
    fun finishLogin(redirected: String) {
        queryParam(redirected, "error")?.let { error(queryParam(redirected, "error_description") ?: it) }
        if (queryParam(redirected, "state") != state) error("SoundCloud sent back a different sign-in")
        val code = queryParam(redirected, "code") ?: error("SoundCloud didn't send a sign-in back")
        val tok = httpJson(TOKEN, mapOf("accept" to "application/json; charset=utf-8"), form("grant_type" to "authorization_code",
            "client_id" to clientId, "client_secret" to auth.secret, "redirect_uri" to REDIRECT, "code_verifier" to verifier, "code" to code))
        take(tok)
        val me = get("/me")
        auth = auth.copy(user = me["username"].str ?: me["full_name"].str ?: "")
        save()
    }

    private fun take(tok: JsonElement) {
        auth = auth.copy(access_token = tok["access_token"].str ?: error("SoundCloud didn't sign you in"),
            expires = now() + (tok["expires_in"].dbl ?: 3600.0) - 60,
            refresh_token = tok["refresh_token"].str ?: auth.refresh_token)   // refresh tokens are used once: the new one is kept
    }

    /** A token to call the API with: yours when signed in, else the app's own (for search and playing), each renewed with
     *  its refresh token when it runs out (SoundCloud hands out only a few app tokens an hour, so they're kept). */
    @Synchronized private fun access(): String {
        if (signedIn) {
            if (auth.access_token.isNotEmpty() && auth.expires > now()) return auth.access_token
            take(httpJson(TOKEN, mapOf("accept" to "application/json; charset=utf-8"), form("grant_type" to "refresh_token",
                "client_id" to clientId, "client_secret" to auth.secret, "refresh_token" to auth.refresh_token)))
            save()
            return auth.access_token
        }
        if (!ready) error("Set up SoundCloud in Settings first")
        if (auth.app_token.isNotEmpty() && auth.app_expires > now()) return auth.app_token
        val tok = runCatching {
            if (auth.app_refresh.isEmpty()) error("none")
            httpJson(TOKEN, mapOf("accept" to "application/json; charset=utf-8"), form("grant_type" to "refresh_token",
                "client_id" to clientId, "client_secret" to auth.secret, "refresh_token" to auth.app_refresh))
        }.getOrElse {
            val basic = java.util.Base64.getEncoder().encodeToString("$clientId:${auth.secret}".toByteArray())
            httpJson(TOKEN, mapOf("accept" to "application/json; charset=utf-8", "Authorization" to "Basic $basic"),
                form("grant_type" to "client_credentials"))
        }
        auth = auth.copy(app_token = tok["access_token"].str ?: error("SoundCloud didn't accept your app's Client ID and secret"),
            app_refresh = tok["refresh_token"].str ?: "", app_expires = now() + (tok["expires_in"].dbl ?: 3600.0) - 60)
        save()
        return auth.app_token
    }

    private fun request(path: String, method: String? = null): JsonElement {
        val url = if (path.startsWith("http")) path else API + path
        var attempt = 0
        while (true) {
            try {
                return httpJson(url, mapOf("Authorization" to "OAuth ${access()}", "accept" to "application/json; charset=utf-8"),
                    if (method == "POST") form() else null, method)
            } catch (e: HttpError) {
                when {
                    e.code == 401 && attempt == 0 -> synchronized(this) { auth = auth.copy(expires = 0.0, app_expires = 0.0) }
                    e.code == 429 -> error("SoundCloud's limit for today is reached: try again later")
                    else -> throw e
                }
            }
            attempt++
        }
    }
    private fun get(path: String) = request(path)

    /** Every page of a list (linked_partitioning), up to max. */
    private fun pages(path: String, max: Int = 1000): List<JsonElement> {
        val out = ArrayList<JsonElement>()
        var next: String? = path + (if ('?' in path) "&" else "?") + "linked_partitioning=true&limit=200"
        while (next != null && out.size < max) {
            val page = get(next)
            out += page["collection"].list
            next = page["next_href"].str
        }
        return out
    }

    override fun search(q: String): List<Track> {
        if (q.contains("soundcloud.com/")) return resolve(q)
        return get("/tracks?q=${enc(q)}&access=playable&limit=50").let { it["collection"].list.ifEmpty { it.list } }.mapNotNull { trackFromSoundCloud(it) }
    }

    /** A pasted SoundCloud link: its track, or a playlist's (an album's) tracks. */
    private fun resolve(link: String): List<Track> {
        val r = get("/resolve?url=${enc(link.trim())}")
        return when (r["kind"].str) {
            "track" -> listOfNotNull(trackFromSoundCloud(r))
            "playlist" -> r["tracks"].list.mapNotNull { trackFromSoundCloud(it) }
            "user" -> r["id"].str?.let { pages("/users/soundcloud:users:$it/tracks", 200).mapNotNull { t -> trackFromSoundCloud(t) } } ?: emptyList()
            else -> emptyList()
        }
    }

    override fun find(q: String): Found {
        val songs = search(q)
        if (q.contains("soundcloud.com/")) return Found(songs)
        val artists = runCatching { get("/users?q=${enc(q)}&limit=8").let { it["collection"].list.ifEmpty { it.list } }.map {
            Album("scuser:${it["id"].str}", it["username"].str ?: "", "Artist", it["avatar_url"].str?.replace("-large.", "-t500x500.") ?: "") } }
            .getOrDefault(emptyList())
        val albums = runCatching { get("/playlists?q=${enc(q)}&limit=12&show_tracks=false").let { it["collection"].list.ifEmpty { it.list } }.map { playlistAlbum(it) } }
            .getOrDefault(emptyList())
        return Found(songs, artists, albums)
    }

    private fun playlistAlbum(p: JsonElement) = Album(p["id"].str ?: p["urn"].str?.substringAfterLast(':') ?: "", p["title"].str ?: "",
        p.path("user", "username").str ?: "SoundCloud", (p["artwork_url"].str ?: p.path("user", "avatar_url").str)?.replace("-large.", "-t500x500.") ?: "",
        p["track_count"].str ?: "")

    override fun myPlaylists(): List<Album> {
        if (!signedIn) return emptyList()
        return listOf(Album("likes", "Liked tracks", "SoundCloud", "")) + pages("/me/playlists?show_tracks=false", 400).map { playlistAlbum(it) }
    }

    override fun playlist(id: String): List<Track> =
        (if (id == "likes") pages("/me/likes/tracks?access=playable", 2000) else pages("/playlists/soundcloud:playlists:$id/tracks?access=playable", 2000))
            .mapNotNull { trackFromSoundCloud(it) }

    override fun likedIds(): Set<String> = if (!signedIn) emptySet() else
        pages("/me/likes/tracks", 2000).mapNotNull { it["id"].str?.let { s -> "sc$s" } }.toSet()

    override fun rate(t: Track, like: Boolean) {
        if (!signedIn || t.source != KEY) return
        request("/likes/tracks/soundcloud:tracks:${t.id.removePrefix("sc")}", if (like) "POST" else "DELETE")
    }

    override fun artist(name: String): ArtistInfo? {
        val u = if (name.startsWith("scuser:")) get("/users/soundcloud:users:${name.removePrefix("scuser:")}")
            else get("/users?q=${enc(name)}&limit=1").let { it["collection"].list.ifEmpty { it.list } }.firstOrNull() ?: return null
        val id = u["id"].str ?: return null
        val top = runCatching { pages("/users/soundcloud:users:$id/tracks?access=playable", 100).mapNotNull { trackFromSoundCloud(it) } }.getOrDefault(emptyList())
        val albums = runCatching { pages("/users/soundcloud:users:$id/playlists?show_tracks=false", 100).map { playlistAlbum(it) } }.getOrDefault(emptyList())
        return ArtistInfo(u["username"].str ?: name, u["avatar_url"].str?.replace("-large.", "-t500x500.") ?: "", top, albums, KEY, KEY)
    }

    override fun album(id: String): List<Track> = playlist(id)

    override fun radio(t: Track): List<Track> = if (t.source != KEY) emptyList() else
        runCatching { get("/tracks/soundcloud:tracks:${t.id.removePrefix("sc")}/related?access=playable&limit=50")
            .let { it["collection"].list.ifEmpty { it.list } }.mapNotNull { trackFromSoundCloud(it) } }.getOrDefault(emptyList())

    /** Where to play a track from (blocking): SoundCloud's AAC stream (about 160 kbps, HLS), else its MP3 one. The API's
     *  stream address needs the sign-in, so it's followed here to the CDN's own (signed, for a while) address, which the
     *  player can open by itself. (address, quality) */
    fun stream(t: Track): Pair<String, String> {
        val s = get("/tracks/soundcloud:tracks:${t.id.removePrefix("sc")}/streams")
        val (url, quality) = s["hls_aac_160_url"].str?.let { it to "AAC 160 kbps" }
            ?: s["hls_mp3_128_url"].str?.let { it to "MP3 128 kbps" }
            ?: error("SoundCloud won't play this one outside its own apps")
        val client = HTTP.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val req = okhttp3.Request.Builder().url(url).header("Authorization", "OAuth ${access()}").header("User-Agent", APP_UA).build()
        client.newCall(req).execute().use { r ->
            if (r.isRedirect) r.header("Location")?.let { return it to quality }
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw HttpError(r.code, body, url)
            // An answer with the address in it; or the playlist itself (then the address works as it is)
            runCatching { JSON.parseToJsonElement(body)["url"].str }.getOrNull()?.let { return it to quality }
            return url to quality
        }
    }

    /** The track's page on soundcloud.com (credit and a link back, as SoundCloud asks). */
    fun pageOf(t: Track): String? = runCatching { get("/tracks/soundcloud:tracks:${t.id.removePrefix("sc")}")["permalink_url"].str }.getOrNull()

    companion object {
        private val me get() = Music.sources[KEY] as? SoundCloud
        /** The consent page sent you back here: finish signing in. */
        fun finish(redirected: String) {
            val sc = me ?: return
            Music.bg({ sc.finishLogin(redirected) }) { _, err ->
                if (err != null) { Music.toast("SoundCloud didn't connect: ${err.message?.take(80)}"); return@bg }
                Music.toast("SoundCloud connected" + if (sc.user.isNotEmpty()) " as ${sc.user}" else "")
                Music.sourceReady(KEY, sc, null)
            }
        }
        fun signOut() {
            val sc = me ?: return
            Music.bg({ sc.disconnect() }) { _, _ -> Music.sourceReady(KEY, sc, null); Music.toast("Disconnected from SoundCloud") }
        }

        const val KEY = "soundcloud"
        const val API = "https://api.soundcloud.com"
        const val TOKEN = "https://secure.soundcloud.com/oauth/token"
        /** Where the consent page sends you back: the PC answers it itself; on the phone the sign-in page catches it. */
        const val REDIRECT = "http://127.0.0.1:8899/soundcloud"
        /** The note shown wherever SoundCloud is: it's a service of its own here. */
        const val APART = "SoundCloud songs play in a queue of their own: they can't be downloaded, and can't be mixed with " +
            "songs from other services in a queue or playlist (SoundCloud's rules for apps like this one)"
    }
}
