package org.omarchy.music

import java.io.File

/** What ffmpeg tells about a song's file on the PC (the Python app's quality_check and fingerprint): clipping, a
 *  hi-res file that isn't, and the song's sound as AcoustID knows it. ffmpeg: see Bins. */
object AudioTools {
    val supported = true

    fun canRunNow() = true
    const val WHEN = ""

    /** When a song's file last changed (null: it's gone), for checking it again when it changes. */
    fun modified(path: String): Long? = File(path).takeIf { it.isFile }?.lastModified()
    private val windows = System.getProperty("os.name").lowercase().contains("win")
    private val noWindow = windows

    private fun ffmpeg(): String = Bins.ffmpeg

    private fun run(vararg args: String, timeoutS: Long = 600): Pair<String, String> {
        val p = ProcessBuilder(ffmpeg(), *args).redirectInput(ProcessBuilder.Redirect.from(File(if (windows) "NUL" else "/dev/null"))).start()
        val err = StringBuilder()
        val t = Thread { err.append(p.errorStream.bufferedReader().readText()) }.apply { start() }
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(timeoutS, java.util.concurrent.TimeUnit.SECONDS); t.join(2000)
        return out to err.toString()
    }

    /** (blocking) clipped: samples at full scale (0: none to speak of); peak in dBFS; fake: "" or why it isn't the hi-res it says. */
    fun quality(path: String, rate: Int, bits: Int): QualityResult {
        val stats = "astats=measure_perchannel=none:measure_overall=Peak_level+Peak_count+Bit_depth+RMS_level"
        val out = run("-nostdin", "-nostats", "-hide_banner", "-i", path, "-af", stats, "-f", "null", "-").second
        fun num(name: String) = Regex("$name[^:]*:\\s*(-?[\\d.]+|-inf)").find(out)?.groupValues?.get(1)?.takeIf { it != "-inf" }?.toDoubleOrNull()
        val peak = num("Peak level dB"); val count = num("Peak count"); val rms = num("RMS level dB")
        val depth = Regex("Bit depth:\\s*([\\d/]+)").findAll(out).lastOrNull()?.groupValues?.get(1)?.split('/')?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        var clipped = 0
        if (peak != null && peak >= -0.1 && (count ?: 0.0) >= 200) clipped = count!!.toInt()
        val fake = ArrayList<String>()
        if (bits >= 24 && depth.isNotEmpty() && depth.max() <= 16) fake += "16-bit music in a 24-bit file"
        if (rate > 48000 && rms != null) {
            val hp = "highpass=f=21000:poles=2,highpass=f=21000:poles=2,astats=measure_perchannel=none:measure_overall=RMS_level"
            val o2 = run("-nostdin", "-nostats", "-hide_banner", "-i", path, "-af", hp, "-f", "null", "-").second
            val top = Regex("RMS level dB:\\s*(-?[\\d.]+|-inf)").find(o2)?.groupValues?.get(1)?.takeIf { it != "-inf" }?.toDoubleOrNull() ?: -200.0
            if (top - rms < -80) fake += "upsampled: nothing above 21 kHz in a ${"%.1f".format(rate / 1000.0).removeSuffix(".0")} kHz file"
        }
        return QualityResult(clipped, peak?.let { Math.round(it * 100) / 100.0 }, fake.joinToString(", "))
    }

    /** (blocking) [secs] seconds from the middle of a song (of [songSecs]; 0: ffmpeg says how long; a minute, for Scan
     *  moods), in one channel at [rate] samples a second, for Moods. ffmpeg jumps straight there (-ss before -i) and does the mixing down. */
    fun moodPcm(path: String, songSecs: Double, secs: Int, rate: Int): Pcm {
        val length = songSecs.takeIf { it > 0 } ?: Regex("Duration:\\s*(\\d+):(\\d+):([\\d.]+)").find(run("-hide_banner", "-i", path, timeoutS = 30).second)
            ?.groupValues?.let { (it[1].toDouble() * 3600 + it[2].toDouble() * 60 + it[3].toDouble()) } ?: 0.0
        val from = maxOf(0.0, length / 2 - secs / 2.0)
        val p = ProcessBuilder(ffmpeg(), "-v", "error", "-nostdin", "-ss", "%.2f".format(java.util.Locale.ROOT, from), "-i", path,
            "-t", secs.toString(), "-vn", "-ac", "1", "-ar", rate.toString(), "-f", "f32le", "-acodec", "pcm_f32le", "-")
            .redirectInput(ProcessBuilder.Redirect.from(File(if (windows) "NUL" else "/dev/null")))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val bytes = try { p.inputStream.use { it.readNBytes(secs * rate * 4) } } finally { p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS); p.destroy() }
        val n = bytes.size / 4
        if (n == 0) error("couldn't hear the song")
        val bb = java.nio.ByteBuffer.wrap(bytes, 0, n * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val out = FloatArray(n).also { bb.get(it) }
        return Pcm(out, rate)
    }

