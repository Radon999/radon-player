package org.omarchy.music

/** Copy everything for support (Settings › General): one block of text to paste into a message when something is
 *  wrong, so nobody has to ask "which version? which phone? what does Problems say?" one question at a time. It holds
 *  what the app is and how it is set up, then what went wrong lately. Never a sign-in, a key, a token, a folder's
 *  path or the name of a song: nothing in it says who you are or what you listen to. */
object SupportRules {
    /** facts: what to say, in order (a blank value is left out). problems: Settings › Problems' text ("": none). */
    fun report(facts: List<Pair<String, String>>, problems: String): String = buildString {
        append("Radon Player: details for support\n")
        for ((k, v) in facts) if (v.isNotBlank()) append(k).append(": ").append(v.trim().replace('\n', ' ')).append('\n')
        append("\nProblems\n")
        append(if (problems.isBlank()) "None noted." else problems.trim().take(12_000))
        append('\n')
    }

    /** How many problems Settings › Problems' text holds (each starts with a line "18 Nov 2026, 08:03:11 · 4.0.14 · what"). */
    fun count(problems: String): Int = problems.lineSequence().count { STAMP.containsMatchIn(it) }
    private val STAMP = Regex("^\\d{1,2} \\p{L}{3,} \\d{4}, \\d{2}:\\d{2}:\\d{2} · ")

    /** What the Details for support line says is in it now, after its description. */
    fun holds(problems: String): String = when (val n = count(problems)) {
        0 -> if (problems.isBlank()) "No problems are noted in it now" else "It holds what Problems says now; Clear empties that, for once it is fixed"
        1 -> "It holds 1 problem noted; Clear empties that, for once it is fixed"
        else -> "It holds $n problems noted; Clear empties them, for once they are fixed"
    }

    /** A number of bytes as people say it: "512 MB", "3.4 GB". */
    fun size(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1_000_000L -> "%,d KB".format((bytes + 999) / 1000)
        bytes < 1_000_000_000L -> "%,d MB".format(bytes / 1_000_000)
        else -> "%.1f GB".format(bytes / 1_000_000_000.0)
    }

    /** Whether to warn that the download folder is nearly full: under 1 GB free, or under 3% of the disk. */
    fun lowSpace(free: Long, total: Long): Boolean = free >= 0 && (free < 1_000_000_000L || (total > 0 && free * 100 / total < 3))
    fun lowSpaceText(free: Long): String = "Only ${size(free)} free where your downloads go: new downloads may fail. Free some space, or pick another download folder in Settings › Your music"
}
