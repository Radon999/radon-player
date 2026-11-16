package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class StatPickRulesTest {
    @Test fun twoSpellingsAreOneName() {
        assertEquals(StatPickRules.bare("K.Flay"), StatPickRules.bare("KFlay"))
        assertEquals(StatPickRules.bare("Twenty One Pilots"), StatPickRules.bare("twenty-one pilots"))
    }

    @Test fun aPairKeptApartHasOneKeyHoweverItIsSpelled() {
        assertEquals(StatPickRules.apartKey("K.Flay", "Kflay Live"), StatPickRules.apartKey("KFlay", "kflay  live"))
    }

    @Test fun mergingChangesOnlyThatArtistsPartOfACredit() {
        assertEquals("Cavetown", StatPickRules.merged("Cavetown Offline", "Cavetown Offline", "Cavetown"))
        assertEquals("Cavetown & Sody", StatPickRules.merged("Cavetown Offline & Sody", "Cavetown Offline", "Cavetown"))
        assertEquals("Sody, Cavetown", StatPickRules.merged("Sody, Cavetown Offline", "Cavetown Offline", "Cavetown"))
        assertEquals("Sody x Cavetown", StatPickRules.merged("Sody x cavetown offline", "Cavetown Offline", "Cavetown"))
        assertEquals("another artist's credit is left", "Sody", StatPickRules.merged("Sody", "Cavetown Offline", "Cavetown"))
    }

    @Test fun whatMergingSays() {
        assertEquals("A is B now", StatPickRules.mergedText("A", "B", 0))
        assertEquals("A is B now: 1 song retagged", StatPickRules.mergedText("A", "B", 1))
        assertEquals("A is B now: 3 songs retagged", StatPickRules.mergedText("A", "B", 3))
    }

    @Test fun aGroupOfCopiesHasOneKeyInAnyOrder() {
        assertEquals(StatPickRules.dupeKey(listOf("b.flac", "a.flac")), StatPickRules.dupeKey(listOf("a.flac", "b.flac")))
    }
}
