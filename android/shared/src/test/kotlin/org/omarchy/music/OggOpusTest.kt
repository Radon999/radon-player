package org.omarchy.music

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class OggOpusTest {
    /** An EBML element: its id's bytes, an 8-byte size (or "unknown"), its body. */
    private fun el(id: Long, body: ByteArray, unknown: Boolean = false): ByteArray {
        val idBytes = ByteArrayOutputStream().apply { var started = false
            for (i in 3 downTo 0) { val x = ((id shr (8 * i)) and 0xff).toInt(); if (x != 0 || started) { write(x); started = true } } }.toByteArray()
        val size = if (unknown) byteArrayOf(0x01, -1, -1, -1, -1, -1, -1, -1)
            else ByteArray(8).also { it[0] = 0x01; for (i in 1 until 8) it[i] = ((body.size.toLong() shr (8 * (7 - i))) and 0xff).toByte() }
        return idBytes + size + body
    }
    private fun head(preSkip: Int) = "OpusHead".toByteArray() + byteArrayOf(1, 2, (preSkip and 0xff).toByte(), (preSkip shr 8).toByte(),
        0x80.toByte(), 0xBB.toByte(), 0, 0, 0, 0, 0)

    /** A WebM like YouTube's: one Opus track, its packets in SimpleBlocks (20 ms CELT packets), the last one trimmed. */
    private fun webm(packets: List<ByteArray>, trimNs: Long): ByteArray {
        val track = el(0xAE, el(0xD7, byteArrayOf(1)) + el(0x86, "A_OPUS".toByteArray()) + el(0x63A2, head(312)))
        val blocks = packets.dropLast(1).fold(ByteArray(0)) { a, p -> a + el(0xA3, byteArrayOf(0x81.toByte(), 0, 0, 0x80.toByte()) + p) }
        val lastGroup = el(0xA0, el(0xA1, byteArrayOf(0x81.toByte(), 0, 0, 0) + packets.last()) +
            el(0x75A2, byteArrayOf((trimNs shr 16).toByte(), (trimNs shr 8).toByte(), trimNs.toByte())))
        val segment = el(0x1654AE6B, track) + el(0x1F43B675, el(0xE7, byteArrayOf(0)) + blocks + lastGroup)
        return el(0x1A45DFA3, el(0x4282, "webm".toByteArray())) + el(0x18538067, segment, unknown = true)
    }

    private class Page(val flags: Int, val granule: Long, val segs: IntArray, val data: ByteArray)

    private fun pages(b: ByteArray): List<Page> {
        val out = ArrayList<Page>()
        var p = 0
        while (p < b.size) {
            assertEquals("OggS", String(b, p, 4))
            val n = b[p + 26].toInt() and 0xff
            val segs = IntArray(n) { b[p + 27 + it].toInt() and 0xff }
            val len = 27 + n + segs.sum()
            val page = b.copyOfRange(p, p + len)
            val crc = (0 until 4).fold(0) { a, i -> a or ((page[22 + i].toInt() and 0xff) shl (8 * i)) }
            for (i in 22 until 26) page[i] = 0
            assertEquals("page CRC", crc, OggOpus.crc(page))
            var g = 0L; for (i in 0 until 8) g = g or ((b[p + 6 + i].toLong() and 0xff) shl (8 * i))
            out += Page(b[p + 5].toInt(), g, segs, b.copyOfRange(p + 27 + n, p + len))
            p += len
        }
        return out
    }

    /** The packets in the pages, joined again across segments and pages. */
    private fun packets(ps: List<Page>): List<ByteArray> {
        val out = ArrayList<ByteArray>(); val cur = ByteArrayOutputStream()
        for (pg in ps) { var off = 0
            for (s in pg.segs) { cur.write(pg.data, off, s); off += s; if (s < 255) { out += cur.toByteArray(); cur.reset() } } }
        return out
    }

    @Test fun movesWebmOpusIntoOggAsItIs() {
        val pkts = List(600) { i -> byteArrayOf(0xFC.toByte()) + ByteArray(150 + i % 300) { (it * 7 + i).toByte() } }   // CELT 20 ms
        val cover = ByteArray(200_000) { (it % 251).toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }
        val out = ByteArrayOutputStream()
        OggOpus.webmToOpus(webm(pkts, 6_500_000), out, OggOpus.Tags("Title", "Artist", "Album", cover))
        val ps = pages(out.toByteArray())
        assertEquals(2, ps.first().flags)            // the first page begins the stream
        assertEquals(4, ps.last().flags and 4)       // the last ends it
        val all = packets(ps)
        assertArrayEquals(head(312), all[0])
        assertTrue(String(all[1], 0, 8) == "OpusTags" && all[1].size > 200_000)   // the cover went in, over several pages
        assertEquals(pkts.size, all.size - 2)
        pkts.forEachIndexed { i, p -> assertArrayEquals(p, all[i + 2]) }
        assertEquals(312L + 600 * 960 - 312, ps.last().granule)   // 6.5 ms (312 samples) trimmed off the end
    }

    @Test fun countsSamplesFromTheFirstByte() {
        assertEquals(960, OggOpus.samples(byteArrayOf(0xFC.toByte())))         // CELT 20 ms, one frame
        assertEquals(1920, OggOpus.samples(byteArrayOf(0xFD.toByte())))        // two frames
        assertEquals(2880, OggOpus.samples(byteArrayOf(0x18.toByte())))     // SILK 60 ms
        assertEquals(480 * 3, OggOpus.samples(byteArrayOf(0x73.toByte(), 3)))  // hybrid 10 ms, code 3, three frames
    }
}
