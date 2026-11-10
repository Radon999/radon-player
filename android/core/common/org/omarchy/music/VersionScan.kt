package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlin.math.abs

/** Scanner › Clean and explicit versions: your songs that are the other version from the one picked in Settings › Your
 *  music › Clean or explicit (a clean edit when you want explicit, or the other way round), where Tidal has the one you
 *  want. Each is shown with the version it would be replaced by; both can be played to check, and you choose to replace
 *  it (the wanted version downloaded, the old file then deleted, to the trash where there is one) or leave it.
 *
 *  Which version a song is: Tidal's own word for a song saved from Tidal; else its ISRC (a clean edit has its own),
 *  looked up on Tidal; else its tags (iTunes' advisory: explicit or clean); else its name ("(Clean)", "(Explicit)");
 *  else its length, when Tidal's clean and explicit versions differ in length and yours matches one of them. A song
 *  none of these tell about is left alone. */
object VersionScan {
    const val SCAN = "versions"

    /** One song found: [explicitNow] what it is, [how] how that was told, [other] the Tidal track it would become. */
    data class Hit(val song: Track, val explicitNow: Boolean, val how: String, val other: JsonElement) {
        val otherTrack: Track get() = Tidal.trackFromTidal(other)
    }

    val hits = mutableStateListOf<Hit>()
    /** The song being looked at now, and how many were let go for taking too long (see [start]). */
    var checking by mutableStateOf<String?>(null)
        private set
    var skipped by mutableStateOf(0)
        private set
    var checked by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var running by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** The version this scan looked for: true explicit, false clean. */
    var wanted by mutableStateOf(true)
        private set
    /** What happened to each song (by its id): "Downloading…", "Replaced", or what went wrong. */
    val state = mutableStateMapOf<String, String>()
    @Volatile private var stop = false

    fun keyOf(t: Track) = (t.file ?: Music.lib.items[t.id]?.file)?.let { ScanIgnore.fileKey(it) } ?: t.id

    /** A tag's word on it (iTunes' advisory "1"/"2", "explicit", "clean"): true explicit, false clean, else null. */
    fun advisory(v: String?): Boolean? = when (v?.trim()?.lowercase()) {
        "1", "4", "explicit", "true", "yes", "e" -> true
        "2", "clean", "edited", "c" -> false
        else -> null
    }

    private val CLEAN_NAME = Regex("[(\\[]\\s*(clean|edited|clean version|clean edit|censored)\\s*[)\\]]", RegexOption.IGNORE_CASE)
    private val EXPLICIT_NAME = Regex("[(\\[]\\s*(explicit|explicit version|dirty|uncensored)\\s*[)\\]]", RegexOption.IGNORE_CASE)

    // What each song was found to be, kept between scans (by its file): "e" explicit, "c" clean, "u" can't be told;
    // "!" after e or c: Tidal has no other version of it.
    private val cache = HashMap<String, String>()
    private fun loadCache() = synchronized(cache) {
        if (cache.isNotEmpty()) return
        for (l in Prefs.text("versions_cache").split('\n')) { val i = l.lastIndexOf('\t'); if (i > 0) cache[l.substring(0, i)] = l.substring(i + 1) }
    }
    private fun saveCache() = synchronized(cache) { Prefs.setText("versions_cache", cache.entries.joinToString("\n") { "${it.key}\t${it.value}" }) }

