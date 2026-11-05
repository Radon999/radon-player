package org.omarchy.music

import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** The DJ's voice and voice typing on the PC. The DJ speaks with, in order:
 *  1. ElevenLabs, when you've pasted your own API key (DjVoices; it spends your ElevenLabs credits),
 *  2. Kokoro, a free natural voice (Kokoro-82M, Apache-2.0) run on the PC by sherpa-onnx's offline TTS program,
 *     downloaded once when you ask for it,
 *  3. Windows' own speech (System.Speech, through PowerShell), always there.
 *  Each falls back to the next when it can't (offline, out of credits, not installed). Dictation for the search bars
 *  is Windows' own. Off Windows only ElevenLabs can speak, and there's no dictation. */
object Voice {
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    /** Bumped by every say and hush: a voice still going from an older turn stops and says nothing more. */
    @Volatile private var turn = 0L
    @Volatile private var proc: Process? = null
    @Volatile private var line: javax.sound.sampled.SourceDataLine? = null
    @Volatile private var clip: javax.sound.sampled.Clip? = null
    @Volatile private var onDone: (() -> Unit)? = null

    val canSpeak get() = windows || DjVoices.elevenOn

    // ---- Kokoro, through sherpa-onnx (Apache-2.0) ----
    // The program: sherpa-onnx's prebuilt Windows build (its newest release, found through GitHub's API; else the known
    // ones below). The voice: sherpa-onnx's package of Kokoro v1.0 (English voices, 24 kHz).
    private const val RELEASES = "https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/latest"
    private val PROGRAM_FALLBACK = listOf(
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-v2.0.1-win-x64-shared-MT-Release.tar.bz2",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-v2.0.1-win-x64-static-MT-Release.tar.bz2",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-v1.13.7-win-x64-shared-MT-Release.tar.bz2",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-v1.13.7-win-x64-shared.tar.bz2",
    )
    private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
    private const val MODEL_DIR = "kokoro-multi-lang-v1_0"
    private const val EXE = "sherpa-onnx-offline-tts.exe"

    private val dir get() = java.io.File(Music.ctx.filesDir, "dj-voice-kokoro")
    private val modelDir get() = java.io.File(dir, MODEL_DIR)
    private val programDir get() = java.io.File(dir, "program")
    @Volatile private var exeCache: java.io.File? = null

    /** The TTS program, wherever its archive put it (bin/ inside a folder named for its version). */
    private fun exe(): java.io.File? {
        exeCache?.takeIf { it.isFile }?.let { return it }
        if (!programDir.isDirectory) return null
        return programDir.walkTopDown().maxDepth(4).firstOrNull { it.isFile && it.name.equals(EXE, ignoreCase = true) }?.also { exeCache = it }
    }

    private fun modelReady() = java.io.File(modelDir, "model.onnx").isFile && java.io.File(modelDir, "voices.bin").isFile &&
        java.io.File(modelDir, "tokens.txt").isFile && java.io.File(modelDir, "espeak-ng-data").isDirectory

    /** The PC always uses the natural voice once it's here (the choice is the phone's). */
    val useNatural get() = true
    fun setUseNatural(on: Boolean) {}
    val canChoose get() = false
    fun removeNatural() {}

    /** The natural voice is on this PC. */
    val hasNatural get() = windows && exe() != null && modelReady()

    /** The natural voice isn't on this PC yet (it can be fetched). */
    val needsNatural get() = windows && !hasNatural

    /** About how much the natural voice downloads. */
    const val naturalSize = "about 400 MB"

    // ---- the old voice (Piper "Ryan", 3.0's betas): removed once Kokoro is in, only its own files ----
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

    private fun fetch(url: String, to: java.io.File, label: String, progress: (String) -> Unit) {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
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

    /** Whether url answers (a HEAD request, following GitHub's redirect to its download server). */
    private fun exists(url: String): Boolean = runCatching {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.requestMethod = "HEAD"; c.instanceFollowRedirects = true
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "RadonPlayer")
        (c.responseCode in 200..299).also { c.disconnect() }
    }.getOrDefault(false)

