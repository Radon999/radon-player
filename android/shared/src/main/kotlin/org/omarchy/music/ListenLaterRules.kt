package org.omarchy.music

/** Listen later: a list for songs you want to hear but can't right now. Any songs picked (or the songs of albums and
 *  EPs picked) are put on it with "Add to Listen later playlist"; it has its own tab by Discovery. Unlike a discovery
 *  playlist it holds only what you put on it, not an artist's whole discography. */
object ListenLaterRules {
    const val NAME = "Listen later"

    /** What adding says: asked is how many you added, new how many of them weren't on it yet. */
    fun addedText(asked: Int, new: Int): String = when {
        asked <= 0 -> ""
        new == 0 -> if (asked == 1) "Already on Listen later" else "All %,d are on Listen later already".format(asked)
        asked == 1 -> "Added to Listen later"
        new < asked -> "%,d song%s added to Listen later (%,d %s on it)".format(new, if (new != 1) "s" else "", asked - new, if (asked - new == 1) "was" else "were")
        else -> "%,d songs added to Listen later".format(new)
    }

    /** What taking songs off it says. */
    fun removedText(n: Int): String = if (n == 1) "Taken off Listen later" else "%,d songs taken off Listen later".format(n)
}
