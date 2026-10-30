package org.omarchy.music

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Back up and restore (Settings): what's yours that can't be got back from elsewhere, in one zip. Not your songs,
 *  nor logins (those you have, and sign in again). The PC's backups are its own: each app restores its own. */
object Backup {
    private val FILES = listOf("state.json", "playlists.json", "playlist-folders.json", "favorites.json", "plays.json", "skips.json",
        "song-speeds.json", "song-volume.json", "spotify-history.json", "apple-history.json", "deezer-history.json", "new-releases.json")
    private const val PREFS = "radon-2"   // 2.0's switches (Prefs)

    /** (blocking) Into the zip at uri. How many files went in. */
    fun make(ctx: Ctx, uri: String): Int {
        Music.saveStateNow()
        var n = 0
        ctx.openOut(uri, "w")!!.use { os ->
            ZipOutputStream(os).use { z ->
                z.putNextEntry(ZipEntry("radon-backup.json"))
                z.write("""{"app":"radon-player","platform":"$platformName","made":${System.currentTimeMillis() / 1000}}""".toByteArray())
                z.closeEntry()
                for (name in FILES) {
                    val f = File(ctx.filesDir, name)
                    if (!f.isFile) continue
                    z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry(); n++
                }
                val prefs = prefsFile(ctx, PREFS)
                if (prefs.isFile) { z.putNextEntry(ZipEntry("switches.xml")); prefs.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
        }
        return n
    }

    /** (blocking) Put a backup back: each file in its place, the one there kept as .before-restore. The names restored. */
    fun restore(ctx: Ctx, uri: String): List<String> {
        val got = HashMap<String, ByteArray>()
        ctx.openIn(uri)!!.use { ins ->
            ZipInputStream(ins).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.name == "radon-backup.json" || e.name in FILES || e.name == "switches.xml") got[e.name] = z.readBytes()
                }
            }
        }
        val info = got["radon-backup.json"]?.decodeToString() ?: throw IllegalArgumentException("that isn't a Radon Player backup")
        if ("\"$platformName\"" !in info) throw IllegalArgumentException("that's a backup from the other app (phone or PC): restore it there")
        val done = ArrayList<String>()
        for (name in FILES) {
            val data = got[name] ?: continue
            val f = File(ctx.filesDir, name)
            if (f.exists()) f.copyTo(File(ctx.filesDir, "$name.before-restore"), overwrite = true)
            writeAtomic(f, data.decodeToString())
            done += name
        }
        got["switches.xml"]?.let { prefsFile(ctx, PREFS).apply { parentFile?.mkdirs() }.writeBytes(it) }
        return done
    }

    /** After a restore: the app closes (its music service too), to start afresh with the backup when you open it. */
    fun close(activity: Any?) {
        closeApp(activity)
        Runtime.getRuntime().exit(0)
    }
}
