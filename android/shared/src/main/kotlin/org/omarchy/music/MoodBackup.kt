package org.omarchy.music

import kotlinx.serialization.Serializable

/** One song's moods as a backup keeps them: the song by its names and length (paths differ between devices, and
 *  after files are moved), its moods (its mood first), how sure the first one is, whether they came from the whole
 *  song (Full scan) or a minute of it, whether you picked the mood yourself, and the sound heard (if any). */
@Serializable
data class MoodBackupSong(val title: String, val artist: String, val album: String = "", val duration: Double = 0.0,
                          val moods: List<String> = emptyList(), val sure: Double = 0.0, val full: Boolean = false,
                          val picked: Boolean = false, val sound: SoundFeatures? = null)

/** A song on this device, for matching a backup's moods to it: [key] is how the caller finds it again (its file). */
data class MoodSongHere(val key: String, val title: String, val artist: String, val album: String = "", val duration: Double = 0.0)

/** Restoring moods: each song here gets the backed-up moods of the same song (same title and lead artist, tidied as
 *  everywhere else; the same length within a few seconds when both are known; the same album first when there's a
 *  choice), so a scan never needs doing again. */
object MoodBackup {
    /** How far apart two lengths of "the same song" may be (a phone and a PC can read a file's length a bit apart). */
    const val SLACK = 4.0

    private val LEAD = Regex(",|;|&| feat\\.? | ft\\.? | featuring | with | x ", RegexOption.IGNORE_CASE)
    private fun artistKey(a: String) = norm(a.split(LEAD).firstOrNull().orEmpty().trim())
    private fun near(a: Double, b: Double) = a <= 0 || b <= 0 || kotlin.math.abs(a - b) <= SLACK

    /** {song here's key: its backed-up moods}, for the songs here the backup has. */
    fun match(saved: List<MoodBackupSong>, here: List<MoodSongHere>): Map<String, MoodBackupSong> {
        val exact = saved.filter { it.moods.isNotEmpty() }.groupBy { norm(cleanTitle(it.title)) to artistKey(it.artist) }
        val bare = saved.filter { it.moods.isNotEmpty() && it.duration > 0 }.groupBy { norm(bareTitle(it.title)) to artistKey(it.artist) }
        val out = HashMap<String, MoodBackupSong>()
        for (h in here) {
            val a = artistKey(h.artist)
            val t = norm(cleanTitle(h.title))
            if (t.isEmpty()) continue
            var cands = exact[t to a].orEmpty().filter { near(it.duration, h.duration) }
            // "Song (2011 Remaster)" here and "Song" there: only when the length says it's the same recording
            if (cands.isEmpty() && h.duration > 0) cands = bare[norm(bareTitle(h.title)) to a].orEmpty()
                .filter { kotlin.math.abs(it.duration - h.duration) <= SLACK }.takeIf { it.size == 1 }.orEmpty()
            if (cands.isEmpty()) continue
            val album = albumBase(h.album)
            out[h.key] = cands.maxWith(compareBy<MoodBackupSong>(
                { album.isNotEmpty() && albumBase(it.album) == album },        // the same album
                { if (it.duration > 0 && h.duration > 0) -kotlin.math.abs(it.duration - h.duration) else -SLACK },   // the closest length
                { it.picked }, { it.full }, { it.sure }))
        }
        return out
    }
}
