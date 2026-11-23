package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BtIgnoreRulesTest {
    private val car = setOf("Chevy")

    @Test fun listsRoundTrip() {
        assertEquals(listOf("a", "b"), BtIgnoreRules.parse(" a\n\nb\na "))
        assertEquals("a\nb", BtIgnoreRules.join(listOf("a", " b", "a", "")))
    }

    @Test fun seenKeepsNewestUpToTheLimit() {
        assertEquals(listOf("a", "b"), BtIgnoreRules.remember(listOf("a"), listOf("b", "a", "b")))
        val full = BtIgnoreRules.remember((1..30).map { "$it" }, listOf("new"))
        assertEquals(30, full.size)
        assertEquals("new", full.last())
        assertEquals("2", full.first())
    }

    @Test fun keepsTheHeadphonesWhenTheCarComes() {
        assertEquals("XM6", BtIgnoreRules.keepOn(true, car, listOf("Chevy", "XM6"), "XM6", listOf("XM6", "Chevy")))
    }

    @Test fun leavesTheSoundAlone() {
        assertNull(BtIgnoreRules.keepOn(false, car, listOf("Chevy"), "XM6", listOf("XM6")))          // ignoring off
        assertNull(BtIgnoreRules.keepOn(true, car, listOf("XM6"), "XM6", listOf("XM6")))             // no ignored device
        assertNull(BtIgnoreRules.keepOn(true, car, listOf("Chevy"), "XM6", listOf("Chevy")))         // headphones gone
        assertNull(BtIgnoreRules.keepOn(true, car, listOf("Chevy"), null, listOf("Chevy")))          // nothing before
        assertNull(BtIgnoreRules.keepOn(true, car, listOf("Chevy"), "Chevy", listOf("Chevy")))       // before was ignored
        assertNull(BtIgnoreRules.keepOn(true, emptySet(), listOf("Chevy"), "XM6", listOf("XM6")))    // none picked
    }

    @Test fun previousStaysWhileIgnoring() {
        assertEquals("XM6", BtIgnoreRules.previousAfter(true, car, listOf("XM6"), "XM6", null))
        assertEquals("XM6", BtIgnoreRules.previousAfter(true, car, listOf("Chevy", "XM6"), "Chevy", "XM6"))
        assertNull(BtIgnoreRules.previousAfter(false, car, listOf("Chevy"), "Chevy", "XM6"))
        assertNull(BtIgnoreRules.previousAfter(true, car, emptyList(), null, "XM6"))
    }

    @Test fun newestWins() {
        val at = mapOf("Buds" to 100L, "Truck" to 200L)
        assertEquals("Truck", BtIgnoreRules.newest(listOf("Buds", "Truck"), at, emptySet()))
        // Ignored first: the truck is ignored, so nothing to choose between
        assertNull(BtIgnoreRules.newest(listOf("Buds", "Truck"), at, setOf("Truck")))
        // One device: nothing to decide
        assertNull(BtIgnoreRules.newest(listOf("Buds"), at, emptySet()))
        // Came together: no telling
        assertNull(BtIgnoreRules.newest(listOf("Buds", "Truck"), mapOf("Buds" to 5L, "Truck" to 5L), emptySet()))
        // Three, the newest ignored: the next newest
        assertEquals("Truck", BtIgnoreRules.newest(listOf("Buds", "Truck", "Speaker"), at + ("Speaker" to 300L), setOf("Speaker")))
    }

    @Test fun arrivalsKeepTheirTime() {
        val was = mapOf("Buds" to 100L, "Gone" to 50L)
        assertEquals(mapOf("Buds" to 100L, "Truck" to 900L), BtIgnoreRules.arrivals(was, listOf("Buds", "Truck"), 900L))
    }

    @Test fun stirOnlyAroundIgnoredDevices() {
        val buds = setOf("Buds")
        val both = setOf("Buds", "Chevy")
        assertEquals(true, BtIgnoreRules.stirs(buds, both, emptySet(), car))        // the car arrives
        assertEquals(true, BtIgnoreRules.stirs(both, buds, car, emptySet()))        // the car leaves
        assertEquals(true, BtIgnoreRules.stirs(both, car, car, car))                // the buds drop out while the car's there
        assertEquals(false, BtIgnoreRules.stirs(emptySet(), buds, emptySet(), emptySet()))   // nothing ignored involved
        assertEquals(false, BtIgnoreRules.stirs(both, both, car, car))              // nothing changed
    }

    @Test fun nearIsEitherSide() {
        assertEquals(true, BtIgnoreRules.near(10_000L, 15_000L))
        assertEquals(true, BtIgnoreRules.near(15_000L, 10_000L))
        assertEquals(false, BtIgnoreRules.near(10_000L, 10_000L + BtIgnoreRules.STIR_MS))
        assertEquals(false, BtIgnoreRules.near(0L, 100L))   // never happened
    }

    @Test fun mediaOffWhileThereIsSomewhereElse() {
        val ign = listOf("Chevy")
        assertEquals(ign, BtIgnoreRules.mediaOff(true, ign, elsewhere = true, elsewhereGoneAt = 0L, now = 50_000L))
        // the earbuds out for a moment: still off
        assertEquals(ign, BtIgnoreRules.mediaOff(true, ign, elsewhere = false, elsewhereGoneAt = 45_000L, now = 50_000L))
        // gone for good: the car may have the music again
        assertEquals(emptyList<String>(), BtIgnoreRules.mediaOff(true, ign, false, 30_000L, 50_000L))
        assertEquals(emptyList<String>(), BtIgnoreRules.mediaOff(true, ign, false, 0L, 50_000L))
        assertEquals(emptyList<String>(), BtIgnoreRules.mediaOff(false, ign, true, 0L, 50_000L))
    }

    @Test fun offKeysRoundTrip() {
        assertEquals("Chevy" to BtIgnoreRules.A2DP, BtIgnoreRules.parseOff(BtIgnoreRules.offKey("Chevy", BtIgnoreRules.A2DP)))
        assertEquals("Chevy" to BtIgnoreRules.HEADSET, BtIgnoreRules.parseOff(BtIgnoreRules.offKey("Chevy", BtIgnoreRules.HEADSET)))
        assertEquals("Chevy" to BtIgnoreRules.A2DP, BtIgnoreRules.parseOff("Chevy"))   // kept before calls were switched too
    }

    @Test fun keptOffWholeUnlessAndroidAutoOrConnected() {
        val calls = BtIgnoreRules.CALL_PROFILES.map { BtIgnoreRules.offKey("Chevy", it) }
        // earbuds on, the truck not connected: kept off entirely
        assertEquals(listOf("Chevy") + calls, BtIgnoreRules.keptOff(listOf("Chevy"), emptySet(), autoAllowed = false))
        // Connect Android Auto tapped: only its Media audio stays off
        assertEquals(listOf("Chevy"), BtIgnoreRules.keptOff(listOf("Chevy"), emptySet(), autoAllowed = true))
        // already connected for calls (Android Auto on): left connected
        assertEquals(listOf("Chevy"), BtIgnoreRules.keptOff(listOf("Chevy"), setOf("Chevy"), autoAllowed = false))
        // ... unless Disconnect Android Auto was tapped
        assertEquals(listOf("Chevy") + calls, BtIgnoreRules.keptOff(listOf("Chevy"), setOf("Chevy"), autoAllowed = false, forced = true))
        assertEquals(emptyList<String>(), BtIgnoreRules.keptOff(emptyList(), emptySet(), false))
    }
}
