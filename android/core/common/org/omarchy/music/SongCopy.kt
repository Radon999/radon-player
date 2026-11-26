package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Other device's songs: the song's own file copied from your other device, over the connection Sync uses
 *  ([SongTransferRules] has the rules). The phone always starts it, as the PC can't reach the phone: it fetches files
 *  from the PC (GET /song-file, only files of the PC's own music), and sends its own files to the PC (PUT /song-file)
 *  when the PC has asked for them. The PC asks by keeping a short list of the files it wants; the phone sees how many
 *  are waiting in the answer to the question Sync already asks every 20 seconds while the app is on its screen (every 2
 *  minutes away from it), and sends them then: nothing is asked more often for this, and nothing moves unless you asked.
 *  Each file goes across in pieces it can carry on from after a drop, never whole in memory, and is checked against
 *  its checksum and size before it's saved, as downloads are (by artist and album), into the download folder. */
object SongCopy {
    /** Prefs: Copy the file from your other device: [SongTransferRules.ALWAYS], NOT_FOUND (the default) or NEVER. */
    const val PREF = "copy_from_other"

    private var picked by mutableStateOf(0)   // bumps when the setting is changed here (the screens follow it)
    private fun modeNow(): String { picked; return SongTransferRules.mode(Prefs.text(PREF)) }
    fun choose(m: String) { Prefs.setText(PREF, SongTransferRules.mode(m)); picked++ }
    /** The setting as it is. */
    val current: String get() = modeNow()

    /** The line shown while files go across ("Copying from your PC · 2 of 5 · 40%"); "" when none are. */
    var note by mutableStateOf("")
        internal set
    /** Bumps when a song's state changes (its row says so). */
    var version by mutableStateOf(0)
        private set

    private val isPc get() = RemoteHost.supported
    /** The other device, as the rows say it. */
    val other: String get() = if (isPc) "your phone" else "your PC"
    private val service get() = if (Music.tidal.connected) "Tidal" else "YouTube"

    /** What is happening to a listed song now, by its id: "copying", "waiting" (the PC: asked the phone), "failed: …". */
    private val state = ConcurrentHashMap<String, String>()
    /** Songs to copy if the streaming service hasn't got them (only when it can't be found), by the listed song's id. */
    private val fallback = ConcurrentHashMap<String, Track>()

    private fun changed() = Music.scope.launch { version++ }

    /** What a song's row in Other device's songs says: which way it will come, or how it's coming. */
    fun how(t: Track): String {
        version
        state[t.id]?.let { s ->
            return when {
                s == "copying" -> "⇄ coming from $other…"
                s == "waiting" -> "⇄ asked $other: it sends it when it next checks in"
                s.startsWith("failed: ") -> "⇄ couldn't copy: " + s.removePrefix("failed: ")
                else -> s
            }
        }
        if (isPc && wantedNow().any { it.id == t.id }) return "⇄ asked $other: it sends it when it next checks in"
        val theirs = LibraryMatch.theirsOf(t.id)?.takeIf { !it.file.isNullOrEmpty() }
        return SongTransferRules.label(SongTransferRules.way(modeNow(), theirs != null), service, other)
    }

    /** Get these songs here, each the way the setting says: copied from the other device, or downloaded (and copied
     *  only if the streaming service hasn't got it). */
    fun get(tracks: List<Track>) {
        val m = modeNow()
        val copy = ArrayList<Pair<Track, Track>>(); val dl = ArrayList<Track>()
        for (t in tracks) {
            val theirs = LibraryMatch.theirsOf(t.id)?.takeIf { !it.file.isNullOrEmpty() }
            when (SongTransferRules.way(m, theirs != null)) {
                SongTransferRules.Way.COPY -> copy += t to theirs!!
                SongTransferRules.Way.DOWNLOAD -> dl += t
                SongTransferRules.Way.DOWNLOAD_ELSE_COPY -> { fallback[t.id] = theirs!!; dl += t }
            }
        }
        if (dl.isNotEmpty()) Music.download(dl, checked = true)
        if (copy.isNotEmpty()) copy(copy)
    }

    /** (Downloads, after a batch) The songs the streaming service hadn't got: the ones waiting to be copied instead are. */
    fun notFound(missing: List<Track>) {
        val copy = missing.mapNotNull { t -> fallback.remove(t.id)?.let { t to it } }
        if (copy.isNotEmpty()) copy(copy)
    }

    private fun copy(pairs: List<Pair<Track, Track>>) {
        if (isPc) ask(pairs) else pull(pairs)
    }

    /** Stop: what's going across now stops (nothing half-copied is kept), and nothing else waits. On the PC, what was
     *  asked of the phone is taken back. */
    fun cancel() {
        stop.set(true)
        runCatching { call?.cancel() }
        synchronized(queue) { queue.clear() }
        cancelledAt = System.currentTimeMillis()
        if (isPc) { wanted = emptyList(); runCatching { wantedFile.delete() } }
        state.clear(); fallback.clear()
        note = ""; changed()
        Music.toast("Copying stopped")
    }
    /** When Stop was last tapped (the PC: a file coming in from before then is let go). */
    @Volatile var cancelledAt = 0L
        private set
    val busy: Boolean get() = note.isNotEmpty()

    // ---- the phone: fetching files from the PC ----

    private val stop = AtomicBoolean(false)
    @Volatile private var call: okhttp3.Call? = null
    private val queue = ArrayDeque<Pair<Track, Track>>()
    private val pulling = AtomicBoolean(false)
    private val client by lazy { HTTP.newBuilder().readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build() }

    private class Stopped : IOException("stopped")
    /** The other device couldn't be reached at all. */
    private class Unreachable(e: Throwable) : IOException(e.message, e)

    private fun pull(pairs: List<Pair<Track, Track>>) {
        if (!Remote.paired) { Music.toast("Pair with the PC first (Settings › Play on)"); return }
        synchronized(queue) { pairs.forEach { p -> if (queue.none { it.first.id == p.first.id }) queue.addLast(p) } }
        pairs.forEach { state[it.first.id] = "copying" }; changed()
        if (!pulling.compareAndSet(false, true)) return
        stop.set(false)
        val host = Remote.host; val headers = Remote.headers()
        if (pairs.isNotEmpty()) Music.toast("Copying %,d song%s from $other".format(pairs.size, if (pairs.size != 1) "s" else ""))
        Music.bg({
            var copied = 0; var already = 0; var failed = 0; var why = ""; var done = 0
            while (!stop.get()) {
                val (shown, theirs) = synchronized(queue) { queue.removeFirstOrNull() } ?: break
                val total = done + 1 + synchronized(queue) { queue.size }
                fun say(pct: Int?) = Music.scope.launch { note = SongTransferRules.progress("Copying from $other", done, total, pct) }
                say(null)
                try {
                    if (Music.lib.has(shown.id)) already++ else { pullOne(host, headers, shown, theirs) { say(it) }; copied++ }
                    state.remove(shown.id)
                } catch (_: Stopped) { break
                } catch (e: Unreachable) {
                    failed += 1 + synchronized(queue) { queue.size.also { queue.clear() } }
                    why = SongTransferRules.unreachable(other)
                    state.clear(); break
                } catch (e: Exception) {
                    failed++; val w = reason(e); if (why.isEmpty()) why = w
                    state[shown.id] = "failed: $w"
                    logWarn("copying ${theirs.file} from $other", e)
                }
                done++
                Music.scope.launch { Music.savedVersion++; Music.musicVersion++ }
            }
            Triple(copied to already, failed, why)
        }) { r, err ->
            pulling.set(false); note = ""; changed()
            Music.savedVersion++; Music.musicVersion++
            if (stop.get()) return@bg
            val (ca, f, w) = r ?: Triple(0 to 0, 1, reason(err))
            Music.toast(if (w == SongTransferRules.unreachable(other)) w else SongTransferRules.summary(ca.first, ca.second, f, w, other))
            if (synchronized(queue) { queue.isNotEmpty() }) pull(emptyList())   // more asked for meanwhile
        }
    }

    private fun reason(e: Throwable?): String = when {
        e == null -> "something went wrong"
        e is Unreachable -> SongTransferRules.unreachable(other)
        e is HttpError && e.code == 404 && "no such thing" in e.body -> "update the app on $other first"
        e is HttpError && e.code == 401 -> "$other forgot this one: pair again (Play on)"
        e is HttpError -> runCatching { JSON.parseToJsonElement(e.body)["error"].str }.getOrNull() ?: "HTTP ${e.code}"
        else -> e.message?.take(90) ?: e.javaClass.simpleName
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** (blocking) A file's SHA-256, read through once (never whole in memory). */
    fun sha256(ins: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 16)
        ins.use { while (true) { val n = it.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun connectionLost(e: Throwable) = e is java.net.ConnectException || e is java.net.UnknownHostException ||
        e is java.net.NoRouteToHostException || (e is java.net.SocketTimeoutException && e.message?.contains("connect") == true)

    /** (blocking) One file from the PC: fetched into the app's cache (carried on from where it stopped after a drop, a
     *  few times), checked (its size and checksum), then saved as downloads are and added to your music. */
    private fun pullOne(host: String, headers: Map<String, String>, shown: Track, theirs: Track, progress: (Int?) -> Unit) {
        val ref = theirs.file!!
        val dir = File(Music.ctx.cacheDir, "copies").apply { mkdirs() }
        val key = sha1(ref).take(20)
        val part = File(dir, "$key.part"); val shaFile = File(dir, "$key.sha")
        var sha = ""; var size = -1L; var name = ""
        var tries = 0
        try {
            while (true) {
                if (stop.get()) throw Stopped()
                val have = if (part.isFile && readText(shaFile) != null) part.length() else 0L
                val url = "http://$host/song-file?path=" + URLEncoder.encode(ref, "UTF-8")
                val req = Request.Builder().url(url).header("User-Agent", APP_UA).apply {
                    headers.forEach { (k, v) -> header(k, v) }
                    if (have > 0) header("Range", "bytes=$have-")
                }.build()
                try {
                    val c = client.newCall(req); call = c
                    var again = false
                    c.execute().use { r ->
                        if (r.code == 416) { part.delete(); shaFile.delete(); again = true; return@use }
                        if (!r.isSuccessful) throw HttpError(r.code, r.body?.string() ?: "", url)
                        sha = r.header("X-Sha256") ?: ""; size = r.header("X-Size")?.toLongOrNull() ?: -1L
                        name = URLDecoder.decode(r.header("X-Name") ?: "", "UTF-8")
                        if (!SongTransferRules.validSha(sha) || size <= 0) throw HttpError(502, "{\"error\":\"$other didn't say what the file is\"}", url)
                        val append = r.code == 206 && have > 0
                        // The file changed over there since the part kept here: from the start again
                        if (append && readText(shaFile) != sha) { part.delete(); shaFile.delete(); again = true; return@use }
                        if (!append) writeAtomic(shaFile, sha)
                        FileOutputStream(part, append).use { out ->
                            val ins = r.body!!.byteStream()
                            val buf = ByteArray(1 shl 16)
                            var got = if (append) have else 0L
                            var shown0 = -1
                            while (true) {
                                if (stop.get()) throw Stopped()
                                val n = ins.read(buf); if (n < 0) break
                                out.write(buf, 0, n); got += n
                                val pct = (got * 100 / size).toInt()
                                if (pct != shown0) { shown0 = pct; progress(pct) }
                            }
                        }
                    }
                    if (again) continue
                    if (part.length() == size) break
                    throw IOException("the copy stopped part-way")
                } catch (e: Stopped) { throw e
                } catch (e: HttpError) { throw e
                } catch (e: IOException) {
                    if (stop.get()) throw Stopped()
                    if (connectionLost(e) && (tries >= 1 || part.length() == 0L)) throw Unreachable(e)
                    if (++tries > 5) throw e
                    Thread.sleep(2000L * tries)   // the network for a moment: carried on from where it stopped
                }
            }
            progress(100)
            if (sha256(part.inputStream()) != sha) throw IOException("it didn't come through whole (its checksum differs): try again")
            val orig = name.ifEmpty { ref.substringAfterLast('/').substringAfterLast('\\') }
            val (where, fname) = SongTransferRules.savePlace(Music.downloadFolder(), theirs.artist, theirs.album, theirs.title, orig)
                ?: throw IOException("“$orig” isn't a song file")
            val uri = Music.ctx.newMusicFile(fname, where) { out -> part.inputStream().use { it.copyTo(out, 1 shl 16) } }
            addToLibrary(shown.id, shown.source, sha, theirs, uri, fname, shown.thumb)
        } catch (e: Stopped) {
            part.delete(); shaFile.delete(); throw e
        } finally {
            call = null
            if (part.isFile && part.length() == size && size > 0) { part.delete(); shaFile.delete() }
        }
    }

    /** A copied file into your music, as a download of the song listed (its cover from the file). */
    fun addToLibrary(id: String?, source: String?, sha: String, song: Track, uri: String, fname: String, thumb: String = "") {
        val (lid, src) = SongTransferRules.libraryId(id, source, sha)
        val ext = SongTransferRules.audioExt(fname) ?: "flac"
        val art = runCatching {
            embeddedPicture(Music.ctx, uri)?.let { b -> File(Music.lib.artDir, "${safeName(lid)}.jpg").apply { writeBytes(b) }.path }
        }.getOrNull()
        val quality = if (ext != "flac") "" else runCatching {
            Music.ctx.openIn(uri)!!.use { Flac.format(Flac.readHeader(it)) }.let { (bits, rate) -> "$bits-bit/${"%g".format(rate / 1000.0)} kHz" }
        }.getOrDefault("")
        Music.lib.add(Track(lid, src, song.title, song.artist, song.album, song.duration, thumb = thumb), uri, art, ext, quality)
        Music.lib.markMissing(lid, false)
    }

    // ---- the phone: sending its files to the PC, when the PC asked ----

    @Serializable
    data class Wanted(val ref: String, val title: String = "", val artist: String = "", val album: String = "", val id: String = "",
                      val source: String = "", val duration: Double = 0.0, val asked: Long = 0)
    private val wantedSer = ListSerializer(Wanted.serializer())
    private val pushing = AtomicBoolean(false)

    /** (the phone, from Sync's question) The PC has files waiting to be sent: send them now, one by one. */
    fun sendWanted() {
        if (!Remote.paired || !pushing.compareAndSet(false, true)) return
        stop.set(false)
        val host = Remote.host; val headers = Remote.headers()
        Music.bg({
            val want = JSON.decodeFromString(wantedSer, http("http://$host/song-file/wanted", headers))
            val own = ownFiles()
            var sent = 0; var already = 0; var failed = 0; var why = ""
            for ((i, w) in want.withIndex()) {
                if (stop.get()) break
                fun say(pct: Int?) = Music.scope.launch { note = SongTransferRules.progress("Sending to $other", i, want.size, pct) }
                say(null)
                val mine = own[w.ref]
                if (mine == null || !SongTransferRules.servable(w.ref, own.keys)) {
                    runCatching { drop(host, headers, w.ref, "it isn't on the phone any more") }; failed++; continue
                }
                try {
                    if (pushOne(host, headers, w, mine) { say(it) }) sent++ else already++
                } catch (_: Stopped) { break
                } catch (e: Unreachable) { why = SongTransferRules.unreachable(other); failed += want.size - i; break
                } catch (e: Exception) {
                    failed++; if (why.isEmpty()) why = reason(e)
                    logWarn("sending ${w.ref} to $other", e)
                    if (e is HttpError && e.code in setOf(400, 403, 422)) runCatching { drop(host, headers, w.ref, reason(e)) }
                    if (e is HttpError && e.code == 410) break   // stopped on the PC
                }
            }
            Triple(sent, already, failed to why)
        }) { r, err ->
            pushing.set(false); note = ""
            if (r == null) { if (err !is Stopped) logWarn("sending songs to $other", err); return@bg }
            val (s, a, fw) = r
            if (s + a + fw.first > 0) Music.toast(if (fw.second == SongTransferRules.unreachable(other)) fw.second
                else SongTransferRules.summary(s, a, fw.first, fw.second, other).replace("Copied", "Sent").replace("from $other", "to $other"))
        }
    }

    /** This device's own song files (your folders' and your downloads'), by where each is. */
    private fun ownFiles(): Map<String, Track> {
        val out = HashMap<String, Track>()
        runCatching { Music.local.files.forEach { t -> t.file?.takeIf { it.isNotEmpty() }?.let { out[it] = t } } }
        runCatching { synchronized(Music.lib) { Music.lib.items.values.forEach { e -> if (e.file.isNotEmpty()) out[e.file] = e.track() } } }
        return out
    }

    private fun drop(host: String, headers: Map<String, String>, ref: String, why: String) =
        http("http://$host/song-file/drop", headers, jsonBody(buildJsonObject { put("ref", ref); put("why", why) }))

    /** (blocking) One of this phone's files to the PC: its checksum worked out, then sent from where the PC has it up to
     *  (all of it, or the rest after a drop). True if it was sent, false if the PC had it already. */
    private fun pushOne(host: String, headers: Map<String, String>, w: Wanted, mine: Track, progress: (Int?) -> Unit): Boolean {
        val ctx = Music.ctx
        val ref = w.ref
        val name = ctx.displayName(ref) ?: ref.substringAfterLast('/')
        if (SongTransferRules.audioExt(name) == null) throw IOException("“$name” isn't a song file")
        var size = 0L
        val sha = run {
            val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(1 shl 16)
            (ctx.openIn(ref) ?: throw IOException("the file can't be opened")).use { ins ->
                while (true) { if (stop.get()) throw Stopped(); val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n); size += n }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val q = "sha256=$sha&size=$size&name=${enc(name)}&ref=${enc(ref)}&title=${enc(mine.title.ifBlank { w.title })}" +
            "&artist=${enc(mine.artist.ifBlank { w.artist })}&album=${enc(mine.album.ifBlank { w.album })}&id=${enc(w.id)}&source=${enc(w.source)}" +
            "&duration=${mine.duration.takeIf { it > 0 } ?: w.duration}"
        var tries = 0
        while (true) {
            if (stop.get()) throw Stopped()
            try {
                val st = httpJson("http://$host/song-file/part?$q", headers)
                if (st["done"].bool == true) return false
                val from = st["have"].long ?: 0L
                val url = "http://$host/song-file?$q&offset=$from"
                val body = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = size - from
                    override fun writeTo(sink: BufferedSink) {
                        (ctx.openIn(ref) ?: throw IOException("the file can't be opened")).use { ins ->
                            var skip = from
                            while (skip > 0) { val k = ins.skip(skip); if (k <= 0) { if (ins.read() < 0) throw IOException("the file got shorter"); skip-- } else skip -= k }
                            val buf = ByteArray(1 shl 16); var sent = from; var shown0 = -1
                            while (true) {
                                if (stop.get()) throw Stopped()
                                val n = ins.read(buf); if (n < 0) break
                                sink.write(buf, 0, n); sent += n
                                val pct = (sent * 100 / size).toInt()
                                if (pct != shown0) { shown0 = pct; progress(pct) }
                            }
                        }
                    }
                }
                val req = Request.Builder().url(url).header("User-Agent", APP_UA).apply { headers.forEach { (k, v) -> header(k, v) } }.put(body).build()
                val c = client.newCall(req); call = c
                c.execute().use { r ->
                    val text = r.body?.string() ?: ""
                    if (r.code == 409) return@use   // the PC has another amount of it than thought: asked again
                    if (!r.isSuccessful) throw HttpError(r.code, text, url)
                    return JSON.parseToJsonElement(text)["already"].bool != true
                }
            } catch (e: Stopped) { throw e
            } catch (e: HttpError) { throw e
            } catch (e: IOException) {
                if (stop.get()) throw Stopped()
                if (connectionLost(e) && tries >= 1) throw Unreachable(e)
                if (++tries > 5) throw e
                Thread.sleep(2000L * tries)
            } finally { call = null }
        }
    }

    // ---- the PC: what it has asked the phone for ----

    private val wantedFile get() = File(Music.ctx.filesDir, "copy-wanted.json")
    @Volatile private var wanted: List<Wanted>? = null
    private fun wantedNow(): List<Wanted> = wanted ?: (readText(wantedFile)?.let { runCatching { JSON.decodeFromString(wantedSer, it) }.getOrNull() } ?: emptyList()).also { wanted = it }
    private fun saveWanted(w: List<Wanted>) { wanted = w; runCatching { writeAtomic(wantedFile, JSON.encodeToString(wantedSer, w)) }; changed() }

    /** (the PC) Ask the phone for these files: it sends them the next time it checks in. */
    private fun ask(pairs: List<Pair<Track, Track>>) {
        val now = System.currentTimeMillis() / 1000
        val add = pairs.map { (shown, theirs) -> Wanted(theirs.file!!, theirs.title, theirs.artist, theirs.album, shown.id, shown.source, theirs.duration, now) }
        synchronized(this) { saveWanted((wantedNow().filter { w -> add.none { it.ref == w.ref } } + add)) }
        pairs.forEach { state.remove(it.first.id) }
        val phone = LibraryMatch.from.ifEmpty { "your phone" }
        Music.toast(if (!RemoteHost.running) "Turn on Phone remote first (Settings › Remote & sync), so $phone can send them"
            else SongTransferRules.asked(pairs.size, phone))
    }

    /** (the PC, the server's thread) How many files wait for the phone (said in the answer to its question). */
    fun wantedCount(): Int = wantedNow().size
    /** (the PC, the server's thread) The files waiting, for the phone. */
    fun wantedText(): String = JSON.encodeToString(wantedSer, wantedNow())

    /** (the PC, the server's thread) A file the phone sent has landed (or the phone can't send it): off the list. */
    fun received(ref: String?, failed: String? = null) {
        if (ref == null) return
        val w = wantedNow().firstOrNull { it.ref == ref }
        synchronized(this) { saveWanted(wantedNow().filter { it.ref != ref }) }
        if (failed != null && w != null) {
            state[w.id] = "failed: $failed"
            Music.scope.launch { Music.toast("“${w.title}” couldn't be copied from ${LibraryMatch.from.ifEmpty { "your phone" }}: $failed") }
        }
        Music.scope.launch { Music.savedVersion++; Music.musicVersion++; version++ }
    }
}
