package org.omarchy.music

/** The last few things that happened to where and how loud the music sounds (the output it moved to, the DJ turning
 *  it down and up, a volume found left down), each with its time. Kept only while the app runs, and put in Settings ›
 *  Details for support: when the music goes silent while it plays on, this says what changed just before. */
object SoundLog {
    private val lines = ArrayDeque<String>()
    private val clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)

    @Synchronized fun note(what: String) {
        val line = clock.format(java.util.Date()) + " " + what
        // A device coming or going, or the app moving the music to another output: the output made again is that
        if (ROUTING.any { what.startsWith(it) }) deviceAt = System.currentTimeMillis()
        if (lines.lastOrNull()?.substringAfter(' ') == what) return   // the same thing again: once
        lines.addLast(line)
        while (lines.size > 40) lines.removeFirst()
    }

    private val ROUTING = listOf("connected:", "music kept on", "music left where", "sound on ")
    private var deviceAt = 0L                    // when a device last came or went, or the music was moved (see note)
    private val recent = ArrayDeque<Long>()      // when the last hiccups were, those counted
    private var keptAt = 0L                      // when Problems was last told the sound kept cutting out
    /** Something that can be heard as a stutter or a gap (the output ran dry, was made again mid-song, another app took
     *  the sound for a moment, the output reported a fault it got over): into this log, which Settings › Details shows.
     *  Each one alone the music gets over by itself, so it isn't a problem: Settings › Problems hears of it only when the
     *  sound keeps cutting out ([KEPT_CUTTING]: so many within a minute), once in five minutes at most. One within a few
     *  seconds of a device coming or going (earbuds in, the car connecting) is the sound moving, and isn't counted. */
    fun hiccup(what: String) {
        note(what)
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - deviceAt < 5_000) return
            recent.addLast(now)
            while (recent.isNotEmpty() && now - recent.first() > 60_000) recent.removeFirst()
            if (recent.size < KEPT_CUTTING || now - keptAt < 5 * 60_000) return
            keptAt = now; recent.clear()
        }
        runCatching {
            val t = Music.current()
            Crashes.note("The sound kept cutting out around ${Music.position.toInt()}s of “${t?.title}” ($KEPT_CUTTING hiccups in a minute), the last: $what. Just before: ${text().takeLast(600)}")
        }
    }
    private const val KEPT_CUTTING = 3

    /** When real sound last left the app for the phone's sound output (the clock's milliseconds; 0: not known, as on
     *  the PC). A song silent to you while this is a moment ago went quiet after the app, inside Android. */
    @Volatile var outLoudAt = 0L
    fun leftApp(): String = outLoudAt.let { if (it == 0L) "not measured" else "${(System.currentTimeMillis() - it) / 1000} s ago" }

    @Synchronized fun text(): String = lines.joinToString("; ")
}
