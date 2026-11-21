package org.omarchy.music

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A song that is playing (or next) is never written under the player: a scan leaves it for another time, any other
 *  change is made on a copy that waits and goes into the file once the song is free. */
class SafeWriteInUseTest {
    // An MP3: a small ID3 tag, then frames (MPEG 1 layer III, 128 kbit/s)
    private fun mp3(dir: File, name: String): File {
        val tag = TagWrite.buildId3(TagWrite.Id3(3, ArrayList()), padding = 64)
        val frame = byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x00) + ByteArray(413) { 0x55 }
        return File(dir, name).apply { outputStream().use { o -> o.write(tag); repeat(300) { o.write(frame) } } }
    }
    private fun tags(f: File) = f.inputStream().use { MoodTag.readTags(it, setOf(MoodTag.LYRICS, MoodTag.MOOD)) }.orEmpty()

    @Test fun aChangeToTheSongPlayingWaitsUntilItIsFree() {
        val ctx = Harness.ctx
        val dir = File(ctx.filesDir, "inuse-${System.nanoTime()}").apply { mkdirs() }
        val f = mp3(dir, "playing.mp3"); val other = mp3(dir, "other.mp3")
        val t = Track("file-playing", "file", "Playing", "Band", file = f.path)
        val was = f.readBytes(); val sound = FileWhole.soundBytes(f)
        try {
            // it is the song playing
            Harness.ui { Music.queue = listOf(t); Music.index = 0; PlaybackCore.loadedFor = t.id; Music.paused = false }
            assertTrue(Music.inUse(f.path)); assertTrue("another address of the same file", Music.inUse("file://" + f.path)); assertFalse(Music.inUse(other.path))
            // a scan leaves it for another time
            assertFalse(LyricsTags.writeMood(ctx, f.path, "Happy"))
            assertFalse(LyricsTags.writeTags(ctx, f.path, "words", emptyMap()))
            assertArrayEquals("not a byte of it changed", was, f.readBytes())
            // any other change is made, and waits: the file is as it was
            SafeWrite.edit(ctx, f.path, "mp3") { c -> c.writeBytes(TagWrite.mp3(c.readBytes(), "the words", emptyMap())!!) }
            assertArrayEquals("the file isn't touched while it plays", was, f.readBytes())
            // …a second change is made on top of the first
            SafeWrite.edit(ctx, f.path, "mp3") { c -> c.writeBytes(TagWrite.mp3(c.readBytes(), null, emptyMap(), "Happy")!!) }
            assertArrayEquals(was, f.readBytes())
            SafeWrite.writeWaiting(ctx)
            assertArrayEquals("still playing: still waiting", was, f.readBytes())
            // a writer that changes a file where it is refuses
            assertTrue(runCatching { SafeWrite.alone(f.path) { f.appendBytes(byteArrayOf(1)) } }.exceptionOrNull() is SafeWrite.Busy)
            assertArrayEquals(was, f.readBytes())
            // a song that isn't playing is written at once
            SafeWrite.edit(ctx, other.path, "mp3") { c -> c.writeBytes(TagWrite.mp3(c.readBytes(), "other words", emptyMap())!!) }
            assertEquals("other words", tags(other)[MoodTag.LYRICS])
            // the song ends: both changes go in, and the music is what it was
            Harness.ui { Music.queue = emptyList(); Music.index = -1; PlaybackCore.loadedFor = null; Music.paused = true }
            assertFalse(Music.inUse(f.path))
            SafeWrite.writeWaiting(ctx)
            assertEquals("the words", tags(f)[MoodTag.LYRICS]); assertEquals("Happy", tags(f)[MoodTag.MOOD])
            assertEquals(sound, FileWhole.soundBytes(f))
            assertFalse(SafeWrite.isWriting(f.path))
        } finally {
            Harness.ui { Music.queue = emptyList(); Music.index = -1; PlaybackCore.loadedFor = null; Music.paused = true }
        }
    }
}
