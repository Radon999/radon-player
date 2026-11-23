package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Add to playlist… leaves out the playlists the app makes and fills itself. */
class AddToRulesTest {
    @Test fun discoveryAndWeeklyMixAreLeftOut() {
        assertFalse(AddToRules.offered("Keane Discovery playlist", true, listOf("Keane")))
        assertFalse(AddToRules.offered("Keane discovered", true, emptyList()))
        assertFalse(AddToRules.offered("Weekly mix", false, listOf("Lauv", "Keane")))
    }

    @Test fun anArtistPlaylistIsLeftOut() {
        assertFalse(AddToRules.offered("Juice WRLD", false, List(20) { "Juice WRLD" } + "Juice WRLD, Halsey"))
        assertFalse(AddToRules.offered("K.Flay", false, listOf("KFlay", "K.Flay", "K.Flay")))
    }

    @Test fun yourOwnPlaylistsAreOffered() {
        assertTrue(AddToRules.offered("only bangers", false, listOf("Lauv", "Keane", "Juice WRLD")))
        assertTrue(AddToRules.offered("best of hoax", false, listOf("Hoax", "Lauv", "Keane", "Sody")))   // a name, but others' songs too
        assertTrue(AddToRules.offered("New playlist", false, emptyList()))
        assertTrue(AddToRules.offered("Juice WRLD", false, emptyList()))   // empty: nothing says it's an artist playlist
    }
}
