package org.omarchy.music

/** A WAV file's sound: its samples (-1 to 1) as one channel (several are averaged) and its sample rate. For the
 *  recording the DJ's voice is modelled on. 16-bit and 32-bit float PCM; null for anything else or a file cut short. */
fun wavSamples(bytes: ByteArray): Pair<FloatArray, Int>? {
    fun u16(i: Int) = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
    fun u32(i: Int) = u16(i) or (u16(i + 2) shl 16)
    fun tag(i: Int) = String(bytes, i, 4, Charsets.ISO_8859_1)
    if (bytes.size < 44 || tag(0) != "RIFF" || tag(8) != "WAVE") return null
    var i = 12
    var format = 0; var channels = 0; var rate = 0; var bits = 0
    while (i + 8 <= bytes.size) {
        val id = tag(i); val len = u32(i + 4); val at = i + 8
        if (id == "fmt " && at + 16 <= bytes.size) {
            format = u16(at); channels = u16(at + 2); rate = u32(at + 4); bits = u16(at + 14)
        } else if (id == "data") {
            if (channels <= 0 || rate <= 0) return null
            val float = bits == 32 && (format == 3 || format == 0xFFFE)
            if (!float && !(bits == 16 && (format == 1 || format == 0xFFFE))) return null
            val end = if (len <= 0 || at + len > bytes.size) bytes.size else at + len   // (a length left open: to the end)
            val frame = channels * bits / 8
            val n = (end - at) / frame
            val out = FloatArray(n)
            for (k in 0 until n) {
                var sum = 0f
                for (c in 0 until channels) {
                    val p = at + k * frame + c * bits / 8
                    sum += if (float) java.lang.Float.intBitsToFloat(u32(p)) else u16(p).toShort() / 32768f
                }
                out[k] = sum / channels
            }
            return out to rate
        }
        if (len < 0) return null
        i = at + len + (len and 1)
    }
    return null
}

/** Samples (-1 to 1) as 16-bit little-endian PCM bytes, to play them. */
fun pcm16(samples: FloatArray): ByteArray {
    val out = ByteArray(samples.size * 2)
    for (k in samples.indices) {
        val v = (samples[k].coerceIn(-1f, 1f) * 32767f).toInt()
        out[2 * k] = v.toByte(); out[2 * k + 1] = (v shr 8).toByte()
    }
    return out
}
