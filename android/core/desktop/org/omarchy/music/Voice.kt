package org.omarchy.music

import kotlinx.coroutines.launch

/** The DJ's voice and voice typing on the PC: Windows' own speech (System.Speech, through PowerShell): its natural
 *  English voice, a touch slower than its default, and dictation for the search bars. Off Windows there's neither. */
object Voice {
    private val windows = System.getProperty("os.name").lowercase().contains("win")
    @Volatile private var talking: Process? = null
    @Volatile private var onDone: (() -> Unit)? = null

    val canSpeak get() = windows

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
