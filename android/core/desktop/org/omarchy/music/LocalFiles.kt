package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** The songs in your folders on the PC, read from the folders (the phone's come from Android's media library):
 *  each file's tags, kept with its last-changed time so only new or changed files are read again. A folder is a path,
 *  or "Music/…" for one in your home folder. */
class LocalFiles(private val ctx: Ctx) {
    private val index = File(ctx.cacheDir, "local.json")
    private val ser = MapSerializer(String.serializer(), Seen.serializer())

    @Serializable
    data class Seen(val modified: Long, val added: Double, val title: String = "", val artist: String = "", val album: String = "",
                    val duration: Double = 0.0, val genre: String = "")

    @Volatile var files: List<Track> = emptyList()
    @Volatile var paths: Map<String, String> = emptyMap(); private set
    @Volatile var genres: Map<String, String> = emptyMap(); private set
    @Volatile var allPaths: Map<String, String> = emptyMap(); private set
    @Volatile var scanning = false
    var scanned = 0.0

    init { Logger.getLogger("org.jaudiotagger").level = Level.OFF }

    private val home = File(System.getProperty("user.home"))
    private fun dirOf(folder: String) = File(folder).takeIf { it.isAbsolute } ?: File(home, folder)
    /** A file's folder as the app shows it: "Music/Album/" under your home folder, else its full path. */
    private fun shown(dir: File): String {
        val p = dir.path.replace('\\', '/')
        val h = home.path.replace('\\', '/') + "/"
        return (if (p.startsWith(h)) p.removePrefix(h) else p).trimEnd('/') + "/"
    }

    fun folderCounts(): Map<String, Int> = files.groupingBy { paths[it.file] ?: "" }.eachCount()

    fun scan(folders: List<String>, saved: Set<String>): Boolean {
        val seen = readText(index)?.let { runCatching { JSON.decodeFromString(ser, it) }.getOrNull() } ?: emptyMap()
        val first = seen.isEmpty()
        val keep = HashMap<String, Seen>()
        val out = ArrayList<Track>()
        val where = HashMap<String, String>()
        val genre = HashMap<String, String>()
        for (folder in folders.map(::dirOf).filter { it.isDirectory }) {
            folder.walkTopDown().filter { it.isFile && it.extension.lowercase() in AUDIO }.forEach { f ->
                val path = f.path
                if (path in saved) return@forEach
                val mod = f.lastModified()
                val old = seen[path]
                val s = if (old != null && old.modified == mod) old else read(f, mod, old?.added ?: if (first) mod / 1000.0 else now())
                keep[path] = s
                where[path] = shown(f.parentFile)
                if (s.genre.isNotEmpty()) genre[path] = s.genre
                out += Track(id = "f" + path.hashCode().toUInt().toString(16), source = "file", title = s.title, artist = s.artist,
                    album = s.album, duration = s.duration, file = path, added = s.added)
            }
        }
        out.sortBy { norm(it.title) }
        val changed = out.map { it.file } != files.map { it.file }
        files = out; paths = where; genres = genre; allPaths = where
        if (keep != seen) writeAtomic(index, JSON.encodeToString(ser, keep))
        scanned = now()
        return changed
    }

    /** A file's tags (its name when it has none: "Artist - Title"). */
    private fun read(f: File, mod: Long, added: Double): Seen {
        val (nameArtist, nameTitle) = f.nameWithoutExtension.replace(Regex("^\\d+[ .-]+"), "").split(" - ", limit = 2)
            .let { if (it.size == 2) it[0] to it[1] else "" to it[0] }
        return try {
            val a = org.jaudiotagger.audio.AudioFileIO.read(f)
            val t = a.tag
            fun tag(k: org.jaudiotagger.tag.FieldKey) = runCatching { t?.getFirst(k) }.getOrNull().orEmpty().trim()
            Seen(mod, added, tag(org.jaudiotagger.tag.FieldKey.TITLE).ifEmpty { nameTitle },
                tag(org.jaudiotagger.tag.FieldKey.ARTIST).ifEmpty { nameArtist.ifEmpty { "Unknown artist" } },
                tag(org.jaudiotagger.tag.FieldKey.ALBUM), (a.audioHeader?.trackLength ?: 0).toDouble(), tag(org.jaudiotagger.tag.FieldKey.GENRE))
        } catch (e: Exception) {
            Seen(mod, added, nameTitle, nameArtist.ifEmpty { "Unknown artist" })
        }
    }

    fun everywhere(): Map<String, Track> = files.associateBy { (paths[it.file] ?: "") + File(it.file ?: "").name }

    companion object {
        val AUDIO = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "aiff", "aif", "wma", "alac", "dsf")
        /** The PC has no media library numbers: a song is its path. */
        fun mediaId(uri: String): String? = uri.takeIf { it.isNotEmpty() }
        const val DOWNLOADS = "Music/Radon Player/"
        fun idOf(uri: String?): String? = uri?.takeIf { it.isNotEmpty() }
    }
}
