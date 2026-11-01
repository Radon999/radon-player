package org.omarchy.music

import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer

/** Ignore Bluetooth devices, the phone's side: when an ignored device (the car) is connected, the app's players are asked
 *  to play on the output the music was on before (ExoPlayer's preferred audio device), and let go again when it leaves
 *  or ignoring is turned off. Android decides in the end: many phones only keep one Bluetooth audio device playing at a
 *  time, and when the car takes over, the headphones drop out of the outputs, so there's nowhere to keep the music and
 *  it's left as it is. Never onto the phone's own speaker, and never while Android Auto has the car (its sound is
 *  its own). Runs on the main thread (the outputs watcher, the switch). */
object BtRoute {
    private val BT = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
    private val WIRED = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)

    private val players = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ExoPlayer, Boolean>())
    private var device: AudioDeviceInfo? = null   // where the music's kept now (null: where Android puts it)
    private var previous: String? = null          // the output before the ignored device came (see key)

    /** Where the music's kept now, as Outputs names it (for the EQ): null while it isn't. */
    @Volatile var routed: Outputs.Out? = null
        private set

    /** One of the app's players (the main one, crossfade's second): it plays where the music's kept too. */
    fun attach(p: ExoPlayer) {
        players += p
        device?.let { d -> runCatching { p.setPreferredAudioDevice(d) } }
    }

    fun detach(p: ExoPlayer) { players -= p }

    private fun name(d: AudioDeviceInfo) = d.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != android.os.Build.MODEL }

    /** An output's name for the rules: a Bluetooth device's own name (as picked in Settings), else "usb:…" or "wired". */
    private fun key(d: AudioDeviceInfo): String? = when (d.type) {
        in BT -> name(d)
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "usb:" + (name(d) ?: "USB")
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired"
        else -> null
    }

    /** The output as Outputs.now names it (its key and label are the EQ's, so the name exactly as Android gives it). */
    private fun out(d: AudioDeviceInfo): Outputs.Out {
        val n = d.productName?.toString()?.takeIf { it.isNotBlank() && it != android.os.Build.MODEL }
        return when (d.type) {
            in BT -> (n ?: "Bluetooth").let { Outputs.Out("bt:$it", it, "bluetooth") }
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> (n ?: "USB").let { Outputs.Out("usb:$it", "$it (USB)", "usb") }
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> Outputs.Out("headset", "Headset (jack, with mic)", "wired")
            else -> Outputs.Out("wired", "Headphones (jack)", "wired")
        }
    }

    /** Look at the outputs again: remember the Bluetooth ones, keep the music off an ignored one if there's somewhere to. */
    fun check() {
        val am = Music.ctx.getSystemService(AudioManager::class.java) ?: return
        val outs = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
            .filter { (it.type in BT || it.type in WIRED) && key(it) != null }
        val bt = outs.filter { it.type in BT }.mapNotNull { key(it) }.distinct()
        IgnoreBt.saw(bt)
        val ignored = IgnoreBt.ignored().toSet()
        val auto = Outputs.carProjecting
        val ignoring = IgnoreBt.on && !auto
        // Where Android plays by itself when nothing's ignored: a Bluetooth device first, then USB or wired (as Outputs.now)
        val natural = (outs.firstOrNull { it.type in BT && key(it) !in ignored } ?: outs.firstOrNull { it.type in WIRED })?.let { key(it) }
        val target = BtIgnoreRules.keepOn(ignoring, ignored, bt, previous, outs.mapNotNull { key(it) })
        previous = BtIgnoreRules.previousAfter(ignoring, ignored, bt, natural, previous)
        val d = target?.let { t -> outs.firstOrNull { key(it) == t } }
        use(d)
        val here = if (IgnoreBt.on) bt.filter { it in ignored }.joinToString(", ") else ""
        IgnoreBt.report(here, routed?.label ?: "", when {
            here.isEmpty() || d != null -> ""
            auto -> "Android Auto is connected, and its sound can't be moved"
            else -> "your headphones aren't connected (or the phone let them go when it connected)"
        })
    }

    private fun use(d: AudioDeviceInfo?) {
        if (d?.id == device?.id) return   // the same one (or none, as before)
        device = d
        routed = d?.let { out(it) }
        players.toList().forEach { p -> runCatching { p.setPreferredAudioDevice(d) } }
    }
}
