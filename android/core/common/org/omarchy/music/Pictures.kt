package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.File

/** Artists' pictures and album covers from Google's image search (with a key of yours, in
 *  Settings), else Genius, else Wikipedia; artists' bios from Wikipedia, else Genius. Each is
 *  looked for once and kept in pictures.json; the desktop does the same. Everything here blocks:
 *  call it from [Music.bg]. A service that can't be reached is skipped. */
object Pictures {
    @Serializable
    data class Artist(val image: String = "", val imageFrom: String = "", val bio: String = "", val bioFrom: String = "",
                      val bioUrl: String = "", val chosen: Boolean = false, val t: Double = 0.0, val v: Int = 0,
                      val failed: Boolean = false, val saved: String = "")   // saved: the picture's copy on the phone

    /** A picture to pick from, and where it's from ("Google", "Genius", "Wikipedia"). */
    data class Choice(val url: String, val thumb: String, val from: String)

    @Serializable
    private data class Saved(val artists: Map<String, Artist> = emptyMap(), val covers: Map<String, String> = emptyMap())

    private const val RETRY = 14 * 86400   // an artist nothing was found for is looked for again after this long
    private const val RETRY_FAILED = 3600   // …or after this long, when Deezer didn't answer (rather than had nothing)
    const val V = 3   // 3: Deezer paced; the ones found with no picture before it are looked for again, once
    private val deezerGap = Any()
    private var deezerLast = 0L
    private const val WIKI_API = "https://en.wikipedia.org/w/api.php"
    private const val GENIUS_BLANK = "assets.genius.com/images/default_"   // Genius's grey placeholder
    private val MUSIC_WORDS = Regex("singer|band|music|rapper|group|producer|\\bdj\\b|composer|songwriter|duo|trio|artist|" +
        "vocalist|orchestra|ensemble|guitarist|pianist|drummer", RegexOption.IGNORE_CASE)
    private val ALBUM_WORDS = Regex("album|\\bep\\b|mixtape|soundtrack|compilation|record", RegexOption.IGNORE_CASE)

    /** The keys from Settings (google_key, google_cx). */
    internal var keys: () -> Map<String, String> = { Music.apiKeys }
    internal var fetch: (String, Map<String, String>) -> JsonElement = { url, h -> httpJson(url, h) }   // tests swap it
    private val artists = HashMap<String, Artist>()
    private val covers = HashMap<String, String>()
    private var file: File? = null

    private var picsDir: File? = null   // where artists' pictures are kept on the phone (not looked up again)

    fun init(c: Ctx) {
        val f = File(appCtx(c).filesDir, "pictures.json")
        file = f
        picsDir = File(appCtx(c).filesDir, "artist-pics")
        Music.bg({ readText(f)?.let { runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } }) { s, _ ->
            if (s != null) synchronized(this) { s.artists.forEach { (k, v) -> artists.putIfAbsent(k, v) }; s.covers.forEach { (k, v) -> covers.putIfAbsent(k, v) } }
        }
    }

    private fun save() {
        val f = file ?: return
        val text = synchronized(this) { JSON.encodeToString(Saved.serializer(), Saved(artists.toMap(), covers.toMap())) }
        writeAtomic(f, text)
    }

    private fun <T> tried(what: String, work: () -> T): T? = try { work() } catch (e: Exception) {
        runCatching { logWarn("pictures: $what: $e") }   // (not on the JVM tests' fake Android)
        null
    }

    /** Waits its turn to ask Deezer: about nine a second at most, for everything that asks it (it allows fifty in five). */
    fun deezerPace() = synchronized(deezerGap) {
        val wait = deezerLast + 110 - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        deezerLast = System.currentTimeMillis()
    }

    // ---- the services ----
    /** Deezer's photo of the artist (no key needed), if one is named closely enough; its blank one isn't. */
    internal fun deezer(name: String): String? {
        val url = "https://api.deezer.com/search/artist?q=${enc(name)}&limit=5"
        // Paced, about nine a second (Deezer allows fifty in five): Most played's hundreds at once went over the
        // limit and came back empty, as if there were no picture. Still over it: an error, asked again soon.
        fun ask(): JsonElement { deezerPace(); return fetch(url, emptyMap()) }
        var res = ask()
        if (res["error"]["code"].long == 4L) { Thread.sleep(5000); res = ask() }
        if (res["error"] is kotlinx.serialization.json.JsonObject && res["data"] == null)
            throw java.io.IOException("Deezer: ${res["error"]["message"].str ?: "busy"}")
        val data = res["data"].list
            .sortedByDescending { similar(norm(it["name"].str), norm(name)) }
        val best = data.firstOrNull() ?: return null
        if (similar(norm(best["name"].str), norm(name)) <= 0.85) return null
        return (best["picture_xl"].str ?: best["picture_big"].str)?.takeIf { it.isNotEmpty() && "/artist//" !in it }
    }

