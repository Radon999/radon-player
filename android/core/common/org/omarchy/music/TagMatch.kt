package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Tags match (Settings › Remote & sync): your songs that are on both devices end up the same. You pick the main device
 *  (the PC or the phone): at each sync it sends what every song of its says (title, artist, album, album artist, genre,
 *  year, track, the lyrics and their mood, and its cover), and the other device makes its copy of the song say exactly
 *  that: what differs is replaced, what the main device's hasn't got is taken off (never the title or the artist). The
 *  moods follow the same main device (MoodMatch). Changes you make by hand on the other device and haven't sent yet
 *  win until they have gone across (TagSync). Covers obey Prevent album cover changes.
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
    /** The receiving device: the main device's cover (its mark) last put into each file, so a picture the main device
     *  keeps as a PNG (its mark never the JPEG's here) isn't put in again at every sync. */
    private val appliedFile get() = File(dir, "tag-match-art.json")
    private val strMap = MapSerializer(String.serializer(), String.serializer())
    private var appliedArt: HashMap<String, String>? = null
    private fun applied(): HashMap<String, String> = synchronized(lock) {
        appliedArt ?: (readText(appliedFile)?.let { runCatching { HashMap(JSON.decodeFromString(strMap, it)) }.getOrNull() } ?: HashMap()).also { appliedArt = it }
    }
    private fun keepApplied() = synchronized(lock) { appliedArt?.let { writeAtomic(appliedFile, JSON.encodeToString(strMap, it)) } }
    /** The lyrics and their mood: read and written their own way (every kind of file), beside the tags. */
    private const val LYRICS = "lyrics"
    private const val LYRICS_MOOD = "mood_lyrics"
    private const val FILE_MOOD = "mood_file"
    /** A MOOD tag's moods, in order, as the app reads them ("happy; Sad" = Happy, Sad). */
    private fun moodsOf(text: String): List<String> = MoodTag.pickAll(text).split(';').filter { it.isNotBlank() }
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
                     val tags: Map<String, String> = emptyMap(), val art: String = "",
                     /** The song's moods on the main device ("Happy;Sad"), as the app has them. */
                     val mood: String = "")

    /** What goes across: this device's songs (only when it's the main one), the covers the other lacks, and the
     *  fingerprints of this device's covers (so the main device knows which to send). */
    @Serializable
    data class Sent(val main: String = "", val entries: List<Entry> = emptyList(), val art: Map<String, String> = emptyMap(),
                    val have: Map<String, String> = emptyMap(),
                    /** The main device has covers left that didn't fit in this sync: the phone syncs again by itself. */
                    val more: Boolean = false)

    /** The main device's covers sent in this run of syncs (song and picture), so a round never sends one twice and
     *  the rounds come to an end even when the other device can't take a picture (Prevent album cover changes). */
    private val sentThisRun = HashSet<String>()
    @Volatile private var sentAt = 0L
    /** The main device's last outgoing had covers left over. */
    @Volatile var lastMore = false
        private set

    /** Whether what the other device sent says it has covers left for here. */
    fun moreIn(text: String?): Boolean = !text.isNullOrBlank() &&
        runCatching { JSON.decodeFromString(Sent.serializer(), text).more }.getOrDefault(false)

    private val readSer = MapSerializer(String.serializer(), Read.serializer())
    private var cache: HashMap<String, Read>? = null
    @Volatile private var reading = false

    private fun cache(): HashMap<String, Read> = synchronized(lock) {
        cache ?: (readText(cacheFile)?.let { runCatching { HashMap(JSON.decodeFromString(readSer, it)) }.getOrNull() } ?: HashMap())
            .also { cache = it }
    }

    private fun keepCache() = synchronized(lock) { cache?.let { writeAtomic(cacheFile, JSON.encodeToString(readSer, it)) } }

    /** A file the app has just changed (its tags or cover): read again next time. */
    fun touched(uri: String) { synchronized(lock) { cache?.remove(uri); appliedArt?.remove(uri) } }

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
        val tags = Tags.read(Music.ctx, listOf(uri)).mapValues { it.value.orEmpty() }.filterKeys { it in FIELDS } +
            (runCatching { Music.ctx.openIn(uri)?.use { MoodTag.readTags(it, setOf(MoodTag.LYRICS, MoodTag.MOOD_LYRICS, MoodTag.MOOD)) } }.getOrNull().orEmpty()
                .let { m -> listOfNotNull(m[MoodTag.LYRICS]?.let { LYRICS to it }, m[MoodTag.MOOD_LYRICS]?.let { LYRICS_MOOD to it },
                    m[MoodTag.MOOD]?.let { FILE_MOOD to it }).toMap() })
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
        lastMore = false
        val c = cache()
        val list = songs().mapNotNull { (t, f) -> synchronized(lock) { c[f] }?.let { r -> Triple(t, f, r) } }
        if (!isMain) {
            val ap = applied()
            // (a cover the main device's was put in: said as the main device knows it, so it isn't sent again)
            val have = list.associate { (t, f, r) -> "${TagChanges.key(t.title, t.artist)}#${nameOf(f)}" to (synchronized(lock) { ap[f] } ?: r.art) }
            return JSON.encodeToString(Sent.serializer(), Sent(main = main, have = have))
        }
        // Which covers the other device has: from what it sent now, else what it sent last time
        val peer = theirs?.let { runCatching { JSON.decodeFromString(Sent.serializer(), it) }.getOrNull() }?.have?.takeIf { it.isNotEmpty() }
            ?: readText(peerFile)?.let { runCatching { JSON.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull() }
            ?: emptyMap()
        val entries = list.map { (t, f, r) ->
            Entry(t.isrc.orEmpty(), TagChanges.key(t.title, t.artist), nameOf(f), t.duration.toInt(), r.tags.filterValues { it.isNotBlank() }.filterKeys { it != FILE_MOOD }, r.art,
                runCatching { Moods.moodText(t) }.getOrDefault("").ifEmpty { r.tags[FILE_MOOD].orEmpty() })
        }
        // The covers the other device's copies haven't got (as it said, song by song): as many as fit, the rest next time
        val art = LinkedHashMap<String, String>()
        var size = 0
        var more = false
        synchronized(sentThisRun) { if (System.currentTimeMillis() - sentAt > 20 * 60_000L) sentThisRun.clear(); sentAt = System.currentTimeMillis() }
        for ((t, f, r) in list) {
            if (r.art.isEmpty() || r.art in art) continue
            val songKey = "${TagChanges.key(t.title, t.artist)}#${nameOf(f)}"
            if (peer[songKey] == r.art) continue
            if (synchronized(sentThisRun) { "$songKey|${r.art}" in sentThisRun }) continue
            if (size >= ART_BUDGET) { more = true; break }
            val b = runCatching { FileTags.cover(Music.ctx, f) }.getOrNull() ?: continue
            val jpeg = runCatching { Covers.jpeg(b) }.getOrNull() ?: continue
            if (size > 0 && size + jpeg.size > ART_BUDGET) { more = true; break }
            size += jpeg.size
            art[r.art] = java.util.Base64.getEncoder().encodeToString(jpeg)
            synchronized(sentThisRun) { sentThisRun += "$songKey|${r.art}" }
        }
        lastMore = more
        return JSON.encodeToString(Sent.serializer(), Sent(main = main, entries = entries, art = art, more = more))
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
                // Exactly as the main device's copy says: what differs is replaced, what it hasn't got is taken off. Never
                // a title or an artist taken off, and nothing at all from a copy the main device couldn't read the title of.
                if (e.tags["title"].isNullOrBlank()) continue
                val changes = FIELDS.mapNotNull { k ->
                    val want = e.tags[k].orEmpty(); val have = now.tags[k].orEmpty()
                    when {
                        want.isBlank() && (k == "title" || k == "artist") -> null
                        want.isBlank() && have.isBlank() -> null
                        want.isNotBlank() && have.isNotBlank() && (want.trim() == have.trim() || same(k, want, have) && k != "artist" && k != "albumartist") -> null
                        else -> k to want
                    }
                }.toMap()
                var did = false; var artPut = false
                if (changes.isNotEmpty()) TagSync.quietly {
                    if (runCatching { Tags.write(Music.ctx, f, changes) }.isSuccess) did = true
                }
                // The lyrics and their mood, the same way
                val lyr = e.tags[LYRICS].orEmpty(); val lm = e.tags[LYRICS_MOOD].orEmpty()
                fun plain(x: String) = x.replace("\r\n", "\n").replace('\r', '\n').trim()
                val lyrDiffers = plain(lyr) != plain(now.tags[LYRICS].orEmpty())
                val lmDiffers = plain(lm) != plain(now.tags[LYRICS_MOOD].orEmpty())
                if (lyrDiffers || lmDiffers) TagSync.quietly {
                    if (runCatching { LyricsTags.writeTags(Music.ctx, f, if (lyrDiffers) lyr else null, if (lmDiffers) mapOf(MoodTag.MOOD_LYRICS to lm) else emptyMap()) }.getOrDefault(false)) did = true
                }
                // The moods: exactly the main device's, in its order (extra ones here taken off), in the file and in the app.
                // Left alone when they are the same already, and when the main device has none for the song yet.
                val wantMoods = moodsOf(e.mood)
                if (wantMoods.isNotEmpty() && !Moods.prevented) {
                    if (moodsOf(now.tags[FILE_MOOD].orEmpty()) != wantMoods) TagSync.quietly {
                        if (runCatching { LyricsTags.writeMood(Music.ctx, f, wantMoods.joinToString(";")) }.getOrDefault(false)) did = true
                    }
                    if (moodsOf(runCatching { Moods.moodText(t) }.getOrDefault("")) != wantMoods) { runCatching { Moods.takeExact(t, wantMoods) }; did = true }
                }
                // The cover: the main device's picture when this copy's isn't it (Prevent album cover changes stops it)
                if (e.art.isNotEmpty() && e.art != now.art && synchronized(lock) { applied()[f] } != e.art && CoverGuard.allowed()) {
                    val jpeg = got.art[e.art]?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }
                    if (jpeg != null) {
                        val wrote = runCatching {
                            if (Music.lib.items.values.any { it.file == f && it.format == "flac" }) { Covers.write(Music.ctx, f, jpeg); true }
                            else FileTags.writeCover(Music.ctx, f, jpeg)
                        }.getOrDefault(false)
                        if (wrote) {
                            did = true; artPut = true
                            Music.lib.items.values.filter { it.file == f }.forEach { le ->   // a download's picture in your music follows
                                runCatching {
                                    val pic = File(Music.lib.artDir, "${safeName(le.id)}-${randomHex(4)}.jpg").apply { writeBytes(jpeg) }
                                    val old = le.art
                                    Music.lib.update(le.id) { it.copy(art = pic.path) }
                                    if (old.isNotEmpty() && Music.lib.items.values.none { it.art == old }) File(old).delete()
                                }
                            }
                        }
                    }
                }
                if (did) {
                    done += f; changed++
                    touched(f)
                    if (artPut) synchronized(lock) { applied()[f] = e.art }   // (after touched, which lets the record go)
                    changes["title"]?.let { nt ->   // a download's name in your music follows (your own files: the scan)
                        Music.lib.items.values.filter { it.file == f }.forEach { le ->
                            Music.lib.update(le.id) { it.copy(title = nt, artist = changes["artist"] ?: it.artist,
                                album = changes["album"] ?: it.album, genre = changes["genre"] ?: it.genre) }
                        }
                    }
                }
            }
        }
        keepApplied()
        return changed
    }
}
