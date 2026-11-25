package org.omarchy.music

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Gets songs ready to play: Tidal's stream looked up (nothing saved), or the song
 *  downloaded as tagged FLAC into Music/Omarchy Music/. Songs Tidal has no lossless copy
 *  of stream in what there is (Tidal's AAC, else YouTube's audio), but are never saved lossy. */
class Downloader(private val ctx: Ctx, private val lib: Library, val tidal: Tidal) {
    data class Ready(val uri: String, val isDash: Boolean = false, val quality: String = "", val onDisk: Boolean = false,
                     val lossyFrom: String? = null,   // "Tidal" or "YouTube": no FLAC of it
                     val soon: Boolean = false)       // less than Tidal's best only because Tidal didn't answer: looked up again soon

    private val streams = HashMap<String, Pair<Ready, Double>>()   // id -> (stream, when looked up)

    /** The Tidal song each song was found as (song id -> Tidal id), kept between runs: a song played before starts
     *  straight away, without searching Tidal for it again. */
    private val matchFile = File(ctx.filesDir, "tidal-matches.json")
    private val MATCHES = MapSerializer(String.serializer(), Long.serializer())
    private val matches: HashMap<String, Long> by lazy {
        HashMap(readText(matchFile)?.let { runCatching { JSON.decodeFromString(
            MATCHES, it) }.getOrNull() } ?: emptyMap())
    }
    private fun remember(t: Track, tidalId: Long?) {
        if (tidalId == null || tidalId <= 0 || t.tidal == tidalId) return
        val text = synchronized(matches) { if (matches[t.id] == tidalId) return; matches[t.id] = tidalId
            JSON.encodeToString(MATCHES, matches) }
        runCatching { writeAtomic(matchFile, text) }
    }
    /** t with the Tidal song it was found as before, if any. */
    private fun known(t: Track): Track = if (t.tidal != null) t else synchronized(matches) { matches[t.id] }?.let { t.copy(tidal = it) } ?: t
    private val active = HashSet<String>()                          // being downloaded now
    var onSaved: ((String) -> Unit)? = null                         // the tagger takes it from there
    var folder: () -> String = { "" }                               // your download folder ("Music/Mine/"), "" for the app's own

    fun busy(id: String) = synchronized(active) { id in active }
    /** Whether any song is being downloaded now (tidying empty folders waits, as a download may be making its folder). */
    fun busyNow() = synchronized(active) { active.isNotEmpty() }
    fun streamQuality(id: String) = synchronized(streams) { streams[id]?.first?.quality ?: "" }
    fun streamLossy(id: String) = synchronized(streams) { streams[id]?.first?.lossyFrom }
    fun forgetStream(id: String) = synchronized(streams) { dropAhead(id); streams.remove(id) }
    /** The streams looked up already, forgotten (the stream quality changed: the next play looks again). */
    fun forgetStreams() = synchronized(streams) { dropAhead(null); streams.clear() }

    // ---- the next song fetched whole while this one plays (Settings › Playback › Fetch the next song ahead) ----
    // A streamed song starts by asking the service for its first seconds, so there is a moment of nothing before it,
    // longer on a slow connection. With this on, the song after the one playing is fetched whole into the app's cache
    // while the one before it plays, and played from there: it starts at once, and can't stall halfway. Nothing is
    // kept: only the last few are there, and they go when the app's cache is cleared or the app starts again.
    private val ahead = LinkedHashMap<String, Ready>()   // song id -> its fetched copy, as something to play
    private val aheadBusy = HashSet<String>()
    @Volatile private var aheadCleaned = false
    private fun aheadDir(): File = File(ctx.cacheDir, "ahead").apply { mkdirs() }
    /** One fetched copy let go (null: all of them). */
    private fun dropAhead(id: String?) = synchronized(ahead) {
        val gone = if (id == null) ahead.keys.toList() else listOf(id)
        gone.forEach { k -> ahead.remove(k)?.let { runCatching { File(it.uri).delete() } } }
    }
    private fun fetched(id: String): Ready? = synchronized(ahead) { ahead[id] }?.takeIf { File(it.uri).isFile && Music.cast == null }

