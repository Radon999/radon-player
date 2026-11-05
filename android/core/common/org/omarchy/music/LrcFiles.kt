package org.omarchy.music

import java.io.File

/** Lyrics files: for each song with lyrics in its file's tags, a .lrc file of the same name beside it, so any player
 *  (and any device the folder goes to) has them. Timed lyrics are saved as they are; plain ones as lines with no times.
 *  A song with a .lrc beside it already is left as it is. */
object LrcFiles {
    data class Done(val saved: Int, val had: Int, val none: Int, val failed: Int, val notes: List<String>)

    private fun clean(s: String) = s.replace(Regex("[\\r\\n\\[\\]]"), " ").trim()

    /** The .lrc text for a song's lyrics. */
    fun text(t: Track, g: Lyrics.Got): String? {
        val head = buildString {
            if (t.title.isNotBlank()) append("[ti:${clean(t.title)}]\n")
            if (t.artist.isNotBlank()) append("[ar:${clean(t.artist)}]\n")
            if (t.album.isNotBlank()) append("[al:${clean(t.album)}]\n")
            if (t.duration > 0) append("[length:%d:%02d]\n".format(t.duration.toInt() / 60, t.duration.toInt() % 60))
        }
        val synced = g.synced?.trim()?.takeIf { Lyrics.parseLrc(it).isNotEmpty() }
        if (synced != null) {
            // Its own header lines (ti, ar…) kept when it has them, else ours
            return if (Regex("(?m)^\\[(ti|ar):").containsMatchIn(synced)) synced + "\n" else head + synced + "\n"
        }
        val plain = g.plain?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return head + plain.lines().joinToString("\n") { it.trimEnd() } + "\n"
    }

    /** (blocking) A .lrc beside every song of yours that has lyrics in its file. */
    fun save(progress: (String) -> Unit = {}): Done {
        var saved = 0; var had = 0; var none = 0; var failed = 0
        val notes = ArrayList<String>()
        val songs = Music.mySongs()
        val seen = HashSet<String>()
        for ((i, t) in songs.withIndex()) {
            if (i % 20 == 0) progress("Making lyrics files… %,d of %,d".format(i, songs.size))
            val f = Music.lib.items[t.id]?.file ?: t.file?.takeIf { t.isFile } ?: continue
            if (!seen.add(f)) continue
            val dir = runCatching { FileTags.folderOf(Music.ctx, f) }.getOrNull()?.takeIf { it.isNotBlank() }
            val name = (if (f.startsWith("content:")) runCatching { Music.ctx.displayName(f) }.getOrNull() else File(f).name)
                ?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            if (dir == null || name == null) { failed++; if (notes.size < 20) notes += "“${t.title}”: couldn't find its folder"; continue }
            val lrc = File(dir, "$name.lrc")
            if (lrc.isFile) { had++; continue }
            val g = runCatching { Lyrics.inFile(Music.ctx, t) }.getOrNull()
            val body = g?.let { text(t, it) }
            if (body == null) { none++; continue }
            if (runCatching { lrc.writeText(body, Charsets.UTF_8); true }.getOrDefault(false)) saved++
            else { failed++; if (notes.size < 20) notes += "“${t.title}”: couldn't save ${lrc.name}" }
        }
        return Done(saved, had, none, failed, notes)
    }
}
