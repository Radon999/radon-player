package org.omarchy.music

/** Your plays by mood: every play the DJ counts (Last.fm, your Spotify, Apple Music and other imports, the plays
 *  here) of a song of yours whose mood is known (Moods), matched by Stats.statsKey. For the DJ's mood facts, and for
 *  any screen that wants plays per mood (Most played by mood). All calls block (run them in Music.bg); the plays are
 *  read again every ten minutes. The counting itself is [MoodTally]. */
object MoodPlays {
    /** Every play with a mood, oldest first. */
    fun all(): List<MoodPlay> = runCatching { TasteService.moodPlays() }.getOrDefault(emptyList())

    /** Plays of each mood from fromEpoch (inclusive) to toEpoch (exclusive), in seconds. */
    fun counts(fromEpoch: Long, toEpoch: Long): Map<String, Int> = MoodTally.counts(all(), fromEpoch, toEpoch)

}