    /** (blocking) The song fetched whole, to play from there when its turn comes. A song you have, one already fetched
     *  or being fetched, and a stream that only comes as a playlist of pieces (.m3u8) are left alone. Hi-res from Tidal,
     *  which comes in pieces, is put together as one FLAC, as a download is. Anything going wrong leaves the song to
     *  stream as it always did. */
    fun fetchAhead(t: Track) {
        if (standIn != null) { runCatching { stream(t) }; return }   // the tests: looked up only, nothing fetched
        if (!aheadCleaned) { aheadCleaned = true; runCatching { aheadDir().listFiles()?.forEach { it.delete() } } }
        val r = runCatching { stream(t) }.getOrNull() ?: return
        if (r.onDisk || r.uri.contains(".m3u8") || !(r.isDash || r.uri.startsWith("http"))) return
        synchronized(ahead) { if (t.id in ahead || !aheadBusy.add(t.id)) return }
        val dest = File(aheadDir(), safeName(t.id) + (if (!r.isDash) ".audio" else if (r.lossyFrom != null) ".mp4" else ".flac"))
        val part = File(dest.path + ".part")
        try {
            if (r.isDash) {
                val mpd = String(java.util.Base64.getDecoder().decode(r.uri.substringAfter("base64,")))
                if (r.lossyFrom != null) fetchDashRaw(mpd, part) else fetchDash(mpd, part)
            } else part.outputStream().use { o ->
                HTTP.newCall(okhttp3.Request.Builder().url(r.uri).header("User-Agent", DESKTOP_UA).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw HttpError(resp.code, "", r.uri)
                    resp.body!!.byteStream().copyTo(o, 1 shl 16)
                }
            }
            if (part.length() < 50_000 || !part.renameTo(dest)) return
            synchronized(ahead) {
                ahead[t.id] = r.copy(uri = dest.path, isDash = false)
                while (ahead.size > 4) dropAhead(ahead.keys.first())   // only the last few are kept
            }
        } catch (_: Exception) {
        } finally { runCatching { part.delete() }; synchronized(ahead) { aheadBusy.remove(t.id) } }
    }
    /** The player couldn't play a hi-res (DASH) stream (the PC's, once): streams are asked for in CD quality since. */
    @Volatile var dashFailed = false
    /** Settings › Stream quality: "flac24", "flac16" or "aac" (320 kbps), for Tidal's streams. */
    var streamQuality: () -> String = { "flac24" }

    /** Play t without saving it (blocking): the file when it is on the phone, else Tidal's stream. */
    /** A stand-in for the services, for the tests (null in the app): what a song that isn't a file plays from, or the
     *  error getting it. With it, nothing is looked up anywhere. */
    @Volatile var standIn: ((Track) -> Ready)? = null

    /** Set on the thread making a clip: Tidal is asked for CD quality (one FLAC file) rather than hi-res, which comes in
     *  pieces a clip can't be cut from. */
    private val oneFile = ThreadLocal<Boolean>()

    /** (blocking) Where a song clip takes the song's sound from: its file if you have it, else its stream, as one
     *  address that can be read from any point (never hi-res in pieces). Throws, with why in words, when there is none. */
    fun clipSource(t: Track): String {
        (t.file ?: lib.items[t.id]?.file)?.takeIf { it.isNotEmpty() }?.let { return it }
        var r = stream(t)
        if (r.isDash) { oneFile.set(true); try { r = find(t) } finally { oneFile.set(false) } }
        if (r.isDash || r.uri.contains(".m3u8")) error("this song's stream comes in pieces a clip can't be cut from; download the song and it can be clipped")
        return r.uri
    }

    fun stream(t: Track): Ready {
        if (t.isFile) return Ready(t.file!!, onDisk = true)
        standIn?.let { return it(t) }
        if (t.source == SoundCloud.KEY) {   // its own service: streamed from SoundCloud itself, never matched elsewhere or saved
            val sc = Music.sources[SoundCloud.KEY] as? SoundCloud ?: error("SoundCloud isn't set up")
            synchronized(streams) { streams[t.id]?.takeIf { now() - it.second < 600 }?.let { return it.first } }   // its addresses last a while
            val (uri, q) = sc.stream(t)
            return Ready(uri, quality = q, lossyFrom = "SoundCloud").also { r -> synchronized(streams) { streams[t.id] = r to now() } }
        }
        lib.items[t.id]?.let { return Ready(it.file, quality = it.quality, onDisk = true) }
        fetched(t.id)?.let { return it }   // fetched whole while the song before it played: it starts at once
        synchronized(streams) { streams[t.id]?.takeIf { now() - it.second < StreamRules.keptFor(it.first.soon, STREAM_FOR) }?.let { return it.first } }
        val ready = find(t)
        synchronized(streams) { streams[t.id] = ready to now() }
        return ready
    }

