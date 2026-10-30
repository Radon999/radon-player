package org.omarchy.music

import java.io.File

/** What ffmpeg tells about a song's file on the PC (the Python app's quality_check and fingerprint): clipping, a
 *  hi-res file that isn't, and the song's sound as AcoustID knows it. ffmpeg: RADON_FFMPEG, the Python app's own (its
 *  installer's bin folder), or ffmpeg on the PATH. */
object AudioTools {
    val supported = true
    private val windows = System.getProperty("os.name").lowercase().contains("win")
    private val noWindow = windows

    private fun ffmpeg(): String = System.getenv("RADON_FFMPEG")
        ?: listOfNotNull(System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Omarchy Music\\bin\\ffmpeg.exe" }).firstOrNull { File(it).exists() }
        ?: "ffmpeg"

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

    /** (blocking) A song's sound as AcoustID knows it: (its fingerprint, its length in seconds). */
    fun fingerprint(path: String, secs: Int): Pair<String, Int> {
        val fp = run("-v", "error", "-nostdin", "-i", path, "-t", "120", "-ac", "2", "-f", "chromaprint", "-fp_format", "base64", "-", timeoutS = 180).first.trim()
        if (fp.isEmpty()) error("couldn't hear the song (this ffmpeg can't make fingerprints)")
        return fp to secs
    }
}
