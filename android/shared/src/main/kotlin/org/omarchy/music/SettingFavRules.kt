package org.omarchy.music

/** Favorite settings (Settings, the first section): the settings you use all the time, together in one place. A star
 *  beside a setting's name puts it there; it stays in its own section as well. Kept by name, one a line, in the order
 *  they were starred. */
object SettingFavRules {
    fun read(text: String): Set<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toCollection(LinkedHashSet())
    fun write(names: Set<String>): String = names.joinToString("\n")
    fun toggled(names: Set<String>, name: String): Set<String> {
        val n = name.trim().replace("\n", " ")
        return if (n.isEmpty()) names else if (n in names) names - n else names + n
    }
}
