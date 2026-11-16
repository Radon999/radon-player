package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The equalizer's rules: each output keeping its own settings, what plays when a song has its own EQ or volume, the
 *  switch between outputs, Boost, and ReplayGain's factor. */
class EqControlTest {
    private val flat = EqSettings()

    @Test fun anOutputKeepsItsSettingsWithWhenTheyLastChanged() {
        val on = flat.copy(perOutput = true, output = "wired", on = true, bass = 3.0)
        val kept = EqControl.kept(on, at = 1000)
        assertEquals("the output's own copy is what is set now", 3.0, kept.outputs.getValue("wired").bass, 0.0)
        assertEquals("stamped", 1000L, kept.outputs.getValue("wired").at)
        assertEquals("its name is its label", "Headphones (jack)", kept.outputs.getValue("wired").label)
        val again = EqControl.kept(kept, at = 2000)
        assertEquals("nothing changed: the stamp stays (a sync must not think it is newer)", 1000L, again.outputs.getValue("wired").at)
        assertEquals("one EQ for every output: no copy is kept", emptyMap<String, OutputEq>(), EqControl.kept(flat.copy(perOutput = false, output = "wired", bass = 3.0), 1000).outputs)
    }

    @Test fun aSongsOwnEqPlaysOverTheOutputs() {
        val eq = flat.copy(on = false, bass = 2.0, boost = 4.0, preset = "rock")
        val song = OutputEq("x", on = true, preset = "flat", boost = 1.0)
        val p = EqControl.played(eq, song, dual = true, gained = false)
        assertTrue("on, whatever the equalizer's switch says", p.on)
        assertEquals("its Sound", "flat", p.preset)
        assertEquals("its Boost", 1.0, p.boost, 0.0)
        assertEquals("the bass stays the output's", 2.0, p.bass, 0.0)
        assertEquals("no song EQ: the settings as they are", eq, EqControl.played(eq, null, dual = true, gained = false))
    }

    @Test fun withDualVolumeOffASongWithItsOwnVolumeLosesTheBoost() {
        val eq = flat.copy(on = true, boost = 6.0)
        assertEquals(0.0, EqControl.played(eq, null, dual = false, gained = true).boost, 0.0)
        assertEquals("a song without its own volume keeps it", 6.0, EqControl.played(eq, null, dual = false, gained = false).boost, 0.0)
        assertEquals("Dual volume on: both", 6.0, EqControl.played(eq, null, dual = true, gained = true).boost, 0.0)
    }

    @Test fun theSoundMovingToAnotherOutputBringsThatOutputsOwnEq() {
        val wired = flat.copy(perOutput = true, output = "wired", on = true, bass = 3.0)
        val bt = Outputs.Out("bt:Buds", "Buds", "bluetooth")
        val there = EqControl.moved(wired, "wired", bt)
        assertEquals("bt:Buds", there.output)
        assertEquals("a new output starts flat", 0.0, there.bass, 0.0)
        assertEquals("the one left keeps what it had", 3.0, there.outputs.getValue("wired").bass, 0.0)
        val back = EqControl.moved(there.copy(bass = -2.0, on = true), "bt:Buds", Outputs.Out("wired", "Headphones (jack)", "wired"))
        assertEquals("back on the first: its own again", 3.0, back.bass, 0.0)
        assertEquals("and the other's kept", -2.0, back.outputs.getValue("bt:Buds").bass, 0.0)
    }

    @Test fun boostIsKeptInHalfDecibelsUpToTheMost() {
        assertEquals(3.5, EqControl.boost(3.4), 0.0)
        assertEquals(0.0, EqControl.boost(-2.0), 0.0)
        assertEquals(Eq.BOOST_MAX, EqControl.boost(40.0), 0.0)
    }

    @Test fun replayGainNeverPushesASongPastFullScale() {
        assertEquals("a loud song is turned down as tagged", 0.5, EqControl.rgFactor(-6.02, null, -18.0, clipGuard = true), 0.01)
        assertEquals("without Clip guard a quiet song is not made louder", 1.0, EqControl.rgFactor(9.0, 0.5, -18.0, clipGuard = false), 0.0)
        assertEquals("with it: up to where its peak reaches full scale (and a little)", 1.12 / 0.8, EqControl.rgFactor(9.0, 0.8, -18.0, clipGuard = true), 0.001)
        assertEquals("no peak tagged: +6 dB at most", 2.0, EqControl.rgFactor(12.0, null, -18.0, clipGuard = true), 0.0)
        assertEquals("a song already at full scale stays", 1.0, EqControl.rgFactor(9.0, 1.2, -18.0, clipGuard = true), 0.0)
    }

    @Test fun anOutputIsNamedFromItsKey() {
        assertEquals("Built-in speaker", EqControl.outLabel("speaker"))
        assertEquals("Built-in speaker", EqControl.outLabel(""))
        assertEquals("Buds Pro", EqControl.outLabel("bt:Buds Pro"))
    }
}
