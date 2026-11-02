package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {
    @Test fun stateKeepsThisDevicesOwn() {
        val phone = """{"queue":[],"source":"tidal","folders":["Music/"],"bit_perfect":true,"look":"apple"}"""
        val pc = """{"folders":["C:/Users/me/Music"],"download_dir":"D:/Songs"}"""
        val o = JSON.parseToJsonElement(BackupMerge.mergeState(phone, pc)).toString()
        assertTrue(o.contains("C:/Users/me/Music")); assertTrue(o.contains("D:/Songs"))
        assertFalse(o.contains("bit_perfect")); assertTrue(o.contains("\"look\":\"apple\""))
        assertTrue(BackupMerge.mergeState(phone, null).let { !it.contains("folders") && it.contains("tidal") })
    }

    @Test fun switches() {
        val saved = BackupMerge.switchesToSave(mapOf("crossfade" to true, "balance" to 0.5f, "ytm_cookie" to "secret", "pc_volume" to 0.8f))
        assertFalse(saved.contains("secret"))
        val cross = BackupMerge.switchesToRestore(saved, crossDevice = true)
        assertEquals(true, cross["crossfade"]); assertEquals(0.5f, cross["balance"]); assertFalse("pc_volume" in cross)
        assertTrue("pc_volume" in BackupMerge.switchesToRestore(saved, crossDevice = false))
    }

    @Test fun files() {
        assertTrue(BackupMerge.fileHere("content://media/external/audio/media/12", android = true))
        assertFalse(BackupMerge.fileHere("C:\\Users\\me\\Music\\a.flac", android = true))
        assertTrue(BackupMerge.fileHere("C:\\Users\\me\\Music\\a.flac", android = false))
        assertFalse(BackupMerge.fileHere("content://media/external/audio/media/12", android = false))
    }
}
