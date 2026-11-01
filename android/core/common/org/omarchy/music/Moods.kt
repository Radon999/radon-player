package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** What Moods knows of one song file: when the file last changed (to read it again if it does), its sound (null: it
 *  couldn't be heard), whether that was tried, and Last.fm's tags of it (tag to Last.fm's count, 0–100) if fetched. */
@Serializable
data class MoodEntry(val mtime: Long = 0, val sound: SoundFeatures? = null, val heard: Boolean = false,
                     val tags: Map<String, Int> = emptyMap(), val fm: Boolean = false)

@Serializable
internal data class MoodStore(val songs: Map<String, MoodEntry> = emptyMap(), val artists: Map<String, Map<String, Int>> = emptyMap())

/** Moods: every song of yours (files and downloads) gets a mood (Chill, Happy, Sad…) and up to two more, from its sound
 *  (half a minute from its middle, analysed on the device), its file's genre, and Last.fm's tags of it and its artist
 *  when there's a Last.fm key. The rules are in [MoodRules]. Each file is read once (again only when it changes), in
 *  the background, a song at a time at low priority, and kept in moods.json. A song not read yet still gets a mood from
 *  its genre when its genre says one. */
object Moods {
    /** The moods, in the order the Moods tab shows them. */
    val ALL: List<String> get() = MoodRules.ALL
    /** Bumped when results change. */
    var version by mutableStateOf(0)
    /** (done, total) while reading songs' moods, else null. */
    var reading by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    private val lock = Any()
    private val known = HashMap<String, MoodEntry>()
    private val artists = HashMap<String, Map<String, Int>>()
    @Volatile private var loaded = false
    @Volatile private var running = false
    @Volatile private var stopAsked = false
    private val file get() = File(Music.ctx.filesDir, "moods.json")

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

    private fun save() = runCatching {
        val text = synchronized(lock) { JSON.encodeToString(MoodStore.serializer(), MoodStore(known.toMap(), artists.toMap())) }
        writeAtomic(file, text)
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
            MoodRules.decide(e?.sound, genre, e?.tags.orEmpty().mapValues { it.value / 100.0 }, fromArtist.mapValues { it.value / 100.0 })
        }
    }

    /** A song's mood, null if not known yet (fast: from what's been read). */
    fun of(t: Track): String? = scored(t).firstOrNull()?.first

    /** A song's moods: its mood first, then the others. */
    fun all(t: Track): List<String> = scored(t).map { it.first }

    /** How sure the song's mood is (0–1; 0: no mood). */
    fun confidence(t: Track): Double = scored(t).firstOrNull()?.second ?: 0.0

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

    /** How many of your songs have been read (sound and tags) and how many there are. */
    fun counts(): Pair<Int, Int> {
        load()
        val paths = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNullTo(HashSet()) { pathOf(it) }
        val done = synchronized(lock) { paths.count { known[it]?.heard == true } }
        return done to paths.size
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

    /** After a scan of your folders: read the songs not read yet. On the phone only while it's charging, like the
     *  other background checks, so it never costs battery. */
    fun auto() {
        if (!AudioTools.supported || !AudioTools.canRunNow()) return
        start(auto = true)
    }

    /** Find moods (the Moods tab): read every song not read yet, now. */
    fun find() = start(auto = false)

    /** Stop reading (what's been read is kept; Find moods carries on from there). */
    fun stop() { stopAsked = true }

    val busy get() = running

    private fun start(auto: Boolean) {
        if (running) return
        val list = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { it to t } }.distinctBy { it.first }
        if (list.isEmpty()) return
        running = true; stopAsked = false
        if (!auto) reading = 0 to list.size
        Thread({
            try { work(list, auto) } catch (e: Throwable) { Crashes.caught(e) }
            finally {
                running = false
                save()
                Music.scope.launch { reading = null; version++ }
            }
        }, "moods").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    private fun work(list: List<Pair<String, Track>>, auto: Boolean) {
        load()
        val fmKey = LastFm.key.isNotEmpty()
        // What needs reading: songs new, changed since, or without Last.fm's tags now that there's a key.
        val todo = list.mapNotNull { (p, t) ->
            val m = runCatching { AudioTools.modified(p) }.getOrNull() ?: return@mapNotNull null   // gone
            val e = synchronized(lock) { known[p] }
            if (e == null || e.mtime != m || !e.heard || (fmKey && !e.fm)) Triple(p, t, m) else null
        }
        if (todo.isEmpty()) { if (!auto) Music.toast("Every song's mood is found already"); return }
        Music.scope.launch { reading = 0 to todo.size }
        var heardAny = false; var fails = 0
        for ((i, x) in todo.withIndex()) {
            if (stopAsked || (auto && !AudioTools.canRunNow())) break
            val (p, t, m) = x
            val old = synchronized(lock) { known[p] }?.takeIf { it.mtime == m }
            val sound = if (old != null && old.heard) old.sound
                else runCatching { SoundMood.features(AudioTools.moodPcm(p, t.duration, SECS, RATE)) }.getOrNull()
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
            // Nothing heard at all yet (no decoder for it, ffmpeg missing): not marked as read, so it's tried again later.
            synchronized(lock) { known[p] = MoodEntry(m, sound, heardAny || old?.heard == true, tags, fm) }
            if (!heardAny && fails >= 8) { Music.scope.launch { Music.toast("Couldn't hear your songs to find their moods") }; break }
            val done = i + 1
            Music.scope.launch { reading = done to todo.size; if (done % 10 == 0) version++ }
            if (done % 25 == 0) save()
            // Gentle: a pause between songs, longer while music plays, so playback never stutters.
            Thread.sleep(if (Music.paused) 40 else 300)
        }
    }

    /** Last.fm's tags of a song (tag to count, 0–100), the ones that say something. */
    private fun trackTags(t: Track): Map<String, Int> =
        LastFm.call("track.getTopTags", params = mapOf("artist" to t.artist.split(",")[0].trim(), "track" to t.title, "autocorrect" to 1))
            .path("toptags", "tag").list.take(15)
            .mapNotNull { x -> val name = x["name"].str?.lowercase()?.trim() ?: return@mapNotNull null; val n = x["count"].long?.toInt() ?: 0
                if (name.isEmpty() || n <= 0) null else name to n }
            .toMap()

    private const val SECS = 30      // seconds of each song heard
    private const val RATE = 22050   // samples a second they're heard at
}
