package org.omarchy.music

/** The music as it goes to a USB DAC straight from the app (bit-perfect around Android): Android's visualizer hears
 *  nothing then (the sound never passes through Android), so the visualizers read it here instead. The player's
 *  output hands each frame in (mono, -1..1); the spectrum is worked out from what's being heard now (the frames
 *  still queued for the DAC held back), in the same form Android's visualizer gives it. */
object AudioTap {
    private const val N = 1 shl 16
    private val ring = FloatArray(N)
    private var written = 0L
    @Volatile private var done = 0L        // frames written, as last published
    @Volatile var rate = 44100
    @Volatile private var lastAt = 0L
    /** Frames handed in but not heard yet (queued for the DAC). */
    @Volatile var lag: () -> Long = { 0L }

    /** (the player's thread) One frame. */
    fun push(x: Float) { ring[(written and (N - 1).toLong()).toInt()] = x; written++ }
    /** (the player's thread) A buffer's frames are in. */
    fun pushed(rate: Int) { this.rate = rate; done = written; lastAt = System.currentTimeMillis() }

    /** Music has come through here lately (then the visualizers use it). */
    val active get() = System.currentTimeMillis() - lastAt < 1500

    /** The spectrum of the last size frames heard, as Android's Visualizer gives it: DC, Nyquist, then real and
     *  imaginary bytes for each bin. */
    fun fft(size: Int = 1024): ByteArray? {
        val end = done - runCatching { lag() }.getOrDefault(0L).coerceIn(0L, (N - size).toLong())
        if (end < size) return null
        val re = DoubleArray(size); val im = DoubleArray(size)
        for (i in 0 until size) {
            val w = 0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * i / (size - 1))   // Hann
            re[i] = ring[((end - size + i) and (N - 1).toLong()).toInt()] * 128.0 * w
        }
        // In place, radix 2
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var len = 2
        while (len <= size) {
            val ang = -2 * Math.PI / len
            val wr = kotlin.math.cos(ang); val wi = kotlin.math.sin(ang)
            var s = 0
            while (s < size) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = s + k; val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci; val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                s += len
            }
            len = len shl 1
        }
        val k = 4.0 / size   // a full-scale sine's bin about 128, as Android's
        fun b(x: Double) = (x * k).toInt().coerceIn(-128, 127).toByte()
        val out = ByteArray(size)
        out[0] = b(re[0]); out[1] = b(re[size / 2])
        for (i in 1 until size / 2) { out[2 * i] = b(re[i]); out[2 * i + 1] = b(im[i]) }
        return out
    }
}
