package org.omarchy.music

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.launch

/** The DJ's voice on the phone: Android's text-to-speech, the most natural English voice installed (the "high quality"
 *  ones Google's speech engine downloads), a touch slower and lower than its default so it sounds like a radio host. */
object Voice {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val waiting = ArrayList<Pair<String, () -> Unit>>()
    private val done = HashMap<String, () -> Unit>()
    private var n = 0

    val canSpeak get() = true

    private fun start() {
        if (tts != null) return
        tts = TextToSpeech(Music.ctx) { status ->
            ready = status == TextToSpeech.SUCCESS
            val t = tts ?: return@TextToSpeech
            if (ready) {
                runCatching {
                    val best = t.voices?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired &&
                        "notInstalled" !in (it.features ?: emptySet()) }
                        ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }
                            .thenByDescending { it.locale.country == Locale.getDefault().country || it.locale.country == "US" })
                        ?.firstOrNull()
                    if (best != null) t.voice = best else t.language = Locale.US
                }
                t.setSpeechRate(1.02f); t.setPitch(0.92f)
            }
            val w = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
            if (ready) w.forEach { (s, d) -> say(s, d) } else w.forEach { it.second() }
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
        start()
        if (!ready) { synchronized(waiting) { waiting += text to onDone }; return }
        val id = "dj${n++}"
        synchronized(done) { done[id] = onDone }
        val p = android.os.Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC) }
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, p, id) != TextToSpeech.SUCCESS) finish(id)
    }

    /** Stop talking now. */
    fun hush() {
        runCatching { tts?.stop() }
        val all = synchronized(done) { done.values.toList().also { done.clear() } }
        all.forEach { Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { it() } }
    }
}
