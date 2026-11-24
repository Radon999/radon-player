package org.omarchy.music

import java.io.File

/** A change to one of your song files that can't leave you half a file. A tag write puts a file down again, some of
 *  them by moving the music inside it; the app being closed in the middle (Android closes an app working in the
 *  background whenever it likes) left a file no player would open (“Hymn for the Weekend”, 21 Nov 2026: "First frame
 *  does not start with sync code"). Now the change is made to a copy in the app's own folder; the copy is checked
 *  ([FileWhole.sound]); only then is it written over your file, and until that is done it is noted, so a start after
 *  an interruption finishes the job from the copy. A copy that fails the check is thrown away: your file stays as it was.
 *
 *  One change at a time, for every writer: two that rewrote the same file at once (the lyrics scan and the moods scan,
 *  each reading the whole file, changing its tag and writing it all back) could read it while the other was half way
 *  through writing it, and the two writes landed on top of each other: MP3s with big covers in them kept their size and
 *  came out 10 to 30 seconds long in every player (“New Wave”, “Prime Time (v2)”, 21 Nov 2026). So every tag write of the app's goes through here, and a change
 *  that leaves less sound in the copy than it started with ([FileWhole.kept]) is thrown away. */
object SafeWrite {
    private fun dir(ctx: Ctx) = File(appCtx(ctx).filesDir, "tag-work").apply { mkdirs() }
    private fun journal(ctx: Ctx) = File(dir(ctx), "pending.txt")
    private val lock = Any()

    /** key: where your file is (a path, or the phone's address for it); ext: its kind ("flac"), which the copy keeps.
     *  work changes the copy it is given and may return something. (blocking) */
    fun <T> edit(ctx: Ctx, key: String, ext: String, unchanged: (T) -> Boolean = { false }, work: (File) -> T): T = synchronized(lock) {
        recover(ctx)
        val copy = File(dir(ctx), "w-${System.nanoTime()}.$ext")
        val out: T
        // (a change still waiting for this song to finish playing: this one is made on top of it)
        val waiting = later(ctx)[fileKey(key)]?.takeIf { it.isFile }
        try {
            if (waiting != null) waiting.copyTo(copy, overwrite = true)
            else (ctx.openIn(key) ?: error("couldn't open the file")).use { i -> copy.outputStream().use { o -> i.copyTo(o) } }
            if (!FileWhole.sound(copy)) error("the file is already damaged, so it is left alone (it needs replacing)")
            val before = FileWhole.soundBytes(copy)
            out = work(copy)
            if (unchanged(out)) { copy.delete(); return@synchronized out }   // nothing to write: your file isn't touched
            if (!FileWhole.sound(copy)) error("the change left the copy unreadable: your file is untouched")
            // The music itself must all still be there (a tag write changes the tags, never the sound)
            if (!FileWhole.kept(before, FileWhole.soundBytes(copy))) error("the change would have cut the music short: your file is untouched")
        } catch (e: Throwable) { copy.delete(); throw e }
        // The song is playing (or is the next one): its file isn't touched under the player. The finished copy is kept,
        // and put in its place once the song is free (when the next song starts, or the app does).
        if (busy(key)) {
            val keep = File(dir(ctx), "later-${System.nanoTime()}.$ext")
            if (!copy.renameTo(keep)) { copy.copyTo(keep, overwrite = true); copy.delete() }
            val all = later(ctx)
            all.put(fileKey(key), keep)?.takeIf { it != keep }?.delete()
            laterKeys[fileKey(key)] = key
            saveLater(ctx)
            runCatching { Music.toast("That song is playing: the change goes into its file when it has finished") }
            return@synchronized out
        }
        later(ctx).remove(fileKey(key))?.also { it.delete(); laterKeys.remove(fileKey(key)); saveLater(ctx) }   // (this change holds that one's)
        // From here the copy is the truth: noted first, so an interruption while it is written over yours is put right
        writeAtomic(journal(ctx), copy.path + "\n" + key)
        put(ctx, key, copy)
        journal(ctx).delete(); copy.delete()
        out
    }

    // ---- never under the player ----

    /** A change to a song that is playing can't be made now. */
    class Busy(what: String) : RuntimeException(what)

    /** Whether the file is the song playing or the next one (Music.inUse): never written then. */
    private fun busy(key: String): Boolean = runCatching { Music.inUse(key) }.getOrDefault(false)

    /** The file being written over at this moment (as [fileKey] names it), for the player: a song isn't opened while
     *  its file is half way through being put down. */
    @Volatile private var writing: String? = null
    fun isWriting(uri: String?): Boolean = uri != null && writing != null && writing == fileKey(uri)

