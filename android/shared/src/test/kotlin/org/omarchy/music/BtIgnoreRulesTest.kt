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
}

class AutoIgnoredTest {
    private val isCar = { n: String -> n == "CHEVROLET1512" }

    @Test fun theIgnoredTruckStaysIgnoredWhileAndroidAutoLingersAfterItsBluetoothWent() {
        // the truck's Bluetooth gone, wireless Android Auto still on: still the ignored car (the music stays on the earbuds)
        assertEquals(true, BtIgnoreRules.autoIgnored(true, true, setOf("CHEVROLET1512"), listOf("WF-1000XM6"), isCar))
        assertEquals(true, BtIgnoreRules.autoIgnored(true, true, setOf("CHEVROLET1512"), listOf("CHEVROLET1512", "WF-1000XM6"), isCar))
        assertEquals(true, BtIgnoreRules.autoIgnored(true, true, setOf(BtIgnoreRules.AUTO), emptyList(), isCar))
    }

    @Test fun notIgnoredWhenItIsnt() {
        assertEquals(false, BtIgnoreRules.autoIgnored(false, true, setOf("CHEVROLET1512"), emptyList(), isCar))   // Android Auto off
        assertEquals(false, BtIgnoreRules.autoIgnored(true, false, setOf("CHEVROLET1512"), emptyList(), isCar))   // ignoring off
        assertEquals(false, BtIgnoreRules.autoIgnored(true, true, setOf("Kitchen speaker"), listOf("WF-1000XM6"), isCar))   // only a speaker ignored
    }
}
