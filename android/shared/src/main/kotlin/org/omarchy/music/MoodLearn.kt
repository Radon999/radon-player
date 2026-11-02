package org.omarchy.music

import kotlin.math.sqrt

/** A song as learning from your fixes sees it: [key] finds it again (its file), [artist] its lead artist (tidied, ""
 *  none), [sound] what it sounds like (null: not heard). */
data class LearnSong(val key: String, val artist: String, val sound: SoundFeatures?)

/** A fix you made in the Tagger: the song, the moods you gave it that weren't worked out for it ([added]), and the
 *  ones worked out that you took off ([removed]). */
data class LearnFix(val song: LearnSong, val added: Set<String>, val removed: Set<String>)

/** Learning from your fixes (pure, no app): when you set a song's moods yourself, the songs that sound most like it
 *  (its nearest few, and only ones that are really close) and the same artist's other songs get a small nudge the same
 *  way: a mood you added scores a bit higher for them, one you took off a bit lower. Each song's nudges add up but stay
 *  small ([CAP]), so they tip a song that was nearly in a mood (or nearly out of it) and never outweigh its own clear
 *  evidence (see [MoodRules.judge]). Songs whose moods you set yourself get no nudge: yours stand as they are. */
object MoodLearn {
    /** How much one fix nudges a song that sounds just like it (less the further it is). */
    const val STEP = 0.08
    /** How much one fix nudges the same artist's other songs. */
    const val ARTIST_STEP = 0.04
    /** The most a song's mood is nudged, up or down, by all fixes together. */
    const val CAP = 0.15
    /** How many of the nearest songs a fix nudges. */
    const val K = 6
    /** How close (in [distance]) a song must sound to be nudged at all. */
    const val NEAR = 0.3

    /** A fix from what you picked and what had been worked out (the moods you added, and the ones you took off). */
    fun fix(song: LearnSong, picked: Collection<String>, worked: Collection<String>): LearnFix {
        val p = picked.filter { it in MoodRules.ALL }.toSet(); val w = worked.filter { it in MoodRules.ALL }.toSet()
        return LearnFix(song, p - w, w - p)
    }

    private fun clamp(v: Double) = v.coerceIn(0.0, 1.0)

    /** A sound as a few numbers, each 0–1 (loudness, tempo, beat, brightness, busyness, key, dynamics), for [distance]. */
    fun vector(f: SoundFeatures): DoubleArray = doubleArrayOf(
        clamp((f.loud + 28) / 18),
        if (f.bpm <= 0) 0.5 else clamp((f.bpm - 60) / 120),
        clamp(f.beat * 2),
        clamp((f.bright - 1000) / 2500),
        clamp(f.onsets / 5),
        clamp((f.major + 1) / 2),
        clamp((f.range - 6) / 14),
    )

    /** How different two sounds are: 0 the same … about 1 nothing alike (the root mean square of [vector]'s gaps, ×2). */
    fun distance(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in a.indices) { val d = a[i] - b[i]; s += d * d }
        return 2 * sqrt(s / a.size)
    }

    /** Every song's nudges from all the fixes: song key to (mood to nudge, -[CAP]…[CAP]); songs with none left out. */
    fun biases(songs: List<LearnSong>, fixes: List<LearnFix>): Map<String, Map<String, Double>> {
        val useful = fixes.filter { it.added.isNotEmpty() || it.removed.isNotEmpty() }
        if (useful.isEmpty()) return emptyMap()
        val fixed = fixes.mapTo(HashSet()) { it.song.key }
        val others = songs.filter { it.key !in fixed }.distinctBy { it.key }
        val heard = others.mapNotNull { s -> s.sound?.takeIf { it.loud > -60 }?.let { s to vector(it) } }
        val byArtist = others.filter { it.artist.isNotEmpty() }.groupBy { it.artist }
        val out = HashMap<String, HashMap<String, Double>>()
        fun nudge(key: String, f: LearnFix, k: Double) {
            val m = out.getOrPut(key) { HashMap() }
            for (a in f.added) m.merge(a, k, Double::plus)
            for (r in f.removed) m.merge(r, -k, Double::plus)
        }
        for (f in useful) {
            // The songs that sound most like it: the nearest K that are close enough, the closer the more.
            f.song.sound?.takeIf { it.loud > -60 }?.let { vector(it) }?.let { v ->
                heard.map { (s, w) -> s to distance(v, w) }.filter { it.second < NEAR }.sortedBy { it.second }.take(K)
                    .forEach { (s, d) -> nudge(s.key, f, STEP * (1 - d / NEAR)) }
            }
            // The same artist's songs, a little.
            if (f.song.artist.isNotEmpty()) byArtist[f.song.artist]?.forEach { nudge(it.key, f, ARTIST_STEP) }
        }
        return out.mapValues { (_, m) -> m.mapValues { Math.round(it.value.coerceIn(-CAP, CAP) * 1000) / 1000.0 }.filterValues { it != 0.0 } }
            .filterValues { it.isNotEmpty() }
    }
}
