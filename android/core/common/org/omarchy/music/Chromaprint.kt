/*
 * Chromaprint.kt - a pure Kotlin port of Chromaprint (https://acoustid.org/chromaprint),
 * default algorithm (CHROMAPRINT_ALGORITHM_TEST2, "algorithm 1" as used by AcoustID).
 *
 * Original Chromaprint: Copyright (C) 2010-2016 Lukas Lalinsky.
 * Resampler (avresample/resample2.c): Copyright (c) 2004 Michael Niedermayer.
 * KISS FFT: Copyright (c) 2003-2010 Mark Borgerding (BSD-3-Clause).
 *
 * This port is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation;
 * either version 2.1 of the License, or (at your option) any later version, like the
 * original.
 *
 * The pipeline mirrors the C++ code exactly (internal av_resample polyphase resampler to
 * 11025 Hz mono, 4096-sample Hamming-windowed float KISS FFT with 2/3 overlap, 12-band
 * chroma 28..3520 Hz, 5-tap chroma filter, normalizer, rolling integral image, 16
 * classifiers with gray-code quantization, compressor, URL-safe unpadded base64), so the
 * output string matches `ffmpeg -f chromaprint -fp_format base64` / fpcalc.
 */
package org.omarchy.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Chromaprint(sampleRate: Int, channels: Int) {
    private val numChannels = channels
    private val leftover = ShortArray(max(channels, 1))
    private var leftoverSize = 0

    // ---- AudioProcessor ----
    private val buffer = ShortArray(MAX_BUFFER_SIZE)
    private var bufferOffset = 0
    private val resampleBuffer = ShortArray(MAX_BUFFER_SIZE)
    private val resampler: Resampler? =
        if (sampleRate != TARGET_RATE) Resampler(TARGET_RATE, sampleRate, 16, 8, 0.8) else null

    // ---- FFT (slicer + window + kiss_fftr) ----
    private val frame = ShortArray(FRAME_SIZE + MAX_BUFFER_SIZE)
    private var frameFill = 0
    private val window = FloatArray(FRAME_SIZE).also {
        val scale = 1.0 / 32767
        for (i in 0 until FRAME_SIZE) it[i] = (scale * (0.54 - 0.46 * cos(i * 2.0 * PI / (FRAME_SIZE - 1)))).toFloat()
    }
    private val fft = KissFftr(FRAME_SIZE)
    private val fftIn = FloatArray(FRAME_SIZE)
    private val fftOut = FloatArray(FRAME_SIZE + 2)
    private val spectrum = DoubleArray(FRAME_SIZE / 2 + 1)

    // ---- Chroma ----
    private val minIndex = max(1, freqToIndex(28.0))
    private val maxIndex = min(FRAME_SIZE / 2, freqToIndex(3520.0))
    private val notes = IntArray(FRAME_SIZE).also {
        for (i in minIndex until maxIndex) {
            val freq = i.toDouble() * TARGET_RATE / FRAME_SIZE
            val octave = ln(freq / (440.0 / 16.0)) / ln(2.0)
            val note = 12 * (octave - floor(octave))
            it[i] = note.toInt()
        }
    }
    private val features = DoubleArray(12)

    // ---- ChromaFilter ----
    private val filterBuf = Array(8) { DoubleArray(12) }
    private var filterOffset = 0
    private var filterSize = 1
    private val filterResult = DoubleArray(12)

    // ---- FingerprintCalculator / RollingIntegralImage ----
    private val imageMaxRows = 257
    private val image = DoubleArray(imageMaxRows * 12)
    private var imageRows = 0
    private var fingerprint = IntArray(1024)
    private var fingerprintSize = 0

    init {
        require(channels > 0) { "no audio channels" }
        require(sampleRate > 1000) { "sample rate too low: $sampleRate" }
    }

    /** Feeds interleaved 16-bit samples; [count] is the number of shorts (all channels). */
    fun feed(pcm: ShortArray, count: Int) {
        var pos = 0
        var length = count
        if (leftoverSize > 0) {
            while (length > 0 && leftoverSize < numChannels) {
                leftover[leftoverSize++] = pcm[pos++]; length--
            }
            if (leftoverSize < numChannels) return
            consumeAligned(leftover, 0, numChannels)
            leftoverSize = 0
        }
        val remainder = length % numChannels
        consumeAligned(pcm, pos, length - remainder)
        for (i in 0 until remainder) leftover[leftoverSize++] = pcm[pos + length - remainder + i]
    }

    /** Flushes the pipeline and returns the compressed base64 fingerprint. */
    fun finish(): String {
        if (bufferOffset > 0) resample()
        return encode(fingerprint, fingerprintSize)
    }

    private fun consumeAligned(input: ShortArray, start: Int, len: Int) {
        var pos = start
        var frames = len / numChannels
        while (frames > 0) {
            val n = min(frames, MAX_BUFFER_SIZE - bufferOffset)
            var o = bufferOffset
            when (numChannels) {
                1 -> { System.arraycopy(input, pos, buffer, o, n); pos += n }
                2 -> for (k in 0 until n) {
                    buffer[o++] = ((input[pos] + input[pos + 1]) / 2).toShort(); pos += 2
                }
                else -> for (k in 0 until n) {
                    var sum = 0
                    for (c in 0 until numChannels) sum += input[pos++]
                    buffer[o++] = (sum / numChannels).toShort()
                }
            }
            bufferOffset += n
            frames -= n
            if (bufferOffset == MAX_BUFFER_SIZE) {
                resample()
                if (bufferOffset == MAX_BUFFER_SIZE) return
            }
        }
    }

    private fun resample() {
        val rs = resampler
        if (rs == null) {
            consumeAudio(buffer, bufferOffset)
            bufferOffset = 0
            return
        }
        var length = rs.resample(resampleBuffer, buffer, bufferOffset, MAX_BUFFER_SIZE)
        if (length > MAX_BUFFER_SIZE) length = MAX_BUFFER_SIZE
        consumeAudio(resampleBuffer, length)
        val consumed = rs.consumed
        var remaining = bufferOffset - consumed
        if (remaining > 0) System.arraycopy(buffer, consumed, buffer, 0, remaining)
        else if (remaining < 0) remaining = 0
        bufferOffset = remaining
    }

    // Equivalent to AudioSlicer: frames of FRAME_SIZE starting every INCREMENT samples.
    private fun consumeAudio(input: ShortArray, length: Int) {
        var pos = 0
        while (pos < length) {
            val n = min(length - pos, FRAME_SIZE - frameFill)
            System.arraycopy(input, pos, frame, frameFill, n)
            frameFill += n
            pos += n
            if (frameFill == FRAME_SIZE) {
                processFrame()
                System.arraycopy(frame, INCREMENT, frame, 0, FRAME_SIZE - INCREMENT)
                frameFill = FRAME_SIZE - INCREMENT
            }
        }
    }

    private fun processFrame() {
        for (i in 0 until FRAME_SIZE) fftIn[i] = frame[i].toFloat() * window[i]
        fft.transform(fftIn, fftOut)
        for (i in 0..FRAME_SIZE / 2) {
            val r = fftOut[2 * i]; val im = fftOut[2 * i + 1]
            spectrum[i] = (r * r + im * im).toDouble()
        }
        // Chroma
        features.fill(0.0)
        for (i in minIndex until maxIndex) features[notes[i]] += spectrum[i]
        // ChromaFilter
        System.arraycopy(features, 0, filterBuf[filterOffset], 0, 12)
        filterOffset = (filterOffset + 1) % 8
        if (filterSize >= 5) {
            val offset = (filterOffset + 8 - 5) % 8
            filterResult.fill(0.0)
            for (i in 0 until 12) for (j in 0 until 5) {
                filterResult[i] += filterBuf[(offset + j) % 8][i] * FILTER_COEFFS[j]
            }
            normalizeAndAdd(filterResult)
        } else {
            filterSize++
        }
    }

    private fun normalizeAndAdd(f: DoubleArray) {
        var squares = 0.0
        for (v in f) squares += v * v
        val norm = if (squares > 0) sqrt(squares) else 0.0
        if (norm < 0.01) f.fill(0.0) else for (i in 0 until 12) f[i] /= norm
        // RollingIntegralImage.AddRow
        val cur = (imageRows % imageMaxRows) * 12
        var acc = 0.0
        for (i in 0 until 12) { acc = if (i == 0) f[0] else acc + f[i]; image[cur + i] = acc }
        if (imageRows > 0) {
            val last = ((imageRows - 1) % imageMaxRows) * 12
            for (i in 0 until 12) image[cur + i] = image[last + i] + image[cur + i]
        }
        imageRows++
        if (imageRows >= MAX_FILTER_WIDTH) {
            val offset = imageRows - MAX_FILTER_WIDTH
            var bits = 0
            for (c in 0 until 16) {
                val v = applyFilter(CLS_TYPE[c], offset, CLS_Y[c], CLS_W[c], CLS_H[c])
                val q = if (v < CLS_T1[c]) (if (v < CLS_T0[c]) 0 else 1) else (if (v < CLS_T2[c]) 2 else 3)
                bits = (bits shl 2) or GRAY[q]
            }
            if (fingerprintSize == fingerprint.size) fingerprint = fingerprint.copyOf(fingerprint.size * 2)
            fingerprint[fingerprintSize++] = bits
        }
    }

    private fun area(r1: Int, c1: Int, r2: Int, c2: Int): Double {
        if (r1 == r2 || c1 == c2) return 0.0
        return if (r1 == 0) {
            val row = ((r2 - 1) % imageMaxRows) * 12
            if (c1 == 0) image[row + c2 - 1] else image[row + c2 - 1] - image[row + c1 - 1]
        } else {
            val row1 = ((r1 - 1) % imageMaxRows) * 12
            val row2 = ((r2 - 1) % imageMaxRows) * 12
            if (c1 == 0) image[row2 + c2 - 1] - image[row1 + c2 - 1]
            else image[row2 + c2 - 1] - image[row1 + c2 - 1] - image[row2 + c1 - 1] + image[row1 + c1 - 1]
        }
    }

    private fun subtractLog(a: Double, b: Double): Double = ln((1.0 + a) / (1.0 + b))

    private fun applyFilter(type: Int, x: Int, y: Int, w: Int, h: Int): Double = when (type) {
        0 -> subtractLog(area(x, y, x + w, y + h), 0.0)
        1 -> { val h2 = h / 2; subtractLog(area(x, y + h2, x + w, y + h), area(x, y, x + w, y + h2)) }
        2 -> { val w2 = w / 2; subtractLog(area(x + w2, y, x + w, y + h), area(x, y, x + w2, y + h)) }
        3 -> {
            val w2 = w / 2; val h2 = h / 2
            subtractLog(
                area(x, y + h2, x + w2, y + h) + area(x + w2, y, x + w, y + h2),
                area(x, y, x + w2, y + h2) + area(x + w2, y + h2, x + w, y + h))
        }
        4 -> {
            val h3 = h / 3
            subtractLog(area(x, y + h3, x + w, y + 2 * h3),
                area(x, y, x + w, y + h3) + area(x, y + 2 * h3, x + w, y + h))
        }
        5 -> {
            val w3 = w / 3
            subtractLog(area(x + w3, y, x + 2 * w3, y + h),
                area(x, y, x + w3, y + h) + area(x + 2 * w3, y, x + w, y + h))
        }
        else -> 0.0
    }

    /** av_resample (resample2.c, FELEM=int16, FILTER_SHIFT=15, kaiser window type 9). */
    private class Resampler(outRate: Int, inRate: Int, filterSize: Int, private val phaseShift: Int, cutoff: Double) {
        private val phaseMask = (1 shl phaseShift) - 1
        private val filterLength: Int
        private val filterBank: ShortArray
        private val srcIncr = outRate
        private val dstIncr: Int
        private var index: Int
        private var frac = 0
        var consumed = 0

        init {
            val factor = min(outRate * cutoff / inRate, 1.0)
            val phaseCount = 1 shl phaseShift
            filterLength = max(ceil(filterSize / factor).toInt(), 1)
            filterBank = ShortArray(filterLength * (phaseCount + 1))
            buildFilter(factor, filterLength, phaseCount, 1 shl 15, 9)
            System.arraycopy(filterBank, 0, filterBank, filterLength * phaseCount + 1, filterLength - 1)
            filterBank[filterLength * phaseCount] = filterBank[filterLength - 1]
            dstIncr = inRate * phaseCount
            index = -phaseCount * ((filterLength - 1) / 2)
        }

        private fun bessel(x0: Double): Double {
            var v = 1.0; var lastv = 0.0; var t = 1.0
            val x = x0 * x0 / 4
            var i = 1
            while (v != lastv) {
                lastv = v
                t *= x / (i * i)
                v += t
                i++
            }
            return v
        }

        private fun buildFilter(factorIn: Double, tapCount: Int, phaseCount: Int, scale: Int, type: Int) {
            val factor = if (factorIn > 1.0) 1.0 else factorIn
            val tab = DoubleArray(tapCount)
            val center = (tapCount - 1) / 2
            for (ph in 0 until phaseCount) {
                var norm = 0.0
                for (i in 0 until tapCount) {
                    val x = PI * ((i - center).toDouble() - ph.toDouble() / phaseCount) * factor
                    var y = if (x == 0.0) 1.0 else sin(x) / x
                    val w = 2.0 * x / (factor * tapCount * PI)
                    y *= bessel(type * sqrt(max(1 - w * w, 0.0)))
                    tab[i] = y
                    norm += y
                }
                for (i in 0 until tapCount) {
                    // lrintf(): argument rounded to float, then rounded half-to-even
                    val v = Math.rint((tab[i] * scale / norm).toFloat().toDouble()).toLong()
                    filterBank[ph * tapCount + i] = v.coerceIn(-32768L, 32767L).toInt().toShort()
                }
            }
        }

        fun resample(dst: ShortArray, src: ShortArray, srcSize: Int, dstSize: Int): Int {
            var idx = index
            var fr = frac
            val incrFrac = dstIncr % srcIncr
            val incr = dstIncr / srcIncr
            val fl = filterLength
            var dstIndex = 0
            while (dstIndex < dstSize) {
                val fOff = fl * (idx and phaseMask)
                val sampleIndex = idx shr phaseShift
                var v = 0
                if (sampleIndex < 0) {
                    for (i in 0 until fl) v += src[abs(sampleIndex + i) % srcSize] * filterBank[fOff + i]
                } else if (sampleIndex + fl > srcSize) {
                    break
                } else {
                    for (i in 0 until fl) v += src[sampleIndex + i] * filterBank[fOff + i]
                }
                v = (v + (1 shl 14)) shr 15
                dst[dstIndex] = (if ((v + 32768).toUInt() > 65535u) (v shr 31) xor 32767 else v).toShort()
                fr += incrFrac
                idx += incr
                if (fr >= srcIncr) { fr -= srcIncr; idx++ }
                dstIndex++
            }
            consumed = max(idx, 0) shr phaseShift
            if (idx >= 0) idx = idx and phaseMask
            frac = fr
            index = idx
            return dstIndex
        }
    }

    /** KISS FFT real transform (float), radix-4/radix-2 only, as used for nfft = 4096. */
    private class KissFftr(nfft: Int) {
        private val ncfft = nfft / 2
        private val tw = FloatArray(2 * ncfft)
        private val superTw = FloatArray(ncfft)
        private val factors = IntArray(64)
        private val tmp = FloatArray(2 * ncfft)

        init {
            val pi = 3.141592653589793238462643383279502884197169399375105820974944
            for (i in 0 until ncfft) {
                val phase = -2 * pi * i / ncfft
                tw[2 * i] = cos(phase).toFloat(); tw[2 * i + 1] = sin(phase).toFloat()
            }
            for (i in 0 until ncfft / 2) {
                val phase = -3.14159265358979323846264338327 * ((i + 1).toDouble() / ncfft + .5)
                superTw[2 * i] = cos(phase).toFloat(); superTw[2 * i + 1] = sin(phase).toFloat()
            }
            var n = ncfft; var p = 4; var k = 0
            val floorSqrt = floor(sqrt(n.toDouble()))
            do {
                while (n % p != 0) {
                    p = when (p) { 4 -> 2; 2 -> 3; else -> p + 2 }
                    if (p > floorSqrt) p = n
                }
                n /= p
                factors[k++] = p; factors[k++] = n
            } while (n > 1)
            var j = 0
            while (j < k) { require(factors[j] == 2 || factors[j] == 4) { "unsupported FFT size" }; j += 2 }
        }

        fun transform(timedata: FloatArray, out: FloatArray) {
            work(tmp, 0, timedata, 0, 1, 0)
            val tdcr = tmp[0]; val tdci = tmp[1]
            out[0] = tdcr + tdci; out[1] = 0f
            out[2 * ncfft] = tdcr - tdci; out[2 * ncfft + 1] = 0f
            for (k in 1..ncfft / 2) {
                val fpkr = tmp[2 * k]; val fpki = tmp[2 * k + 1]
                val fpnkr = tmp[2 * (ncfft - k)]; val fpnki = -tmp[2 * (ncfft - k) + 1]
                val f1kr = fpkr + fpnkr; val f1ki = fpki + fpnki
                val f2kr = fpkr - fpnkr; val f2ki = fpki - fpnki
                val twr = superTw[2 * (k - 1)]; val twi = superTw[2 * (k - 1) + 1]
                val tr = f2kr * twr - f2ki * twi
                val ti = f2kr * twi + f2ki * twr
                out[2 * k] = (f1kr + tr) * 0.5f
                out[2 * k + 1] = (f1ki + ti) * 0.5f
                out[2 * (ncfft - k)] = (f1kr - tr) * 0.5f
                out[2 * (ncfft - k) + 1] = (ti - f1ki) * 0.5f
            }
        }

        // fout/f are complex indices into interleaved arrays
        private fun work(fo: FloatArray, foutStart: Int, f: FloatArray, fStart: Int, fstride: Int, fi: Int) {
            val p = factors[fi]; val m = factors[fi + 1]
            val foutEnd = foutStart + p * m
            var fout = foutStart
            var fIdx = fStart
            if (m == 1) {
                do {
                    fo[2 * fout] = f[2 * fIdx]; fo[2 * fout + 1] = f[2 * fIdx + 1]
                    fIdx += fstride
                } while (++fout != foutEnd)
            } else {
                do {
                    work(fo, fout, f, fIdx, fstride * p, fi + 2)
                    fIdx += fstride
                    fout += m
                } while (fout != foutEnd)
            }
            if (p == 4) bfly4(fo, foutStart, fstride, m) else bfly2(fo, foutStart, fstride, m)
        }

        private fun bfly2(o: FloatArray, start: Int, fstride: Int, m: Int) {
            var a = 2 * start; var b = 2 * (start + m); var t = 0
            for (k in 0 until m) {
                val twr = tw[t]; val twi = tw[t + 1]
                val tr = o[b] * twr - o[b + 1] * twi
                val ti = o[b] * twi + o[b + 1] * twr
                t += 2 * fstride
                o[b] = o[a] - tr; o[b + 1] = o[a + 1] - ti
                o[a] += tr; o[a + 1] += ti
                a += 2; b += 2
            }
        }

        private fun bfly4(o: FloatArray, start: Int, fstride: Int, m: Int) {
            var t1 = 0; var t2 = 0; var t3 = 0
            var f0 = 2 * start
            val m1 = 2 * m; val m2 = 4 * m; val m3 = 6 * m
            for (k in 0 until m) {
                val a1r = o[f0 + m1]; val a1i = o[f0 + m1 + 1]
                val a2r = o[f0 + m2]; val a2i = o[f0 + m2 + 1]
                val a3r = o[f0 + m3]; val a3i = o[f0 + m3 + 1]
                val s0r = a1r * tw[t1] - a1i * tw[t1 + 1]; val s0i = a1r * tw[t1 + 1] + a1i * tw[t1]
                val s1r = a2r * tw[t2] - a2i * tw[t2 + 1]; val s1i = a2r * tw[t2 + 1] + a2i * tw[t2]
                val s2r = a3r * tw[t3] - a3i * tw[t3 + 1]; val s2i = a3r * tw[t3 + 1] + a3i * tw[t3]
                var fr = o[f0]; var fi = o[f0 + 1]
                val s5r = fr - s1r; val s5i = fi - s1i
                fr += s1r; fi += s1i
                val s3r = s0r + s2r; val s3i = s0i + s2i
                val s4r = s0r - s2r; val s4i = s0i - s2i
                o[f0 + m2] = fr - s3r; o[f0 + m2 + 1] = fi - s3i
                t1 += 2 * fstride; t2 += 4 * fstride; t3 += 6 * fstride
                o[f0] = fr + s3r; o[f0 + 1] = fi + s3i
                o[f0 + m1] = s5r + s4i; o[f0 + m1 + 1] = s5i - s4r
                o[f0 + m3] = s5r - s4i; o[f0 + m3 + 1] = s5i + s4r
                f0 += 2
            }
        }
    }

    companion object {
        private const val TARGET_RATE = 11025
        private const val MAX_BUFFER_SIZE = 1024 * 32
        private const val FRAME_SIZE = 4096
        private const val INCREMENT = FRAME_SIZE / 3 // frame_size - overlap, overlap = 4096 - 4096/3
        private const val MAX_FILTER_WIDTH = 16
        private const val ALGORITHM = 1
        private val FILTER_COEFFS = doubleArrayOf(0.25, 0.75, 1.0, 0.75, 0.25)
        private val GRAY = intArrayOf(0, 1, 3, 2)

        // Classifier(Filter(type, y, height, width), Quantizer(t0, t1, t2)) - kClassifiersTest2
        private val CLS_TYPE = intArrayOf(0, 4, 1, 3, 3, 4, 1, 2, 2, 2, 5, 3, 2, 3, 1, 3)
        private val CLS_Y = intArrayOf(4, 4, 0, 8, 4, 0, 2, 7, 6, 1, 10, 6, 1, 5, 9, 4)
        private val CLS_H = intArrayOf(3, 6, 4, 2, 4, 3, 2, 3, 2, 3, 1, 2, 1, 6, 2, 2)
        private val CLS_W = intArrayOf(15, 15, 16, 12, 8, 5, 9, 4, 16, 2, 15, 10, 14, 4, 12, 14)
        private val CLS_T0 = doubleArrayOf(1.98215, -1.03809, -0.298702, -0.105439, -0.142891, -0.826319, -0.557409,
            -0.0646826, -0.192387, -0.0397818, -0.53823, -0.124877, -0.101475, -0.0799915, -0.272556, -0.164292)
        private val CLS_T1 = doubleArrayOf(2.35817, -0.651211, 0.119262, 0.0153946, 0.0258736, -0.590612, -0.233035,
            0.00620476, -0.029699, -0.00568076, -0.369934, 0.0296483, 0.0225617, -0.00729616, 0.019424, -0.0321188)
        private val CLS_T2 = doubleArrayOf(2.63523, -0.282167, 0.558497, 0.135898, 0.200632, -0.368214, 0.0534525,
            0.0784847, 0.215855, 0.0292026, -0.190235, 0.139239, 0.231971, 0.063262, 0.302559, 0.0846339)

        private fun freqToIndex(freq: Double): Int = Math.round(FRAME_SIZE * freq / TARGET_RATE).toInt()

        private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        /** FingerprintCompressor + URL-safe unpadded base64 (chromaprint_encode_fingerprint). */
        private fun encode(fp: IntArray, size: Int): String {
            val normal = Bits(); val exceptional = Bits()
            for (i in 0 until size) {
                var x = if (i == 0) fp[0] else fp[i] xor fp[i - 1]
                var bit = 1; var lastBit = 0
                while (x != 0) {
                    if (x and 1 != 0) {
                        val value = bit - lastBit
                        if (value >= 7) { normal.put(7, 3); exceptional.put(value - 7, 5) } else normal.put(value, 3)
                        lastBit = bit
                    }
                    x = x ushr 1
                    bit++
                }
                normal.put(0, 3)
            }
            val bytes = java.io.ByteArrayOutputStream()
            bytes.write(ALGORITHM and 255)
            bytes.write((size shr 16) and 255); bytes.write((size shr 8) and 255); bytes.write(size and 255)
            normal.flushTo(bytes); exceptional.flushTo(bytes)
            val src = bytes.toByteArray()
            val sb = StringBuilder((src.size * 4 + 2) / 3)
            var i = 0
            while (i + 3 <= src.size) {
                val s0 = src[i].toInt() and 255; val s1 = src[i + 1].toInt() and 255; val s2 = src[i + 2].toInt() and 255
                sb.append(B64[s0 shr 2]).append(B64[((s0 shl 4) or (s1 shr 4)) and 63])
                    .append(B64[((s1 shl 2) or (s2 shr 6)) and 63]).append(B64[s2 and 63])
                i += 3
            }
            if (src.size - i == 2) {
                val s0 = src[i].toInt() and 255; val s1 = src[i + 1].toInt() and 255
                sb.append(B64[s0 shr 2]).append(B64[((s0 shl 4) or (s1 shr 4)) and 63]).append(B64[(s1 shl 2) and 63])
            } else if (src.size - i == 1) {
                val s0 = src[i].toInt() and 255
                sb.append(B64[s0 shr 2]).append(B64[(s0 shl 4) and 63])
            }
            return sb.toString()
        }
    }

    /** LSB-first bit packer (PackInt3Array / PackInt5Array). */
    private class Bits {
        private val out = java.io.ByteArrayOutputStream()
        private var acc = 0
        private var n = 0
        fun put(value: Int, width: Int) {
            acc = acc or ((value and ((1 shl width) - 1)) shl n)
            n += width
            while (n >= 8) { out.write(acc and 255); acc = acc ushr 8; n -= 8 }
        }
        fun flushTo(dst: java.io.ByteArrayOutputStream) {
            if (n > 0) { out.write(acc and 255); acc = 0; n = 0 }
            out.writeTo(dst)
        }
    }
}