    /** FLAC from Tidal; else Tidal's AAC; else YouTube's own audio (no Tidal: YouTube). */
    private fun find(t: Track): Ready {
        // Whatever goes wrong on Tidal (not there, not in your country, the login, the network),
        // the next one is tried: Tidal's FLAC, Tidal's AAC, YouTube's own audio.
        var trouble: Throwable? = null
        var tries = 0
        if (tidal.connected) while (true) {
            trouble = null
            var match: kotlinx.serialization.json.JsonElement? = null
            try {
                val q = streamQuality().let { if (it == "flac24" && (dashFailed || oneFile.get() == true)) "flac16" else it }
                if (q == "aac") {   // 320 kbps picked: Tidal's AAC (its "high"), else on to YouTube
                    val m = tidal.resolve(known(t), lossy = true)
                    if (m != null) {
                        remember(t, m["id"].long)
                        lib.byTidal(m["id"].long ?: 0)?.let { return Ready(it.file, quality = it.quality, onDisk = true) }
                        return tidal.stream(m, lossy = true, quality = "HIGH").let { Ready(it.uri, it.isDash, it.quality) }
                    }
                    throw NoLosslessMatch("not on Tidal")
                }
                match = tidal.resolve(known(t))
                if (match != null) {
                    remember(t, match["id"].long)
                    lib.byTidal(match["id"].long ?: 0)?.let { return Ready(it.file, quality = it.quality, onDisk = true) }   // saved from another service
                    return tidal.stream(match, quality = if (q == "flac16") "LOSSLESS" else Tidal.QUALITY).let { Ready(it.uri, it.isDash, it.quality) }
                }
            } catch (_: NoLosslessMatch) {
            } catch (e: Exception) { trouble = e }
            // Tidal didn't answer (too many asks at once, the network for a moment): it is asked again before anything
            // less is played. Several songs looked up together (the DJ's next ones) each got YouTube's audio for
            // one such hiccup, though Tidal had them in FLAC.
            if (StreamRules.again(trouble, tries)) { tries++; runCatching { Thread.sleep(StreamRules.pauseMs(tries)) }; continue }
            if (trouble == null) lib.markMissing(t.id)   // no FLAC to download: syncs leave it alone for a while
            val hiccup = trouble != null
            try {
                (match ?: tidal.resolve(t, lossy = true))?.let { m ->
                    return tidal.stream(m, lossy = true).let { Ready(it.uri, it.isDash, it.quality, lossyFrom = "Tidal", soon = hiccup) }
                }
            } catch (e: Exception) { if (trouble == null) trouble = e }
            break
        }
        // Played from YouTube though Tidal is connected, because Tidal couldn't be reached (it plays all the same)
        trouble?.let { logWarn("“${t.title}” by ${t.artist} plays from YouTube this time: Tidal didn't answer (${it.message?.take(120)})") }
        val soon = trouble != null
        val yt = try { YouTubeAudio.find(t) } catch (e: Exception) {
            throw IOException(if (trouble != null) "Tidal: ${trouble.message}; YouTube: ${e.message}" else "YouTube: ${e.message}", e)
        }
        if (yt == null) {
            if (trouble != null) throw IOException("not on YouTube Music, and Tidal: ${trouble.message}", trouble)
            throw NoLosslessMatch(if (tidal.connected) "not on Tidal or YouTube Music"
                else "not on YouTube Music (connect Tidal in Settings for more)")
        }
        return Ready(yt.first, quality = yt.second, lossyFrom = "YouTube", soon = soon)
    }

    /** Save t (blocking) in the download quality picked (Settings): from Tidal, or from YouTube when Tidal hasn't got
     *  it or isn't connected (youtubeToo: Keep, which saves what you play, leaves those). Returns its address. */
    fun download(t: Track, root: String? = null, youtubeToo: Boolean = true, final: JsonElement? = null): String {
        lib.items[t.id]?.let { return it.file }
        if (t.source == SoundCloud.KEY) throw NoLosslessMatch("SoundCloud songs can't be downloaded: they only stream")
        synchronized(active) { if (!active.add(t.id)) error("already downloading") }
        try {
            if (tidal.connected) {
                try { return fromTidal(t, root, final = final) } catch (e: NoLosslessMatch) { if (!youtubeToo) throw e }
            } else if (!youtubeToo) error("connect Tidal in Settings first")
            return fromYouTube(t, root) ?: throw NoLosslessMatch(if (tidal.connected) "not on Tidal or YouTube Music" else "not on YouTube Music")
        } finally {
            synchronized(active) { active.remove(t.id) }
        }
    }

    /** (blocking) A download that came from YouTube got again from Tidal in FLAC (the quality picked), else (an Opus one
     *  Tidal hasn't got) from YouTube as AAC; its old file deleted once the new one is in. False when neither could be. */
    fun toFlac(id: String): Boolean {
        val old = lib.items[id] ?: return false
        if (!tidal.connected) error("connect Tidal in Settings first")
        synchronized(active) { if (!active.add(id)) error("already downloading") }
        try {
            val uri = try { fromTidal(old.track(), null) } catch (_: NoLosslessMatch) {
                // Not on Tidal: an Opus download got again as YouTube's AAC (.m4a), which plays and tags everywhere
                if (old.format != "opus") return false
                val pick = YouTubeAudio.forDownload(old.track()).firstOrNull { it.ext == "m4a" } ?: return false
                lib.items.remove(id)
                try { fromYouTube(old.track(), null, pick) } catch (e: Exception) { lib.items[id] = old; throw e }
            }
            if (uri != old.file && lib.items.values.none { it.file == old.file }) runCatching { ctx.deleteUri(old.file) }
            return true
        } finally {
            synchronized(active) { active.remove(id) }
        }
    }

