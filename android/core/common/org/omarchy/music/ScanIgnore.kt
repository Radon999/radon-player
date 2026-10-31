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

    fun has(scan: String, key: String): Boolean = synchronized(this) { key in of(scan) }
    fun count(scan: String): Int = synchronized(this) { of(scan).size }

    fun add(scan: String, key: String) {
        synchronized(this) { of(scan) += key; Prefs.setText("ignore_$scan", of(scan).joinToString("\n")) }
        version++
    }

    fun clear(scan: String) {
        synchronized(this) { of(scan).clear(); Prefs.setText("ignore_$scan", "") }
        version++
    }

    // The keys each scan uses.
    fun fileKey(f: String) = liveKeptKey(f)
    fun fixupKey(c: FixupChange) = liveKeptKey(c.file) + "|" + c.new.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
    fun albumKey(artist: String, album: String) = norm(artist.split(",")[0]) + "|" + norm(album).ifEmpty { album.trim().lowercase() }
}
