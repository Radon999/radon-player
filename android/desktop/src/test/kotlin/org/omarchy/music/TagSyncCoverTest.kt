package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A cover you pick goes to your other device with your tag changes: noted with its picture, sent, and let go once
 *  the other device has taken it. */
class TagSyncCoverTest {
    @Test fun aPickedCoverIsNotedAndSentWithItsPicture() {
        Harness.ctx
        val one = ByteArray(2000) { (it % 7).toByte() }; val two = ByteArray(3000) { (it % 11).toByte() }
        TagSync.noteCover(listOf("Cover Song A" to "Cover Band", "Cover Song B" to "Cover Band"), one)
        var sent = JSON.decodeFromString(TagChangesSent.serializer(), TagSync.outgoing())
        val mine = sent.pending.filter { it.artist == "Cover Band" }
        assertEquals(2, mine.size); assertTrue(mine.all { it.cover.isNotEmpty() && it.changes.isEmpty() })
        assertEquals("one picture for both songs", 1, mine.map { it.cover }.distinct().size)
        assertEquals("the picture itself goes with them", one.toList(), java.util.Base64.getDecoder().decode(sent.art[mine[0].cover]!!).toList())
        // picked again for one of them: the newer picture is the one that goes for it
        TagSync.noteCover(listOf("Cover Song A" to "Cover Band"), two)
        sent = JSON.decodeFromString(TagChangesSent.serializer(), TagSync.outgoing())
        val a = sent.pending.filter { it.title == "Cover Song A" }
        assertEquals(1, a.size); assertEquals(two.toList(), java.util.Base64.getDecoder().decode(sent.art[a[0].cover]!!).toList())
        assertEquals(2, sent.art.size)
        // the other device says it has taken them: they are let go here, and their pictures with them
        val ids = sent.pending.filter { it.artist == "Cover Band" }.map { it.id }
        TagSync.take(JSON.encodeToString(TagChangesSent.serializer(), TagChangesSent(applied = ids)))
        sent = JSON.decodeFromString(TagChangesSent.serializer(), TagSync.outgoing())
        assertTrue(sent.pending.none { it.artist == "Cover Band" }); assertTrue(sent.art.isEmpty())
    }
}
