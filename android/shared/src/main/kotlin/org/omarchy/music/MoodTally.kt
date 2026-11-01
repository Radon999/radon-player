package org.omarchy.music

/** One play of a song whose mood is known: when (seconds), its mood, and which song (any key, e.g. "title|artist"). */
data class MoodPlay(val t: Long, val mood: String, val song: String = "")

/** Counting plays by mood (pure, no app): how much of each mood you played in a time span, how that changed, which
 *  part of the day you go for a mood, and a mood you've drifted away from. The DJ's mood facts are made from these;
 *  the app's side is [MoodPlays] (it gives the plays). */
object MoodTally {
    /** Plays of each mood from fromEpoch (inclusive) to toEpoch (exclusive), seconds. */
    fun counts(plays: List<MoodPlay>, fromEpoch: Long, toEpoch: Long): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (p in plays) if (p.t >= fromEpoch && p.t < toEpoch) out.merge(p.mood, 1, Int::plus)
        return out
    }

    /** The mood played most in counts, with its plays; null when counts has fewer than minPlays in all. */
    fun top(counts: Map<String, Int>, minPlays: Int = 1): Pair<String, Int>? {
        if (counts.values.sum() < minPlays) return null
        return counts.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.toPair()
    }

    /** Percent (0–100, rounded down) of counts that mood is; 0 when counts is empty. */
    fun percent(counts: Map<String, Int>, mood: String): Int {
        val all = counts.values.sum()
        return if (all == 0) 0 else (counts[mood] ?: 0) * 100 / all
    }

    /** The mood that grew most between before and now, as a share of each span's plays: (mood, percent before,
     *  percent now). Only when both spans have at least minPlays, the mood is up by at least minPoints percentage
     *  points and to at least 1.5 times its old share. Null when nothing shifted that much. */
    fun shift(now: Map<String, Int>, before: Map<String, Int>, minPlays: Int = 20, minPoints: Int = 8): Triple<String, Int, Int>? {
        val a = now.values.sum(); val b = before.values.sum()
        if (a < minPlays || b < minPlays) return null
        return now.keys.mapNotNull { m ->
            val p = (now[m] ?: 0) * 100.0 / a
            val q = (before[m] ?: 0) * 100.0 / b
            if (p - q >= minPoints && p >= 1.5 * q) Triple(m, q.toInt(), p.toInt()) to (p - q) else null
        }.maxByOrNull { it.second }?.first
    }

    /** Parts of the day, by hour (0–23): "morning" 5–11, "afternoon" 12–16, "evening" 17–21, "late night" 22–4. */
    fun partOfDay(hour: Int): String = when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..21 -> "evening"; else -> "late night" }

    /** For each mood with at least minPlays, the part of the day it leans to most: the part where that mood's share of
     *  its plays is highest compared with all your plays' share in that part, at least 1.4 times as much. hourOf gives
     *  a play's local hour. Mood to (part, how many times more than usual). */
    fun leanings(plays: List<MoodPlay>, hourOf: (Long) -> Int, minPlays: Int = 20): Map<String, Pair<String, Double>> {
        val allByPart = HashMap<String, Int>()
        val byMood = HashMap<String, HashMap<String, Int>>()
        for (p in plays) {
            val part = partOfDay(hourOf(p.t))
            allByPart.merge(part, 1, Int::plus)
            byMood.getOrPut(p.mood) { HashMap() }.merge(part, 1, Int::plus)
        }
        val total = plays.size.toDouble()
        val out = HashMap<String, Pair<String, Double>>()
        for ((m, parts) in byMood) {
            val n = parts.values.sum()
            if (n < minPlays) continue
            val best = parts.entries.map { (part, c) -> part to (c / n.toDouble()) / ((allByPart[part] ?: 1) / total) }
                .maxByOrNull { it.second } ?: continue
            if (best.second >= 1.4 && (parts[best.first] ?: 0) >= 5) out[m] = best
        }
        return out
    }

    /** A mood you used to play (at least minShare percent of allTime, and minPlays of it) that's down to under a third
     *  of that share lately (recent has at least minRecent plays). The most fallen first; null if none. */
    fun neglected(allTime: Map<String, Int>, recent: Map<String, Int>, minShare: Int = 6, minPlays: Int = 30, minRecent: Int = 30): String? {
        val a = allTime.values.sum(); val r = recent.values.sum()
        if (a == 0 || r < minRecent) return null
        return allTime.entries.filter { (m, n) ->
            n >= minPlays && n * 100.0 / a >= minShare && (recent[m] ?: 0) * 100.0 / r < n * 100.0 / a / 3
        }.maxByOrNull { (m, n) -> n * 100.0 / a - (recent[m] ?: 0) * 100.0 / r }?.key
    }

    /** The song played most in mood from fromEpoch to toEpoch: (song key, plays); null when none has minPlays. */
    fun topSong(plays: List<MoodPlay>, mood: String, fromEpoch: Long = Long.MIN_VALUE, toEpoch: Long = Long.MAX_VALUE, minPlays: Int = 1): Pair<String, Int>? {
        val c = HashMap<String, Int>()
        for (p in plays) if (p.mood == mood && p.song.isNotEmpty() && p.t >= fromEpoch && p.t < toEpoch) c.merge(p.song, 1, Int::plus)
        return c.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.takeIf { it.value >= minPlays }?.toPair()
    }

    /** The moods to try for the next mood mix, best first: the ones not played in a mood mix lately first (all moods
     *  come round before one repeats), then the others, the stalest first, so the last one played comes last of all
     *  (never the same mood twice running while there's another). usable: moods to choose from; history: the mood
     *  mixes' moods so far, oldest first. */
    fun moodOrder(usable: List<String>, history: List<String>, rng: kotlin.random.Random = kotlin.random.Random.Default): List<String> {
        val distinct = usable.distinct()
        if (distinct.isEmpty()) return emptyList()
        return NoRepeat.order(distinct, history, NoRepeat.window(distinct.size - 1, distinct.size, 1), rng)
    }
}
