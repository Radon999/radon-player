package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a sync takes from the other device: every file the two send each other, and nothing else. */
class SyncTakesTest {
    @Test fun eachDevicesListOfItsSongsIsTaken() {
        Harness.ctx
        assertTrue("the other device's songs (Other device's songs was empty without it)", Sync.takes("other-device-songs.json"))
        assertTrue(Sync.takes("downloads.json")); assertTrue(Sync.takes("listen-log.jsonl"))
        assertFalse(Sync.takes("something-else.sh"))
    }
}
