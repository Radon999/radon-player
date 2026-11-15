package org.omarchy.music

/** A song of an album's tracklist: its number, its name, its name without the version, its length in seconds. */
data class MissingSong(val n: Int, val title: String, val short: String, val secs: Int)

/** The rules of "Missing from this album": which songs of an album's tracklist aren't among yours. */
object MissingSongs {
    private val RELEASE_KIND_END = Regex("(?:\\s*[(\\[]\\s*(?:ep|single)\\s*[)\\]]|(?:\\s+[-–—]|\\s*:)\\s*(?:ep|single)|\\s+ep)\\s*$", RegexOption.IGNORE_CASE)

    /** A release's name without its kind on the end, for finding it: "Ruby - EP", "Ruby (EP)", "Ruby [EP]", "Ruby EP",
     *  "Talk - Single", "Talk (Single)" -> "Ruby", "Talk". "Sleep" and "Singles Club" stay as they are. */
    fun releaseName(s: String): String =
        s.trim().replace(RELEASE_KIND_END, "").trim().ifEmpty { s.trim() }

    /** The album songs (all) not among yours: matched by name, forgivingly (versions, "feat.", punctuation and spelling
     *  left out; one name a near-whole part of the other), then any left over by length against the album's own songs
     *  nothing matched yet (within 2 s), so a song you have under another name isn't called missing. */
    fun from(all: List<MissingSong>, album: List<Track>, yours: List<Track>): List<MissingSong> {
        // "&" is "and" ("Salt & Stone" is your "Salt and Stone"): said before the name is tidied, which drops the "&"
        // (3.2.28 said it after, so it never took).
        fun key(s: String) = norm(bareTitle(cleanTitle(s.replace("&", " and ")))).replace(Regex("[^\\p{L}\\p{N}]"), "")
        fun same(a: String, b: String): Boolean {
            if (a.isEmpty() || b.isEmpty()) return false
            if (a == b) return true
            val (short, long) = if (a.length <= b.length) a to b else b to a
            return short.length >= 4 && short.length * 10 >= long.length * 6 && short in long
        }
        val unmatched = album.toMutableList()
        val left = all.filter { m ->
            val ks = listOf(key(m.title), key(m.short), norm(cleanTitle(m.title)).replace(Regex("[^\\p{L}\\p{N}]"), ""))
            val hit = yours.firstOrNull { y -> val yk = key(y.title); ks.any { same(it, yk) } }
            if (hit != null) { unmatched.remove(hit); false } else true
        }
        return left.filter { m ->
            val byLength = unmatched.firstOrNull { m.secs > 0 && it.duration > 0 && kotlin.math.abs(it.duration - m.secs) <= 2.0 }
            if (byLength != null) { unmatched.remove(byLength); false } else true
        }
    }
}
