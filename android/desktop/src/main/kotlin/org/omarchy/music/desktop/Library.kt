package org.omarchy.music.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jaudiotagger.audio.AudioFileIO
import org.omarchy.music.JSON
import org.omarchy.music.Track
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** A song on the PC: its file and what its tags say (the file's name when it has none). */
data class Song(val file: File, val title: String, val artist: String, val album: String, val albumArtist: String,
                val track: Int, val seconds: Int)

/** Your music folders, read: every song in them and below, by artist, album and track. */
object Library {
    val AUDIO = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "aiff", "aif", "wma", "alac", "dsf")

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF   // it logs every file it reads
    }

    private val home = File(System.getProperty("user.home"))
    /** Where the Python app keeps its music and lists (the same files: one library for both while they live side by side). */
    val musicDir = File(System.getenv("OMARCHY_MUSIC_DIR") ?: File(home, "Music/Omarchy Music").path)
    private val stateFile = File(home, ".local/state/omarchy-music.json")

    /** The folders to read: RADON_MUSIC (separated by ;), else the Python app's music folders, else your Music folder. */
    fun folders(): List<File> {
        System.getenv("RADON_MUSIC")?.let { env -> return env.split(';').filter { it.isNotBlank() }.map(::File).filter { it.isDirectory } }
        val theirs = runCatching {
            JSON.parseToJsonElement(stateFile.readText()).let { (it as kotlinx.serialization.json.JsonObject)["folders"]!!.jsonArray }
                .map { File(it.jsonPrimitive.content) }
        }.getOrDefault(emptyList()).filter { it.isDirectory }
        return theirs.ifEmpty { listOf(File(home, "Music")).filter { it.isDirectory } }
    }

    /** A playlist of the Python app's (playlists.json): its name and songs. */
    @Serializable
    data class Playlist(val id: String = "", val name: String = "", val tracks: List<Track> = emptyList())

    fun playlists(): List<Playlist> = runCatching {
        JSON.decodeFromString<List<Playlist>>(File(musicDir, "playlists.json").readText())
    }.getOrDefault(emptyList()).filter { it.tracks.isNotEmpty() }

    /** The song of a playlist's track on this PC: its own file, or the download of it (library.json), or one of your
     *  songs with its name and artist; null if it isn't here. */
    fun fileOf(t: Track, songs: List<Song>): File? {
        t.file?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
        downloads[t.id]?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
        val k = org.omarchy.music.statsKey(t.title, t.artist)
        return songs.firstOrNull { org.omarchy.music.statsKey(it.title, it.artist) == k }?.file
    }

    private val downloads: Map<String, String> by lazy {
        runCatching {
            (JSON.parseToJsonElement(File(musicDir, "library.json").readText()) as kotlinx.serialization.json.JsonObject)
                .mapValues { (_, v) -> ((v as kotlinx.serialization.json.JsonObject)["file"]?.jsonPrimitive?.content).orEmpty() }
                .filterValues { it.isNotEmpty() }
        }.getOrDefault(emptyMap())
    }

    fun scan(folders: List<File> = folders()): List<Song> = folders.flatMap { root ->
        root.walkTopDown().filter { it.isFile && it.extension.lowercase() in AUDIO }.map(::read).toList()
    }.sortedWith(compareBy({ org.omarchy.music.norm(it.albumArtist.ifEmpty { it.artist }) }, { org.omarchy.music.norm(it.album) },
        { it.track }, { org.omarchy.music.norm(it.title) }))

    /** A file's song: its tags, or "Artist - Title" from its name. */
    fun read(f: File): Song {
        val (nameArtist, nameTitle) = f.nameWithoutExtension.replace(Regex("^\\d+[ .-]+"), "").split(" - ", limit = 2)
            .let { if (it.size == 2) it[0] to it[1] else "" to it[0] }
        return try {
            val a = AudioFileIO.read(f)
            val t = a.tag
            fun tag(k: FieldKey) = runCatching { t?.getFirst(k) }.getOrNull().orEmpty().trim()
            Song(f, tag(FieldKey.TITLE).ifEmpty { nameTitle }, tag(FieldKey.ARTIST).ifEmpty { nameArtist.ifEmpty { "Unknown artist" } },
                tag(FieldKey.ALBUM), tag(FieldKey.ALBUM_ARTIST), tag(FieldKey.TRACK).substringBefore('/').toIntOrNull() ?: 0,
                a.audioHeader?.trackLength ?: 0)
        } catch (e: Exception) {
            Song(f, nameTitle, nameArtist.ifEmpty { "Unknown artist" }, "", "", 0, 0)
        }
    }

    /** The songs whose title, artist or album has every word of q in it (tidied as norm does), in their order. */
    fun search(songs: List<Song>, q: String): List<Song> {
        val words = org.omarchy.music.norm(q).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return songs
        return songs.filter { s ->
            val hay = org.omarchy.music.norm("${s.title} ${s.artist} ${s.album}")
            words.all { it in hay }
        }
    }
}
