package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** What the lyrics scanner knows of one song: [state] (see [LyricsScan]'s states) and the song itself, to list it again. */
@Serializable
data class LyricsSong(val state: String = "", val title: String = "", val artist: String = "", val time: Double = 0.0)

/** The lyrics scanner (Settings › Scanner, and the Moods tab): goes through your songs on this device (your files and the
 *  app's downloads), finds each one's lyrics where the Lyrics tab finds them (the file's own, the ones kept here, Genius,
 *  Musixmatch through Tidal, LRCLIB) and writes them into the song's file (its LYRICS / USLT tag, timed lines when there
 *  are some) where the app may change the file: on the PC all of yours, on the phone its own downloads. Songs it can't
 *  write keep their lyrics in the app (offline too). Streamed songs aren't scanned (no file to write into): theirs are
 *  found when they're played and kept in the app too. Songs with lyrics in their file already are skipped. The songs it
 *  found nothing for are listed afterwards to deal with one by one (Settings › Scanner › Lyrics › Show…): leave it alone
 *  (remembered), it's an instrumental, type the lyrics in, search the web, search again. */
object LyricsScan {
    const val FILE = "file"                 // its lyrics are in its file
    const val APP = "app"                   // kept in the app (a file it can't change, or a song that's streamed)
    const val NONE = "none"                 // none found
    const val LEFT = "left"                 // you said leave it alone
    const val INSTRUMENTAL = "instrumental" // no words to find

    /** Bumped when something changes, for the screens to look again. */
    var version by mutableStateOf(0)
        private set
    /** (done, of) while scanning, else null; the song being looked for. */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var nowSong by mutableStateOf("")
        private set
    /** How it stands (counted after each change, in the background). */
    var counts by mutableStateOf<Counts?>(null)
        private set

    @Volatile private var running = false
    @Volatile private var stopAsked = false
    val busy get() = running
    fun stop() { stopAsked = true }

    private val lock = Any()
    private val songs = HashMap<String, LyricsSong>()
    @Volatile private var loaded = false
    private val file get() = File(Music.ctx.filesDir, "lyrics-scan.json")
    private val ser = MapSerializer(String.serializer(), LyricsSong.serializer())

    private fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            runCatching { readText(file)?.let { JSON.decodeFromString(ser, it) } }.getOrNull()?.let { songs.putAll(it) }
            loaded = true
        }
    }

    private val saving = Any()
    private fun save() = runCatching {
        synchronized(saving) { writeAtomic(file, synchronized(lock) { JSON.encodeToString(ser, songs.toMap()) }) }
    }

    /** A song's file (its own, or its download's), or null when it's streamed. */
    fun pathOf(t: Track): String? = t.file?.takeIf { it.isNotEmpty() }
        ?: runCatching { Music.lib.items[t.id]?.file }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** How the scanner knows a song: its file, else the song (title and lead artist). */
    fun keyOf(t: Track): String = pathOf(t) ?: ("song:" + songPickKey(t.title, t.artist))

    fun stateOf(t: Track): String { load(); return synchronized(lock) { songs[keyOf(t)]?.state }.orEmpty() }

    private fun set(t: Track, state: String) {
        load()
        synchronized(lock) { songs[keyOf(t)] = LyricsSong(state, t.title, t.artist, now()) }
    }

    // ---- lyrics kept in the app ----

    private fun keptFile(ctx: Ctx, t: Track): File {
        val k = keyOf(t)
        return File(File(ctx.filesDir, "lyrics-kept").apply { mkdirs() }, safeName(k).takeLast(60) + "-" + k.hashCode().toUInt().toString(16) + ".json")
    }

    /** The lyrics kept in the app for a song (found by the scanner, or typed in by you), or null. */
    fun kept(ctx: Ctx, t: Track): Lyrics.Got? = runCatching {
        readText(keptFile(ctx, t))?.let { JSON.decodeFromString(Lyrics.Got.serializer(), it) }?.takeIf { it.has }
    }.getOrNull()

    private fun keep(t: Track, g: Lyrics.Got) = runCatching { writeAtomic(keptFile(Music.ctx, t), JSON.encodeToString(Lyrics.Got.serializer(), g.copy(time = now()))) }
    private fun unkeep(t: Track) { runCatching { keptFile(Music.ctx, t).delete() } }

    /** Lyrics found online for a streamed song (no file of yours, no download) kept in the app, offline and over a
     *  restart, as the scanner keeps the ones it can't write into a file. Ones you typed in aren't replaced; timing kept
     *  before stays when the new ones have only the words. */
    fun keepStreamed(t: Track, g: Lyrics.Got) {
        if (!g.has || pathOf(t) != null) return
        val k = kept(Music.ctx, t)
        if (k?.plain_from == "you" || k?.synced_from == "you") return
        if (k != null && k.plain == g.plain && k.instrumental == g.instrumental && (g.synced == null || g.synced == k.synced)) return   // kept already
        keep(t, if (g.synced == null && k?.synced != null) g.copy(synced = k.synced, synced_from = k.synced_from) else g)
    }

    // ---- lyrics waiting for their song's file to be free ----
    // The song playing (or next) isn't written under the player: its new lyrics (found, timed, edited, removed) are
    // kept in the app and noted here, shown in its place by the Lyrics tab, and put into the file once the song is free.

    private val heldLock = Any()
    private var heldMap: MutableMap<String, Track>? = null
    private val heldFile get() = File(Music.ctx.filesDir, "lyrics-held.json")
    private val heldSer = MapSerializer(String.serializer(), Track.serializer())
    private fun held(): MutableMap<String, Track> = heldMap ?: (runCatching { readText(heldFile)?.let { JSON.decodeFromString(heldSer, it) } }
        .getOrNull()?.toMutableMap() ?: mutableMapOf()).also { heldMap = it }
    private fun saveHeld() { val m = held(); runCatching { if (m.isEmpty()) heldFile.delete() else writeAtomic(heldFile, JSON.encodeToString(heldSer, m)) } }
    private fun hold(t: Track) = synchronized(heldLock) { held()[keyOf(t)] = t; saveHeld() }

    /** Whether a song's lyrics in the app are newer than its file's (waiting for the song to finish playing). */
    fun isHeld(t: Track): Boolean = synchronized(heldLock) { keyOf(t) in held() }

    /** (blocking, quick when nothing waits) The lyrics that waited for their songs to finish playing put into their
     *  files, each once its song is free. Asked when a song starts. */
    fun writeHeld() {
        val wrote = synchronized(heldLock) {
            val m = held(); if (m.isEmpty()) return
            var any = false
            for ((k, t) in m.toList()) {
                val path = pathOf(t)
                if (path != null && runCatching { Music.inUse(path) }.getOrDefault(false)) continue
                val g = kept(Music.ctx, t)
                m.remove(k)
                if (g == null || path == null || !canWrite(path)) continue   // stays as it's kept in the app
                val text = LyricsTags.forFile(g) ?: ""   // "": they were removed
                if (runCatching { LyricsTags.writeTags(Music.ctx, path, text, emptyMap()) }.getOrDefault(false)) {
                    unkeep(t); set(t, if (text.isEmpty()) NONE else FILE); any = true
                }
            }
            saveHeld(); any
        }
        if (wrote) { save(); Music.scope.launch { version++ } }
    }

    /** (blocking) Lyrics put where they go: into the song's file where the app may change it (at once, or once the
     *  song is free: kept and shown meanwhile), else kept in the app. A [g] with no words ("" plain) takes them away.
     *  Its state set (FILE, APP, NONE); whether they went into the file (or will). */
    private fun putLyrics(t: Track, path: String?, g: Lyrics.Got, playing: String? = null): Boolean {
        val text = LyricsTags.forFile(g) ?: ""
        val writable = path != null && canWrite(path)
        val busy = path != null && (path == playing || runCatching { Music.inUse(path) }.getOrDefault(false))
        val wrote = writable && !busy && runCatching { LyricsTags.writeTags(Music.ctx, path!!, text, emptyMap()) }.getOrDefault(false)
        val gone = text.isEmpty()
        when {
            wrote -> unkeep(t)
            writable && busy -> { keep(t, g); hold(t) }
            gone -> unkeep(t)
            else -> keep(t, g)
        }
        set(t, if (gone) NONE else if (wrote) FILE else APP)
        return wrote || (writable && busy)
    }

    /** Whether the app may change the file: on the PC any of yours; on the phone only its own downloads. */
    private fun canWrite(path: String) = runCatching { FileTags.canWrite(path) }.getOrDefault(false)

    /** The lyrics in the song's file itself, or null (read straight from the file, any kind the app can open). */
    private fun inFile(path: String): String? = runCatching {
        Music.ctx.openIn(path)?.buffered()?.use { MoodTag.readTags(it, setOf(MoodTag.LYRICS)) }?.get(MoodTag.LYRICS)
    }.getOrNull()

    /** Whether the [wordsFor] just made on this thread had no words only because no site answered. */
    val wordsUnreached: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    /** (blocking) A song's words for reading its mood: (the words, or null; whether it's an instrumental). */
    fun wordsFor(t: Track): Pair<String?, Boolean> {
        if (stateOf(t) == INSTRUMENTAL) return null to true
        val g = runCatching { Lyrics.get(Music.ctx, t) }.getOrNull()
        wordsUnreached.set(g == null && Lyrics.unreachedJustNow())
        val w = g?.let { LyricsTags.words(it) }
        return w to (w == null && g?.instrumental == true)
    }

    // ---- the scan ----

    /** The songs it goes through: yours on this device (your files and the app's downloads), each with its file, once.
     *  Streamed songs aren't: there's no file to put lyrics into, and they sat in "still to find" for ever. Theirs are
     *  found as they're played (the Lyrics tab) and kept in the app ([keepStreamed]). */
    private fun allSongs(): List<Pair<Track, String>> =
        runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { t to it } }.distinctBy { it.second }

    /** How it stands: songs with lyrics in their file, kept in the app, none found, left alone, instrumentals, of how many. */
    data class Counts(val inFiles: Int, val inApp: Int, val none: Int, val left: Int, val instrumental: Int, val total: Int) {
        /** Songs still without lyrics (not looked for yet, or none found), not counting the ones left alone. */
        val toFind get() = maxOf(0, total - inFiles - inApp - left - instrumental)
    }

    /** The files already looked at for lyrics of their own this run (see [recount]), so each is read once. */
    private val looked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** (blocking) Songs whose file has lyrics already, though the scanner never put them there (the files were copied
     *  over from your other device with their lyrics, or another app wrote them): counted as in their file, and kept so,
     *  with nothing scanned or looked up. Before, they counted as still to find, and Lyrics asked for a scan. Each file
     *  is read once a run; one the scanner marked already is left as it is. How many were found. */
    private fun adoptFiles(all: List<Pair<Track, String>>): Int {
        var n = 0
        for ((t, path) in all) {
            if (!looked.add(path)) continue
            val st = synchronized(lock) { songs[keyOf(t)]?.state }.orEmpty()
            if (st == FILE || st == LEFT || st == INSTRUMENTAL) continue
            if (!inFile(path).isNullOrBlank()) { set(t, FILE); n++ }
        }
        if (n > 0) save()
        return n
    }

    /** Count again, in the background. */
    fun recount() {
        Music.bg({
            load()
            val all = allSongs()
            adoptFiles(all)
            val st = synchronized(lock) { all.map { (t, _) -> songs[keyOf(t)]?.state.orEmpty() } }
            Counts(st.count { it == FILE }, st.count { it == APP }, st.count { it == NONE }, st.count { it == LEFT },
                st.count { it == INSTRUMENTAL }, all.size)
        }) { c, _ -> if (c != null) counts = c }
    }

    /** Find the lyrics of every song that hasn't got them yet (see the object's note); Stop keeps what's done. */
    fun start() {
        if (running || BatterySaver.holdsScan("the lyrics scan")) return
        running = true; stopAsked = false
        progress = 0 to 0; nowSong = ""
        Thread({
            var found = 0; var none = 0
            try { val r = work(); found = r.first; none = r.second } catch (e: Throwable) { Crashes.caught(e) }
            finally {
                running = false
                save()
                Music.scope.launch {
                    progress = null; nowSong = ""; version++
                    Music.toast(if (cutOff) "No connection: the lyrics scan stopped. The songs not looked up wait for next time" +
                            (if (found > 0) " (%,d found)".format(found) else "")
                        else if (found + none == 0) (if (stopAsked) "Lyrics scan stopped" else "Every song has its lyrics already") else
                        "Lyrics found for %,d song%s".format(found, if (found != 1) "s" else "") +
                            if (none > 0) ". %,d with none found: Settings › Scanner › Lyrics › Show…".format(none) else "")
                    recount()
                }
            }
        }, "lyrics-scan").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; this.start() }
    }

    @Volatile private var cutOff = false
    /** (found, none found). */
    private fun work(): Pair<Int, Int> {
        load()
        // Only the songs still without lyrics: the ones whose file has them (known, or found now by looking in the files
        // not looked at yet), the ones kept in the app, and the ones left alone or instrumental aren't gone through at
        // all. Before, every song was, each file read again, and the count ran over all of them.
        val everything = allSongs()
        adoptFiles(everything)
        val all = everything.filter { (t, _) ->
            when (stateOf(t)) {
                FILE, LEFT, INSTRUMENTAL -> false
                APP -> kept(Music.ctx, t) == null
                else -> true
            }
        }
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        Music.scope.launch { progress = 0 to all.size }
        var found = 0; var none = 0; var unreached = 0
        cutOff = false
        for ((i, x) in all.withIndex()) {
            if (stopAsked) break
            val (t, path) = x
            if (i % 5 == 0) Music.scope.launch { progress = i to all.size }
            val st = stateOf(t)
            if (st == LEFT || st == INSTRUMENTAL) continue
            if (!inFile(path).isNullOrBlank()) { if (st != FILE) set(t, FILE); continue }   // in its file already
            Music.scope.launch { nowSong = "${t.title} · ${t.artist.split(",")[0].trim()}" }
            val got = kept(Music.ctx, t) ?: runCatching { Lyrics.get(Music.ctx, t) }.getOrNull()
            // No site answered (offline): the song is left to look up another time, not written down as having none.
            // Three in a row: the scan stops there.
            if (got == null && Lyrics.unreachedJustNow()) { if (++unreached >= 3) { cutOff = true; break }; continue }
            unreached = 0
            if (place(t, path, got, playing)) found++ else if (stateOf(t) == NONE) none++
            if ((i + 1) % 25 == 0) save()
            Thread.sleep(if (Music.paused) 50 else 200)   // gently: one site after another, a song at a time
        }
        Music.scope.launch { progress = all.size to all.size }
        return found to none
    }

    /** Lyrics found for a song put where they go (putLyrics: its file when the app may change it, once it isn't
     *  playing, else kept in the app). Nothing found: an instrumental, or none. True if it has lyrics now. */
    private fun place(t: Track, path: String?, got: Lyrics.Got?, playing: String? = null): Boolean {
        if (got == null || LyricsTags.forFile(got) == null) {
            set(t, if (got?.instrumental == true) INSTRUMENTAL else NONE)
            return false
        }
        putLyrics(t, path, got, playing)
        return true
    }

    // ---- the songs it found nothing for (Show…) ----

    /** The songs with no lyrics found, as they are now (the ones gone dropped). */
    fun notFound(): List<Track> {
        load()
        val want = synchronized(lock) { songs.filterValues { it.state == NONE }.keys }
        if (want.isEmpty()) return emptyList()
        return allSongs().map { it.first }.filter { keyOf(it) in want }
    }

    /** How many of your songs you said to leave alone (Leave alone; streamed songs aren't counted, see [allSongs]). */
    fun leftCount(): Int { load(); return synchronized(lock) { songs.entries.count { it.value.state == LEFT && !it.key.startsWith("song:") } } }

    /** Leave it alone: not asked about again (Show them again brings it back). Instrumental: no words to find. */
    fun leave(t: Track, instrumental: Boolean = false) { set(t, if (instrumental) INSTRUMENTAL else LEFT); save(); version++ }

    /** Undo of Leave alone, right after. */
    fun unleave(t: Track) { set(t, NONE); save(); version++ }

    /** The songs left alone shown again (they're looked for in the next scan too). */
    fun showLeftAgain() {
        load()
        synchronized(lock) { for ((k, v) in songs.entries.toList()) if (v.state == LEFT) songs[k] = v.copy(state = NONE) }
        save(); version++
    }

    /** Lyrics you typed in: into the file (where the app may change it), else kept in the app. */
    fun enter(t: Track, text: String) {
        val words = text.trim()
        if (words.isEmpty()) return
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        Music.bg({ place(t, pathOf(t), Lyrics.Got(plain = words, plain_from = "you"), playing); save() }) { _, _ ->
            version++; recount()
            Music.toast(if (stateOf(t) == FILE) "Lyrics written into “${t.title}”" else "Lyrics kept for “${t.title}”")
        }
    }

    /** Search again: the lyrics sites asked once more, with a looser title ("Song (2011 Remaster) - Live" as "Song")
     *  and the lead artist. */
    fun searchAgain(t: Track, done: (Boolean) -> Unit = {}) {
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        Music.bg({
            val lead = t.artist.split(",")[0].trim()
            val tries = linkedSetOf(bareTitle(t.title), cleanTitle(t.title)).filter { it.isNotBlank() }
            var got: Lyrics.Got? = null
            for (title in tries) { got = runCatching { Lyrics.search(Music.ctx, t, lead, title) }.getOrNull(); if (got != null) break }
            val ok = got != null && place(t, pathOf(t), got, playing)
            save(); ok
        }) { ok, _ ->
            version++; recount()
            Music.toast(if (ok == true) "Found the lyrics of “${t.title}”" else "Still no lyrics for “${t.title}”")
            done(ok == true)
        }
    }

    /** A web search for the song's lyrics, to open in your browser (nothing is read from it here). */
    fun webSearch(t: Track): String = "https://www.google.com/search?q=" + enc("${t.title} ${t.artist.split(",")[0].trim()} lyrics")

    // ---- the songs with words but no timing (auto-sync, LyricsAutoSync) ----

    /** (blocking) The lyrics a song has here, nothing looked up: the ones waiting for its file when there are some
     *  (null: they were removed), else its file's, with timed lines kept in the app when the file has only the words
     *  (a file the app couldn't write the timing into), else the ones kept in the app. */
    fun here(t: Track): Lyrics.Got? {
        if (isHeld(t)) return kept(Music.ctx, t)?.takeIf { LyricsTags.forFile(it) != null }
        val path = pathOf(t)
        val f = path?.let { inFile(it) }?.takeIf { it.isNotBlank() }?.let { txt ->
            if (Lyrics.parseLrc(txt).isNotEmpty()) Lyrics.Got(null, null, txt, "the file") else Lyrics.Got(txt, "the file")
        }
        val k = kept(Music.ctx, t)
        if (f == null) return k
        if (f.synced == null && k?.synced != null) return f.copy(synced = k.synced, synced_from = k.synced_from)
        return f
    }

    /** (blocking) Your songs (with a file, so they can be listened to) that the scan found no lyrics for and that
     *  still have none: what auto-sync listens to for their words. */
    fun unheard(): List<Track> = notFound().filter { pathOf(it) != null && here(it) == null }

    /** (blocking) Your songs (with a file, so they can be listened to) that have their words but no timing: what
     *  auto-sync goes through. The ones left alone or instrumental aren't. */
    fun untimed(): List<Pair<Track, Lyrics.Got>> {
        load()
        return allSongs().mapNotNull { (t, _) ->
            val st = stateOf(t)
            if (st == LEFT || st == INSTRUMENTAL) return@mapNotNull null
            val g = here(t) ?: return@mapNotNull null
            if (g.synced != null || LyricsTags.words(g) == null) null else t to g
        }
    }

    /** (blocking) Timed lyrics for a song put where its lyrics go (its file where the app may change it, else kept in
     *  the app, as the scan does), with its words. True if they were put somewhere. */
    fun placeTimed(t: Track, words: String, lrc: String, from: String): Boolean {
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        val ok = place(t, pathOf(t), Lyrics.Got(plain = words, plain_from = "the file", synced = lrc, synced_from = from), playing)
        save(); Music.scope.launch { version++ }
        return ok
    }

    /** (blocking) A song's timing taken away again (auto-sync's, removed): only its words, in its file or kept. */
    fun dropTiming(t: Track): Boolean {
        val words = here(t)?.let { LyricsTags.words(it) } ?: return false
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        putLyrics(t, pathOf(t), Lyrics.Got(plain = words, plain_from = "the file"), playing)
        save(); Music.scope.launch { version++ }
        return true
    }

    /** (blocking) A song's lyrics taken away (ones the app heard, removed): out of its file, or the app. It's among the
     *  songs with none found again. */
    fun dropLyrics(t: Track): Boolean {
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        putLyrics(t, pathOf(t), Lyrics.Got(plain = ""), playing)
        save(); Music.scope.launch { version++ }
        return true
    }

    /** Lyrics you changed (the Lyrics tab's Edit): timed lines when they have times ("[01:23.45]"), else the words.
     *  Straight into the song's file where the app may change it (the song playing: the moment it ends, shown as they
     *  are meanwhile), else kept in the app. [done]: whether they were saved. */
    fun edit(t: Track, text: String, done: (Boolean) -> Unit = {}) {
        val body = text.lines().joinToString("\n") { it.trimEnd() }.trim()
        if (body.isEmpty()) return
        val g = if (Lyrics.parseLrc(body).isNotEmpty()) Lyrics.Got(synced = body, synced_from = "you") else Lyrics.Got(plain = body, plain_from = "you")
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        Music.bg({
            val path = pathOf(t)
            val toFile = putLyrics(t, path, g, playing)
            save()
            when {
                toFile && isHeld(t) -> "Saved: it goes into the song's file as soon as it has finished playing"
                toFile -> "Saved into the song's file"
                path == null -> "Saved in the app (the song is streamed: there's no file)"
                else -> "Saved in the app (the app may not change this song's file)"
            }
        }) { msg, err ->
            version++; recount()
            Music.toast(msg ?: "Couldn't save the lyrics: ${err?.message}")
            done(msg != null)
        }
    }
}
