package org.omarchy.music

/** Settings › Personalization › Colours: the colour you set for one visualizer, kept as "style=colour;…" (a Dual
 *  one's bottom part as "myvis-3#bottom"). "" for a style: none set (it comes as it was made). */
object VisColourRules {
    fun get(text: String, style: String): String = text.split(';')
        .firstNotNullOfOrNull { e -> e.split('=', limit = 2).takeIf { it.size == 2 && it[0] == style }?.get(1) }.orEmpty()

    /** [text] with [style]'s colour set to [color] ("" takes it off, so it comes as it was made). */
    fun set(text: String, style: String, color: String): String =
        (text.split(';').filter { it.isNotBlank() && it.substringBefore('=') != style } +
            listOfNotNull(color.takeIf { it.isNotEmpty() }?.let { "$style=$it" })).joinToString(";")
}
