package org.omarchy.music

/** The "What's new" card: the first time the app opens after an update, a short card says so and names what the update
 *  brought (the bold headline of each item in the Changelog's section for this version), with a button to read it all.
 *  Shown once for each version. A fresh install shows nothing: there is no "before" to be new against. */
object WhatsNewRules {
    /** seen: the version the card was last shown (or the app first opened) for, "" on a fresh install. */
    fun show(seen: String, now: String): Boolean = seen.isNotBlank() && now.isNotBlank() && seen != now

    /** The headlines of this version's section of the changelog (its text, as the Changelog tab has it), at most [most]. */
    fun headlines(changelog: String, version: String, most: Int = 8): List<String> {
        val lines = changelog.lines()
        val start = lines.indexOfFirst { it.startsWith("## ") && it.removePrefix("## ").trim().let { h -> h == version || h.startsWith("$version ") || h.startsWith("$version:") } }
        if (start < 0) return emptyList()
        return lines.drop(start + 1).takeWhile { !it.startsWith("## ") }
            .mapNotNull { Regex("""^[-*]\s+\*\*(.+?)\*\*""").find(it.trim())?.groupValues?.get(1)?.trim()?.trimEnd(':') }
            .filter { it.isNotEmpty() }.take(most)
    }

    /** How many more there are than the card names. */
    fun more(changelog: String, version: String, shown: Int): Int = (headlines(changelog, version, Int.MAX_VALUE).size - shown).coerceAtLeast(0)
}
