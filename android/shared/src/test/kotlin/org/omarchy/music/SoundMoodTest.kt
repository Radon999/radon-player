package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** The mood analyzer on made-up sounds: click tracks at known tempos, major and minor chords, quiet and loud, dull and bright. */
class SoundMoodTest {
    private val rate = 22050

    /** Short bursts of noise, bpm a minute, for secs seconds. */
    private fun clicks(bpm: Double, secs: Int = 30, amp: Float = 0.8f): FloatArray {
        val x = FloatArray(secs * rate); val r = java.util.Random(1)
        val period = 60.0 / bpm * rate
        var t = 0.0
        while (t < x.size) {
            val s = t.toInt()
            for (i in 0 until 400) if (s + i < x.size) x[s + i] += (amp * exp(-i / 60.0) * (r.nextFloat() * 2 - 1)).toFloat()
            t += period
        }
        for (i in x.indices) x[i] += 0.01f * (r.nextFloat() * 2 - 1)
        return x
    }

    /** Chords (MIDI notes) one after another, as sine tones. */
    private fun chords(vararg cs: List<Int>, secs: Int = 30, amp: Double = 0.2): FloatArray {
        val x = FloatArray(secs * rate); val per = x.size / cs.size
        for (i in x.indices) {
            val c = cs[minOf(i / per, cs.size - 1)]
            var v = 0.0
            for (m in c) v += sin(2 * PI * 440.0 * 2.0.pow((m - 69) / 12.0) * i / rate)
            x[i] = (amp * v / c.size).toFloat()
        }
        return x
    }

    private fun feat(x: FloatArray) = SoundMood.features(Pcm(x, rate))

    @Test fun tempoOfClickTracks() {
        for (bpm in listOf(75.0, 90.0, 120.0, 128.0, 150.0, 174.0)) {
            val f = feat(clicks(bpm))!!
            assertEquals("tempo of a $bpm BPM click track", bpm, f.bpm, bpm * 0.03)
            assertTrue("a clear beat at $bpm", f.beat > 0.5)
        }
    }

    @Test fun moreClicksMoreOnsets() {
        val slow = feat(clicks(75.0))!!.onsets
        val fast = feat(clicks(150.0))!!.onsets
        assertEquals(1.25, slow, 0.3)
        assertTrue(fast > slow * 1.6)
    }

    @Test fun majorAndMinor() {
        val c = listOf(60, 64, 67); val f = listOf(65, 69, 72); val g = listOf(67, 71, 74)
        assertTrue(feat(chords(c, f, g, c, f, g, c))!!.major > 0.3)
        val am = listOf(57, 60, 64); val dm = listOf(62, 65, 69); val e = listOf(64, 68, 71)
        assertTrue(feat(chords(am, dm, e, am, dm, e, am))!!.major < -0.3)
    }

    @Test fun loudnessAndBrightness() {
        val quietLow = feat(chords(listOf(55), secs = 20, amp = 0.05))!!
        val r = java.util.Random(3)
        val loudNoise = feat(FloatArray(20 * rate) { 0.5f * (r.nextFloat() * 2 - 1) })!!
        assertTrue(loudNoise.loud > quietLow.loud + 10)
        assertTrue(loudNoise.bright > quietLow.bright * 5)
        assertEquals("a held note has no beat", 0.0, quietLow.bpm, 0.0)
    }

    @Test fun silenceAndTooShort() {
        assertNull(feat(FloatArray(20 * rate)))
        assertNull(feat(FloatArray(rate) { 0.5f }))
    }

    @Test fun monoDownHalvesTheRate() {
        val d = MonoDown(44100, 2, 22050, 5)
        val buf = FloatArray(44100 * 2 * 6) { sin(2 * PI * 1000 * (it / 2) / 44100).toFloat() }
        d.feed(buf, buf.size)
        val p = d.result()
        assertEquals(22050, p.rate)
        assertEquals(22050 * 5, p.samples.size)
        assertTrue(d.full)
        // A low file rate stays as it is
        assertEquals(8000, MonoDown(8000, 1, 22050, 1).rate)
    }

    @Test fun moodsFromGenreAndTags() {
        assertEquals("Intense", MoodRules.decide(null, "Death Metal").first().first)
        assertEquals("Hype", MoodRules.decide(null, "Hip-Hop/Rap").first().first)
        assertEquals("Chill", MoodRules.decide(null, "", mapOf("chill" to 1.0, "electronic" to 0.5)).first().first)
        assertEquals("Sad", MoodRules.decide(null, "Pop", mapOf("sad" to 1.0, "melancholic" to 0.8)).first().first)
        assertTrue(MoodRules.decide(null, "Unknown").isEmpty())
        assertTrue(MoodRules.decide(null, "").isEmpty())
        // "emotional" is a mood word, not the emo genre
        assertEquals("Sad", MoodRules.decide(null, "", mapOf("emotional" to 1.0)).first().first)
    }

    @Test fun moodsAreKnownAndAtMostThree() {
        val f = feat(clicks(128.0))
        assertNotNull(f)
        val got = MoodRules.decide(f, "House", mapOf("dance" to 1.0, "party" to 0.6))
        assertTrue(got.size in 1..3)
        assertEquals("Party", got.first().first)
        assertTrue(got.all { it.first in MoodRules.ALL && it.second in 0.0..1.0 })
    }
}
