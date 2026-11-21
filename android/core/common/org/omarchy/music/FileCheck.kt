package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** Storage and damaged files ([FileCheckRules]): one pass over your song files, in the background, on demand. Each
 *  file's size is read and its first few bytes looked at; nothing is played, changed or deleted. */
object FileCheck {
    /** tracks: the damaged files' songs, for getting new copies of them (Download new copies). */
    class Result(val summary: FileCheckRules.Summary, val damaged: List<Pair<String, String>>, val unread: Int, val said: String,
                 val tracks: List<Track> = emptyList())

    var busy by mutableStateOf<String?>(null)   // "Looking at 1,200 of 3,322…" while it runs
        private set
    var result by mutableStateOf<Result?>(null)
        private set

    fun run() {
        if (busy != null) return
        busy = "Reading your songs…"
        Music.bg({
            val songs = Music.mySongs().filter { !it.file.isNullOrEmpty() }
            val out = ArrayList<FileCheckRules.Song>(songs.size); val bad = ArrayList<Pair<String, String>>(); var unread = 0
            val badSongs = ArrayList<Track>()
            songs.forEachIndexed { i, t ->
                if (i % 50 == 0) Music.scope.launch { busy = "Looking at %,d of %,d…".format(i, songs.size) }
                val file = t.file!!
                val bytes = runCatching { Music.ctx.typeAndSize(file)?.second }.getOrNull()
                val head = runCatching { Music.ctx.openIn(file)?.use { s -> ByteArray(32).also { b -> var n = 0; while (n < 32) { val r = s.read(b, n, 32 - n); if (r < 0) break; n += r } } } }.getOrNull()
                if (bytes == null || head == null) { unread++; return@forEachIndexed }
                val kind = MoodWriteRules.kindOf(head)
                out += FileCheckRules.Song(t.title, t.artist, t.album, kind, bytes)
                val why = FileCheckRules.damage(head, bytes, t.duration)
                    // …or a FLAC whose tags and cover no longer lead to its music: what a tag write cut short leaves. Its
                    // first bytes look right, so this is looked for too (the blocks are stepped over, not read).
                    ?: if (String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" && runCatching { Music.ctx.openIn(file)?.use { !FileWhole.flacLeadsToSound(it) } }.getOrNull() == true)
                        "its tags and cover no longer lead to its music (a tag write that was cut short). Play it on the phone and the app puts it right; on the PC it plays as it is" else null
                    // …or an MP3 or WAV that plays for far less than the music in it (an MP3 only when sound does follow
                    // its tag: a FLAC behind an ID3 tag isn't measured as an MP3)
                    ?: FileCheckRules.soundOf(head, bytes)?.takeIf { kind != "MP3" || mp3Follows(file, bytes - it.first) }
                        ?.let { (sound, atMost) -> FileCheckRules.stopsEarly(sound, atMost, t.duration) }
                why?.let {
                    badSongs += t
                    bad += ((runCatching { FileTags.nameOf(file) }.getOrNull() ?: MoodWriteRules.fileName(file)) + "  (" + PlaylistSendRules.songName(t.title, t.artist) + ")") to it }
            }
            val sum = FileCheckRules.summary(out)
            Result(sum, bad, unread, FileCheckRules.said(sum.songs, sum.total, bad.size, unread), badSongs)
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
