package org.omarchy.music

/** The last few things that happened to where and how loud the music sounds (the output it moved to, the DJ turning
 *  it down and up, a volume found left down), each with its time. Kept only while the app runs, and put in Settings ›
 *  Details for support: when the music goes silent while it plays on, this says what changed just before. */
object SoundLog {
    private val lines = ArrayDeque<String>()
    private val clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)

    @Synchronized fun note(what: String) {
        val line = clock.format(java.util.Date()) + " " + what
        if (lines.lastOrNull()?.substringAfter(' ') == what) return   // the same thing again: once
        lines.addLast(line)
        while (lines.size > 16) lines.removeFirst()
    }

    @Synchronized fun text(): String = lines.joinToString("; ")
}
