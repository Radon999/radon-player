package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** The songs your other device has that this one hasn't ([LibraryMatchRules]). At each Sync the phone and the PC send
 *  each other every song they have (names only: no files cross). What the other sent is kept here; Settings › Remote & sync › Other
 *  device's songs lists the ones this device lacks, to tick and download (from the streaming service, as any download: tagged,
 *  with its cover, into My music) or to leave out. Nothing is downloaded by itself. */
object LibraryMatch {
    @Serializable
    private data class Theirs(val from: String = "", val at: Long = 0, val songs: List<Track> = emptyList())

    private val file get() = File(Music.ctx.filesDir, "other-library.json")
    private val leftOutFile get() = File(Music.ctx.filesDir, "other-library-left-out.json")
    private val setSer = SetSerializer(String.serializer())
    @Volatile private var theirs: Theirs? = null
    @Volatile private var leftOut: Set<String>? = null

    private fun loaded(): Theirs = theirs ?: (readText(file)?.let { runCatching { JSON.decodeFromString(Theirs.serializer(), it) }.getOrNull() } ?: Theirs()).also { theirs = it }
    private fun left(): Set<String> = leftOut ?: (readText(leftOutFile)?.let { runCatching { JSON.decodeFromString(setSer, it) }.getOrNull() } ?: emptySet()).also { leftOut = it }

    /** The other device's name ("" before the first Sync that carried its songs), and when it last told its songs (seconds). */
    val from: String get() = loaded().from
    val at: Long get() = loaded().at

    /** (blocking) Every song on this device, for the other one: what is needed to tell songs apart and to find one
     *  again (a download's own address on its service goes along, so the very same song is downloaded there). */
    fun mine(): String {
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList()).map { t ->
            val streamed = t.source != "file" && !t.isFile
            Track(if (streamed) t.id else "", if (streamed) t.source else "file", t.title, t.artist, t.album, t.duration)
        }
        return JSON.encodeToString(Theirs.serializer(), Theirs("", System.currentTimeMillis() / 1000, songs))
    }

    /** The other device's songs, as a Sync brought them: kept. Returns how many this device hasn't got. */
    fun take(text: String?, device: String): Int {
        val got = text?.takeIf { it.isNotBlank() }?.let { runCatching { JSON.decodeFromString(Theirs.serializer(), it) }.getOrNull() } ?: return 0
        val kept = got.copy(from = device)
        theirs = kept
        runCatching { writeAtomic(file, JSON.encodeToString(Theirs.serializer(), kept)) }
        return runCatching { missing().size }.getOrDefault(0)
    }

    /** (blocking) The other device's songs this one hasn't got (and you haven't left out), as songs to download: the
     *  one it downloaded, by its own address; one of its files, by name (found on Tidal or YouTube when it's downloaded). */
    fun missing(): List<Track> {
        val t = loaded()
        if (t.songs.isEmpty()) return emptyList()
        val mine = runCatching { Music.mySongs() }.getOrDefault(emptyList())
        val lacking = LibraryMatchRules.missing(t.songs, mine, left(), { it.title }, { it.artist })
        val index = runCatching { Music.songIndex() }.getOrDefault(emptyMap())
        return lacking.map { s ->
            if (s.id.isNotEmpty() && s.source != "file") s
            else runCatching { Music.fmSong(s.title, s.artist, "", index, s.album) }.getOrDefault(s).let { f -> if (f.duration <= 0.0) f.copy(duration = s.duration) else f }
        }.filter { !it.isFile && !Music.lib.has(it.id) }.distinctBy { it.id }
    }

    /** Leave these out: they aren't offered again (until Bring back the ones left out). */
    fun leaveOut(tracks: List<Track>) {
        val now = left() + tracks.map { LibraryMatchRules.key(it.title, it.artist) }
        leftOut = now
        runCatching { writeAtomic(leftOutFile, JSON.encodeToString(setSer, now)) }
        Music.toast("%,d song%s left out: not offered again".format(tracks.size, if (tracks.size != 1) "s" else ""))
        Music.musicVersion++
    }

    val leftOutCount: Int get() = left().size

    fun bringBack() {
        leftOut = emptySet()
        runCatching { leftOutFile.delete() }
        Music.toast("The songs you left out are offered again")
        Music.musicVersion++
    }
}
