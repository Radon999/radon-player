package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A filter stays a filter: its frequency under half the sample rate, its width a real width. */
class EqRulesTest {
    @Test fun aBandOverHalfTheSampleRateIsKeptUnderIt() {
        assertEquals(1000.0, EqRules.safeHz(1000.0, 44100.0), 0.0)
        assertEquals(16000.0, EqRules.safeHz(16000.0, 44100.0), 0.0)
        assertEquals(44100 * 0.45, EqRules.safeHz(22050.0, 44100.0), 1e-9)
        assertEquals(44100 * 0.45, EqRules.safeHz(24000.0, 44100.0), 1e-9)
        assertEquals("on 96 kHz music it is fine as it is", 24000.0, EqRules.safeHz(24000.0, 96000.0), 0.0)
        assertEquals(10.0, EqRules.safeHz(0.0, 48000.0), 0.0)
        assertTrue(EqRules.safeHz(Double.NaN, 48000.0) in 10.0..21600.0)
    }

    @Test fun aWidthIsARealWidth() {
        assertEquals(1.41, EqRules.safeQ(1.41), 0.0)
        assertEquals(0.05, EqRules.safeQ(0.0), 0.0)
        assertEquals(0.05, EqRules.safeQ(-3.0), 0.0)
        assertEquals(50.0, EqRules.safeQ(900.0), 0.0)
        assertEquals(0.71, EqRules.safeQ(Double.NaN), 0.0)
    }
}
