package org.omarchy.music

import kotlinx.coroutines.launch

/** The DJ's voice and voice typing on the PC: Windows' own speech (System.Speech, through PowerShell): its natural
 *  English voice, a touch slower than its default, and dictation for the search bars. Off Windows there's neither. */
object Voice {
    private val windows = System.getProperty("os.name").lowercase().contains("win")
    @Volatile private var talking: Process? = null
    @Volatile private var onDone: (() -> Unit)? = null

    val canSpeak get() = windows

    // ---- The natural voice: Piper, a free neural text-to-speech (MIT licence) that runs on the PC, with its "Ryan" voice:
    // it sounds like a person, not a robot. Downloaded once, when you ask for it (about 140 MB), from Piper's own releases. ----
    private const val PIPER_ZIP = "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip"
    private const val MODEL = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/ryan/high/en_US-ryan-high.onnx"
    private val dir get() = java.io.File(Music.ctx.filesDir, "dj-voice")
    private val exe get() = java.io.File(dir, "piper/piper.exe")
    private val model get() = java.io.File(dir, "en_US-ryan-high.onnx")
    private val modelJson get() = java.io.File(dir, "en_US-ryan-high.onnx.json")

    /** The natural voice isn't on this PC yet (it can be fetched). */
    val needsNatural get() = windows && !(exe.isFile && model.isFile && modelJson.isFile)

    private fun fetch(url: String, to: java.io.File, label: String, progress: (String) -> Unit) {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        if (c.responseCode !in 200..299) error("$label: the download failed (${c.responseCode})")
        val total = c.contentLengthLong
        val part = java.io.File(to.path + ".part")
        c.inputStream.use { ins -> part.outputStream().use { out ->
            val buf = ByteArray(1 shl 16); var done = 0L; var shown = -1L
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                out.write(buf, 0, n); done += n
                val pct = if (total > 0) done * 100 / total else -1
                if (pct != shown) { shown = pct; progress(if (pct >= 0) "$label… $pct%" else "$label… ${done / 1_000_000} MB") }
            }
        } }
        to.delete(); if (!part.renameTo(to)) error("$label: couldn't save it")
    }

    /** (blocking) Fetch the natural voice: Piper and its voice. progress: what it's doing. */
    fun getNatural(progress: (String) -> Unit) {
        dir.mkdirs()
        if (!exe.isFile) {
            val zip = java.io.File(dir, "piper.zip")
            fetch(PIPER_ZIP, zip, "Getting the voice program", progress)
            progress("Unpacking…")
            java.util.zip.ZipFile(zip).use { z ->
                for (e in z.entries()) {
                    val out = java.io.File(dir, e.name).canonicalFile
                    if (!out.path.startsWith(dir.canonicalPath)) continue   // nothing outside its folder
                    if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); z.getInputStream(e).use { i -> out.outputStream().use { i.copyTo(it) } } }
                }
            }
            zip.delete()
        }
        if (!modelJson.isFile) fetch("$MODEL.json", modelJson, "Getting the voice", progress)
        if (!model.isFile) fetch(MODEL, model, "Getting the voice", progress)
        if (needsNatural) error("The voice didn't install")
    }

    @Volatile private var clip: javax.sound.sampled.Clip? = null

    /** (blocking) text said by the natural voice; false if it couldn't. */
    private fun sayNatural(text: String, p: (Process) -> Unit): Boolean = runCatching {
        val wav = java.io.File.createTempFile("dj", ".wav")
        try {
            val proc = ProcessBuilder(exe.path, "--model", model.path, "--output_file", wav.path, "--sentence_silence", "0.3")
                .directory(exe.parentFile).redirectErrorStream(true).start()
            p(proc)
            proc.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            proc.inputStream.readBytes(); proc.waitFor()
            if (talking !== proc || !wav.isFile || wav.length() < 1000) return@runCatching false
            val ais = javax.sound.sampled.AudioSystem.getAudioInputStream(wav)
            val c = javax.sound.sampled.AudioSystem.getClip()
            c.open(ais); clip = c
            c.start()
            Thread.sleep(150)
            while (c.isRunning && talking === proc) Thread.sleep(50)
            c.stop(); c.close(); clip = null
            true
        } finally { wav.delete() }
    }.getOrDefault(false)

    private fun ps(script: String): Process {
        val enc = java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", enc).redirectErrorStream(true).start()
    }

    private fun main(f: () -> Unit) { Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { f() } }

    /** Say text out loud; onDone once it's said (or couldn't be). */
    fun say(text: String, done: () -> Unit = {}) {
        if (!windows) { done(); return }
        hush()
        onDone = done
        val said = text.replace("'", "''")
        Thread {
            if (!needsNatural) {
                var mine: Process? = null
                val ok = sayNatural(text) { mine = it; talking = it }
                if (ok || (mine != null && talking !== mine)) {   // said, or stopped (hush)
                    if (mine != null && talking === mine) { talking = null; onDone?.let { d -> onDone = null; main(d) } }
                    return@Thread
                }
            }
            val p = runCatching {
                ps("Add-Type -AssemblyName System.Speech; \$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    // The best English voice installed: a newer one (Mark, Aria, Guy…) over the old David and Zira
                    "\$v = \$s.GetInstalledVoices() | ? { \$_.Enabled -and \$_.VoiceInfo.Culture.Name -like 'en*' } | " +
                    "Sort-Object { if (\$_.VoiceInfo.Name -match 'Mark|Guy|Aria|Jenny|Natural') { 0 } elseif (\$_.VoiceInfo.Gender -eq 'Male') { 1 } else { 2 } } | Select-Object -First 1; " +
                    "if (\$v) { \$s.SelectVoice(\$v.VoiceInfo.Name) }; \$s.Rate = 0; \$s.Speak('$said')")
            }.getOrNull()
            talking = p
            runCatching { p?.inputStream?.readBytes(); p?.waitFor() }
            if (talking === p) { talking = null; onDone?.let { d -> onDone = null; main(d) } }
        }.apply { isDaemon = true }.start()
    }

    /** Stop talking now. */
    fun hush() {
        val p = talking; talking = null
        runCatching { p?.destroyForcibly() }
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
