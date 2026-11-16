package org.omarchy.music

import org.junit.Assert.assertNotNull
import org.junit.Test

/** Needs the network: YouTube still gives its audio to NewPipe's extractor. YouTube now and then turns the build
 *  server away (the same test passes on the next run), so it's asked up to three times before it counts as failed. */
class YouTubeAudioTest {
    @Test
    fun findsAudio() {
        var got: Pair<String, String>? = null
        var last: Throwable? = null
        for (attempt in 1..3) {
            try { got = YouTubeAudio.audio("dQw4w9WgXcQ") } catch (e: Exception) { last = e }
            if (got != null) break
            println("YouTube audio: nothing on try $attempt (${last?.javaClass?.simpleName}: ${last?.message?.take(120)})")
            if (attempt < 3) Thread.sleep(8_000)
        }
        println("YouTube audio: ${got?.second} ${got?.first?.take(60)}")
        if (got == null && last != null) throw last
        assertNotNull(got)
    }
}