    /** (blocking) A download whose file is damaged got again, as it was first got (Tidal, else YouTube), under the same
     *  song: playlists, favorites and plays stay on it. Its entry (and any other service's that shares the file) is put
     *  aside so the fetch is a real one, and put back as it was if the fetch fails; the damaged file is deleted only
     *  once the new one is in. */
    fun again(id: String, root: String? = null, final: JsonElement? = null) {
        val old = lib.items[id] ?: error("not a download")
        val sharing = synchronized(lib) { lib.items.filter { it.value.file == old.file }.also { it.keys.forEach { k -> lib.items.remove(k) } } }
        val uri = try { download(old.track(), root, final = final) } catch (e: Exception) {
            synchronized(lib) { sharing.forEach { (k, v) -> lib.items[k] = v }; lib.save() }
            throw e
        }
        synchronized(lib) {
            val now = lib.items[id]
            sharing.forEach { (k, v) -> if (k != id) lib.items[k] = v.copy(file = uri, format = now?.format ?: v.format, quality = now?.quality ?: v.quality) }
            now?.let { lib.items[id] = it.copy(plays = old.plays, last = old.last, added = old.added) }
            lib.save()
        }
        if (uri != old.file) runCatching {
            val folder = runCatching { FileTags.folderOf(ctx, old.file) }.getOrNull()
            ctx.deleteUri(old.file)
            FolderCleanup.after(listOf(folder))
        }
    }

    /** The quality picked for downloads (Settings › Downloads): "flac24", "flac16" or "mp3". */
    private val quality get() = Prefs.text(Prefs.DL_QUALITY, "flac24").ifEmpty { "flac24" }

    /** Where a download goes and its file name, with ext. */
    private fun place(t: Track, root: String?, match: JsonElement?, album: JsonElement?, ext: String): Pair<String, String> {
        val (where, name) = (root ?: folder()).takeIf { it.isNotEmpty() }?.let { r ->
            // In the artist's folder, never "Various Artists" (a song only on a compilation goes under its own artist)
            if (match != null) downloadPlace(r, album.path("artist", "name").str?.takeUnless { ReleasePick.compilation(ReleasePick.Release(null, it, "", false)) }
                ?: match.path("artist", "name").str ?: t.artist.split(",")[0], match.path("album", "title").str ?: t.album,
                album["type"].str, match["trackNumber"].long, Tidal.tidalTitle(match))
            else downloadPlace(r, t.artist.split(",")[0].trim(), t.album, null, null, t.title)
        } ?: (LocalFiles.DOWNLOADS to "${safeName(t.id)}.flac")
        return where to name.substringBeforeLast('.') + "." + ext
    }

    /** Clean or explicit (Settings › Your music): true when downloads should be the explicit version. */
    fun explicitWanted() = Prefs.text(Prefs.DL_VERSION, "explicit") != "clean"

    /** (blocking) The very Tidal song that getting t again would save, found the way the download finds it, so it can
     *  be shown before anything is downloaded; null when Tidal hasn't got it. own: a file of yours (looked up as
     *  Upgrade looks it up); else one of the app's downloads. */
    fun planAgain(t: Track, own: Boolean): JsonElement? {
        val aac = quality == "mp3" && !CAN_MP3
        return try {
            if (own) tidal.resolve(t)?.let { c -> tidalPick(Tidal.trackFromTidal(c), aac, null, mark = false) }
            else tidalPick(t, aac, null, mark = false)
        } catch (_: NoLosslessMatch) { null }
    }

    /** (blocking) Song t's explicit version (explicit) or clean one on Tidal, as a download would save it (in the quality
     *  picked, from the artist's own release where that one has the same version); null when Tidal hasn't got that
     *  version. Queued clean/explicit changes downloads it with [download]'s final. */
    fun versionToSave(t: Track, explicit: Boolean): JsonElement? {
        val aac = quality == "mp3" && !CAN_MP3
        val v = tidal.versionOf(t, explicit, aac) ?: return null
        return runCatching { tidal.bestRelease(v, aac, t.album) }.getOrNull()?.takeIf { it["explicit"].bool == explicit } ?: v
    }

    /** final: the Tidal song to save, already chosen (and shown): saved as it is, nothing looked up again. */
    private fun fromTidal(t: Track, root: String?, exact: JsonElement? = null, final: JsonElement? = null): String {
        val q = quality
        val aac = q == "mp3" && !CAN_MP3   // the phone: Tidal's own 320 kbps (AAC) in place of MP3
        val match = final ?: tidalPick(t, aac, exact)
        return saveFromTidal(t, root, q, aac, match)
    }

