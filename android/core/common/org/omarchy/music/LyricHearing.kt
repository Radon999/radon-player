package org.omarchy.music

/** The model auto-sync (LyricsAutoSync) listens with: NVIDIA's Parakeet TDT 110M, English speech recognition (CC BY 4.0),
 *  in sherpa-onnx's package of it (int8), run by sherpa-onnx as the DJ's voice is. Fetched once, when auto-sync is first
 *  started, into the app's own folder; it hears the words of a song and when each is sung. The same on the phone and
 *  the PC (SpeechHear, each side's). Tried on Code Monkey (Jonathan Coulton): nearly every word heard, the lines a mean
 *  0.34 s from LRCLIB's timing before the lead was set (LyricSyncRules.LEAD). */
object LyricHearing {
    const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8.tar.bz2"
    const val DIR = "sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8"
    const val SIZE = "about 100 MB"
    const val ENCODER = "encoder.int8.onnx"
    const val DECODER = "decoder.int8.onnx"
    const val JOINER = "joiner.int8.onnx"
    const val TOKENS = "tokens.txt"
    const val TYPE = "nemo_transducer"
    /** The rate it hears at. */
    const val RATE = 16000
    /** Songs longer than this aren't listened to (a long mix: too much to hold at once). */
    const val MAX_SECS = 15 * 60

    /** The app's own folder for it (inside it, [DIR] once unpacked). */
    fun home(ctx: Ctx): java.io.File = java.io.File(appCtx(ctx).filesDir, "lyric-hearing")
    fun modelDir(ctx: Ctx): java.io.File = java.io.File(home(ctx), DIR)
    fun ready(ctx: Ctx): Boolean = listOf(ENCODER, DECODER, JOINER, TOKENS).all { java.io.File(modelDir(ctx), it).isFile }

    /** (blocking) The archive fetched to [to], saying how far it's got; [stop] ends it (nothing kept). */
    fun fetch(to: java.io.File, stop: () -> Boolean, progress: (String) -> Unit) {
        val c = java.net.URI(URL).toURL().openConnection() as java.net.HttpURLConnection
        c.instanceFollowRedirects = true; c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "RadonPlayer")
        if (c.responseCode !in 200..299) error("the download failed (${c.responseCode})")
        val total = c.contentLengthLong
        val part = java.io.File(to.path + ".part")
        c.inputStream.use { ins -> part.outputStream().use { out ->
            val buf = ByteArray(1 shl 16); var got = 0L; var shown = -1L
            while (true) {
                if (stop()) { out.close(); part.delete(); error("stopped") }
                val k = ins.read(buf); if (k < 0) break
                out.write(buf, 0, k); got += k
                val step = if (total > 0) got * 100 / total else got / 5_000_000
                if (step != shown) { shown = step; progress(if (total > 0) "Getting the listening model… $step%" else "Getting the listening model… ${got / 1_000_000} MB") }
            }
        } }
        to.delete(); if (!part.renameTo(to)) error("couldn't save it")
    }
}