    /** Look at every song of yours (once Tidal is connected); found ones come into [hits] as they're found.
     *  fresh: look again at songs already told about in earlier scans. */
    fun start(fresh: Boolean = false) {
        if (running) return
        if (!Music.tidal.connected) { error = "Connect Tidal in Settings first: the versions are looked up on Tidal"; return }
        stop = false; running = true; error = null; hits.clear(); state.clear(); checked = 0; skipped = 0
        listenOn = listen
        val want = Music.dl.explicitWanted()
        wanted = want
        val songs = Music.mySongs().filter { !ScanIgnore.has(SCAN, keyOf(it)) }
        total = songs.size
        Music.bg({
            loadCache()
            if (fresh) synchronized(cache) { cache.clear() }
            var n = 0
            // Each song on a worker of its own, given a time limit: one that hangs (a damaged file the tag reader loops
            // on, a network call that never answers) is let go and marked to be skipped, so the scan always goes on
            // (it got stuck on one before), and Stop works at once.
            val pool = java.util.concurrent.Executors.newCachedThreadPool { r -> Thread(r, "versions").apply { isDaemon = true } }
            for (t in songs) {
                if (stop) break
                checking = "${t.title} — ${t.artist.split(",")[0]}"
                val job = pool.submit<Hit?> { runCatching { check(t, want) }.getOrNull() }
                val limit = System.currentTimeMillis() + (if (listen) 6 * 60_000L else 45_000L)
                var h: Hit? = null
                while (true) {
                    try { h = job.get(500, java.util.concurrent.TimeUnit.MILLISECONDS); break }
                    catch (_: java.util.concurrent.TimeoutException) {
                        if (stop || System.currentTimeMillis() > limit) {
                            job.cancel(true)
                            if (!stop) { synchronized(cache) { cache[keyOf(t)] = "u!" }; skipped++ }
                            break
                        }
                    } catch (_: Exception) { break }
                }
                n++
                val done = n
                Music.scope.launch(Dispatchers.Main) { if (h != null) hits += h; checked = done }
                if (n % 40 == 0) saveCache()
            }
            saveCache()
            pool.shutdownNow()
        }) { _, e ->
            running = false; checking = null
            if (e != null) error = "The scan stopped: ${e.message?.take(120)}"
        }
    }

    fun stopScan() { stop = true }

    /** Listen (the switch beside the scan): songs whose version isn't certain (nothing tells, or only their tags, name
     *  or length) are heard in full beside Tidal's clean and explicit versions, to tell which one they are. Slower. */
    var listen: Boolean
        get() = Prefs.on(LISTEN)
        set(v) { Prefs.set(LISTEN, v); listenOn = v }
    var listenOn by mutableStateOf(false)
        private set
    const val LISTEN = "versions_listen"
    /** What the scan is listening to now (a song's name), or null. */
    var hearing by mutableStateOf<String?>(null)
        private set

    // A song's cache entry: its version ("e", "c", or "u" when nothing tells), then "?" when only its tags, name or
    // length told (not certain: Listen hears it again), then "!" when Tidal hasn't both versions of it.
    private fun check(t: Track, want: Boolean): Hit? {
        val deep = listen
        val key = keyOf(t)
        val was = synchronized(cache) { cache[key] }
        val weak = was != null && (was.startsWith("u") || '?' in was)
        if (was != null && '!' in was) return null
        if (was != null && !(deep && weak) && (was.startsWith("u") || was.startsWith("e") == want)) return null
        var now: Boolean? = null
        var how = ""
        var sure = false
        if (was != null && !(deep && weak)) { now = was.startsWith("e"); how = "found in an earlier scan"; sure = !weak }
        else current(t)?.let { now = it.first; how = it.second; sure = it.third }
        fun note(v: Boolean?, none: Boolean = false) = synchronized(cache) {
            cache[key] = (if (v == null) "u" else if (v) "e" else "c") + (if (v != null && !sure) "?" else "") + (if (none) "!" else "")
        }
        if (now == want && (sure || !deep)) { note(now); return null }
        val vs = Music.tidal.versions(t)
        val ex = Music.tidal.versionOf(t, true, among = vs)
        val cl = Music.tidal.versionOf(t, false, among = vs)
        if (ex == null || cl == null) { note(now, none = true); return null }   // Tidal hasn't both: nothing to change it to
        if (deep && !sure && !stop) {
            hearing = "${t.title} — ${t.artist.split(",")[0]}"
            val heard = try { listenTo(t, ex, cl) } finally { hearing = null }
            if (heard != null) { now = heard; how = "heard: it sounds like Tidal's ${if (heard) "explicit" else "clean"} one"; sure = true }
        }
        if (now == null) { now = byLength(t, vs); how = "by its length" }
        val is_ = now ?: run { note(null); return null }
        if (is_ == want) { note(is_); return null }
        val ours = t.isrc ?: Music.lib.items[t.id]?.isrc
        val other = (if (want) ex else cl).takeIf { ours == null || it["isrc"].str != ours } ?: run { note(is_, none = true); return null }
        note(is_)
        return Hit(t, is_, how, other)
    }

