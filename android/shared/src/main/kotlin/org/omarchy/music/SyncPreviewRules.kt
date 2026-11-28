package org.omarchy.music

/** Preview a sync: what a Sync now would change on each device, said before anything is changed. Pure. */
object SyncPreviewRules {
    /** How long the PC waits for the phone to answer a preview asked from the PC (seconds). */
    const val WAIT = 60L

    /** Why the look couldn't be made, as a sentence for the box. */
    fun failed(why: String): String = why.trim().replaceFirstChar { it.uppercase() }.let { if (it.endsWith(".") || it.endsWith("?") || it.endsWith(")")) it else "$it." } +
        "\n\nNothing was changed."

    fun title(here: List<String>, there: List<String>): String {
        val n = here.size + there.size
        return if (n == 0) "A sync would change nothing" else "A sync would change $n thing${if (n != 1) "s" else ""}"
    }

    /** The lines of the dialog: each device's changes under its name; a device nothing changes on says so. */
    fun body(here: List<String>, there: List<String>, hereName: String, thereName: String, most: Int = 40): String {
        if (here.isEmpty() && there.isEmpty()) return "Both devices already have the same playlists, favorites, plays and settings."
        fun part(name: String, lines: List<String>) = "On $name:\n" + (if (lines.isEmpty()) "• nothing" else
            lines.take(most).joinToString("\n") { "• $it" } + if (lines.size > most) "\n• …and ${lines.size - most} more" else "")
        return part(hereName, here) + "\n\n" + part(thereName, there) + "\n\nNothing has been changed yet."
    }
}
