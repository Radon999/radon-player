package org.omarchy.music

import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A song clip made on the phone ([ClipRules]), with Android's own coders (the PC uses ffmpeg): the card as a video
 *  that stands still but for its seek bar, and the song's sound from [startSecs] for [secs], in one .mp4.
 *  The sound is decoded and made AAC (48 kHz or less, two channels); the picture is H.264. Both are made in memory
 *  first (a minute is a few megabytes), then written together. */
object ClipMaker {
    private class Sample(val data: ByteArray, val timeUs: Long, val flags: Int)
    private class Coded(val format: MediaFormat, val samples: List<Sample>)

    /** (blocking) Throws, with why in words, if the clip can't be made. */
    fun make(card: Bitmap, bar: ClipRules.Bar?, source: String, startSecs: Double, secs: Double, out: File) {
        val pcm = decode(source, startSecs, secs)
        val audio = encodeAudio(pcm.first, pcm.second, pcm.third)
        val video = encodeVideo(card, bar, secs)
        out.parentFile?.mkdirs(); out.delete()
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val vt = mux.addTrack(video.format); val at = mux.addTrack(audio.format)
            mux.start()
            // in the order of their times, as a player wants them
            var v = 0; var a = 0; val info = MediaCodec.BufferInfo()
            while (v < video.samples.size || a < audio.samples.size) {
                val takeV = a >= audio.samples.size || (v < video.samples.size && video.samples[v].timeUs <= audio.samples[a].timeUs)
                val s = if (takeV) video.samples[v++] else audio.samples[a++]
                info.set(0, s.data.size, s.timeUs, s.flags)
                mux.writeSampleData(if (takeV) vt else at, ByteBuffer.wrap(s.data), info)
            }
            mux.stop()
        } finally { runCatching { mux.release() } }
        if (!out.isFile || out.length() < 1000) error("the clip came out empty")
    }

    /** The song's sound from [startSecs] for [secs]: 16-bit samples, interleaved, with their rate and channels (one or
     *  two; 48 kHz or less: a hi-res file is brought down by whole steps, 96 to 48, 176.4 to 44.1). */
    private fun decode(path: String, startSecs: Double, secs: Double): Triple<ShortArray, Int, Int> {
        val ex = MediaExtractor()
        try {
            if (path.startsWith("content:") || path.startsWith("file:")) ex.setDataSource(Music.ctx, android.net.Uri.parse(path), null) else ex.setDataSource(path)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("there is no sound in the song's file")
            ex.selectTrack(track)
            val inFmt = ex.getTrackFormat(track)
            val startUs = (startSecs * 1_000_000).toLong(); val endUs = startUs + (secs * 1_000_000).toLong()
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(inFmt, null, null, 0); codec.start()
                var rate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var enc = AudioFormat.ENCODING_PCM_16BIT
                val info = MediaCodec.BufferInfo(); var inDone = false
                var out = ShortArray(0); var n = 0          // what is kept, at the song's own rate and channels
                var idle = 0
                while (true) {
                    if (!inDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val got = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (got < 0 || ex.sampleTime > endUs + 500_000) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { codec.queueInputBuffer(i, 0, got, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        enc = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        if (out.isEmpty()) out = ShortArray(((secs + 1) * rate * ch).toInt())
                    } else if (o >= 0) {
                        idle = 0
                        if (out.isEmpty()) out = ShortArray(((secs + 1) * rate * ch).toInt())
                        val bb = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                        bb.position(info.offset); bb.limit(info.offset + info.size)
                        val bytes = when (enc) { AudioFormat.ENCODING_PCM_FLOAT -> 4; AudioFormat.ENCODING_PCM_8BIT -> 1
                            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3; AudioFormat.ENCODING_PCM_32BIT -> 4; else -> 2 }
                        val frames = info.size / (bytes * ch)
                        // the frames of this block that fall inside the clip
                        val firstUs = info.presentationTimeUs
                        val skip = if (firstUs < startUs) ((startUs - firstUs) * rate / 1_000_000).toInt().coerceAtMost(frames) else 0
                        val room = (((endUs - startUs) * rate / 1_000_000).toInt() - n / ch).coerceAtLeast(0)
                        val take = (frames - skip).coerceIn(0, room)
                        if (take > 0 && n + take * ch <= out.size) {
                            val base = info.offset + skip * bytes * ch
                            for (k in 0 until take * ch) {
                                val p = base + k * bytes
                                out[n++] = when (enc) {
                                    AudioFormat.ENCODING_PCM_FLOAT -> (bb.getFloat(p).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                    AudioFormat.ENCODING_PCM_8BIT -> (((bb.get(p).toInt() and 0xFF) - 128) shl 8).toShort()
                                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> (((bb.get(p + 2).toInt() shl 8) or (bb.get(p + 1).toInt() and 0xFF))).toShort()
                                    AudioFormat.ENCODING_PCM_32BIT -> (bb.getInt(p) shr 16).toShort()
                                    else -> bb.getShort(p)
                                }
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || room == 0) break
                    } else if (inDone && ++idle > 300) break   // nothing more is coming
                }
                if (n < rate * ch / 2) error("the song's sound couldn't be read there")
                return bringDown(out, n, rate, ch)
            } finally { runCatching { codec.stop() }; codec.release() }
        } finally { ex.release() }
    }

    /** Two channels at most, 48 kHz at most. */
    private fun bringDown(src: ShortArray, n: Int, rate: Int, ch: Int): Triple<ShortArray, Int, Int> {
        val step = when { rate > 150_000 -> 4; rate > 50_000 -> 2; else -> 1 }
        val outCh = minOf(ch, 2)
        if (step == 1 && outCh == ch) return Triple(src.copyOf(n), rate, ch)
        val frames = n / ch / step
        val out = ShortArray(frames * outCh)
        for (f in 0 until frames) for (c in 0 until outCh) {
            var sum = 0
            for (s in 0 until step) sum += src[(f * step + s) * ch + c]   // the steps averaged: a plain, gentle filter
            out[f * outCh + c] = (sum / step).toShort()
        }
        return Triple(out, rate / step, outCh)
    }

    private fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo, into: ArrayList<Sample>, wait: Long, time: ((Int) -> Long)? = null): Pair<MediaFormat?, Boolean> {
        var format: MediaFormat? = null
        while (true) {
            val o = codec.dequeueOutputBuffer(info, wait)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { format = codec.outputFormat; continue }
            if (o < 0) return format to false
            val end = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                val bb = codec.getOutputBuffer(o)!!
                val data = ByteArray(info.size); bb.position(info.offset); bb.get(data)
                into += Sample(data, time?.invoke(into.size) ?: info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
            }
            codec.releaseOutputBuffer(o, false)
            if (end) return format to true
        }
    }

    private fun encodeAudio(pcm: ShortArray, rate: Int, ch: Int): Coded {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch)
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, if (ch == 1) 128_000 else 192_000)
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            val out = ArrayList<Sample>(); val info = MediaCodec.BufferInfo()
            var format: MediaFormat? = null; var at = 0; var done = false; var sent = false; var idle = 0
            while (!done) {
                if (!sent) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val bb = codec.getInputBuffer(i)!!.order(ByteOrder.nativeOrder()); bb.clear()
                        val take = minOf((bb.remaining() / 2) / ch * ch, pcm.size - at, 4096 * ch)
                        val us = at.toLong() / ch * 1_000_000 / rate
                        if (take <= 0) { codec.queueInputBuffer(i, 0, 0, us, MediaCodec.BUFFER_FLAG_END_OF_STREAM); sent = true }
                        else { bb.asShortBuffer().put(pcm, at, take); codec.queueInputBuffer(i, 0, take * 2, us, 0); at += take }
                    }
                }
                val (f, end) = drain(codec, info, out, 5_000)
                if (f != null) format = f
                done = end
                if (sent && !end && ++idle > 400) break
            }
            return Coded(format ?: error("the phone couldn't make the clip's sound"), out)
        } finally { runCatching { codec.stop() }; codec.release() }
    }

    /** The card as video: every picture is the card, with the bar filled a little further. The card is turned into the
     *  video's own kind of picture (brightness and two colour planes) once; each picture after only fills the bar on. */
    private fun encodeVideo(card: Bitmap, bar: ClipRules.Bar?, secs: Double): Coded {
        val w = ClipRules.WIDTH; val h = ClipRules.height(card.width, card.height)
        val small = Bitmap.createScaledBitmap(if (card.config == Bitmap.Config.HARDWARE) card.copy(Bitmap.Config.ARGB_8888, false) else card, w, h, true)
        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
        val cw = w / 2; val chh = h / 2
        val y = ByteArray(w * h); val u = ByteArray(cw * chh); val v = ByteArray(cw * chh)
        fun yOf(r: Int, g: Int, b: Int) = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        fun uOf(r: Int, g: Int, b: Int) = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
        fun vOf(r: Int, g: Int, b: Int) = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
        for (j in 0 until h) for (i in 0 until w) {
            val p = px[j * w + i]; val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            y[j * w + i] = yOf(r, g, b).coerceIn(0, 255).toByte()
            if (j % 2 == 0 && i % 2 == 0 && j / 2 < chh && i / 2 < cw) {
                u[j / 2 * cw + i / 2] = uOf(r, g, b).coerceIn(0, 255).toByte(); v[j / 2 * cw + i / 2] = vOf(r, g, b).coerceIn(0, 255).toByte()
            }
        }
        val b = bar?.scaled(card.width, w)?.takeIf { it.x + it.w <= w && it.y + it.h <= h }
        val br = ((b?.rgb ?: 0) shr 16) and 0xFF; val bg = ((b?.rgb ?: 0) shr 8) and 0xFF; val bb = (b?.rgb ?: 0) and 0xFF
        val barY = yOf(br, bg, bb).toByte(); val barU = uOf(br, bg, bb).toByte(); val barV = vOf(br, bg, bb).toByte()

        val fps = ClipRules.FPS
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            val frames = (secs * fps).toInt().coerceAtLeast(1)
            val out = ArrayList<Sample>(); val info = MediaCodec.BufferInfo()
            var format: MediaFormat? = null; var sent = 0; var done = false; var filled = 0; var idle = 0
            val time: (Int) -> Long = { it * 1_000_000L / fps }   // each picture's own time, by its place
            while (!done) {
                if (sent <= frames) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        if (sent == frames) { codec.queueInputBuffer(i, 0, 0, time(sent), MediaCodec.BUFFER_FLAG_END_OF_STREAM); sent++ }
                        else {
                            if (b != null) {   // the bar filled on to where this picture is
                                val to = ClipRules.filled((sent + 1).toDouble() / fps, secs, b.w)
                                for (row in b.y until b.y + b.h) for (col in b.x + filled until b.x + to) y[row * w + col] = barY
                                for (row in b.y / 2 until (b.y + b.h) / 2) for (col in (b.x + filled) / 2 until (b.x + to) / 2) { u[row * cw + col] = barU; v[row * cw + col] = barV }
                                filled = to
                            }
                            val img = codec.getInputImage(i) ?: error("the phone's video coder can't take pictures this way")
                            put(img.planes[0], y, w, h); put(img.planes[1], u, cw, chh); put(img.planes[2], v, cw, chh)
                            codec.queueInputBuffer(i, 0, w * h * 3 / 2, time(sent), 0); sent++
                        }
                    }
                }
                val (f, end) = drain(codec, info, out, 5_000, time)
                if (f != null) format = f
                done = end
                if (sent > frames && !end && ++idle > 600) break
            }
            if (out.isEmpty()) error("the phone couldn't make the clip's picture")
            return Coded(format ?: error("the phone couldn't make the clip's picture"), out)
        } finally { runCatching { codec.stop() }; codec.release() }
    }

    /** One plane of a picture into the coder's own, which may keep its rows wider and its values a step apart. */
    private fun put(plane: android.media.Image.Plane, src: ByteArray, w: Int, h: Int) {
        val buf = plane.buffer; val rowStride = plane.rowStride; val step = plane.pixelStride
        if (step == 1) { for (row in 0 until h) { buf.position(row * rowStride); buf.put(src, row * w, w) }; return }
        for (row in 0 until h) { val at = row * rowStride; val from = row * w
            for (col in 0 until w) buf.put(at + col * step, src[from + col]) }
    }
}