    /** (blocking) The whole song, start to end, for Moods' Full scan: ffmpeg decodes it to one channel at [rate]
     *  samples a second, read a block at a time and cut into windows, each heard and let go, so memory stays low
     *  however long the song is. null: it couldn't be heard, or [stop] said to stop (checked as it goes). */
    fun moodWhole(path: String, rate: Int, stop: () -> Boolean): SoundFeatures? {
        val p = ProcessBuilder(ffmpeg(), "-v", "error", "-nostdin", "-i", path, "-vn", "-ac", "1", "-ar", rate.toString(),
            "-f", "f32le", "-acodec", "pcm_f32le", "-")
            .redirectInput(ProcessBuilder.Redirect.from(File(if (windows) "NUL" else "/dev/null")))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val song = WholeSong()
        val win = MonoWindows(rate, 1, rate, WholeSong.WINDOW) { song.add(it) }
        try {
            val ins = p.inputStream
            val bytes = ByteArray(1 shl 16); val floats = FloatArray(bytes.size / 4)
            var keep = 0   // bytes of a sample cut off at the end of the last read
            while (true) {
                if (stop()) return null
                val n = ins.read(bytes, keep, bytes.size - keep)
                if (n <= 0) break
                val have = keep + n; val count = have / 4
                java.nio.ByteBuffer.wrap(bytes, 0, count * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats, 0, count)
                win.feed(floats, count)
                keep = have - count * 4
                if (keep > 0) System.arraycopy(bytes, count * 4, bytes, 0, keep)
            }
            win.finish()
        } finally { p.destroy(); p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) }
        if (win.heard < 1) return null
        return song.result()
    }

    /** (blocking) A song's sound as AcoustID knows it: (its fingerprint, its length in seconds), from its first two
     *  minutes. ffmpeg decodes (to WAV, for its rate and channels) and the app's own Chromaprint hears it, as on the phone,
     *  so any ffmpeg does (not only one built with chromaprint); it makes the same fingerprint ffmpeg's would. */
    fun fingerprint(path: String, secs: Int): Pair<String, Int> {
        val cp = hear(path, 120) { false } ?: error("couldn't hear the song")
        val fp = cp.finish()
        if (fp.isEmpty()) error("couldn't hear the song")
        return fp to secs
    }

    /** (blocking) The whole song's raw fingerprint (Chromaprint's words, ~8 a second), for Clean and explicit versions'
     *  Listen; null if it couldn't be heard or [stop] said to stop. */
    fun wholePrint(path: String, stop: () -> Boolean): IntArray? = hear(path, 0, stop)?.raw()?.takeIf { it.isNotEmpty() }

    /** The song (its first [maxSecs], 0: all of it) through Chromaprint: ffmpeg decodes it to WAV, the app hears it. */
    private fun hear(path: String, maxSecs: Int, stop: () -> Boolean): Chromaprint? {
        val args = mutableListOf(ffmpeg(), "-v", "error", "-nostdin", "-i", path)
        if (maxSecs > 0) args += listOf("-t", maxSecs.toString())
        args += listOf("-vn", "-ac", "2", "-f", "wav", "-acodec", "pcm_s16le", "-")
        val p = ProcessBuilder(args)
            .redirectInput(ProcessBuilder.Redirect.from(File(if (windows) "NUL" else "/dev/null")))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        return try {
            val ins = java.io.DataInputStream(p.inputStream.buffered(1 shl 16))
            fun le(b: ByteArray, o: Int, n: Int) = (0 until n).fold(0) { acc, k -> acc or ((b[o + k].toInt() and 255) shl (8 * k)) }
            fun word(): ByteArray = ByteArray(4).also { ins.readFully(it) }
            word(); word(); word()   // RIFF, its size, WAVE
            var rate = 44100; var ch = 2
            while (true) {
                val id = String(word(), Charsets.US_ASCII); val size = le(word(), 0, 4)
                if (id == "data") break
                val b = ByteArray(size + (size and 1)).also { ins.readFully(it) }
                if (id == "fmt ") { ch = le(b, 2, 2); rate = le(b, 4, 4) }
            }
            val cp = Chromaprint(rate, ch)
            val bytes = ByteArray(32768); val shorts = ShortArray(16384)
            while (true) {
                if (stop()) return null
                var n = ins.read(bytes); if (n <= 0) break
                if (n % 2 == 1) { if (ins.read(bytes, n, 1) == 1) n++ else n-- }
                for (k in 0 until n / 2) shorts[k] = ((bytes[2 * k].toInt() and 255) or (bytes[2 * k + 1].toInt() shl 8)).toShort()
                cp.feed(shorts, n / 2)
            }
            cp
        } catch (_: java.io.EOFException) { null } finally { p.destroy(); p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) }
    }
}
