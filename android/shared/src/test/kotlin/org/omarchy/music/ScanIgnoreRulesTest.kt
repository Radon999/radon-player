package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Never show picks made on one device are recognised on the other after a sync, though the music folder's address
 *  differs between the phone and the PC. */
class ScanIgnoreRulesTest {
    private val phone = "/storage/emulated/0/Music/Artists/HOAX/b/02 into the blackhole (Live).flac"
    private val pc = "C:\\Users\\Radon\\Music\\Artists\\HOAX\\b\\02 into the blackhole (Live).flac"

    @Test fun theSameFileOnBothDevicesHasTheSameEnd() {
        assertEquals(ScanIgnoreRules.tail(phone), ScanIgnoreRules.tail(pc))
        assertEquals("hoax/b/02 into the blackhole (live).flac", ScanIgnoreRules.tail(pc))
    }

    @Test fun aFileHiddenOnThePcIsHiddenOnThePhone() {
        assertTrue(ScanIgnoreRules.hidden("live", phone, setOf(pc)))
        assertTrue(ScanIgnoreRules.hidden("empty", "/storage/emulated/0/Music/Artists/HOAX/b", setOf("C:/Users/Radon/Music/Artists/HOAX/b")))
    }

    @Test fun whatFixAlbumsAddsAfterTheAddressMustMatchToo() {
        assertTrue(ScanIgnoreRules.hidden("fixup", "$phone|album=b", setOf("$pc|album=b")))
        assertFalse(ScanIgnoreRules.hidden("fixup", "$phone|album=c", setOf("$pc|album=b")))
    }

    @Test fun anotherSongOrAnotherAlbumIsNotHidden() {
        assertFalse(ScanIgnoreRules.hidden("live", phone.replace("02 into", "03 into"), setOf(pc)))
        assertFalse(ScanIgnoreRules.hidden("live", phone.replace("/b/", "/c/"), setOf(pc)))
    }

    @Test fun scansByNameMatchOnlyTheSameName() {
        // Similar artists, Duplicates in Most played, Joint artists: the same key on both devices, nothing looser
        assertTrue(ScanIgnoreRules.hidden(ScanIgnoreRules.JOINT, "logic|marshmello", setOf("logic|marshmello")))
        assertFalse(ScanIgnoreRules.hidden(ScanIgnoreRules.JOINT, "joji|don toliver", setOf("logic|marshmello")))
        assertFalse(ScanIgnoreRules.hidden(ScanIgnoreRules.SIMILAR, "a/b/c", setOf("x/b/c")))
    }
}
