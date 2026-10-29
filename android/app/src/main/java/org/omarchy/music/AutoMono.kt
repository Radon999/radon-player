package org.omarchy.music

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Auto mono (Settings): mono on its own while your chosen earbuds are connected, off again when they go. Android can't
 *  tell other apps whether one earbud or both are in (Sony's, among others, keep that to their own app), so it's by the
 *  earbuds being connected; Now playing's Mono button still turns it on or off by hand. */
object AutoMono {
    private var am: AudioManager? = null
    /** Auto mono has mono on right now (the earbuds are connected). */
    var active by mutableStateOf(false)
        private set

    private val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26 /* BLE headset */,
        27 /* BLE speaker */, 30 /* BLE broadcast */)

    fun start(ctx: Context) {
        val m = ctx.getSystemService(AudioManager::class.java) ?: return
        am = m
        m.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = check()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = check()
        }, Handler(Looper.getMainLooper()))
        check()
    }

    /** The Bluetooth earbuds and headphones connected now, by name. */
    fun connected(): List<String> = runCatching {
        am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().filter { it.type in BLUETOOTH }
            .map { it.productName?.toString().orEmpty() }.filter { it.isNotBlank() }.distinct()
    }.getOrDefault(emptyList())

    /** Look again (the switch, the earbuds picked, or a device came or went). */
    fun check() {
        val want = Prefs.text(Prefs.AUTO_MONO_DEVICE)
        active = Prefs.on(Prefs.AUTO_MONO) && want.isNotEmpty() && connected().any { it == want }
        Music.eqProcessor.mono = Prefs.on(Prefs.MONO) || active
    }
}