    /** The program's download: the newest release's Windows x64 build with TTS (shared libraries first: smaller),
     *  else the first known one that's there. */
    private fun programUrl(): String {
        runCatching {
            val rel = httpJson(RELEASES, mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "RadonPlayer"))
            val assets = rel["assets"].list.mapNotNull { a -> (a["name"].str ?: return@mapNotNull null) to (a["browser_download_url"].str ?: return@mapNotNull null) }
            fun ok(n: String, kind: String) = Regex("^sherpa-onnx-v[\\d.]+-win-x64-$kind(-MT-Release)?\\.tar\\.bz2$").matches(n)
            val best = assets.filter { ok(it.first, "shared") }.sortedBy { !it.first.contains("MT-Release") }.firstOrNull()
                ?: assets.filter { ok(it.first, "static") }.sortedBy { !it.first.contains("MT-Release") }.firstOrNull()
            if (best != null) return best.second
        }
        return PROGRAM_FALLBACK.firstOrNull { exists(it) } ?: error("Couldn't find the voice program to download")
    }

    /** Unpacks a .tar.bz2 into to, with Windows' own tar (in Windows 10 and 11). */
    private fun untar(archive: java.io.File, to: java.io.File) {
        to.mkdirs()
        val tar = System.getenv("SystemRoot")?.let { java.io.File(it, "System32\\tar.exe") }?.takeIf { it.isFile }?.path ?: "tar"
        val p = ProcessBuilder(tar, "-xf", archive.path, "-C", to.path).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) error("Couldn't unpack it: ${out.trim().take(120)}")
    }

    /** (blocking) Fetch the natural voice: the program and Kokoro. progress: what it's doing. Then the old Piper
     *  voice is deleted. */
    fun getNatural(progress: (String) -> Unit) {
        if (!windows) error("The natural voice is for Windows")
        dir.mkdirs()
        if (exe() == null) {
            progress("Finding the voice program…")
            val url = programUrl()
            val zip = java.io.File(dir, "program.tar.bz2")
            fetch(url, zip, "Getting the voice program", progress)
            progress("Unpacking the program…")
            programDir.deleteRecursively()
            try { untar(zip, programDir) } finally { zip.delete() }
            exeCache = null
            // Only the TTS program and its libraries are needed: the other programs in the package go
            programDir.walkTopDown().filter { it.isFile && it.extension.equals("exe", true) && !it.name.equals(EXE, true) }.forEach { it.delete() }
            if (exe() == null) error("The voice program didn't unpack")
        }
        if (!modelReady()) {
            val zip = java.io.File(dir, "kokoro.tar.bz2")
            fetch(MODEL_URL, zip, "Getting the voice", progress)
            progress("Unpacking the voice… (a minute or two)")
            modelDir.deleteRecursively()
            try { untar(zip, dir) } finally { zip.delete() }
        }
        if (!hasNatural) error("The voice didn't install")
        removePiper()
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
    private fun sayEleven(text: String, mine: Long): Boolean = runCatching {
        val pcm = DjVoices.eleven(text, "pcm_24000")
        if (turn != mine) return@runCatching true   // hushed while it came: said nothing, and that's fine
        playPcm(pcm, 24000f, mine)
        true
    }.getOrElse { e ->
        // Not the key, only what went wrong, and only once in a while (a toast for each line would be a lot)
        if (now() - elevenToldAt > 600) { elevenToldAt = now(); val why = e.message?.take(80); main { Music.toast("ElevenLabs voice: $why. Using the voice on this PC.") } }
        false
    }
    @Volatile private var elevenToldAt = 0.0

    /** The natural voice didn't speak: say so once per run, with its last line, so it isn't silently Windows' voice. */
    @Volatile private var toldFail = false
    /** Why the natural voice last failed ("" when it last worked): shown in the DJ's Voice settings, so it can be read. */
    var lastError by androidx.compose.runtime.mutableStateOf("")
    private fun naturalFailed(out: String) {
        val last = out.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty().take(300)
        lastError = last.ifEmpty { "it stopped without saying why" }
        if (toldFail) return
        toldFail = true
        Music.toast("The natural voice couldn't speak, so Windows' voice is used" + if (last.isNotEmpty()) ": $last" else "")
    }

    /** Kokoro's English dictionary's words (lowercase), read once: a word not in it is respelt ([Pronounce]), since
     *  Kokoro skips words it doesn't know (artists' names were left out). */
    @Volatile private var lexWords: Set<String>? = null
    private fun lexicon(): Set<String> = lexWords ?: runCatching {
        java.io.File(modelDir, "lexicon-us-en.txt").useLines { lines ->
            lines.mapNotNull { l -> l.trim().split(Regex("\\s+"), limit = 2).firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() } }.toHashSet() }
    }.getOrDefault(emptySet()).also { if (it.isNotEmpty()) lexWords = it }

    /** Kokoro's speed: a touch faster than its default, like someone talking, not reading (length scale under 1). */
    private const val LENGTH_SCALE = "0.92"

    /** (blocking) text said by Kokoro; false if it couldn't. */
    private fun sayNatural(text: String, mine: Long): Boolean = runCatching {
        val exe = exe() ?: return@runCatching false
        val wav = java.io.File.createTempFile("djvoice", ".wav")   // (Java wants 3+ letters before the random part)
        try {
            val v = DjVoices.kokoroVoice
            val m = modelDir
            // Plain letters only on the command line (é is e): the program reads it in the PC's old code page.
            val plain = java.text.Normalizer.normalize(Pronounce.sayable(text, lexicon()), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace("\"", "").replace(Regex("[^\\x20-\\x7E]"), " ").replace(Regex("\\s+"), " ").trim()
            if (plain.isEmpty()) return@runCatching false
            var lastOut = ""
            val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
            fun synth(withSpeed: Boolean): Boolean {
                val args = mutableListOf(exe.path,
                    "--kokoro-model=${java.io.File(m, "model.onnx").path}",
                    "--kokoro-voices=${java.io.File(m, "voices.bin").path}",
                    "--kokoro-tokens=${java.io.File(m, "tokens.txt").path}",
                    "--kokoro-data-dir=${java.io.File(m, "espeak-ng-data").path}",
                    // As sherpa-onnx's own example for this model: its dictionary folder and both lexicons
                    "--kokoro-dict-dir=${java.io.File(m, "dict").path}",
                    "--kokoro-lexicon=${java.io.File(m, "lexicon-us-en.txt").path},${java.io.File(m, "lexicon-zh.txt").path}",
                    "--num-threads=$threads", "--sid=${v.sid}", "--output-filename=${wav.path}")
                if (withSpeed) args += "--kokoro-length-scale=$LENGTH_SCALE"
                args += plain
                val pb = ProcessBuilder(args).directory(exe.parentFile).redirectErrorStream(true)
                // Its libraries (onnxruntime and sherpa-onnx's own) sit next to it or in lib/
                val lib = java.io.File(exe.parentFile.parentFile, "lib")
                pb.environment()["PATH"] = listOf(exe.parentFile.path, lib.path, System.getenv("PATH") ?: "").joinToString(";")
                wav.delete()
                // Noted before it starts, so a program Windows won't run still leaves its reason here
                val log = java.io.File(dir, "last-run.txt")
                runCatching { log.writeText("starting\n" + args.dropLast(1).joinToString("\n") + "\n") }
                val p = try { pb.start() } catch (e: Exception) {
                    runCatching { log.appendText("couldn't start: ${e.javaClass.simpleName}: ${e.message}\n") }
                    lastOut = "couldn't start it: ${e.message}"
                    return false
                }
                proc = p
                val out = p.inputStream.readBytes(); val code = p.waitFor()
                if (proc === p) proc = null
                // What it said, kept for finding out why the natural voice didn't speak (dj-voice-kokoro/last-run.txt)
                runCatching {
                    java.io.File(dir, "last-run.txt").writeText("exit $code, wav ${wav.length()} bytes\n" +
                        args.dropLast(1).joinToString("\n") + "\n---\n" + String(out).takeLast(6000))
                }
                lastOut = String(out)
                return turn == mine && wav.isFile && wav.length() > 1000
            }
            // The speed flag is newer than some builds: without it if it's not known
            if (!synth(true) && (turn != mine || !synth(false))) {
                if (turn == mine) naturalFailed(lastOut)
                return@runCatching turn != mine
            }
            lastError = ""
            val ais = javax.sound.sampled.AudioSystem.getAudioInputStream(wav)
            val c = javax.sound.sampled.AudioSystem.getClip()
            c.open(ais); clip = c
            try {
                c.start()
                Thread.sleep(150)
                while (c.isRunning && turn == mine) Thread.sleep(50)
            } finally { runCatching { c.stop(); c.close() }; clip = null }
            true
        } finally { wav.delete() }
    }.onFailure { naturalFailed("${it.javaClass.simpleName}: ${it.message ?: ""}") }.getOrDefault(false)

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

    /** Say text out loud; done once it's said (or couldn't be, or was hushed). */
    fun say(text: String, done: () -> Unit = {}) {
        if (!canSpeak) { done(); return }
        hush()
        val mine = ++turn
        onDone = done
        Thread {
            if (hasNatural) removePiper()   // the old voice, if it's still there
            val said = (DjVoices.elevenOn && sayEleven(text, mine)) ||
                (turn == mine && hasNatural && sayNatural(text, mine))
            if (!said && turn == mine) saySapi(text, mine)
            finish(mine)
        }.apply { isDaemon = true }.start()
    }

    /** Stop talking now. */
    /** A line the DJ is about to say, made ahead: the PC's voices make a line in a moment, so nothing to do. */
    @Suppress("UNUSED_PARAMETER") fun prepare(text: String) {}

    fun hush() {
        turn++
        val p = proc; proc = null
        runCatching { p?.destroyForcibly() }
        runCatching { line?.stop(); line?.flush() }
        runCatching { clip?.stop() }
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
