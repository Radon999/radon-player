package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A scan's choice that names a file or folder made on the PC is the phone's own file or folder after a sync, and the
 *  other way round. */
class SyncPathsTest {
    private val pcRoots = listOf("C:/Users/Radon/Music")
    private val phoneRoots = listOf("/storage/emulated/0/Music")
    private val pcFile = "C:\\Users\\Radon\\Music\\Artists\\HOAX\\b\\02 into the blackhole (Live).flac"
    private val phoneFile = "/storage/emulated/0/Music/Artists/HOAX/b/02 into the blackhole (Live).flac"

    @Test fun aSongKeptAsLiveOnThePcIsThePhonesOwnFile() {
        val sent = SyncPaths.out(pcFile, pcRoots)
        assertEquals("<music>/Artists/HOAX/b/02 into the blackhole (Live).flac", sent)
        assertEquals(phoneFile, SyncPaths.into(sent, phoneRoots) { false })
    }

    @Test fun andTheOtherWay() {
        val sent = SyncPaths.out(phoneFile, phoneRoots)
        assertEquals("C:/Users/Radon/Music/Artists/HOAX/b/02 into the blackhole (Live).flac", SyncPaths.into(sent, pcRoots) { false })
    }

    @Test fun aFolderLeftAloneAndWhatFixAlbumsAddsAfterTheAddress() {
        assertEquals("/storage/emulated/0/Music/Artists/HOAX", SyncPaths.into(SyncPaths.out("C:/Users/Radon/Music/Artists/HOAX", pcRoots), phoneRoots) { false })
        val fix = "$pcFile|album=b;artist=HOAX"
        assertEquals("$phoneFile|album=b;artist=HOAX", SyncPaths.into(SyncPaths.out(fix, pcRoots), phoneRoots) { false })
    }

    @Test fun theMusicFolderThatHasItIsPicked() {
        val roots = listOf("/storage/emulated/0/Music", "/storage/1234-5678/Music")
        val sent = SyncPaths.out(pcFile, pcRoots)
        assertEquals("/storage/1234-5678/Music/Artists/HOAX/b/02 into the blackhole (Live).flac",
            SyncPaths.into(sent, roots) { it.startsWith("/storage/1234-5678") })
    }

    @Test fun addressesOutsideTheMusicFoldersGoAsTheyAre() {
        assertEquals("D:/Other/song.flac", SyncPaths.out("D:/Other/song.flac", pcRoots))
        assertEquals("content://media/external/audio/media/12", SyncPaths.out("content://media/external/audio/media/12", phoneRoots))
        assertEquals("D:/Other/song.flac", SyncPaths.into("D:/Other/song.flac", phoneRoots) { false })
        // A folder whose name only starts the same isn't inside it
        assertEquals("C:/Users/Radon/Music2/x.flac", SyncPaths.out("C:/Users/Radon/Music2/x.flac", pcRoots))
    }

    @Test fun wholeSettingsAndNotADuplicateKeys() {
        val text = "$pcFile\n\nC:/Users/Radon/Music/Artists/HOAX"
        val back = SyncPaths.intoLines(SyncPaths.outLines(text, pcRoots), phoneRoots) { false }
        assertEquals("$phoneFile\n\n/storage/emulated/0/Music/Artists/HOAX", back)
        val key = StatPickRules.dupeKey(listOf("C:/Users/Radon/Music/A/x.flac", "C:/Users/Radon/Music/B/x.mp3"))
        val phoneKey = SyncPaths.intoDupeKey(SyncPaths.outDupeKey(key, pcRoots), phoneRoots) { false }
        assertEquals(StatPickRules.dupeKey(listOf("/storage/emulated/0/Music/A/x.flac", "/storage/emulated/0/Music/B/x.mp3")), phoneKey)
    }

    @Test fun theSettingsThatHoldAddresses() {
        assertTrue("fixup_skip" in SyncPaths.PREFS && "live_kept" in SyncPaths.PREFS && "ignore_empty" in SyncPaths.PREFS)
        assertTrue("ignore_joint" !in SyncPaths.PREFS)   // by name: goes as it is
    }
}
