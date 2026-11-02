package org.omarchy.music

import kotlin.math.ln
import kotlin.math.log10

/** Wrapped's pure sums: how varied something is (diversity), and what kind of listener you are. No app state here,
 *  so the unit tests can run them. */
object Listening {

    /** How evenly plays (or songs) spread over kinds (genres, moods): normalised Shannon entropy, 0 (all one kind) to
     *  1 (every kind the same). Fewer than two kinds with anything in them: 0. */
    fun diversity(counts: Collection<Int>): Double {
        val c = counts.filter { it > 0 }
        if (c.size < 2) return 0.0
        val total = c.sum().toDouble()
        val h = -c.sumOf { val p = it / total; p * ln(p) }
        return (h / ln(c.size.toDouble())).coerceIn(0.0, 1.0)
    }

    /** The score as a percentage word: "very varied" … "very focused". */
    fun diversityWord(score: Double): String = when {
        score >= 0.85 -> "very varied"
        score >= 0.65 -> "varied"
        score >= 0.45 -> "fairly focused"
        score > 0.0 -> "very focused"
        else -> "all one kind"
    }

    /** A plain sentence about it: "Your 42 genres are very varied: no one genre takes over." */
    fun diversitySentence(score: Double, kinds: Int, what: String, top: String?): String = when {
        kinds == 0 -> "None of your songs has a $what yet."
        kinds == 1 -> "All of it is ${top ?: "one $what"}."
        score >= 0.85 -> "Your $kinds ${what}s are spread out evenly: no one $what takes over."
        score >= 0.65 -> "A good mix of $kinds ${what}s${top?.let { ", with $it out in front" } ?: ""}."
        score >= 0.45 -> "Mostly a few ${what}s${top?.let { ", $it most of all" } ?: ""}, with $kinds in all."
        else -> "${top ?: "One $what"} is most of it, out of $kinds ${what}s."
    }

    /** What we know of how someone listens; null: not known (no data for it). Shares are 0..1.
     *  fans: the typical fan count of the artists they play (Deezer's, weighted by plays); shuffle: the share of
     *  listening from playlists and albums done shuffled; top10: the share of plays on their top 10 artists;
     *  newArtists: the share of artists played that are new to them; night (10 PM–4 AM) and morning (5–10 AM): the
     *  share of plays then; skips: skips / (skips + songs heard to the end). */
    data class Traits(val fans: Long? = null, val shuffle: Double? = null, val top10: Double? = null, val newArtists: Double? = null,
                      val night: Double = 0.0, val morning: Double = 0.0, val skips: Double? = null)

    /** One side of one axis: axis "taste", word "Mainstream", why it says so, and how strongly (0..1). */
    data class Trait(val axis: String, val word: String, val why: String, val strength: Double)

    /** The listener type: a fun name, one line about it, and every trait that went into it. */
    data class Kind(val name: String, val line: String, val traits: List<Trait>)

