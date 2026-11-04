package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Tags & covers match (Settings › Sync): your songs that are on both devices end up with the same tags and the same
 *  album art. You pick the main device (the PC or the phone): at each sync it sends what every song of its says (title,
 *  artist, album, album artist, genre, year, track) and its cover, and the other device fills in what its copy of the
 *  song is missing: a tag that's filled in, and a cover it has, are never changed by it. The main device always sends, the other always takes; tag edits made on either still go both ways (TagSync).
 *
 *  A song is found on the other device by its ISRC, else its title and artist, else its file's name, as long as the
 *  lengths agree. On the phone only the songs the app downloaded can be changed (Android lets an app write only its own
 *  files); on the PC, any song of yours. What each file says is read in the background and kept, so a sync stays quick. */
object TagMatch {
    const val ON = "tag_match_on"
    const val MAIN = "tag_match_main"   // "pc" or "phone"
    const val FILE = "tag-match.json"   // the name of what goes across in a sync

    private val FIELDS = Tags.FIELDS.map { it.first }
    private val lock = Any()
    private val dir get() = Music.ctx.filesDir
    private val cacheFile get() = File(dir, "tag-match-cache.json")
    private val peerFile get() = File(dir, "tag-match-peer.json")
    /** How much album art goes across in one sync, at most (the rest next time). */
    private const val ART_BUDGET = 24_000_000

    val on: Boolean get() = Prefs.on(ON)
    val main: String get() = Prefs.text(MAIN, "pc")
    val here: String get() = if (platformName == "android") "phone" else "pc"
    val isMain: Boolean get() = main == here

    /** What a file says, as last read: its tags, its cover's fingerprint ("" for none) and when it was read. */
    @Serializable
    data class Read(val stamp: Long = 0, val tags: Map<String, String> = emptyMap(), val art: String = "")

    /** A song as the main device sends it. */
    @Serializable
    data class Entry(val isrc: String = "", val key: String = "", val name: String = "", val secs: Int = 0,
                     val tags: Map<String, String> = emptyMap(), val art: String = "")

    /** What goes across: this device's songs (only when it's the main one), the covers the other lacks, and the
     *  fingerprints of this device's covers (so the main device knows which to send). */
    @Serializable
    data class Sent(val main: String = "", val entries: List<Entry> = emptyList(), val art: Map<String, String> = emptyMap(),
                    val have: Map<String, String> = emptyMap())

    private val readSer = MapSerializer(String.serializer(), Read.serializer())
    private var cache: HashMap<String, Read>? = null
    @Volatile private var reading = false

    private fun cache(): HashMap<String, Read> = synchronized(lock) {
        cache ?: (readText(cacheFile)?.let { runCatching { HashMap(JSON.decodeFromString(readSer, it)) }.getOrNull() } ?: HashMap())
            .also { cache = it }
    }

    private fun keepCache() = synchronized(lock) { cache?.let { writeAtomic(cacheFile, JSON.encodeToString(readSer, it)) } }

    /** A file the app has just changed (its tags or cover): read again next time. */
    fun touched(uri: String) { synchronized(lock) { cache?.remove(uri) } }

    /** When a file last changed, for the ones that have a path; content:// files (the phone's downloads) are only
     *  changed by the app, which says so ([touched]). */
    private fun stampOf(uri: String): Long =
        if (!uri.startsWith("content:")) File(uri).let { it.lastModified() * 31 + it.length() } else 1L

    private fun hash(b: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(b).take(10).joinToString("") { "%02x".format(it) }

    /** Your songs with files here: the song and its file. */
    private fun songs(): List<Pair<Track, String>> = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t ->
        val f = Music.lib.items[t.id]?.file ?: t.file?.takeIf { t.isFile } ?: return@mapNotNull null
        if (SyncIgnore.ignored(f)) return@mapNotNull null   // a folder sync leaves alone (unreleased music…)
        t to f
    }.distinctBy { it.second }

    private fun readFile(uri: String): Read? = runCatching {
        val tags = Tags.read(Music.ctx, listOf(uri)).mapValues { it.value.orEmpty() }.filterKeys { it in FIELDS }
        val art = runCatching { FileTags.cover(Music.ctx, uri) }.getOrNull()?.let { hash(it) } ?: ""
        Read(stampOf(uri), tags, art)
    }.getOrNull()

