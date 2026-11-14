package org.omarchy.music

/** Names and capitals made sayable before the DJ's voice gets a line (docs/rebuild/RADON_REBUILD_PLAN.md, 2.4), so it
 *  never spells a word out letter by letter by accident, nor reads a symbol: stylised names said as they're spoken
 *  (CHVRCHES is "Churches"), initials spelt on purpose (AJR is "A J R", R.E.M. is "R E M"), words in capitals said as
 *  words (HUMBLE is "Humble", NASA is "Nasa"), and a letter stuck to a number taken apart (U2 is "U 2", for the
 *  numbers to be put into words after). Pure text: [Spoken.humanize] runs [names] first and [capitals] last, whatever
 *  voice speaks. A name it gets wrong is one more line in [NAMES]. */
object SpeechPrep {
    /** Names no rule can read right: as written (whatever the capitals) to as said. Longer ones first. */
    private val NAMES = listOf(
        "Florence + the Machine" to "Florence and the Machine", "Ty Dolla \$ign" to "Ty Dolla Sign", "\$uicideboy\$" to "Suicide Boys",
        "XXXTENTACION" to "Ex Ex Ex Tentacion", "JPEGMAFIA" to "J Peg Mafia", "Juice WRLD" to "Juice World", "MSTRKRFT" to "Master Craft",
        "blink-182" to "Blink one eighty-two", "Sunn O)))" to "Sun", "CHVRCHES" to "Churches", "deadmau5" to "Dead Mouse",
        "SBTRKT" to "Subtract", "PVRIS" to "Paris", "3OH!3" to "Three Oh Three", "6LACK" to "Black", "AC/DC" to "A C D C",
        "Ke\$ha" to "Kesha", "A\$AP" to "A sap", "MGMT" to "M G M T", "INXS" to "In Excess", "WRLD" to "World", "P!nk" to "Pink",
        "R&B" to "R and B", "+44" to "Plus forty-four", "SZA" to "Sizza", "RZA" to "Rizza", "GZA" to "Jizza", "!!!" to "Chk Chk Chk",
        "No. 1" to "number one", "OK" to "okay",
    ).map { (written, said) -> Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(written) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE) to said }

    /** Roman numbers in names ("Boyz II Men", "Led Zeppelin IV"): only these, and only in capitals. */
    private val ROMAN = mapOf("II" to "two", "III" to "three", "IV" to "four", "VI" to "six", "VII" to "seven", "VIII" to "eight",
        "IX" to "nine", "XI" to "eleven", "XII" to "twelve")
    private val ROMAN_WORD = Regex("(?<![\\p{L}\\p{N}'])(II|III|IV|VI|VII|VIII|IX|XI|XII)(?![\\p{L}\\p{N}'])")
    private val DOTTED = Regex("(?<![\\p{L}\\p{N}])(?:\\p{L}\\.){2,}")                       // R.E.M., M.I.A.
    private val LETTER_NUMBER = Regex("(?<![\\p{L}\\p{N}])(\\p{L}{1,2})(\\d{1,3})(?![\\p{L}\\p{N}])")   // U2, M83, UB40

    /** Stylised names, dotted initials, Roman numbers and letter-number names, as said. Before anything else is done to
     *  the line (the names here are written with the signs that are otherwise read as words or dropped). */
    fun names(text: String): String {
        var s = text
        for ((written, said) in NAMES) s = written.replace(s) { said }
        s = DOTTED.replace(s) { m -> m.value.filter { it.isLetter() }.uppercase().toList().joinToString(" ") }
        s = ROMAN_WORD.replace(s) { ROMAN.getValue(it.value) }
        s = LETTER_NUMBER.replace(s) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        return s
    }

    private const val VOWELS = "AEIOU"
    /** Two and three letter words that are words when written in capitals (a song called "GO", "WHY"), not initials. */
    private val SHORT_WORDS = setOf("AM", "AN", "AS", "AT", "BE", "BY", "DO", "GO", "HE", "HI", "IF", "IN", "IS", "IT", "ME", "MY", "NO",
        "OF", "OH", "ON", "OR", "SO", "TO", "UP", "WE",
        "ACT", "AGE", "AIR", "ALL", "AND", "ANY", "ARE", "ART", "ASK", "BOY", "CRY", "DAY", "DRY", "EAT", "EGO", "END", "ERA", "EVE", "EYE",
        "FLY", "HER", "HIS", "HOW", "ICE", "ILL", "INK", "ITS", "JOY", "MAY", "NEW", "NOW", "ODD", "OFF", "OIL", "OLD", "ONE", "OUR", "OUT",
        "OWN", "SAY", "SHE", "SKY", "THE", "TRY", "TWO", "USE", "WAY", "WHO", "WHY", "YOU")
    private val CAPITALS = Regex("(?<![\\p{L}\\p{N}'])[A-Z]{2,}(?![\\p{L}\\p{N}'])")

    private fun word(w: String) = w.take(1) + w.drop(1).lowercase()
    private fun spelt(w: String) = w.toList().joinToString(" ")

    /** A run of capitals, as it should be said: a word ("Humble", "Nasa", "Go") or its letters ("A J R", "D N A").
     *  Two letters: letters, but for the short words. Three: a word when it's one of the short words or reads as one
     *  (consonant, vowel, consonant: NAS, WAR), else letters (AJR, ELO, DNA, BTS). Longer: a word when it has a vowel,
     *  letters when it has none and is five letters at most (RHCP), else as it is. */
    fun capital(w: String): String = when {
        w in SHORT_WORDS -> word(w)
        w.length == 2 -> spelt(w)
        w.length == 3 -> if (w[0] !in VOWELS && w[1] in VOWELS && w[2] !in VOWELS) word(w) else spelt(w)
        w.any { it in VOWELS || it == 'Y' } -> word(w)
        w.length <= 5 -> spelt(w)
        else -> w
    }

    /** Every run of capitals left in the line, as said (see [capital]). After times and numbers are in words (so the
     *  PM of "10 PM" is gone by now). */
    fun capitals(text: String): String = CAPITALS.replace(text) { capital(it.value) }
}
