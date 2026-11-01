package org.omarchy.music

/** Words the natural voice (Kokoro) can say. It reads each word from its dictionary and skips one it doesn't know, so
 *  an artist like Seesyn or Juice WRLD came out as a gap ("because you've been playing … and …"). Each word it
 *  doesn't know is respelt here with words it does: a known stylised spelling (WRLD is "world"), a short name in
 *  capitals spelt out (UK), else pieces of the word that are in the dictionary ("see sin"), else the word's
 *  syllables spelt the way they sound. */
object Pronounce {
    /** Stylised spellings, as they're said. */
    private val SAID = mapOf("wrld" to "world", "thru" to "through", "nite" to "night", "luv" to "love", "tha" to "the",
        "da" to "duh", "xo" to "ex oh", "lil" to "lil", "yung" to "young", "boyz" to "boys", "girlz" to "girls",
        "gangsta" to "gangster", "nothin" to "nothing", "somethin" to "something", "feat" to "featuring", "ft" to "featuring",
        "vs" to "versus", "dj" to "D J", "mc" to "M C")

    /** text with every word known to the voice. known: the voice's dictionary (lowercase words); empty: as it is. */
    fun sayable(text: String, known: Set<String>): String {
        if (known.isEmpty()) return text
        return Regex("[A-Za-z][A-Za-z']*").replace(text) { m -> word(m.value, known) }
    }

    private fun has(w: String, known: Set<String>) = w.lowercase() in known

    fun word(w: String, known: Set<String>): String {
        if (has(w, known)) return w
        val low = w.lowercase()
        if ('\'' in low && has(low.substringBefore('\''), known)) return w   // you've, it's: as they are
        val bare = low.replace("'", "")
        if (has(bare, known)) return bare
        SAID[bare]?.let { return it }
        // A short name in capitals (UK, NF): its letters
        if (w.length in 2..4 && w.all { it.isUpperCase() } && w.all { has(it.toString(), known) }) return w.toCharArray().joinToString(" ")
        pieces(bare, known)?.let { return it }
        return syllables(bare, known)
    }

    /** The word as the fewest pieces (of 2 letters or more, or "a" / "i") that are words, each as written or spelt as
     *  it sounds (y as i, ph as f, z as s, a doubled letter once, k as c…); null when it can't be, or takes over 3. */
    private fun pieces(w: String, known: Set<String>): String? {
        val n = w.length
        if (n < 3) return null
        val best = arrayOfNulls<List<String>>(n + 1)
        best[0] = emptyList()
        for (i in 0 until n) {
            val sofar = best[i] ?: continue
            for (j in i + 1..n) {
                val p = w.substring(i, j)
                if (p.length < 2 && p != "a" && p != "i") continue
                val said = sounds(p).firstOrNull { it in known } ?: continue
                val cand = sofar + said
                if (best[j] == null || cand.size < best[j]!!.size) best[j] = cand
            }
        }
        return best[n]?.takeIf { it.size <= 3 }?.joinToString(" ")
    }

    /** p, and the ways it could be spelt that sound the same. */
    private fun sounds(p: String): List<String> {
        val out = LinkedHashSet<String>()
        out += p
        val swaps = listOf("y" to "i", "i" to "y", "ph" to "f", "z" to "s", "k" to "c", "c" to "k", "x" to "ks", "q" to "k",
            "ck" to "k", "ee" to "ea", "ea" to "ee", "oo" to "u", "w" to "u")
        for ((a, b) in swaps) if (a in p) out += p.replace(a, b)
        out += p.replace(Regex("(.)\\1"), "$1")
        if (!p.endsWith("e")) out += p + "e"
        return out.toList()
    }

    /** The word cut into syllables (each its consonants and the vowels after them), each said as a word that sounds
     *  like it, or its letters when nothing does. */
    private fun syllables(w: String, known: Set<String>): String {
        val parts = Regex("[^aeiouy]*[aeiouy]+(?:[^aeiouy]+$)?|[^aeiouy]+$").findAll(w).map { it.value }.toList().ifEmpty { listOf(w) }
        return parts.joinToString(" ") { s ->
            sounds(s).firstOrNull { it in known }
                ?: sounds(s).flatMap { listOf(it + "h", it + "w") }.firstOrNull { it in known }
                ?: s.toCharArray().filter { has(it.toString(), known) }.joinToString(" ")
        }.replace(Regex("\\s+"), " ").trim().ifEmpty { w }
    }
}
