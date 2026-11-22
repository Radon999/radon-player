package org.omarchy.music

/** Never show, the same on both devices: what you hid in a scan goes across with Sync (its list is one of the switches),
 *  so the other device needn't be told again. Most scans name what you hid by the song, album or artist, which is the
 *  same on both; a few name it by its file or folder, whose whole address differs between the phone and the PC
 *  (/storage/emulated/0/Music/… and C:\Users\…\Music\…). Those are matched by the end of the address: the file and the
 *  two folders it is in (artist and album), the same on both when the music was copied over as it is. */
object ScanIgnoreRules {
    /** The scans that name what you hid by its file or folder. */
    val BY_ADDRESS = setOf("fixup", "live", "movie", "lone", "empty")

    /** The three stats screens' Never show (Similar artists, Duplicates in Most played, Joint artists): by name. */
    const val SIMILAR = "similar"
    const val DUPE_SONGS = "dupesongs"
    const val JOINT = "joint"

    /** How many folders above the file are kept: its album's and its artist's. */
    private const val KEEP = 3

    /** A key by its address, as both devices write it: the last parts of the address (any slashes, any case), and
     *  whatever the scan added after it (Fix albums and artists adds the change, after a "|"). */
    fun tail(key: String): String {
        val bar = key.indexOf('|')
        val path = if (bar < 0) key else key.substring(0, bar)
        val rest = if (bar < 0) "" else key.substring(bar)
        val parts = path.replace('\\', '/').removePrefix("file://").split('/').filter { it.isNotEmpty() && it != "." }
        return parts.takeLast(KEEP).joinToString("/").lowercase() + rest
    }

    /** Whether [key] is among [hidden] for [scan]: the same key, or for a scan by address, the same end of address. */
    fun hidden(scan: String, key: String, hidden: Set<String>): Boolean =
        key in hidden || (scan in BY_ADDRESS && tail(key).let { t -> t.isNotEmpty() && hidden.any { tail(it) == t } })
}
