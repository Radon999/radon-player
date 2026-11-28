package org.omarchy.music

/** The slow-frame log (Settings › Problems › Record slow frames): which frames count as slow, and what's said of a
 *  recording. Pure. A screen draws 60 times a second or more: a frame that took over twice as long as one of those is
 *  one you can see as a stutter. */
object FrameLogRules {
    /** Slow: a frame was skipped (ms). */
    const val SLOW_MS = 34
    /** Very slow: a freeze you'd notice (ms). */
    const val FREEZE_MS = 120
    /** Longer than this isn't a frame at all: the app was off the screen (ms). */
    const val AWAY_MS = 1500
    /** The recording stops by itself after this (it keeps the screen drawing, which costs battery). */
    const val RUN_MS = 10 * 60_000L
    /** The slowest ones kept, each with where it was. */
    const val KEEP = 25

    data class Slow(val ms: Int, val where: String)
    data class Log(val frames: Int = 0, val slow: Int = 0, val freezes: Int = 0, val worst: List<Slow> = emptyList()) {
        /** One more frame, ms after the one before it, on the screen called where. */
        fun plus(ms: Int, where: String): Log {
            if (ms <= 0 || ms > AWAY_MS) return this
            if (ms < SLOW_MS) return copy(frames = frames + 1)
            return Log(frames + 1, slow + 1, freezes + (if (ms >= FREEZE_MS) 1 else 0), (worst + Slow(ms, where)).sortedByDescending { it.ms }.take(KEEP))
        }
    }

    /** One line, for Settings. */
    fun line(l: Log): String = when {
        l.frames == 0 -> "Nothing recorded yet"
        l.slow == 0 -> "%,d frames, none slow".format(l.frames)
        else -> "%,d frames, %,d slow (%.1f%%)%s, the worst %d ms on %s".format(l.frames, l.slow, 100.0 * l.slow / l.frames,
            if (l.freezes > 0) ", %,d freeze%s".format(l.freezes, if (l.freezes != 1) "s" else "") else "", l.worst.first().ms, l.worst.first().where)
    }

    /** The whole of it, to copy and send. */
    fun report(l: Log, version: String, device: String): String = buildString {
        append("Radon Player $version · $device · slow frames\n").append(line(l)).append('\n')
        val by = l.worst.groupBy { it.where }.mapValues { (_, v) -> v.size to v.maxOf { it.ms } }.entries.sortedByDescending { it.value.second }
        if (by.isNotEmpty()) append("Where the slowest were: ").append(by.joinToString(", ") { "${it.key} (${it.value.first}, up to ${it.value.second} ms)" }).append('\n')
        l.worst.forEach { append("${it.ms} ms · ${it.where}\n") }
    }
}