    /** Which Tidal song is saved for t (the release and the clean or explicit version). */
    private fun tidalPick(t: Track, aac: Boolean, exact: JsonElement?, mark: Boolean = true): JsonElement {
        val found = exact ?: tidal.resolve(t, lossy = aac) ?: run { if (mark) lib.markMissing(t.id); throw NoLosslessMatch("not on Tidal" + if (aac) "" else " in lossless") }
        // Saved from the artist's own album (deluxe first), else EP, else single; a compilation only if nothing else has it
        var match = runCatching { tidal.bestRelease(found, aac, t.album) }.getOrDefault(found)
        // Clean or explicit (Settings › Your music): the other version of the song when this one isn't the one wanted
        // and Tidal has it (a song with only one version is saved as it is)
        val want = explicitWanted()
        if (exact == null && match["explicit"].bool == !want) runCatching {
            tidal.versionOf(Tidal.trackFromTidal(match).copy(album = t.album.ifEmpty { match.path("album", "title").str ?: "" }), want, aac)
        }.getOrNull()?.let { alt -> match = runCatching { tidal.bestRelease(alt, aac, t.album) }.getOrDefault(alt) }
        return match
    }

    private fun saveFromTidal(t: Track, root: String?, q: String, aac: Boolean, match: JsonElement): String {
        val tid = match["id"].long ?: 0
        if (q != "mp3") lib.byTidal(tid)?.let { same ->
            // Already saved from another service: the same file serves both.
            lib.add(t, same.file, same.art, "flac", same.quality, tid, extra = same)
            lib.markMissing(t.id, false)
            return same.file
        }
        val st = try { if (aac) tidal.stream(match, lossy = true, quality = "HIGH")
            else tidal.stream(match, quality = if (q == "flac24") Tidal.QUALITY else "LOSSLESS") } catch (e: NoLosslessMatch) { lib.markMissing(t.id); throw e }
        val album = runCatching { tidal.albumJson(match.path("album", "id").long ?: 0) }.getOrNull()
        val cover = runCatching { httpBytes(Tidal.image(match.path("album", "cover").str, 1280)) }.getOrNull()
        val raw = File(ctx.cacheDir, "${safeName(t.id)}.part")
        val art = File(lib.artDir, "${safeName(t.id)}.jpg")
        fun artPath() = runCatching { art.writeBytes(cover ?: httpBytes(sizedThumb(t.thumb, 226))); art.path }.getOrNull()
        try {
            if (st.isDash) { if (aac) fetchDashRaw(st.manifest, raw) else fetchDash(st.manifest, raw) } else raw.outputStream().use { o ->
                HTTP.newCall(okhttp3.Request.Builder().url(st.uri).build()).execute().use { r ->
                    if (!r.isSuccessful) throw HttpError(r.code, "", st.uri)
                    r.body!!.byteStream().copyTo(o, 1 shl 16)
                }
            }
            if (aac || (st.quality.isNotEmpty() && "bit" !in st.quality)) {   // lossy from Tidal: kept as it came (.m4a)
                val (where, name) = place(t, root, match, album, "m4a")
                val uri = saveToMusic(name, where) { out -> raw.inputStream().use { it.copyTo(out) } }
                lib.add(t, uri, artPath(), "m4a", st.quality.ifEmpty { "AAC 320 kbps" }, tid, match["isrc"].str)
                lib.markMissing(t.id, false)
                return uri
            }
            val (bits, rate) = raw.inputStream().use { Flac.format(Flac.readHeader(it)) }
            if (q == "mp3") {   // the PC: an MP3 made from the FLAC, its tags and cover with it
                val tagged = File(ctx.cacheDir, "${safeName(t.id)}.flac")
                val mp3 = File(ctx.cacheDir, "${safeName(t.id)}.mp3")
                try {
                    tagged.outputStream().use { out -> Flac.retag(raw, out, tags(t, match, album), cover) }
                    if (!flacToMp3(tagged, mp3)) error("couldn't make the MP3 (ffmpeg)")
                    val (where, name) = place(t, root, match, album, "mp3")
                    val uri = saveToMusic(name, where) { out -> mp3.inputStream().use { it.copyTo(out) } }
                    lib.add(t, uri, artPath(), "mp3", "MP3 320 kbps", tid, match["isrc"].str)
                    lib.markMissing(t.id, false)
                    return uri
                } finally { tagged.delete(); mp3.delete() }
            }
            val (where, name) = place(t, root, match, album, "flac")
            val uri = saveToMusic(name, where) { out -> Flac.retag(raw, out, tags(t, match, album), cover) }
            lib.add(t, uri, artPath(), "flac", "$bits-bit/${"%g".format(rate / 1000.0)} kHz", tid, match["isrc"].str)
            lib.markMissing(t.id, false)
            onSaved?.invoke(t.id)
            return uri
        } finally {
            raw.delete()
        }
    }

