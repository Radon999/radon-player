package org.omarchy.music

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Back up and restore (Settings): what's yours that can't be got back from elsewhere, in one zip. Not your songs,
 *  nor logins (those you have, and sign in again). A backup of the phone's restores on the PC and the other way:
 *  then this device's folders, audio output and own switches stay as they are, and songs that were files on the
 *  other device play from the same song of yours here (else the service's). */
object Backup {
    private val FILES = listOf("state.json", "playlists.json", "playlist-folders.json", "favorites.json", "plays.json", "skips.json",
        "song-speeds.json", "song-volume.json", "song-volume-outputs.json", "song-eq.json", "spotify-history.json", "apple-history.json", "deezer-history.json", "new-releases.json")
    /** Kept to the kind of device they were made on: the volumes and EQs you gave songs fit the headphones you use with
     *  it, so a phone's go only to a phone and a PC's only to a PC. */
    private val OWN_DEVICE = setOf("song-volume.json", "song-volume-outputs.json", "song-eq.json")
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
                z.putNextEntry(ZipEntry("switches.json")); z.write(BackupMerge.switchesToSave(Prefs.all()).toByteArray()); z.closeEntry()
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
                    if (e.name == "radon-backup.json" || e.name in FILES || e.name == "switches.xml" || e.name == "switches.json") got[e.name] = z.readBytes()
                }
            }
        }
        val info = got["radon-backup.json"]?.decodeToString() ?: throw IllegalArgumentException("that isn't a Radon Player backup")
        val cross = "\"$platformName\"" !in info   // made on the other device (the phone, or the PC)
        val done = ArrayList<String>()
        for (name in FILES) {
            if (cross && name in OWN_DEVICE) continue   // songs' volumes and EQs are the phone's or the PC's own
            var data = got[name]?.decodeToString() ?: continue
            val f = File(ctx.filesDir, name)
            if (cross && name == "state.json") data = BackupMerge.mergeState(data, readText(f))
            if (f.exists()) f.copyTo(File(ctx.filesDir, "$name.before-restore"), overwrite = true)
            writeAtomic(f, data)
            done += name
        }
        val switches = got["switches.json"]
        if (switches != null) Prefs.restore(BackupMerge.switchesToRestore(switches.decodeToString(), cross))
        else if (!cross) got["switches.xml"]?.let { prefsFile(ctx, PREFS).apply { parentFile?.mkdirs() }.writeBytes(it) }   // an older backup
        if (cross) Prefs.set("adopt_files", true)   // the other device's song files: this one's copies found once it has read its folders
        return done
    }

    /** After a restore: the app closes (its music service too), to start afresh with the backup when you open it. */
    fun close(activity: Any?) {
        closeApp(activity)
        Runtime.getRuntime().exit(0)
    }
}
