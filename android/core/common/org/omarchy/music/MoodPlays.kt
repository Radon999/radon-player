package org.omarchy.music

/** Your plays by mood: every play the DJ counts (Last.fm, your Spotify, Apple Music and other imports, the plays
 *  here) of a song of yours whose mood is known (Moods), matched by Stats.statsKey. For the DJ's mood facts, and for
 *  any screen that wants plays per mood (Most played by mood). All calls block (run them in Music.bg); the plays are
 *  read again every ten minutes. The counting itself is [MoodTally]. */
object MoodPlays {
    /** Every play with a mood, oldest first. */
    fun all(): List<MoodPlay> = runCatching { Dj.moodPlays() }.getOrDefault(emptyList())

    /** Plays of each mood from fromEpoch (inclusive) to toEpoch (exclusive), in seconds. */
    fun counts(fromEpoch: Long, toEpoch: Long): Map<String, Int> = MoodTally.counts(all(), fromEpoch, toEpoch)

    /** Plays of each mood in the last days days. */
    fun lastDays(days: Int): Map<String, Int> { val t = now().toLong(); return counts(t - days * 86_400L, t + 1) }

    /** Your songs in mood, most played first (from fromEpoch to toEpoch), each with its plays. */
    fun topSongs(mood: String, fromEpoch: Long = Long.MIN_VALUE, toEpoch: Long = Long.MAX_VALUE, n: Int = 50): List<Pair<Track, Int>> {
        val c = HashMap<String, Int>()
        for (p in all()) if (p.mood == mood && p.t >= fromEpoch && p.t < toEpoch) c.merge(p.song, 1, Int::plus)
        return c.entries.sortedByDescending { it.value }.take(n).mapNotNull { (k, v) -> Dj.moodSong(k)?.let { it to v } }
    }
}
