package org.omarchy.music

/** Artist names that stay whole (Settings › Statistics › Names that stay whole): a credit is split into its artists at
 *  commas, "&" and "feat.", which is right for "Post Malone, Swae Lee" and wrong for "Tyler, The Creator" or "Earth,
 *  Wind & Fire". The names here are never split: the well-known ones the app comes with, and the ones you add. */
object WholeArtists {
    const val PREF = "artists_whole"   // yours, one a line
    val BUILT_IN = listOf("Tyler, The Creator", "Earth, Wind & Fire", "Crosby, Stills & Nash", "Crosby, Stills, Nash & Young",
        "Emerson, Lake & Palmer", "Peter, Paul and Mary", "Blood, Sweat & Tears", "Now, Now")

    @Volatile private var names: List<String> = tidy(BUILT_IN)
    private fun tidy(l: List<String>) = l.map { it.trim().lowercase() }.filter { it.length > 2 }.distinct().sortedByDescending { it.length }

    fun parse(text: String): List<String> = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** Your names (with the app's own), used from now on; the keys worked out with the old ones are forgotten. */
    fun set(yours: List<String>) { names = tidy(BUILT_IN + yours); forgetNameKeys() }

    private val TOKEN = Regex("\u0001(\\d+)\u0002")

    /** credit split by split, with every whole name in it kept in one piece (as it is written there). */
    fun split(credit: String, split: (String) -> List<String>): List<String> {
        var text = credit
        val kept = ArrayList<String>()
        for (n in names) {
            var i = text.indexOf(n, ignoreCase = true)
            while (i >= 0) {
                val end = i + n.length
                // (a name of its own, not a piece of a longer word)
                if ((i == 0 || !text[i - 1].isLetterOrDigit()) && (end == text.length || !text[end].isLetterOrDigit())) {
                    kept += text.substring(i, end)
                    text = text.substring(0, i) + "\u0001${kept.size - 1}\u0002" + text.substring(end)
                    i = text.indexOf(n, ignoreCase = true)
                } else i = text.indexOf(n, end, ignoreCase = true)
            }
        }
        if (kept.isEmpty()) return split(credit)
        return split(text).map { p -> TOKEN.replace(p) { m -> kept[m.groupValues[1].toInt()] } }
    }

    /** Whether the name is one that stays whole. */
    fun has(name: String): Boolean = name.trim().lowercase() in names
}
