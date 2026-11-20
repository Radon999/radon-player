package org.omarchy.music

/** The same recording under another artist's name. A history can name a song by someone who isn't on your file of it
 *  (an unreleased song put up under an uploader's name; a play whose artist had to be guessed): the name is the same and
 *  so is the length, to the second. Of your songs of that name, the only one of that length is taken to be it; with none,
 *  or two, nothing is. */
object SameLengthRules {
    /** How far apart two lengths of one recording may be, in seconds (a history rounds to the second). */
    const val WITHIN = 1.5
    /** Nothing shorter than this is matched by its length (seconds): a skit or an intro is too easily the same length. */
    const val SHORTEST = 30.0

    /** A song's length in seconds, from how long each play of it in a history ran (ms): what most of them ran to
     *  (a play let run to its end is the song's length; one skipped partway is its own odd number), the longer of
     *  two as common. 0 with nothing to go by. */
    fun usual(ms: List<Long>): Double {
        val whole = ms.filter { it in 30_000L..3_600_000L }
        if (whole.isEmpty()) return 0.0
        val best = whole.groupBy { (it + 500) / 1000 }.entries.maxWith(compareBy<Map.Entry<Long, List<Long>>>({ it.value.size }, { it.key }))
        return best.value.average() / 1000.0
    }

    /** Which of your songs of the same name (their lengths in seconds) is this recording: the only one within
     *  [WITHIN] of secs; -1 when none is, when more than one is, or when the length isn't known. */
    fun pick(secs: Double, lengths: List<Double>): Int {
        if (secs < SHORTEST) return -1
        val fits = lengths.indices.filter { lengths[it] >= SHORTEST && kotlin.math.abs(lengths[it] - secs) <= WITHIN }
        return if (fits.size == 1) fits[0] else -1
    }
}
