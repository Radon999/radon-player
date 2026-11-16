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

    // ---- how the DJ says numbers and times ----
    /** A count, with its thousands apart: 1,234. */
    fun num(n: Int): String = String.format(java.util.Locale.ENGLISH, "%,d", n)

    /** A time as a month and its year: "March 2021". */
    fun month(seconds: Long, zone: java.time.ZoneId): String {
        val d = java.time.Instant.ofEpochSecond(seconds).atZone(zone)
        return d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + d.year
    }

    /** Hours of listening: whole from ten up ("12"), else to one place ("3.5", "4"). */
    fun hoursText(h: Double): String = if (h >= 10) String.format(java.util.Locale.ENGLISH, "%.0f", h)
        else String.format(java.util.Locale.ENGLISH, "%.1f", h).removeSuffix(".0")

    /** An hour of the day: "midnight", "noon", "3 PM". */
    fun hourName(h: Int): String = when (h) { 0 -> "midnight"; 12 -> "noon"; else -> "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}" }

    /** A place in a list, in words up to five ("Number three"), in figures after. */
    fun ordinal(n: Int): String = when (n) { 1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; else -> n.toString() }
}
