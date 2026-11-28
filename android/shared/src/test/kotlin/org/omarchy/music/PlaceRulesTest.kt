package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRulesTest {
    @Test fun aSongGoesBackToItsPlaceUnlessBarelyBegunOrAllButOver() {
        assertTrue("40 minutes into a mix", PlaceRules.resumes(2400.0, 3600.0))
        assertFalse("three seconds in: from its start", PlaceRules.resumes(3.0, 200.0))
        assertFalse("its last seconds: from its start", PlaceRules.resumes(195.0, 200.0))
        assertTrue("a length not known", PlaceRules.resumes(90.0, 0.0))
    }
}
