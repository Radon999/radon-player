package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** What Moods knows of one song file: when the file last changed (to read it again if it does), its sound (null: it
 *  couldn't be heard), whether that was tried, and Last.fm's tags of it (tag to Last.fm's count, 0–100) if fetched.
 *  full: the sound is the whole song's (Full scan), not a minute of it. len: the song's length when it was heard (a
 *  file changed but just as long only had its tags changed, so its sound is kept). looked: the file's own MOOD tag
 *  looked for (at this mtime); inFile: the mood that tag gives ("" none); wrote: the mood the app last wrote into the
 *  file. picked: the mood you gave it (Tagger). kept/keptSure: moods from a backup, used until it's heard here. */
@Serializable
data class MoodEntry(val mtime: Long = 0, val sound: SoundFeatures? = null, val heard: Boolean = false,
                     val tags: Map<String, Int> = emptyMap(), val fm: Boolean = false,
                     val full: Boolean = false, val len: Double = 0.0, val looked: Boolean = false, val inFile: String = "",
                     val wrote: String = "", val picked: String = "", val kept: List<String> = emptyList(), val keptSure: Double = 0.0) {
    /** The mood that's settled, not worked out: yours, or one in the file that the app didn't put there. */
    val confirmed: String get() = picked.ifEmpty { inFile.takeIf { it.isNotEmpty() && it != wrote }.orEmpty() }
}

@Serializable
internal data class MoodStore(val songs: Map<String, MoodEntry> = emptyMap(), val artists: Map<String, Map<String, Int>> = emptyMap())

/** Moods: every song of yours (files and downloads) gets a mood (Chill, Happy, Sad…) and up to two more, from its sound
 *  (a minute from its middle, or the whole song with Full scan, analysed on the device), its file's genre, and
 *  Last.fm's tags of it and its artist when there's a Last.fm key. The rules are in [MoodRules]. A mood you pick
 *  (Tagger) or one already in the file's MOOD tag is the song's mood. Each file is read once (again only when it
 *  changes), in the background, a song at a time at low priority, and kept in moods.json. A song not read yet still
 *  gets a mood from its genre when its genre says one. Moods can be written into the files' MOOD tags, and go into
 *  backups by song (title, artist, length), so they come back on another device without a scan. */
object Moods {
    /** The moods, in the order the Moods tab shows them. */
    val ALL: List<String> get() = MoodRules.ALL
    /** Bumped when results change. */
    var version by mutableStateOf(0)
    /** (done, total) while reading songs' moods, else null. */
    var reading by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    /** While reading: whether it's a Full scan, the song being heard ("Title · Artist"), about how many seconds are left (null: not known yet). */
    var readingFull by mutableStateOf(false)
        private set
    var readingNow by mutableStateOf("")
        private set
    var readingLeft by mutableStateOf<Long?>(null)
        private set
    /** Songs whose mood could go into their file and isn't there yet (counted after each scan; 0 when writing is off). */
    var toWrite by mutableStateOf(0)
        private set
    /** After a scan: that many files are waiting to be written, and the Moods tab should ask first (0: nothing to ask). */
    var askWrite by mutableStateOf(0)
    /** Writing moods into files now. */
    var writing by mutableStateOf(false)
        private set

    private val lock = Any()
    private val known = HashMap<String, MoodEntry>()
    private val artists = HashMap<String, Map<String, Int>>()
    @Volatile private var loaded = false
    @Volatile private var running = false
    @Volatile private var stopAsked = false
    private val file get() = File(Music.ctx.filesDir, "moods.json")
    /** Moods from a restored backup, waiting for their songs to turn up here. */
    private val restoreFile get() = File(Music.ctx.filesDir, "moods-restore.json")

    private const val WRITE_KEY = "moods_write"         // "on", "off", or "" (on once a Full scan has finished)
    private const val FULL_DONE_KEY = "moods_full_done"  // a Full scan went through every song once
    const val ASK_OVER = 25                              // more files than this to write: ask first

    private fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            runCatching { readText(file)?.let { JSON.decodeFromString(MoodStore.serializer(), it) } }.getOrNull()?.let {
                known.putAll(it.songs); artists.putAll(it.artists)
            }
            loaded = true
        }
    }

    private val saving = Any()   // the scan, writing files and a restore can save at once: one at a time

    private fun save() = runCatching {
        synchronized(saving) {
            val text = synchronized(lock) { JSON.encodeToString(MoodStore.serializer(), MoodStore(known.toMap(), artists.toMap())) }
            writeAtomic(file, text)
        }
    }

    /** A song's file (its own, or its download's), or null when it has none. */
    private fun pathOf(t: Track): String? = t.file?.takeIf { it.isNotEmpty() }
        ?: runCatching { Music.lib.items[t.id]?.file }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun lead(artist: String) = norm(artist.split(",")[0].trim())

    // ---- the moods, worked out from what's known (cheap; remembered until something changes) ----
    private class Memo(val stamp: Pair<Int, Int>) {
        val moods = ConcurrentHashMap<String, List<Pair<String, Double>>>()
        @Volatile var index: Map<String, List<Track>>? = null
    }
    @Volatile private var memo = Memo(-1 to -1)

    private fun memoNow(): Memo {
        val stamp = version to Music.musicVersion
        memo.let { if (it.stamp == stamp) return it }
        return Memo(stamp).also { memo = it }
    }

    /** A song's moods, best first, each with how sure (0–1); empty when nothing is known yet. */
    fun scored(t: Track): List<Pair<String, Double>> {
        val path = pathOf(t) ?: return emptyList()
        load()
        return memoNow().moods.getOrPut(path) {
            val e = synchronized(lock) { known[path] }
            val genre = runCatching { Music.genreOf(t) }.getOrDefault("").takeIf { it != "Unknown genre" } ?: ""
            val fromArtist = synchronized(lock) { artists[lead(t.artist)] }.orEmpty()
            val worked = MoodRules.decide(e?.sound, genre, e?.tags.orEmpty().mapValues { it.value / 100.0 }, fromArtist.mapValues { it.value / 100.0 })
            val sure = e?.confirmed.orEmpty()
            when {
                // Yours, or the file's: that's its mood, for sure; the others as worked out.
                sure.isNotEmpty() -> listOf(sure to 1.0) + worked.filter { it.first != sure }.take(2)
                // From a backup, until it's heard here.
                e != null && e.kept.isNotEmpty() ->
                    e.kept.take(3).mapIndexed { i, m -> m to (if (i == 0) e.keptSure else e.keptSure * 0.8) }
                else -> worked
            }
        }
    }

    /** A song's mood, null if not known yet (fast: from what's been read). */
    fun of(t: Track): String? = scored(t).firstOrNull()?.first

    /** A song's moods: its mood first, then the others. */
    fun all(t: Track): List<String> = scored(t).map { it.first }

    /** How sure the song's mood is (0–1; 0: no mood). */
    fun confidence(t: Track): Double = scored(t).firstOrNull()?.second ?: 0.0

    /** The mood you picked for the song in the Tagger, or "" (it's worked out). */
    fun picked(t: Track): String = pathOf(t)?.let { p -> load(); synchronized(lock) { known[p] }?.picked }.orEmpty()

    /** Your songs with that mood (as their mood or one of the others): the surest first. */
    fun songs(mood: String): List<Track> {
        val m = memoNow()
        val idx = m.index ?: run {
            val by = HashMap<String, MutableList<Pair<Track, Double>>>()
            for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList())) {
                scored(t).forEachIndexed { i, (name, sure) -> by.getOrPut(name) { ArrayList() } += t to (if (i == 0) 1 + sure else sure) }
            }
            by.mapValues { (_, l) -> l.sortedByDescending { it.second }.map { it.first } }.also { m.index = it }
        }
        return idx[mood].orEmpty()
    }

    /** How far the scans have got: songs with a mood that's read (heard, or yours, or in the file, or from a backup),
     *  songs heard whole (Full scan), and how many songs there are. */
    data class Counts(val done: Int, val full: Int, val total: Int)

    fun counts(): Counts {
        load()
        val paths = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNullTo(HashSet()) { pathOf(it) }
        return synchronized(lock) {
            Counts(paths.count { p -> known[p]?.let { it.heard || it.confirmed.isNotEmpty() || it.kept.isNotEmpty() } == true },
                paths.count { known[it]?.full == true }, paths.size)
        }
    }

    /** Words in a request ("something chill", "sad songs") and the mood they mean. */
    private val WORDS = mapOf(
        "chill" to "Chill", "chilled" to "Chill", "chillout" to "Chill", "relax" to "Chill", "relaxing" to "Chill", "lofi" to "Chill",
        "happy" to "Happy", "upbeat" to "Happy", "cheerful" to "Happy", "sunny" to "Happy", "feelgood" to "Happy",
        "sad" to "Sad", "melancholy" to "Sad", "melancholic" to "Sad", "heartbreak" to "Sad", "crying" to "Sad",
        "energetic" to "Energetic", "energy" to "Energetic", "workout" to "Energetic", "gym" to "Energetic", "running" to "Energetic",
        "party" to "Party", "dance" to "Party", "dancing" to "Party", "hype" to "Hype", "pumped" to "Hype", "bangers" to "Hype",
        "romantic" to "Romantic", "love" to "Romantic", "date" to "Romantic", "sexy" to "Romantic",
        "angry" to "Intense", "aggressive" to "Intense", "intense" to "Intense", "rage" to "Intense", "heavy" to "Intense",
        "dark" to "Dark", "moody" to "Dark", "calm" to "Calm", "mellow" to "Calm", "peaceful" to "Calm", "sleep" to "Calm",
        "quiet" to "Calm", "soft" to "Calm", "focus" to "Focus", "study" to "Focus", "studying" to "Focus", "concentrate" to "Focus",
        "dreamy" to "Dreamy", "ethereal" to "Dreamy", "spacey" to "Dreamy",
    ) + MoodRules.ALL.associate { it.lowercase() to it }

    /** The mood a request asks for ("something chill": Chill), or null. */
    fun fromWords(text: String): String? =
        Regex("[\\p{L}]+").findAll(text.lowercase()).firstNotNullOfOrNull { WORDS[it.value] }

    /** How a mood reads in a sentence: "a chill mood", "a focused mood". */
    fun adjective(mood: String): String = when (mood) { "Focus" -> "focused"; else -> mood.lowercase() }

    // ---- reading songs' moods in the background ----

    /** After a scan of your folders: moods from a restored backup given to the songs that have turned up, then the
     *  songs not read yet read (a minute each). On the phone the reading is only while it's charging, like the other
     *  background checks, so it never costs battery. */
    fun auto() {
        val restore = restoreFile.isFile
        if (!restore) { if (AudioTools.supported && AudioTools.canRunNow()) start(auto = true, full = false); return }
        // The backup's moods first, so the songs they're given to aren't heard again.
        Thread({
            runCatching { applyRestored() }.onFailure { Crashes.caught(it) }
            if (AudioTools.supported && AudioTools.canRunNow()) start(auto = true, full = false)
        }, "moods-restore").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    /** Scan moods (the Moods tab): a minute from the middle of every song not read yet, now. */
    fun find() = start(auto = false, full = false)

    /** Full scan (the Moods tab): every song heard whole, start to end, that isn't yet. Stopped, it carries on from the
     *  song it stopped at (each song's result is kept as soon as it's done). */
    fun findFull() = start(auto = false, full = true)

    /** Stop reading (what's been read is kept; the scan carries on from there next time). */
    fun stop() { stopAsked = true }

    val busy get() = running

    private fun start(auto: Boolean, full: Boolean) {
        if (running) return
        val list = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { it to t } }.distinctBy { it.first }
        if (list.isEmpty()) return
        running = true; stopAsked = false
        if (!auto) { reading = 0 to list.size; readingFull = full; readingNow = ""; readingLeft = null }
        Thread({
            var finished = false
            try { finished = work(list, auto, full) } catch (e: Throwable) { Crashes.caught(e) }
            finally {
                running = false
                save()
                if (full && finished) Prefs.set(FULL_DONE_KEY, true)
                val waiting = runCatching { if (writeOn()) writable().size else 0 }.getOrDefault(0)
                Music.scope.launch {
                    reading = null; readingNow = ""; readingLeft = null; version++
                    toWrite = waiting
                    if (waiting > ASK_OVER && !auto) askWrite = waiting
                    if (waiting in 1..ASK_OVER) writeFiles()   // a few (new songs): no need to ask
                }
            }
        }, "moods").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    /** The file's own MOOD tag, as one of the moods ("" none or none of ours). */
    private fun fileMood(path: String): String =
        runCatching { Music.ctx.openIn(path)?.buffered()?.use { MoodTag.pick(MoodTag.read(it)) } }.getOrNull().orEmpty()

    /** The entry for a file as it is now ([m]: its mtime): its MOOD tag looked at again when it's changed, its sound
     *  kept when only its tags changed (the same length), forgotten when it's another song now. */
    private fun fresh(path: String, t: Track, m: Long): MoodEntry {
        val e = synchronized(lock) { known[path] }
        if (e != null && e.mtime == m && e.looked) return e
        val sameSound = e != null && (e.mtime == m || (e.len > 0 && t.duration > 0 && kotlin.math.abs(e.len - t.duration) < 1.5))
        val base = when {
            e == null -> MoodEntry()
            sameSound -> e
            else -> MoodEntry(tags = e.tags, fm = e.fm, picked = e.picked, wrote = e.wrote)   // another sound: heard again
        }
        val now = base.copy(mtime = m, looked = true, inFile = fileMood(path))
        synchronized(lock) { known[path] = now }
        return now
    }

    /** Reads what needs reading; true if it went through all of it (not stopped). */
    private fun work(list: List<Pair<String, Track>>, auto: Boolean, full: Boolean): Boolean {
        load()
        val fmKey = LastFm.key.isNotEmpty()
        // Each file looked at first (cheap: when it changed, and its own MOOD tag if it did).
        val todo = ArrayList<Triple<String, Track, Long>>()
        for ((p, t) in list) {
            if (stopAsked || (auto && !AudioTools.canRunNow())) return false
            val m = runCatching { AudioTools.modified(p) }.getOrNull() ?: continue   // gone
            val e = fresh(p, t, m)
            val need = if (full) !e.full else
                (!e.heard && e.confirmed.isEmpty() && e.kept.isEmpty()) || (fmKey && !e.fm)   // settled moods needn't be heard
            if (need) todo += Triple(p, t, m)
        }
        Music.scope.launch { version++ }
        if (todo.isEmpty()) {
            if (!auto) Music.toast(if (full) "Every song has been heard whole already" else "Every song's mood is found already")
            return true
        }
        Music.scope.launch { reading = 0 to todo.size }
        var heardAny = false; var fails = 0
        val began = System.currentTimeMillis()
        var audioDone = 0.0   // seconds of songs heard so far (Full scan: for the time left)
        for ((i, x) in todo.withIndex()) {
            if (stopAsked || (auto && !AudioTools.canRunNow())) return false
            val (p, t, m) = x
            if (!auto) Music.scope.launch { readingNow = "${t.title} · ${t.artist.split(",")[0].trim()}" }
            val old = synchronized(lock) { known[p] }
            val reuse = !full && old != null && old.heard && old.sound != null   // only Last.fm's tags needed
            val sound = when {
                full -> runCatching { AudioTools.moodWhole(p, RATE) { stopAsked } }.getOrNull()
                reuse -> old?.sound
                else -> runCatching { SoundMood.features(AudioTools.moodPcm(p, t.duration, SECS, RATE)) }.getOrNull()
            }
            if (stopAsked) return false   // stopped in the middle of a song: it's heard again next time
            var tags = old?.tags.orEmpty()
            var fm = old?.fm == true
            if (fmKey && !fm) {
                runCatching { tags = trackTags(t) ; fm = true }
                val a = lead(t.artist)
                if (a.isNotEmpty() && synchronized(lock) { a !in artists }) runCatching {
                    val got = LastFm.artistTags(t.artist.split(",")[0].trim()).filter { it.first.isNotBlank() && it.second > 0 }
                        .associate { it.first.lowercase() to it.second }
                    synchronized(lock) { artists[a] = got }
                }
            }
            if (sound != null) heardAny = true else fails++
            val base = old ?: MoodEntry(mtime = m, looked = true)
            // Heard: the new sound (a whole song's replaces a minute's). Not heard: as it was, so a minute's result
            // isn't lost when the whole song can't be heard. Nothing heard at all yet (no decoder for it, ffmpeg
            // missing): not marked as read, so it's tried again later.
            val entry = if (reuse) base.copy(mtime = m, tags = tags, fm = fm)
                else if (sound != null) base.copy(mtime = m, sound = sound, heard = true, full = full, len = t.duration, tags = tags, fm = fm, kept = emptyList())
                else base.copy(mtime = m, heard = base.heard || heardAny, tags = tags, fm = fm)
            synchronized(lock) { known[p] = entry }
            if (!heardAny && fails >= 8) { Music.scope.launch { Music.toast("Couldn't hear your songs to find their moods") }; return false }
            val done = i + 1
            audioDone += t.duration.takeIf { it > 0 } ?: 200.0
            // Time left: from how fast it's gone so far (by songs for a minute each, by length for whole songs).
            val spent = (System.currentTimeMillis() - began) / 1000.0
            val left = if (done < 2 && spent < 20) null else if (full) {
                val audioLeft = todo.drop(done).sumOf { it.second.duration.takeIf { d -> d > 0 } ?: 200.0 }
                (audioLeft * spent / audioDone).toLong()
            } else ((todo.size - done) * spent / done).toLong()
            Music.scope.launch { reading = done to todo.size; readingLeft = left; if (full || done % 10 == 0) version++ }
            if (done % (if (full) 5 else 25) == 0) save()
            // Gentle: a pause between songs, longer while music plays, so playback never stutters.
            Thread.sleep(if (Music.paused) 40 else 300)
        }
        return true
    }

    /** Last.fm's tags of a song (tag to count, 0–100), the ones that say something. */
    private fun trackTags(t: Track): Map<String, Int> =
        LastFm.call("track.getTopTags", params = mapOf("artist" to t.artist.split(",")[0].trim(), "track" to t.title, "autocorrect" to 1))
            .path("toptags", "tag").list.take(15)
            .mapNotNull { x -> val name = x["name"].str?.lowercase()?.trim() ?: return@mapNotNull null; val n = x["count"].long?.toInt() ?: 0
                if (name.isEmpty() || n <= 0) null else name to n }
            .toMap()

    private const val SECS = 60      // seconds of each song heard by Scan moods (from its middle)
    private const val RATE = 22050   // samples a second they're heard at

    // ---- your own mood for a song (Tagger) ----

    /** Give songs a mood of your own (null: take it off, so it's worked out again): kept in the app, and written into
     *  the files that can be written (on the PC all of yours; on the phone the app's own downloads). */
    fun pick(tracks: List<Track>, mood: String?) {
        load()
        val paths = tracks.mapNotNull { t -> pathOf(t)?.let { it to t } }.distinctBy { it.first }
        if (paths.isEmpty()) { Music.toast("Only songs that are files (yours or downloaded) can have a mood"); return }
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        Music.bg({
            var wrote = 0; var skipped = 0
            for ((p, t) in paths) {
                val m = runCatching { AudioTools.modified(p) }.getOrNull() ?: continue
                val e = fresh(p, t, m)
                var now = e.copy(picked = mood.orEmpty())
                // Into the file: the new mood, or (taken off) the file's MOOD if it was this one, so it's worked out again.
                val change = when {
                    mood != null -> mood.takeIf { it != e.inFile }
                    e.inFile.isNotEmpty() && e.inFile == e.picked -> ""
                    else -> null
                }
                if (change != null && canWrite(p)) {
                    if (p == playing) skipped++
                    else if (runCatching { Tags.write(Music.ctx, p, mapOf("mood" to change)) }.isSuccess) {
                        wrote++
                        now = now.copy(mtime = AudioTools.modified(p) ?: m, inFile = change, wrote = if (mood == null) "" else now.wrote)
                    }
                }
                synchronized(lock) { known[p] = now }
            }
            save()
            wrote to skipped
        }) { r, _ ->
            version++
            val (wrote, skipped) = r ?: (0 to 0)
            Music.toast((if (mood == null) "Mood taken off" else "Mood: $mood") +
                (if (wrote > 0) " (written into ${"%,d".format(wrote)} file${if (wrote != 1) "s" else ""})" else "") +
                (if (skipped > 0) ". The playing song's file is left as it is" else ""))
        }
    }

    // ---- moods into the files ----

    /** Whether moods are written into the files: as you set it, else on once a Full scan has gone through every song. */
    fun writeOn(): Boolean = Prefs.text(WRITE_KEY).let { if (it.isEmpty()) Prefs.on(FULL_DONE_KEY) else it == "on" }

    /** Turn writing moods into files on or off (the Moods tab then counts the files waiting, see [recount]). */
    fun setWriteOn(on: Boolean) {
        Prefs.setText(WRITE_KEY, if (on) "on" else "off")
        if (!on) toWrite = 0
    }

    /** Count the files waiting for their mood, in the background (0 when writing is off). */
    fun recount() {
        if (!writeOn()) { toWrite = 0; return }
        Music.bg({ writable().size }) { n, _ -> toWrite = n ?: 0 }
    }

    /** Whether the app may change the file: on the PC any of yours; on the phone only its own downloads. */
    private fun canWrite(path: String) = runCatching { FileTags.canWrite(path) }.getOrDefault(false)

    /** (blocking) The songs whose mood should go into their file: a mood that's read (not only a guess from the
     *  genre), not what the file says already, and a file the app may change. */
    private fun writable(): List<Pair<String, String>> {
        load()
        val out = ArrayList<Pair<String, String>>()
        val seen = HashSet<String>()
        for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList())) {
            val p = pathOf(t) ?: continue
            val e = synchronized(lock) { known[p] } ?: continue
            if (!e.looked || !(e.heard || e.picked.isNotEmpty() || e.kept.isNotEmpty())) continue
            val m = scored(t).firstOrNull()?.first ?: continue
            if (m == e.inFile || !seen.add(p)) continue
            if (canWrite(p)) out += p to m
        }
        return out
    }

    /** Write the songs' moods into their files' MOOD tags, in the background (the playing song's waits till next time). */
    fun writeFiles() {
        if (writing || running) return   // not while songs are being heard (a file isn't rewritten while it's read)
        writing = true
        Music.bg({
            val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
            var done = 0; var failed = 0
            for ((p, m) in writable()) {
                if (p == playing) continue
                if (runCatching { Tags.write(Music.ctx, p, mapOf("mood" to m)) }.isSuccess) {
                    done++
                    // The file changed because of us: remember its new time, so it isn't read again for it.
                    val mt = runCatching { AudioTools.modified(p) }.getOrNull()
                    synchronized(lock) { known[p]?.let { e -> known[p] = e.copy(mtime = mt ?: e.mtime, inFile = m, wrote = m) } }
                } else failed++
                if (done > 0 && done % 50 == 0) save()
            }
            save()
            done to failed
        }) { r, _ ->
            writing = false; toWrite = 0; version++
            val (done, failed) = r ?: (0 to 0)
            if (done > 0 || failed > 0) Music.toast("Moods written into ${"%,d".format(done)} file${if (done != 1) "s" else ""}" +
                if (failed > 0) " (${"%,d".format(failed)} couldn't be written)" else "")
        }
    }

    // ---- backups ----

    /** (blocking) Every song's moods for a backup, by song (title, artist, album, length), not by file. */
    fun backup(): String {
        load()
        val out = ArrayList<MoodBackupSong>()
        val seen = HashSet<String>()
        for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList())) {
            val p = pathOf(t) ?: continue
            if (!seen.add(p)) continue
            val e = synchronized(lock) { known[p] } ?: continue
            if (!(e.heard || e.confirmed.isNotEmpty() || e.kept.isNotEmpty())) continue   // only a guess from the genre: nothing to keep
            val s = scored(t)
            if (s.isEmpty()) continue
            out += MoodBackupSong(t.title, t.artist, t.album, t.duration, s.map { it.first }, s[0].second, e.full,
                e.confirmed.isNotEmpty(), e.sound)
        }
        return JSON.encodeToString(ListSerializer(MoodBackupSong.serializer()), out)
    }

    /** A backup's moods, kept to be given to the songs here once they're read (after the app starts again). */
    fun restoreLater(text: String) {
        runCatching { JSON.decodeFromString(ListSerializer(MoodBackupSong.serializer()), text) }.getOrNull() ?: return
        writeAtomic(restoreFile, text)
    }

    /** (blocking) The waiting backup's moods given to the songs here that match (title and artist, length, album).
     *  A song heard here already keeps its own unless the backup's is better (heard whole, or yours). The ones that
     *  matched nothing wait for the next scan of your folders. */
    private fun applyRestored() {
        val saved = runCatching { JSON.decodeFromString(ListSerializer(MoodBackupSong.serializer()), readText(restoreFile) ?: return) }.getOrNull()
        if (saved == null) { restoreFile.delete(); return }
        load()
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { it to t } }.distinctBy { it.first }
        if (songs.isEmpty()) return   // your music isn't read yet: next time
        val byPath = songs.toMap()
        val got = MoodBackup.match(saved, songs.map { (p, t) -> MoodSongHere(p, t.title, t.artist, t.album, t.duration) })
        var n = 0
        for ((p, b) in got) {
            val t = byPath[p] ?: continue
            val e = synchronized(lock) { known[p] }
            val better = e == null || (!e.heard && e.confirmed.isEmpty()) || (b.full && !e.full) || (b.picked && e.picked.isEmpty())
            if (!better) continue
            val m = runCatching { AudioTools.modified(p) }.getOrNull() ?: continue
            val base = e ?: MoodEntry()
            synchronized(lock) {
                known[p] = base.copy(mtime = m, sound = b.sound ?: base.sound, heard = true, full = b.full || base.full,
                    len = t.duration, picked = if (b.picked) b.moods.first() else base.picked, kept = b.moods, keptSure = b.sure,
                    looked = base.looked && base.mtime == m)
            }
            n++
        }
        val used = got.values.toHashSet()
        val left = saved.filter { it !in used }
        if (left.isEmpty()) restoreFile.delete() else writeAtomic(restoreFile, JSON.encodeToString(ListSerializer(MoodBackupSong.serializer()), left))
        if (n > 0) {
            save()
            Music.scope.launch { version++; Music.toast("Moods from your backup given to ${"%,d".format(n)} song${if (n != 1) "s" else ""}") }
        }
    }
}
