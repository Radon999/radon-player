package org.omarchy.music

import java.io.File

/** A change to one of your song files that can't leave you half a file. A tag write puts a file down again, some of
 *  them by moving the music inside it; the app being closed in the middle (Android closes an app working in the
 *  background whenever it likes) left a file no player would open (“Hymn for the Weekend”, 21 Nov 2026: "First frame
 *  does not start with sync code"). Now the change is made to a copy in the app's own folder; the copy is checked
 *  ([FileCheck.sound]); only then is it written over your file, and until that is done it is noted, so a start after
 *  an interruption finishes the job from the copy. A copy that fails the check is thrown away: your file stays as it was. */
object SafeWrite {
    private fun dir(ctx: Ctx) = File(appCtx(ctx).filesDir, "tag-work").apply { mkdirs() }
    private fun journal(ctx: Ctx) = File(dir(ctx), "pending.txt")
    private val lock = Any()

    /** key: where your file is (a path, or the phone's address for it); ext: its kind ("flac"), which the copy keeps.
     *  work changes the copy it is given and may return something. (blocking) */
    fun <T> edit(ctx: Ctx, key: String, ext: String, work: (File) -> T): T = synchronized(lock) {
        recover(ctx)
        val copy = File(dir(ctx), "w-${System.nanoTime()}.$ext")
        val out: T
        try {
            (ctx.openIn(key) ?: error("couldn't open the file")).use { i -> copy.outputStream().use { o -> i.copyTo(o) } }
            if (!FileCheck.sound(copy)) error("the file is already damaged, so it is left alone (it needs replacing)")
            out = work(copy)
            if (!FileCheck.sound(copy)) error("the change left the copy unreadable: your file is untouched")
        } catch (e: Throwable) { copy.delete(); throw e }
        // From here the copy is the truth: noted first, so an interruption while it is written over yours is put right
        writeAtomic(journal(ctx), copy.path + "\n" + key)
        put(ctx, key, copy)
        journal(ctx).delete(); copy.delete()
        out
    }

    private fun put(ctx: Ctx, key: String, copy: File) {
        (ctx.openOut(key) ?: error("couldn't write the file")).use { o -> copy.inputStream().use { it.copyTo(o) }; o.flush() }
    }

    /** (blocking, quick when there is nothing to do) A write that was interrupted is finished from its copy. */
    fun recover(ctx: Ctx) = synchronized(lock) {
        val j = journal(ctx)
        val lines = readText(j)?.lines() ?: return@synchronized
        val copy = File(lines.getOrNull(0).orEmpty()); val key = lines.getOrNull(1).orEmpty()
        runCatching {
            if (key.isNotEmpty() && copy.isFile && FileCheck.sound(copy)) {
                put(ctx, key, copy)
                Crashes.note("A tag write that was interrupted was finished from its copy (${key.substringAfterLast('/')})")
            }
        }.onFailure { return@synchronized }   // (still can't be written: kept for the next start)
        j.delete(); copy.delete()
        // Copies left by a write stopped before it got this far: nothing of yours was touched; they go
        dir(ctx).listFiles()?.filter { it.name.startsWith("w-") }?.forEach { it.delete() }
    }
}
