package org.omarchy.music

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A backup made on the other device (the phone's on the PC, or the PC's on the phone): what of it comes across,
 *  and what stays this device's own. */
object BackupMerge {
    /** state.json's settings that belong to the device: where its music is, where downloads go, its audio output. */
    val DEVICE_STATE = listOf("folders", "download_dir", "bit_perfect", "mic_asked")

    /** Switches that belong to the device (its earbuds, its window, its own reminders), and sign-ins, which no backup keeps. */
    val DEVICE_SWITCHES = setOf("auto_mono_device", "pc_volume", "tray_hint_shown", "notif_asked", "notif_hinted",
        "phone_remote", "discord", "close_after", "adopt_files")

    fun isSignIn(key: String) = key.endsWith("_cookie") || key.endsWith("_token") || key.endsWith("_password")

    /** The backup's state.json with this device's own settings (from here: this device's state.json) kept. */
    fun mergeState(backup: String, here: String?): String {
        val b = JSON.parseToJsonElement(backup) as JsonObject
        val h = here?.let { runCatching { JSON.parseToJsonElement(it) as JsonObject }.getOrNull() } ?: JsonObject(emptyMap())
        val out = LinkedHashMap(b)
        for (k in DEVICE_STATE) { val v = h[k]; if (v != null) out[k] = v else out.remove(k) }
        return JsonObject(out).toString()
    }

    /** The switches to put in a backup: all but sign-ins. */
    fun switchesToSave(all: Map<String, Any>): String = JsonObject(all.filterKeys { !isSignIn(it) }.mapNotNull { (k, v) ->
        when (v) { is Boolean -> k to JsonPrimitive(v); is Number -> k to JsonPrimitive(v); is String -> k to JsonPrimitive(v); else -> null }
    }.toMap()).toString()

    /** The switches a backup brings back: on the other device, not this one's own. */
    fun switchesToRestore(json: String, crossDevice: Boolean): Map<String, Any> {
        val o = JSON.parseToJsonElement(json) as JsonObject
        val out = LinkedHashMap<String, Any>()
        for ((k, v) in o) {
            if (isSignIn(k) || (crossDevice && k in DEVICE_SWITCHES)) continue
            val p = v as? JsonPrimitive ?: continue
            out[k] = when {
                p.isString -> p.content
                p.content == "true" || p.content == "false" -> p.content == "true"
                else -> p.content.toFloatOrNull() ?: continue
            }
        }
        return out
    }

    /** Whether a song file's address can be this device's (else it's the other one's, from a backup). */
    fun fileHere(f: String, android: Boolean): Boolean =
        if (android) f.startsWith("content:") || f.startsWith("/")
        else !f.startsWith("content:") && !f.startsWith("/storage/") && !f.startsWith("/sdcard/")
}