    internal fun google(q: String, n: Int = 6): List<Choice> {
        val k = keys()
        val key = k["google_key"].orEmpty(); val cx = k["google_cx"].orEmpty()
        if (key.isEmpty() || cx.isEmpty()) return emptyList()
        val res = fetch("https://www.googleapis.com/customsearch/v1?key=${enc(key)}&cx=${enc(cx)}&q=${enc(q)}" +
            "&searchType=image&num=$n&imgSize=large&safe=active", emptyMap())
        return res["items"].list.mapNotNull { i ->
            val link = i["link"].str ?: return@mapNotNull null
            Choice(link, i.path("image", "thumbnailLink").str ?: link, "Google")
        }
    }

    private data class GeniusArtist(val id: Long, val name: String, val image: String, val url: String)

    private fun geniusArtist(name: String): GeniusArtist? {
        val hits = fetch("https://genius.com/api/search/artist?q=${enc(name)}&per_page=5", emptyMap())
            .path("response", "sections", 0, "hits").list.map { it["result"] }.filter { it["name"].str != null }
        val best = bestNamed(hits, name) { it["name"].str ?: "" } ?: return null
        val img = best["image_url"].str ?: ""
        return GeniusArtist(best["id"].long ?: 0, best["name"].str ?: name, if (GENIUS_BLANK in img) "" else img, best["url"].str ?: "")
    }

    private fun geniusBio(id: Long): String {
        val text = fetch("https://genius.com/api/artists/$id?text_format=plain", emptyMap())
            .path("response", "artist", "description", "plain").str?.trim() ?: ""
        return if (text == "?") "" else text
    }

    private fun geniusCovers(artist: String, album: String): List<Choice> =
        fetch("https://genius.com/api/search/album?q=${enc("$artist $album")}&per_page=5", emptyMap())
            .path("response", "sections", 0, "hits").list.mapNotNull { h ->
                val r = h["result"]
                val img = r["cover_art_url"].str ?: ""
                val who = norm(r.path("artist", "name").str)
                if (img.isEmpty() || GENIUS_BLANK in img || who.startsWith("genius") ||   // its translations
                    similar(norm(bareTitle(r["name"].str ?: "")), norm(bareTitle(album))) < 0.6) null
                else Choice(img, r["cover_art_thumbnail_url"].str ?: img, "Genius")
            }

    private data class WikiPage(val bio: String, val image: String, val url: String)

    /** The article a search for q finds that's titled name (near enough) and whose short description has one of words. */
    private fun wikipedia(q: String, name: String, words: Regex): WikiPage? {
        val hdr = mapOf("User-Agent" to APP_UA)
        val res = fetch("$WIKI_API?action=query&format=json&generator=search&gsrsearch=${enc(q)}&gsrlimit=5" +
            "&prop=extracts%7Cpageimages%7Cpageprops&exintro=1&explaintext=1&exlimit=max&piprop=original" +
            "&ppprop=wikibase-shortdesc%7Cpage_image%7Cdisambiguation&redirects=1", hdr)
        val pages = res.path("query", "pages").obj?.values.orEmpty().sortedBy { it["index"].long ?: 99 }
        for (p in pages) {
            val props = p["pageprops"]
            val title = (p["title"].str ?: "").replace(Regex("\\s*\\([^)]*\\)$"), "")
            val about = props["wikibase-shortdesc"].str ?: (p["extract"].str ?: "").take(200)
            if (props["disambiguation"] != null || similar(norm(title), norm(name)) < 0.85 || !words.containsMatchIn(about)) continue
            var image = p.path("original", "source").str ?: ""
            val file = props["page_image"].str
            if (image.isEmpty() && file != null)   // a cover that isn't free: pageimages leaves it out
                image = fetch("$WIKI_API?action=query&format=json&prop=imageinfo&iiprop=url&titles=${enc("File:$file")}", hdr)
                    .path("query", "pages").obj?.values?.firstNotNullOfOrNull { it.path("imageinfo", 0, "url").str } ?: ""
            val t = p["title"].str ?: ""
            return WikiPage(wikiText(p["extract"].str ?: ""), image, "https://en.wikipedia.org/wiki/" + enc(t.replace(' ', '_')))
        }
        return null
    }

