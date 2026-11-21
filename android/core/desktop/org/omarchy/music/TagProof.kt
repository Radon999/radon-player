package org.omarchy.music

import java.io.File
import java.security.MessageDigest

/** A proof, on real files, that no tag write of the app's changes a song's sound: for trying from a terminal (the PC
 *  app started with this as its main class). Each file given is COPIED into a work folder (the first argument; your
 *  own file is only read), and the copy is put through every writer the scans and the Tagger use, one after another
 *  and then many at once from several threads (what cut songs short before 4.0.32). After every step the copy's sound
 *  (its bytes of music, apart from its tags) must be exactly what it was, and [FullCheck] must say what it said
 *  before. */
object TagProof {
    private fun sha(b: ByteArray, from: Int, to: Int): String =
        MessageDigest.getInstance("SHA-256").apply { update(b, from, maxOf(0, to - from)) }.digest().joinToString("") { "%02x".format(it) }.take(24)

    /** The file's sound, as a mark of its bytes of music and how many there are; null for a kind not told apart from its tags here. */
    fun sound(f: File): String? {
        val b = f.readBytes()
        fun s(i: Int, n: Int) = if (i + n <= b.size) String(b, i, n, Charsets.ISO_8859_1) else ""
        fun be(i: Int) = ((b[i].toLong() and 0xff) shl 24) or ((b[i + 1].toLong() and 0xff) shl 16) or ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
        fun le(i: Int) = (b[i].toLong() and 0xff) or ((b[i + 1].toLong() and 0xff) shl 8) or ((b[i + 2].toLong() and 0xff) shl 16) or ((b[i + 3].toLong() and 0xff) shl 24)
        var at = 0
        while (s(at, 3) == "ID3") at += 10 + (((b[at + 6].toInt() and 0x7f) shl 21) or ((b[at + 7].toInt() and 0x7f) shl 14) or ((b[at + 8].toInt() and 0x7f) shl 7) or (b[at + 9].toInt() and 0x7f)) + (if (b[at + 5].toInt() and 0x10 != 0) 10 else 0)
        var end = b.size
        if (end - at >= 128 && s(end - 128, 3) == "TAG") end -= 128
        return when {
            s(at, 4) == "fLaC" -> {
                var p = at + 4
                while (true) { val last = b[p].toInt() and 0x80 != 0; p += 4 + (be(p) and 0xffffff).toInt(); if (last) break }
                "${end - p} bytes " + sha(b, p, end)
            }
            at > 0 || ((b[0].toInt() and 0xff) == 0xff && (b[1].toInt() and 0xe0) == 0xe0) -> "${end - at} bytes " + sha(b, at, end)
            s(0, 4) == "RIFF" && s(8, 4) == "WAVE" -> {
                var p = 12
                while (p + 8 <= b.size) { val n = le(p + 4).toInt(); if (s(p, 4) == "data") return "$n bytes " + sha(b, p + 8, minOf(b.size, p + 8 + n)); p += 8 + n + (n and 1) }
                null
            }
            s(4, 4) == "ftyp" -> {
                var p = 0
                while (p + 8 <= b.size) { val n = be(p).toInt(); if (n < 8) break; if (s(p + 4, 4) == "mdat") return "${n - 8} bytes " + sha(b, p + 8, minOf(b.size, p + n)); p += n }
                null
            }
            else -> null
        }
    }

