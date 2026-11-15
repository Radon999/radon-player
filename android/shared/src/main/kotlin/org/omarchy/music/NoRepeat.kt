package org.omarchy.music

import kotlin.math.pow
import kotlin.random.Random

/** Picking at random without saying the same thing again too soon (the DJ's kinds of set, its facts, its lines).
 *  A history is what was picked, oldest first, newest last. */
object NoRepeat {
    /** How many of the last picks are kept out: at most max, and always leaving at least spare + 1 to choose from
     *  among usable choices. Never below 0. */
    fun window(max: Int, usable: Int, spare: Int): Int = maxOf(0, minOf(max, usable - spare))

    /** The order to try choices in: the ones not among the last window picks first, at random (a choice listed
     *  twice is twice as likely to come early), then the kept-out ones, the one picked longest ago first, so a
     *  choice that can't be used right now falls back to the stalest. Each choice once. */
    fun order(choices: List<String>, history: List<String>, window: Int, rng: Random = Random.Default): List<String> {
        val weight = LinkedHashMap<String, Int>()
        choices.forEach { weight.merge(it, 1, Int::plus) }
        val recent = history.takeLast(maxOf(0, window))
        val blocked = weight.keys.filter { it in recent }
        // Weighted shuffle (Efraimidis–Spirakis): u^(1/w), biggest first.
        val open = weight.keys.filter { it !in recent }
            .map { it to rng.nextDouble().pow(1.0 / weight.getValue(it)) }
            .sortedByDescending { it.second }.map { it.first }
        return open + blocked.sortedBy { recent.lastIndexOf(it) }
    }

    /** One of keys at random, none of the last window picks of history; null when keys is empty. window is
     *  capped so at least one key is left. */
    fun pick(keys: List<String>, history: List<String>, max: Int, rng: Random = Random.Default): String? {
        val distinct = keys.distinct()
        if (distinct.isEmpty()) return null
        return order(distinct, history, window(max, distinct.size, 1), rng).first()
    }

    /** One of keys, with its subject taking turns: first the subject (topicOf a key: skips, moods, an artist…) is
     *  picked, none of the last topicMax subjects told (as far as there are others), the one told longest ago when all
     *  were; then a key of that subject, none of the last max keys. So with six things to say about skips and one
     *  about genres, skips still come round only once in a turn of subjects. Null when keys is empty. */
    fun pickTurn(keys: List<String>, history: List<String>, topicOf: (String) -> String, max: Int, topicMax: Int = 8,
                 rng: Random = Random.Default): String? {
        val distinct = keys.distinct()
        if (distinct.isEmpty()) return null
        val topics = distinct.map(topicOf).distinct()
        val topic = order(topics, history.map(topicOf), window(topicMax, topics.size, 1), rng).first()
        // Within the subject, by what was said of it (the other subjects' tellings in between don't count as time passed)
        return pick(distinct.filter { topicOf(it) == topic }, history.filter { topicOf(it) == topic }, max, rng)
    }

    /** history with key added at the end, only the last keep kept. */
    fun remember(history: List<String>, key: String, keep: Int = 40): List<String> = (history + key).takeLast(keep)

    /** A shuffle bag of size items: used is the bag so far (the indices given out since it was last full, in order);
     *  gives the new bag, whose last is the pick. Nothing is picked twice until all have been, and a new round never
     *  starts with the one the last round ended on. */
    fun bag(used: List<Int>, size: Int, rng: Random = Random.Default): List<Int> {
        if (size <= 1) return listOf(0)
        val left = (0 until size).filter { it !in used }
        if (left.isNotEmpty()) return used + left[rng.nextInt(left.size)]
        val fresh = (0 until size).filter { it != used.lastOrNull() }
        return listOf(fresh[rng.nextInt(fresh.size)])
    }

    /** A history as one line of text to keep (Prefs), and back. Keys lose their line breaks. */
    fun encode(history: List<String>): String = history.joinToString("\n") { it.replace('\n', ' ') }
    fun decode(text: String): List<String> = text.split("\n").filter { it.isNotEmpty() }
}
