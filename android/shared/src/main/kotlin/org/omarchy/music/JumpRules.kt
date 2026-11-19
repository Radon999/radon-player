package org.omarchy.music

/** Jump to a letter: a long list that is in alphabetical order (songs, albums, artists, genres sorted by name) gets
 *  a strip of letters down its side; a tap on one (or a finger run down them) goes to where that letter begins.
 *  A list in any other order (by date, by plays) has no strip: letters would mean nothing there. */
object JumpRules {
    /** A list shorter than this needs no strip. */
    const val LEAST = 40

    /** A name's letter: its first letter (accents dropped, upper case), "#" for a digit or sign. A leading "The ", "A "
     *  or "An " is skipped only if [skipArticle], as some lists sort that way. */
    fun letter(name: String, skipArticle: Boolean = false): String {
        var s = name.trim()
        if (skipArticle) s = s.replaceFirst(Regex("^(?i)(the|a|an)\\s+"), "")
        val c = s.firstOrNull { it.isLetterOrDigit() } ?: return "#"
        if (!c.isLetter()) return "#"
        val plain = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD).firstOrNull { it.isLetter() } ?: c
        val up = plain.uppercaseChar()
        return if (up in 'A'..'Z') up.toString() else "#"
    }

    /** Where each letter starts in a list of n names (the letter, the place of its first name), or null: the list is
     *  too short, isn't in alphabetical order, or is nearly all one letter. Tried as the names are and with a leading
     *  article skipped; the order that fits better is used.
     *
     *  A real library is never perfectly in order by first letter: "(Don't Fear) The Reaper" is filed under the bracket,
     *  "Édith" after Z, names in other alphabets at the end, numbers at both ends. So a few names out of place are
     *  allowed (one in twenty): the names that ARE in order (the longest run of them going up) say where the letters
     *  start, and the rest are passed over. (Asking for perfect order, as 4.0.14 did, left real libraries with no strip.) */
    fun anchors(n: Int, name: (Int) -> String): List<Pair<String, Int>>? {
        if (n < LEAST) return null
        var best: List<Pair<String, Int>>? = null; var bestStrays = Int.MAX_VALUE
        for (skip in listOf(false, true)) {
            val ls = IntArray(n) { val l = letter(name(it), skip); if (l == "#") -1 else l[0] - 'A' }
            // The longest run of names whose letters only go up: per letter, the longest such run ending in it so far
            val len = IntArray(26); val end = IntArray(26) { -1 }; val prev = IntArray(n) { -1 }
            var lettered = 0
            for (i in 0 until n) {
                val c = ls[i]; if (c < 0) continue
                lettered++
                var b = 0; for (k in 1..c) if (len[k] > len[b]) b = k
                prev[i] = end[b].takeIf { len[b] > 0 } ?: -1
                len[c] = len[b] + 1; end[c] = i
            }
            var top = 0; for (k in 1..25) if (len[k] >= len[top]) top = k   // a tie: the later letter, so the strip reaches it
            if (len[top] == 0) continue
            val strays = lettered - len[top]
            if (strays > maxOf(3, lettered / 20)) continue
            val first = IntArray(26) { -1 }
            var i = end[top]; while (i >= 0) { first[ls[i]] = i; i = prev[i] }
            val out = ArrayList<Pair<String, Int>>()
            ls.indexOfFirst { it < 0 }.takeIf { it >= 0 }?.let { out += "#" to it }
            for (k in 0..25) if (first[k] >= 0) out += ('A' + k).toString() to first[k]
            out.sortBy { it.second }
            if (out.size >= 5 && strays < bestStrays) { best = out; bestStrays = strays }
        }
        return best
    }

    /** The same for a list in either direction: A to Z as [anchors] finds it, or Z to A (sorted by name, last first),
     *  where the strip then runs from Z down to A and each letter is where its names begin on the screen. */
    fun anchorsEitherWay(n: Int, name: (Int) -> String): List<Pair<String, Int>>? {
        anchors(n, name)?.let { return it }
        // Z to A: read from the bottom up it is A to Z. A letter that starts at j from the bottom ends at n-1-j from the
        // top, so it begins on the screen right after where the next letter (from the bottom) starts
        val up = anchors(n) { name(n - 1 - it) } ?: return null
        return up.indices.map { k -> up[k].first to if (k + 1 < up.size) n - up[k + 1].second else 0 }.sortedBy { it.second }
    }

    /** The row a place in the list is on: itself in a plain list, its row of tiles in a grid of [columns]. base: how
     *  many rows the screen has above the list. */
    fun row(base: Int, index: Int, columns: Int): Int = base + if (columns <= 1) index else index / columns
}