    private fun jpeg(side: Int, noisy: Boolean): ByteArray {
        val img = java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val r = java.util.Random(side.toLong())
        for (y in 0 until side) for (x in 0 until side) img.setRGB(x, y, if (noisy) r.nextInt(0xffffff) else (x * 255 / side shl 16) or (y * 255 / side shl 8) or 0x80)
        return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    @JvmStatic fun main(args: Array<String>) {
        val c = Ctx.pc
        runCatching { Crashes.init(c) }
        runCatching { Music.init(c) }.onFailure { println("(the app's own start said: $it)") }
        val small = jpeg(300, false); val big = jpeg(2200, true)
        val writers: List<Pair<String, (String, Int) -> Any?>> = listOf(
            "lyrics and the words' mood" to { p, n -> LyricsTags.writeTags(c, p, "Line one $n\nLine two\nLine three", mapOf("MOOD_LYRICS" to "Happy")) },
            "mood" to { p, n -> LyricsTags.writeMood(c, p, if (n % 2 == 0) "Happy;Sad" else "Chill") },
            "lyrics if none" to { p, _ -> LyricsTags.write(c, p, "Some words") },
            "title and genre (the Tagger)" to { p, n -> FileTags.write(c, p, mapOf("title" to "Proof $n", "genre" to "Test $n")) },
            "album and artist (Tags)" to { p, n -> Tags.write(c, p, mapOf("album" to "Proof album $n", "artist" to "Proof artist")) },
            "a cover" to { p, _ -> CoverGuard.byHand { FileTags.writeCover(c, p, small) } },
            "a big cover (the tags grow by megabytes)" to { p, _ -> CoverGuard.byHand { FileTags.writeCover(c, p, big) } },
            "long lyrics" to { p, n -> LyricsTags.writeTags(c, p, (1..400).joinToString("\n") { "Line $it of take $n, sung again and again" }, emptyMap()) },
            "moods taken out in place" to { p, _ -> SafeWrite.alone { MoodWipe.wipe(File(p), setOf("MOOD", "MOOD_LYRICS")) } },
            "mood again" to { p, _ -> LyricsTags.writeMood(c, p, "Energetic") },
        )
        // The app closed in the middle of a write. "run <copy>": the writers go round and round on the copy until this is
        // killed from outside (it says "writing" once they have started). "verify <copy> <the song it was copied from>":
        // what an app starting again does (a write that was interrupted is finished from its copy), then the copy's
        // sound must be the song's.
        if (args[0] == "run") {
            val f = File(args[1])
            println("writing"); System.out.flush()
            var n = 0
            while (true) { val (_, w) = writers[n % writers.size]; runCatching { w(f.path, n) }; n++ }
        }
        if (args[0] == "verify") {
            val f = File(args[1]); val was = sound(File(args[2]))
            val half = runCatching { sound(f) }.getOrNull() != was
            SafeWrite.recover(c)
            val now = runCatching { sound(f) }.getOrNull(); val said = runCatching { f.inputStream().use { FullCheck.check(it) } }.getOrElse { "unreadable: $it" }
            println((if (now == was && said == null) "SAME" else "CHANGED (sound $now, was $was; full check: $said)") +
                (if (half) "  [the app was killed while the file itself was being replaced: finished from the copy]" else ""))
            kotlin.system.exitProcess(if (now == was && said == null) 0 else 1)
        }
        val work = File(args[0]).apply { mkdirs() }
        println("covers used: ${small.size / 1024} KB and ${big.size / 1024} KB")
        var files = 0; var wrong = 0; var steps = 0
        for (src in args.drop(1).map { File(it) }.filter { it.isFile }) {
            files++
            val f = File(work, "proof-$files." + src.extension)
            src.copyTo(f, overwrite = true)
            val before = sound(f); val said = f.inputStream().use { FullCheck.check(it) }
            println("\n${src.name}  (${src.length() / 1024} KB; sound: ${before ?: "not told apart here"}; full check: ${said ?: "whole"})")
            var ok = true
            fun verify(step: String, result: Any?) {
                steps++
                val now = sound(f); val saidNow = f.inputStream().use { FullCheck.check(it) }
                val same = now == before && saidNow == said
                if (!same) { ok = false; println("   CHANGED after $step: sound ${now ?: "?"}; full check: ${saidNow ?: "whole"}") }
                else println("   same after $step  (${result.toString().take(60)}; file now ${f.length() / 1024} KB)")
            }
            writers.forEachIndexed { n, (name, w) -> verify(name, runCatching { w(f.path, n) }.getOrElse { "refused: ${it.message?.take(80)}" }) }
            // Many at once: six threads, each every writer four times, in its own order, on the same file
            val threads = (1..6).map { t -> Thread {
                val order = writers.shuffled(java.util.Random(t.toLong()))
                repeat(4) { round -> order.forEachIndexed { n, (_, w) -> runCatching { w(f.path, t * 100 + round * 10 + n) } } }
            } }
            threads.forEach { it.start() }; threads.forEach { it.join() }
            verify("240 writes from six threads at once", "done")
            if (!ok) wrong++
        }
        println("\n$files files, $steps checks, " + if (wrong == 0) "the sound of every one exactly as it was" else "$wrong FILES CHANGED")
        kotlin.system.exitProcess(if (wrong == 0) 0 else 1)
    }
}
