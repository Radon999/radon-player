package org.omarchy.music

// Which picked covers can be believed, and whether two artist credits are the same artist.

/** A picture's address as a file on this device, made comparable ("file:///C:/A/b.jpg" and "C:\A\b.jpg" are one):
 *  null for a picture on the web. */
fun coverFileKey(url: String): String? {
    var p = url.trim()
    if (p.isEmpty() || p.startsWith("http://", true) || p.startsWith("https://", true)) return null
    if (p.startsWith("file:", true)) {
        p = p.substring(5).trimStart('/').replace("%20", " ")
        if (!Regex("^[A-Za-z]:").containsMatchIn(p)) p = "/$p"   // file:///home/x is /home/x; file:///C:/x is C:/x
    }
    return p.replace('\\', '/').lowercase()
}

/** A cover key's (lead artist, album): "lead||album" now, "lead|album" before 3.0.0. */
private fun coverKeyParts(key: String): Pair<String, String> =
    key.substringBefore('|') to key.substringAfter('|').removePrefix("|")

/** The covers (album key to picture) that can't be believed: one file of the device's that's the cover of different
 *  artists' different albums. The PC app before this one picked every cover into the same scratch file
 *  (cache/single-cover.jpg), so each album it was picked for shows whatever was picked last (another artist's album).
 *  One file for an artist's albums (the same picture picked twice), or for one album's songs by different artists
 *  (a compilation), is fine. */
fun untrustedCovers(covers: Map<String, String>): Set<String> {
    val out = HashSet<String>()
    for ((file, keys) in covers.entries.groupBy({ coverFileKey(it.value) }, { it.key })) {
        if (file == null || keys.size < 2) continue
        val parts = keys.map { coverKeyParts(it) }
        if (parts.map { it.first }.toSet().size > 1 && parts.map { it.second }.toSet().size > 1) out += keys
    }
    return out
}

/** Whether two artist credits share an artist ("Sigrid" and "Sigrid, Bring Me The Horizon"; "K.Flay" and "K Flay";
 *  "Sigrid" and "SIGRID (NO)"), so a picture from a file of one may stand for a song of the other. */
fun sameArtist(a: String, b: String): Boolean {
    fun parts(s: String) = (splitArtists(s) + s).map { norm(it).replace(Regex("[\\W_]+"), "") }.filter { it.isNotEmpty() }.toSet()
    val x = parts(a); val y = parts(b)
    return x.any { p -> y.any { q -> p == q || similar(p, q) >= 0.85 } }
}
