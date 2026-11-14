package org.omarchy.music

import kotlin.random.Random

/** The DJ's lines made to sound like a person talking, whatever voice says them: no symbols read out ("feat." is
 *  "featuring", "&" is "and"), numbers said the way people say them ("1,234" is "twelve hundred thirty-four", 2015
 *  is "twenty fifteen"), contractions ("you have been" is "you've been"), shorter sentences (a colon is a full
 *  stop), a comma to breathe before "but", and now and then a little "alright" or "okay so" up front. Pure text,
 *  so it's tested on its own. */
object Spoken {
    private val ONES = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
        "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    /** n (0 … 999,999,999) in words, as said out loud: 42 "forty-two", 1,500 "fifteen hundred", 2,345 "two thousand
     *  three hundred forty-five", 1,000,000 "a million". */
    fun words(n: Long): String {
        if (n < 0) return "minus " + words(-n)
        if (n < 20) return ONES[n.toInt()]
        if (n < 100) return TENS[(n / 10).toInt()] + (if (n % 10 != 0L) "-" + ONES[(n % 10).toInt()] else "")
        if (n < 1000) return ONES[(n / 100).toInt()] + " hundred" + (if (n % 100 != 0L) " " + words(n % 100) else "")
        // 1,100 … 1,999 and the like: "fifteen hundred", the way people say it, when it's whole hundreds
        if (n in 1100..1999 || (n in 2100..9999 && n % 100 == 0L && n % 1000 != 0L))
            return words(n / 100) + " hundred" + (if (n % 100 != 0L) " " + words(n % 100) else "")
        if (n < 1_000_000) return (if (n / 1000 == 1L) "a" else words(n / 1000)) + " thousand" + (if (n % 1000 != 0L) " " + words(n % 1000) else "")
        if (n < 1_000_000_000) return (if (n / 1_000_000 == 1L) "a" else words(n / 1_000_000)) + " million" +
            (if (n % 1_000_000 != 0L) " " + words(n % 1_000_000) else "")
        return n.toString()
    }

    private val ORDINALS = mapOf("one" to "first", "two" to "second", "three" to "third", "five" to "fifth", "eight" to "eighth",
        "nine" to "ninth", "twelve" to "twelfth")

    /** n as a place in a row: 1 "first", 25 "twenty-fifth", 40 "fortieth", 100 "one hundredth", 1,000 "one thousandth". */
    fun ordinal(n: Long): String {
        val w = words(n).let { if (it.startsWith("a ")) "one " + it.drop(2) else it }
        val cut = maxOf(w.lastIndexOf(' '), w.lastIndexOf('-')) + 1
        val last = w.substring(cut)
        return w.substring(0, cut) + (ORDINALS[last] ?: if (last.endsWith("y")) last.dropLast(1) + "ieth" else last + "th")
    }

    /** A year as said: 1999 "nineteen ninety-nine", 2008 "two thousand eight", 2015 "twenty fifteen", 1900 "nineteen hundred". */
    fun year(y: Int): String = when {
        y in 2000..2009 -> words(y.toLong())
        y % 100 == 0 -> words((y / 100).toLong()) + " hundred"
        y % 100 < 10 -> words((y / 100).toLong()) + " oh " + words((y % 100).toLong())
        else -> words((y / 100).toLong()) + " " + words((y % 100).toLong())
    }

    /** A decade as said: "80s" "eighties", "1990s" "nineteen nineties", "2010s" "twenty tens". */
    private fun decade(d: String): String {
        val n = d.dropLast(1).toInt()
        val tensWord = mapOf(0 to "hundreds", 10 to "tens", 20 to "twenties", 30 to "thirties", 40 to "forties", 50 to "fifties",
            60 to "sixties", 70 to "seventies", 80 to "eighties", 90 to "nineties")
        return if (n < 100) tensWord[n] ?: d
        else if (n in 2000..2009) "two thousands"
        else words((n / 100).toLong()) + " " + (tensWord[n % 100] ?: return d)
    }

