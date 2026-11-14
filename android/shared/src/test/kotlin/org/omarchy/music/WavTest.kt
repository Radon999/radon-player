package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WavTest {
    /** A 16-bit WAV file's bytes: the samples given, channels interleaved, with an odd chunk before its sound. */
    private fun wav(rate: Int, channels: Int, samples: ShortArray): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        fun s(t: String) = o.write(t.toByteArray(Charsets.ISO_8859_1))
        fun u32(v: Int) { o.write(v); o.write(v shr 8); o.write(v shr 16); o.write(v shr 24) }
        fun u16(v: Int) { o.write(v); o.write(v shr 8) }
        s("RIFF"); u32(0); s("WAVE")
        s("fmt "); u32(16); u16(1); u16(channels); u32(rate); u32(rate * channels * 2); u16(channels * 2); u16(16)
        s("LIST"); u32(3); o.write(byteArrayOf(1, 2, 3, 0))   // an odd length, padded: skipped
        s("data"); u32(samples.size * 2); samples.forEach { u16(it.toInt()) }
        return o.toByteArray()
    }

    @Test fun readsOneChannel() {
        val (s, rate) = wavSamples(wav(24000, 1, shortArrayOf(0, 16384, -16384, 32767)))!!
        assertEquals(24000, rate)
        assertEquals(4, s.size)
        assertEquals(0.5f, s[1], 1e-4f); assertEquals(-0.5f, s[2], 1e-4f)
    }

    @Test fun twoChannelsBecomeOne() {
        val (s, rate) = wavSamples(wav(44100, 2, shortArrayOf(16384, 0, -16384, -16384)))!!
        assertEquals(44100, rate)
        assertEquals(2, s.size)
        assertEquals(0.25f, s[0], 1e-4f); assertEquals(-0.5f, s[1], 1e-4f)
    }

    @Test fun notAWavIsNothing() {
        assertNull(wavSamples(ByteArray(100)))
        assertNull(wavSamples("RIFF".toByteArray()))
    }

    @Test fun samplesBackToBytes() {
        val b = pcm16(floatArrayOf(0f, 1f, -1f, 2f))
        assertEquals(8, b.size)
        assertEquals(0x7FFF, (b[2].toInt() and 0xFF) or (b[3].toInt() shl 8))
        assertEquals(0x7FFF, (b[6].toInt() and 0xFF) or (b[7].toInt() shl 8))   // over full scale: held at it
    }
}
