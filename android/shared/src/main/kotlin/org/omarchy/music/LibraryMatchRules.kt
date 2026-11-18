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
        val plain = GeniusRules.plainTitle(title)
        val k = statsKey(plain, artist)
        return k.first + "|" + k.second + "|" + CopyRules.versionWords(plain).sorted().joinToString(",")
    }

    /** Of the other device's songs, the ones this device hasn't got and you haven't left out: each once, in the other
     *  device's order. */
    fun <T> missing(theirs: List<T>, mine: List<T>, leftOut: Set<String>, title: (T) -> String, artist: (T) -> String): List<T> {
        val have = mine.mapTo(HashSet()) { key(title(it), artist(it)) }
        val seen = HashSet<String>()
        return theirs.filter { t ->
            val k = key(title(t), artist(t))
            title(t).isNotBlank() && k !in have && k !in leftOut && seen.add(k)
        }
    }

    /** What the screen says above the list. */
    fun heading(n: Int, device: String): String =
        if (n == 0) "Nothing missing: this device has every song $device has"
        else "%,d song%s on %s that this device doesn't have".format(n, if (n != 1) "s" else "", device)

    /** What a Sync says when the other device has songs this one lacks ("" when it has none). */
    fun afterSync(n: Int, device: String): String =
        if (n <= 0) "" else "%,d song%s on %s %s on this device: ☰ › Other device's songs".format(n, if (n != 1) "s" else "", device, if (n != 1) "aren't" else "isn't")
}