    // Changes waiting for their song to finish playing: the file's key -> the finished copy (and the address to write to)
    private var laterMap: LinkedHashMap<String, File>? = null
    private val laterKeys = HashMap<String, String>()
    private fun laterFile(ctx: Ctx) = File(dir(ctx), "later.txt")
    private fun later(ctx: Ctx): LinkedHashMap<String, File> = laterMap ?: LinkedHashMap<String, File>().also { m ->
        readText(laterFile(ctx))?.lines()?.forEach { l ->
            val p = l.split('\t'); if (p.size == 2 && File(p[0]).isFile) { m[fileKey(p[1])] = File(p[0]); laterKeys[fileKey(p[1])] = p[1] }
        }
        laterMap = m
    }
    private fun saveLater(ctx: Ctx) {
        val m = later(ctx)
        if (m.isEmpty()) laterFile(ctx).delete() else writeAtomic(laterFile(ctx), m.entries.joinToString("\n") { it.value.path + "\t" + (laterKeys[it.key] ?: it.key) })
    }

    /** (blocking, quick when nothing waits) The changes that waited for their songs to finish playing are put into
     *  their files, each once its song is free. Asked when a song starts and when the app does. */
    fun writeWaiting(ctx: Ctx) = synchronized(lock) {
        val m = later(ctx)
        if (m.isEmpty()) return@synchronized
        for ((k, copy) in m.entries.toList()) {
            val key = laterKeys[k] ?: k
            if (busy(key)) continue
            runCatching {
                if (copy.isFile && FileWhole.sound(copy)) {
                    writeAtomic(journal(ctx), copy.path + "\n" + key)
                    put(ctx, key, copy)
                    journal(ctx).delete()
                }
                copy.delete(); m.remove(k); laterKeys.remove(k)
            }
        }
        saveLater(ctx)
    }

    /** The copies in the app's tag-work folder that nothing will use any more (a write cut short before its copy was
     *  noted): not the note of a write under way (pending.txt) or its copy, not a change waiting for its song to finish
     *  playing (later.txt and its copies). Each one is a whole song. (blocking: waits for a write under way) */
    fun leftovers(ctx: Ctx): List<File> = synchronized(lock) {
        val keep = HashSet<String>()
        keep += journal(ctx).path; keep += laterFile(ctx).path
        later(ctx).values.forEach { keep += it.path }
        readText(journal(ctx))?.lineSequence()?.firstOrNull()?.let { keep += it }
        dir(ctx).listFiles()?.filter { it.isFile && it.path !in keep }.orEmpty()
    }

    /** The copy written over your file, and your file then measured: it must be as long as the copy (once more if not). */
    /** A writer that changes a file where it is (a repair, a tag blanked in place, a WAV's own writer) takes its turn
     *  with every other: never while another write of the app's is under way. */
    fun <T> alone(key: String? = null, work: () -> T): T = synchronized(lock) {
        // (key: the file it changes; none for the player's own repair of the song it is opening)
        if (key != null && busy(key)) throw Busy("the song is playing: its file is changed another time")
        writing = key?.let { fileKey(it) }
        try { work() } finally { writing = null }
    }

    private fun put(ctx: Ctx, key: String, copy: File) {
        writing = fileKey(key)
        try {
            repeat(2) {
                (ctx.openOut(key) ?: error("couldn't write the file")).use { o -> copy.inputStream().use { it.copyTo(o) }; o.flush() }
                if (sizeAt(ctx, key) == copy.length()) return
            }
        } finally { writing = null }
        error("the file didn't take the whole of its new copy (it is kept, and written again at the next start)")
    }

    private fun sizeAt(ctx: Ctx, key: String): Long = runCatching {
        if (key.startsWith("/")) File(key).length()
        else ctx.openIn(key)?.use { s -> val b = ByteArray(1 shl 16); var n = 0L; while (true) { val r = s.read(b); if (r < 0) break; n += r }; n } ?: -1L
    }.getOrDefault(-1L)

    /** (blocking, quick when there is nothing to do) A write that was interrupted is finished from its copy. */
    fun recover(ctx: Ctx) = synchronized(lock) {
        val j = journal(ctx)
        val lines = readText(j)?.lines() ?: return@synchronized
        val copy = File(lines.getOrNull(0).orEmpty()); val key = lines.getOrNull(1).orEmpty()
        runCatching {
            if (key.isNotEmpty() && copy.isFile && FileWhole.sound(copy)) {
                put(ctx, key, copy)
                Crashes.note("A tag write that was interrupted was finished from its copy (${key.substringAfterLast('/')})")
            }
        }.onFailure { return@synchronized }   // (still can't be written: kept for the next start)
        j.delete(); copy.delete()
        // Copies left by a write stopped before it got this far: nothing of yours was touched; they go
        dir(ctx).listFiles()?.filter { it.name.startsWith("w-") }?.forEach { it.delete() }
    }
}
