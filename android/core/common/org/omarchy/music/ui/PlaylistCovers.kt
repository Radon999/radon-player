package org.omarchy.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.omarchy.music.Music
import org.omarchy.music.Prefs
import org.omarchy.music.openIn
import java.io.File

/** A cover of your own for a playlist (its menu › Set a cover…): a picture you pick, shown for the playlist in place
 *  of its first song's cover. A copy of the picture is kept with the app, so the one you picked can be moved or
 *  deleted. It is this device's: a synced playlist keeps its own cover on each device. */
object PlaylistCovers {
    private var version by mutableStateOf(0)
    private val dir get() = File(Music.ctx.filesDir, "playlist-covers")
    private fun key(id: String) = "playlist_cover_$id"

    /** The cover you gave this playlist (a picture file), or null: its first song's cover is shown. */
    fun of(id: String): File? = version.let { Prefs.text(key(id)).takeIf { it.isNotEmpty() }?.let { File(dir, it) }?.takeIf { it.isFile } }

    /** The picture at uri (one you picked) becomes the playlist's cover. */
    fun set(id: String, uri: String) {
        Music.bg({
            val bytes = Music.ctx.openIn(uri)?.use { it.readBytes() } ?: error("the picture couldn't be opened")
            if (bytes.size > 25_000_000) error("that picture is too big (over 25 MB)")
            dir.mkdirs()
            // a new name each time, so the picture shown is never an old one kept from before
            val name = id + "-" + System.currentTimeMillis().toString(36)
            File(dir, name).writeBytes(bytes)
            dir.listFiles()?.filter { it.name.startsWith("$id-") && it.name != name }?.forEach { it.delete() }
            name
        }) { name, err ->
            if (name == null) { Music.toast("Couldn't set the cover: ${err?.message?.take(80)}"); return@bg }
            Prefs.setText(key(id), name); version++; Music.musicVersion++
            Music.toast("Cover set")
        }
    }

    fun clear(id: String) {
        of(id)?.delete(); Prefs.setText(key(id), ""); version++; Music.musicVersion++
        Music.toast("Back to its first song's cover")
    }
}