    /** An article's opening without its pronunciation guides ("(BYURK; Icelandic: [pjœr̥k])"). */
    internal fun wikiText(text: String): String {
        val head = text.substringBefore("\n")
        val rest = if ("\n" in text) text.substringAfter("\n") else ""
        var cut = head
        repeat(2) { cut = Regex("\\s*\\((?:\\s|[^()]*[\\[/;])[^()]*\\)").find(cut)?.let { m -> cut.removeRange(m.range) } ?: cut }
        return (cut + if (rest.isNotEmpty()) "\n$rest" else "").replace(Regex("  +"), " ").trim()
    }

    private fun wikiArtist(name: String) = wikipedia("$name musician", name, MUSIC_WORDS) ?: wikipedia(name, name, MUSIC_WORDS)

    private fun wikiAlbum(artist: String, album: String) = wikipedia("${bareTitle(album)} $artist album", bareTitle(album), ALBUM_WORDS)

    // ---- what the app asks for ----
    /** The artist's picture kept on the phone (downloaded once, then shown from there): its file, or null. */
    fun keepOnPhone(name: String): File? {
        val dir = picsDir ?: return null
        val a = known(name) ?: return null
        if (a.saved.isNotEmpty() && File(a.saved).exists()) return File(a.saved)
        val url = a.image.takeIf { it.startsWith("http") } ?: return null
        val f = File(dir.apply { mkdirs() }, "${java.security.MessageDigest.getInstance("SHA-1")
            .digest("${norm(name)}|$url".toByteArray()).joinToString("") { "%02x".format(it) }.take(24)}.jpg")
        val bytes = runCatching { httpBytes(url) }.getOrNull()?.takeIf { it.size > 100 } ?: return null
        f.writeBytes(bytes)
        synchronized(this) { artists[norm(name)]?.let { if (it.image == url) artists[norm(name)] = it.copy(saved = f.path) } }
        save()
        return f
    }

    /** Forget the artists not in keep (names, normed), and delete their pictures kept on the phone. How many. */
    fun prune(keep: Set<String>): Int {
        val gone = synchronized(this) { artists.filterKeys { it !in keep }.also { g -> g.keys.forEach { artists.remove(it) } } }
        for (a in gone.values) {
            if (a.saved.isNotEmpty()) File(a.saved).delete()
            if (a.chosen && a.image.startsWith("/") && "/artists/" in a.image) File(a.image).delete()   // one you picked, copied in
        }
        if (gone.isNotEmpty()) save()
        return gone.size
    }

    /** What's known of the artist, if it was looked up already (no network). */
    fun known(name: String): Artist? = synchronized(this) { artists[norm(name)] }

    /** Whether the artist's picture needn't be looked for (again): picked by you, found (since Deezer was
     *  asked), or not found lately. */
    fun settled(a: Artist?): Boolean = a != null && (a.chosen || (a.image.isNotEmpty() && a.v >= 2) ||
        (a.v >= V && now() - a.t < (if (a.failed) RETRY_FAILED else RETRY)))

