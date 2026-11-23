package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hi-res music with bit-perfect on reaches the DAC with all its bits. */
class SinkRulesTest {
    @Test fun aSongAboutToGoStraightToTheDacKeepsAllItsBits() {
        // The owner's case: bit-perfect on, the DAC plugged in, no stream to it running at the moment the song is set up
        assertTrue("not brought down to 16 bits", SinkRules.asItIs(bitPerfectNow = false, dacWanted = true))
    }

    @Test fun bitPerfectInEffectKeepsAllItsBits() {
        assertTrue(SinkRules.asItIs(bitPerfectNow = true, dacWanted = false))
        assertTrue(SinkRules.asItIs(bitPerfectNow = true, dacWanted = true))
    }

    @Test fun withoutBitPerfectHiResComesDownForAndroidsOutput() {
        assertFalse("the phone's speaker, Bluetooth, the car: 16 bits, as before", SinkRules.asItIs(bitPerfectNow = false, dacWanted = false))
    }
}
