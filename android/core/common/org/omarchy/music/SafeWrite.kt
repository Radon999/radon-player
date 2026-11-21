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
        try {
            (ctx.openIn(key) ?: error("couldn't open the file")).use { i -> copy.outputStream().use { o -> i.copyTo(o) } }
            if (!FileWhole.sound(copy)) error("the file is already damaged, so it is left alone (it needs replacing)")
            val before = FileWhole.soundBytes(copy)
            out = work(copy)
            if (unchanged(out)) { copy.delete(); return@synchronized out }   // nothing to write: your file isn't touched
            if (!FileWhole.sound(copy)) error("the change left the copy unreadable: your file is untouched")
            // The music itself must all still be there (a tag write changes the tags, never the sound)
            if (!FileWhole.kept(before, FileWhole.soundBytes(copy))) error("the change would have cut the music short: your file is untouched")
        } catch (e: Throwable) { copy.delete(); throw e }
        // From here the copy is the truth: noted first, so an interruption while it is written over yours is put right
        writeAtomic(journal(ctx), copy.path + "\n" + key)
        put(ctx, key, copy)
        journal(ctx).delete(); copy.delete()
        out
    }

    /** The copy written over your file, and your file then measured: it must be as long as the copy (once more if not). */
    /** A writer that changes a file where it is (a repair, a tag blanked in place, a WAV's own writer) takes its turn
     *  with every other: never while another write of the app's is under way. */
    fun <T> alone(work: () -> T): T = synchronized(lock) { work() }

    private fun put(ctx: Ctx, key: String, copy: File) {
        repeat(2) {
            (ctx.openOut(key) ?: error("couldn't write the file")).use { o -> copy.inputStream().use { it.copyTo(o) }; o.flush() }
            if (sizeAt(ctx, key) == copy.length()) return
        }
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
