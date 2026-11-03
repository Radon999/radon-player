package org.omarchy.music

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** The DJ's voice on the phone: ElevenLabs first when you've pasted your own API key (DjVoices; it spends your
 *  ElevenLabs credits), then the PC's natural voice, Kokoro (Kokoro-82M, Apache-2.0, the same "af_heart" voice), run
 *  on the phone by sherpa-onnx once you've fetched it (about 130 MB, once), else Android's text-to-speech with the most
 *  natural English voice installed. */
object Voice {
    /** Why the natural voice last failed ("" when it last worked). */
    var lastError by androidx.compose.runtime.mutableStateOf("")

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val waiting = ArrayList<Pair<String, () -> Unit>>()
    private val done = HashMap<String, () -> Unit>()
    private var n = 0

    val canSpeak get() = true

    // ---- Kokoro, through sherpa-onnx (Apache-2.0): the voice the PC uses ----
    private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"
    private const val MODEL_DIR = "kokoro-int8-multi-lang-v1_0"
    private val dir get() = java.io.File(Music.ctx.filesDir, "dj-voice-kokoro")
    private val modelDir get() = java.io.File(dir, MODEL_DIR)
    private fun onnx(): java.io.File? = modelDir.listFiles()?.filter { it.isFile && it.name.endsWith(".onnx") }?.maxByOrNull { it.length() }
    private fun modelReady() = onnx() != null && java.io.File(modelDir, "voices.bin").isFile && java.io.File(modelDir, "tokens.txt").isFile
    private var naturalOk by androidx.compose.runtime.mutableStateOf(false)

    /** The natural voice isn't on the phone yet (it can be fetched). */
    val needsNatural get() = !naturalOk && !modelReady()
    val hasNatural get() = naturalOk || modelReady()
    const val naturalSize = "about 130 MB"

    /** The phone: which voice the DJ uses once the natural one is here (on, the default: the natural one; off: the
     *  phone's own Google voice). */
    const val USE_NATURAL = "dj_use_natural"
    private var naturalOn by androidx.compose.runtime.mutableStateOf(Prefs.on(USE_NATURAL, true))
    val useNatural: Boolean get() = naturalOn
    fun setUseNatural(on: Boolean) { Prefs.set(USE_NATURAL, on); naturalOn = on }
    /** Can choose between the two (the natural voice is on the phone). */
    val canChoose get() = hasNatural

    /** Deletes the natural voice (its own folder only), back to the phone's voice. */
    fun removeNatural() {
        runCatching { kokoro?.release() }; kokoro = null
        dir.deleteRecursively(); naturalOk = false; lexWords = null
    }

