package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class DeclipTest {
    /** The signal (interleaved) through a Declipper, lined up again (its delay taken off, the tail drained). */
    private fun run(d: Declipper, x: DoubleArray): DoubleArray {
        val out = DoubleArray(x.size)
        val lag = d.delay * d.channels
        for (i in x.indices) { val y = d.next(x[i]); if (i >= lag) out[i - lag] = y }
        for (i in 0 until lag) { val y = d.next(0.0); val j = x.size - lag + i; if (j >= 0) out[j] = y }
        return out
    }

    private fun rms(a: DoubleArray, b: DoubleArray, from: Int, to: Int) =
        sqrt((from until to).sumOf { (a[it] - b[it]) * (a[it] - b[it]) } / (to - from))

    private fun sine(n: Int, hz: Double, amp: Double, rate: Int = 48000) = DoubleArray(n) { amp * sin(2 * PI * hz * it / rate + 0.3) }

    @Test fun clippedSineIsRebuiltCloserToTheRealOne() {
        for (hz in listOf(60.0, 440.0, 1000.0, 3000.0)) for (overDb in listOf(1.0, 3.0)) {
            val truth = sine(9600, hz, Math.pow(10.0, overDb / 20))
            val clipped = DoubleArray(truth.size) { truth[it].coerceIn(-1.0, 1.0) }
            val d = Declipper(1, 48000)
            val fixed = run(d, clipped)
            val before = rms(clipped, truth, 200, 9400)
            val after = rms(fixed, truth, 200, 9400)
            assertTrue("$hz Hz, $overDb dB over: $after should be well under $before", after < before * 0.55)
            assertTrue(d.rebuilt > 0)
            assertTrue(fixed.all { abs(it) <= d.cap })
        }
    }

    @Test fun stereoChannelsAreRebuiltEachOnTheirOwn() {
        val l = sine(4800, 500.0, 1.4); val r = sine(4800, 500.0, 0.5)   // only the left is clipped
        val x = DoubleArray(9600) { if (it % 2 == 0) l[it / 2].coerceIn(-1.0, 1.0) else r[it / 2] }
        val y = run(Declipper(2, 48000), x)
        for (i in 0 until 4800) assertEquals(r[i], y[2 * i + 1], 0.0)   // the clean right side: untouched
        assertTrue(rms(DoubleArray(4800) { y[2 * it] }, l, 100, 4700) < rms(DoubleArray(4800) { x[2 * it] }, l, 100, 4700) * 0.55)
    }

    @Test fun aCleanSignalPassesBitForBit() {
        val x = DoubleArray(20000) { 0.55 * sin(it * 0.05) + 0.28 * sin(it * 0.731) + 0.003 * ((it * 7919) % 13 - 6) }   // peaks under -1 dBFS
        val d = Declipper(2, 44100)
        val y = run(d, x)
        for (i in x.indices) assertEquals(x[i], y[i], 0.0)
        assertEquals(0L, d.rebuilt)
        for (v in x) assertEquals(v, SoftClip.apply(v), 0.0)   // and the soft clip leaves it alone too
    }

    @Test fun switchedOffItOnlyDelays() {
        val x = DoubleArray(4800) { (1.5 * sin(2 * PI * 500.0 * it / 48000)).coerceIn(-1.0, 1.0) }
        val d = Declipper(1, 48000).apply { active = false }
        val y = run(d, x)
        for (i in x.indices) assertEquals(x[i], y[i], 0.0)
        assertEquals(0L, d.rebuilt)
    }

    @Test fun loneFullScaleSamplesAreLeftAlone() {
        val x = DoubleArray(1000) { if (it % 50 == 25) 1.0 else 0.2 }   // a single sample at full scale isn't a clipped run
        val d = Declipper(1, 48000)
        val y = run(d, x)
        for (i in x.indices) assertEquals(x[i], y[i], 0.0)
        assertEquals(0L, d.rebuilt)
    }

    @Test fun softClipIsSmoothAndNeverGoesPastFullScale() {
        var last = 0.0
        var x = 0.0
        while (x <= 4.0) {
            val y = SoftClip.apply(x)
            assertTrue(y <= 1.0 && y >= last)           // rising, never past full scale
            assertEquals(-y, SoftClip.apply(-x), 0.0)   // the same both ways
            last = y; x += 0.001
        }
        // no corner at the knee: the slope just above it is still 1
        val k = SoftClip.KNEE
        assertEquals(1.0, (SoftClip.apply(k + 1e-6) - SoftClip.apply(k)) / 1e-6, 1e-4)
    }

    @Test fun headroomIsThreeDecibels() {
        assertEquals(-3.0, 20 * Math.log10(Smooth.HEADROOM), 1e-9)
    }
}
