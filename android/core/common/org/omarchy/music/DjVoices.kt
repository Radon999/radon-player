package org.omarchy.music

/** The voices the DJ can speak with, beyond the device's own (Voice, per platform, says them):
 *  - ElevenLabs, with your own API key: a studio quality voice over the internet, used first when a key is set (it
 *    spends your ElevenLabs credits). Only ElevenLabs' own premade voices, no copies of real people.
 *  - Kokoro (on the PC): a free voice that runs on the PC, picked here. */
object DjVoices {
    // ---- ElevenLabs ----
    const val ELEVEN_KEY = "dj_elevenlabs_key"       // your API key (kept in Prefs; never shown in logs or errors)
    const val ELEVEN_VOICE = "dj_elevenlabs_voice"   // which voice (an ElevenLabs voice id)

    /** ElevenLabs' premade voices that suit a radio host: id to name. */
    val ELEVEN_VOICES = listOf(
        "iP95p4xoKVk53GoZ742B" to "Chris: warm and easy going",
        "nPczCjzI2devNBz1zQrb" to "Brian: deep and smooth",
        "cjVigY5qzO86Huf0OWal" to "Eric: friendly and bright",
    )

    val elevenKey: String get() = Prefs.text(ELEVEN_KEY).trim()
    val elevenOn: Boolean get() = false   // Kokoro only (the user's choice); the ElevenLabs code stays, unused
    val elevenVoice: String get() = Prefs.text(ELEVEN_VOICE).takeIf { v -> ELEVEN_VOICES.any { it.first == v } } ?: ELEVEN_VOICES[0].first

    fun setElevenKey(key: String) = Prefs.setText(ELEVEN_KEY, key.trim())
    fun setElevenVoice(id: String) = Prefs.setText(ELEVEN_VOICE, id)

    /** (blocking) text said by ElevenLabs, as audio in format ("mp3_44100_128", or "pcm_24000": 16-bit mono samples).
     *  Throws when it can't (no key, offline, out of credits); the error never holds the key. */
    fun eleven(text: String, format: String): ByteArray {
        val key = elevenKey
        if (key.isEmpty()) error("No ElevenLabs key")
        val url = java.net.URL("https://api.elevenlabs.io/v1/text-to-speech/$elevenVoice?output_format=$format")
        val c = url.openConnection() as java.net.HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000; c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("xi-api-key", key)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", if (format.startsWith("mp3")) "audio/mpeg" else "application/octet-stream")
            val body = kotlinx.serialization.json.buildJsonObject {
                put("text", kotlinx.serialization.json.JsonPrimitive(text))
                put("model_id", kotlinx.serialization.json.JsonPrimitive("eleven_turbo_v2_5"))
                // A bit of life in it: less stable than the default, so it sounds like talking, not reading
                put("voice_settings", kotlinx.serialization.json.buildJsonObject {
                    put("stability", kotlinx.serialization.json.JsonPrimitive(0.4))
                    put("similarity_boost", kotlinx.serialization.json.JsonPrimitive(0.75))
                    put("style", kotlinx.serialization.json.JsonPrimitive(0.3))
                    put("use_speaker_boost", kotlinx.serialization.json.JsonPrimitive(true))
                })
            }.toString()
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code !in 200..299) error(when (code) {
                401 -> "ElevenLabs didn't accept the key"
                402, 429 -> "ElevenLabs is out of credits or busy"
                else -> "ElevenLabs answered $code"
            })
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size < 1000) error("ElevenLabs sent no audio")
            return bytes
        } finally { c.disconnect() }
    }

    // ---- Kokoro (the PC's free natural voice) ----
    const val KOKORO_VOICE = "dj_kokoro_voice"

    /** Kokoro's voices offered (name in the model, its speaker number in kokoro-multi-lang-v1_0, what it sounds like). */
    data class KokoroVoice(val id: String, val sid: Int, val label: String)

    /** The DJ's one voice: Heart, a female voice that sounds like a person. */
    val KOKORO_VOICES = listOf(KokoroVoice("af_heart", 3, "Female"))

    val kokoroVoice: KokoroVoice get() = KOKORO_VOICES[0]
    fun setKokoroVoice(id: String) = Prefs.setText(KOKORO_VOICE, id)

    /** What the DJ says to try a voice. */
    const val SAMPLE = "Hey. This is how I sound... not bad, right? Alright, back to the music."
}
