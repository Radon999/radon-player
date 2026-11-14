package org.omarchy.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DJ's voice processing: the rate change, the three filters, the limiter, and all of it in order. */
class VoiceDspTest {
    private fun tone(hz: Double, fs: Int, seconds: Double, level: Double = 1.0) =
        FloatArray((fs * seconds).toInt()) { (level * sin(2 * PI * hz * it / fs)).toFloat() }
    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var s = 0.0; for (i in from until to) s += x[i].toDouble() * x[i]; return sqrt(s / (to - from))
    }
    private fun db(ratio: Double) = 20 * log10(ratio)
    private fun peak(x: FloatArray, from: Int = 0, to: Int = x.size): Float { var m = 0f; for (i in from until to) m = maxOf(m, abs(x[i])); return m }

    /** How much louder (dB) a filter makes a steady tone, once it has settled. */
    private fun gain(make: () -> Biquad, hz: Double, fs: Int): Double {
        val x = tone(hz, fs, 1.0, 0.5); val f = make()
        val y = FloatArray(x.size) { f.run(x[it].toDouble()).toFloat() }
        return db(rms(y, fs / 2, fs) / rms(x, fs / 2, fs))
    }

    @Test fun aToneComesOutTheSameToneAtTheNewRateAndInStep() {
        for (to in listOf(48000, 44100, 96000, 192000)) {
            val out = Resampler.resample(tone(1000.0, 24000, 0.5, 0.5), 24000, to)
            assertEquals("length at $to", to / 2, out.size)
            var worst = 0.0
            for (k in to / 10 until out.size - to / 10) worst = maxOf(worst, abs(out[k] - 0.5 * sin(2 * PI * 1000.0 * k / to)))
            assertTrue("at $to Hz the tone is off by $worst", worst < 0.005)
        }
    }

    @Test fun aSteadyLevelStaysThatLevel() {
        val out = Resampler.resample(FloatArray(4800) { 0.5f }, 24000, 44100)
        for (k in 500 until out.size - 500) assertEquals(0.5f, out[k], 1e-4f)
    }

    @Test fun whatAVoiceCantHaveIsntAddedAbove() {
        // 5 kHz at 24 kHz, taken to 48 kHz: its mirror at 19 kHz is gone (it was there, loudly, with straight lines)
        val out = Resampler.resample(tone(5000.0, 24000, 0.5, 0.5), 24000, 48000)
        var s = 0.0; var c = 0.0
        val from = 4800; val to = out.size - 4800
        for (k in from until to) { s += out[k] * sin(2 * PI * 19000.0 * k / 48000); c += out[k] * kotlin.math.cos(2 * PI * 19000.0 * k / 48000) }
        val mirror = 2 * sqrt(s * s + c * c) / (to - from)
        assertTrue("the mirror is ${db(mirror / 0.5)} dB", db(mirror / 0.5) < -70)
    }

    @Test fun anotherRateDownAndTheSameRate() {
        val x = tone(1000.0, 48000, 0.25, 0.5)
        assertEquals(x.toList(), Resampler.resample(x, 48000, 48000).toList())
        val down = Resampler.resample(x, 48000, 44100)
        var worst = 0.0
        for (k in 4410 until down.size - 4410) worst = maxOf(worst, abs(down[k] - 0.5 * sin(2 * PI * 1000.0 * k / 44100)))
        assertTrue("off by $worst", worst < 0.005)
    }

    @Test fun theHighPassTakesRumbleOutAndLeavesTheVoice() {
        for (fs in listOf(44100, 48000, 96000, 192000)) {
            val hp = { Biquad.highPass(fs, 80.0, 0.70710678) }
            assertEquals("80 Hz at $fs", -3.0, gain(hp, 80.0, fs), 0.1)
            assertTrue("20 Hz at $fs", gain(hp, 20.0, fs) < -23)
            assertEquals("1 kHz at $fs", 0.0, gain(hp, 1000.0, fs), 0.05)
            val f = hp(); var last = 0.0
            repeat(fs) { last = f.run(0.5) }   // a steady push: gone
            assertTrue("a steady level at $fs leaves $last", abs(last) < 1e-6)
        }
    }

    @Test fun theShelfAddsWarmthBelowAndNothingAbove() {
        for (fs in listOf(44100, 48000, 96000)) {
            val shelf = { Biquad.lowShelf(fs, 180.0, 2.5, 0.70710678) }
            assertEquals("30 Hz at $fs", 2.5, gain(shelf, 30.0, fs), 0.15)
            assertEquals("180 Hz at $fs", 1.25, gain(shelf, 180.0, fs), 0.1)
            assertEquals("2 kHz at $fs", 0.0, gain(shelf, 2000.0, fs), 0.05)
        }
    }

    @Test fun thePeakAddsPresenceAroundItsNoteOnly() {
        for (fs in listOf(44100, 48000, 96000)) {
            val pk = { Biquad.peak(fs, 3500.0, 2.0, 1.2) }
            assertEquals("3.5 kHz at $fs", 2.0, gain(pk, 3500.0, fs), 0.05)
            assertEquals("200 Hz at $fs", 0.0, gain(pk, 200.0, fs), 0.05)
            assertTrue("15 kHz at $fs", abs(gain(pk, 15000.0, fs)) < 0.3)
        }
    }

    @Test fun theLimiterLeavesQuietSoundExactlyAsItIs() {
        val x = tone(440.0, 48000, 0.5, 0.55)   // under the ceiling by more than the knee
        assertEquals(x.toList(), Limiter.limit(x, 48000).toList())
    }

    @Test fun nothingComesOutOverTheCeiling() {
        assertEquals(0.8414, Limiter.CEILING, 0.0001)
        for (fs in listOf(44100, 48000, 96000)) {
            val x = tone(300.0, fs, 1.0, 0.4)
            for (i in x.indices) {   // bursts far too loud, a click, a jump at the very start and the very end
                if (i % (fs / 5) < fs / 50) x[i] *= 5f
            }
            x[fs / 2 + 7] = 3f; x[0] = -2f; x[x.size - 1] = 2f
            val y = Limiter.limit(x, fs)
            assertEquals(x.size, y.size)
            assertTrue("peak ${peak(y)} at $fs", peak(y) <= Limiter.CEILING * 1.000001)
            assertEquals("cut by the guard at $fs", 0, Limiter.cut)
        }
    }

    @Test fun theLimiterTurnsDownBeforeAPeakAndLetsGoAfter() {
        val fs = 48000
        val x = FloatArray(fs) { 0.3f }
        x[fs / 4] = 2f
        val y = Limiter.limit(x, fs)
        assertEquals("well before the click", 0.3f, y[fs / 4 - 200], 1e-6f)
        assertTrue("just before it, already coming down", y[fs / 4 - 10] < 0.2f)
        assertTrue("the click itself", abs(y[fs / 4]) <= Limiter.CEILING * 1.000001)
        assertTrue("20 ms after, still coming back", y[fs / 4 + 960] < 0.28f)
        assertEquals("half a second after", 0.3f, y[fs / 4 + fs / 2], 0.001f)
        for (i in 1 until fs) if (i != fs / 4 && i != fs / 4 + 1) assertTrue("a jump at $i", abs(y[i] - y[i - 1]) < 0.01f)   // a ramp, never a step
    }

    @Test fun theKneeIsSmooth() {
        assertEquals(1.0, Limiter.gainFor(0.59), 0.0)
        var before = 0.0
        var p = 0.3
        while (p < 4.0) {
            val out = p * Limiter.gainFor(p)
            assertTrue("$p comes out $out", out <= Limiter.CEILING * 1.0000001 && out >= before - 1e-12)
            before = out; p += 0.001
        }
        assertEquals(Limiter.CEILING, 3.0 * Limiter.gainFor(3.0), 1e-9)
    }

    @Test fun aWholeLineComesOutReadyForTheMusic() {
        // Something like speech: a low note and a high one, loud, with gaps, and a thump of rumble under it
        val fs = 24000
        val x = FloatArray(fs * 2) { i ->
            val on = (i / (fs / 4)) % 2 == 0
            ((if (on) 0.6 * sin(2 * PI * 180.0 * i / fs) + 0.5 * sin(2 * PI * 3400.0 * i / fs) else 0.0) + 0.3 * sin(2 * PI * 25.0 * i / fs)).toFloat()
        }
        for (to in listOf(44100, 48000, 96000)) {
            val y = DjVoiceProcessor.process(x, fs, to)
            assertEquals("length at $to", to * 2, y.size)
            assertTrue("peak ${peak(y)} at $to", peak(y) <= Limiter.CEILING * 1.000001)
            assertEquals("cut by the guard at $to", 0, Limiter.cut)
            assertEquals(0f, y[0], 0f); assertEquals(0f, y[y.size - 1], 0f)
            assertTrue("it's still there", rms(y, to / 20, to / 5) > 0.2)
            // In a gap only the rumble is left, and most of that is gone (25 Hz is 20 dB down)
            assertTrue("rumble left: ${peak(y, to * 3 / 8 - to / 50, to * 3 / 8 + to / 50)}", peak(y, to * 3 / 8 - to / 50, to * 3 / 8 + to / 50) < 0.05f)
        }
    }

    @Test fun aQuietLineIsOnlyFilteredAndSetForTwoSpeakers() {
        val y = DjVoiceProcessor.process(tone(1000.0, 24000, 1.0, 0.5), 24000, 48000)
        assertEquals(0.5 * DjVoiceProcessor.UPMIX, peak(y, 12000, 36000).toDouble(), 0.01)   // 1 kHz: no filter moves it
        val two = DjVoiceProcessor.stereo(y)
        assertEquals(y.size * 2, two.size)
        for (i in 10000 until 10100) { assertEquals(y[i], two[2 * i], 0f); assertEquals(y[i], two[2 * i + 1], 0f) }
    }

    @Test fun nothingInNothingOutAndNoBadNumbers() {
        assertEquals(0, DjVoiceProcessor.process(FloatArray(0), 24000, 48000).size)
        val x = tone(500.0, 24000, 0.2, 0.5); x[100] = Float.NaN; x[200] = Float.POSITIVE_INFINITY
        val y = DjVoiceProcessor.process(x, 24000, 48000)
        assertTrue(y.all { !it.isNaN() && !it.isInfinite() })
        assertTrue(DjVoiceProcessor.process(FloatArray(2400), 24000, 48000).all { it == 0f })
    }
}
