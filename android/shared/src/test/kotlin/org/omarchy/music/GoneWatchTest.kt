package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A song file that isn't found: not called gone while its drive is out, nor before it has been missing a while. */
class GoneWatchTest {
    private val R = MusicFolderRules

    @Test fun aFolderIsInsideAnotherOnlyUnderIt() {
        assertEquals(listOf("D:\\Music", "D:\\Music FLAC"), R.clean(listOf("D:\\Music FLAC", "D:\\Music")))
        assertEquals("one inside the other: the outer one", listOf("D:\\Music"), R.clean(listOf("D:\\Music\\Rock", "D:\\Music")))
        assertEquals(listOf("/home/me/Music"), R.clean(listOf("/home/me/Music/", "/home/me/Music/Live/")).map { it.trimEnd('/') })
    }

    @Test fun whereASongFilesPlaceIsAsked() {
        assertEquals("its music folder", "E:/Demos", R.rootOf("E:\\Demos\\Yesterday.flac", listOf("C:\\Music", "E:\\Demos")))
        assertEquals("else its drive", "E:/", R.rootOf("E:/Other/x.flac", listOf("C:/Music")))
        assertEquals("the phone's memory card", "/storage/1234-ABCD", R.rootOf("/storage/1234-ABCD/Music/x.flac", listOf("Music/")))
        assertEquals("/run/media/me/USB", R.rootOf("/run/media/me/USB/x.flac", emptyList()))
        assertNull("the phone's own library: nothing to ask", R.rootOf("content://media/external/audio/media/5", listOf("Music/")))
    }

    @Test fun missingForTwoDaysOverThreeReadsBeforeAnythingIsDone() {
        val day = 86_400L
        var (note, ripe) = R.goneWatch(listOf("a", "b"), emptyList(), 1000)
        assertTrue(ripe.isEmpty())
        R.goneWatch(listOf("a", "b"), note, 1000 + day).let { note = it.first; ripe = it.second }
        assertTrue("a day, two reads: not yet", ripe.isEmpty())
        R.goneWatch(listOf("a"), note, 1000 + 3 * day).let { note = it.first; ripe = it.second }
        assertEquals("three reads over three days", setOf("a"), ripe)
        assertFalse("b came back: its note is dropped", note.any { it.startsWith("b|") })
        // three reads in one hour are not enough either
        var n = emptyList<String>()
        repeat(5) { i -> R.goneWatch(listOf("c"), n, 5000L + i * 600).let { n = it.first; assertTrue(it.second.isEmpty()) } }
    }
}