    private fun pct(x: Double) = "${Math.round(x * 100)}%"
    private fun fansText(n: Long) = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> "${n / 1000}K"
        else -> "$n"
    }

    /** Each axis's verdict, the clear ones only (a middling one is left out). */
    fun traits(x: Traits): List<Trait> {
        val out = ArrayList<Trait>()
        x.fans?.takeIf { it > 0 }?.let { f ->
            val lg = log10(f.toDouble())   // 1M fans: 6; 100K: 5
            if (lg >= 6.2) out += Trait("taste", "Mainstream", "Your artists have about ${fansText(f)} fans each on Deezer: big names.", ((lg - 6.2) / 1.3).coerceIn(0.15, 1.0))
            else if (lg <= 5.2) out += Trait("taste", "Underground", "Your artists have about ${fansText(f)} fans each on Deezer: you find them before everyone else.", ((5.2 - lg) / 1.5).coerceIn(0.15, 1.0))
            else out += Trait("taste", "Balanced", "Your artists have about ${fansText(f)} fans each: some big names, some small.", 0.05)
        }
        x.shuffle?.let { s ->
            if (s >= 0.6) out += Trait("order", "Shuffler", "${pct(s)} of your playlist and album time was shuffled.", ((s - 0.6) / 0.4).coerceIn(0.15, 1.0))
            else if (s <= 0.25) out += Trait("order", "In order", "${pct(1 - s)} of your playlists and albums played in order.", ((0.25 - s) / 0.25).coerceIn(0.15, 1.0))
        }
        val top = x.top10; val new = x.newArtists
        if (top != null) {
            if (top >= 0.6 && (new == null || new < 0.35)) out += Trait("loyalty", "Loyal", "${pct(top)} of your plays went to your top 10 artists.", ((top - 0.6) / 0.4).coerceIn(0.15, 1.0))
            else if (top < 0.35 || (new != null && new >= 0.5)) out += Trait("loyalty", "Explorer",
                if (new != null && new >= 0.5) "${pct(new)} of the artists you played were new to you." else "Only ${pct(top)} of your plays went to your top 10 artists.",
                maxOf(if (top < 0.35) (0.35 - top) / 0.35 else 0.0, if (new != null && new >= 0.5) (new - 0.5) / 0.5 else 0.0).coerceIn(0.15, 1.0))
        } else if (new != null && new >= 0.5) out += Trait("loyalty", "Explorer", "${pct(new)} of the artists you played were new to you.", ((new - 0.5) / 0.5).coerceIn(0.15, 1.0))
        if (x.night >= 0.25 && x.night >= x.morning) out += Trait("clock", "Night owl", "${pct(x.night)} of your plays were between 10 PM and 4 AM.", ((x.night - 0.25) / 0.35).coerceIn(0.15, 1.0))
        else if (x.morning >= 0.3) out += Trait("clock", "Early bird", "${pct(x.morning)} of your plays were between 5 and 10 AM.", ((x.morning - 0.3) / 0.4).coerceIn(0.15, 1.0))
        x.skips?.let { k ->
            if (k >= 0.4) out += Trait("skips", "Skipper", "You skipped ${pct(k)} of the songs you left or finished.", ((k - 0.4) / 0.4).coerceIn(0.15, 1.0))
            else if (k <= 0.15) out += Trait("skips", "Finisher", "You heard ${pct(1 - k)} of your songs to the end.", ((0.15 - k) / 0.15).coerceIn(0.15, 1.0))
        }
        return out
    }

    private val NOUNS = mapOf("Explorer" to "Explorer", "Loyal" to "Loyalist", "Shuffler" to "Shuffler", "In order" to "Album Purist",
        "Skipper" to "Skipper", "Finisher" to "Finisher")
    private val ADJECTIVES = mapOf("Night owl" to "Night-Owl", "Early bird" to "Early-Bird", "Mainstream" to "Chart-Topping",
        "Underground" to "Underground")
    private val LINES = mapOf(
        "Explorer" to "always after the next new sound", "Loyalist" to "faithful to the artists you love",
        "Shuffler" to "happiest when you don't know what's next", "Album Purist" to "you play things the way the artist meant them",
        "Skipper" to "you know in seconds whether a song is the one", "Finisher" to "you let every song have its say",
        "Night-Owl" to "mostly after dark", "Early-Bird" to "mostly in the morning", "Chart-Topping" to "with the big names",
        "Underground" to "with artists most people haven't found yet")

    /** The listener type: "The Night-Owl Explorer" from the strongest traits, with a line about it. */
    fun type(x: Traits): Kind {
        val all = traits(x)
        val noun = all.filter { it.word in NOUNS }.maxByOrNull { it.strength }?.let { NOUNS.getValue(it.word) }
        val adj = all.filter { it.word in ADJECTIVES }.maxByOrNull { it.strength }?.let { ADJECTIVES.getValue(it.word) }
        val name = when {
            noun != null && adj != null -> "The $adj $noun"
            noun != null -> "The $noun"
            adj != null -> "The $adj All-Rounder"
            else -> "The All-Rounder"
        }
        val line = when {
            noun != null && adj != null -> "${LINES.getValue(noun).replaceFirstChar { it.uppercase() }}, ${LINES.getValue(adj)}."
            noun != null -> "${LINES.getValue(noun).replaceFirstChar { it.uppercase() }}."
            adj != null -> "A bit of everything, ${LINES.getValue(adj)}."
            else -> "A bit of everything: no one habit takes over."
        }
        return Kind(name, line, all)
    }

    /** The longest run of days in a row in days. */
    fun longestRun(days: Collection<java.time.LocalDate>): Int {
        var best = 0; var run = 0; var prev: java.time.LocalDate? = null
        for (d in days.toSortedSet()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return best
    }

    /** "3 days 4 hours", "5 hours 12 min", "12 min": how long seconds of music is. */
    fun duration(secs: Double): String {
        val m = Math.round(secs / 60).toLong()
        val d = m / 1440; val h = (m % 1440) / 60; val mm = m % 60
        return when {
            d > 0 -> "$d day${if (d != 1L) "s" else ""}" + if (h > 0) " $h hour${if (h != 1L) "s" else ""}" else ""
            h > 0 -> "$h hour${if (h != 1L) "s" else ""}" + if (mm > 0) " $mm min" else ""
            else -> "$mm min"
        }
    }

    /** The middle value of values, each counting weight times (the typical fan count of the artists you play). */
    fun weightedMedian(values: List<Pair<Long, Int>>): Long? {
        val v = values.filter { it.second > 0 }.sortedBy { it.first }
        val total = v.sumOf { it.second.toLong() }
        if (total == 0L) return null
        var run = 0L
        for ((x, w) in v) { run += w; if (run * 2 >= total) return x }
        return v.last().first
    }

}
