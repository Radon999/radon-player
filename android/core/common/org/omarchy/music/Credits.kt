package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import java.io.File

/** Song credits: who wrote, produced and played on a song. Looked up once for each song (by its name and lead artist, so
 *  your file and a stream of it share them) and kept in the app, so they show offline after: from Tidal when you're
 *  signed in to it, else Genius, else MusicBrainz. Look through my music looks up your songs' in the background, a song
 *  every few seconds (on the phone only while it's charging), so a person's other songs of yours can be listed. */
object Credits {
    /** A song's credits as kept: when they were looked up, where they came from, and the credits (none: none found). */
    @Serializable
    data class Saved(val time: Double, val from: String = "", val credits: List<SongCreditRules.Credit> = emptyList())

    /** Bumped when credits are found or the look-through moves on. */
    var version by mutableStateOf(0)
        private set
    /** (done, of) while looking through your music, else null. */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    /** The phone is waiting to be put on charge before it looks any further. */
    var waiting by mutableStateOf(false)
        private set

    private const val MB_UA = "RadonPlayer/4.0 ( https://github.com/Radon999/radon-player )"
    private val lock = Any()
    private var known: HashMap<String, Saved>? = null
    private val file get() = File(Music.ctx.filesDir, "credits.json")
    // Set while a look-through is wanted, so it carries on after the app is started again (it skips the songs done).
    private val going get() = File(Music.ctx.filesDir, "credits-scan.on")
    private val SERIAL = MapSerializer(String.serializer(), Saved.serializer())

    private fun key(t: Track) = songPickKey(t.title, t.artist)

    private fun all(): HashMap<String, Saved> = synchronized(lock) {
        known ?: (readText(file)?.let { runCatching { HashMap(JSON.decodeFromString(SERIAL, it)) }.getOrNull() } ?: HashMap()).also { known = it }
    }

    private fun save() {
        val text = synchronized(lock) { JSON.encodeToString(SERIAL, all()) }
        runCatching { writeAtomic(file, text) }.onFailure { Crashes.caught(it) }
    }

    private fun bump() { Music.scope.launch { version++ } }

    /** The song's credits as kept, without looking anything up (null: never looked up). It blocks the first time (reading the file). */
    fun kept(t: Track): Saved? = synchronized(lock) { all()[key(t)] }

    private fun fresh(s: Saved?) = s != null && !SongCreditRules.lookAgain(s.credits.isNotEmpty(), s.time, now())

    /** (blocking) The song's credits: the kept ones, else looked up and kept. Null when nobody could be reached (offline)
     *  and none were kept; kept ones looked up again but unreachable now are still given. */
    fun get(t: Track): Saved? {
        val had = kept(t)
        if (fresh(had)) return had
        val (got, reached) = lookUp(t)
        if (!reached) return had
        synchronized(lock) { all()[key(t)] = got }
        save(); bump()
        return got
    }

    /** (blocking) Each place asked in turn, the best first; the first with credits gives them. reached: one answered. */
    private fun lookUp(t: Track): Pair<Saved, Boolean> {
        var reached = false
        val tidal = Music.tidal
        if (tidal.connected) runCatching { tidal.credits(t) }.onSuccess { reached = true; if (it.isNotEmpty()) return Saved(now(), "Tidal", it) to true }
        runCatching { genius(t) }.onSuccess { reached = true; if (it.isNotEmpty()) return Saved(now(), "Genius", it) to true }
        runCatching { musicBrainz(t) }.onSuccess { reached = true; if (it.isNotEmpty()) return Saved(now(), "MusicBrainz", it) to true }
        return Saved(now()) to reached
    }

    // ---- Genius ----
    private fun genius(t: Track): List<SongCreditRules.Credit> {
        // The lyrics found for it may know the song on Genius already: no search needed
        val id = readText(File(File(Music.ctx.cacheDir, "lyrics"), "${safeName(t.id)}.json"))
            ?.let { runCatching { JSON.decodeFromString(Lyrics.Got.serializer(), it).genius_id }.getOrNull() }
            ?: Lyrics.geniusSong(t.artist, t.title)["id"].long ?: return emptyList()
        return SongCreditRules.fromGenius(httpJson("https://genius.com/api/songs/$id?text_format=plain").path("response", "song"))
    }

    // ---- MusicBrainz: one request a second at most, as it asks, with the app's name ----
    private val mbLock = Any()
    private var mbLast = 0L