    /** (blocking) Read the files that are new or changed since; quick when nothing is. */
    fun refresh() {
        if (!on || reading) return
        reading = true
        try {
            val c = cache()
            var n = 0
            val live = HashSet<String>()
            for ((_, f) in songs()) {
                live += f
                val st = stampOf(f)
                val had = synchronized(lock) { c[f] }
                if (had != null && had.stamp == st) continue
                val r = readFile(f) ?: continue
                synchronized(lock) { c[f] = r }
                if (++n % 200 == 0) keepCache()
            }
            synchronized(lock) { c.keys.retainAll(live) }
            keepCache()
        } finally { reading = false }
    }

    /** Soon, in the background (the app opened, the switch turned on, after a scan or a sync). */
    fun refreshSoon() { if (on) Music.bg({ refresh() }) }

    /** Whether two values of a tag say the same thing. */
    private fun same(k: String, a: String, b: String): Boolean {
        fun n(x: String) = x.trim().lowercase().replace(Regex("\\s+"), " ")
        val x = n(a); val y = n(b)
        if (x == y) return true
        val sep = Regex("\\s*(,|;|/|&| feat\\.? | ft\\.? )\\s*")
        return when (k) {
            "artist", "albumartist" -> x.split(sep).filter { it.isNotEmpty() }.toSet() == y.split(sep).filter { it.isNotEmpty() }.toSet()
            "date" -> x.take(4) == y.take(4) && (x.length == 4 || y.length == 4)
            "tracknumber" -> x.substringBefore('/').trimStart('0') == y.substringBefore('/').trimStart('0')
            else -> false
        }
    }

