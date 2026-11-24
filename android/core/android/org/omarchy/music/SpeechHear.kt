package org.omarchy.music

/** The phone's side of auto-sync's listening ([LyricHearing]): the model fetched and unpacked, and a stretch of a song
 *  heard, with each piece of a word and when it's heard. Loaded once and kept while auto-sync runs, let go after. */
object SpeechHear {
    @Volatile private var rec: com.k2fsa.sherpa.onnx.OfflineRecognizer? = null

    /** (blocking) Fetch the model and unpack it (only into its own folder); [stop] ends the download. */
    fun get(stop: () -> Boolean, progress: (String) -> Unit) {
        val home = LyricHearing.home(Music.ctx).apply { mkdirs() }
        val zip = java.io.File(home, "model.tar.bz2")
        LyricHearing.fetch(zip, stop, progress)
        progress("Unpacking the listening model… (a minute or two)")
        LyricHearing.modelDir(Music.ctx).deleteRecursively()
        try {
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(zip.inputStream().buffered(1 shl 16))).use { tar ->
                val root = home.canonicalPath + java.io.File.separator
                while (true) {
                    val e = tar.nextEntry ?: break
                    val f = java.io.File(home, e.name)
                    if (!f.canonicalPath.startsWith(root)) continue   // nothing outside its own folder
                    if (e.isDirectory) { f.mkdirs(); continue }
                    f.parentFile?.mkdirs()
                    f.outputStream().use { tar.copyTo(it) }
                }
            }
        } finally { zip.delete() }
        if (!LyricHearing.ready(Music.ctx)) error("the listening model didn't unpack")
    }

    private fun recognizer(): com.k2fsa.sherpa.onnx.OfflineRecognizer {
        rec?.let { return it }
        fun p(name: String) = java.io.File(LyricHearing.modelDir(Music.ctx), name).path
        val model = com.k2fsa.sherpa.onnx.OfflineModelConfig(
            transducer = com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig(encoder = p(LyricHearing.ENCODER),
                decoder = p(LyricHearing.DECODER), joiner = p(LyricHearing.JOINER)),
            tokens = p(LyricHearing.TOKENS), numThreads = 2, modelType = LyricHearing.TYPE, provider = "cpu")
        return com.k2fsa.sherpa.onnx.OfflineRecognizer(null, com.k2fsa.sherpa.onnx.OfflineRecognizerConfig(modelConfig = model,
            decodingMethod = "greedy_search")).also { rec = it }
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
