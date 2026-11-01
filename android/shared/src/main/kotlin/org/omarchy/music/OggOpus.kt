package org.omarchy.music

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** YouTube's best audio (Opus in a WebM file) moved as it is into an Ogg Opus file (.opus) with its tags and cover:
 *  no re-encoding, so not a bit of quality is lost, and players and tag editors read .opus where they skip .webm. */
object OggOpus {
    /** Tags to write: title, artist, album (empty ones left out), and the cover (JPEG or PNG bytes, or null). */
    class Tags(val title: String = "", val artist: String = "", val album: String = "", val cover: ByteArray? = null,
               val extra: List<Pair<String, String>> = emptyList())

    // Matroska (WebM) element ids
    private const val SEGMENT = 0x18538067L; private const val CLUSTER = 0x1F43B675L; private const val BLOCK_GROUP = 0xA0L
    private const val TRACKS = 0x1654AE6BL; private const val TRACK_ENTRY = 0xAEL
    private const val TRACK_NUMBER = 0xD7L; private const val CODEC_ID = 0x86L; private const val CODEC_PRIVATE = 0x63A2L
    private const val CODEC_DELAY = 0x56AAL; private const val AUDIO = 0xE1L; private const val CHANNELS = 0x9FL
    private const val SIMPLE_BLOCK = 0xA3L; private const val BLOCK = 0xA1L; private const val DISCARD_PADDING = 0x75A2L
    private val MASTERS = setOf(SEGMENT, CLUSTER, BLOCK_GROUP, TRACKS, TRACK_ENTRY, AUDIO)

    /** The Opus track of a WebM file: its OpusHead and its packets in order. Throws if it isn't one (or uses lacing,
     *  which YouTube's files don't). */
    class Opus(val head: ByteArray, val packets: List<ByteArray>, val endTrim: Int = 0)

    fun readWebm(b: ByteArray): Opus {
        require(b.size > 4 && readId(b, 0).first == 0x1A45DFA3L) { "not a WebM file" }
        var p = 0
        var track = -1L; var codec = ""; var priv: ByteArray? = null; var delayNs = 0L; var channels = 2
        var opusTrack = -1L; var head: ByteArray? = null
        val packets = ArrayList<ByteArray>()
        var groupStart = 0; var pendingTrim = 0; var endTrim = 0   // DiscardPadding: samples to cut off the end
        while (p < b.size) {
            val (id, idLen) = readId(b, p)
            val (size, sizeLen) = readSize(b, p + idLen)
            val body = p + idLen + sizeLen
            if (body > b.size) break
            if (id in MASTERS) {
                if (id == TRACK_ENTRY) { track = -1; codec = ""; priv = null; delayNs = 0; channels = 2 }
                if (id == BLOCK_GROUP) { groupStart = packets.size; pendingTrim = 0 }
                p = body   // its children follow (an unknown size too)
                continue
            }
            if (size < 0 || body + size > b.size) break   // cut short: what came before is kept
            val end = (body + size).toInt()
            when (id) {
                TRACK_NUMBER -> track = uint(b, body, end)
                CODEC_ID -> codec = String(b, body, end - body, Charsets.US_ASCII).trimEnd('\u0000')
                CODEC_PRIVATE -> priv = b.copyOfRange(body, end)
                CODEC_DELAY -> delayNs = uint(b, body, end)
                CHANNELS -> channels = uint(b, body, end).toInt()
                DISCARD_PADDING -> {   // in nanoseconds, signed; it's the last packet's (before or after its Block)
                    var ns = uint(b, body, end); val bits = 8 * (end - body)
                    if (bits in 1..63 && ns shr (bits - 1) == 1L) ns -= 1L shl bits
                    val n = (ns.coerceAtLeast(0) * 48_000 / 1_000_000_000L).toInt()
                    if (packets.size > groupStart) endTrim = n else pendingTrim = n
                }
                SIMPLE_BLOCK, BLOCK -> {
                    val (num, numLen) = readSize(b, body)
                    val flags = b[body + numLen + 2].toInt() and 0xff
                    if (num == opusTrack) {
                        require((flags shr 1) and 3 == 0) { "laced WebM blocks" }
                        packets += b.copyOfRange(body + numLen + 3, end)
                        endTrim = pendingTrim; pendingTrim = 0
                    }
                }
            }
            // A track entry's last child read: is it the Opus one?
            if (opusTrack < 0 && codec == "A_OPUS" && track > 0) {
                opusTrack = track
                head = priv?.takeIf { it.size >= 19 && String(it, 0, 8, Charsets.US_ASCII) == "OpusHead" }
                    ?: opusHead(channels, (delayNs * 48_000 / 1_000_000_000L).toInt())
            }
            if (id == CODEC_PRIVATE && opusTrack == track && track > 0) {
                priv?.takeIf { it.size >= 19 && String(it, 0, 8, Charsets.US_ASCII) == "OpusHead" }?.let { head = it }
            }
            p = end
        }
        require(opusTrack > 0 && packets.isNotEmpty()) { "no Opus audio in it" }
        return Opus(head ?: opusHead(channels, 312), packets, endTrim)
    }

