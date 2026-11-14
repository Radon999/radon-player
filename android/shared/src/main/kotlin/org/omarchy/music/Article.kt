package org.omarchy.music

private val AN_NUMBER = Regex("^(8|(11|18)(\\d\\d)?(\\D|$))")
private val A_ALL_THE_SAME = Regex("^(uni|use|usu|euro|ukulele|one(\\W|$)|once(\\W|$))")
private val AN_ALL_THE_SAME = Regex("^(hour|honest|honou?r|heir|r&b|rnb|r'n'b)")

/** "a" or "an" for the word that follows, by how it is said ("an alternative set", "an 80s set", "an R&B set",
 *  "a unique one"). The DJ's lines take a genre, a mood or a decade in that place. */
fun article(next: String): String {
    val w = next.trimStart().lowercase()
    val an = when {
        w.isEmpty() -> false
        w[0].isDigit() -> AN_NUMBER.containsMatchIn(w)
        A_ALL_THE_SAME.containsMatchIn(w) -> false
        AN_ALL_THE_SAME.containsMatchIn(w) -> true
        else -> w[0] in "aeiou"
    }
    return if (an) "an" else "a"
}

/** The word with its "a" or "an" before it; capital: to begin a sentence with. */
fun withArticle(word: String, capital: Boolean = false): String =
    article(word).let { if (capital) it.replaceFirstChar(Char::uppercaseChar) else it } + " " + word