    /** Which version song t is, how that was told, and whether that's certain (Tidal's word or the ISRC), or null when
     *  nothing tells. */
    private fun current(t: Track): Triple<Boolean, String, Boolean>? {
        val e = Music.lib.items[t.id]
        (t.tidal ?: e?.tidal)?.let { id -> Music.tidal.explicitOf(id)?.let { return Triple(it, "Tidal says so", true) } }
        val file = t.file ?: e?.file
        val tags = file?.let { runCatching { FileTags.versionTags(it) }.getOrNull() }
        (t.isrc ?: e?.isrc ?: tags?.first)?.let { isrc -> Music.tidal.explicitByIsrc(isrc)?.let { return Triple(it, "by its ISRC", true) } }
        tags?.second?.let { return Triple(it, "its tags say so", false) }
        val name = "${t.title} ${t.album}"
        if (CLEAN_NAME.containsMatchIn(name)) return Triple(false, "its name says so", false)
        if (EXPLICIT_NAME.containsMatchIn(name)) return Triple(true, "its name says so", false)
        return null
    }

    /** Explicit (true) or clean, by length: Tidal's two versions differ by 2 seconds or more and yours is within 1.5 of one. */
    private fun byLength(t: Track, vs: List<JsonElement>): Boolean? {
        val d = t.duration
        if (d <= 0) return null
        fun off(explicit: Boolean) = vs.filter { it["explicit"].bool == explicit }.minOfOrNull { abs((it["duration"].dbl ?: 0.0) - d) }
        val de = off(true) ?: return null
        val dc = off(false) ?: return null
        if (abs(de - dc) < 2 || minOf(de, dc) > 1.5) return null
        return de < dc
    }

    /** Listen: song t heard in full, and Tidal's explicit (ex) and clean (cl) versions too (streamed at low quality,
     *  which is plenty to tell them apart); true if it sounds like the explicit one, false the clean one, null if it
     *  can't be told (the two sound the same, or one couldn't be heard). */
    private fun listenTo(t: Track, ex: JsonElement, cl: JsonElement): Boolean? {
        val mine = t.file ?: Music.lib.items[t.id]?.file ?: return null
        val y = runCatching { AudioTools.wholePrint(mine) { stop } }.getOrNull() ?: return null
        val e = tidalPrint(ex) ?: return null
        val c = tidalPrint(cl) ?: return null
        return Prints.which(y, e, c)
    }

    private fun tidalPrint(c: JsonElement): IntArray? {
        val st = runCatching { Music.tidal.stream(c, lossy = true, quality = "LOW") }.getOrNull() ?: return null
        if (st.isDash) return null
        val f = java.io.File(Music.ctx.cacheDir, "version-${c["id"].long}.m4a")
        return try {
            HTTP.newCall(okhttp3.Request.Builder().url(st.uri).build()).execute().use { r ->
                if (!r.isSuccessful) return null
                f.outputStream().use { o -> r.body!!.byteStream().copyTo(o, 1 shl 16) }
            }
            runCatching { AudioTools.wholePrint(f.path) { stop } }.getOrNull()
        } catch (_: Exception) { null } finally { f.delete() }
    }

    /** Telling two recordings apart by their fingerprints (Chromaprint's words, one every ~0.124 s). */
    object Prints {
        private fun d(a: Int, b: Int) = Integer.bitCount(a xor b)

        /** How far b runs behind a (in words), up to ±15 seconds: where they match best. */
        fun offset(a: IntArray, b: IntArray, max: Int = 120): Int {
            var best = 0; var bestD = Double.MAX_VALUE
            for (off in -max..max) {
                var sum = 0L; var n = 0
                var i = maxOf(0, -off); val to = minOf(a.size, b.size - off)
                while (i < to) { sum += d(a[i], b[i + off]); n++; i += 2 }
                if (n >= 100 && sum.toDouble() / n < bestD) { bestD = sum.toDouble() / n; best = off }
            }
            return best
        }

