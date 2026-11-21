package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Storage and damaged files ([FileCheckRules]): one pass over your song files, in the background, on demand. Each
 *  file's size is read and its first few bytes looked at; nothing is played, changed or deleted. */
object FileCheck {
    /** tracks: the damaged files' songs, for getting new copies of them (Download new copies). */
    class Result(val summary: FileCheckRules.Summary, val damaged: List<Pair<String, String>>, val unread: Int, val said: String,
                 val tracks: List<Track> = emptyList(), val tell: (Int) -> String = { said })

    // ---- songs you told the check to leave out: never listed as damaged again (a song you can't get again, and
    // keep as it is). Known by the song (its title, artist and album: FileCheckRules.leftKey), so it holds on your
    // other device too, and goes over with Sync. (4.0.33 kept them by the file's address, on the device only: those
    // are taken over at the next check.) ----
    private val leftSer = MapSerializer(String.serializer(), String.serializer())
    private fun leftFile() = java.io.File(Music.ctx.filesDir, "damaged-left-out.json")
    private fun oldLeftFile() = java.io.File(Music.ctx.filesDir, "damaged-left-out.txt")
    /** key -> the song's name. */
    var leftOut by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var oldLeft: Set<String> = emptySet()   // file addresses, from 4.0.33
    private var leftRead = false
    private fun readLeft() {
        if (leftRead) return
        leftRead = true
        leftOut = readText(leftFile())?.let { runCatching { JSON.decodeFromString(leftSer, it) }.getOrNull() } ?: emptyMap()
        oldLeft = readText(oldLeftFile())?.lines()?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }
    private fun keepLeft(now: Map<String, String>) { leftOut = now; runCatching { writeAtomic(leftFile(), JSON.encodeToString(leftSer, now)) } }
    private fun keyOf(t: Track) = FileCheckRules.leftKey(t.title, t.artist, t.album)
    /** How many are left out (read from where they are kept, the first time it's asked). */
    fun leftOutCount(): Int { readLeft(); return leftOut.size + oldLeft.size }

    /** For Sync: what is left out here; and what Sync made of both devices' lists. */
    fun leftMap(): Map<String, String> { readLeft(); return leftOut }
    fun takeLeft(now: Map<String, String>) { readLeft(); if (now != leftOut) keepLeft(now) }

    /** These songs are never listed as damaged again; they go from the list now showing. */
    fun leaveOut(tracks: List<Track>) {
        readLeft()
        if (tracks.isEmpty()) return
        val keys = tracks.associate { keyOf(it) to PlaylistSendRules.songName(it.title, it.artist) }
        keepLeft(leftOut + keys)
        result?.let { r ->
            val keep = r.tracks.indices.filter { keyOf(r.tracks[it]) !in keys }
            result = Result(r.summary, keep.map { r.damaged[it] }, r.unread, r.tell(keep.size), keep.map { r.tracks[it] }, r.tell)
        }
    }

    /** Every song left out is looked at again by the next check. */
    fun bringBack() { readLeft(); keepLeft(emptyMap()); oldLeft = emptySet(); runCatching { oldLeftFile().delete() } }

    var busy by mutableStateOf<String?>(null)   // "Looking at 1,200 of 3,322…" while it runs
        private set
    var result by mutableStateOf<Result?>(null)
        private set

    @Volatile private var stopped = false
    /** A check that is running is asked to end: what it found so far is shown. */
    fun stop() { stopped = true }

