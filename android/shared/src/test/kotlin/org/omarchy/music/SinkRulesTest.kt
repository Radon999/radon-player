package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hi-res music with bit-perfect on reaches the DAC with all its bits. */
class SinkRulesTest {
    @Test fun aSongAboutToGoStraightToTheDacKeepsAllItsBits() {
        // The owner's case: bit-perfect on, the DAC plugged in, no stream to it running at the moment the song is set up
        assertFalse("not brought down to 16 bits", SinkRules.toSixteen(hiRes = true, bitPerfectNow = false, dacWanted = true))
        assertTrue(SinkRules.asItIs(bitPerfectNow = false, dacWanted = true))
    }

    @Test fun bitPerfectInEffectKeepsAllItsBits() {
        assertFalse(SinkRules.toSixteen(hiRes = true, bitPerfectNow = true, dacWanted = false))
        assertFalse(SinkRules.toSixteen(hiRes = true, bitPerfectNow = true, dacWanted = true))
    }

    @Test fun withoutBitPerfectHiResComesDownForAndroidsOutput() {
        assertTrue("the phone's speaker, Bluetooth, the car: 16 bits, as before", SinkRules.toSixteen(hiRes = true, bitPerfectNow = false, dacWanted = false))
        assertFalse("16-bit music is never touched", SinkRules.toSixteen(hiRes = false, bitPerfectNow = false, dacWanted = false))
    }
}
