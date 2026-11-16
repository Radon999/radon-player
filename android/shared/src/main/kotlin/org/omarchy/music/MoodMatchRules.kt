package org.omarchy.music

/** Moods match (Settings › Remote & sync): the moods of the songs on both devices made the same as on the one you
 *  pick, at each sync. It is apart from Tags match: the tags can follow one device and the moods the other. With it
 *  off, moods don't go across in a sync at all. */
object MoodMatchRules {
    /** This device takes the other's moods in a sync: Moods match is on and the other device is the main one
     *  (main, here: "pc" or "phone"). */
    fun takes(on: Boolean, main: String, here: String): Boolean = on && main != here

    /** This device's moods are the ones kept (it never takes the other's). */
    fun leads(on: Boolean, main: String, here: String): Boolean = on && main == here

    /** What Settings says under the switch. */
    fun note(on: Boolean, main: String, here: String, thisDevice: String, other: String, prevented: Boolean): String = when {
        !on -> ""
        takes(on, main, here) && prevented -> "Main device: the $other. Prevent auto mood scans is on, so nothing is taken: turn it off (Settings › Scanner) for its moods to come across"
        leads(on, main, here) -> "Main device: this $thisDevice. Its moods go to the $other at each sync"
        else -> "Main device: the $other. Its moods are put on this $thisDevice's songs at each sync"
    }

    /** What is said once the other device's moods are on the songs here. */
    fun takenText(songs: Int, files: Int, from: String): String =
        String.format(java.util.Locale.ENGLISH, "Moods from the $from given to %,d song%s", songs, if (songs != 1) "s" else "") +
            (if (files > 0) String.format(java.util.Locale.ENGLISH, ", written into %,d file%s", files, if (files != 1) "s" else "") else "")
}