    /** full: every file read from start to end and followed all the way through ([FullCheck]): slow (it reads your
     *  whole library), and finds damage anywhere inside a file. */
    fun run(full: Boolean = false) {
        if (busy != null) return
        stopped = false
        readLeft()
        busy = "Reading your songs…"
        Music.bg({
            val songs = Music.mySongs().filter { !it.file.isNullOrEmpty() }
            // (the ones 4.0.33 kept by file address: known by their song from here on)
            if (oldLeft.isNotEmpty()) {
                val old = oldLeft
                songs.filter { it.file in old }.takeIf { it.isNotEmpty() }?.let { m -> keepLeft(leftOut + m.associate { keyOf(it) to PlaylistSendRules.songName(it.title, it.artist) }) }
                oldLeft = emptySet(); runCatching { oldLeftFile().delete() }
            }
            val left = leftOut
            val out = ArrayList<FileCheckRules.Song>(songs.size); val bad = ArrayList<Pair<String, String>>(); var unread = 0
            val badSongs = ArrayList<Track>()
            var looked = 0
            for ((i, t) in songs.withIndex()) {
                if (stopped) break
                looked = i + 1
                if (full || i % 50 == 0) Music.scope.launch { if (busy != null) busy = (if (full) "Reading every file in full: %,d of %,d…" else "Looking at %,d of %,d…").format(i, songs.size) }
                val file = t.file!!
                val bytes = runCatching { Music.ctx.typeAndSize(file)?.second }.getOrNull()
                val head = runCatching { Music.ctx.openIn(file)?.use { s -> ByteArray(32).also { b -> var n = 0; while (n < 32) { val r = s.read(b, n, 32 - n); if (r < 0) break; n += r } } } }.getOrNull()
                if (bytes == null || head == null) { unread++; continue }
                val kind = MoodWriteRules.kindOf(head)
                out += FileCheckRules.Song(t.title, t.artist, t.album, kind, bytes)
                if (keyOf(t) in left) continue   // one you told the check to leave out: counted above, not looked into
                val why = FileCheckRules.damage(head, bytes, t.duration)
                    // …or a FLAC whose tags and cover no longer lead to its music: what a tag write cut short leaves. Its
                    // first bytes look right, so this is looked for too (the blocks are stepped over, not read).
                    ?: if (String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" && runCatching { Music.ctx.openIn(file)?.use { !FileWhole.flacLeadsToSound(it) } }.getOrNull() == true)
                        "its tags and cover no longer lead to its music (a tag write that was cut short). Play it on the phone and the app puts it right; on the PC it plays as it is" else null
                    // …or an MP3 or WAV that plays for far less than the music in it (an MP3 only when sound does follow
                    // its tag: a FLAC behind an ID3 tag isn't measured as an MP3)
                    ?: FileCheckRules.soundOf(head, bytes)?.takeIf { kind != "MP3" || mp3Follows(file, bytes - it.first) }
                        ?.let { (sound, atMost) -> FileCheckRules.stopsEarly(sound, atMost, t.duration) }
                    // The full check: the whole file read and followed to its end
                    ?: if (full) runCatching { Music.ctx.openIn(file)?.use { FullCheck.check(it) { stopped } } }.getOrElse { "it couldn't be read to its end (${it.message?.take(60) ?: it.javaClass.simpleName})" } else null
                why?.let {
                    badSongs += t
                    bad += ((runCatching { FileTags.nameOf(file) }.getOrNull() ?: MoodWriteRules.fileName(file)) + "  (" + PlaylistSendRules.songName(t.title, t.artist) + ")") to it }
            }
            val sum = FileCheckRules.summary(out)
            val wasStopped = stopped; val all = songs.size
            val tell = { n: Int ->
                val said = FileCheckRules.said(sum.songs, sum.total, n, unread)
                when {
                    wasStopped -> "Stopped after %,d of %,d songs. ".format(looked, all) + said
                    full -> "$said. Every file was read in full"
                    else -> said
                }
            }
            Result(sum, bad, unread, tell(bad.size), badSongs, tell)
        }) { r, err ->
            busy = null
            if (r == null) { Music.toast("Couldn't look at your files: ${err?.message?.take(80)}"); return@bg }
            result = r
            Music.toast(r.said)
            if (r.damaged.isNotEmpty()) runCatching { Crashes.note("Damaged files found: " + r.damaged.take(40).joinToString("; ") { "${it.first}: ${it.second}" }) }
        }
    }

    /** Whether MP3 sound starts at byte [at] of the file (after its ID3 tag). */
    private fun mp3Follows(file: String, at: Long): Boolean = runCatching {
        Music.ctx.openIn(file)?.use { s ->
            var left = at
            while (left > 0) { val k = s.skip(left); if (k <= 0) return@use false; left -= k }
            val a = s.read(); val b = s.read()
            a == 0xff && (b and 0xe0) == 0xe0 && (b and 0x06) != 0
        } ?: false
    }.getOrDefault(false)

    /** The whole of it as text, to copy. */
    fun text(): String = result?.let { r -> buildString {
        append(r.said).append('\n')
        fun part(name: String, rows: List<Pair<String, Long>>) { append('\n').append(name).append('\n'); rows.forEach { append("  ").append(SupportRules.size(it.second)).append("  ").append(it.first).append('\n') } }
        part("By kind of file", r.summary.byKind); part("Artists taking the most", r.summary.artists); part("Albums taking the most", r.summary.albums); part("Biggest songs", r.summary.biggest)
        if (r.damaged.isNotEmpty()) { append("\nDamaged files\n"); r.damaged.forEach { append("  ").append(it.first).append(": ").append(it.second).append('\n') } }
    } } ?: ""
}
