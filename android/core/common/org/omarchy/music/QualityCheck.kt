package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** A song file's check: clipped samples (0: none to speak of), its peak in dBFS, and why it isn't the hi-res it says ("" if it is). */
@Serializable
data class QualityResult(val clipped: Int = 0, val peak: Double? = null, val fake: String = "")

@Serializable
data class QualityEntry(val mtime: Long = 0, val res: QualityResult = QualityResult())

/** Loudness check (Settings › Playback): every song of yours checked once (again when it changes), in the background, a
 *  song at a time, for clipping and for files that aren't the hi-res they say. Identify by sound: AcoustID, by the song's sound. */
object QualityCheck {
    private lateinit var file: File
    private val done = HashMap<String, QualityEntry>()
    var busy by mutableStateOf<String?>(null)
        private set
    var version by mutableStateOf(0)
        private set

    fun init(ctx: Ctx) {
        file = File(ctx.filesDir, "quality-checks.json")
        readText(file)?.let { runCatching { JSON.decodeFromString(MapSerializer(String.serializer(), QualityEntry.serializer()), it) }.getOrNull() }?.let { done.putAll(it) }
    }

    private fun save() = writeAtomic(file, JSON.encodeToString(MapSerializer(String.serializer(), QualityEntry.serializer()), synchronized(done) { done.toMap() }))

    val checked get() = synchronized(done) { done.size }

    /** The songs the check found something in, the most clipped first. */
    fun flagged(): List<Pair<String, QualityResult>> = synchronized(done) { done.map { it.key to it.value.res } }
        .filter { it.second.clipped > 0 || it.second.fake.isNotEmpty() }.sortedWith(compareBy({ -it.second.clipped }, { it.first }))

    /** Check the songs not checked yet (or changed since). */
    fun scan() {
        return   // Loudness check was taken out in 3.0.0: no songs are checked in the background
        if (!AudioTools.supported || busy != null || !Prefs.on(Prefs.LOUDNESS_CHECK) || !AudioTools.canRunNow()) return
        val files = Music.mySongs().mapNotNull { it.file ?: Music.lib.items[it.id]?.file }.distinct()
        busy = "looking…"
        Music.bg({
            val todo = files.mapNotNull { f -> AudioTools.modified(f)?.let { m -> if (done[f]?.mtime != m) f to m else null } }
            Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { busy = if (todo.isEmpty()) null else "0 of ${"%,d".format(todo.size)}" }
            for ((i, fm) in todo.withIndex()) {
                if (!Prefs.on(Prefs.LOUDNESS_CHECK) || !AudioTools.canRunNow()) break
                val (f, m) = fm
                val facts = org.omarchy.music.ui.fileFacts(f)
                val q = facts.firstOrNull { it.first == "Quality" }?.second ?: ""
                val bits = Regex("(\\d+)-bit").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val rate = Regex("/([\\d.]+) kHz").find(q)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 1000).toInt() } ?: 0
                val r = runCatching { AudioTools.quality(f, rate, bits) }.getOrDefault(QualityResult())
                synchronized(done) { done[f] = QualityEntry(m, r) }
                Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { busy = "${"%,d".format(i + 1)} of ${"%,d".format(todo.size)}"; version++ }
                if (i % 25 == 24) save()
            }
            save()
        }) { _, _ -> busy = null; version++ }
    }

    data class Identified(val title: String, val artist: String, val album: String, val type: String, val score: Double)

    /** (blocking) What a song is by its sound (AcoustID, then MusicBrainz's names), the likeliest first. */
    fun identify(path: String, secs: Int): List<Identified> {
        val key = Music.apiKeys["acoustid"] ?: error("Identify by sound needs an AcoustID key (Settings › More › Settings)")
        val (fp, d) = AudioTools.fingerprint(path, secs)
        val body = "client=${enc(key)}&duration=$d&fingerprint=${enc(fp)}&format=json&meta=${enc("recordings releasegroups compress")}"
        val res = httpJson("https://api.acoustid.org/v2/lookup", mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
        if (res["status"].str != "ok") error(res["error"]["message"].str ?: "AcoustID didn't answer")
        val out = ArrayList<Identified>(); val seen = HashSet<String>()
        for (r in res["results"].list) for (rec in r["recordings"].list) {
            val title = rec["title"].str ?: ""
            val artist = rec["artists"].list.joinToString(", ") { it["name"].str ?: "" }
            for (g in rec["releasegroups"].list.ifEmpty { listOf(null) }.take(3)) {
                val k = "${norm(title)}|${norm(artist)}|${norm(g["title"].str ?: "")}"
                if (title.isNotEmpty() && seen.add(k)) out += Identified(title, artist, g["title"].str ?: "", g["type"].str ?: "", r["score"].dbl ?: 0.0)
            }
        }
        return out.take(12)
    }
}
