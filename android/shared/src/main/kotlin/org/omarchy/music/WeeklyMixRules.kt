package org.omarchy.music

/** The weekly mix (Playlists › Make this week's mix): a playlist put together from what you have been playing lately
 *  and songs of yours you haven't heard in a long while, so it is familiar with something to rediscover in it. Two
 *  recent songs, then one long-unheard one, and so on: up to 20 recent and 10 unheard. The unheard ones are picked by
 *  the week, so making it again the same week gives the same mix, and next week a new one. */
object WeeklyMixRules {
    const val NAME = "Weekly mix"
    const val RECENT = 20
    const val OLD = 10
    /** A song counts as long unheard after this many days without a play (or never played at all). */
    const val UNHEARD_DAYS = 90

    /** recent: what you played lately, the latest first. mine: every song you have. lastPlayed: when a song was last
     *  played (seconds), null: never. key: what makes two entries the same song. week: this week's number (the seed). */
    fun <T> pick(recent: List<T>, mine: List<T>, lastPlayed: (T) -> Long?, now: Long, key: (T) -> String, week: Long): List<T> {
        val seen = HashSet<String>()
        val fresh = recent.filter { seen.add(key(it)) }.take(RECENT)
        val cutoff = now - UNHEARD_DAYS * 86_400L
        val old = mine.filter { key(it) !in seen && (lastPlayed(it) ?: 0L) < cutoff }.distinctBy(key)
            .sortedBy { (key(it).hashCode().toLong() * 31 + week * 1_000_003L).let { h -> (h xor (h ushr 13)) and 0x7FFFFFFF } }.take(OLD)
        val out = ArrayList<T>(); var f = 0; var o = 0
        while (f < fresh.size || o < old.size) {
            repeat(2) { if (f < fresh.size) out += fresh[f++] }
            if (o < old.size) out += old[o++]
        }
        return out
    }

    /** What is said when it is made. */
    fun said(total: Int, old: Int): String = when {
        total == 0 -> "Nothing to make a mix from yet: play some music first"
        old == 0 -> "Weekly mix made: %,d songs you have been playing".format(total)
        else -> "Weekly mix made: %,d songs, %,d of them ones you haven't heard in a while".format(total, old)
    }
}
