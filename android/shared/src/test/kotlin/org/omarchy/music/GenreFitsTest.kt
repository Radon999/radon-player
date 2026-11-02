package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreFitsTest {
    @Test fun fits() {
        assertTrue(genreFits("Indie Rock", "rock"))
        assertTrue(genreFits("Alternative; Pop Rock", "pop rock"))
        assertTrue(genreFits("Hip-Hop", "hip hop"))
        assertTrue(genreFits("HipHop/Rap", "rap"))
        assertFalse(genreFits("Trap", "rap"))
        assertFalse(genreFits("", "rock"))
        assertEquals(listOf("Alternative", "Indie Rock"), genreParts("Alternative; Indie Rock"))
    }
}
