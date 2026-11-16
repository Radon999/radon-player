package org.omarchy.music

/** The rules of your picks in the stats (Joint artists, Similar artists, Merge into, Duplicates): how names are
 *  told to be the same, and what a credit becomes when one artist is merged into another. */
object StatPickRules {
    /** An artist's name with only its letters and digits left: how two spellings are told to be one name. */
    fun bare(name: String): String = norm(name).replace(Regex("[\\W_]+"), "")

    /** The key of a pair of similar artists you kept apart ("from>to"). */
    fun apartKey(from: String, to: String): String = bare(from) + ">" + bare(to)

    /** A song's credit after name is merged into to: the whole credit when it is name, else name's part of a joint
     *  credit ("A & B", "A x B", "A, B", "A;B"), with the rest and what joins them left as they are. */
    fun merged(credit: String, name: String, to: String): String {
        val k = bare(name)
        fun same(n: String) = bare(n) == k
        return if (same(credit)) to
            else credit.split(Regex("(?<=( & | x |, |;))|(?=( & | x |, |;))")).joinToString("") { if (same(it)) to else it }
    }

    /** What Merge into says at the end (n: songs retagged). */
    fun mergedText(name: String, to: String, n: Int): String =
        "$name is $to now" + if (n > 0) ": $n song${if (n != 1) "s" else ""} retagged" else ""

    /** The key of a group of copies marked "Not a duplicate": their addresses, sorted and joined. */
    fun dupeKey(addresses: List<String>): String = addresses.sorted().joinToString("\u0001")
}
