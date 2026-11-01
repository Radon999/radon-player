package org.omarchy.music

import kotlinx.serialization.Serializable
import java.io.File

/** A diary of listening, kept on this device from 3.0 on, for Wrapped: each stretch of a song you heard, how long,
 *  where it was played from (a playlist of yours, an album, an artist, a search, the DJ, a radio…), whether that list
 *  was shuffled, which output the sound went to (the car, earbuds, a speaker, the PC) and whether you picked the song
 *  from search results. Music feeds it: [picked] when a list starts playing, [begin] when a song starts, [heard] as it
 *  plays. One line of JSON a song in listen-log.jsonl; the song playing now is kept in listen-open.json till it's done,
 *  so a closed app loses nothing. */
object ListenLog {
    @Serializable
    data class Entry(
        val t: Long,                      // when it started (seconds)
        val secs: Double = 0.0,           // how long it was heard
        val title: String = "", val artist: String = "", val album: String = "", val id: String = "",
        val from: String = "",            // the list's name ("Gym", "Search: daft punk", "Up next")
        val kind: String = "",            // playlist, album, artist, search, dj, radio, other
        val list: String = "",            // a playlist of yours: its id
        val shuffled: Boolean = false,
        val out: String = "",             // the output's name ("Sony WF-1000XM6", "Built-in speaker")
        val outKind: String = "",         // speaker, bluetooth, car, usb, wired…
        val search: Boolean = false,      // picked from search results
    )

    /** Seconds of a song less than this aren't written down (a tap past it). */
    const val MIN_SECS = 5.0

    private val lock = Any()
    private var open: Entry? = null
    private var savedAt = 0.0
    private var searchPick: String? = null   // the song id you just tapped in search results
    @Volatile private var recovered = false
    private val dir get() = Music.ctx.filesDir
    private val file get() = File(dir, "listen-log.jsonl")
    private val openFile get() = File(dir, "listen-open.json")

    /** A list starts playing at song t (Music.playFrom), from the screen called listName: noted if it's a search. */
    fun picked(t: Track?, listName: String) {
        searchPick = if (t != null && listName.startsWith("Search: ")) t.id else null
    }

    /** A song starts: the one before it is written down, and this one opened with where it's played from now. */
    fun begin(t: Track) { runCatching { start(t) } }

    private fun start(t: Track) {
        if (!recovered) Music.bg({ recover() })
        close()
        val from = Music.queueName.ifEmpty { "Up next" }
        var kind = "other"; var list = ""
        val p = runCatching { Music.lists.all().firstOrNull { it.name == from } }.getOrNull()
        when {
            Music.dj.on -> kind = "dj"
            from.startsWith("Search: ") -> kind = "search"
            p != null -> {
                val inIt = p.tracks.any { it.id == t.id || songKey(it) == songKey(t) }
                if (inIt) { kind = "playlist"; list = p.id } else kind = "radio"   // ∞ played on past its end
            }
            Music.isRadio() -> kind = "radio"
            t.album.isNotBlank() && norm(t.album) == norm(from) -> kind = "album"
            splitArtists(t.artist).any { norm(it) == norm(from) } -> kind = "artist"
        }
        val o = Music.outputNow
        val searched = searchPick == t.id
        if (searched) searchPick = null
        open = Entry(now().toLong(), 0.0, t.title, t.artist, t.album, t.id, from, kind, list, Music.shuffled, o.label, o.kind, searched)
        savedAt = now()
    }

    /** A song left open when the app was closed last time: written down now (once a start). */
    private fun recover() { synchronized(lock) {
        if (recovered) return
        recovered = true
        readText(openFile)?.let { txt -> runCatching { JSON.decodeFromString(Entry.serializer(), txt) }.getOrNull() }?.let { e ->
            // (unless it was written down after all: the app closed between the two)
            val had = runCatching { file.takeIf { it.exists() }?.readLines()?.takeLast(50).orEmpty()
                .any { l -> runCatching { JSON.decodeFromString(Entry.serializer(), l) }.getOrNull()?.let { it.t == e.t && it.id == e.id } == true } }
                .getOrDefault(false)
            if (e.secs >= MIN_SECS && !had) runCatching { file.appendText(JSON.encodeToString(Entry.serializer(), e) + "\n") }
        }
        openFile.delete()
    } }

    /** seconds more of the song playing were heard. The output changed mid-song: the rest counts for the new one. */
    fun heard(seconds: Double) {
        // A song that started without [begin] (picked up where the app left off, then played): opened now
        val cur = runCatching { Music.current() }.getOrNull()
        if (cur != null && open?.id != cur.id) begin(cur)
        val e = open ?: return
        val o = runCatching { Music.outputNow }.getOrNull()
        if (o != null && o.label != e.out) {
            close()
            open = e.copy(t = now().toLong(), secs = 0.0, out = o.label, outKind = o.kind, search = false)
        }
        open = open?.let { it.copy(secs = it.secs + seconds) }
        if (now() - savedAt > 30) {   // the song so far, in case the app is closed mid-song
            savedAt = now()
            val snap = open ?: return
            Music.bg({ synchronized(lock) { if (open?.t == snap.t) writeAtomic(openFile, JSON.encodeToString(Entry.serializer(), snap)) } })
        }
    }

    /** The song playing is written down (if it was heard long enough). */
    private fun close() {
        val e = open ?: return
        open = null
        Music.bg({ synchronized(lock) {
            if (e.secs >= MIN_SECS) file.appendText(JSON.encodeToString(Entry.serializer(), e) + "\n")
            openFile.delete()
        } })
    }

    /** (blocking) Everything written down, oldest first, with the song playing now so far. */
    fun all(): List<Entry> = synchronized(lock) {
        if (!recovered && open == null) recover()
        val out = ArrayList<Entry>()
        runCatching {
            if (file.exists()) file.forEachLine { line ->
                if (line.isNotBlank()) runCatching { JSON.decodeFromString(Entry.serializer(), line) }.getOrNull()?.let { out += it }
            }
        }
        open?.takeIf { it.secs >= MIN_SECS }?.let { out += it }
        out.sortedBy { it.t }
    }

    /** (blocking) The entries from start to end (seconds). */
    fun between(start: Long, end: Long): List<Entry> = all().filter { it.t in start until end }

    /** (blocking) When the diary begins (seconds), or 0 when it's empty. */
    fun since(): Long = all().firstOrNull()?.t ?: 0L
}
