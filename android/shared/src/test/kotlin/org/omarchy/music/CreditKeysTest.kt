package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** A song is looked for under each artist it is credited to. */
class CreditKeysTest {
    @Test fun eachArtistOfACreditTheLeadFirst() {
        assertEquals(listOf("ott" to "hardlife", "ott" to "benee"), creditKeys("OTT", "hard life & BENEE"))
        assertEquals(listOf("painkill" to "2xt", "painkill" to "kflay"), creditKeys("PAINKILL", "2XT & K.Flay"))
        assertEquals("the lead is the song's usual key", statsKey("PAINKILL", "2XT & K.Flay"), creditKeys("PAINKILL", "2XT & K.Flay").first())
        assertEquals(listOf("song" to "one"), creditKeys("Song", "One"))
        assertEquals(3, creditKeys("Song", "One, Two feat. Three").size)
    }
}
