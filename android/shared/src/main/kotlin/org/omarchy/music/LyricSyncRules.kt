package org.omarchy.music

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Auto-sync (Settings › Scanner › Lyrics): a song's plain lyrics timed line by line from the song itself. The app
 *  listens to the song (speech recognition, the words it hears and when), and this lines those words up with the
 *  lyrics: each line starts when its first word is heard; a line none of whose words were heard (a mumbled one, a
 *  scream) is placed between its neighbours by how many words lie between them. The plain rules, no Android or PC
 *  parts, so they can be tested. */
object LyricSyncRules {
    /** A word heard, at [at] seconds into the song. */
    data class Heard(val word: String, val at: Double)

    /** How many seconds a line is shown before it's sung, as timed lyrics are: the words heard are timed where they're
     *  heard, which reads late (Code Monkey: 0.35 left the lines a mean 0.33 s behind LRCLIB's timing, 51 lines). */
    const val LEAD = 0.65
    /** About how long a sung word lasts, for the lines that can only be placed by counting words. */
    const val WORD_SECS = 0.4
    /** The least share of the lyrics' words that must be heard for the timing to be trusted (else: couldn't time it). */
    const val MIN_HEARD = 0.3
    /** Recognition windows: this long, each overlapping the next by [OVERLAP] (the words in an overlap kept once). */
    const val WINDOW_SECS = 20
    const val OVERLAP_SECS = 2

    /** A word as compared: lower case, letters and digits only ("Don't" = "dont"), a sung "youuuu" as "you". */
    fun norm(w: String): String = w.lowercase().filter { it.isLetterOrDigit() }.replace(Regex("(.)\\1{2,}"), "$1")

    /** The lyric lines that are sung (empty ones and section labels such as "[Chorus]" or "(x2)" left out), each with
     *  its words. */
    fun lines(plain: String): List<Pair<String, List<String>>> = plain.lines().map { it.trim() }
        .filter { it.isNotEmpty() && !(it.startsWith("[") && it.endsWith("]")) }
        .map { it to it.split(Regex("\\s+")).map(::norm).filter { w -> w.isNotEmpty() } }
        .filter { it.second.isNotEmpty() }

    /** Words from a recogniser's pieces (each with its time), whose pieces starting with a space begin a new word. */
    fun words(tokens: List<String>, times: List<Double>, offset: Double): List<Heard> {
        val out = ArrayList<Heard>()
        var cur = StringBuilder(); var at = 0.0
        for (i in tokens.indices) {
            val t = tokens[i]
            if ((t.startsWith(" ") || t.startsWith("\u2581")) && cur.isNotEmpty()) { norm(cur.toString()).takeIf { it.isNotEmpty() }?.let { out += Heard(it, at) }; cur = StringBuilder() }
            if (cur.isEmpty()) at = offset + (times.getOrNull(i) ?: 0.0)
            cur.append(t.trimStart(' ', '\u2581'))
        }
        norm(cur.toString()).takeIf { it.isNotEmpty() }?.let { out += Heard(it, at) }
        return out
    }

    /** The words of one recognition window to keep: the window starting at [start] seconds, of a song of [total]
     *  seconds; a word in an overlap is kept from the window it's further inside of. */
    fun keep(ws: List<Heard>, start: Double, total: Double): List<Heard> {
        val from = if (start <= 0.0) Double.NEGATIVE_INFINITY else start + OVERLAP_SECS / 2.0
        val end = start + WINDOW_SECS
        val to = if (end >= total) Double.POSITIVE_INFINITY else end - OVERLAP_SECS / 2.0
        return ws.filter { it.at >= from && it.at < to }
    }

