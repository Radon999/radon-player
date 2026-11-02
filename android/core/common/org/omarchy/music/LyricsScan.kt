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

/** The lyrics scanner (Settings › Scanner, and the Moods tab): goes through your songs, finds each one's lyrics where
 *  the Lyrics tab finds them (the file's own, the ones kept here, Genius, Musixmatch through Tidal, LRCLIB) and writes
 *  them into the song's file (its LYRICS / USLT tag, timed lines when there are some) where the app may change the file:
 *  on the PC all of yours, on the phone its own downloads. Songs it can't write, and the songs in your playlists and
 *  favorites that are streamed, keep their lyrics in the app (offline too). Songs with lyrics in their file already are
 *  skipped. The songs it found nothing for are listed afterwards to deal with one by one (Settings › Scanner ›
 *  Lyrics › Show…): leave it alone (remembered), it's an instrumental, type the lyrics in, search the web, search again. */
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
    private fun pathOf(t: Track): String? = t.file?.takeIf { it.isNotEmpty() }
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

    /** Whether the app may change the file: on the PC any of yours; on the phone only its own downloads. */
    private fun canWrite(path: String) = runCatching { FileTags.canWrite(path) }.getOrDefault(false)

    /** The lyrics in the song's file itself, or null (read straight from the file, any kind the app can open). */
    private fun inFile(path: String): String? = runCatching {
        Music.ctx.openIn(path)?.buffered()?.use { MoodTag.readTags(it, setOf(MoodTag.LYRICS)) }?.get(MoodTag.LYRICS)
    }.getOrNull()

    /** (blocking) A song's words for reading its mood: (the words, or null; whether it's an instrumental). */
    fun wordsFor(t: Track): Pair<String?, Boolean> {
        if (stateOf(t) == INSTRUMENTAL) return null to true
        val g = runCatching { Lyrics.get(Music.ctx, t) }.getOrNull()
        val w = g?.let { LyricsTags.words(it) }
        return w to (w == null && g?.instrumental == true)
    }

    // ---- the scan ----

    /** The songs it goes through: (song, its file or null), your songs first, then the streamed ones of your
     *  playlists and favorites (once each, and not the ones you have as files). */
    private fun allSongs(): List<Pair<Track, String?>> {
        val mine = runCatching { Music.mySongs() }.getOrDefault(emptyList()).mapNotNull { t -> pathOf(t)?.let { t to it } }.distinctBy { it.second }
        val have = mine.mapTo(HashSet()) { songPickKey(it.first.title, it.first.artist) }
        val streamed = (runCatching { Music.favorites.tracks() }.getOrDefault(emptyList()) +
            runCatching { Music.lists.all().flatMap { it.tracks } }.getOrDefault(emptyList()))
            .filter { pathOf(it) == null && songPickKey(it.title, it.artist) !in have }
            .distinctBy { songPickKey(it.title, it.artist) }
        return mine.map { it.first to it.second } + streamed.map { it to null }
    }

    /** How it stands: songs with lyrics in their file, kept in the app, none found, left alone, instrumentals, of how many. */
    data class Counts(val inFiles: Int, val inApp: Int, val none: Int, val left: Int, val instrumental: Int, val total: Int) {
        /** Songs still without lyrics (not looked for yet, or none found), not counting the ones left alone. */
        val toFind get() = maxOf(0, total - inFiles - inApp - left - instrumental)
    }

    /** Count again, in the background. */
    fun recount() {
        Music.bg({
            load()
            val all = allSongs()
            val st = synchronized(lock) { all.map { (t, _) -> songs[keyOf(t)]?.state.orEmpty() } }
            Counts(st.count { it == FILE }, st.count { it == APP }, st.count { it == NONE }, st.count { it == LEFT },
                st.count { it == INSTRUMENTAL }, all.size)
        }) { c, _ -> if (c != null) counts = c }
    }

    /** Find the lyrics of every song that hasn't got them yet (see the object's note); Stop keeps what's done. */
    fun start() {
        if (running) return
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
                    Music.toast(if (found + none == 0) "Every song has its lyrics already" else
                        "Lyrics found for %,d song%s".format(found, if (found != 1) "s" else "") +
                            if (none > 0) ". %,d with none found: Settings › Scanner › Lyrics › Show…".format(none) else "")
                    recount()
                }
            }
        }, "lyrics-scan").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; this.start() }
    }

    /** (found, none found). */
    private fun work(): Pair<Int, Int> {
        load()
        val all = allSongs()
        val playing = runCatching { Music.current()?.let { pathOf(it) } }.getOrNull()
        Music.scope.launch { progress = 0 to all.size }
        var found = 0; var none = 0
        for ((i, x) in all.withIndex()) {
            if (stopAsked) break
            val (t, path) = x
            if (i % 5 == 0) Music.scope.launch { progress = i to all.size }
            val st = stateOf(t)
            if (st == LEFT || st == INSTRUMENTAL) continue
            if (path != null) {
                if (inFile(path) != null) { if (st != FILE) set(t, FILE); continue }   // in its file already
            } else if (st == APP && kept(Music.ctx, t) != null) continue
            Music.scope.launch { nowSong = "${t.title} · ${t.artist.split(",")[0].trim()}" }
            val got = kept(Music.ctx, t) ?: runCatching { Lyrics.get(Music.ctx, t) }.getOrNull()
            if (place(t, path, got, playing)) found++ else if (stateOf(t) == NONE) none++
            if ((i + 1) % 25 == 0) save()
            Thread.sleep(if (Music.paused) 50 else 200)   // gently: one site after another, a song at a time
        }
        Music.scope.launch { progress = all.size to all.size }
        return found to none
    }

    /** Lyrics found for a song put where they go: its file when the app may change it (not while it plays), else kept
     *  in the app. Nothing found: an instrumental, or none. True if it has lyrics now. */
    private fun place(t: Track, path: String?, got: Lyrics.Got?, playing: String? = null): Boolean {
        val text = got?.let { LyricsTags.forFile(it) }
        if (got == null || text == null) {
            set(t, if (got?.instrumental == true) INSTRUMENTAL else NONE)
            return false
        }
        val wrote = path != null && path != playing && canWrite(path) &&
            runCatching { LyricsTags.writeTags(Music.ctx, path, text, emptyMap()) }.getOrDefault(false)
        if (!wrote) keep(t, got)
        set(t, if (wrote) FILE else APP)
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

    /** How many songs you said to leave alone (Leave alone, Instrumental). */
    fun leftCount(): Int { load(); return synchronized(lock) { songs.values.count { it.state == LEFT } } }

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
}
