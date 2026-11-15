package org.omarchy.music

/** How a DJ's break is put together from its parts: the set's own line, what may come before it (a hello on the first
 *  set, a word about the day, the thread from the song before) and the fact after it. A break is [MOST_WORDS] words
 *  at most, counted as said (numbers and signs as words). What the parts say is the DJ's; this is only what fits. */
object ScriptRules {
    const val MOST_WORDS = 45
    private val SPACES = Regex("\\s+")

    /** How many words a text is when said. */
    fun wordsSaid(text: String): Int = if (text.isBlank()) 0 else Spoken.humanize(text, filler = 0.0).split(SPACES).count { it.isNotBlank() }

    /** The set's line with its opener before it: (the opener kept, the line). A word about the day goes when the break
     *  would be over the most; the first set's hello always stays. */
    fun opened(opener: String, setLine: String, first: Boolean): Pair<String, String> {
        val line = if (opener.isEmpty()) setLine else "$opener $setLine"
        return if (!first && opener.isNotEmpty() && wordsSaid(line) > MOST_WORDS) "" to setLine else opener to line
    }

    /** Whether there's room for the fact after the line (none to say always fits). */
    fun factFits(line: String, fact: String) = fact.isEmpty() || wordsSaid(line) + wordsSaid(fact) <= MOST_WORDS

    /** Whether the thread from the song before can be said ahead of the line and its fact. */
    fun threadFits(thread: String, line: String, fact: String) =
        thread.isNotEmpty() && wordsSaid(thread) + wordsSaid(line) + wordsSaid(fact) <= MOST_WORDS

    /** A break made ahead (in the part of the day madeKey), as it should be said in nowKey: (the line, its opener), or
     *  null when it stays as it is (no opener, or still the same part of the day). The first set gets a new hello; any
     *  other set loses its word about the day. */
    fun asOfNow(line: String, opener: String, first: Boolean, madeKey: String, nowKey: String, hello: () -> String): Pair<String, String>? {
        if (opener.isEmpty() || madeKey.isEmpty() || madeKey == nowKey) return null
        val body = line.removePrefix(opener).trim()
        val o = if (first) hello() else ""
        return (if (o.isEmpty()) body else "$o $body") to o
    }
}
