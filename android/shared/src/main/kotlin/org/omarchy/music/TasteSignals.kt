package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.sqrt

/** What you've told the app about a song or an artist, and what it saw you do, beyond plays and skips (SongStats):
 *  kept by key ("title\tartist" from [statsKey] for a song, "artist:" + its lead name for an artist), in taste.json,
 *  which syncs. up/down: when you said More like this / Less like this (seconds, 0: never); noAuto: when you said Not
 *  for autoplay (never put in by autoplay, radios or the DJ); early: when you skipped it in its first 30 seconds;
 *  intro*: a song new to you that the DJ or autoplay brought, and what came of it ("kept": saved, favourited or put
 *  in a playlist; "heard": heard to the end; "skipped"). */
@Serializable
data class SignalEntry(val title: String = "", val artist: String = "", val up: Long = 0, val down: Long = 0, val noAuto: Long = 0,
                       val early: List<Long> = emptyList(), val introAt: Long = 0, val introVia: String = "", val introOut: String = "")

object SignalRules {
    const val EARLY_SECS = 30.0
    const val DAY = 86_400L

    fun songKey(title: String, artist: String): String = statsKey(title, artist).let { it.first + "\t" + it.second }
    fun artistKey(artist: String): String = "artist:" + statsKey("", artist).second

    /** Skipped in its first seconds this often lately (60 days): you don't want it now. */
    fun earlyLately(e: SignalEntry?, now: Long): Int = e?.early?.count { now - it < 60 * DAY } ?: 0

    /** Your latest word on it: 1 More like this, -1 Less like this, 0 neither. */
    fun thumb(e: SignalEntry?): Int = when { e == null -> 0; e.up > e.down -> 1; e.down > e.up -> -1; else -> 0 }

    /** An early skip written down (the last 30 kept). */
    fun withEarly(e: SignalEntry, at: Long) = e.copy(early = (e.early + at).takeLast(30))

    /** A new song's outcome, the best one kept: kept beats heard beats skipped (a song you saved after skipping it once
     *  is a keeper). */
    fun outcome(old: String, new: String): String {
        fun rank(s: String) = when (s) { "kept" -> 3; "heard" -> 2; "skipped" -> 1; else -> 0 }
        return if (rank(new) >= rank(old)) new else old
    }

    /** How well the new songs from an artist went (0 bad … 1 good), from their outcomes; null with fewer than 2. */
    fun introSuccess(outcomes: List<String>): Double? {
        val done = outcomes.filter { it.isNotEmpty() }
        if (done.size < 2) return null
        return done.sumOf { when (it) { "kept" -> 1.0; "heard" -> 0.7; else -> 0.0 } } / done.size
    }
}

/** Your listening habits, from every play there is (Last.fm, the imports, the plays here), counted in time order: which
 *  songs you play straight after which (in the same sitting: under 20 minutes apart), which artists you play in the
 *  same sittings (your own "similar artists"), and each artist's phase in your life. */
class Habits(
    /** A song's key ("title\tartist") → the songs you've played right after it, most often first, with how often. */
    val after: Map<String, List<Pair<String, Int>>> = emptyMap(),
    /** An artist (lead, as statsKey has it) → the artists you play in the same sittings, closest first (0 … 1). */
    val near: Map<String, List<Pair<String, Double>>> = emptyMap(),
    /** An artist → their phase in your listening ([HabitRules.NEW_LOVE] …). */
    val phase: Map<String, String> = emptyMap(),
    /** An artist → when you play them: the share of their plays in each 4-hour stretch of the day (6 numbers). */
    val hours: Map<String, DoubleArray> = emptyMap(),
)

object HabitRules {
    const val SITTING = 20 * 60L
    const val NEW_LOVE = "new"        // first played in the last 4 months, and a lot since
    const val LONG_TIME = "longtime"  // played across 3 years or more, and still lately
    const val FADED = "faded"         // played a lot once, nothing in the last 6 months
    const val RISING = "rising"       // twice as much in the last 2 months as their usual