    /** YouTube's audio of t: the best it has (Opus ~160 kbps, or AAC 256 kbps where it's offered), saved without
     *  re-encoding: Opus moved from YouTube's WebM into an .opus file with the song's name, artist, album and cover;
     *  AAC kept as it comes (.m4a). YouTube has no lossless audio, so a FLAC made from it would only be bigger. If the
     *  first stream fails, the next (AAC) is tried. Null if YouTube Music hasn't got it. */
    private fun fromYouTube(t: Track, root: String?): String? {
        val picks = YouTubeAudio.forDownload(t)
        if (picks.isEmpty()) return null
        var last: Exception? = null
        for (pick in picks) {
            try { return fromYouTube(t, root, pick) } catch (e: Exception) {
                last = e
                logWarn("YouTube download (${pick.label}) of ${t.artist} – ${t.title}", e)   // (the next way is tried; the download says if none worked)
            }
        }
        throw last ?: IllegalStateException("YouTube wouldn't send the audio")
    }

    private fun fromYouTube(t: Track, root: String?, pick: YouTubeAudio.Pick): String {
        val raw = File(ctx.cacheDir, "${safeName(t.id)}.yt")
        try {
            var ok = false
            repeat(20) {   // YouTube's address answers only a moment after it's made
                if (ok) return@repeat
                ok = runCatching {
                    raw.outputStream().use { o ->
                        HTTP.newCall(okhttp3.Request.Builder().url(pick.url).header("User-Agent", DESKTOP_UA).build()).execute().use { r ->
                            if (!r.isSuccessful) throw HttpError(r.code, "", pick.url)
                            r.body!!.byteStream().copyTo(o, 1 shl 16)
                        }
                    }
                    raw.length() > 0
                }.getOrDefault(false)
                if (!ok) Thread.sleep(500)
            }
            if (!ok) error("YouTube wouldn't send the audio")
            val cover = runCatching { httpBytes(sizedThumb(t.thumb, 544)) }.getOrNull()
            val ext = if (pick.ext == "webm") "opus" else pick.ext
            val (where, name) = place(t, root, null, null, ext)
            val uri = if (ext == "opus") {
                // The Opus audio as it is, in the Ogg file players and tag editors read (checked before anything is saved)
                val audio = OggOpus.readWebm(raw.readBytes())
                val tags = OggOpus.Tags(t.title, t.artist, t.album, cover)
                saveToMusic(name, where) { out -> OggOpus.write(audio, out, tags) }
            } else saveToMusic(name, where) { out -> raw.inputStream().use { it.copyTo(out) } }
            val art = File(lib.artDir, "${safeName(t.id)}.jpg")
            val artPath = runCatching { art.writeBytes(cover ?: httpBytes(sizedThumb(t.thumb, 544))); art.path }.getOrNull()
            lib.add(t, uri, artPath, ext, "YouTube · ${pick.label}")
            lib.markMissing(t.id, false)
            return uri
        } finally {
            raw.delete()
        }
    }

    /** A DASH stream as it is (the init segment, then each media segment): an MP4 file (Tidal's AAC). */
    private fun fetchDashRaw(mpd: String, dest: File) {
        val init = Regex("initialization=\"([^\"]+)\"").find(mpd)!!.groupValues[1].replace("&amp;", "&")
        val media = Regex("media=\"([^\"]+)\"").find(mpd)!!.groupValues[1].replace("&amp;", "&")
        val start = Regex("startNumber=\"(\\d+)\"").find(mpd)?.groupValues?.get(1)?.toInt() ?: 1
        var count = 0
        for (m in Regex("<S\\s([^>]*)/>").findAll(mpd)) count += (Regex("r=\"(\\d+)\"").find(m.groupValues[1])?.groupValues?.get(1)?.toInt() ?: 0) + 1
        dest.outputStream().buffered().use { out ->
            out.write(httpBytes(init))
            for (n in start until start + count) out.write(httpBytes(media.replace("\$Number\$", n.toString())))
        }
    }

    /** How an Upgrade went: (your song, the download's id) swapped; songs Tidal hasn't got; kept as good already (why). */
    class Upgraded {
        val done = mutableListOf<Pair<Track, String>>()
        val missing = mutableListOf<Track>()
        val kept = mutableListOf<Pair<Track, String>>()
        val failed = mutableListOf<Pair<Track, String>>()
    }

