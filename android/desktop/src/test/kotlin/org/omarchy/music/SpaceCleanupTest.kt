package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Clear unimportant cache and data: only the leftovers go, and only ones done with; everything of value stays. The
 *  files are the test's own, in its own folder. */
class SpaceCleanupTest {
    private val hoursAgo = System.currentTimeMillis() - 3 * 60 * 60 * 1000L

    private fun file(dir: File, name: String, old: Boolean = true) = File(dir, name).apply {
        parentFile.mkdirs(); writeText("x".repeat(100)); if (old) setLastModified(hoursAgo)
    }

    @Test fun onlyLeftoversDoneWithGo() {
        val ctx = Ctx(java.nio.file.Files.createTempDirectory("cleanup-data").toFile(), java.nio.file.Files.createTempDirectory("cleanup-cache").toFile())
        val data = ctx.filesDir; val cache = ctx.cacheDir
        // of value: settings, lyrics, the voice, backups, the caches that make it fast
        val keep = listOf(file(data, "state.json"), file(data, "playlists.json"), file(data, "lyrics-kept/a.json"),
            file(data, "lyrics-scan.json"), file(data, "dj-voice-pocket/lm_main.int8.onnx"), file(data, "lyric-hearing/x/tokens.txt"),
            file(data, "auto-backups/radon-backup-2026-11-20.zip"), file(data, "artist-pics/a.jpg"), file(data, "covers/c.jpg"),
            file(cache, "lyrics/a.json"), file(cache, "genius-notes/a.json"), file(cache, "cover-cache/a.jpg"), file(cache, "art/a.0"),
            file(cache, "local.json"), file(cache, "autoeq-index.md"), file(cache, "ahead/next.flac"))
        // leftovers, done with
        val go = listOf(file(cache, "now-art/old.jpg"), file(cache, "song.part"), file(cache, "song.yt"), file(cache, "song.flac"),
            file(cache, "update.apk"), file(cache, "dj-line.mp3"), file(cache, "sync-out.zip"), file(cache, "shared/card.png"),
            file(data, "lyric-hearing/model.tar.bz2"), file(data, "dj-voice-pocket/pocket.tar.bz2"), file(data, "auto-backups/x.zip.part"),
            file(data, "tag-work/w-1.flac"), file(cache, "WebView/Default/HTTP Cache/data_1"))
        // leftovers still being written: stay
        val busy = listOf(file(cache, "downloading.part", old = false), file(data, "lyric-hearing/model.tar.bz2.part", old = false))

        val found = SpaceCleanup.look(ctx).flatMap { it.files }
        assertTrue("nothing of value is offered: ${found.filter { f -> keep.any { it == f } }}", found.none { f -> keep.any { it == f } })
        SpaceCleanup.clear(ctx)
        keep.forEach { assertTrue("kept: $it", it.isFile) }
        go.forEach { assertFalse("cleared: $it", it.exists()) }
        busy.forEach { assertTrue("still being written: kept: $it", it.isFile) }
        assertEquals("nothing left to clear", emptyList<SpaceCleanup.Kind>(), SpaceCleanup.look(ctx))
    }
}