    /** plays: every play (any order). keyOf: a play's song key. leadOf: its lead artist (statsKey's). */
    fun count(plays: List<Heard>, nowS: Long, zoneHour: (Long) -> Int): Habits {
        val ps = plays.filter { it.t > 0 }.sortedBy { it.t }
        val after = HashMap<String, HashMap<String, Int>>()
        val pair = HashMap<String, HashMap<String, Int>>()
        val artistPlays = HashMap<String, Int>()
        val first = HashMap<String, Long>(); val last = HashMap<String, Long>()
        val years = HashMap<String, HashSet<Int>>()
        val recent60 = HashMap<String, Int>(); val year = HashMap<String, Int>(); val before = HashMap<String, Int>()
        val hourCounts = HashMap<String, IntArray>()
        var sitting = ArrayList<String>()
        var prevKey: String? = null; var prevT = 0L
        for (p in ps) {
            val k = statsKey(p.title, p.artist); val key = k.first + "\t" + k.second; val a = k.second
            if (a.isEmpty()) continue
            if (prevKey != null && p.t - prevT in 1..SITTING && prevKey != key)
                after.getOrPut(prevKey) { HashMap() }.merge(key, 1, Int::plus)
            if (p.t - prevT > SITTING) { sittingPairs(sitting, pair); sitting = ArrayList() }
            if (a !in sitting) sitting += a
            prevKey = key; prevT = p.t
            artistPlays.merge(a, 1, Int::plus)
            if (a !in first) first[a] = p.t
            last[a] = p.t
            years.getOrPut(a) { HashSet() } += (p.t / (365L * 86_400L)).toInt()
            val age = nowS - p.t
            when { age < 60 * 86_400L -> recent60.merge(a, 1, Int::plus); age < 365 * 86_400L -> year.merge(a, 1, Int::plus); else -> before.merge(a, 1, Int::plus) }
            hourCounts.getOrPut(a) { IntArray(6) }[(zoneHour(p.t) / 4).coerceIn(0, 5)]++
        }
        sittingPairs(sitting, pair)
        val afterTop = after.mapValues { (_, m) -> m.entries.sortedByDescending { it.value }.take(15).map { it.key to it.value } }
        val near = pair.mapValues { (a, m) ->
            val na = artistPlays[a] ?: 1
            m.entries.map { (b, n) -> b to n / sqrt(na.toDouble() * (artistPlays[b] ?: 1)).coerceAtLeast(1.0) }
                .filter { it.second > 0.0 }.sortedByDescending { it.second }.take(25)
                .map { it.first to it.second.coerceIn(0.0, 1.0) }
        }
        val phase = HashMap<String, String>()
        for ((a, n) in artistPlays) {
            val r = recent60[a] ?: 0; val y = year[a] ?: 0; val b = before[a] ?: 0
            val since = nowS - (first[a] ?: nowS); val quiet = nowS - (last[a] ?: nowS)
            phase[a] = when {
                since < 120 * 86_400L && n >= 15 -> NEW_LOVE
                quiet > 180 * 86_400L && n >= 40 -> FADED
                (years[a]?.size ?: 0) >= 3 && r >= 3 -> LONG_TIME
                r >= 10 && r * 6.0 >= 2.0 * (y + r) -> RISING   // the last 2 months are a sixth of a year: twice their share
                else -> continue
            }
        }
        val hours = hourCounts.filter { (artistPlays[it.key] ?: 0) >= 10 }.mapValues { (_, c) -> val s = c.sum().toDouble(); DoubleArray(6) { c[it] / s } }
        return Habits(afterTop, near, phase, hours)
    }

    private fun sittingPairs(s: List<String>, pair: HashMap<String, HashMap<String, Int>>) {
        if (s.size < 2 || s.size > 40) return   // a shuffle of everything says little
        for (i in s.indices) for (j in s.indices) if (i != j) pair.getOrPut(s[i]) { HashMap() }.merge(s[j], 1, Int::plus)
    }

    /** Artists like an artist: Last.fm's similar artists (how alike, 0 … 1) blended with the ones you play in the same
     *  sittings, so the list fits you as well as everyone. */
    fun similar(lastFm: List<Pair<String, Double>>, near: List<Pair<String, Double>>): List<Pair<String, Double>> {
        val out = HashMap<String, Double>(); val name = HashMap<String, String>()
        for ((n, s) in lastFm) { val k = statsKey("", n).second; out.merge(k, 0.6 * s, Double::plus); name.putIfAbsent(k, n) }
        for ((k, s) in near) { out.merge(k, 0.4 * s + 0.15, Double::plus); name.putIfAbsent(k, k) }
        return out.entries.sortedByDescending { it.value }.map { name.getValue(it.key) to it.value.coerceAtMost(1.0) }
    }
}