    /** A WebM's Opus audio written as an Ogg Opus file, with tags. Throws if it isn't Opus in WebM. */
    fun webmToOpus(webm: ByteArray, out: OutputStream, tags: Tags) = write(readWebm(webm), out, tags)

    /** The OpusHead packet for channels (1 or 2) and preSkip samples, 48 kHz. */
    private fun opusHead(channels: Int, preSkip: Int): ByteArray = ByteArrayOutputStream().apply {
        write("OpusHead".toByteArray()); write(1); write(channels.coerceIn(1, 2)); le16(this, preSkip); le32(this, 48_000); le16(this, 0); write(0)
    }.toByteArray()

    /** The OpusTags packet: the vendor, then each tag as NAME=value (the cover as METADATA_BLOCK_PICTURE). */
    fun opusTags(t: Tags): ByteArray {
        val list = ArrayList<String>()
        if (t.title.isNotBlank()) list += "TITLE=${t.title}"
        if (t.artist.isNotBlank()) list += "ARTIST=${t.artist}"
        if (t.album.isNotBlank()) list += "ALBUM=${t.album}"
        t.extra.filter { it.second.isNotBlank() }.forEach { list += "${it.first.uppercase()}=${it.second}" }
        t.cover?.takeIf { it.isNotEmpty() }?.let { list += "METADATA_BLOCK_PICTURE=" + java.util.Base64.getEncoder().encodeToString(pictureBlock(it)) }
        return ByteArrayOutputStream().apply {
            write("OpusTags".toByteArray())
            val vendor = "Radon Player".toByteArray()
            le32(this, vendor.size); write(vendor)
            le32(this, list.size)
            for (c in list) { val x = c.toByteArray(Charsets.UTF_8); le32(this, x.size); write(x) }
        }.toByteArray()
    }

    /** A FLAC PICTURE block's body (front cover), as Ogg files carry their cover. */
    private fun pictureBlock(img: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        val png = img.size > 4 && img[0] == 0x89.toByte() && img[1] == 'P'.code.toByte()
        val mime = (if (png) "image/png" else "image/jpeg").toByteArray()
        be32(this, 3); be32(this, mime.size); write(mime); be32(this, 0)
        be32(this, 0); be32(this, 0); be32(this, 0); be32(this, 0)   // width, height, depth, colours: not known, allowed
        be32(this, img.size); write(img)
    }.toByteArray()

    /** Samples (at 48 kHz) in an Opus packet, from its first byte (and the second, for a code 3 packet). */
    fun samples(pkt: ByteArray): Int {
        if (pkt.isEmpty()) return 0
        val toc = pkt[0].toInt() and 0xff
        val config = toc shr 3
        val frame = when {
            config < 12 -> intArrayOf(480, 960, 1920, 2880)[config and 3]     // SILK: 10, 20, 40, 60 ms
            config < 16 -> intArrayOf(480, 960)[config and 1]                 // hybrid: 10, 20 ms
            else -> intArrayOf(120, 240, 480, 960)[config and 3]              // CELT: 2.5, 5, 10, 20 ms
        }
        val frames = when (toc and 3) { 0 -> 1; 1, 2 -> 2; else -> if (pkt.size > 1) pkt[1].toInt() and 0x3f else 0 }
        return frame * frames
    }