    /** How alike two words are, 0 … 1 (1: the same). */
    fun alike(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val keep = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = keep
            }
        }
        return 1.0 - d[b.length].toDouble() / max(a.length, b.length)
    }

    /** For each lyric word, when it was heard (null: not heard), by lining the two up in order (Needleman-Wunsch):
     *  a word matches a heard word alike enough; words heard that aren't in the lyrics (ad-libs, a backing vocal) and
     *  lyric words not heard are skipped over. */
    fun align(lyric: List<String>, heard: List<Heard>): Array<Double?> {
        val n = lyric.size; val m = heard.size
        val out = arrayOfNulls<Double>(n)
        if (n == 0 || m == 0) return out
        val gap = -0.4
        val score = Array(n + 1) { DoubleArray(m + 1) }
        val move = Array(n + 1) { ByteArray(m + 1) }   // 1: matched, 2: lyric word skipped, 3: heard word skipped
        for (i in 1..n) { score[i][0] = score[i - 1][0] + gap; move[i][0] = 2 }
        for (j in 1..m) { score[0][j] = score[0][j - 1] + gap; move[0][j] = 3 }
        for (i in 1..n) for (j in 1..m) {
            val s = alike(lyric[i - 1], heard[j - 1].word)
            val diag = score[i - 1][j - 1] + if (s >= 0.6) 2.0 * s else -1.0
            val up = score[i - 1][j] + gap; val left = score[i][j - 1] + gap
            if (diag >= up && diag >= left) { score[i][j] = diag; move[i][j] = 1 }
            else if (up >= left) { score[i][j] = up; move[i][j] = 2 }
            else { score[i][j] = left; move[i][j] = 3 }
        }
        var i = n; var j = m
        while (i > 0 && j > 0) {
            when (move[i][j].toInt()) {
                1 -> { if (alike(lyric[i - 1], heard[j - 1].word) >= 0.6) out[i - 1] = heard[j - 1].at; i--; j-- }
                2 -> i--
                else -> j--
            }
        }
        return out
    }

    /** The lyrics timed: (each sung line's start in seconds, the line), or null when too few of the words were heard to
     *  trust it. [length]: the song's length in seconds. */
    fun timeLines(plain: String, heard: List<Heard>, length: Double): List<Pair<Double, String>>? {
        val ls = lines(plain)
        if (ls.isEmpty()) return null
        val flat = ls.flatMap { it.second }
        val at = align(flat, heard)
        if (at.count { it != null } < MIN_HEARD * flat.size) return null
        // Each line's start: its first heard word, less the words before it in the line
        val starts = arrayOfNulls<Double>(ls.size)
        var w = 0
        for ((k, l) in ls.withIndex()) {
            val idx = (0 until l.second.size).firstOrNull { at[w + it] != null }
            if (idx != null) starts[k] = at[w + idx]!! - idx * WORD_SECS
            w += l.second.size
        }
        // A line timed before the one ahead of it is a word heard in the wrong place (a repeated chorus): dropped,
        // to be placed by counting like the lines not heard at all
        var last = Double.NEGATIVE_INFINITY
        for (k in starts.indices) { val s = starts[k] ?: continue; if (s <= last) starts[k] = null else last = s }
        val anchored = starts.indices.filter { starts[it] != null }
        if (anchored.size < max(2, (ls.size * MIN_HEARD).toInt())) return null
        // Word counts before each line, to place the lines in between
        val before = IntArray(ls.size + 1).also { for (k in ls.indices) it[k + 1] = it[k] + ls[k].second.size }
        for (k in ls.indices) {
            if (starts[k] != null) continue
            val p = anchored.lastOrNull { it < k }; val q = anchored.firstOrNull { it > k }
            starts[k] = when {
                p != null && q != null -> starts[p]!! + (starts[q]!! - starts[p]!!) * (before[k] - before[p]).toDouble() / (before[q] - before[p])
                p != null -> starts[p]!! + (before[k] - before[p]) * WORD_SECS
                else -> starts[q!!]!! - (before[q] - before[k]) * WORD_SECS
            }
        }
        // Shown a moment before they're sung, in order, inside the song
        val out = ArrayList<Pair<Double, String>>()
        var prev = -1.0
        for (k in ls.indices) {
            var s = (starts[k]!! - LEAD).coerceIn(0.0, max(0.0, length - 0.5))
            if (s <= prev) s = min(prev + 0.1, max(prev, length))
            out += s to ls[k].first; prev = s
        }
        return out
    }

    /** As an LRC file ("[mm:ss.xx]line", one a line). */
    fun lrc(lines: List<Pair<Double, String>>): String = lines.joinToString("\n") { (s, l) ->
        val cs = Math.round(s * 100); "[%02d:%02d.%02d]%s".format(cs / 6000, (cs / 100) % 60, cs % 100, l)
    }
}
