package org.omarchy.music

import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** The DJ's voice and voice typing on the PC (Windows and Linux). The DJ speaks with, in order:
 *  1. ElevenLabs, when it's on (DjVoices; it's off),
 *  2. Pocket TTS (Kyutai's, CC BY 4.0), a natural voice that runs on the PC inside the app, through sherpa-onnx's
 *     library: downloaded once when you ask for it (DjVoices.POCKET_URL), the same voice as on the phone,
 *  3. Windows' own speech (System.Speech, through PowerShell), on Windows.
 *  Each falls back to the next when it can't. Lines are made ahead ([prepare]) so the DJ talks the moment a set begins.
 *  Dictation for the search bars is Windows' own.
 *  (Before 3.3 the natural voice was Kokoro, run by a separate program started for every line, on Windows only.) */
object Voice {
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    /** Bumped by every say and hush: a voice still going from an older turn stops and says nothing more. */
    @Volatile private var turn = 0L
    @Volatile private var proc: Process? = null
    @Volatile private var line: javax.sound.sampled.SourceDataLine? = null
    @Volatile private var onDone: (() -> Unit)? = null

    private val dir get() = java.io.File(Music.ctx.filesDir, "dj-voice-pocket")
    private val modelDir get() = java.io.File(dir, DjVoices.POCKET_DIR)

    /** The natural voice is on this PC. */
    val hasNatural get() = DjVoices.pocketReady(modelDir)
    /** The natural voice isn't on this PC yet (it can be fetched). */
    val needsNatural get() = !hasNatural
    val canSpeak get() = hasNatural || windows || DjVoices.elevenOn

    /** The PC always uses the natural voice once it's here (the choice is the phone's). */
    val useNatural get() = true
    fun setUseNatural(on: Boolean) {}
    val canChoose get() = false
    fun removeNatural() {}

    /** About how much the natural voice downloads. */
    const val naturalSize = DjVoices.POCKET_SIZE

    // ---- the voices before this one: removed once Pocket TTS is in, only the app's own folders ----
    private val piperDir get() = java.io.File(Music.ctx.filesDir, "dj-voice")

    /** Deletes the Piper program and Ryan voice the app downloaded before (its own "dj-voice" folder: piper/, the
     *  en_US-ryan-high model, a leftover piper.zip), and the folder when that leaves it empty. Nothing else. */
    private fun removePiper() = runCatching {
        val d = piperDir
        if (!d.isDirectory) return@runCatching
        java.io.File(d, "piper").takeIf { it.isDirectory }?.deleteRecursively()
        for (n in listOf("en_US-ryan-high.onnx", "en_US-ryan-high.onnx.json", "piper.zip")) {
            java.io.File(d, n).delete(); java.io.File(d, "$n.part").delete()
        }
        if (d.list()?.isEmpty() == true) d.delete()
    }

    /** Deletes Kokoro (3.0 to 3.2's natural voice: its program and its model, about 400 MB), the app's own
     *  "dj-voice-kokoro" folder and nothing else. */
    private fun removeKokoro() = runCatching {
        java.io.File(Music.ctx.filesDir, "dj-voice-kokoro").takeIf { it.isDirectory }?.deleteRecursively()
    }

    private fun fetch(url: String, to: java.io.File, label: String, progress: (String) -> Unit) {
        val c = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "RadonPlayer")
        if (c.responseCode !in 200..299) error("$label: the download failed (${c.responseCode})")
        val total = c.contentLengthLong
        val part = java.io.File(to.path + ".part")
        c.inputStream.use { ins -> part.outputStream().use { out ->
            val buf = ByteArray(1 shl 16); var done = 0L; var shown = -1L
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                out.write(buf, 0, n); done += n
                val pct = if (total > 0) done * 100 / total else -1
                val mb = done / 5_000_000   // without a size: every 5 MB
                val step = if (pct >= 0) pct else mb
                if (step != shown) { shown = step; progress(if (pct >= 0) "$label… $pct%" else "$label… ${done / 1_000_000} MB") }
            }
        } }
        to.delete(); if (!part.renameTo(to)) error("$label: couldn't save it")
    }

    /** Unpacks a .tar.bz2 into to, with the PC's own tar (in Windows 10 and 11, and every Linux). */
    private fun untar(archive: java.io.File, to: java.io.File) {
        to.mkdirs()
        val tar = System.getenv("SystemRoot")?.let { java.io.File(it, "System32\\tar.exe") }?.takeIf { it.isFile }?.path ?: "tar"
        val p = ProcessBuilder(tar, "-xf", archive.path, "-C", to.path).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) error("Couldn't unpack it: ${out.trim().take(120)}")
    }

    /** (blocking) Fetch the natural voice (Pocket TTS). progress: what it's doing. Then the voices from before
     *  (Kokoro, Piper) are deleted. */
    fun getNatural(progress: (String) -> Unit) {
        dir.mkdirs()
        if (!hasNatural) {
            val zip = java.io.File(dir, "pocket.tar.bz2")
            fetch(DjVoices.POCKET_URL, zip, "Getting the voice", progress)
            progress("Unpacking the voice…")
            modelDir.deleteRecursively()
            try { untar(zip, dir) } finally { zip.delete() }
        }
        if (!hasNatural) error("The voice didn't install")
        removePiper(); removeKokoro()
    }

    // ---- Pocket TTS, inside the app ----
    private val engineLock = Any()
    private var tts: com.k2fsa.sherpa.onnx.OfflineTts? = null
    private var ttsFrom: String? = null
    private var like: Pair<FloatArray, Int>? = null   // the recording the voice is modelled on (samples, rate)

    /** (blocking; one line at a time) text as sound, 24 kHz samples and their rate, made by the voice in from (its
     *  model's folder); null for no text or once go says to stop. The voice is loaded the first time and kept. */
    fun synth(text: String, from: java.io.File = modelDir, go: () -> Boolean = { true }): Pair<FloatArray, Int>? {
        val plain = DjVoices.plain(text)
        if (plain.isEmpty()) return null
        synchronized(engineLock) {
            if (!go()) return null
            if (tts == null || ttsFrom != from.path) {
                runCatching { tts?.release() }
                fun p(name: String) = java.io.File(from, name).path
                val pocket = com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig.builder()
                    .setLmMain(p(DjVoices.POCKET_LM_MAIN)).setLmFlow(p(DjVoices.POCKET_LM_FLOW))
                    .setEncoder(p(DjVoices.POCKET_ENCODER)).setDecoder(p(DjVoices.POCKET_DECODER))
                    .setTextConditioner(p(DjVoices.POCKET_TEXT))
                    .setVocabJson(p(DjVoices.POCKET_VOCAB)).setTokenScoresJson(p(DjVoices.POCKET_SCORES)).build()
                // Two threads: on a four-core processor it made speech twice as fast as with four (docs/rebuild/TTS_ENGINES.md)
                val model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig.builder().setPocket(pocket).setNumThreads(2).build()
                tts = com.k2fsa.sherpa.onnx.OfflineTts(com.k2fsa.sherpa.onnx.OfflineTtsConfig.builder().setModel(model).build())
                ttsFrom = from.path
                like = wavSamples(java.io.File(from, DjVoices.POCKET_VOICE).readBytes()) ?: error("The voice's recording couldn't be read")
            }
            val ref = like ?: error("The voice has no recording to go by")
            val how = com.k2fsa.sherpa.onnx.GenerationConfig()
            how.setReferenceAudio(ref.first); how.setReferenceSampleRate(ref.second); how.setNumSteps(DjVoices.POCKET_STEPS)
            val audio = tts!!.generateWithConfigAndCallback(plain, how, com.k2fsa.sherpa.onnx.OfflineTtsCallback { if (go()) 1 else 0 })
            val samples = audio.samples
            if (!go()) return null
            if (samples == null || samples.isEmpty()) error("it made no sound")
            return samples to audio.sampleRate
        }
    }

    // ---- lines made ahead (the next set's, while this one plays), so the DJ talks straight away ----
    private val ahead = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Pair<FloatArray, Int>?>>()
    private val maker by lazy { java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "dj-voice").apply { isDaemon = true } } }

    /** Make a line the DJ is about to say now, in the background: when its turn comes it's said at once. The last
     *  eight are kept. (A line said now doesn't wait behind these: only for the one being made.) */
    fun prepare(text: String) {
        if (text.isBlank() || !hasNatural || DjVoices.elevenOn || ahead.containsKey(text)) return
        if (ahead.size >= 8) ahead.keys.firstOrNull()?.let { ahead.remove(it) }
        val f = java.util.concurrent.CompletableFuture<Pair<FloatArray, Int>?>()
        ahead[text] = f
        maker.execute { f.complete(if (f.isDone) null else runCatching { synth(text) }.getOrNull()) }
    }

    // ---- saying it ----

    private fun main(f: () -> Unit) { Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { f() } }

    private fun finish(mine: Long) {
        if (turn != mine) return
        onDone?.let { d -> onDone = null; main(d) }
    }

    /** Plays 16-bit mono PCM at rate; false if stopped (hush) or it couldn't. */
    private fun playPcm(pcm: ByteArray, rate: Float, mine: Long): Boolean {
        val fmt = javax.sound.sampled.AudioFormat(rate, 16, 1, true, false)
        val l = javax.sound.sampled.AudioSystem.getSourceDataLine(fmt)
        l.open(fmt); line = l
        try {
            l.start()
            var i = 0
            while (i < pcm.size && turn == mine) {
                val n = minOf(8192, pcm.size - i)
                l.write(pcm, i, n); i += n
            }
            if (turn == mine) l.drain()
            return turn == mine
        } finally { runCatching { l.stop(); l.close() }; line = null }
    }

    /** (blocking) text said by ElevenLabs; false if it couldn't. */
    private fun sayEleven(text: String, mine: Long, onStart: () -> Unit): Boolean = runCatching {
        val pcm = DjVoices.eleven(text, "pcm_24000")
        if (turn != mine) return@runCatching true   // hushed while it came: said nothing, and that's fine
        main(onStart)
        playPcm(pcm, 24000f, mine)
        true
    }.getOrElse { e ->
        // Not the key, only what went wrong, and only once in a while (a toast for each line would be a lot)
        if (now() - elevenToldAt > 600) { elevenToldAt = now(); val why = e.message?.take(80); main { Music.toast("ElevenLabs voice: $why. Using the voice on this PC.") } }
        false
    }
    @Volatile private var elevenToldAt = 0.0

    /** The natural voice didn't speak: say so once per run, so it isn't silently Windows' voice (or silence). */
    @Volatile private var toldFail = false
    /** Why the natural voice last failed ("" when it last worked): shown in the DJ's Voice settings, so it can be read. */
    var lastError by androidx.compose.runtime.mutableStateOf("")
    private fun naturalFailed(why: String) {
        val last = why.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty().take(300)
        lastError = last.ifEmpty { "it stopped without saying why" }
        if (toldFail) return
        toldFail = true
        Music.toast("The natural voice couldn't speak" + (if (windows) ", so Windows' voice is used" else "") + if (last.isNotEmpty()) ": $last" else "")
    }

    /** (blocking) text said by Pocket TTS: the line made ahead if there is one, else made now. onStart runs when the
     *  sound begins (not when the line starts being made: the music is only turned down once there's a voice to
     *  hear). False if it couldn't; true if it was said or hushed. */
    private fun sayNatural(text: String, mine: Long, onStart: () -> Unit): Boolean = runCatching {
        val made = ahead.remove(text)?.let { f -> runCatching { f.get(60, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull() }
            ?: synth(text, go = { turn == mine })
        if (turn != mine) return@runCatching true
        val (samples, rate) = made ?: error("it made no sound")
        lastError = ""
        main(onStart)
        playPcm(pcm16(samples), rate.toFloat(), mine)
        true
    }.getOrElse { e ->
        if (turn == mine) naturalFailed("${e.javaClass.simpleName}: ${e.message ?: ""}")
        turn != mine
    }

    private fun ps(script: String): Process {
        val enc = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", enc).redirectErrorStream(true).start()
    }

    /** (blocking) text said by Windows' own voice. */
    private fun saySapi(text: String, mine: Long) {
        if (!windows || turn != mine) return
        val said = text.replace("'", "''")
        val p = runCatching {
            ps("Add-Type -AssemblyName System.Speech; \$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                // The best English voice installed: a newer female one (Aria, Jenny…) first, then any female voice
                "\$v = \$s.GetInstalledVoices() | ? { \$_.Enabled -and \$_.VoiceInfo.Culture.Name -like 'en*' } | " +
                "Sort-Object { if (\$_.VoiceInfo.Name -match 'Aria|Jenny|Natural') { 0 } elseif (\$_.VoiceInfo.Gender -eq 'Female') { 1 } else { 2 } } | Select-Object -First 1; " +
                "if (\$v) { \$s.SelectVoice(\$v.VoiceInfo.Name) }; \$s.Rate = 1; \$s.Speak('$said')")
        }.getOrNull() ?: return
        proc = p
        runCatching { p.inputStream.readBytes(); p.waitFor() }
        if (proc === p) proc = null
    }

    /** Say text out loud; onStart when its sound begins, done once it's said (or couldn't be, or was hushed). */
    fun say(text: String, onStart: () -> Unit = {}, done: () -> Unit = {}) {
        if (!canSpeak) { done(); return }
        hush()
        val mine = ++turn
        onDone = done
        Thread {
            if (hasNatural) { removePiper(); removeKokoro() }   // the voices from before, if they're still there
            val said = (DjVoices.elevenOn && sayEleven(text, mine, onStart)) ||
                (turn == mine && hasNatural && sayNatural(text, mine, onStart))
            if (!said && turn == mine && windows) { main(onStart); saySapi(text, mine) }
            finish(mine)
        }.apply { isDaemon = true }.start()
    }

    /** Stop talking now. */
    fun hush() {
        turn++
        val p = proc; proc = null
        runCatching { p?.destroyForcibly() }
        runCatching { line?.stop(); line?.flush() }
        onDone?.let { d -> onDone = null; main(d) }
    }

    /** (blocking) What you say into the microphone, as text (Windows' dictation); null if nothing was heard. */
    fun listen(): String? {
        if (!windows) return null
        val p = ps("[Console]::OutputEncoding = [Text.Encoding]::UTF8; try { Add-Type -AssemblyName System.Speech; " +
            "\$r = New-Object System.Speech.Recognition.SpeechRecognitionEngine; \$r.SetInputToDefaultAudioDevice(); " +
            "\$r.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar)); " +
            "\$r.InitialSilenceTimeout = [TimeSpan]::FromSeconds(6); \$r.EndSilenceTimeout = [TimeSpan]::FromSeconds(0.8); " +
            "\$res = \$r.Recognize([TimeSpan]::FromSeconds(12)); if (\$res) { 'TEXT:' + \$res.Text } } catch { 'ERR:' + \$_.Exception.Message }")
        val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText().also { p.waitFor() }.lines()
        out.firstOrNull { it.startsWith("ERR:") }?.let { error(it.removePrefix("ERR:").trim().ifEmpty { "Windows speech recognition isn't set up" }) }
        return out.firstOrNull { it.startsWith("TEXT:") }?.removePrefix("TEXT:")?.trim()?.ifEmpty { null }
    }
}