        /** y heard beside the explicit (e) and clean (c) recordings: true if it's the explicit one, false the clean one,
         *  null if it can't be told. Only the moments where the two differ (the words changed) are looked at: there,
         *  whichever y matches more closely is the one it is. */
        fun which(y: IntArray, e: IntArray, c: IntArray): Boolean? {
            if (y.size < 200 || e.size < 200 || c.size < 200) return null
            val oe = offset(y, e); val oc = offset(y, c)
            val pairs = ArrayList<Int>()
            for (i in y.indices) { val je = i + oe; val jc = i + oc; if (je in e.indices && jc in c.indices) pairs += i }
            if (pairs.size < 200) return null
            // How far apart the two usually are (the same music, encoded twice): the changed moments stand out above it
            val base = pairs.map { d(e[it + oe], c[it + oc]) }.sorted()[pairs.size / 2]
            var n = 0; var de = 0L; var dc = 0L
            for (i in pairs) {
                if (d(e[i + oe], c[i + oc]) < base + 7) continue
                n++; de += d(y[i], e[i + oe]); dc += d(y[i], c[i + oc])
            }
            if (n < 4) return null   // they sound the same: nothing to tell by
            val me = de.toDouble() / n; val mc = dc.toDouble() / n
            if (abs(me - mc) < 2.0) return null
            return me < mc
        }
    }

    // ---- playing one to check ----
    /** What's playing to check (a song's id, or its id + "|new" for the version it would become), or null. */
    var previewing by mutableStateOf<String?>(null)
        private set
    private var pe: Engine? = null

    /** Play hit's song (mine) or the version it would become, in a player of its own; the music pauses. */
    fun preview(h: Hit, mine: Boolean) {
        val key = h.song.id + if (mine) "" else "|new"
        if (previewing == key) { stopPreview(); return }
        stopPreview()
        if (Music.player?.isPlaying == true) Music.togglePause()
        previewing = key
        Music.bg({
            if (mine) h.song.file ?: Music.lib.items[h.song.id]?.file ?: error("its file isn't here")
            else Music.tidal.stream(h.other, lossy = true, quality = "HIGH").uri
        }) { uri, e ->
            if (previewing != key) return@bg
            if (uri == null) { previewing = null; Music.toast("Can't play it: ${e?.message?.take(80)}"); return@bg }
            val x = runCatching { Music.player?.tail(uri, 0) }.getOrNull()
            if (x == null) { previewing = null; Music.toast("Can't play it here"); return@bg }
            x.volume = 1f; x.playWhenReady = true; x.play()
            pe = x
        }
    }

    /** Jump the one playing on (or back, less than 0) by ms. */
    fun skip(ms: Long) { pe?.let { it.seekTo((it.positionMs + ms).coerceAtLeast(0)) } }

    fun stopPreview() {
        pe?.let { runCatching { it.release() } }
        pe = null; previewing = null
    }

    // ---- replacing ----
    /** (step one) The wanted version downloaded (into Downloads, as every download); then done(the new song), or the
     *  trouble in [state]. The old one is removed after ([replaced]). */
    fun download(h: Hit, done: (Track) -> Unit) {
        val id = h.song.id
        if (state[id] == "Downloading…") return
        if (Music.current()?.id == id) { state[id] = "It's the song playing now: skip it first"; return }
        if (previewing?.startsWith(id) == true) stopPreview()
        state[id] = "Downloading…"
        val newT = h.otherTrack
        Music.bg({ Music.dl.downloadTidal(newT, h.other) }) { _, e ->
            if (e != null) { state[id] = "Couldn't download it: ${e.message?.take(100)}"; return@bg }
            done(newT)
        }
    }

    /** (step two) The old song gone (its file deleted, or the user said no: kept): the new one takes its place in the
     *  queue, playlists and favorites. */
    fun replaced(h: Hit, newT: Track, oldGone: Boolean) {
        val id = h.song.id
        if (oldGone) {
            Music.versionReplaced(h.song, newT)
            synchronized(cache) { cache.remove(keyOf(h.song)) }
            state[id] = "Replaced: the ${if (wanted) "explicit" else "clean"} version is in your music"
        } else state[id] = "The ${if (wanted) "explicit" else "clean"} version is downloaded; your old file was kept"
    }
}