    private fun nameOf(f: String) = norm(f.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.'))

    /** (blocking) What this device sends: its songs if it's the main device, with the covers the other device lacks
     *  (as it said at this sync, or the last one); else just its covers' fingerprints. theirs: what the other sent in
     *  this sync, if it came first. */
    fun outgoing(theirs: String?): String {
        if (!on) return ""
        val c = cache()
        val list = songs().mapNotNull { (t, f) -> synchronized(lock) { c[f] }?.let { r -> Triple(t, f, r) } }
        if (!isMain) {
            val have = list.associate { (t, f, r) -> "${TagChanges.key(t.title, t.artist)}#${nameOf(f)}" to r.art }
            return JSON.encodeToString(Sent.serializer(), Sent(main = main, have = have))
        }
        // Which covers the other device has: from what it sent now, else what it sent last time
        val peer = theirs?.let { runCatching { JSON.decodeFromString(Sent.serializer(), it) }.getOrNull() }?.have?.takeIf { it.isNotEmpty() }
            ?: readText(peerFile)?.let { runCatching { JSON.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull() }
            ?: emptyMap()
        val peerArt = peer.values.toHashSet()
        val entries = list.map { (t, f, r) ->
            Entry(t.isrc.orEmpty(), TagChanges.key(t.title, t.artist), nameOf(f), t.duration.toInt(), r.tags.filterValues { it.isNotBlank() }, r.art)
        }
        val art = LinkedHashMap<String, String>()
        var size = 0
        if (peer.isNotEmpty()) for ((_, f, r) in list) {
            if (r.art.isEmpty() || r.art in peerArt || r.art in art) continue
            val b = runCatching { FileTags.cover(Music.ctx, f) }.getOrNull() ?: continue
            val jpeg = runCatching { Covers.jpeg(b) }.getOrNull() ?: continue
            if (size + jpeg.size > ART_BUDGET) break
            size += jpeg.size
            art[r.art] = java.util.Base64.getEncoder().encodeToString(jpeg)
        }
        return JSON.encodeToString(Sent.serializer(), Sent(main = main, entries = entries, art = art))
    }

    /** (blocking) The other device's songs made the same here, when it's the main device; what it says it has kept for
     *  next time. How many songs were changed. */
    fun take(text: String?): Int {
        if (!on || text.isNullOrBlank()) return 0
        val got = runCatching { JSON.decodeFromString(Sent.serializer(), text) }.getOrNull() ?: return 0
        if (got.have.isNotEmpty() && isMain)
            writeAtomic(peerFile, JSON.encodeToString(MapSerializer(String.serializer(), String.serializer()), got.have))
        // Only the main device's songs are taken, and only by the other one (the main device is picked on either; the
        // choice goes across with your other settings)
        if (got.entries.isEmpty() || got.main == here) return 0
        val c = cache()
        // Your songs here that can be changed, by ISRC, by title and artist, and by file name
        val byIsrc = HashMap<String, MutableList<Pair<Track, String>>>()
        val byKey = HashMap<String, MutableList<Pair<Track, String>>>()
        val byName = HashMap<String, MutableList<Pair<Track, String>>>()
        for ((t, f) in songs()) {
            if (!Tags.writable(f)) continue
            t.isrc?.takeIf { it.isNotBlank() }?.let { byIsrc.getOrPut(it.uppercase()) { ArrayList() } += t to f }
            byKey.getOrPut(TagChanges.key(t.title, t.artist)) { ArrayList() } += t to f
            byName.getOrPut(nameOf(f)) { ArrayList() } += t to f
        }
        val playing = runCatching { Music.current() }.getOrNull()?.let { Music.lib.items[it.id]?.file ?: it.file }
        val waiting = TagSync.waitingKeys()   // songs you changed here that haven't gone across yet: theirs win
        var changed = 0
        val done = HashSet<String>()
        for (e in got.entries) {
            // The same song: both lengths known and within 2 seconds (a song with no length, as a broken file has, never matches)
            fun close(p: Pair<Track, String>) = e.secs > 0 && p.first.duration > 0 && Math.abs(p.first.duration - e.secs) <= 2
            val mine = (e.isrc.takeIf { it.isNotBlank() }?.let { byIsrc[it.uppercase()] }?.filter(::close)?.takeIf { it.isNotEmpty() }
                ?: byKey[e.key]?.filter(::close)?.takeIf { it.isNotEmpty() }
                ?: byName[e.name]?.filter(::close)?.takeIf { it.isNotEmpty() }) ?: continue
            for ((t, f) in mine) {
                if (f == playing || f in done) continue
                if (TagChanges.key(t.title, t.artist) in waiting) continue
                val now = synchronized(lock) { c[f] }?.takeIf { it.stamp == stampOf(f) } ?: readFile(f) ?: continue
                // Only what's missing here: a tag that's filled in is never changed by a sync (only by you, in the
                // Tagger or Edit tags, or a scan you run to change it)
                val changes = e.tags.filter { (k, v) -> k in FIELDS && v.isNotBlank() && now.tags[k].isNullOrBlank() }
                var did = false
                if (changes.isNotEmpty()) TagSync.quietly {
                    if (runCatching { Tags.write(Music.ctx, f, changes) }.isSuccess) did = true
                }
                // The cover only when it's the same album (a single's cover never goes onto the album version)
                val sameAlbum = norm(e.tags["album"].orEmpty()) == norm((changes["album"] ?: now.tags["album"]).orEmpty())
                // and only when the song has none here: a cover it has is never replaced or taken off by a sync
                if (sameAlbum && e.art.isNotEmpty() && now.art.isEmpty()) got.art[e.art]?.let { b64 ->
                    val jpeg = runCatching { java.util.Base64.getDecoder().decode(b64) }.getOrNull()
                    // A cover that's already here and looks the same (the same picture saved another way) stays as it is
                    val mine = if (now.art.isEmpty()) null else runCatching { FileTags.cover(Music.ctx, f) }.getOrNull()
                    val alike = jpeg != null && mine != null && run {
                        val a = MusicTools.pictureHash(jpeg); val b = MusicTools.pictureHash(mine)
                        a != null && b != null && java.lang.Long.bitCount(a xor b) <= 8
                    }
                    if (jpeg != null && !alike && runCatching { FileTags.writeCover(Music.ctx, f, jpeg) }.getOrDefault(false)) did = true
                }
                if (did) {
                    done += f; changed++
                    touched(f)
                    changes["title"]?.let { nt ->   // a download's name in your music follows (your own files: the scan)
                        Music.lib.items.values.filter { it.file == f }.forEach { le ->
                            Music.lib.update(le.id) { it.copy(title = nt, artist = changes["artist"] ?: it.artist,
                                album = changes["album"] ?: it.album, genre = changes["genre"] ?: it.genre) }
                        }
                    }
                }
            }
        }
        return changed
    }
}
