package org.omarchy.music

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.launch

/** The DJ's voice on the phone: ElevenLabs first when you've pasted your own API key (DjVoices; it spends your
 *  ElevenLabs credits), else (or offline, or out of credits) Android's text-to-speech with the most natural English
 *  voice installed (the "high quality" ones Google's speech engine downloads), a touch lower than its default so it
 *  sounds like a radio host. Kokoro, the PC's natural voice, would need a large native library on the phone, so the
 *  phone keeps the system's voices. */
object Voice {
    /** Why the PC's natural voice last failed (the phone has none: always ""). */
    val lastError: String get() = ""

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val waiting = ArrayList<Pair<String, () -> Unit>>()
    private val done = HashMap<String, () -> Unit>()
    private var n = 0

    val canSpeak get() = true

    /** The PC can fetch a natural (neural) voice; the phone uses Google's natural voices already. */
    val needsNatural get() = false
    val hasNatural get() = false
    const val naturalSize = ""
    fun getNatural(progress: (String) -> Unit) {}

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
        if (DjVoices.elevenOn) { sayEleven(text, mine, onDone); return }
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
        player?.let { mp -> runCatching { mp.stop() }; elevenEnd?.let { e -> elevenEnd = null; e() } }
        runCatching { tts?.stop() }
        val all = synchronized(done) { done.values.toList().also { done.clear() } }
        all.forEach { Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { it() } }
    }
}
