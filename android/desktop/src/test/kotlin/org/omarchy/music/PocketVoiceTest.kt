package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The DJ's natural voice (Pocket TTS) made for real, inside the app's own code, on this machine: the library loads,
 *  the voice speaks a line, and what comes out is what the rest of the DJ's sound expects (24 kHz, one channel, not
 *  silence, about as long as the line takes to say). Runs where the voice has been fetched (the checks do it and name
 *  its folder in RADON_VOICE_DIR); skipped anywhere else, so the tests never download 95 MB by themselves. */
class PocketVoiceTest {
    @Test fun theVoiceSpeaksALine() {
        val dir = System.getenv("RADON_VOICE_DIR")?.let { java.io.File(it) }
        assumeTrue("the voice isn't fetched here", dir != null && DjVoices.pocketReady(dir))
        val line = "Good evening. Let's settle in with some music."
        val t0 = System.nanoTime()
        val (samples, rate) = Voice.synth(line, from = dir!!)!!
        val first = (System.nanoTime() - t0) / 1e9
        assertEquals(24000, rate)
        val seconds = samples.size / rate.toDouble()
        assertTrue("about as long as the line takes to say ($seconds s)", seconds in 1.5..8.0)
        assertTrue("not silence", samples.maxOf { kotlin.math.abs(it) } > 0.05f)
        assertTrue("within full scale", samples.all { it in -1.0001f..1.0001f })
        // Again: the voice is loaded now, so this is how long a line takes while the app runs
        val t1 = System.nanoTime()
        val again = Voice.synth("Three in a row from Loveless by My Bloody Valentine, in order, the way the record runs.", from = dir)!!
        val took = (System.nanoTime() - t1) / 1e9
        val spoke = again.first.size / again.second.toDouble()
        println("POCKET VOICE: first line %.1f s of speech in %.1f s (with loading the voice); then %.1f s of speech in %.1f s (%.1fx real time)"
            .format(seconds, first, spoke, took, spoke / took))
        // Stopped before it starts: nothing is made
        assertEquals(null, Voice.synth(line, from = dir, go = { false }))
    }
}