    private fun mb(path: String): JsonElement? {
        synchronized(mbLock) {
            val wait = mbLast + 1100 - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            mbLast = System.currentTimeMillis()
        }
        return try {
            httpJson("https://musicbrainz.org/ws/2/$path${if ('?' in path) "&" else "?"}fmt=json", mapOf("User-Agent" to MB_UA))
        } catch (e: HttpError) { if (e.code == 404) null else throw e }
    }

    /** The recording that is this song: the one with its ISRC, else the best of a search by its name and lead artist. */
    private fun recordingId(t: Track): String? {
        t.isrc?.takeIf { it.isNotBlank() }?.let { isrc -> mb("isrc/${enc(isrc)}")["recordings"].list.firstOrNull()["id"].str?.let { return it } }
        val lead = splitArtists(t.artist).firstOrNull() ?: return null
        fun q(s: String) = s.replace(Regex("[\"\\\\]"), " ").trim()
        val want = cleanTitle(t.title)
        val hits = mb("recording?limit=10&query=${enc("recording:\"${q(t.title)}\" AND artist:\"${q(lead)}\"")}")["recordings"].list
        return hits.filter { r ->
            similar(want, cleanTitle(r["title"].str ?: "")) >= 0.8 &&
                r["artist-credit"].list.any { a -> similar(norm(lead), norm(a["name"].str)) >= 0.8 } &&
                (t.duration <= 0 || r["length"].long == null || kotlin.math.abs(r["length"].long!! / 1000.0 - t.duration) <= 10)
        }.maxByOrNull { it["score"].long ?: 0 }?.get("id").str
    }

    private fun musicBrainz(t: Track): List<SongCreditRules.Credit> {
        val id = recordingId(t) ?: return emptyList()
        return SongCreditRules.fromMusicBrainz(mb("recording/$id?inc=artist-credits+artist-rels+work-rels+work-level-rels"))
    }

    // ---- a person's songs ----
    /** (blocking) Your songs person worked on (under their name, or in the credits kept), each with how. */
    fun workedOn(person: String): List<Pair<Track, List<String>>> {
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList())
        val map = synchronized(lock) { HashMap(all()) }
        return SongCreditRules.workedOn(person, songs, { it.title to it.artist }, { map[key(it)]?.credits })
    }

    /** (blocking) How many of your songs have had their credits looked up, and how many songs you have. */
    fun counted(): Pair<Int, Int> {
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList()).distinctBy { key(it) }
        val map = synchronized(lock) { all().keys.toHashSet() }
        return songs.count { key(it) in map } to songs.size
    }

    // ---- Look through my music ----
    @Volatile private var stopAsked = false
    private var thread: Thread? = null

    /** Look through your songs for their credits, in the background, a song every few seconds. */
    fun scan() {
        runCatching { going.writeText("1") }
        start()
    }

    fun stop() {
        stopAsked = true
        going.delete()
    }

    /** At the start: a look-through not finished carries on (from the songs not done yet). */
    fun resume() { if (going.exists()) start() }

    @Synchronized private fun start() {
        if (thread?.isAlive == true) return
        stopAsked = false
        thread = Thread({
            runCatching { work() }.onFailure { Crashes.caught(it) }
            save()
            Music.scope.launch { progress = null; waiting = false; version++ }
        }, "credits").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    private fun work() {
        val todo = runCatching { Music.mySongs() }.getOrDefault(emptyList()).distinctBy { key(it) }.filter { !fresh(kept(it)) }
        if (todo.isEmpty()) {
            going.delete()
            Music.toast("Every song's credits are looked up already")
            return
        }
        Music.scope.launch { progress = 0 to todo.size }
        var unreached = 0
        for ((i, t) in todo.withIndex()) {
            // The phone looks only while it's charging: it waits for that (a minute at a time), the PC goes straight on
            while (!AudioTools.canRunNow()) {
                Music.scope.launch { waiting = true }
                repeat(60) { if (stopAsked) return; Thread.sleep(1000) }
            }
            Music.scope.launch { waiting = false }
            if (stopAsked) return
            val (got, reached) = lookUp(t)
            if (reached) {
                unreached = 0
                synchronized(lock) { all()[key(t)] = got }
            } else if (++unreached >= 5) {   // offline: it carries on from here next time the app starts
                Music.toast("Song credits: stopped looking, the services can't be reached. It carries on later")
                return
            }
            if ((i + 1) % 20 == 0) save()
            Music.scope.launch { progress = (i + 1) to todo.size; version++ }
            repeat(3) { if (stopAsked) return; Thread.sleep(1000) }   // gently: a song every few seconds
        }
        going.delete()
        Music.toast("Song credits looked up for %,d songs".format(todo.size))
    }
}