    // Song-name clutter the DJ shouldn't read: "(Remastered 2011)", "- 2009 Remaster", "[Live]", "(Radio Edit)".
    private val CLUTTER = Regex("\\s*[(\\[][^)\\]]*\\b(remaster(ed)?|version|edit|mono|stereo|mix|live|deluxe|bonus|explicit|clean)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE)
    private val DASH_CLUTTER = Regex("\\s+-\\s+[^.,!?]*\\b(remaster(ed)?|version|edit|mono|stereo|live)\\b(\\s+\\d{4})?", RegexOption.IGNORE_CASE)

    private val CONTRACT = listOf(
        "\\b([Ii]t|[Tt]hat|[Hh]ere|[Tt]here|[Ww]hat|[Ww]ho|[Ss]he|[Hh]e) is (?=\\w)" to "$1's ",
        "\\b([Yy]ou|[Ww]e|[Tt]hey) are (?=\\w)" to "$1're ",
        "\\b(I) am (?=\\w)" to "$1'm ",
        "\\b([Yy]ou|[Ww]e|[Tt]hey|I) have (?=(been|played|heard|had|got|listened|spent|found|never|already|always|counted|starred|skipped|loved|probably|done|seen)\\b)" to "$1've ",
        "\\b([Ll])et us\\b" to "$1et's",
        "\\b([Dd])o not\\b" to "$1on't",
        "\\b([Dd])oes not\\b" to "$1oesn't",
        "\\b([Ii])s not\\b" to "$1sn't",
        "\\b([Cc])an not\\b|\\b([Cc])annot\\b" to "can't",
        "\\b([Hh])ave not\\b" to "$1aven't",
        "\\b([Yy]ou|[Ww]e|[Tt]hey|I) will (?=\\w)" to "$1'll ",
    ).map { Regex(it.first) to it.second }

    // Ordinary words a line can start with (lowercased after a filler); anything else may be a name.
    private val COMMON = setOf("here's", "here", "let's", "some", "something", "you", "you've", "you're", "your", "the", "these", "this",
        "that's", "time", "more", "from", "what", "what's", "lately", "around", "if", "only", "straight", "going",
        "same", "right", "riffing", "inspired", "spotlight", "back", "deep", "beyond", "genre", "shuffling", "dusting", "first",
        "hiding", "matching", "feeling", "let", "on", "a", "an", "all", "about", "now", "there's", "there", "it's", "fans",
        "new", "meet", "say", "trust", "rediscovery", "sing", "just", "one", "two", "three", "four", "five", "six", "seven",
        "eight", "nine", "ten", "number", "my", "we're", "we", "next", "up")
    private val FILLERS = listOf("Alright, ", "Okay so, ", "So, ", "Right, ", "Okay, ")
    // Lines that already open like someone talking get no filler.
    private val OPENS = Regex("^(hey|hi|hello|good|morning|evening|afternoon|still|rise|a late|it's late|okay|ok|alright|so|right|well|now|oh|yes|fun fact|did you know|real talk|heads up|throwback|remember|new music|favourites)\\b", RegexOption.IGNORE_CASE)

    /** text as the DJ should say it. filler: the chance (0–1) of a little "alright" up front. */
    fun humanize(text: String, filler: Double = 0.3, rng: Random = Random.Default): String {
        var s = " " + text.trim() + " "
        // Quotes and clutter out
        s = s.replace(Regex("[“”\"«»]"), "").replace(CLUTTER, "").replace(DASH_CLUTTER, "")
        // Names written in a way no voice reads right (AC/DC, CHVRCHES, R.E.M., U2), before their signs are touched
        s = SpeechPrep.names(s)
        // Symbols said as words
        s = s.replace(Regex("(?<=[\\s(\\[])(feat|ft)\\.?\\s", RegexOption.IGNORE_CASE), "featuring ")
            .replace(Regex("\\s*[(\\[]([^)\\]]*)[)\\]]"), ", $1,")   // "(featuring X)": said as an aside
            .replace(Regex("\\s[Ww]/\\s"), " with ")
            .replace(Regex("\\s*&\\s*"), " and ")
            .replace(Regex("\\s*\\+\\s+"), " plus ")
            .replace(Regex("#(\\d)"), "number $1")
            .replace(Regex("(\\d)\\s*%"), "$1 percent")
            .replace(Regex("\\s@\\s"), " at ")
            // Dots for a pause ("wait... what", a title like "...Baby One More Time") make a voice sound odd: a comma
            // (or the line's end) instead
            .replace("…", "...").replace(Regex("\\s*\\.{3,}(?=\\s*$)"), ".").replace(Regex("(?<=\\s)\\.{3,}(?=\\S)"), "").replace(Regex("\\s*\\.{3,}\\s*"), ", ").replace(Regex("\\s*[—–]\\s*"), ", ").replace(Regex("\\s*·\\s*"), ", ")
            .replace(Regex("(?<=\\p{L})/(?=\\p{L})"), " ")   // AC/DC: "AC DC"
            .replace(Regex("\\s*[*_~|<>{}\\[\\]^=]+\\s*"), " ")
        // Places in a row: "25th" is "twenty-fifth" (it was "twenty-five" with a "th" stuck on, said "twenty-five-ith")
        s = s.replace(Regex("(?<![\\d,.])(\\d{1,3}(?:,\\d{3})+|\\d+)(st|nd|rd|th)\\b", RegexOption.IGNORE_CASE)) { m ->
            m.groupValues[1].replace(",", "").toLongOrNull()?.let { ordinal(it) } ?: m.value
        }
        // Times: "10 AM" "ten in the morning"
        s = s.replace(Regex("\\b(\\d{1,2}) (AM|PM)\\b")) { m ->
            val h = m.groupValues[1].toInt()
            words(h.toLong()) + if (m.groupValues[2] == "AM") (if (h == 12) " at night" else " in the morning")
                else if (h == 12 || h < 5) " in the afternoon" else if (h < 9) " in the evening" else " at night"
        }
        // Decades, years, then other numbers
        s = s.replace(Regex("\\b((19|20)?\\d0)s\\b")) { decade(it.value) }
        s = s.replace(Regex("(?<![\\d,.])(19\\d\\d|20\\d\\d)(?![\\d,]|\\.\\d)")) { m ->
            // A year when it reads as one ("in 2015", "of 1999"), else a number ("2015 plays")
            val after = s.substring(m.range.last + 1).trimStart().lowercase()
            if (Regex("^(plays|times|songs|artists|minutes|hours|skips|scrobbles)\\b").containsMatchIn(after)) words(m.value.toLong()) else year(m.value.toInt())
        }
        s = s.replace(Regex("(?<![\\d.])(\\d{1,3}(,\\d{3})+|\\d+)(\\.(\\d+))?(?!\\d)")) { m ->
            val whole = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@replace m.value
            val frac = m.groupValues[4]
            when {
                frac.isEmpty() -> words(whole)
                frac == "5" -> if (whole == 0L) "half a" else words(whole) + " and a half"
                else -> words(whole) + " point " + frac.map { ONES[it - '0'] }.joinToString(" ")
            }
        }
        s = s.replace("half a hours", "half an hour")
        // Capitals: words said as words (HUMBLE), initials spelt on purpose (AJR), never by accident
        s = SpeechPrep.capitals(s)
        // Contractions
        for ((re, to) in CONTRACT) s = s.replace(re, to)
        // Shorter sentences: a colon or semicolon ends one; a breath before "but"
        s = s.replace(Regex("\\s*[:;]\\s+(\\p{L})")) { ". " + it.groupValues[1].uppercase() }
        s = s.replace(Regex("(?<![,.!?])\\s+but\\s"), ", but ")
        // Nothing left that a voice trips on: only letters, numbers, and a single full stop, comma, question or
        // exclamation mark, apostrophe or hyphen. A slash or a dash between words is a small pause (a comma); any
        // other sign still here (a slash in a title, a colon, a star) is a space; a full stop is never doubled.
        s = s.replace(Regex("\\s+[/\\\\|-]+\\s+"), ", ")
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s.,?!'’-]"), " ")
            .replace(Regex("(?<![\\p{L}\\p{N}])-+|-+(?![\\p{L}\\p{N}])"), " ")   // a hyphen only inside a word
        // Tidy spacing and punctuation
        s = s.replace(Regex("\\s+([,.!?])"), "$1").replace(Regex(",{2,}"), ",").replace(Regex(",(?=[.!?])"), "").replace(Regex("^,\\s*"), "").replace(Regex("\\s{2,}"), " ").trim()
        s = s.replace(Regex("([.?!])[.?!,]+"), "$1")
        if (s.isNotEmpty() && s.last().isLetterOrDigit()) s += "."
        s = s.replaceFirstChar { it.uppercase() }   // "4.5 hours" began a line: "Four and a half hours"
        // Now and then a little "alright" up front, never twice the same way of opening
        if (s.isNotEmpty() && filler > 0 && rng.nextDouble() < filler && !OPENS.containsMatchIn(s)) {
            val f = FILLERS[rng.nextInt(FILLERS.size)]
            // "Here's…" becomes "Alright, here's…"; a name ("Daft Punk, coming up") keeps its capital.
            val first = s.substringBefore(' ').trimEnd(',', '.', '!', '?')
            s = f + if (first.lowercase() in COMMON) s.replaceFirstChar { it.lowercase() } else s
        }
        return s
    }
}
