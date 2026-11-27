package org.omarchy.music

/** Followed artists: whose new releases New releases looks for. The artists of your songs (on this device) and of
 *  the playlists you keep in Radon Player are followed by themselves while Follow artists by themselves is on (Settings ›
 *  General); a streaming service's own playlists never count. You can follow anyone (an artist's menu) and unfollow
 *  anyone, and an unfollow stays: each artist is offered for following by itself once ([seen]), never again after you
 *  unfollowed them. Turned off, no one new is followed by themselves, and no one is unfollowed; turned on again, those
 *  added meanwhile are followed at once. */
object FollowRules {
    const val LIST = "followed_artists"   // the followed, one per line, as written
    const val SEEN = "follow_seen"        // the artists already offered for following by themselves (their keys, one per line)
    const val AUTO = "follow_auto"        // Follow artists by themselves (on at first)

    /** One artist however it's written ("Tyler, The Creator" = "tyler, the creator"). */
    fun key(name: String): String = name.trim().lowercase()

    fun parse(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    fun write(names: Collection<String>): String = names.joinToString("\n")

    /** The artists to look at for following by themselves: lead artists, each once, never "Unknown artist" or "Various". */
    fun library(artists: List<String>): List<String> {
        val seen = HashSet<String>()
        return artists.map { it.trim() }.filter { a ->
            a.isNotEmpty() && key(a) !in setOf("unknown artist", "various artists", "various", "va") && seen.add(key(a))
        }
    }

    /** Following by themselves: with it on, every library artist not offered before is followed (and counted as
     *  offered); off, nothing changes. (followed, seen) as they are now. */
    fun auto(followed: List<String>, seen: Set<String>, library: List<String>, on: Boolean): Pair<List<String>, Set<String>> {
        if (!on) return followed to seen
        val have = followed.mapTo(HashSet()) { key(it) }
        val f = followed.toMutableList(); val s = seen.toMutableSet()
        for (a in library(library)) {
            val k = key(a)
            if (k in s) continue
            s += k
            if (have.add(k)) f += a
        }
        return f to s
    }

    /** Followed by hand (and counted as offered, so it's never followed twice). */
    fun follow(followed: List<String>, seen: Set<String>, names: List<String>): Pair<List<String>, Set<String>> {
        val have = followed.mapTo(HashSet()) { key(it) }
        val f = followed.toMutableList()
        for (n in names.map { it.trim() }.filter { it.isNotEmpty() }) if (have.add(key(n))) f += n
        return f to (seen + names.map { key(it) })
    }

    /** Unfollowed: off the list, and still counted as offered (so following by themselves leaves them unfollowed). */
    fun unfollow(followed: List<String>, seen: Set<String>, names: List<String>): Pair<List<String>, Set<String>> {
        val out = names.mapTo(HashSet()) { key(it) }
        return followed.filter { key(it) !in out } to (seen + out)
    }

    fun isFollowed(followed: List<String>, name: String): Boolean = followed.any { key(it) == key(name) }
}
