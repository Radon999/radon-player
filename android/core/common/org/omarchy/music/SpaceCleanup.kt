package org.omarchy.music

import java.io.File

/** Settings › General › Clear unimportant cache and data: frees the space the app's leftovers take, and nothing else.
 *  It goes only by the list below, each a kind of file the app makes for a moment or makes again by itself, and only
 *  ones not touched for an hour (so nothing being downloaded, shared or written now). Everything else stays: your
 *  settings, playlists, favorites, plays and stats, the equalizers and their presets, lyrics (kept, found, timed) and
 *  the lyrics scan, moods, sign-ins, backups, the pictures you picked, the DJ's voice and auto-sync's listening model,
 *  the library's index, and the caches that make it fast (covers and artist photos, online lyrics, Genius notes). */
object SpaceCleanup {
    /** One kind of leftover: what it is, as said to you, and its files now. */
    data class Kind(val what: String, val files: List<File>) {
        val bytes: Long get() = files.sumOf { size(it) }
    }

    /** Not touched for this long: done with (a download or a write under way keeps touching its file). */
    const val IDLE_MS = 60 * 60 * 1000L

    private fun size(f: File): Long = if (f.isDirectory) f.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else f.length()
    // A folder by the files in it (its own date changes as files come and go)
    private fun idle(f: File, now: Long) = now - (if (f.isDirectory) f.walkBottomUp().filter { it.isFile }.maxOfOrNull { it.lastModified() } ?: 0L else f.lastModified()) > IDLE_MS
    private fun files(dir: File) = dir.listFiles()?.toList().orEmpty()

    /** (blocking) The leftovers there are now, kind by kind (the empty kinds left out). */
    fun look(ctx: Ctx, now: Long = System.currentTimeMillis()): List<Kind> {
        val cache = appCtx(ctx).cacheDir
        val data = appCtx(ctx).filesDir
        fun done(fs: List<File>) = fs.filter { it.exists() && idle(it, now) }
        val shown = runCatching { NowArt.inUse() }.getOrDefault(emptySet())
        val kinds = listOf(
            // Now playing's copy of a song's cover from its file: made again from the file whenever it's needed (it's
            // made afresh each time the app starts), so the ones of songs played before only take room
            Kind("Covers copied out of songs played before (made again from the song when it plays)",
                files(File(cache, "now-art")).filter { it.path !in shown }.let(::done)),
            // A download's pieces, in the cache while it's fetched and tagged; a finished one is moved to your music
            Kind("Pieces of downloads that were cut short",
                done(files(cache).filter { it.isFile && (it.name.endsWith(".part") || it.name.endsWith(".yt") ||
                    it.name.endsWith(".flac") || it.name.endsWith(".mp3")) && !it.name.startsWith("dj-line") })),
            Kind("An app update already installed (or not wanted)", done(listOf(File(cache, "update.apk")))),
            // The DJ's last spoken line, a sync's packed-up copy before it's sent, an import's copy, a picture or clip you shared
            Kind("Short-lived files: the DJ's last line, a sync's or an import's copy, pictures and clips you shared",
                done(files(cache).filter { it.isFile && (it.name.startsWith("dj-line") || it.name == "sync-out.zip" || it.name == "deezer-import.tmp") } +
                    files(File(cache, "shared")))),
            // A download of the DJ's voice, auto-sync's listening model or a backup the app made, cut short (the
            // finished ones are unpacked or kept and these deleted)
            Kind("Downloads of the DJ's voice or the listening model, and backups, cut short",
                done(listOfNotNull(File(data, "dj-voice-pocket/pocket.tar.bz2"), File(data, "dj-voice-pocket/pocket.tar.bz2.part"),
                    File(data, "lyric-hearing/model.tar.bz2"), File(data, "lyric-hearing/model.tar.bz2.part")) +
                    files(File(data, "auto-backups")).filter { it.name.endsWith(".part") })),
            // A tag write works on a copy of the song; one cut short before it was noted is never used (a change waiting
            // for its song to finish playing, and one being put back, stay)
            Kind("Copies of songs left by a tag change that was cut short",
                done(runCatching { SafeWrite.leftovers(ctx) }.getOrDefault(emptyList()))),
            // The sign-in pages' web cache (the phone): pages and scripts, fetched again; the sign-ins aren't in it
            Kind("The sign-in pages' web cache",
                done(File(cache, "WebView").takeIf { it.isDirectory }?.walkTopDown()
                    ?.filter { it.isDirectory && it.name in setOf("HTTP Cache", "Code Cache", "GPUCache") }?.toList().orEmpty())),
        )
        return kinds.filter { it.files.isNotEmpty() }
    }

    /** (blocking) The leftovers deleted: how many bytes were freed. */
    fun clear(ctx: Ctx): Long {
        var freed = 0L
        for (k in look(ctx)) for (f in k.files) {
            val n = size(f)
            if (if (f.isDirectory) f.deleteRecursively() else f.delete()) freed += n
        }
        return freed
    }

    fun mb(bytes: Long): String = if (bytes < 1_000_000) "%,d KB".format(bytes / 1000) else "%,.1f MB".format(bytes / 1e6)
}
