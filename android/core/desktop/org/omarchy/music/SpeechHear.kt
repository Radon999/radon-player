package org.omarchy.music

/** The PC's side of auto-sync's listening ([LyricHearing]): the model fetched and unpacked (with the PC's own tar, as
 *  the DJ's voice), and a stretch of a song heard, with each piece of a word and when it's heard. Loaded once and kept
 *  while auto-sync runs, let go after. */
object SpeechHear {
    @Volatile private var rec: com.k2fsa.sherpa.onnx.OfflineRecognizer? = null

    /** (blocking) Fetch the model and unpack it (only into its own folder). */
    fun get(progress: (String) -> Unit) {
        val home = LyricHearing.home(Music.ctx).apply { mkdirs() }
        val zip = java.io.File(home, "model.tar.bz2")
        LyricHearing.fetch(zip, progress)
        progress("Unpacking the listening model…")
        LyricHearing.modelDir(Music.ctx).deleteRecursively()
        try {
            val tar = System.getenv("SystemRoot")?.let { java.io.File(it, "System32\\tar.exe") }?.takeIf { it.isFile }?.path ?: "tar"
            val p = ProcessBuilder(tar, "-xf", zip.path, "-C", home.path).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) error("couldn't unpack it: ${out.trim().take(120)}")
        } finally { zip.delete() }
        if (!LyricHearing.ready(Music.ctx)) error("the listening model didn't unpack")
    }

    private fun recognizer(): com.k2fsa.sherpa.onnx.OfflineRecognizer {
        rec?.let { return it }
        fun p(name: String) = java.io.File(LyricHearing.modelDir(Music.ctx), name).path
        val tr = com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig.builder().setEncoder(p(LyricHearing.ENCODER))
            .setDecoder(p(LyricHearing.DECODER)).setJoiner(p(LyricHearing.JOINER)).build()
        // Half the PC's cores (at least two): it hears a song in a few seconds and leaves the rest of the PC free
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 8)
        val model = com.k2fsa.sherpa.onnx.OfflineModelConfig.builder().setTransducer(tr).setTokens(p(LyricHearing.TOKENS))
            .setNumThreads(threads).setModelType(LyricHearing.TYPE).build()
        return com.k2fsa.sherpa.onnx.OfflineRecognizer(com.k2fsa.sherpa.onnx.OfflineRecognizerConfig.builder()
            .setOfflineModelConfig(model).setDecodingMethod("greedy_search").build()).also { rec = it }
    }

    /** (blocking) A stretch of sound ([LyricHearing.RATE], one channel): the pieces of words heard and when (seconds
     *  into the stretch). */
    fun hear(samples: FloatArray): Pair<List<String>, List<Double>> {
        val r = recognizer()
        val s = r.createStream()
        try {
            s.acceptWaveform(samples, LyricHearing.RATE)
            r.decode(s)
            val res = r.getResult(s)
            return res.tokens.toList() to res.timestamps.map { it.toDouble() }
        } finally { s.release() }
    }

    /** Let the model go (auto-sync done). */
    fun release() { runCatching { rec?.release() }; rec = null }
}
