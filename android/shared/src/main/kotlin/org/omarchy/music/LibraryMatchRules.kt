package org.omarchy.music

/** The songs your other device has that this one hasn't. At each Sync the phone and the PC tell each other every song
 *  they have; each then knows what it lacks, and lists it for you to tick: download the ones you want here, leave the
 *  rest out (those aren't offered again). So the two libraries can be kept the same without moving files across by
 *  hand or downloading everything twice. A song is the same song as Most played matches them (its title tidied, its
 *  lead artist, the same kind of version): however each device's file happens to be tagged or named. */
object LibraryMatchRules {
    /** A song as the two libraries are compared: its name and lead artist, and the kind of version ("Song (Live)" is
     *  not "Song"; the single's, the album's and the EP's version are one). */
    fun key(title: String, artist: String): String {
        val plain = GeniusRules.plainTitle(title.replace('_', ' '))
        val k = statsKey(plain, artist.replace('_', ' '))
        return k.first + "|" + k.second + "|" + CopyRules.versionWords(plain).sorted().joinToString(",")
    }

    private fun noArtist(artist: String): Boolean = artist.replace('_', ' ').trim().lowercase().let { it.isEmpty() || it == "unknown" || it == "unknown artist" || it == "<unknown>" }

    /** A song by its name alone (and its kind of version): for a file with no artist in its tags. */
    private fun nameKey(title: String): String = key(title, "").let { k -> k.substringBefore('|') + "||" + k.substringAfterLast('|') }

    /** Every way a song may be named on the other device. A file with no tags is named by each device in its own way:
     *  one reads "Jon Bellion - Graveyard [DEMO]" as the whole title, the other takes the artist out of it (or out of
     *  the folder's name); one keeps the underscores of a file's name, the other has had them tidied away. So: as it is;
     *  with its artist's name taken off the front or the end of its title; and, when the title is "A - B", as B by A and
     *  as A by B. */
    internal fun keys(title: String, artist: String): Set<String> {
        val t = title.replace('_', ' ').trim(); val a = artist.replace('_', ' ').trim()
        val out = LinkedHashSet<String>()
        out += key(t, a)
        if (!noArtist(a)) {
            if (t.lowercase().startsWith(a.lowercase() + " - ")) out += key(t.substring(a.length + 3), a)
            if (t.lowercase().endsWith(" - " + a.lowercase())) out += key(t.dropLast(a.length + 3), a)
        }
        val dash = t.indexOf(" - ")
        if (dash > 0 && dash + 3 < t.length) {
            val left = t.substring(0, dash).trim(); val right = t.substring(dash + 3).trim()
            out += key(right, left); out += key(left, right)
        }
        return out
    }

    /** Of the other device's songs, the ones this device hasn't got and you haven't left out: each once, in the other
     *  device's order. The same song is one this device has under any of its names ([keys]); and a song that has no
     *  artist on either device is the same when its name is. */
    fun <T> missing(theirs: List<T>, mine: List<T>, leftOut: Set<String>, title: (T) -> String, artist: (T) -> String): List<T> {
        val have = HashSet<String>(); val haveNames = HashSet<String>(); val haveNamesNoArtist = HashSet<String>()
        for (m in mine) {
            val t = title(m); val a = artist(m)
            have += keys(t, a)
            // its name alone, as it is and without what stands before or after a dash in it
            val names = listOf(t) + t.replace('_', ' ').split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
            for (n in names) { val nk = nameKey(n); haveNames += nk; if (noArtist(a)) haveNamesNoArtist += nk }
        }
        val seen = HashSet<String>()
        return theirs.filter { t ->
            val ti = title(t); val ar = artist(t)
            if (ti.isBlank()) return@filter false
            val k = key(ti, ar)
            val names = (listOf(ti) + ti.replace('_', ' ').split(" - ").map { it.trim() }.filter { it.isNotEmpty() }).map { nameKey(it) }
            val here = keys(ti, ar).any { it in have } || names.any { it in haveNamesNoArtist } || (noArtist(ar) && names.any { it in haveNames })
            !here && k !in leftOut && seen.add(k)
        }
    }

    /** What the screen says above the list. */
    fun heading(n: Int, device: String): String =
        if (n == 0) "Nothing missing: this device has every song $device has"
        else "%,d song%s on %s that this device doesn't have".format(n, if (n != 1) "s" else "", device)

    /** What a Sync says when the other device has songs this one lacks ("" when it has none). */
    fun afterSync(n: Int, device: String): String =
        if (n <= 0) "" else "%,d song%s on %s %s on this device: Settings › Remote & sync › Other device's songs".format(n, if (n != 1) "s" else "", device, if (n != 1) "aren't" else "isn't")
}
