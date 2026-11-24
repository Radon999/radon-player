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

/** Auto-sync (Settings › Scanner › Lyrics, after the lyrics scan): your songs that have their words but no timing get
 *  timed lyrics. Each is first looked up once more where timed lyrics come from (LRCLIB, Musixmatch through Tidal), and
 *  the ones nobody has timed (your unreleased songs) are listened to: the app hears the words being sung and when
 *  ([LyricHearing], run on this device, nothing sent anywhere) and lines them up with the song's own lyrics
 *  ([LyricSyncRules]). The timed lyrics go where the lyrics scan puts lyrics: into the song's file (its lyrics tag, so
 *  Sync takes them to your other device) where the app may change it, else kept in the app. Lyrics it timed itself are
 *  marked ([MARK], an LRC "re:" line players ignore): the Lyrics tab says so, with Time again and Remove timing. Runs
 *  on whichever device it's started on; Stop keeps what's done, and the next start carries on. */
object LyricsAutoSync {
    const val TIMED = "timed"       // timed by listening
    const val FOUND = "found"       // timed lyrics found online
    const val FAILED = "failed"     // couldn't hear enough of its words to time them
    const val REMOVED = "removed"   // you removed the timing: not timed again by itself
    /** In the lyrics the app timed itself (an LRC "re:" line: the program that made it; players show nothing of it). */
    const val MARK = "[re:Radon Player auto-sync]"

    fun isAuto(lrc: String?): Boolean = lrc?.contains(MARK) == true

    var version by mutableStateOf(0)
        private set
    /** (done, of) while it runs, else null; the song being timed; what it's doing (fetching the model…). */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
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

    /** The songs auto-sync could time: words, no timing, not ones whose timing you removed. With whether it tried them
     *  before and couldn't. */
    fun waiting(): List<Pair<Track, Lyrics.Got>> {
        load()
        return LyricsScan.untimed().filter { stateOf(it.first)?.state != REMOVED }
    }

    /** Time every song waiting (see the object's note); [again]: the ones it couldn't before too. */
    fun start(again: Boolean = false) {
        if (running) return
        running = true; stopAsked = false
        progress = 0 to 0; nowSong = ""; doing = ""
        Thread({
            var timed = 0; var found = 0; var failed = 0
            var problem = ""
            try {
                if (!modelReady) {
                    Music.scope.launch { doing = "Getting the listening model (${LyricHearing.SIZE}, once)…" }
                    SpeechHear.get { p -> Music.scope.launch { doing = p } }
                }
                Music.scope.launch { doing = "" }
                val list = waiting().filter { again || stateOf(it.first)?.state != FAILED }
                Music.scope.launch { progress = 0 to list.size }
                for ((i, x) in list.withIndex()) {
                    if (stopAsked) break
                    val (t, got) = x
                    Music.scope.launch { progress = i to list.size; nowSong = "${t.title} · ${t.artist.split(",")[0].trim()}" }
                    when (one(t, got)) { TIMED -> timed++; FOUND -> found++; FAILED -> failed++ }
                    if ((i + 1) % 10 == 0) save()
                }
            } catch (e: Throwable) {
                problem = e.message ?: e.toString()
                Crashes.caught(e, "Auto-sync stopped: $problem")
            } finally {
                runCatching { SpeechHear.release() }
                running = false
                save()
                Music.scope.launch {
                    progress = null; nowSong = ""; doing = ""; version++
                    Music.toast(when {
                        problem.isNotEmpty() && timed + found == 0 -> "Auto-sync couldn't go on: $problem"
                        timed + found + failed == 0 -> "No songs waiting for timed lyrics"
                        else -> "Timed lyrics: %,d timed by listening, %,d found online".format(timed, found) +
                            if (failed > 0) ", %,d couldn't be timed".format(failed) else ""
                    })
                }
            }
        }, "lyrics-autosync").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; this.start() }
    }

    /** (blocking) One song: looked up once more, else listened to. What happened ([TIMED], [FOUND], [FAILED]). */
    private fun one(t: Track, got: Lyrics.Got): String {
        val words = LyricsTags.words(got) ?: return FAILED.also { set(t, it, "no words to time") }
        // Timed lyrics somebody has (a released song): exact, and quick
        val online = runCatching { Lyrics.timed(Music.ctx, t) }.getOrNull()?.takeIf { Lyrics.parseLrc(it).isNotEmpty() }
        if (online != null) {
            return if (LyricsScan.placeTimed(t, words, online, "LRCLIB or Musixmatch")) FOUND.also { set(t, it) }
                else FAILED.also { set(t, it, "couldn't be saved") }
        }
        val (lrc, why) = listen(t, words)
        if (lrc == null) return FAILED.also { set(t, it, why) }
        return if (LyricsScan.placeTimed(t, words, lrc, "timed by Radon")) TIMED.also { set(t, it) }
            else FAILED.also { set(t, it, "couldn't be saved") }
    }

    /** (blocking) The song listened to and its words timed: (the LRC, "") or (null, why not). */
    private fun listen(t: Track, words: String): Pair<String?, String> {
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
            heard += LyricSyncRules.keep(LyricSyncRules.words(tokens, times, at), at, secs)
            if (end >= n) break
            st += hop
        }
        val lines = LyricSyncRules.timeLines(words, heard, secs) ?: return null to "too few of its words could be heard (${heard.size} heard)"
        return (MARK + "\n" + LyricSyncRules.lrc(lines)) to ""
    }

    /** Time one song again by listening (the Lyrics tab's Time again), whatever was there. Needs the model here. */
    fun again(t: Track, done: (Boolean) -> Unit = {}) {
        if (running) { Music.toast("Auto-sync is running: it's done when that's finished"); return }
        if (!modelReady) { Music.toast("Start auto-sync once first (Settings › Scanner › Lyrics): it fetches what it listens with"); return }
        Music.bg({
            val words = LyricsScan.here(t)?.let { LyricsTags.words(it) } ?: return@bg false
            val (lrc, why) = try { listen(t, words) } finally { SpeechHear.release() }
            if (lrc == null) { set(t, FAILED, why); save(); return@bg false }
            val ok = LyricsScan.placeTimed(t, words, lrc, "timed by Radon")
            if (ok) { set(t, TIMED); save() }
            ok
        }) { ok, _ ->
            version++
            Music.toast(if (ok == true) "Timed “${t.title}” again" else "Couldn't time “${t.title}”: ${stateOf(t)?.why ?: "its words weren't heard"}")
            done(ok == true)
        }
    }

    /** Take the timing the app made away again (the Lyrics tab's Remove timing): only the words stay, and auto-sync
     *  leaves the song alone after. */
    fun remove(t: Track, done: () -> Unit = {}) {
        Music.bg({ LyricsScan.dropTiming(t).also { if (it) { set(t, REMOVED); save() } } }) { ok, _ ->
            version++
            Music.toast(if (ok == true) "Timing removed from “${t.title}”" else "Couldn't remove the timing")
            done()
        }
    }
}
