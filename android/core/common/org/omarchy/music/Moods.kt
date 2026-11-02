package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** What Moods knows of one song file: when the file last changed (to read it again if it does), its sound (null: it
 *  couldn't be heard), whether that was tried, and Last.fm's tags of it (tag to Last.fm's count, 0–100) if fetched.
 *  full: the sound is the whole song's (Full scan), not a minute of it. len: the song's length when it was heard (a
 *  file changed but just as long only had its tags changed, so its sound is kept). looked: the file's own MOOD tag
 *  looked for (at this mtime); inFile: the mood that tag gives ("" none); wrote: the mood the app last wrote into the
 *  file. picked: the mood you gave it (Tagger). kept/keptSure: moods from a backup, used until it's heard here.
 *  lyr: what its words say (the MOOD_LYRICS line, see [LyricsReading]; "" not read yet), lyrInFile: that line is in the
 *  file itself, lyrTried: when its words were last looked for and none were found (0: not). */
@Serializable
data class MoodEntry(val mtime: Long = 0, val sound: SoundFeatures? = null, val heard: Boolean = false,
                     val tags: Map<String, Int> = emptyMap(), val fm: Boolean = false,
                     val full: Boolean = false, val len: Double = 0.0, val looked: Boolean = false, val inFile: String = "",
                     val wrote: String = "", val picked: String = "", val kept: List<String> = emptyList(), val keptSure: Double = 0.0,
                     val lyr: String = "", val lyrInFile: Boolean = false, val lyrTried: Double = 0.0) {
    /** The mood that's settled, not worked out: yours, or one in the file that the app didn't put there. */
    val confirmed: String get() = picked.ifEmpty { inFile.takeIf { it.isNotEmpty() && it != wrote }.orEmpty() }
}

/** moods.json: every song's entry (by its file), Last.fm's tags of the artists, the nudges learnt from your fixes (by
 *  file: mood to nudge, see [MoodLearn]), and the songs you said look right (Check these). */
@Serializable
internal data class MoodStore(val songs: Map<String, MoodEntry> = emptyMap(), val artists: Map<String, Map<String, Int>> = emptyMap(),
                              val bias: Map<String, Map<String, Double>> = emptyMap(), val ok: List<String> = emptyList())

/** Moods: every song of yours (files and downloads) gets every mood that fits it (Chill, Happy, Sad…; the best fit is
 *  its main mood), each with its own score, from its sound
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

    /** While writing: files written so far and how many there are to write. */
    var writeProgress by mutableStateOf(0 to 0)
        private set

    private val lock = Any()
    private val known = HashMap<String, MoodEntry>()
    private val artists = HashMap<String, Map<String, Int>>()
    private val bias = HashMap<String, Map<String, Double>>()   // learnt from your fixes, see [learn]
    private val ok = HashSet<String>()                          // songs you said look right (Check these)
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
                known.putAll(it.songs); artists.putAll(it.artists); bias.putAll(it.bias); ok.addAll(it.ok)
            }
            loaded = true
        }
    }

    private val saving = Any()   // the scan, writing files and a restore can save at once: one at a time

    private fun save() = runCatching {
        synchronized(saving) {
            val text = synchronized(lock) { JSON.encodeToString(MoodStore.serializer(), MoodStore(known.toMap(), artists.toMap(), bias.toMap(), ok.toList())) }
            writeAtomic(file, text)
        }
    }

    /** A song's file (its own, or its download's), or null when it has none. */
    private fun pathOf(t: Track): String? = t.file?.takeIf { it.isNotEmpty() }
        ?: runCatching { Music.lib.items[t.id]?.file }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun lead(artist: String) = norm(artist.split(",")[0].trim())

    // ---- the moods, worked out from what's known (cheap; remembered until something changes) ----

    /** What's known of a song's moods: [moods], every mood that fits it, best first, each with its score (0–1; the first
     *  is its main mood); [confidence], how sure (0–1); [conflict], how much its sound and its words disagree (0–1);
     *  [settled]: yours, the file's or a backup's, not worked out; [doubt]: worth a look (Check these). */
    class Judged(val moods: List<Pair<String, Double>>, val confidence: Double, val conflict: Double, val settled: Boolean, val doubt: Boolean)

    private val NONE = Judged(emptyList(), 0.0, 0.0, false, false)

    /** The rules' version: bumped when they change, so what's remembered is worked out again (from what's stored:
     *  no song needs hearing again). */
    private const val RULES = 2

    private class Memo(val stamp: Triple<Int, Int, Int>) {
        val judged = ConcurrentHashMap<String, Judged>()
        @Volatile var index: Map<String, List<Track>>? = null
    }
    @Volatile private var memo = Memo(Triple(-1, -1, -1))

    private fun memoNow(): Memo {
        val stamp = Triple(version, Music.musicVersion, RULES)
        memo.let { if (it.stamp == stamp) return it }
        return Memo(stamp).also { memo = it }
    }

    /** The moods the song's own clues give ([MoodRules.judge]): its sound, genre, Last.fm's tags, its words, and the
     *  nudge learnt from your fixes. */
    private fun worked(t: Track, e: MoodEntry?, nudge: Map<String, Double>): MoodRules.Judgement {
        val genre = runCatching { Music.genreOf(t) }.getOrDefault("").takeIf { it != "Unknown genre" } ?: ""
        val fromArtist = synchronized(lock) { artists[lead(t.artist)] }.orEmpty()
        val words = e?.lyr?.takeIf { it.isNotEmpty() }?.let { LyricsReading.parse(it) }
        return MoodRules.judge(e?.sound, genre, e?.tags.orEmpty().mapValues { it.value / 100.0 }, fromArtist.mapValues { it.value / 100.0 },
            words = words, bias = nudge)
    }

    private fun judge(t: Track, path: String): Judged {
        val e = synchronized(lock) { known[path] }
        val nudge = if (e?.picked.isNullOrEmpty()) synchronized(lock) { bias[path] }.orEmpty() else emptyMap()
        val j = worked(t, e, nudge)
        val words = e?.lyr?.isNotEmpty() == true
        // A mood in the file's MOOD tag is the song's mood. (One the app wrote there itself is what it worked out, so
        // that stands while there's nothing newer to go by: the song not heard, nor its words read, here.)
        val sure = e?.confirmed.orEmpty().ifEmpty {
            if (e != null && e.inFile.isNotEmpty() && !e.heard && !words) e.inFile else ""
        }
        val sures = sure.split(';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val moods = when {
            // Yours (Tagger): exactly those, in that order. Several in the file: exactly those too.
            e != null && e.picked.isNotEmpty() && sures.isNotEmpty() -> sures.map { it to 1.0 }
            sures.size > 1 -> sures.map { it to 1.0 }
            // One in the file (from another app, say): that's its main mood; the others that fit as worked out.
            sures.size == 1 -> listOf(sures[0] to 1.0) + j.moods.filter { it.first != sures[0] }.take(MoodRules.MOST - 1)
            // From a backup, until it's heard here.
            e != null && e.kept.isNotEmpty() ->
                e.kept.take(MoodRules.MOST).mapIndexed { i, m -> m to (if (i == 0) e.keptSure else e.keptSure * 0.8) }
            else -> null
        }
        if (moods != null) return Judged(moods, if (sures.isNotEmpty()) 1.0 else e?.keptSure ?: 0.0, j.conflict, true, false)
        // Worth a look: the sound and the words plainly disagree, or there's more than one clue and it's still unsure.
        val doubt = j.moods.isNotEmpty() && (j.conflict >= 0.5 || (j.clues >= 2 && j.confidence < 0.5))
        return Judged(j.moods, j.confidence, j.conflict, false, doubt)
    }

    /** What's known of the song's moods (see [Judged]); remembered until something changes. */
    fun judged(t: Track): Judged {
        val path = pathOf(t) ?: return NONE
        load()
        return memoNow().judged.getOrPut(path) { judge(t, path) }
    }

    /** A song's moods (every one that fits), best first, each with its score (0–1); empty when nothing is known yet. */
    fun scored(t: Track): List<Pair<String, Double>> = judged(t).moods

    /** A song's main mood (the one that fits best), null if not known yet (fast: from what's been read). For anything
     *  that needs one mood a song (shares that add up to 100%, like Wrapped's). */
    fun of(t: Track): String? = scored(t).firstOrNull()?.first

    /** A song's moods: every one that fits, its main mood first. */
    fun all(t: Track): List<String> = scored(t).map { it.first }

    /** How sure the song's moods are (0–1; 0: no mood). */
    fun confidence(t: Track): Double = judged(t).let { if (it.moods.isEmpty()) 0.0 else it.confidence }

    /** The mood you picked for the song in the Tagger, or "" (it's worked out). */
    fun picked(t: Track): String = pathOf(t)?.let { p -> load(); synchronized(lock) { known[p] }?.picked }.orEmpty()

    /** Your songs with that mood (their main mood or one of the others that fit): the ones it fits best first. */
    fun songs(mood: String): List<Track> {
        val m = memoNow()
        val idx = m.index ?: run {
            val by = HashMap<String, MutableList<Pair<Track, Double>>>()
            for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList())) {
                // The songs it fits best first; a song whose main mood it is a little ahead of one that only also fits.
                scored(t).forEachIndexed { i, (name, fit) -> by.getOrPut(name) { ArrayList() } += t to (if (i == 0) fit + 0.15 else fit) }
            }
            by.mapValues { (_, l) -> l.sortedByDescending { it.second }.map { it.first } }.also { m.index = it }
        }
        return idx[mood].orEmpty()
    }

    // ---- Check these: songs whose moods may well be wrong ----

    /** Bumped when you say a song looks right (the Check these list changes, the moods don't). */
    var checked by mutableStateOf(0)
        private set

    /** Your songs whose moods are worth a look (their sound and words disagree, or they're unsure), not settled (yours,
     *  the file's) and not said to look right: the most doubtful first. */
    fun checkThese(): List<Pair<Track, Judged>> {
        load()
        val fine = synchronized(lock) { HashSet(ok) }
        val seen = HashSet<String>()
        val out = ArrayList<Pair<Track, Judged>>()
        for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList())) {
            val p = pathOf(t) ?: continue
            if (p in fine || !seen.add(p)) continue
            val j = judged(t)
            if (j.doubt) out += t to j
        }
        return out.sortedWith(compareByDescending<Pair<Track, Judged>> { it.second.conflict }.thenBy { it.second.confidence })
    }

    /** "Looks right": the song leaves Check these for good (its moods stay worked out as they are). */
    fun looksRight(t: Track) {
        val p = pathOf(t) ?: return
        load()
        synchronized(lock) { ok += p }
        checked++
        Music.bg({ save(); Unit })
    }

    /** Why a song is in Check these, in a few words. */
    fun doubtWhy(j: Judged): String = when {
        j.conflict >= 0.5 -> "its sound and its words disagree"
        else -> "not much to go on yet"
    }

    // ---- learning from your fixes ----

    /** (blocking) Learns from the moods you set in the Tagger (see [MoodLearn]): each song you set nudges the few songs
     *  that sound most like it, and its artist's other songs, a little toward what you chose. Worked out again from all
     *  your fixes each time, so taking a fix off takes its nudges off too. */
    private fun learn() {
        load()
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { it to t } }.distinctBy { it.first }
        val snap = synchronized(lock) { HashMap(known) }
        val fixes = songs.mapNotNull { (p, t) ->
            val e = snap[p]?.takeIf { it.picked.isNotEmpty() } ?: return@mapNotNull null
            val worked = runCatching { worked(t, e, emptyMap()).moods.map { it.first } }.getOrDefault(emptyList())
            MoodLearn.fix(LearnSong(p, lead(t.artist), e.sound), e.picked.split(';').map { it.trim() }, worked)
        }
        val got = if (fixes.isEmpty()) emptyMap() else MoodLearn.biases(songs.map { (p, t) -> LearnSong(p, lead(t.artist), snap[p]?.sound) }, fixes)
        synchronized(lock) { bias.clear(); bias.putAll(got) }
    }

    /** How far the scans have got: songs with a mood that's read (heard, or yours, or in the file, or from a backup),
     *  songs heard whole (Full scan), and how many songs there are; songs whose file has a MOOD tag; songs whose files
     *  haven't been looked at yet; songs whose words have been read (and of them, in the file), and songs with no words
     *  found (instrumentals count as read); songs whose words were read by the older word reader (read again next time),
     *  and songs whose words were read on this device (not by the AI). */
    data class Counts(val done: Int, val full: Int, val total: Int, val inFiles: Int = 0, val unlooked: Int = 0,
                      val words: Int = 0, val wordsInFiles: Int = 0, val noWords: Int = 0,
                      val wordsOld: Int = 0, val wordsOnDevice: Int = 0)

    fun counts(): Counts {
        load()
        val paths = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNullTo(HashSet()) { pathOf(it) }
        return synchronized(lock) {
            Counts(paths.count { p -> known[p]?.let { it.heard || it.confirmed.isNotEmpty() || it.kept.isNotEmpty() || it.inFile.isNotEmpty() } == true },
                paths.count { known[it]?.let { e -> e.full || e.inFile.isNotEmpty() } == true }, paths.size,
                paths.count { known[it]?.inFile?.isNotEmpty() == true },
                paths.count { known[it]?.looked != true },
                paths.count { known[it]?.lyr?.isNotEmpty() == true },
                paths.count { known[it]?.lyrInFile == true },
                paths.count { known[it]?.let { e -> e.lyr.isEmpty() && e.lyrTried > 0 } == true },
                paths.count { known[it]?.lyr?.let { l -> LyricsReading.parse(l)?.outdated } == true },
                paths.count { known[it]?.lyr?.let { l -> LyricsReading.parse(l)?.let { r -> !r.instrumental && r.by != "ai" } } == true })
        }
    }

    /** Where a song's mood came from, in a few words ("in the file", "sound + words", "you"…); "" when it has none. */
    fun sources(t: Track): String {
        val path = pathOf(t) ?: return ""
        load()
        val e = synchronized(lock) { known[path] }
        val words = e?.lyr?.let { LyricsReading.parse(it) }?.takeIf { !it.instrumental && it.mood.isNotEmpty() }
        return when {
            e == null -> if (scored(t).isNotEmpty()) "genre" else ""
            e.picked.isNotEmpty() -> "you (Tagger)"
            e.inFile.isNotEmpty() && e.inFile != e.wrote -> "the file's MOOD tag"
            e.kept.isNotEmpty() && !e.heard -> "a backup"
            else -> listOfNotNull(
                (if (e.full) "whole song's sound" else "sound").takeIf { e.sound != null },
                (if (words?.by == "ai") "words (AI)" else "words").takeIf { words != null },
                "Last.fm tags".takeIf { e.tags.isNotEmpty() },
            ).joinToString(" + ").ifEmpty { if (scored(t).isNotEmpty()) "genre" else "" }
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
        lookFiles()   // moods already in the files (written on the PC, say) noticed straight away, with no scan
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
                // Newly heard songs can be nudged by your fixes too.
                runCatching { if (synchronized(lock) { known.values.any { it.picked.isNotEmpty() } }) learn() }.onFailure { Crashes.caught(it) }
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

    /** The file's own MOOD tag, as one of the moods ("" none or none of ours), and its MOOD_LYRICS line ("" none).
     *  Read straight from the file (no tag library), so it works for every file the app can open, on the phone too. */
    private fun fileMoods(path: String): Pair<String, String> {
        val tags = runCatching { Music.ctx.openIn(path)?.buffered()?.use { MoodTag.readTags(it, setOf(MoodTag.MOOD, MoodTag.MOOD_LYRICS)) } }.getOrNull().orEmpty()
        return MoodTag.pickAll(tags[MoodTag.MOOD]) to tags[MoodTag.MOOD_LYRICS]?.takeIf { LyricsReading.parse(it) != null }.orEmpty()
    }

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
        val (mood, words) = fileMoods(path)
        // The words' reading: the file's, unless the app has a better one of its own (the AI's, or a newer reader's,
        // that couldn't be written into the file yet).
        val kept = base.lyr.takeIf { !base.lyrInFile }.orEmpty()
        val keepOwn = kept.isNotEmpty() && (words.isEmpty() ||
            (LyricsReading.parse(kept)?.rank ?: 0) > (LyricsReading.parse(words)?.rank ?: 0))
        val now = base.copy(mtime = m, looked = true, inFile = mood,
            lyr = if (keepOwn) kept else words, lyrInFile = !keepOwn && words.isNotEmpty())
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
            // A song whose file already has a MOOD tag has its mood: neither scan hears it again.
            // (Unless the app put it there and it was sent to hear the song again: see [hearAgainFromElsewhere].)
            val need = if (e.inFile.isNotEmpty() && (e.heard || e.inFile != e.wrote)) false else if (full) !e.full else
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

    // ---- moods already in the files ----

    /** (looked at, of) while the files are looked at for moods already in them, else null. */
    var looking by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    @Volatile private var lookRunning = false

    /** A quick look at the files not looked at yet (their own MOOD and MOOD_LYRICS tags only, no sound), so moods
     *  already in them (written on the PC, then copied to the phone) count straight away, with no scan. Runs by itself
     *  when the Moods tab opens and after your music is scanned; a scan does the same as it goes, so it gives way. */
    fun lookFiles() {
        if (lookRunning || running) return
        lookRunning = true
        Thread({
            var any = false
            try {
                load()
                val list = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { it to t } }.distinctBy { it.first }
                val todo = list.filter { (p, _) -> synchronized(lock) { known[p] }?.looked != true }
                if (todo.isNotEmpty()) {
                    any = true
                    Music.scope.launch { looking = 0 to todo.size }
                    for ((i, x) in todo.withIndex()) {
                        if (running) break   // a scan looks at them itself
                        val (p, t) = x
                        val m = runCatching { AudioTools.modified(p) }.getOrNull() ?: continue   // gone
                        fresh(p, t, m)
                        if (i % 25 == 24) { val d = i + 1; Music.scope.launch { looking = d to todo.size; version++ } }
                    }
                    save()
                }
            } catch (e: Throwable) { Crashes.caught(e) }
            finally {
                lookRunning = false
                if (any) Music.scope.launch { looking = null; version++ }
            }
        }, "moods-look").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    // ---- moods from the songs' words ----

    /** (done, of) while reading moods from songs' words, else null; the song being read. */
    var wordsReading by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var wordsNow by mutableStateOf("")
        private set
    @Volatile private var wordsRunning = false
    @Volatile private var wordsStop = false
    val wordsBusy get() = wordsRunning
    fun stopWords() { wordsStop = true }

    /** Settings: the Anthropic key for the AI reading (ends in _token: never in backups or sync), and the switch. */
    const val AI_KEY = "anthropic_token"
    /** The key as pasted, without spaces or line breaks (a key copied from a wrapped line had one in the middle). */
    fun cleanKey(k: String): String = k.filterNot { it.isWhitespace() }
    /** A message with any key in it hidden, so it's never shown or logged. */
    fun hideKey(msg: String): String = msg.replace(Regex("sk-ant-[A-Za-z0-9_\\-]+"), "sk-ant-…")
    const val AI_ON = "moods_ai"
    /** Set once "Read again with AI" has gone through the songs read on the device (or you chose to keep them). */
    const val AI_AGAIN_DONE = "moods_ai_again_done"
    private const val AI_MODEL = "claude-haiku-4-5"
    /** The AI reading's switch: as you set it; until you do, on once a key is saved. */
    fun aiSwitch(): Boolean = Prefs.on(AI_ON, Prefs.text(AI_KEY).isNotBlank())
    /** Whether songs' words are read by the AI (the switch on, and a key). */
    fun aiOn(): Boolean = aiSwitch() && Prefs.text(AI_KEY).isNotBlank()

    /** Read moods from lyrics (the Moods tab): each song's words (from its file, kept in the app, or found online as the
     *  Lyrics tab finds them) read for their mood, on the device (or by the AI, when it's on), and the result written
     *  into the file's MOOD_LYRICS tag where the app may change the file (else kept in the app), so it's never read
     *  again. Songs whose file has it already are skipped; songs with no words found are tried again after a month.
     *  Readings by an older word reader are read again (by the new one, or the AI when it's on). again: also read again
     *  by the AI the songs read on the device (Read again with AI); a song the AI has read is never read again. */
    fun readWords(again: Boolean = false) {
        if (wordsRunning) return
        wordsRunning = true; wordsStop = false
        wordsReading = 0 to 0; wordsNow = ""
        Thread({
            var stats = Triple(0, 0, 0)
            var all = false
            try { val (r, n, w, through) = wordsWork(again); stats = Triple(r, n, w); all = through } catch (e: Throwable) { Crashes.caught(e) }
            finally {
                wordsRunning = false
                save()
                if (again && all) Prefs.set(AI_AGAIN_DONE, true)   // offered once: done
                val (read, none, wrote) = stats
                Music.scope.launch {
                    wordsReading = null; wordsNow = ""; version++
                    if (read + none > 0) Music.toast("Words read for %,d song%s".format(read, if (read != 1) "s" else "") +
                        (if (wrote > 0) " (%,d written into the files)".format(wrote) else "") +
                        (if (none > 0) ". %,d have no lyrics to be found".format(none) else ""))
                }
            }
        }, "moods-words").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    private data class WordsDone(val read: Int, val none: Int, val wrote: Int, val through: Boolean)

    /** (read, none found, written into files, went through them all). */
    private fun wordsWork(again: Boolean): WordsDone {
        load()
        val list = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { it to t } }.distinctBy { it.first }
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        val todo = ArrayList<Triple<String, Track, Long>>()
        var wrote = 0
        var ai = aiOn()
        val redoAll = again && ai
        for ((p, t) in list) {
            if (wordsStop) return WordsDone(0, 0, wrote, false)
            val m = runCatching { AudioTools.modified(p) }.getOrNull() ?: continue
            val e = fresh(p, t, m)
            // Read before, by an older word reader (or on the device, and Read again with AI): read again. Never one the AI read.
            val before = LyricsReading.parse(e.lyr)
            if (before != null && !before.instrumental && before.by != "ai" && (before.outdated || redoAll)) { todo += Triple(p, t, m); continue }
            if (e.lyrInFile) continue   // in its file: read for good
            if (e.lyr.isNotEmpty()) {   // read before (kept in the app): into the file now, if it can take it
                if (p != playing && writeWords(p, e)) wrote++
                continue
            }
            if (e.lyrTried > 0 && now() - e.lyrTried < 30 * 86400) continue   // no words a little while ago
            todo += Triple(p, t, m)
        }
        Music.scope.launch { wordsReading = 0 to todo.size; version++ }
        var read = 0; var none = 0
        for ((i, x) in todo.withIndex()) {
            if (wordsStop) break
            val (p, t, m) = x
            Music.scope.launch { wordsNow = "${t.title} · ${t.artist.split(",")[0].trim()}" }
            val (text, instrumental) = runCatching { LyricsScan.wordsFor(t) }.getOrDefault(null to false)
            val reading: LyricsReading? = when {
                instrumental -> LyricsReading(instrumental = true, confidence = 1.0, by = "words")
                text == null -> null
                ai -> try { aiRead(t, text).also { if (aiStatus.isNotEmpty()) Music.scope.launch { aiStatus = "" } } } catch (e: HttpError) {
                    val why = aiWhy(e)
                    Music.scope.launch { aiStatus = why }
                    // A key that doesn't work, or no credits: stop asking (each song would fail the same way)
                    if (e.code == 401 || e.code == 403 || (e.code == 400 && "credit" in e.body.lowercase())) {
                        ai = false
                        Music.scope.launch { Music.toast("AI reading stopped: $why. The words are read on this ${org.omarchy.music.ui.DEVICE} instead") }
                    }
                    LyricsMood.read(text)
                } catch (e: Exception) { Music.scope.launch { aiStatus = "couldn't reach Anthropic: ${hideKey(e.message.orEmpty()).take(100)}" }; LyricsMood.read(text) }
                else -> LyricsMood.read(text)
            }
            val e = synchronized(lock) { known[p] } ?: MoodEntry(mtime = m, looked = true)
            if (reading == null && e.lyr.isNotEmpty()) {
                // Being read again, but its words can't be found now: the old reading stays.
            } else if (reading == null) {
                none++
                synchronized(lock) { known[p] = e.copy(lyrTried = now()) }
            } else {
                read++
                val got = e.copy(lyr = reading.tag(), lyrInFile = false, lyrTried = 0.0)
                synchronized(lock) { known[p] = got }
                if (p != playing && writeWords(p, got)) wrote++
            }
            val done = i + 1
            Music.scope.launch { wordsReading = done to todo.size; if (done % 10 == 0) version++ }
            if (done % 25 == 0) save()
            Thread.sleep(if (Music.paused) 30 else 150)
        }
        return WordsDone(read, none, wrote, !wordsStop)
    }

    /** A song's words' reading written into its file's MOOD_LYRICS tag, if the app may change the file; true if it did. */
    private fun writeWords(p: String, e: MoodEntry): Boolean {
        if (!canWrite(p) || running) return false   // not while songs are being heard
        val ok = runCatching { LyricsTags.writeTags(Music.ctx, p, null, mapOf(MoodTag.MOOD_LYRICS to e.lyr)) }.getOrDefault(false)
        if (ok) {
            val mt = runCatching { AudioTools.modified(p) }.getOrNull()
            synchronized(lock) { known[p] = (known[p] ?: e).copy(mtime = mt ?: e.mtime, lyrInFile = true) }
        }
        return ok
    }

    /** Why the AI reading last failed ("" when it last worked): shown under the key in Settings. */
    var aiStatus by mutableStateOf("")

    /** A failed request in plain words. */
    private fun aiWhy(e: HttpError): String {
        val msg = runCatching { JSON.parseToJsonElement(e.body)["error"]["message"].str }.getOrNull().orEmpty().let(::hideKey).take(160)
        return when {
            e.code == 401 -> "Anthropic says the key isn't valid (check it was copied whole)"
            e.code == 403 -> "the key isn't allowed to do this" + if (msg.isNotEmpty()) " ($msg)" else ""
            e.code == 400 && "credit" in e.body.lowercase() -> "your Anthropic account has no credits (add some under Billing)"
            e.code == 429 -> "too many requests at once (it tries again later)"
            else -> "Anthropic answered ${e.code}" + if (msg.isNotEmpty()) ": $msg" else ""
        }
    }

    /** Test the key (Settings): one tiny request; done gets "" if it works, else why not. Costs a fraction of a cent. */
    fun testAi(done: (String) -> Unit) {
        val key = cleanKey(Prefs.text(AI_KEY))
        if (key.isEmpty()) { done("no key saved yet"); return }
        Music.bg({
            val body = JsonObject(mapOf("model" to JsonPrimitive(AI_MODEL), "max_tokens" to JsonPrimitive(5),
                "messages" to JsonArray(listOf(JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive("Say ok")))))))
            try { httpJson("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), jsonBody(body)); "" }
            catch (e: HttpError) { aiWhy(e) }
            catch (e: Exception) { "couldn't reach Anthropic: ${hideKey(e.message.orEmpty()).take(100)}" }
        }) { r, _ -> val why = r ?: "it didn't answer"; aiStatus = why; done(why) }
    }

    /** The AI reading: the song's title, artist and words sent to Anthropic's Messages API with your key (nothing else
     *  is sent, and the key goes nowhere else and is never logged), its answer read as a [LyricsReading]: a score for
     *  every mood, what the singer means, valence and energy. Costs a little per song, on your Anthropic account. */
    private fun aiRead(t: Track, words: String): LyricsReading {
        val key = cleanKey(Prefs.text(AI_KEY))
        val ask = "Song: ${t.title} by ${t.artist}\n\nLyrics:\n" + words.take(8000)
        val body = JsonObject(mapOf(
            "model" to JsonPrimitive(AI_MODEL),
            "max_tokens" to JsonPrimitive(450),
            "system" to JsonPrimitive("You read a song's lyrics (with its title and artist) and judge the mood it carries for a " +
                "listener: what the singer means, not single words. Mind idioms (\"wash my blues away\" is relief, not sadness), " +
                "irony, a sad story told cheerfully or a happy one told sadly, and whether the singer is looking back or speaking " +
                "now. Score every one of these moods from 0 (not at all) to 1 (plainly); several can be high: " +
                MoodRules.ALL.joinToString(", ") + ". Answer with JSON only, no other text: {\"moods\": {" +
                MoodRules.ALL.joinToString(", ") { "\"$it\": 0.0" } + "}, \"valence\": -1 (gloomy) to 1 (glad), \"energy\": 0 " +
                "(calm) to 1 (charged), \"themes\": up to 3 one-word themes in lower case, \"confidence\": 0 to 1, \"meaning\": " +
                "one short line (under 15 words) on what the song is about, without quoting it}."),
            "messages" to JsonArray(listOf(JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive(ask))))),
        ))
        val res = httpJson("https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), jsonBody(body))
        val text = res["content"].list.firstOrNull { it["type"].str == "text" }["text"].str ?: error("no answer")
        val from = text.indexOf('{'); val to = text.lastIndexOf('}')
        if (from < 0 || to <= from) error("no answer")
        val j = JSON.parseToJsonElement(text.substring(from, to + 1))
        fun mood(x: String?) = x?.let { m -> MoodRules.ALL.firstOrNull { it.equals(m.trim(), true) } ?: MoodTag.pick(m) }.orEmpty()
        // Every mood's score; an answer in the old shape (one mood, a second) is taken as those two.
        var scores = (j["moods"] as? JsonObject)?.entries?.mapNotNull { (k, v) ->
            MoodRules.ALL.firstOrNull { it.equals(k.trim(), true) }?.let { m -> v.dbl?.let { m to it } }
        }?.toMap().orEmpty()
        val sure = j["confidence"].dbl?.coerceIn(0.0, 1.0)
        if (scores.isEmpty()) scores = listOfNotNull(mood(j["mood"].str).takeIf { it.isNotEmpty() }?.let { it to (sure ?: 0.6) },
            mood(j["second"].str).takeIf { it.isNotEmpty() }?.let { it to 0.6 * (sure ?: 0.6) }).toMap()
        if (scores.isEmpty() && sure == null) error("no answer")
        return LyricsReading.fromScores(scores, (j["valence"].dbl ?: 0.0).coerceIn(-1.0, 1.0), (j["energy"].dbl ?: 0.5).coerceIn(0.0, 1.0),
            j["themes"].list.mapNotNull { it.str?.lowercase()?.replace(Regex("[^\\p{L}\\p{N} -]"), "")?.trim()?.takeIf { s -> s.isNotEmpty() } }.take(3),
            "ai", sure, j["meaning"].str.orEmpty())
    }

    // ---- your own mood for a song (Tagger) ----

    /** Give songs moods of your own (null: take them off, so they're worked out again): exactly those, kept in the app,
     *  and written into the files that can be written (on the PC all of yours; on the phone the app's own downloads).
     *  The songs that sound most like them, and their artists' other songs, learn a little from it (see [learn]). */
    fun pick(tracks: List<Track>, moods: List<String>?) = pick(tracks, moods?.distinct()?.filter { it.isNotBlank() }?.ifEmpty { null }?.joinToString(";"))

    /** mood: one mood, or several joined with ";" ("Happy;Party"). */
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
                    else if (runCatching { LyricsTags.writeMood(Music.ctx, p, change) }.getOrDefault(false)) {   // only the mood changes
                        wrote++
                        now = now.copy(mtime = AudioTools.modified(p) ?: m, inFile = change, wrote = if (mood == null) "" else now.wrote)
                    }
                }
                synchronized(lock) { known[p] = now }
            }
            // Your fix teaches the songs that sound like it, and the artist's others, a little (see [learn]).
            runCatching { learn() }.onFailure { Crashes.caught(it) }
            save()
            wrote to skipped
        }) { r, _ ->
            version++
            val (wrote, skipped) = r ?: (0 to 0)
            Music.toast((if (mood == null) "Mood taken off" else if (';' in mood) "Moods: ${mood.replace(";", ", ")}" else "Mood: $mood") +
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
            // Yours (one or several) as you picked them, else every mood worked out (main first, joined with ";"), so
            // the file carries them all: your other device and other apps read the same moods, not just the main one
            val m = e.picked.ifEmpty { scored(t).take(MoodRules.MOST).joinToString(";") { it.first } }.ifEmpty { null } ?: continue
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
            val list = writable()
            Music.scope.launch { writeProgress = 0 to list.size }
            for ((p, m) in list) {
                if (p == playing) continue
                if ((done + failed) % 10 == 0) { val d = done + failed; Music.scope.launch { writeProgress = d to list.size } }
                if (runCatching { LyricsTags.writeMood(Music.ctx, p, m) }.getOrDefault(false)) {   // only the mood changes, never the album or artist
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
            if (!(e.heard || e.confirmed.isNotEmpty() || e.kept.isNotEmpty() || e.lyr.isNotEmpty())) continue   // only a guess from the genre: nothing to keep
            val j = judged(t)
            val s = j.moods
            if (s.isEmpty() && e.lyr.isEmpty()) continue
            out += MoodBackupSong(t.title, t.artist, t.album, t.duration, s.map { it.first }, if (s.isEmpty()) 0.0 else j.confidence, e.full,
                e.confirmed.isNotEmpty(), e.sound, e.lyr)
        }
        return JSON.encodeToString(ListSerializer(MoodBackupSong.serializer()), out)
    }

    /** A backup's moods, kept to be given to the songs here once they're read (after the app starts again). */
    fun restoreLater(text: String, fromSync: Boolean = false) {
        runCatching { JSON.decodeFromString(ListSerializer(MoodBackupSong.serializer()), text) }.getOrNull() ?: return
        if (fromSync) {
            // Tags & covers match: the main device leads the moods too, so it never takes the other's
            if (TagMatch.on && TagMatch.isMain) return
            snapshot()   // what the moods were, so Undo last sync can put them back
            if (TagMatch.on) mainFile.writeText("1") else mainFile.delete()
        } else mainFile.delete()
        writeAtomic(restoreFile, text)
    }

    /** Songs whose moods came from a backup or the other device rather than being heard here (and aren't yours). */
    fun fromElsewhere(): Int { load(); return synchronized(lock) { known.values.count { it.kept.isNotEmpty() && it.picked.isEmpty() } } }

    /** (blocking) Those songs heard again here, from scratch: their moods from elsewhere dropped (the MOOD the app wrote
     *  into their files is worked out again and rewritten). For moods a sync spoilt. How many. */
    fun hearAgainFromElsewhere(): Int {
        load()
        var n = 0
        synchronized(lock) {
            for ((p, e) in known.entries.toList()) if (e.kept.isNotEmpty() && e.picked.isEmpty()) {
                known[p] = e.copy(kept = emptyList(), keptSure = 0.0, heard = false, full = false, sound = null,
                    wrote = e.inFile.ifEmpty { e.wrote })
                n++
            }
        }
        if (n > 0) { save(); Music.scope.launch { version++ } }
        return n
    }

    /** The other device is the main one (Tags & covers match) and these moods came from it: its moods win. */
    private val mainFile get() = File(Music.ctx.filesDir, "moods-restore-main")
    private val beforeSync get() = File(Music.ctx.filesDir, "moods-before-sync.json")

    /** (blocking) The moods as they are, kept aside before a sync changes them. */
    fun snapshot() {
        load(); save()
        runCatching { file.takeIf { it.isFile }?.copyTo(beforeSync, overwrite = true) }
    }

    /** (blocking) Undo last sync: the moods put back as they were before it (and the ones it brought dropped). */
    fun undoSync(): Boolean {
        if (!beforeSync.isFile) return false
        synchronized(saving) {
            beforeSync.copyTo(file, overwrite = true)
            restoreFile.delete(); mainFile.delete()
            synchronized(lock) { known.clear(); artists.clear(); bias.clear(); ok.clear(); loaded = false }
        }
        load()
        Music.scope.launch { version++ }
        return true
    }

    /** (blocking) The waiting backup's moods given to the songs here that match (title and artist, length, album).
     *  A song heard here already keeps its own unless the backup's is better (heard whole, or yours). The ones that
     *  matched nothing wait for the next scan of your folders. */
    private fun applyRestored() {
        val saved = runCatching { JSON.decodeFromString(ListSerializer(MoodBackupSong.serializer()), readText(restoreFile) ?: return) }.getOrNull()
        if (saved == null) { restoreFile.delete(); return }
        val fromMain = mainFile.isFile
        load()
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { it to t } }.distinctBy { it.first }
        if (songs.isEmpty()) return   // your music isn't read yet: next time
        val byPath = songs.toMap()
        val got = MoodBackup.match(saved, songs.map { (p, t) -> MoodSongHere(p, t.title, t.artist, t.album, t.duration) })
        var n = 0
        for ((p, b) in got) {
            val t = byPath[p] ?: continue
            val e = synchronized(lock) { known[p] }
            // The other device's reading of the words wins over this one's when it's better (the AI's over the word
            // reader's): a scan with the AI on the PC doesn't have to be done again on the phone
            val theirs = LyricsReading.parse(b.lyrics)
            val mine = e?.lyr?.takeIf { it.isNotEmpty() }?.let { LyricsReading.parse(it) }
            val wordsBetter = theirs != null && (mine == null || theirs.rank > mine.rank)
            // A song's moods here are only replaced when it has none of its own yet (or the other device's are yours), or the
            // other device is the main one (Tags & covers match). A better reading of the words alone no longer replaces
            // moods heard here: that's what made a sync swap good moods for fewer.
            val notYours = e?.picked.isNullOrEmpty()
            val better = b.moods.isNotEmpty() && (notYours || b.picked) &&
                (e == null || (!e.heard && e.confirmed.isEmpty()) || (b.picked && e.picked.isEmpty()) || (fromMain && notYours))
            val words = wordsBetter   // its words' mood, read before
            if (!better && !words) continue
            val m = runCatching { AudioTools.modified(p) }.getOrNull() ?: continue
            val base = (e ?: MoodEntry()).let { if (words) it.copy(lyr = b.lyrics, lyrInFile = false) else it }
            synchronized(lock) {
                known[p] = if (!better) base.copy(mtime = if (e == null) m else base.mtime, looked = e != null && base.looked)
                    else base.copy(mtime = m, sound = b.sound ?: base.sound, heard = true, full = b.full || base.full,
                    len = t.duration, picked = if (b.picked) b.moods.first() else base.picked, kept = b.moods, keptSure = b.sure,
                    looked = base.looked && base.mtime == m)
            }
            n++
        }
        val used = got.values.toHashSet()
        val left = saved.filter { it !in used }
        if (left.isEmpty()) { restoreFile.delete(); mainFile.delete() } else writeAtomic(restoreFile, JSON.encodeToString(ListSerializer(MoodBackupSong.serializer()), left))
        if (n > 0) {
            save()
            Music.scope.launch { version++; Music.toast("Moods from your backup given to ${"%,d".format(n)} song${if (n != 1) "s" else ""}") }
        }
    }
}
