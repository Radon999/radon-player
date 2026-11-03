package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Sync with phone/PC (Settings › Play on): over the phone remote's pairing, the two apps swap their data and merge
 *  it, like a backup and a restore both ways in one tap. The phone sends its side to the PC (POST /sync); the PC merges
 *  it with its own (SyncMerge: newest change wins, nothing only one side has is lost, what's deleted on one side goes),
 *  puts the result in place and sends it back; the phone puts it in place too. What goes across is [SyncRules]'s. Each
 *  device keeps a copy of its data from just before ("Undo last sync"). Tapped on the PC, it asks the phone, which
 *  checks every so often while the app runs. */
object Sync {
    const val SONGS = "sync_song_settings"   // Prefs: the songs' own volumes and Custom EQs go across too (on both devices)
    private const val LAST = "sync_last"       // when this device last synced (seconds)
    private const val LAST_WITH = "sync_with"  // with which device
    private const val SUMMARY = "sync_summary" // what it did, in words
    private const val FRESH = "sync_fresh"     // the last sync was undone here: the next one is as a first one
    private const val HEAD = "sync.json"       // the zip's first entry: who sent it, how
    private const val WAIT = 5 * 60L           // seconds the PC's Sync now waits for the phone
    private const val MAX_ENTRY = 64L shl 20   // bytes one file in a sync may be
    private const val DIARY = "listen-log.jsonl"   // the listening diary (ListenLog): each device's stretches added to the other's
    private const val DOWNLOADS = "downloads.json" // the songs downloaded on this device: the other downloads the ones it hasn't
    private const val TAGS = "tag-changes.json"  // tag changes made on this device since the other took them (TagSync)
    private val KNOWN = SyncRules.FILES + SyncRules.SONG_FILES + listOf(SyncRules.STATE, SyncRules.SWITCHES, SyncRules.MOODS, DIARY, DOWNLOADS, TAGS, TagMatch.FILE)

    var busy by mutableStateOf<String?>(null)   // "Syncing with …" while it runs
        private set
    var lastAt by mutableStateOf(0L)
        private set
    var lastWith by mutableStateOf("")
        private set
    var lastText by mutableStateOf("")
        private set
    var undoAt by mutableStateOf(0L)            // when the copy "Undo last sync" puts back was made (0: none)
        private set
    var wantedAt by mutableStateOf(0L)          // the PC: when Sync now asked the phone (0: not waiting)
        private set

    private val running = AtomicBoolean(false)
    private var ctx: Ctx? = null
    private val dir: File get() = (ctx ?: Music.ctx).filesDir
    private val undoFile get() = File(dir, "sync-undo.zip")

    fun load(c: Ctx) {
        if (ctx != null) return
        ctx = c
        lastAt = Prefs.text(LAST).toLongOrNull() ?: 0L
        lastWith = Prefs.text(LAST_WITH)
        lastText = Prefs.text(SUMMARY)
        undoAt = if (undoFile.isFile) undoFile.lastModified() / 1000 else 0L
    }

    val songsOn get() = Prefs.on(SONGS)
    fun setSongsOn(on: Boolean) = Prefs.set(SONGS, on)

    private fun secs() = System.currentTimeMillis() / 1000

    /** "just now", "5 min ago", "3 h ago", "2 days ago", else the date. */
    fun ago(at: Long): String {
        val d = secs() - at
        return when {
            d < 60 -> "just now"
            d < 3600 -> "${d / 60} min ago"
            d < 86400 -> "${d / 3600} h ago"
            d < 7 * 86400 -> (d / 86400).let { if (it == 1L) "yesterday" else "$it days ago" }
            else -> java.time.Instant.ofEpochSecond(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        }
    }

    // ---- the phone: Sync now ----

    /** (the phone) Sync with the paired PC. fromPc: the PC's Sync now asked, so its changes win a tie. */
    fun now(fromPc: Boolean = false, auto: Boolean = false) {
        if (!Remote.paired) { if (!auto) Music.toast("Pair with the PC first (Settings › Play on)"); return }
        if (!running.compareAndSet(false, true)) return
        val with = Remote.name.ifEmpty { "the PC" }
        busy = "Syncing with $with…"
        val songs = songsOn
        val st = stateJson(); val sw = switchesJson()   // the screen's state, read on its thread
        val host = Remote.host; val headers = Remote.headers()
        Music.bg({
            val mine = files(songs).apply { put(SyncRules.STATE, st.toString()); put(SyncRules.SWITCHES, sw.toString()) }
            val moods = runCatching { Moods.backup() }.getOrNull()
            val diary = runCatching { ListenLog.syncText() }.getOrDefault("")
            val (_, theirs) = post(host, headers, head(if (fromPc) "pc" else "phone", songs, auto),
                mine + listOfNotNull(moods?.let { SyncRules.MOODS to it }) + (DIARY to diary) + (DOWNLOADS to DownloadSync.mine()) +
                    (TAGS to TagSync.outgoing()) + (TagMatch.FILE to runCatching { TagMatch.outgoing(null) }.getOrDefault("")))
            // The PC's result, on top of what was sent: anything changed here meanwhile (a play counted) stays too.
            val sent = parse(mine)
            val here = parse(files(songs)) + mapOf(SyncRules.STATE to st, SyncRules.SWITCHES to sw)
            val result = SyncMerge.mergeAll(sent, here, parse(theirs), preferLocal = false)
            saveUndo(mine, with)
            theirs[DIARY]?.let { runCatching { ListenLog.mergeIn(it) } }   // the PC's stretches (each one once)
            val tagged = (theirs[TAGS]?.let { runCatching { TagSync.take(it) }.getOrDefault(0) } ?: 0) +   // the PC's tag changes
                runCatching { TagMatch.take(theirs[TagMatch.FILE]) }.getOrDefault(0)   // Tags & covers match: the PC's, when it's the main one
            val sum = SyncMerge.summary(result, here)
            Done(result.mapValues { it.value.toString() }, result.filter { (n, v) -> here[n] != v }.keys,
                sum.text() + (if (tagged > 0) " · tags changed on $tagged song${if (tagged != 1) "s" else ""}" else ""),
                theirs[SyncRules.MOODS], sum.changed == 0 && tagged == 0, theirs[DOWNLOADS], tagged)
        }) { d, err ->
            running.set(false); busy = null
            if (d == null) { if (!auto) Music.toast(failed(err)); return@bg }
            runCatching { putInPlace(d.files, d.changed) }.onFailure { Crashes.caught(it, "Sync"); Music.toast("Synced, but couldn't put everything in place: ${it.message?.take(80)}") }
            d.moods?.let { runCatching { Moods.restoreLater(it, fromSync = true); Moods.auto() } }
            val got = d.downloads?.let { runCatching { DownloadSync.take(it, with) }.getOrDefault(0) } ?: 0
            if (d.tagged > 0) Music.rescan()   // your songs read again with their new tags
            TagMatch.refreshSoon()
            finished(d.summary, with, quiet = auto && d.nothing && got == 0)
        }
    }

    /** (the phone) Synced automatically each time it meets the PC: when it's paired, and whenever the PC can be
     *  reached again after it couldn't (the app opened, back on the same Wi-Fi, the PC woken). */
    private var met = false
    private fun connected() {
        // Sync only when you ask for it (Sync now, here or on the PC): meeting the PC no longer syncs by itself
        met = true
    }

    private class Done(val files: Map<String, String>, val changed: Set<String>, val summary: String, val moods: String?, val nothing: Boolean,
                       val downloads: String?, val tagged: Int = 0)

    private val client by lazy { HTTP.newBuilder().readTimeout(3, TimeUnit.MINUTES).writeTimeout(3, TimeUnit.MINUTES).build() }

    /** (blocking) This side to the PC, streamed from a file; its answer read as it comes. */
    private fun post(host: String, headers: Map<String, String>, head: JsonObject, files: Map<String, String>): Pair<JsonObject, Map<String, String>> {
        val tmp = File((ctx ?: Music.ctx).cacheDir, "sync-out.zip")
        FileOutputStream(tmp).use { writeZip(it, head, files) }
        try {
            val url = "http://$host/sync"
            val req = Request.Builder().url(url).header("User-Agent", APP_UA).apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(tmp.asRequestBody("application/zip".toMediaType())).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw HttpError(r.code, r.body?.string() ?: "", url)
                return readZip(r.body!!.byteStream())
            }
        } finally { tmp.delete() }
    }

    private fun failed(err: Throwable?): String = when {
        // A PC app from before sync doesn't know /sync (it takes the zip for a command, and says it isn't JSON)
        err is HttpError && (err.code == 404 || (err.code == 400 && "JSON" in err.body)) -> "Couldn't sync: update the PC app first (☰ → Update there)"
        err is HttpError && err.code == 401 -> "Couldn't sync: the PC forgot this phone. Pair again (Play on)"
        err is HttpError -> "Couldn't sync: " + (runCatching { JSON.parseToJsonElement(err.body)["error"].str }.getOrNull() ?: "HTTP ${err.code}")
        else -> "Couldn't sync: the PC didn't answer. Is Radon Player open on it, on the same network?"
    }

    private var nextCheck = 0L

    /** (the phone) Just paired with a PC: sync straight away. */
    fun paired() { met = false; nextCheck = 0L }
    /** Something to send soon (a playlist made synced): on the phone, a sync in a few seconds. */
    fun soon() = Unit   // (a sync only ever starts from Sync now)

    /** (the phone, every few seconds) Whether the PC's Sync now is waiting for this phone: then sync. A quick question
     *  every 20 seconds, every 30 seconds while the PC can't be reached; meeting it again syncs ([connected]). */
    fun tick() {
        if (!Remote.paired || running.get()) return
        val t = System.currentTimeMillis()
        if (t < nextCheck) return
        nextCheck = t + 20_000
        val host = Remote.host; val h = Remote.headers()
        Music.bg({ httpJson("http://$host/sync/wanted", h) }) { s, err ->
            if (err != null) { met = false; nextCheck = System.currentTimeMillis() + 30_000; return@bg }
            if (s["wanted"].bool == true) now(fromPc = true) else connected()
            met = true
        }
    }

    // ---- the PC ----

    /** (the PC) Sync now: the phone can't be called, so it's asked to sync next time it checks (within 20 seconds
     *  while the app is open on it). */
    fun ask() { wantedAt = secs() }
    fun cancelAsk() { wantedAt = 0L }
    val waiting get() = wantedAt > 0 && secs() - wantedAt < WAIT

    /** For GET /sync/wanted (the server's thread). */
    fun wanted(): Boolean {
        if (wantedAt > 0 && !waiting) Music.scope.launch { wantedAt = 0L }   // asked too long ago
        return waiting
    }

    private fun <T> onMain(run: () -> T): T = runBlocking { withContext(Dispatchers.Main) { run() } }

    /** (the PC, blocking, on the server's thread) A phone's sync: its side read, merged with this PC's, the result put
     *  in place here; back, what writes the result for the phone. peer: the phone's pairing token (each phone has its
     *  own last sync). */
    fun serve(peer: String, peerName: String, ins: InputStream): (OutputStream) -> Unit {
        if (!running.compareAndSet(false, true)) throw IllegalStateException("the PC is already syncing")
        try {
            onMain { busy = "Syncing with $peerName…"; wantedAt = 0L }
            val (asked, theirs) = readZip(ins)
            if (asked["app"].str != "radon-player") throw IllegalArgumentException("that isn't a Radon Player sync")
            val songs = asked["songs"].bool == true && songsOn
            val (st, sw) = onMain { stateJson() to switchesJson() }
            val mine = files(songs).apply { put(SyncRules.STATE, st.toString()); put(SyncRules.SWITCHES, sw.toString()) }
            val myMoods = runCatching { Moods.backup() }.getOrNull()
            theirs[DIARY]?.let { runCatching { ListenLog.mergeIn(it) } }   // the phone's stretches (each one once)
            val tagged = (theirs[TAGS]?.let { runCatching { TagSync.take(it) }.getOrDefault(0) } ?: 0) +   // the phone's tag changes
                runCatching { TagMatch.take(theirs[TagMatch.FILE]) }.getOrDefault(0)   // Tags & covers match: the phone's, when it's the main one
            val local = parse(mine)
            val remote = parse(theirs).filterKeys { songs || it !in SyncRules.SONG_FILES }
            val baseFile = File(dir, "sync-base-${sha1(peer).take(12)}.zip")
            // (fresh: the phone undid a sync, so this isn't a time to take what it lacks for deleted)
            val base = if (baseFile.isFile && asked["fresh"].bool != true) runCatching { parse(FileInputStream(baseFile).use { readZip(it) }.second) }.getOrNull() else null
            val merged = SyncMerge.mergeAll(base, local, remote, preferLocal = asked["prefer"].str == "pc")
            val sum = SyncMerge.summary(merged, local); val summary = sum.text()
            val out = merged.mapValues { it.value.toString() }
            saveUndo(mine, peerName)
            onMain {
                runCatching { putInPlace(out, merged.filter { (n, v) -> local[n] != v }.keys) }.onFailure { Crashes.caught(it, "Sync") }
                theirs[SyncRules.MOODS]?.let { runCatching { Moods.restoreLater(it, fromSync = true); Moods.auto() } }
                theirs[DOWNLOADS]?.let { runCatching { DownloadSync.take(it, peerName) } }
                if (tagged > 0) Music.rescan()
            }
            val back = head("pc", songs)
            writeZipFile(baseFile, back, out)   // what these two agreed on: next time, what's gone from one side is deleted
            onMain { finished(summary + (if (tagged > 0) " · tags changed on $tagged song${if (tagged != 1) "s" else ""}" else ""), peerName,
                quiet = asked["auto"].bool == true && sum.changed == 0 && tagged == 0) }
            val reply = out + listOfNotNull(myMoods?.let { SyncRules.MOODS to it }) + (DIARY to runCatching { ListenLog.syncText() }.getOrDefault("")) +
                (DOWNLOADS to DownloadSync.mine()) + (TAGS to TagSync.outgoing()) +
                (TagMatch.FILE to runCatching { TagMatch.outgoing(theirs[TagMatch.FILE]) }.getOrDefault(""))
            TagMatch.refreshSoon()
            return { os -> writeZip(os, back, reply) }
        } finally {
            running.set(false)
            runCatching { onMain { busy = null } }
        }
    }

    private fun sha1(s: String) = java.security.MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ---- undo ----

    /** Put back this device's data from just before the last sync. The next sync starts afresh (as a first one, so
     *  what was undone here isn't taken for deleted, on either device). */
    fun undo() {
        if (running.get() || !undoFile.isFile) return
        Music.bg({ FileInputStream(undoFile).use { readZip(it) }.second }) { files, err ->
            if (files == null) { Music.toast("Couldn't undo: ${err?.message?.take(80)}"); return@bg }
            runCatching { putInPlace(files, files.keys - SyncRules.MOODS) }.onFailure {
                Crashes.caught(it, "Undo sync"); Music.toast("Couldn't undo everything: ${it.message?.take(80)}"); return@bg
            }
            runCatching { Moods.undoSync() }   // the moods as they were too
            undoFile.delete(); undoAt = 0L
            Prefs.set(FRESH, true)
            dir.listFiles { f -> f.name.startsWith("sync-base-") }?.forEach { it.delete() }
            Music.toast("Put back as it was before the last sync")
        }
    }

    private fun saveUndo(mine: Map<String, String>, with: String) =
        writeZipFile(undoFile, buildJsonObject { put("app", "radon-player"); put("undo", true); put("with", with); put("made", secs()) }, mine)

    private fun finished(text: String, with: String, quiet: Boolean = false) {
        val t = secs()
        lastAt = t; lastWith = with; lastText = text; undoAt = t
        Prefs.set(FRESH, false)
        Prefs.setText(LAST, "$t"); Prefs.setText(LAST_WITH, with); Prefs.setText(SUMMARY, text)
        if (!quiet) Music.toast(text)
    }

    // ---- this device's side ----

    private fun head(prefer: String, songs: Boolean, auto: Boolean = false) = buildJsonObject {
        put("app", "radon-player"); put("v", 1); put("platform", platformName); put("device", deviceName); put("auto", auto)
        put("prefer", prefer); put("songs", songs); put("made", secs()); put("fresh", Prefs.on(FRESH))
    }

    /** (blocking) The data files that sync, as they are now. */
    private fun files(songs: Boolean): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        for (n in SyncRules.FILES + (if (songs) SyncRules.SONG_FILES else emptyList()))
            out[n] = readText(File(dir, n))?.takeIf { it.isNotBlank() } ?: SyncRules.empty(n)
        // Only synced playlists (⇄) go across, and ones just turned off (once more, so the other keeps its copy)
        out["playlists.json"]?.let { t -> out["playlists.json"] = SyncPlaylists.shared(t) }
        return out
    }

    private fun parse(files: Map<String, String>): Map<String, JsonElement> =
        files.filterKeys { it != SyncRules.MOODS && it != DIARY && it != DOWNLOADS && it != TAGS && it != TagMatch.FILE }.mapNotNull { (n, t) -> runCatching { n to JSON.parseToJsonElement(t) }.getOrNull() }.toMap()

    /** (the screen's thread) The settings of state.json that sync, from the app as it is now. The EQ playing now is
     *  kept in its output's place, so each output's EQ is whole. */
    private fun stateJson(): JsonObject {
        val m = Music
        val e = m.eq
        val outs = if (e.output.isNotEmpty()) e.outputs + (e.output to e.toOutput(e.outputs[e.output]?.label ?: e.output)) else e.outputs
        fun strs(x: Map<String, String>) = JsonObject(x.mapValues { JsonPrimitive(it.value) })
        fun list(x: Collection<String>) = JsonArray(x.map { JsonPrimitive(it) })
        return SyncRules.state(buildJsonObject {
            put("eq", JSON.encodeToJsonElement(EqSettings.serializer(), e.copy(outputs = outs)))
            put("pins", JSON.encodeToJsonElement(ListSerializer(Pin.serializer()), m.pins.toList()))
            put("tab_sorts", strs(m.tabSorts.toMap())); put("tab_picks", strs(m.tabPicks.toMap()))
            put("artist_picks", strs(m.artistPicks)); put("album_picks", strs(m.albumPicks)); put("song_picks", strs(m.songPicks))
            put("similar_kept", list(m.similarKept)); put("singles_kept", list(m.singlesKept)); put("not_dupes", list(m.notDupes))
            put("lyrics_mode", m.lyricsMode); put("music_sort", m.musicSort); put("playlist_sort", m.playlistSort)
            put("skip_order", m.skipOrder); put("skip_period", m.skipPeriod); put("top_mine_only", m.topMineOnly)
            put("top_period", m.topPeriod); put("top_kind", m.topKind); put("collage_size", m.collageSize)
            put("look", m.look); put("np_fallback", m.npFallback); put("vis_preset", m.visPreset); put("wrapped_style", m.wrappedStyle)
            put("autoplay", m.autoplay); put("multi_queue", m.multiQueue); put("lyrics_to_files", m.lyricsToFiles)
            put("dupe_action", m.dupeAction); put("dj_new", m.djNew)
        })
    }

    private fun switchesJson(): JsonObject = SyncRules.prefs(JSON.parseToJsonElement(BackupMerge.switchesToSave(Prefs.all())) as JsonObject)

    // ---- putting a side in place, live ----

    /** (the screen's thread) files in place: each store's file written and read again, then the settings. changed: the
     *  ones that differ from what's here. A store is held while its file is swapped, so it can't save over it. */
    private fun putInPlace(files: Map<String, String>, changed: Set<String>) {
        val m = Music
        fun f(n: String) = File(dir, n)
        fun swap(n: String, old: Any, make: () -> Unit) {
            val text = files[n]?.takeIf { n in changed } ?: return
            synchronized(old) { writeAtomic(f(n), text); make() }
        }
        // Playlists: the synced ones as the sync left them, this device's own (not synced) as they are
        files["playlists.json"]?.let { got -> synchronized(m.lists) {
            val now = readText(f("playlists.json")) ?: "[]"
            val next = SyncPlaylists.putBack(now, got)
            if (next != null) { writeAtomic(f("playlists.json"), next); m.lists = Playlists(m.ctx) }
        } }
        swap("playlist-folders.json", m.playlistFolders) { m.playlistFolders = PlaylistFolders(f("playlist-folders.json")) }
        swap("favorites.json", m.favorites) { m.favorites = Favorites(f("favorites.json")) }
        swap("plays.json", m.plays) { m.plays = Plays(m.ctx) }
        swap("skips.json", m.songStats) { m.songStats = SongStats(f("skips.json")) }
        swap("song-speeds.json", m.speeds) { m.speeds = SongSpeeds(f("song-speeds.json")) }
        val vols = listOf("song-volume.json", "song-volume-outputs.json").filter { it in changed && it in files }
        if (vols.isNotEmpty()) synchronized(m.gains) {
            for (n in vols) writeAtomic(f(n), files.getValue(n))
            m.gains = SongGains(f("song-volume.json"), f("song-volume-outputs.json"))
        }
        swap("song-eq.json", m.songEqs) { m.songEqs = SongEqs(f("song-eq.json")) }
        if (SyncRules.SWITCHES in changed) files[SyncRules.SWITCHES]?.let { applySwitches(it) }
        if (SyncRules.STATE in changed) files[SyncRules.STATE]?.let { applyState(JSON.parseToJsonElement(it) as JsonObject) }
        m.refreshSongVolume()   // the playing song's own volume, as it is now
        m.setEq { it }                         // and its own EQ
        m.musicVersion++
        Stats.refresh(force = true)
        // Playlists and favorites with the other device's song files: this one's copies of them once it reads its folders.
        if (changed.isNotEmpty()) Prefs.set("adopt_files", true)
    }

    private fun applySwitches(text: String) {
        val now = Prefs.all()
        for ((k, v) in BackupMerge.switchesToRestore(text, crossDevice = true)) {
            if (!SyncRules.prefSyncs(k) || now[k] == v) continue
            // Your own looks, layouts and visualizers: the other device's added to yours, not swapped for them
            val made = if (v is String) org.omarchy.music.ui.MadeSync.incoming(k, now[k] as? String ?: "", v) else null
            if (made != null) { Prefs.setText(k, made); continue }
            when (v) {
                is Boolean -> Music.setOpt(k, v)   // with what it takes to take effect (crossfeed…)
                is Float -> Prefs.setNum(k, v)
                is String -> Prefs.setText(k, v)
            }
        }
        Music.applySound()
    }

    private fun applyState(st: JsonObject) {
        val m = Music
        fun s(k: String) = st[k]?.str
        fun b(k: String) = st[k]?.bool
        fun strs(k: String) = (st[k] as? JsonObject)?.mapValues { it.value.str ?: "" }
        fun list(k: String) = (st[k] as? JsonArray)?.mapNotNull { it.str }
        (st["eq"] as? JsonObject)?.let { q -> runCatching { JSON.decodeFromJsonElement(EqSettings.serializer(), q) }.getOrNull() }
            ?.let { got -> m.setEq { e -> withEq(e, got) } }
        (st["pins"] as? JsonArray)?.let { a -> runCatching { JSON.decodeFromJsonElement(ListSerializer(Pin.serializer()), a) }.getOrNull() }
            ?.takeIf { it != m.pins.toList() }?.let { m.pins.clear(); m.pins.addAll(it) }
        strs("tab_sorts")?.let { m.tabSorts.clear(); m.tabSorts.putAll(it) }
        strs("tab_picks")?.let { m.tabPicks.clear(); m.tabPicks.putAll(it) }
        strs("artist_picks")?.let { m.artistPicks = it }
        strs("album_picks")?.let { m.albumPicks = it }
        strs("song_picks")?.let { m.songPicks = it }
        list("similar_kept")?.let { m.similarKept = it }
        list("singles_kept")?.let { m.singlesKept = it.toSet() }
        list("not_dupes")?.let { m.notDupes = it.toSet() }
        s("lyrics_mode")?.let { m.lyricsMode = it }
        s("music_sort")?.let { m.musicSort = it }
        s("playlist_sort")?.let { m.playlistSort = it }
        s("skip_order")?.let { m.skipOrder = it }
        s("skip_period")?.let { m.skipPeriod = it }
        b("top_mine_only")?.let { m.topMineOnly = it }
        s("top_period")?.let { m.topPeriod = it }
        s("top_kind")?.let { m.topKind = it }
        st["collage_size"]?.long?.toInt()?.let { m.collageSize = it }
        s("vis_preset")?.let { m.visPreset = it }
        s("wrapped_style")?.let { m.wrappedStyle = it }
        b("autoplay")?.let { m.autoplay = it }
        b("multi_queue")?.let { m.multiQueue = it }
        b("lyrics_to_files")?.let { m.lyricsToFiles = it }
        s("dupe_action")?.let { m.dupeAction = it }
        b("dj_new")?.takeIf { it != m.djNew }?.let { m.djNew = it; Dj.withNew = it }
        s("np_fallback")?.takeIf { it != m.npFallback }?.let { m.chooseNpFallback(it) }
        s("look")?.takeIf { it != m.look && org.omarchy.music.ui.Looks.known(it) }?.let { m.chooseLook(it) }
        m.saveState()
    }

    /** This device's equalizer with the synced parts in: Sound presets, corrections, the portable outputs' EQs (and
     *  their corrections). Its own outputs stay; if the one playing now is a synced one that changed, it's heard. */
    private fun withEq(e: EqSettings, got: EqSettings): EqSettings {
        val outs = e.outputs.filterKeys { !SyncRules.portableOutput(it) } + got.outputs.filterKeys { SyncRules.portableOutput(it) }
        val devs = e.devices.filterKeys { !SyncRules.portableOutput(it) } + got.devices.filterKeys { SyncRules.portableOutput(it) }
        val n = e.copy(myPresets = got.myPresets, profiles = got.profiles, devices = devs, outputs = outs, perOutput = got.perOutput)
        val cur = e.output
        val theirs = outs[cur]
        return if (cur.isNotEmpty() && SyncRules.portableOutput(cur) && theirs != null && theirs != e.toOutput(theirs.label)) n.withOutput(cur, theirs) else n
    }

    // ---- zips ----

    private fun writeZip(os: OutputStream, head: JsonObject, files: Map<String, String>) {
        val z = ZipOutputStream(os)
        z.putNextEntry(ZipEntry(HEAD)); z.write(head.toString().toByteArray()); z.closeEntry()
        for ((n, t) in files) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
        z.finish()
        z.flush()
    }

    private fun writeZipFile(f: File, head: JsonObject, files: Map<String, String>) {
        val tmp = File(f.path + ".tmp")
        FileOutputStream(tmp).use { writeZip(it, head, files) }
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw IOException("couldn't write ${f.name}") }
    }

    /** A sync's zip: its head, and the files it may have (any other is left out), each no bigger than MAX_ENTRY. */
    private fun readZip(ins: InputStream): Pair<JsonObject, Map<String, String>> {
        var head: JsonObject? = null
        val files = LinkedHashMap<String, String>()
        val z = ZipInputStream(ins)
        while (true) {
            val e = z.nextEntry ?: break
            if (e.name != HEAD && e.name !in KNOWN) continue
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var n = 0L
            while (true) {
                val r = z.read(buf)
                if (r < 0) break
                n += r
                if (n > MAX_ENTRY) throw IOException("${e.name} is too big to sync")
                out.write(buf, 0, r)
            }
            val text = out.toString("UTF-8")
            if (e.name == HEAD) head = JSON.parseToJsonElement(text) as? JsonObject else files[e.name] = text
        }
        return (head ?: throw IllegalArgumentException("that isn't a Radon Player sync")) to files
    }
}
