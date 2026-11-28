package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** What auto-sync did with one song: [state] (see [LyricsAutoSync]'s), why it couldn't when it couldn't. */
@Serializable
data class AutoSyncSong(val state: String = "", val title: String = "", val artist: String = "", val time: Double = 0.0, val why: String = "")

/** Auto-sync (Settings › Scanner › Lyrics, after the lyrics scan), two jobs:
 *  - [TIMING]: your songs that have their words but no timing get timed lyrics. Each is first looked up once more
 *    where timed lyrics come from (LRCLIB, Musixmatch through Tidal); the ones nobody has timed (your unreleased songs)
 *    are listened to: the app hears the words being sung and when ([LyricHearing], run on this device, nothing sent
 *    anywhere) and lines them up with the song's own lyrics ([LyricSyncRules.timeLines]).
 *  - [WORDS]: the songs the scan found no lyrics for are listened to, and the words heard are written down as timed
 *    lyrics ([LyricSyncRules.heardLines]): a first go, to correct with Edit in the Lyrics tab. English only (the model's).
 *  The lyrics go where the lyrics scan puts lyrics: into the song's file (its lyrics tag, so Sync takes them to your
 *  other device) where the app may change it, else kept in the app. What the app made itself is marked ([MARK],
 *  [MARK_HEARD], an LRC "re:" line players ignore): the Lyrics tab says so, with Time again / Listen again and Remove.
 *  Each song is done once: the next start carries on with the ones left (Stop keeps what's done); the ones it couldn't
 *  do are tried again only when asked. Runs on whichever device it's started on, for that device's songs. */
object LyricsAutoSync {
    const val TIMING = "timing"
    const val WORDS = "words"

    const val TIMED = "timed"       // timed by listening
    const val FOUND = "found"       // timed lyrics found online
    const val HEARD = "heard"       // its words written down from listening
    const val FAILED = "failed"     // couldn't hear enough of its words
    const val REMOVED = "removed"   // you removed what it made: not done again by itself
    /** In the lyrics the app timed itself (an LRC "re:" line: the program that made it; players show nothing of it). */
    const val MARK = "[re:Radon Player auto-sync]"
    /** In the lyrics the app wrote down from listening. */
    const val MARK_HEARD = "[re:Radon Player heard]"

    /** Made by the app (timed, or heard). */
    fun isAuto(lrc: String?): Boolean = lrc != null && (lrc.contains(MARK) || lrc.contains(MARK_HEARD))
    fun isHeard(lrc: String?): Boolean = lrc?.contains(MARK_HEARD) == true

    var version by mutableStateOf(0)
        private set
    /** (done, of) while it runs, else null; which job; the song being done; what it's doing (fetching the model…). */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var kind by mutableStateOf("")
        private set
    var nowSong by mutableStateOf("")
        private set
    var doing by mutableStateOf("")
        private set

    @Volatile private var running = false
    @Volatile private var stopAsked = false
    val busy get() = running
    fun stop() { stopAsked = true }
    val modelReady: Boolean get() = LyricHearing.ready(Music.ctx)

    private val lock = Any()
    private val songs = HashMap<String, AutoSyncSong>()
    @Volatile private var loaded = false
    private val file get() = File(Music.ctx.filesDir, "lyrics-autosync.json")
    private val ser = MapSerializer(String.serializer(), AutoSyncSong.serializer())

    private fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            runCatching { readText(file)?.let { JSON.decodeFromString(ser, it) } }.getOrNull()?.let { songs.putAll(it) }
            loaded = true
        }
    }

    private fun save() = runCatching { writeAtomic(file, synchronized(lock) { JSON.encodeToString(ser, songs.toMap()) }) }

    fun stateOf(t: Track): AutoSyncSong? { load(); return synchronized(lock) { songs[LyricsScan.keyOf(t)] } }
    private fun set(t: Track, state: String, why: String = "") {
        load(); synchronized(lock) { songs[LyricsScan.keyOf(t)] = AutoSyncSong(state, t.title, t.artist, now(), why) }
    }

    /** The songs it has done (timed, found online, heard), the newest first. */
    fun done(): List<AutoSyncSong> {
        load()
        return synchronized(lock) { songs.values.filter { it.state == TIMED || it.state == FOUND || it.state == HEARD } }.sortedByDescending { it.time }
    }

    /** (blocking) The songs it could time: words, no timing, not ones whose timing you removed. */
    fun waiting(): List<Track> { load(); return LyricsScan.untimed().map { it.first }.filter { stateOf(it)?.state != REMOVED } }

    /** (blocking) The songs it could write the words of: no lyrics found, not ones whose heard words you removed. */
    fun wordless(): List<Track> { load(); return LyricsScan.unheard().filter { stateOf(it)?.state != REMOVED } }

    /** Do every song waiting for [job] ([TIMING] or [WORDS]; see the object's note); [again]: the ones it couldn't
     *  before too. */
    fun start(job: String, again: Boolean = false) {
        if (running || BatterySaver.holdsScan("listening to your songs")) return
        running = true; stopAsked = false
        progress = 0 to 0; nowSong = ""; doing = ""; kind = job
        Thread({
            var made = 0; var found = 0; var failed = 0
            var problem = ""
            try {
                if (!modelReady) {
                    Music.scope.launch { doing = "Getting the listening model (${LyricHearing.SIZE}, once)…" }
                    SpeechHear.get({ stopAsked }) { p -> Music.scope.launch { doing = p } }
                }
                Music.scope.launch { doing = "" }
                val list = (if (job == WORDS) wordless() else waiting()).filter { again || stateOf(it)?.state != FAILED }
                Music.scope.launch { progress = 0 to list.size }
                for ((i, t) in list.withIndex()) {
                    if (stopAsked) break
                    Music.scope.launch { progress = i to list.size; nowSong = "${t.title} · ${t.artist.split(",")[0].trim()}" }
                    when (if (job == WORDS) hearOne(t) else timeOne(t)) { TIMED, HEARD -> made++; FOUND -> found++; FAILED -> failed++ }
                    if ((i + 1) % 10 == 0) save()
                }
            } catch (e: Throwable) {
                problem = e.message ?: e.toString()
                if (!stopAsked) Crashes.caught(e, "Auto-sync stopped: $problem")
            } finally {
                runCatching { SpeechHear.release() }
                running = false
                save()
                Music.scope.launch {
                    progress = null; nowSong = ""; doing = ""; version++
                    Music.toast(when {
                        stopAsked && made + found == 0 -> "Auto-sync stopped"
                        problem.isNotEmpty() && made + found == 0 -> "Auto-sync couldn't go on: $problem"
                        made + found + failed == 0 -> "No songs waiting"
                        job == WORDS -> "Lyrics heard for %,d song%s".format(made, if (made != 1) "s" else "") +
                            if (failed > 0) ", %,d with too little singing to hear".format(failed) else ""
                        else -> "Timed lyrics: %,d timed by listening, %,d found online".format(made, found) +
                            if (failed > 0) ", %,d couldn't be timed".format(failed) else ""
                    })
                }
            }
        }, "lyrics-autosync").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; this.start() }
    }

    /** (blocking) One song with words: looked up once more, else listened to and timed. What happened ([TIMED],
     *  [FOUND], [FAILED], or "" when Stop came in the middle of it: left waiting). */
    private fun timeOne(t: Track): String {
        val have = LyricsScan.here(t)
        val words = have?.let { LyricsTags.words(it) } ?: return FAILED.also { set(t, it, "no words to time") }
        // Words you typed or changed stay yours: somebody else's timed lyrics are taken only when they say the same,
        // else your own words are timed by listening (before, the site's wording replaced what you wrote).
        fun bare(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        val yours = have?.plain_from == "you" || have?.synced_from == "you"
        // Timed lyrics somebody has (a released song): exact, and quick
        val online = runCatching { Lyrics.timed(Music.ctx, t) }.getOrNull()?.takeIf { Lyrics.parseLrc(it).isNotEmpty() }
        if (online != null && (!yours || bare(Lyrics.parseLrc(online).joinToString("") { it.second }) == bare(words))) {
            return if (LyricsScan.placeTimed(t, words, online, "LRCLIB or Musixmatch")) FOUND.also { set(t, it) }
                else FAILED.also { set(t, it, "couldn't be saved") }
        }
        val (lrc, why) = timed(t, words)
        if (lrc == null) return if (stopAsked) "" else FAILED.also { set(t, it, why) }
        return if (LyricsScan.placeTimed(t, words, lrc, "timed by Radon")) TIMED.also { set(t, it) }
            else FAILED.also { set(t, it, "couldn't be saved") }
    }

    /** (blocking) One song with no lyrics: listened to and its words written down. [HEARD], [FAILED], or "" (stopped). */
    private fun hearOne(t: Track): String {
        val (lrc, why) = heard(t)
        if (lrc == null) return if (stopAsked) "" else FAILED.also { set(t, it, why) }
        val words = Lyrics.parseLrc(lrc).joinToString("\n") { it.second }
        return if (LyricsScan.placeTimed(t, words, lrc, "heard by Radon")) HEARD.also { set(t, it) }
            else FAILED.also { set(t, it, "couldn't be saved") }
    }

    /** (blocking) The song listened to: (the words heard, as written, each with its time; its length in seconds), or
     *  (null, why not). */
    private fun listen(t: Track): Pair<Pair<List<LyricSyncRules.Heard>, Double>?, String> {
        val path = LyricsScan.pathOf(t) ?: return null to "it's streamed: no file to listen to"
        val pcm = runCatching { AudioTools.songPcm(path, LyricHearing.RATE, LyricHearing.MAX_SECS) { stopAsked } }.getOrNull()
            ?: return null to (if (stopAsked) "stopped" else "couldn't be listened to (unreadable, or over 15 minutes)")
        if (pcm.rate != LyricHearing.RATE) return null to "its sound is too low a quality to listen to"
        val rate = pcm.rate
        val n = pcm.samples.size
        val secs = n / rate.toDouble()
        val heard = ArrayList<LyricSyncRules.Heard>()
        val win = LyricSyncRules.WINDOW_SECS * rate
        val hop = (LyricSyncRules.WINDOW_SECS - LyricSyncRules.OVERLAP_SECS) * rate
        var st = 0
        while (st < n) {
            if (stopAsked) return null to "stopped"
            val end = minOf(n, st + win)
            val (tokens, times) = SpeechHear.hear(pcm.samples.copyOfRange(st, end))
            val at = st / rate.toDouble()
            heard += LyricSyncRules.keep(LyricSyncRules.spoken(tokens, times, at), at, secs)
            if (end >= n) break
            st += hop
        }
        return (heard to secs) to ""
    }

    /** (blocking) The song's words timed by listening: (the LRC, "") or (null, why not). */
    private fun timed(t: Track, words: String): Pair<String?, String> {
        val (got, why) = listen(t)
        val (spoken, secs) = got ?: return null to why
        val heard = LyricSyncRules.words(spoken)
        val lines = LyricSyncRules.timeLines(words, heard, secs) ?: return null to "too few of its words could be heard (${heard.size} heard)"
        return (MARK + "\n" + LyricSyncRules.lrc(lines)) to ""
    }

    /** (blocking) The song's words written down from listening: (the LRC, "") or (null, why not). */
    private fun heard(t: Track): Pair<String?, String> {
        val (got, why) = listen(t)
        val (spoken, secs) = got ?: return null to why
        val lines = LyricSyncRules.heardLines(spoken, secs) ?: return null to "too little singing heard (${spoken.size} words): an instrumental, or not in English?"
        return (MARK_HEARD + "\n" + LyricSyncRules.lrc(lines)) to ""
    }

    /** Do one song again by listening (the Lyrics tab's Time again / Listen again), whatever it has now: lyrics the
     *  app heard are heard again, others timed again. Needs the model here. */
    fun again(t: Track, done: (Boolean) -> Unit = {}) {
        if (running) { Music.toast("Auto-sync is running: try again when it has finished"); return }
        if (!modelReady) { Music.toast("Start auto-sync once first (Settings › Scanner › Lyrics): it fetches what it listens with"); return }
        Music.bg({
            val now = LyricsScan.here(t)
            val result = try {
                if (now == null || isHeard(now.synced)) hearOne(t)
                else { val words = LyricsTags.words(now) ?: return@bg false; val (lrc, why) = timed(t, words)
                    if (lrc == null) FAILED.also { set(t, it, why) }
                    else if (LyricsScan.placeTimed(t, words, lrc, "timed by Radon")) TIMED.also { set(t, it) } else FAILED.also { set(t, it, "couldn't be saved") }
                }
            } finally { SpeechHear.release() }
            save()
            result == TIMED || result == HEARD
        }) { ok, _ ->
            version++
            Music.toast(if (ok == true) "Done “${t.title}” again" else "Couldn't do “${t.title}” again: ${stateOf(t)?.why ?: "its words weren't heard"}")
            done(ok == true)
        }
    }

    /** Take away what the app made (the Lyrics tab's Remove): timing it added (the words stay), or the words it heard
     *  (the song has no lyrics again); auto-sync leaves the song alone after. */
    fun remove(t: Track, done: () -> Unit = {}) {
        Music.bg({
            val heardOnes = isHeard(LyricsScan.here(t)?.synced)
            (if (heardOnes) LyricsScan.dropLyrics(t) else LyricsScan.dropTiming(t)).also { if (it) { set(t, REMOVED); save() } }
        }) { ok, _ ->
            version++
            Music.toast(if (ok == true) "Removed from “${t.title}”" else "Couldn't remove it")
            done()
        }
    }
}
