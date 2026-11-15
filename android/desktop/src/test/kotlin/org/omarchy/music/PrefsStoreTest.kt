package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The PC's file of switches, saved from several threads at once (the DJ, the screens, the player all keep things there). */
class PrefsStoreTest {
    @Test fun savingFromManyThreadsAtOnceLosesNothingAndNeverFails() {
        val dir = java.nio.file.Files.createTempDirectory("radon-prefs").toFile()
        val ctx = Ctx(dir, java.io.File(dir, "cache").apply { mkdirs() })
        val store = PrefsStore(ctx, "switches")
        val errors = java.util.Collections.synchronizedList(ArrayList<Throwable>())
        val threads = (0 until 8).map { t ->
            Thread { try { for (i in 0 until 150) store.put("key$t", "value $i") } catch (e: Throwable) { errors += e } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(30_000) }
        assertTrue("no save failed: $errors", errors.isEmpty())
        val back = PrefsStore(ctx, "switches").load()
        for (t in 0 until 8) assertEquals("the last value of each is in the file", "value 149", back["key$t"])
    }
}