    /** Your own files that Tidal has better (blocking): a lossy file (MP3, AAC…) for its FLAC, a
     *  16-bit one for its hi-res. force: every song Tidal has. The old files stay (the app puts them away). */
    fun upgrade(tracks: List<Track>, force: Boolean, stop: () -> Boolean, rootOf: (Track) -> String? = { null },
                picks: Map<String, JsonElement> = emptyMap(), progress: (Int) -> Unit): Upgraded {
        val out = Upgraded()
        val next = AtomicInteger()
        val done = AtomicInteger()
        fun work() {
            while (!stop()) {
                val t = tracks.getOrNull(next.getAndIncrement()) ?: return
                try {
                    val pick = picks[t.id]   // chosen and shown already (a damaged file's new copy): that very song
                    val (lossless, bits) = if (pick != null) false to 0 else fileQuality(t.file!!)
                    if (pick != null) {
                        val new = Tidal.trackFromTidal(pick)
                        if (lib.isFlac(new.id) && lib.file(new.id) == t.file) synchronized(out) { out.kept += t to "it's Tidal's FLAC already" }
                        else {
                            if (!lib.isFlac(new.id)) download(new, rootOf(t), final = pick)
                            synchronized(out) { out.done += t to new.id }
                        }
                    }
                    else if (!force && lossless && bits >= 24) synchronized(out) { out.kept += t to "yours is $bits-bit lossless: as good as Tidal gets" }
                    else {
                        val c = tidal.resolve(t)
                        if (c == null) synchronized(out) { out.missing += t }
                        else if (!force && lossless && "HIRES_LOSSLESS" !in c.path("mediaMetadata", "tags").list.map { it.str })
                            synchronized(out) { out.kept += t to "yours is ${if (bits > 0) "$bits-bit " else ""}lossless; Tidal's is 16-bit too" }
                        else {
                            val new = Tidal.trackFromTidal(c)
                            if (lib.isFlac(new.id) && lib.file(new.id) == t.file)   // that download itself: nothing to swap
                                synchronized(out) { out.kept += t to "it's Tidal's FLAC already" }
                            else {
                                if (!lib.isFlac(new.id)) download(new, rootOf(t))   // beside your songs, in the folder it came from
                                synchronized(out) { out.done += t to new.id }
                            }
                        }
                    }
                } catch (_: NoLosslessMatch) {
                    synchronized(out) { out.missing += t }
                } catch (e: Exception) {
                    synchronized(out) { out.failed += t to (e.message ?: e.javaClass.simpleName) }
                }
                progress(done.incrementAndGet())
            }
        }
        (1..SONGS_AT_ONCE).map { thread { work() } }.forEach { it.join() }
        return out
    }

    /** (lossless, bits per sample) of a file on the phone: FLAC's header says, else its type. */
    fun fileQuality(uri: String): Pair<Boolean, Int> {
        val cr = ctx
        val mime = runCatching { cr.mimeOf(uri) }.getOrNull() ?: ""
        if ("flac" in mime) {
            val bits = runCatching { cr.openIn(uri)!!.use { Flac.format(Flac.readHeader(it)).first } }.getOrNull()
            return true to (bits ?: 16)
        }
        val lossless = listOf("wav", "x-wav", "aiff", "x-aiff", "alac", "x-ape", "x-wavpack").any { mime.endsWith(it) }
        return lossless to (if (lossless) 16 else 0)
    }

    /** How a batch of downloads went. */
    class Outcome {
        var saved = 0
        val missing = mutableListOf<Track>()                  // Tidal has no lossless copy
        val failed = mutableListOf<Pair<Track, String>>()     // and why

        fun summary(): String = buildList {
            if (saved > 0) add("Saved $saved song${if (saved != 1) "s" else ""}")
            if (missing.isNotEmpty()) add("${missing.size} couldn't be found on Tidal or YouTube (" +
                missing.take(2).joinToString(", ") { it.title } + if (missing.size > 2) "…)" else ")")
            if (failed.isNotEmpty()) add("${failed.size} failed: ${failed.first().second.take(90)}")
        }.joinToString(". ").ifEmpty { "Nothing new to download" }
    }

    /** Save every song in tracks as FLAC, workers songs at a time (blocking); progress(done) runs
     *  after each song. Songs Tidal recently didn't have are left alone, unless force (you asked). */
    fun sync(tracks: List<Track>, force: Boolean = false, workers: Int = 1, progress: (Int) -> Unit): Outcome {
        val out = Outcome()
        val next = AtomicInteger()
        val done = AtomicInteger()
        fun work() {
            while (true) {
                val t = tracks.getOrNull(next.getAndIncrement()) ?: return
                if (!(lib.has(t.id) || (!force && lib.recentlyMissing(t.id)) || busy(t.id) || t.isFile)) {
                    try {
                        download(t)
                        synchronized(out) { out.saved++ }
                    } catch (_: NoLosslessMatch) {
                        synchronized(out) { out.missing += t }
                    } catch (e: Exception) {
                        synchronized(out) { out.failed += t to (e.message ?: e.javaClass.simpleName) }
                        logWarn("download ${t.id} (${t.artist} - ${t.title}) failed", e)
                    }
                }
                progress(done.incrementAndGet())
            }
        }
        if (workers <= 1) work() else (1..workers).map { thread { work() } }.forEach { it.join() }
        return out
    }

