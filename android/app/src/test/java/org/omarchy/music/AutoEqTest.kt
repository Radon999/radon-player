package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoEqTest {
    private val index = """# Index
- [Sony WH-1000XM6](./Super%20Review/over-ear/Sony%20WH-1000XM6) by Super Review
- [Sony WH-1000XM6 (analog cable)](./Super%20Review/over-ear/Sony%20WH-1000XM6%20(analog%20cable)) by Super Review
- [Apple AirPods Pro 2](./crinacle/711%20in-ear/Apple%20AirPods%20Pro%202) by crinacle on 711
"""
    private val entries = AutoEq.indexEntries(index)

    @Test fun readsTheIndexAndFinds() {
        assertEquals(3, entries.size)
        assertEquals(AutoEq.Entry("Apple AirPods Pro 2", "crinacle", "crinacle/711%20in-ear/Apple%20AirPods%20Pro%202"), entries[2])
        assertEquals(listOf("Sony WH-1000XM6", "Sony WH-1000XM6 (analog cable)"), AutoEq.search(entries, "xm6").map { it.name })
    }

    @Test fun readsAProfileLikeTheBuiltInOne() {
        val f = AutoEq.parseParametric("Preamp: -6.7 dB\nFilter 1: ON LSC Fc 105 Hz Gain -1.5 dB Q 0.70\nFilter 2: ON PK Fc 161 Hz Gain -7.5 dB Q 0.69\n")
        assertEquals(listOf(Filter("ls", 105.0, -1.5, 0.70), Filter("pk", 161.0, -7.5, 0.69)), f)
        val g = AutoEq.graphicToFilters("GraphicEQ: 20 -2.0; 1000 4.0; 20000 -8.0")
        assertEquals(Filter("pk", 1000.0, 4.0, Eq.Q), g[5])
    }

    @Test fun importsEqFiles() {   // Squiglink's export (LSQ/HSQ), Wavelet's GraphicEQ
        val f = AutoEq.parseFile("Preamp: -4.2 dB\nFilter 1: ON LSQ Fc 105 Hz Gain -2.6 dB Q 0.700\nFilter 2: ON HSQ Fc 10000 Hz Gain -7.6 dB Q 0.700\n")
        assertEquals(listOf(Filter("ls", 105.0, -2.6, 0.7), Filter("hs", 10000.0, -7.6, 0.7)), f)
        assertEquals(10, AutoEq.parseFile("GraphicEQ: 20 -1.0; 1000 0.0; 20000 2.0").size)
        assertEquals(10, Eq.HEADPHONES["mangird-top-pro"]!!.second.size)
    }

    @Test fun knowsTheHeadphonesFromTheirName() {
        assertEquals("Sony WH-1000XM6", AutoEq.matchDevice(listOf("WH-1000XM6"), entries)?.name)   // Bluetooth's name
        assertEquals("Apple AirPods Pro 2", AutoEq.matchDevice(listOf("Apple AirPods Pro 2"), entries)?.name)
        assertNull(AutoEq.matchDevice(listOf("AirPods"), entries))
    }

    @Test fun keepsAProfile() {
        val f = listOf(Filter("ls", 105.0, -1.5, 0.70), Filter("pk", 161.0, -7.5, 0.69))
        val s = EqSettings(profiles = mapOf("ae:x" to AutoEq.profileOf("X", f)))
        assertEquals(f, Eq.headphones(s)["ae:x"]!!.second)
    }
}
