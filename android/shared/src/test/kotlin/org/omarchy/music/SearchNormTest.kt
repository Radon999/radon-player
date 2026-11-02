package org.omarchy.music

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchNormTest {
    @Test fun symbolsDontMatter() {
        assertTrue(searchHit(searchWords("hey soul sister"), "Hey, Soul Sister"))
        assertTrue(searchHit(searchWords("dont stop"), "Don't Stop Me Now"))
        assertTrue(searchHit(searchWords("don't stop"), "Dont Stop Me Now"))
        assertTrue(searchHit(searchWords("tyler the creator"), "Tyler, The Creator"))
        assertTrue(searchHit(searchWords("acdc"), "AC/DC"))
        assertTrue(searchHit(searchWords("hey, soul"), "Hey Soul Sister"))
        assertFalse(searchHit(searchWords("hello"), "Hey, Soul Sister"))
    }
}