    /** The artist's picture and bio ("" where none was found), looked up the first time. */
    fun artist(name: String, refresh: Boolean = false): Artist {
        val k = norm(name)
        val have = known(name)
        // (one looked for before Deezer was asked, and not picked by you: looked for again, once)
        if (have != null && !refresh && settled(have)) return have
        var out = Artist(t = now(), v = V)
        if (have?.chosen == true) out = out.copy(image = have.image, imageFrom = have.imageFrom, chosen = true)   // a picture you picked stays
        if (out.image.isEmpty()) {   // Deezer first: the artist's own photo; "Surfaces & Quinn XCII": Surfaces's, if the pair has none
            val lead = ownArtists(name).firstOrNull().orEmpty()
            for (n in listOf(name) + listOfNotNull(lead.takeIf { it.isNotEmpty() && norm(it) != k })) {
                val pic = try { deezer(n) } catch (e: Exception) {
                    tried("deezer") { throw e }
                    out = out.copy(failed = true); null
                } ?: continue
                out = out.copy(image = pic, imageFrom = "Deezer", failed = false); break
            }
        }
        if (out.image.isEmpty()) tried("google") { google("$name musician", 1) }?.firstOrNull()?.let { out = out.copy(image = it.url, imageFrom = "Google") }
        val genius = tried("genius") { geniusArtist(name) }
        if (out.image.isEmpty() && !genius?.image.isNullOrEmpty()) out = out.copy(image = genius!!.image, imageFrom = "Genius")
        val wiki = tried("wikipedia") { wikiArtist(name) }
        if (out.image.isEmpty() && !wiki?.image.isNullOrEmpty()) out = out.copy(image = wiki!!.image, imageFrom = "Wikipedia")
        if (wiki != null && wiki.bio.isNotEmpty()) out = out.copy(bio = wiki.bio, bioFrom = "Wikipedia", bioUrl = wiki.url)
        else if (genius != null && genius.id > 0)
            tried("genius bio") { geniusBio(genius.id) }?.takeIf { it.isNotEmpty() }?.let { out = out.copy(bio = it, bioFrom = "Genius", bioUrl = genius.url) }
        synchronized(this) {
            val now = artists[k]
            // You picked one while this was being looked up: yours stays (the bio found comes along).
            if (now != null && now.chosen && !(have?.chosen == true && have.image == now.image))
                out = out.copy(image = now.image, imageFrom = now.imageFrom, chosen = true, saved = now.saved)
            artists[k] = out
        }
        save()
        return out
    }

    /** Pictures of the artist to pick from: Deezer's, Google's, Genius's and Wikipedia's. */
    fun artistChoices(name: String): List<Choice> {
        val out = ArrayList<Choice>()
        tried("deezer") { deezer(name) }?.let { out += Choice(it, it, "Deezer") }
        out += tried("google") { google("$name musician") }.orEmpty()
        tried("genius") { geniusArtist(name) }?.image?.takeIf { it.isNotEmpty() }?.let { out += Choice(it, it, "Genius") }
        tried("wikipedia") { wikiArtist(name) }?.image?.takeIf { it.isNotEmpty() }?.let { out += Choice(it, it, "Wikipedia") }
        return out.distinctBy { it.url }
    }

    /** Covers for artist's album to pick from: Google's, then Genius's, then Wikipedia's. */
    fun coverChoices(artist: String, album: String): List<Choice> {
        val out = ArrayList(tried("google") { google("$artist ${bareTitle(album)} album cover") }.orEmpty())
        out += tried("genius") { geniusCovers(artist, album) }.orEmpty()
        tried("wikipedia") { wikiAlbum(artist, album) }?.image?.takeIf { it.isNotEmpty() }?.let { out += Choice(it, it, "Wikipedia") }
        return out.distinctBy { it.url }
    }

    /** Keep url (a web address or a file's) as the artist's picture. */
    fun chooseArtist(name: String, url: String, from: String = "you") {
        synchronized(this) { artists[norm(name)] = (artists[norm(name)] ?: Artist(t = now())).copy(image = url, imageFrom = from, chosen = true, saved = "") }
        save()
    }

    /** An album's key for a cover you picked: its lead artist ("Quinn XCII & Jeremy Zucker" as Quinn XCII, so the
     *  album's songs with someone featured get it too) and its name without its edition (albumBase). */
    private fun lead(artist: String) = norm(artist.split(Regex(",| & | x | feat\\.? | ft\\.? | featuring | with ", RegexOption.IGNORE_CASE))[0])
    private fun coverKey(artist: String, album: String) = "${lead(artist)}||${albumBase(album)}"
    /** The key covers were picked under before 2.1.2: everything after " - " and in brackets left out, so "Mercury - Act 1"
     *  and "Mercury - Act 2" (and any album called Mercury) shared one. */
    private fun oldKey(artist: String, album: String) = "${lead(artist)}|${norm(bareTitle(album))}"

    /** The cover you picked for artist's album, or "". */
    fun cover(artist: String, album: String): String = synchronized(this) { covers[coverKey(artist, album)] ?: "" }

    /** A cover picked before 2.1.2 that may be this album's (or another album's with a name like it): shown only for
     *  songs with no cover of their own. "" if none. */
    fun oldCover(artist: String, album: String): String = synchronized(this) {
        if (covers.containsKey(coverKey(artist, album))) "" else covers[oldKey(artist, album)] ?: "" }

    fun chooseCover(artist: String, album: String, url: String) {
        synchronized(this) { covers[coverKey(artist, album)] = url; covers.remove(oldKey(artist, album)) }
        save()
    }
}