    private fun tags(t: Track, c: JsonElement, album: JsonElement?): Map<String, List<String>> = linkedMapOf(
        "title" to listOf(Tidal.tidalTitle(c)),
        "artist" to c["artists"].list.mapNotNull { it["name"].str },
        "album" to listOfNotNull(c.path("album", "title").str?.let { releaseName(it, album["type"].str) }),   // "Title - Single", as taggers
        "albumartist" to listOfNotNull(album.path("artist", "name").str),
        // Tidal gives no genre: Apple's, Deezer's or Last.fm's for the song
        "genre" to listOfNotNull(runCatching { GenreLookup.of(c["artists"].list.firstOrNull()?.get("name").str ?: t.artist,
            Tidal.tidalTitle(c), c.path("album", "title").str ?: t.album) }.getOrNull()?.ifEmpty { null }),
        "tracknumber" to listOfNotNull(c["trackNumber"].long?.takeIf { it > 0 }?.toString()),
        "discnumber" to listOfNotNull(c["volumeNumber"].long?.takeIf { it > 0 }?.toString()),
        "date" to listOfNotNull((album["releaseDate"].str ?: c["streamStartDate"].str)?.take(10)),
        "isrc" to listOfNotNull(c["isrc"].str),
        "copyright" to listOfNotNull(c["copyright"].str),
        "tidal_id" to listOf(c["id"].long.toString()),
        "youtube_music_id" to listOfNotNull(t.id.takeIf { t.source == "ytm" }),
        "apple_music_id" to listOfNotNull(t.apple.takeIf { t.source == "apple" }),
        "spotify_id" to listOfNotNull(t.spotify.takeIf { t.source == "spotify" }),
    ).filterValues { it.isNotEmpty() }

    /** Hi-res: the init segment and each media segment of the manifest, as one FLAC. */
    private fun fetchDash(mpd: String, dest: File) {
        val init = Regex("initialization=\"([^\"]+)\"").find(mpd)!!.groupValues[1].replace("&amp;", "&")
        val media = Regex("media=\"([^\"]+)\"").find(mpd)!!.groupValues[1].replace("&amp;", "&")
        val start = Regex("startNumber=\"(\\d+)\"").find(mpd)?.groupValues?.get(1)?.toInt() ?: 1
        var count = 0
        var samples = 0L
        for (m in Regex("<S\\s([^>]*)/>").findAll(mpd)) {
            val attrs = m.groupValues[1]
            val d = Regex("d=\"(\\d+)\"").find(attrs)?.groupValues?.get(1)?.toLong() ?: 0
            val r = Regex("r=\"(\\d+)\"").find(attrs)?.groupValues?.get(1)?.toInt() ?: 0
            count += r + 1
            samples += d * (r + 1)
        }
        val timescale = Regex("timescale=\"(\\d+)\"").find(mpd)?.groupValues?.get(1)?.toLong() ?: 0
        val rate = Regex("audioSamplingRate=\"(\\d+)\"").find(mpd)?.groupValues?.get(1)?.toLong() ?: timescale
        val total = if (timescale > 0) samples * rate / timescale else 0
        // The segments are a few seconds each: fetch several at once (one by one, each waited
        // for a round trip), and hand them on in order.
        val pool = Executors.newFixedThreadPool(SEGMENTS_AHEAD)
        try {
            val segments = sequence {
                val pending = ArrayDeque<Future<ByteArray>>()
                var n = start
                while (n < start + count || pending.isNotEmpty()) {
                    while (n < start + count && pending.size < SEGMENTS_AHEAD) {
                        val url = media.replace("\$Number\$", (n++).toString())
                        pending.addLast(pool.submit(Callable { httpBytes(url) }))
                    }
                    yield(try { pending.removeFirst().get() } catch (e: ExecutionException) { throw e.cause ?: e })
                }
            }
            dest.outputStream().buffered().use { out -> Flac.fromMp4(httpBytes(init), segments, out, total) }
        } finally {
            pool.shutdownNow()
        }
    }

    /** A new file in Music/Omarchy Music/, written by write; returns its content:// address. */
    private fun saveToMusic(name: String, where: String, write: (java.io.OutputStream) -> Unit): String = ctx.newMusicFile(name, where, write)

    companion object {
        const val STREAM_FOR = 15 * 60   // seconds a looked-up stream address is trusted for
        const val SEGMENTS_AHEAD = 8     // hi-res segments fetched at once
        const val SONGS_AT_ONCE = 3      // songs a Download you ask for saves at once
    }
}
