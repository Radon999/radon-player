package org.omarchy.music

import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/** Ignore Bluetooth devices, the phone's side: when an ignored device (the car) is connected, the app's players are asked
 *  to play on the output the music was on before (ExoPlayer's preferred audio device), and let go again when it leaves
 *  or ignoring is turned off. Android decides in the end: many phones only keep one Bluetooth audio device playing at a
 *  time, and when the car takes over, the headphones drop out of the outputs, so there's nowhere to keep the music and
 *  it's left as it is. Never onto the phone's own speaker.
 *  Android Auto (wired or wireless) isn't a Bluetooth output: it takes apps' sound for the car itself. When it's on and
 *  the car is ignored (its Bluetooth name, connected for calls or music, or "Android Auto" picked in the list), the
 *  music is kept on your headphones and the app's players ask not to be captured (ALLOW_CAPTURE_BY_NONE), which keeps
 *  Android Auto from taking it: the map, calls and the rest stay on the car's screen. Runs on the main thread (the
 *  outputs watcher, the switch). */
object BtRoute {
    private val BT = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
    private val WIRED = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)

    /** The name Android Auto goes by in the list of devices to ignore. */
    const val AUTO = "Android Auto"

    private val players = java.util.WeakHashMap<ExoPlayer, Boolean>()   // each with whether it handles audio focus
    private val noFocus = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ExoPlayer, Boolean>())
    private var device: AudioDeviceInfo? = null   // where the music's kept now (null: where Android puts it)
    /** Keeping the sound from Android Auto now (its players ask not to be captured). */
    @Volatile var hidden = false
        private set
    private var previous: String? = null          // the output before the ignored device came (see key)
    private var arrived: Map<String, Long> = emptyMap()   // when each connected device came (Newest Bluetooth device wins)
    private val gone = HashMap<String, Pair<Long, Long>>()   // devices that just went: (when they came, when they went)
    private const val BLIP_MS = 90_000L                        // back within this long: the same connection
    /** A Bluetooth name that's plainly a car's (as Outputs names them). */
    private val CAR = Regex("(?i)car|auto|sync|uconnect|vw|toyota|ford|honda|bmw|audi|mazda|kia|hyundai|chevy|chevrolet|colorado|silverado|gmc|mylink|nissan|tesla|subaru|jeep|dodge|mercedes|lexus")

    /** Where the music's kept now, as Outputs names it (for the EQ): null while it isn't. */
    @Volatile var routed: Outputs.Out? = null
        private set

    /** One of the app's players (the main one, crossfade's second): it plays where the music's kept too. */
    fun attach(p: ExoPlayer) {
        players[p] = p !in noFocus
        device?.let { d -> runCatching { p.setPreferredAudioDevice(d) } }
        if (hidden) attrs(p, true)
    }

    fun detach(p: ExoPlayer) { players -= p }

    /** A player that doesn't handle audio focus (crossfade's second): say so before it's attached. */
    fun withoutFocus(p: ExoPlayer) { noFocus += p }

    private fun attrs(p: ExoPlayer, hide: Boolean) = runCatching {
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setAllowedCapturePolicy(if (hide) C.ALLOW_CAPTURE_BY_NONE else C.ALLOW_CAPTURE_BY_ALL).build(), players[p] ?: true)
    }

    /** The DJ's voice: kept off Android Auto and on your headphones too, while the music is. */
    fun voice(b: android.media.AudioAttributes.Builder): android.media.AudioAttributes.Builder {
        if (hidden && android.os.Build.VERSION.SDK_INT >= 29) runCatching { b.setAllowedCapturePolicy(android.media.AudioAttributes.ALLOW_CAPTURE_BY_NONE) }
        return b
    }
    fun voiceDevice(): AudioDeviceInfo? = if (hidden) device else null

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
        // Bluetooth devices connected for calls only (a car on Android Auto often is): there to pick, and to know the car's here
        val calls = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }.mapNotNull { name(it) }.distinct()
        val auto = Outputs.carProjecting
        IgnoreBt.saw(bt + calls + (if (auto) listOf(AUTO) else emptyList()))
        val ignored = IgnoreBt.ignored().toSet()
        val carIgnored = auto && IgnoreBt.on && (AUTO in ignored || (bt + calls).any { it in ignored })
        // Newest Bluetooth device wins: of the ones connected (Android Auto too), the last to come has the music. An
        // ignored one never does (Ignore Bluetooth devices first); with fewer than two left, the usual rules decide.
        val nowMs = System.currentTimeMillis()
        val present = bt + (if (auto) listOf(AUTO) else emptyList())
        // A device that drops out for a moment and comes back (an earbud taken out, a car reconnecting) keeps when it
        // first came: before, its blip made it the newest, and the music went back to it a couple of seconds later.
        for ((k, at) in arrived) if (k !in present) gone[k] = at to nowMs
        gone.entries.removeAll { nowMs - it.value.second > BLIP_MS }
        val before = arrived + present.filter { it !in arrived }.mapNotNull { k -> gone[k]?.let { k to it.first } }
        arrived = BtIgnoreRules.arrivals(before, present, nowMs)
        // Android Auto is the car: it starts a few seconds after the car's own Bluetooth connects. That car (a Bluetooth
        // output that came within a minute of it) stands for both, so Android Auto starting never counts as a newer
        // device (it used to hand the music back to the earbud); the car keeps it, on its Bluetooth.
        val carBt = if (auto) arrived[AUTO]?.let { a -> bt.filter { kotlin.math.abs((arrived[it] ?: 0L) - a) < 60_000 }
            .maxByOrNull { arrived[it] ?: 0L } } else null
        val compete = if (carBt != null) bt else present
        if (carBt != null) IgnoreBt.learnCar(carBt)
        // Car always wins: the car (its Bluetooth, else Android Auto) has the music whenever it's here, headphones or not
        if (IgnoreBt.carFirst) {
            val known = IgnoreBt.carNames().toSet()
            val ign = if (IgnoreBt.on) ignored else emptySet()
            val car = outs.firstOrNull { it.type in BT && key(it) !in ign &&
                (key(it) == carBt || key(it) in known || CAR.containsMatchIn(key(it) ?: "")) }
            if (car != null || (auto && !carIgnored)) {
                use(car)          // the car's Bluetooth; with only Android Auto, where Android puts it (the car)
                hide(false)
                val name = car?.let { key(it) } ?: AUTO
                IgnoreBt.newest(name, compete.filter { it != name && it !in ign && it != AUTO }.joinToString(", "))
                IgnoreBt.report("", routed?.label ?: name, "")
                return
            }
        }
        if (IgnoreBt.newestOn) {
            val ign = (if (IgnoreBt.on) ignored else emptySet()) + (if (carIgnored) setOf(AUTO) else emptySet())
            val pick = BtIgnoreRules.newest(compete, arrived, ign)
            val d = pick?.takeIf { it != AUTO }?.let { k -> outs.firstOrNull { it.type in BT && key(it) == k } }
            if (pick == AUTO || d != null) {
                use(d)          // Android Auto: where Android puts it (the car); else the newest device
                hide(d != null && auto && pick != carBt)   // headphones newer than Android Auto: kept from it (not the car itself)
                IgnoreBt.newest(pick!!, compete.filter { it != pick && it !in ign }.joinToString(", "))
                val here = if (IgnoreBt.on) ((bt + calls).filter { it in ignored } + (if (carIgnored) listOf(AUTO) else emptyList())).distinct().joinToString(", ") else ""
                IgnoreBt.report(here, routed?.label ?: if (pick == AUTO) AUTO else "", "")
                return
            }
        }
        IgnoreBt.newest("", "")
        if (carIgnored) { keepFromAuto(outs, ignored, bt + calls); return }
        hide(false)
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

    /** Android Auto is on and the car is ignored: the music on your headphones (Bluetooth ones not ignored, else wired)
     *  and kept from Android Auto. With none connected, it's left to Android Auto. */
    private fun keepFromAuto(outs: List<AudioDeviceInfo>, ignored: Set<String>, here: List<String>) {
        val d = outs.firstOrNull { it.type in BT && key(it) !in ignored } ?: outs.firstOrNull { it.type in WIRED }
        use(d)
        hide(d != null)
        val car = (here.filter { it in ignored } + listOf(AUTO)).first()
        IgnoreBt.report(car, routed?.label ?: "", if (d == null) "your headphones aren't connected" else "")
    }

    private fun hide(h: Boolean) {
        if (h == hidden) return
        hidden = h
        players.keys.toList().forEach { attrs(it, h) }
    }

    private fun use(d: AudioDeviceInfo?) {
        if (d?.id == device?.id) return   // the same one (or none, as before)
        device = d
        routed = d?.let { out(it) }
        players.keys.toList().forEach { p -> runCatching { p.setPreferredAudioDevice(d) } }
    }
}
