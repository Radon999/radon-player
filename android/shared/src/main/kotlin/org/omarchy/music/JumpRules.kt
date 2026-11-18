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
     *  article skipped; the order that fits is used. */
    fun anchors(n: Int, name: (Int) -> String): List<Pair<String, Int>>? {
        if (n < LEAST) return null
        for (skip in listOf(false, true)) {
            val out = ArrayList<Pair<String, Int>>(); var last = ""; var ok = true
            for (i in 0 until n) {
                val l = letter(name(i), skip)
                if (l == last) continue
                // "#" comes first or last, wherever the list puts its numbers; the letters themselves must go up
                if (l != "#" && last != "#" && last.isNotEmpty() && l < last) { ok = false; break }
                if (out.any { it.first == l }) { ok = false; break }
                out += l to i; last = l
            }
            if (ok && out.size >= 5) return out
        }
        return null
    }

    /** The row a place in the list is on: itself in a plain list, its row of tiles in a grid of [columns]. base: how
     *  many rows the screen has above the list. */
    fun row(base: Int, index: Int, columns: Int): Int = base + if (columns <= 1) index else index / columns
}
