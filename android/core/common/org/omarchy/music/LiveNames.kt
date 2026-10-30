package org.omarchy.music

// Live in song names (the desktop's Settings › Live in song names…): your songs whose names say they're live ("Song
// (Live)", "Song - Live at Red Rocks", "Song [Live]"), each Change (the live part taken off the name), Leave alone (not
// asked again) or Other name. Only the deciding is here: reading the songs and writing the titles are the caller's.

/** A song named as live, and the plain name it'd get (the live part taken off). */
data class LiveName(val file: String, val title: String, val artist: String, val name: String)

/** The plain name of a song named as live ("Song (Live)", "Song - Live at Red Rocks", "Song [Live]" -> "Song"), or null
 *  if its name doesn't say it's live, or is only "Live…" ("Live Forever", "Live Your Life"). */
fun liveName(title: String?): String? {
    val t = title ?: ""
    if (!hasLive(t)) return null
    val plain = stripLive(t)
    return if (plain != t) plain else null
}

/** A file as Leave alone remembers it (the desktop's TagScanDone.key): its path tidied. */
fun liveKeptKey(file: String): String = fixupNormPath(file)

/** Your songs named as live, the artist's then the name's order: each file once, none in skip (the folders left
 *  alone) and none you left alone before (kept: liveKeptKey's of them). */
fun liveNamesFound(songs: List<FixupSong>, skip: Collection<String> = emptyList(), kept: Set<String> = emptySet()): List<LiveName> =
    namesFound(songs, skip, kept, ::liveName)

/** The same for any name tidy (Live in song names, Soundtrack notes in names): songs whose plainer name isn't null. */
fun namesFound(songs: List<FixupSong>, skip: Collection<String>, kept: Set<String>, plainer: (String?) -> String?): List<LiveName> {
    val seen = HashSet<String>()
    val found = ArrayList<LiveName>()
    for (t in songs) {
        val f = t.file
        if (f.isEmpty() || f in seen || inFolders(f, skip)) continue
        seen += f
        val name = plainer(t.title) ?: continue
        if (liveKeptKey(f) in kept) continue
        found += LiveName(f, t.title, t.artist, name)
    }
    return found.sortedWith(compareBy<LiveName>({ norm(it.artist) }, { norm(it.title) }))
}

/** What to do with the songs found, as picked (Change, Leave alone, or Other with a name you typed): ([(file, its new
 *  title)] to write, [liveKeptKey's of the ones left alone] to remember). A name the same as it was isn't written. */
fun liveNamesApply(found: List<LiveName>, picked: List<FixupPick>): Pair<List<Pair<String, String>>, List<String>> {
    val renames = ArrayList<Pair<String, String>>()
    val kept = ArrayList<String>()
    for ((t, p) in found.zip(picked)) {
        if (p is FixupPick.Leave || (p is FixupPick.Other && p.name.isEmpty())) {
            kept += liveKeptKey(t.file)
            continue
        }
        val name = if (p is FixupPick.Other) p.name.trim() else t.name
        if (name.isNotEmpty() && name != t.title) renames += t.file to name
    }
    return renames to kept.distinct()
}
