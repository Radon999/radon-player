package org.omarchy.music

import java.io.File

/** The music's spectrum on the PC (the visualizer, Now playing's bars): the song playing decoded a second time by ffmpeg,
 *  in step with the player (from where it is, at its pace), its sound cut into Spectrum.BANDS levels as Android's
 *  visualizer gives them on the phone. Started while something shows it; stopped when nothing does. */
object PcSpectrum {
    val levels = FloatArray(Spectrum.BANDS)
    @Volatile private var users = 0
    @Volatile private var proc: Process? = null
    private var key = ""
    private var startedAt = 0L
    private var startedPos = 0.0
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    private fun ffmpeg(): String = Bins.ffmpeg

    fun use() { synchronized(this) { if (users++ == 0) Thread({ watch() }, "spectrum").apply { isDaemon = true }.start() } }
    fun done() { synchronized(this) { users = maxOf(0, users - 1) } }

    /** Every so often: is the decoder where the player is? If not (a new song, a seek, a pause), start it again there. */
    private fun watch() {
        while (users > 0) {
            val t = Music.current()
            val uri = t?.let { it.file ?: Music.lib.items[it.id]?.file ?: (Music.player as? MpvEngine)?.currentUri }
            val playing = !Music.paused && uri != null
            val expected = startedPos + (System.currentTimeMillis() - startedAt) / 1000.0
            val drift = kotlin.math.abs(expected - Music.position)
            val k = if (playing) "$uri" else ""
            if (k != key || (playing && drift > 1.5)) {
                stop()
                key = k
                if (playing) start(uri!!, Music.position)
                else levels.fill(0f)
            }
            Thread.sleep(400)
        }
        stop(); key = ""; levels.fill(0f)
    }

    private fun stop() { proc?.destroy(); proc = null }

    private fun start(uri: String, pos: Double) {
        val src = if (uri.startsWith("file:")) java.net.URI(uri).path else uri
        val p = runCatching {
            ProcessBuilder(ffmpeg(), "-v", "quiet", "-nostdin", "-re", "-ss", "%.2f".format(java.util.Locale.US, pos), "-i", src,
                "-ac", "1", "-ar", "44100", "-f", "f32le", "-")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }.getOrNull() ?: return
        proc = p; startedAt = System.currentTimeMillis(); startedPos = pos
        Thread({ read(p) }, "spectrum-read").apply { isDaemon = true }.start()
    }

    private fun read(p: Process) {
        val n = 1024
        val buf = ByteArray(n * 4)
        val re = DoubleArray(n); val im = DoubleArray(n)
        val window = DoubleArray(n) { 0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * it / (n - 1)) }
        val fft = ByteArray(n)
        val ins = p.inputStream
        try {
            while (proc === p) {
                var got = 0
                while (got < buf.size) { val k = ins.read(buf, got, buf.size - got); if (k < 0) return; got += k }
                val bb = java.nio.ByteBuffer.wrap(buf).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until n) { re[i] = bb.getFloat(i * 4) * window[i] * 2; im[i] = 0.0 }
                transform(re, im)
                // As Android's visualizer hands it over: DC, Nyquist, then (real, imaginary) pairs, a full-scale sine near 128
                val k = 128.0 / (n / 2)
                fft[0] = (re[0] * k).toInt().coerceIn(-128, 127).toByte(); fft[1] = (re[n / 2] * k).toInt().coerceIn(-128, 127).toByte()
                for (i in 1 until n / 2) {
                    fft[2 * i] = (re[i] * k).toInt().coerceIn(-128, 127).toByte()
                    fft[2 * i + 1] = (im[i] * k).toInt().coerceIn(-128, 127).toByte()
                }
                Spectrum.bands(fft, 44100).copyInto(levels)
            }
        } catch (_: Exception) {
        }
    }

    /** In-place radix-2 FFT. */
    private fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * Math.PI / len
            val wr = kotlin.math.cos(ang); val wi = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
