package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moods already in a song's file (written on the other device, then the files copied over) mean the song isn't
 *  waiting for Scan moods, and the scan leaves it alone; a reset makes them the app's own so they're scanned again. */
class MoodSettledTest {
    @Test fun movedOverFilesWithMoodsAreSettled() {
        // The phone has never written this song's moods: the file came from the PC with them
        assertTrue(MoodRules.settledInFile(picked = "", inFile = "Happy;Energetic", wrote = ""))
        // The phone wrote something else once; the file now holds other moods (written elsewhere)
        assertTrue(MoodRules.settledInFile(picked = "", inFile = "Sad", wrote = "Chill"))
    }

    @Test fun aFileWithNoMoodsStillWaits() {
        assertFalse(MoodRules.settledInFile(picked = "", inFile = "", wrote = ""))
        assertFalse(MoodRules.settledInFile(picked = "", inFile = "  ", wrote = ""))
    }

    @Test fun moodsThisDeviceWroteDoNotSettleIt() {
        // The scan here wrote them (or a reset made them the app's own): the scan decides, as before
        assertFalse(MoodRules.settledInFile(picked = "", inFile = "Happy;Party", wrote = "Happy;Party"))
    }

    @Test fun moodsYouPickedGoTheirOwnWay() {
        assertFalse(MoodRules.settledInFile(picked = "Chill", inFile = "Chill", wrote = ""))
    }
}
