package org.omarchy.music.desktop

import org.jaudiotagger.audio.AudioFileIO
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

    /** The folders to read: RADON_MUSIC (folders separated by ;), else your Music folder. */
    fun folders(): List<File> = (System.getenv("RADON_MUSIC")?.split(';')?.filter { it.isNotBlank() }?.map(::File)
        ?: listOf(File(System.getProperty("user.home"), "Music"))).filter { it.isDirectory }

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
