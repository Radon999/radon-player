package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Looking artists up: their pictures and bios (kept on this device), a picture of your own choosing, and an
 *  artist's page from the first service that knows them. The rules are in [ArtistLookupRules]. [Music] keeps the
 *  names the screens call and hands on. */
object ArtistLookups {
    private val lookingUp = HashSet<String>()

    /** Look up the pictures and bios of the artists named, one after another off the main thread. */
    fun lookUp(names: List<String>) {
        val each = ArtistLookupRules.once(names) { norm(it) }
        val todo = each.filter { n -> !Pictures.settled(Pictures.known(n)) && synchronized(lookingUp) { lookingUp.add(norm(n)) } }
        if (todo.isEmpty()) return
        Music.scope.launch(Dispatchers.IO) {
            for (n in todo) {
                runCatching { Pictures.artist(n) }
                runCatching { Pictures.keepOnPhone(n) }   // the picture itself kept on the phone: not loaded again
                synchronized(lookingUp) { lookingUp.remove(norm(n)) }
                withContext(Dispatchers.Main) { Music.artistsVersion++ }
            }
        }
        // Ones looked up before pictures were kept on the phone: kept now (no lookup, just the picture)
        val unsaved = each.filter { n -> Pictures.known(n)?.let { it.saved.isEmpty() && it.image.startsWith("http") } == true }
        if (unsaved.isNotEmpty()) Music.scope.launch(Dispatchers.IO) {
            var got = 0
            for (n in unsaved) if (runCatching { Pictures.keepOnPhone(n) }.getOrNull() != null) got++
            if (got > 0) withContext(Dispatchers.Main) { Music.artistsVersion++ }
        }
    }

