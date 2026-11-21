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
        while (lines.size > 40) lines.removeFirst()
    }

    private var hiccupAt = 0L
    /** Something that can be heard as a stutter or a gap (the output ran dry, was made again mid-song, another app
     *  took the sound for a moment): into the log, and into Settings › Problems too, with the song and where it was
     *  (once every 20 seconds at most), so a stutter you hear leaves a line you can send. */
    fun hiccup(what: String) {
        note(what)
        val now = System.currentTimeMillis()
        synchronized(this) { if (now - hiccupAt < 20_000) return; hiccupAt = now }
        runCatching {
            val t = Music.current()
            Crashes.note("A hiccup in the sound at ${Music.position.toInt()}s of “${t?.title}”: $what. Just before: ${text().takeLast(600)}")
        }
    }

    /** When real sound last left the app for the phone's sound output (the clock's milliseconds; 0: not known, as on
     *  the PC). A song silent to you while this is a moment ago went quiet after the app, inside Android. */
    @Volatile var outLoudAt = 0L
    fun leftApp(): String = outLoudAt.let { if (it == 0L) "not measured" else "${(System.currentTimeMillis() - it) / 1000} s ago" }

    @Synchronized fun text(): String = lines.joinToString("; ")
}
