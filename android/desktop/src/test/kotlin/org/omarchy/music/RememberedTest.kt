package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app remembers: a choice is written to its file, and nothing is written while a backup goes back in.
 *  What was chosen before is put back. */
class RememberedTest {
    @Test fun aChoiceIsWritten() {
        Harness.ctx
        val had = Music.skipOrder
        try {
            Harness.ui { Music.skipOrder = "heard"; Remembered.saveNow() }
            assertTrue(Remembered.file.readText().contains("\"skip_order\":\"heard\""))
            Harness.ui { Music.skipOrder = "least"; Remembered.saveNow() }
            assertTrue(Remembered.file.readText().contains("\"skip_order\":\"least\""))
        } finally {
            Harness.ui { Music.skipOrder = had; Remembered.saveNow() }
        }
    }

    @Test fun nothingIsWrittenWhileABackupGoesBackIn() {
        Harness.ctx
        val had = Music.skipOrder
        try {
            Harness.ui { Music.skipOrder = "heard"; Remembered.saveNow() }
            Harness.ui { Music.restoring = true; Music.skipOrder = "least"; Remembered.saveNow(); Remembered.save() }
            assertFalse(Remembered.file.readText().contains("\"skip_order\":\"least\""))
        } finally {
            Harness.ui { Music.restoring = false; Music.skipOrder = had; Remembered.saveNow() }
        }
    }
}