    /** The pictures of artists gone from your music (no songs of theirs left, and no plays in your stats): forgotten,
     *  their files deleted. */
    fun prunePictures() {
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList())
        if (songs.isEmpty()) return
        val keep = (songs.flatMap { ownArtists(it.artist) } + Stats.artists.map { it.name }).map { norm(it) }.toSet()
        Music.bg({ Pictures.prune(keep) })
    }

    /** Keep source (a picture's web address, or a content:// one you picked) as the artist's picture. */
    fun setPicture(name: String, source: String) {
        val ctx = Music.ctx
        Music.bg({
            val url = if (source.startsWith("content://")) {
                val f = File(File(ctx.filesDir, "artists").apply { mkdirs() }, "${randomHex(8)}.jpg")
                ctx.openIn(source)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                f.path
            } else source
            Pictures.chooseArtist(name, url)
        }) { _, err ->
            Music.toast(if (err != null) "Couldn't use it: ${err.message?.take(100)}" else "New picture for $name")
            Music.artistsVersion++
        }
    }

    /** The one of several same-named artists you picked: (the service, its id for them), or null. */
    fun picked(name: String): Pair<String, String>? = ArtistLookupRules.readPick(Prefs.text(ArtistLookupRules.pickKey(name)))

    /** Pick which of the same-named artists this name means from now on (null: none, the service's first again). */
    fun pick(name: String, source: String, id: String?) {
        Prefs.setText(ArtistLookupRules.pickKey(name), if (id == null) "" else ArtistLookupRules.pick(source, id))
        Music.artistsVersion++
    }

    /** (blocking) The artists a service has under exactly this name, when there's more than one. */
    fun choices(name: String, source: String): List<ArtistChoice> =
        runCatching { Music.sources[source]?.artistsNamed(name) }.getOrNull().orEmpty()

    private val releaseCache = HashMap<String, List<Track>>()

    /** (blocking) A release's songs from its service, or null when they can't be had. Remembered while the app runs:
     *  an artist's page asks for each of their releases (to leave out singles that are on a release, to sort two
     *  bands apart, to list every song), and each is asked for once. */
    fun releaseSongs(source: String, id: String): List<Track>? {
        val key = "$source:$id"
        synchronized(releaseCache) { releaseCache[key] }?.let { return it }
        val got = runCatching { Music.sources[source]?.album(id) }.getOrNull() ?: return null
        synchronized(releaseCache) { if (releaseCache.size > 800) releaseCache.clear(); releaseCache[key] = got }
        return got
    }

    /** (blocking) The service's singles of an artist that are on one of their albums or EPs already (their ids):
     *  left out of the page, the song is on its release. See [ReleaseRules]. */
    fun foldedSingles(info: ArtistInfo): Set<String> =
        ReleaseRules.foldedSingles(info.albums.take(80).mapNotNull { al -> releaseSongs(info.source, al.id)?.let { al to it } })

    /** (blocking) Every song on the artist's releases (their albums, EPs and singles, then the compilations they're
     *  on), each once, your own copy in place of each one you have. Each release is asked for in turn: progress
     *  says how far (done, of). A release that can't be had is passed over. */
    fun everySong(info: ArtistInfo, name: String, progress: (Int, Int) -> Unit = { _, _ -> }): List<Track> {
        val src = Music.sources[info.source] ?: return emptyList()
        val releases = info.albums + info.also
        val got = ArrayList<Pair<Album, List<Track>>>()
        for ((i, al) in releases.withIndex()) {
            progress(i, releases.size)
            releaseSongs(info.source, al.id)?.let { got += al to it }
        }
        progress(releases.size, releases.size)
        val who = info.name.ifEmpty { name }
        // Their own songs: the ones they lead. Someone else's song that features them is under Featured on.
        return Music.yourCopies(ArtistLookupRules.everySong(got) { t -> (Music.creditsArtist(t, who) || Music.creditsArtist(t, name)) &&
            (FeatureRules.leads(t.artist, who) || FeatureRules.leads(t.artist, name)) })
    }

    /** (blocking) Featured on: the songs of other artists that credit this one, from the releases they appear on
     *  (their own and the service's "appears on"), the service's search for their name, their top songs, and (with a
     *  Genius artist known) the songs Genius lists them on. Songs; a release only when all of it features them. Your
     *  own copy in place of each one you have. See [FeatureRules]. */
    fun features(info: ArtistInfo, name: String, geniusId: String?): FeatureRules.Featured {
        val who = info.name.ifEmpty { name }
        val credits = { t: Track -> Music.creditsArtist(t, who) || Music.creditsArtist(t, name) }
        val releases = (info.albums + info.also).take(120).mapNotNull { al -> releaseSongs(info.source, al.id)?.let { al to it } }
        val found = runCatching { Music.sources[info.source]?.search(who) }.getOrNull().orEmpty()
        val mine = Music.songIndex()
        val genius = geniusId?.let { id -> runCatching { GeniusArtists.featuredOn(id) }.getOrNull() }.orEmpty()
            .map { h -> Music.fmSong(h.title, if (credits(Track("", title = h.title, artist = h.artist))) h.artist else "${h.artist}, $who", h.art, mine) }
        val f = FeatureRules.featured(who, releases, info.top + found + genius, credits)
        return FeatureRules.Featured(f.albums, Music.yourCopies(f.songs))
    }

    /** (blocking) The artist's page from the first service that knows them. */
    fun info(name: String, prefer: String?): ArtistInfo? {
        // Two bands with one name, and you said which one (the artist's page: Which one?): that one's own page, from
        // the service it was picked on, and nothing of another service's (which may know only the other band)
        picked(name)?.let { (key, id) ->
            val src = Music.sources[key]
            if (src != null && ArtistLookupRules.asked(key, src.signedIn))
                runCatching { src.artistById(id) }.getOrNull()?.let { return it.copy(source = key, topSource = key) }
        }
        var info: ArtistInfo? = null
        var last: Throwable? = null
        val names = ArtistLookupRules.names(name)
        for (key in ArtistLookupRules.order(prefer, Music.source)) {
            val src = Music.sources[key] ?: continue
            if (!ArtistLookupRules.asked(key, src.signedIn)) continue
            var found: ArtistInfo? = null
            for (n in names) {
                found = try { src.artist(n) } catch (e: Exception) { last = e; null }
                if (found != null) break
            }
            found ?: continue
            if (info == null) info = found.copy(source = key, topSource = key)
            else if (found.top.isNotEmpty()) info = info.copy(top = found.top, topSource = key)
            if (info.top.isNotEmpty()) break
        }
        if (info == null && last != null) throw last!!
        return info
    }
}