    /** The Ogg file: OpusHead on its own page, OpusTags from the next, then the audio a few KB a page. */
    fun write(o: Opus, out: OutputStream, tags: Tags) {
        val pages = Pager(out, serial = (System.nanoTime() and 0x7fffffff).toInt())
        pages.packet(o.head, 0); pages.flush(bos = true)
        pages.packet(opusTags(tags), 0); pages.flush()
        val preSkip = (o.head[10].toInt() and 0xff) or ((o.head[11].toInt() and 0xff) shl 8)
        var granule = preSkip.toLong()
        o.packets.forEachIndexed { i, pkt ->
            granule += samples(pkt)
            // The last page's granule tells players where the song ends (the encoder's padding cut off)
            pages.packet(pkt, if (i == o.packets.lastIndex) maxOf(preSkip.toLong(), granule - o.endTrim) else granule)
            if (i == o.packets.lastIndex) pages.flush(eos = true) else if (pages.full()) pages.flush()
        }
    }

    /** Ogg pages, packets laid in 255-byte segments; a page holds at most 255 segments. */
    private class Pager(val out: OutputStream, val serial: Int) {
        private var seq = 0
        private val segs = ArrayList<Int>(); private val data = ByteArrayOutputStream()
        private var granule = -1L          // of the last packet that ended on this page (-1: none did)
        private var continued = false      // this page starts with the rest of a packet

        fun full() = data.size() >= 4096 || segs.size > 200

        fun packet(p: ByteArray, end: Long) {
            var off = 0
            while (true) {
                if (segs.size == 255) { flush(); continued = true }
                val n = minOf(255, p.size - off)
                segs += n; data.write(p, off, n); off += n
                if (n < 255) break   // the packet ends here (a 0 segment when it was a multiple of 255)
            }
            granule = end
        }

        fun flush(bos: Boolean = false, eos: Boolean = false) {
            if (segs.isEmpty()) return
            val h = ByteArrayOutputStream()
            h.write("OggS".toByteArray()); h.write(0)
            h.write((if (continued) 1 else 0) or (if (bos) 2 else 0) or (if (eos) 4 else 0))
            for (i in 0 until 8) h.write(((granule shr (8 * i)) and 0xff).toInt())
            le32(h, serial); le32(h, seq++); le32(h, 0)
            h.write(segs.size); segs.forEach { h.write(it) }
            val page = h.toByteArray() + data.toByteArray()
            val crc = crc(page)
            for (i in 0 until 4) page[22 + i] = (crc shr (8 * i)).toByte()
            out.write(page)
            segs.clear(); data.reset(); granule = -1L; continued = false
        }
    }

    private val TABLE = IntArray(256) { i ->
        var r = i shl 24
        repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
        r
    }
    /** Ogg's CRC-32 (not zip's: unreflected, starting at 0). */
    fun crc(b: ByteArray): Int {
        var c = 0
        for (x in b) c = (c shl 8) xor TABLE[((c ushr 24) xor (x.toInt() and 0xff)) and 0xff]
        return c
    }

    private fun readId(b: ByteArray, p: Int): Pair<Long, Int> {
        val first = b[p].toInt() and 0xff
        val len = Integer.numberOfLeadingZeros(first) - 23
        require(len in 1..4) { "bad WebM element" }
        var v = 0L
        for (i in 0 until len) v = (v shl 8) or (b[p + i].toLong() and 0xff)
        return v to len
    }

    /** An EBML size (or a block's track number): (value, its length); -1 for "unknown". */
    private fun readSize(b: ByteArray, p: Int): Pair<Long, Int> {
        val first = b[p].toInt() and 0xff
        val len = Integer.numberOfLeadingZeros(first) - 23
        require(len in 1..8) { "bad WebM size" }
        var v = (first and (0xff shr len)).toLong()
        var allOnes = v == (0xff shr len).toLong()
        for (i in 1 until len) { val x = b[p + i].toLong() and 0xff; v = (v shl 8) or x; if (x != 0xffL) allOnes = false }
        return (if (allOnes) -1L else v) to len
    }

    private fun uint(b: ByteArray, from: Int, to: Int): Long { var v = 0L; for (i in from until to) v = (v shl 8) or (b[i].toLong() and 0xff); return v }
    private fun le16(o: OutputStream, v: Int) { o.write(v and 0xff); o.write((v shr 8) and 0xff) }
    private fun le32(o: OutputStream, v: Int) { for (i in 0 until 4) o.write((v shr (8 * i)) and 0xff) }
    private fun be32(o: OutputStream, v: Int) { for (i in 3 downTo 0) o.write((v shr (8 * i)) and 0xff) }
}