/** Autoplay (∞) and radios: a service's "songs like this" put in an order that fits you. */
object AutoplayRules {
    /** One song offered, with all that's known of it here. pos: its place in the service's list (0 first). */
    class Cand(
        val key: String, val artist: String, val pos: Int,
        val own: Boolean = false,                 // a song of yours (it plays from your file)
        val skips: Int = 0, val full: Int = 0, val early: Int = 0,
        val thumb: Int = 0, val blocked: Boolean = false,
        val lastPlayed: Long = 0,                 // seconds, 0: never
        val artistPlays: Int = 0,                 // all your plays of the artist
        val near: Double = 0.0,                   // how close the artist is to the one playing in your habits (0 … 1)
        val after: Int = 0,                       // how often you've played it straight after the song playing
        val sound: Double? = null,                // how close it sounds to the one playing (0 … 1), when both are known
        val sameMood: Boolean = false,
        val hourFit: Double = 0.0,                // the share of the artist's plays at this time of day (0 … 1)
        val artistDown: Boolean = false,          // the artist: Less like this or Not for autoplay
        val introFail: Boolean = false,           // new songs of this artist went badly before
        val artistUp: Boolean = false,
        val placeFit: Double = 0.0,               // the share of the artist's plays on the output in use (car, earbuds…), 0 … 1
        val sameGenre: Boolean = false,
    )

    /** Left out altogether: what you said no to, what you heard in the last 3 hours, what you keep skipping. */
    fun leftOut(c: Cand, now: Long): Boolean = c.blocked || c.thumb < 0 || c.artistDown ||
        (c.lastPlayed > 0 && now - c.lastPlayed < 3 * 3600) || c.early >= 2 || (c.skips >= 3 && c.skips > 2 * c.full)

    fun score(c: Cand, n: Int): Double {
        var s = 1.0 - c.pos.toDouble() / n.coerceAtLeast(1)              // the service's own order still counts most
        s += 0.35 * ln(1.0 + c.after) / ln(6.0)                        // you play it after this one
        s += 0.4 * (ln(1.0 + c.artistPlays) / ln(2000.0)).coerceAtMost(1.0)   // an artist you love
        s += 0.4 * c.near
        if (c.own) s += 0.2
        if (c.thumb > 0) s += 0.5
        if (c.artistUp) s += 0.3
        c.sound?.let { s += 0.3 * it }
        if (c.sameMood) s += 0.15
        s += 0.2 * c.hourFit
        s += 0.15 * c.placeFit
        if (c.sameGenre) s += 0.15
        if (c.introFail) s -= 0.4
        val tried = c.skips + c.full
        if (tried >= 2) s -= 0.4 * c.skips / tried
        if (c.early == 1) s -= 0.2
        return s
    }

    /** The candidates in the order to play them: the ones left out gone, the best first, and no artist twice in a row
     *  nor more than twice in [cap] songs (unless nothing else is left). */
    fun rank(cs: List<Cand>, now: Long, seedArtist: String = "", cap: Int = 10): List<Cand> {
        val n = cs.size
        val pool = cs.filter { !leftOut(it, now) }.sortedByDescending { score(it, n) }.toMutableList()
        val out = ArrayList<Cand>()
        var lastArtist = seedArtist
        while (pool.isNotEmpty()) {
            val window = out.takeLast(cap - 1)
            val i = pool.indexOfFirst { c -> c.artist != lastArtist && window.count { it.artist == c.artist } < 2 }.takeIf { it >= 0 } ?: 0
            val c = pool.removeAt(i); out += c; lastArtist = c.artist
        }
        return out
    }

    /** How close two songs sound (0 … 1): tempo (half and double counted as the same) and loudness. */
    fun soundClose(bpmA: Double, loudA: Double, bpmB: Double, loudB: Double): Double? {
        if (bpmA <= 0 || bpmB <= 0) return null
        val r = bpmA / bpmB
        val tempo = listOf(r, r * 2, r / 2).minOf { kotlin.math.abs(ln(it)) }   // 0: same
        val t = (1.0 - tempo / 0.25).coerceIn(0.0, 1.0)
        val l = (1.0 - kotlin.math.abs(loudA - loudB) / 12.0).coerceIn(0.0, 1.0)
        return 0.6 * t + 0.4 * l
    }
}
