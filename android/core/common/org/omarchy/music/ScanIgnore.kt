package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What you marked in a scan as not one of its kind ("Not live", "Not a soundtrack note", "Keep this folder", "Never
 *  show"…): never shown by that scan again, as Duplicates' Not a duplicate. Kept per scan, by a key the scan gives
 *  (a file, an album, a folder); Show again in the scan forgets them. */
object ScanIgnore {
    private val sets = HashMap<String, MutableSet<String>>()
    /** Bumped on each change, for the screens to look again. */
    var version by mutableStateOf(0)
        private set

    private fun of(scan: String): MutableSet<String> = sets.getOrPut(scan) {
        Prefs.text("ignore_$scan").split('\n').filter { it.isNotBlank() }.toMutableSet()
    }

    /** Whether it's hidden: the same key, or for a scan by file or folder, the same file or folder written as the other
     *  device writes it (the Never show picks come across with Sync; see [ScanIgnoreRules]). */
    fun has(scan: String, key: String): Boolean = synchronized(this) { ScanIgnoreRules.hidden(scan, key, of(scan)) }

    /** The lists read again from the settings: a sync just put the other device's Never show picks in place. */
    fun reload() {
        synchronized(this) { sets.clear() }
        version++
    }
    fun count(scan: String): Int = synchronized(this) { of(scan).size }

    fun add(scan: String, key: String) {
        synchronized(this) { of(scan) += key; Prefs.setText("ignore_$scan", of(scan).joinToString("\n")) }
        version++
    }

    /** Never show taken back (Undo, right after): shown by the scan again. */
    fun remove(scan: String, key: String) {
        synchronized(this) { of(scan) -= key; Prefs.setText("ignore_$scan", of(scan).joinToString("\n")) }
        version++
    }

    fun clear(scan: String) {
        synchronized(this) { of(scan).clear(); Prefs.setText("ignore_$scan", "") }
        version++
    }

    /** Every scan's hidden things: the scans' names (Fix albums and artists, Live, Soundtrack notes, songs alone in an
     *  album, Songs without covers, Empty folders, Albums EPs and singles, Fix genre names, and the stats' Similar
     *  artists, Duplicates in Most played and Joint artists). */
    val SCANS = listOf("fixup", "live", "movie", "lone", "nocover", "empty", "truth", "genres",
        ScanIgnoreRules.SIMILAR, ScanIgnoreRules.DUPE_SONGS, ScanIgnoreRules.JOINT)

    /** How many things are hidden across every scan. */
    fun total(): Int = SCANS.sumOf { count(it) }

    /** Settings › Reset Never show: every scan shows everything again. */
    fun clearAll() = SCANS.forEach { clear(it) }

    // The keys each scan uses.
    fun fileKey(f: String) = liveKeptKey(f)
    fun fixupKey(c: FixupChange) = liveKeptKey(c.file) + "|" + c.new.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
    fun albumKey(artist: String, album: String) = norm(artist.split(",")[0]) + "|" + norm(album).ifEmpty { album.trim().lowercase() }
}