    /** (blocking) Fetch the natural voice (the PC's Kokoro voice, made smaller for phones) and unpack it. */
    fun getNatural(progress: (String) -> Unit) {
        dir.mkdirs()
        val zip = java.io.File(dir, "kokoro.tar.bz2")
        val c = java.net.URL(MODEL_URL).openConnection() as java.net.HttpURLConnection
        c.instanceFollowRedirects = true; c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "RadonPlayer")
        if (c.responseCode !in 200..299) error("the download failed (${c.responseCode})")
        val total = c.contentLengthLong
        c.inputStream.use { ins -> zip.outputStream().use { out ->
            val buf = ByteArray(1 shl 16); var got = 0L; var shown = -1L
            while (true) {
                val k = ins.read(buf); if (k < 0) break
                out.write(buf, 0, k); got += k
                val pct = if (total > 0) got * 100 / total else got / 5_000_000
                if (pct != shown) { shown = pct; progress(if (total > 0) "Getting the voice… $pct%" else "Getting the voice… ${got / 1_000_000} MB") }
            }
        } }
        progress("Unpacking the voice… (a minute or two)")
        modelDir.deleteRecursively()
        try {
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
                org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(zip.inputStream().buffered(1 shl 16))).use { tar ->
                val root = dir.canonicalPath + java.io.File.separator
                while (true) {
                    val e = tar.nextEntry ?: break
                    val f = java.io.File(dir, e.name)
                    if (!f.canonicalPath.startsWith(root)) continue   // nothing outside the voice's own folder
                    if (e.isDirectory) { f.mkdirs(); continue }
                    f.parentFile?.mkdirs()
                    f.outputStream().use { tar.copyTo(it) }
                }
            }
        } finally { zip.delete() }
        if (!modelReady()) error("The voice didn't unpack")
        naturalOk = true
    }

    @Volatile private var kokoro: com.k2fsa.sherpa.onnx.OfflineTts? = null
    @Volatile private var track: android.media.AudioTrack? = null

    /** Kokoro's English dictionary's words (lowercase), read once: a word not in it is respelt ([Pronounce]). */
    @Volatile private var lexWords: Set<String>? = null
    private fun lexicon(): Set<String> = lexWords ?: runCatching {
        java.io.File(modelDir, "lexicon-us-en.txt").useLines { lines ->
            lines.mapNotNull { l -> l.trim().split(Regex("\\s+"), limit = 2).firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() } }.toHashSet() }
    }.getOrDefault(emptySet()).also { if (it.isNotEmpty()) lexWords = it }

    private fun engine(): com.k2fsa.sherpa.onnx.OfflineTts {
        kokoro?.let { return it }
        val m = modelDir
        val lex = listOf("lexicon-us-en.txt", "lexicon-zh.txt").map { java.io.File(m, it) }.filter { it.isFile }.joinToString(",") { it.path }
        val cfg = com.k2fsa.sherpa.onnx.OfflineTtsConfig(model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(
            kokoro = com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig(model = onnx()!!.path, voices = java.io.File(m, "voices.bin").path,
                tokens = java.io.File(m, "tokens.txt").path, dataDir = java.io.File(m, "espeak-ng-data").path, lexicon = lex,
                dictDir = java.io.File(m, "dict").path, lengthScale = 0.92f),
            numThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4), provider = "cpu"))
        return com.k2fsa.sherpa.onnx.OfflineTts(null, cfg).also { kokoro = it }
    }

    /** (blocking) text said by Kokoro; false if it couldn't (or was hushed). */
    private fun sayNatural(text: String, mine: Long): Boolean = runCatching {
        val plain = java.text.Normalizer.normalize(Pronounce.sayable(text, lexicon()), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ").trim()
        if (plain.isEmpty()) return@runCatching false
        val audio = engine().generate(plain, DjVoices.kokoroVoice.sid, 1.0f)
        if (turn != mine) return@runCatching true
        val rate = audio.sampleRate; val samples = audio.samples
        if (samples.isEmpty()) error("it made no sound")
        val t = android.media.AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(rate).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(android.media.AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(android.media.AudioTrack.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_FLOAT), rate * 4 / 4)).build()
        track = t
        try {
            t.play()
            var i = 0
            while (i < samples.size && turn == mine) {
                val k = minOf(4096, samples.size - i)
                t.write(samples, i, k, android.media.AudioTrack.WRITE_BLOCKING); i += k
            }
            // What's still in its buffer, then done
            if (turn == mine) Thread.sleep(((android.media.AudioTrack.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_FLOAT) / 4) * 1000L / rate).coerceIn(50, 600))
        } finally { runCatching { t.stop(); t.release() }; if (track === t) track = null }
        lastError = ""
        true
    }.getOrElse { e ->
        lastError = "${e.javaClass.simpleName}: ${e.message ?: ""}".take(300)
        false
    }

    // ---- ElevenLabs ----
    @Volatile private var turn = 0L
    @Volatile private var player: android.media.MediaPlayer? = null
    @Volatile private var elevenToldAt = 0.0

    /** (on a background thread) text said by ElevenLabs; else (it couldn't) by the phone's own voice. */
    private fun sayEleven(text: String, mine: Long, onDone: () -> Unit) {
        Thread {
            val mp3 = runCatching { DjVoices.eleven(text, "mp3_44100_128") }.onFailure { e ->
                if (now() - elevenToldAt > 600) { elevenToldAt = now(); Music.toast("ElevenLabs voice: ${e.message?.take(80)}. Using the phone's voice.") }
            }.getOrNull()
            Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                if (turn != mine) return@launch   // hushed (or another line began) while it came: nothing to say
                if (mp3 == null) { sayTts(text, onDone); return@launch }
                val ok = runCatching {
                    val f = java.io.File(Music.ctx.cacheDir, "dj-line.mp3")
                    f.writeBytes(mp3)
                    val mp = android.media.MediaPlayer()
                    mp.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    mp.setDataSource(f.path)
                    var finished = false
                    val end = { if (!finished) { finished = true; runCatching { mp.release() }; if (player === mp) player = null; onDone() } }
                    mp.setOnCompletionListener { end() }
                    mp.setOnErrorListener { _, _, _ -> end(); true }
                    mp.prepare()
                    player = mp
                    elevenEnd = end
                    mp.start()
                }.isSuccess
                if (!ok) sayTts(text, onDone)
            }
        }.apply { isDaemon = true }.start()
    }
    @Volatile private var elevenEnd: (() -> Unit)? = null

    private fun start() {
        if (tts != null) return
        tts = TextToSpeech(Music.ctx) { status ->
            ready = status == TextToSpeech.SUCCESS
            val t = tts ?: return@TextToSpeech
            if (ready) {
                runCatching {
                    // The most natural English voice: Google's neural ("network") voices sound like a person, so they come
                    // first when there's internet (a female one before others: it sounds most like a person); else the best one on the phone.
                    val online = runCatching {
                        val cm = Music.ctx.getSystemService(android.net.ConnectivityManager::class.java)
                        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    }.getOrDefault(false)
                    val female = listOf("sfg", "tpc", "iob", "iog", "tpf", "female")
                    val best = t.voices?.filter { it.locale.language == "en" && "notInstalled" !in (it.features ?: emptySet()) &&
                            (online || !it.isNetworkConnectionRequired) }
                        ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality >= android.speech.tts.Voice.QUALITY_HIGH }
                            .thenByDescending { it.isNetworkConnectionRequired }   // the neural ones
                            .thenByDescending { v -> female.any { it in v.name.lowercase() } }
                            .thenByDescending { it.locale.country == "US" }
                            .thenByDescending { it.quality })
                        ?.firstOrNull()
                    if (best != null) t.voice = best else t.language = Locale.US
                }
                t.setSpeechRate(1.0f); t.setPitch(0.97f)
            }
            val w = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
            if (ready) w.forEach { (s, d) -> sayTts(s, d) } else w.forEach { it.second() }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { finish(id) }
            @Deprecated("old API") override fun onError(id: String?) { finish(id) }
            override fun onError(id: String?, code: Int) { finish(id) }
            override fun onStop(id: String?, interrupted: Boolean) { finish(id) }
        })
    }

    private fun finish(id: String?) {
        val d = synchronized(done) { done.remove(id ?: "") } ?: return
        Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { d() }
    }

    /** Say text out loud; onDone once it's said (or couldn't be). */
    fun say(text: String, onDone: () -> Unit = {}) {
        val mine = ++turn
        player?.let { mp -> runCatching { mp.stop() }; elevenEnd?.let { e -> elevenEnd = null; e() } }   // one line at a time
        runCatching { track?.pause(); track?.flush() }
        if (DjVoices.elevenOn) { sayEleven(text, mine, onDone); return }
        if (hasNatural && useNatural) {   // the PC's voice, here too; the phone's own if it can't
            Thread {
                val said = sayNatural(text, mine)
                Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    if (turn != mine) { onDone(); return@launch }
                    if (said) onDone() else sayTts(text, onDone)
                }
            }.apply { isDaemon = true }.start()
            return
        }
        sayTts(text, onDone)
    }

    /** text said by the phone's own text-to-speech. */
    private fun sayTts(text: String, onDone: () -> Unit) {
        start()
        if (!ready) { synchronized(waiting) { waiting += text to onDone }; return }
        if (tts == null) { onDone(); return }
        val id = "dj${n++}"
        synchronized(done) { done[id] = onDone }
        val p = android.os.Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC) }
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, p, id) != TextToSpeech.SUCCESS) finish(id)
    }

    /** Stop talking now. */
    fun hush() {
        turn++
        runCatching { track?.pause(); track?.flush() }
        player?.let { mp -> runCatching { mp.stop() }; elevenEnd?.let { e -> elevenEnd = null; e() } }
        runCatching { tts?.stop() }
        val all = synchronized(done) { done.values.toList().also { done.clear() } }
        all.forEach { Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { it() } }
    }
}
