package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import java.io.File

/** The desktop's tagger, for the FLACs the app downloaded: genre, composer, label, barcode, original
 *  date and track total (Apple's catalogue, by ISRC), the MusicBrainz recording, the lyrics, and
 *  Genius's notes on them. Tag now shows what changes first; songs coming in (downloads, an
 *  upgrade) are tagged together once the last one is in. */
object Tagger {
    const val VERSION = 2   // 2: Genius's notes go in too

    /** What tagging a song would do: the tags it adds or changes (old beside them), nothing written yet. */
    data class Plan(val id: String, val uri: String, val old: Map<String, List<String>>, val fill: Map<String, List<String>>)

    val NAMES = linkedMapOf("genre" to "Genre", "composer" to "Composer", "label" to "Label", "barcode" to "Barcode",
        "originaldate" to "Original date", "tracktotal" to "Tracks on the album", "apple_music_id" to "Apple Music ID",
        "musicbrainz_trackid" to "MusicBrainz recording", "lyrics" to "Lyrics", "genius_notes" to "Genius notes")

    var busy by mutableStateOf<String?>(null)
        private set
    private var hold = 0
    private val held = LinkedHashSet<String>()
    private val waiting = LinkedHashSet<String>()
    private val fresh = HashSet<String>()   // songs to tag from scratch (Retag)
    private var running = false

    private fun taggable(e: LibEntry?) = e != null && e.format == "flac" && e.file.startsWith("content://")

    fun untagged(): List<String> = Music.lib.items.values.filter { taggable(it) && it.tagged < VERSION }.map { it.id }

    fun notesText(notes: List<Lyrics.Noted>): String = notes.joinToString("\n\n") { n ->
        val best = n.notes.maxWith(compareBy<Lyrics.Note>({ it.verified }, { it.votes }))
        "“${n.fragment}”\n${best.text}"
    }

    /** (blocking) What tagging id would do. light: tagged before, so just what's new since (Genius's notes). */
    fun plan(id: String, light: Boolean = false): Plan? {
        val e = Music.lib.items[id]?.takeIf { taggable(it) } ?: return null
        val t = e.track()
        val m = songMeta(Music.ctx, Music.lib, t)
        val old = m.tags
        val new = LinkedHashMap<String, List<String>>()
        val isrc = old["isrc"]?.firstOrNull() ?: e.isrc ?: ""
        if (!light && isrc.isNotEmpty()) {
            runCatching {
                val apple = Music.sources["apple"] as? AppleMusic ?: AppleMusic(Music.ctx, null)
                apple.catalogSong(isrc, m.album)
            }.getOrNull()?.let { s ->
                val a = s["attributes"]
                val aa = s.path("relationships", "albums", "data").list.firstOrNull()?.get("attributes")
                fun put(k: String, v: String?) { if (!v.isNullOrBlank()) new[k] = listOf(v) }
                a["genreNames"].list.mapNotNull { it.str }.filter { it != "Music" }.take(3).takeIf { it.isNotEmpty() }?.let { new["genre"] = it }
                put("composer", a["composerName"].str)
                put("label", aa?.get("recordLabel")?.str)
                put("barcode", aa?.get("upc")?.str)
                put("originaldate", a["releaseDate"].str)
                put("tracktotal", aa?.get("trackCount")?.long?.toString())
                put("apple_music_id", s["id"].str)
            }
            runCatching {
                httpJson("https://musicbrainz.org/ws/2/isrc/$isrc?fmt=json", mapOf("User-Agent" to "RadonPlayer/1.4 ( https://github.com/Radon999/radon-player )"))
            }.getOrNull()?.get("recordings")?.list?.minByOrNull { r ->
                r["length"].long?.let { kotlin.math.abs(it / 1000.0 - m.duration) } ?: 999.0
            }?.get("id")?.str?.let { new["musicbrainz_trackid"] = listOf(it) }
        }
        val got = runCatching { Lyrics.get(Music.ctx, t) }.getOrNull()
        if (got != null) {
            LyricsTags.words(got)?.takeIf { it.isNotBlank() }?.let { new["lyrics"] = listOf(it) }
            val notes = runCatching { Lyrics.notes(Music.ctx, got) }.getOrDefault(emptyList())
            if (notes.isNotEmpty()) new["genius_notes"] = listOf(notesText(notes))
        }
        // Keep what the file says already (Tidal's names, numbers, date); add the rest.
        val fill = new.filter { (k, v) -> (k in setOf("lyrics", "genre", "genius_notes") || old[k].isNullOrEmpty()) && old[k] != v }
        return Plan(id, e.file, old, fill)
    }

    /** (blocking) Put plan into its file, and the library. */
    fun write(p: Plan) {
        if (p.fill.isNotEmpty()) {
            val cr = Music.ctx
            val tmp = File.createTempFile("tagging", ".flac", Music.ctx.cacheDir)
            try {
                cr.openIn(p.uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                cr.openOut(p.uri)!!.buffered().use {
                    Flac.retag(tmp, it, p.fill.mapKeys { (k, _) -> k.uppercase() }, null, keepOther = true)
                }
            } finally {
                tmp.delete()
            }
        }
        val genre = (p.fill["genre"] ?: p.old["genre"])?.firstOrNull()
        val file = Music.lib.items[p.id]?.file
        Music.lib.items.values.filter { it.file == file }.forEach { e ->
            Music.lib.update(e.id) { it.copy(tagged = VERSION, genre = genre ?: it.genre) }
        }
        Music.lib.save()
    }

    /** Downloads or an upgrade start: what comes in waits, and is tagged together at the end. */
    @Synchronized fun pause() { hold++ }

    @Synchronized fun resume() {
        hold = maxOf(0, hold - 1)
        if (hold == 0 && held.isNotEmpty()) { val ids = held.toList(); held.clear(); start(ids) }
    }

    /** Tag ids (default: every downloaded song not tagged yet). full: from scratch, as if never tagged. */
    @Synchronized fun start(ids: List<String>? = null, full: Boolean = false) {
        if (full && ids != null) fresh += ids
        if (hold > 0 && ids != null) { held += ids; return }
        waiting += (ids ?: untagged()).filter { taggable(Music.lib.items[it]) }
        if (running || waiting.isEmpty()) return
        running = true
        Thread {
            var done = 0
            while (true) {
                val id = synchronized(this) { waiting.firstOrNull()?.also { waiting.remove(it) } } ?: break
                busy = "Tagging · ${done + 1} of ${done + 1 + synchronized(this) { waiting.size }}"
                runCatching {
                    val e = Music.lib.items[id]
                    val light = (e?.tagged ?: 0) >= 1 && synchronized(this) { !fresh.remove(id) }
                    plan(id, light)?.let { write(it) }
                }
                done++
            }
            synchronized(this) { running = false }
            busy = null
            Music.scope.launch { Music.savedVersion++ }
        }.apply { isDaemon = true }.start()
    }
}
