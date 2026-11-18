package org.omarchy.music

/** Censored words in a title. One copy of a song is named "Just Don't Give a F**k" and another "Just Don't Give A
 *  Fuck": the same song, but as names they differ, so the app took your copy for a song you didn't have (it streamed
 *  instead of playing your file, and offered to download it again). When titles are compared, a censored word is read
 *  as the word it stands for: its letters that are there have to fit, the stars (or any other signs, or a gap) stand
 *  for the ones left out. Only the words that get censored are looked for, so an ordinary title with signs in it
 *  ("P!nk", "t-shirt", "N.P.D") is left as it is. Only for comparing: a title is always shown as it is written. */
object Censored {
    /** The words that get censored, the likeliest first (where two fit, "s**t", the first is taken). */
    private val WORDS = listOf("fuck", "shit", "bitch", "nigga", "fucking", "fuckin", "motherfucker", "motherfucking", "motherfuckin",
        "fucked", "fucker", "fucks", "bitches", "niggas", "bullshit", "shitty", "ass", "asshole", "dick", "pussy", "cock", "cunt",
        "damn", "goddamn", "hoe", "hoes", "whore", "slut", "bastard", "piss", "pissed", "dickhead", "hell", "crap", "sex", "sexy")

    // A word with signs standing in for letters: letters, then signs (and more letters) one or more times
    private const val SIGNS = "*#@\$!_•·✱∗×✕✖\\-"
    private val CANDIDATE = Regex("(?<![a-z0-9])[a-z]+(?:[$SIGNS]+[a-z]*)+(?![a-z0-9])")
    // Letters left out with a gap instead of signs ("f  k"): two or three spaces between a word's first and last letters
    private val GAP = Regex("(?<![a-z0-9])([a-z]{1,2})\\s{2,3}([a-z]{1,3})(?![a-z0-9])")
    private val QUICK = Regex("[a-z][$SIGNS]|[a-z]\\s{2,3}[a-z]")

    /** text (lowercase) with each censored word written out. */
    fun plain(text: String): String {
        if (!QUICK.containsMatchIn(text)) return text
        var t = GAP.replace(text) { m ->
            val joined = m.groupValues[1] + "*".repeat(2) + m.groupValues[2]
            word(joined) ?: m.value
        }
        t = CANDIDATE.replace(t) { m ->
            val c = m.value
            // (signs after the word are just what follows it, "sh*t!": tried without them, and put back)
            val core = c.trimEnd { !it.isLetter() }
            word(c) ?: (if (core.length < c.length) word(core)?.let { it + c.substring(core.length) } else null) ?: c
        }
        return t
    }

    /** The word a censored one stands for, or null (not one of the words that get censored). */
    fun word(censored: String): String? {
        if (censored.none { !it.isLetter() }) return null
        return WORDS.firstOrNull { fits(censored, 0, it, 0) }
    }

    /** Whether the censored word c (from i on) can be the word w (from j on): a letter is itself; a run of signs is
     *  one letter or more, up to one more than there are signs ("f*ck", "f**k", "f***", "f*k" are all "fuck"). */
    private fun fits(c: String, i: Int, w: String, j: Int): Boolean {
        if (i == c.length) return j == w.length
        if (c[i].isLetter()) return j < w.length && c[i] == w[j] && fits(c, i + 1, w, j + 1)
        var e = i
        while (e < c.length && !c[e].isLetter()) e++
        val run = e - i
        for (k in 1..minOf(run + 1, w.length - j)) if (fits(c, e, w, j + k)) return true
        return false
    }
}
