package org.omarchy.music

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Ignore Bluetooth devices: no stutter at all (Settings › Playback, under Ignore Bluetooth devices; the phone, Android
 *  14 on): with Shizuku (a free app that lends other apps the powers of USB debugging), while you have something else to
 *  listen on (your earbuds), an ignored device (the truck) is kept disconnected: its Media audio off, so it never takes
 *  the sound, and its calls, phonebook and messages too, so it doesn't connect at all and can't cut the music in and
 *  out at the edge of its range (each time it connected or dropped there, the earbuds stuttered). The player's
 *  notification has a button for Android Auto: Connect Android Auto lets the truck connect for calls and Android Auto
 *  (its map), Media audio still off, so the music stays on your earbuds; Disconnect Android Auto cuts it off again.
 *  A truck already connected when the earbuds come is left connected. With nothing else to listen on it all comes back
 *  (after [BtIgnoreRules.ELSEWHERE_GONE_MS]), so the truck still plays the music when it's all there is, as without
 *  Shizuku. Switching the feature off, or a device off the list, gives everything back; a connection you'd switched
 *  off yourself is left alone. The switching itself happens in [SeamlessBtService], inside Shizuku. Main thread. */
object SeamlessBt {
    const val SHIZUKU = "moe.shizuku.privileged.api"
    private const val ASK = 4517       // Shizuku's permission question
    private const val VERSION = 2      // SeamlessBtService's: a new one replaces a running old one (2: which connection)
    private const val RETRY_MS = 60_000L

    /** What it's doing or waiting on, for Settings. */
    var status by mutableStateOf("")
        private set
    /** The next thing to do for it to work ("" when nothing): the button beside the status. */
    var step by mutableStateOf("")
        private set

    val on: Boolean get() = Prefs.on(Prefs.IGNORE_BT_SHIZUKU)
    val supported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 34

    private val h = Handler(Looper.getMainLooper())
    private var listening = false
    private var service: IBinder? = null
    private var binding = false
    private var working = false
    private var wasElsewhere = false
    private var goneAt = 0L                          // when the last thing else to listen on went (elapsed ms)
    private val failedAt = HashMap<String, Long>()   // "name:policy" that failed, and when (tried again a minute later)
    private var failure = ""
    private var warned = ""
    private val again = Runnable { update() }

    /** The devices whose Media audio the app switched off (and gives back). */
    private fun mine(): List<String> = BtIgnoreRules.parse(Prefs.text(Prefs.IGNORE_BT_MEDIA_OFF))
    private fun setMine(l: List<String>) = Prefs.setText(Prefs.IGNORE_BT_MEDIA_OFF, BtIgnoreRules.join(l))

    private fun installed() = runCatching { Music.ctx.packageManager.getPackageInfo(SHIZUKU, 0); true }.getOrDefault(false)
    private fun running() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    private fun allowed() = running() &&
        runCatching { !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    private fun listen() {
        if (listening) return
        listening = true
        Shizuku.addBinderReceivedListenerSticky { h.post { update() } }
        Shizuku.addBinderDeadListener { h.post { service = null; binding = false; update() } }
        Shizuku.addRequestPermissionResultListener { code, _ -> if (code == ASK) h.post { update() } }
        // Back in the app (from Shizuku, after starting it or allowing the app): look again
        Music.scope.launch { AppVisible.flow.collect { if (it) h.post { update() } } }
    }

    /** The switch. On: straight to whatever Shizuku still needs (installing it, starting it, allowing the app). */
    fun setOn(v: Boolean) {
        Prefs.set(Prefs.IGNORE_BT_SHIZUKU, v)
        failedAt.clear(); failure = ""
        listen()
        update()
        if (v) nextStep()
    }

    /** The next step for Shizuku to work: Google Play's page for it, Shizuku itself (to start it), or its question. */
    fun nextStep() {
        val c = Music.ctx
        when {
            !supported -> {}
            !installed() -> {
                val market = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$SHIZUKU"))
                val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU"))
                runCatching { c.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { runCatching { c.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            // Not running, or the app was refused for good (only Shizuku's own list can allow it then)
            !running() || runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false) ->
                c.packageManager.getLaunchIntentForPackage(SHIZUKU)?.let { runCatching { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            !allowed() -> runCatching { Shizuku.requestPermission(ASK) }
        }
    }

    /** The player notification's button while Ignore Bluetooth devices and this are both on, saying how it stands now:
     *  "Android Auto off: tap to connect" while the truck is kept off, "Android Auto on: tap to disconnect" while it may
     *  connect; "" (no button) otherwise. [autoOn]: which of the two. */
    var autoButton by mutableStateOf("")
        private set
    val autoOn: Boolean get() = autoButton.isNotEmpty() && !callsOff
    private var autoAllowed = false   // Connect Android Auto was tapped: the truck may connect for calls and Android Auto
    private var forced = false        // Disconnect Android Auto was tapped: kept off even while connected
    private var sawCar = false        // ... and it has since
    private var callsOff = false      // the truck's calls connection is meant to be off now

    /** The notification's button. While off, a tap turns Android Auto on: the truck may connect for calls and Android
     *  Auto, which then starts as usual; its Media audio stays off, so the music stays on your headphones. While on, a
     *  tap turns it off: the truck is cut off at once. It goes off by itself only once the truck has come and gone
     *  again (you left it: walking away it'd otherwise connect and drop at the edge of its range, the cutting in and
     *  out this is for). Each tap says what it is now. */
    fun toggleAuto() {
        if (autoButton.isEmpty()) return
        if (!allowed()) { Music.toast("Shizuku isn't running: open it and tap Start"); return }
        if (callsOff) {
            autoAllowed = true; forced = false; sawCar = false
            Music.toast("Android Auto on: the truck can connect for its map and calls. The music stays on your headphones")
            SoundLog.note("Connect Android Auto: the truck may connect for calls and Android Auto")
        } else if (!wasElsewhere) {   // (no headphones, as update last saw)
            Music.toast("Android Auto is on: no headphones are connected, so the truck can connect and play the music")
            return
        } else {
            autoAllowed = false; forced = true
            Music.toast("Android Auto off: the truck is kept disconnected while your headphones are connected")
            SoundLog.note("Disconnect Android Auto: the truck is kept off")
        }
        update()
    }

    /** Look again (BtRoute.check calls it whenever the outputs or the lists change): switch connections off or on
     *  where they should be, and say how it stands. */
    fun update() {
        val mine = mine()
        if (!on && mine.isEmpty()) { status = ""; step = ""; autoButton = ""; return }
        listen()
        val now = SystemClock.elapsedRealtime()
        val elsewhere = BtRoute.elsewhere
        if (wasElsewhere && !elsewhere) goneAt = now
        wasElsewhere = elsewhere
        val ignored = if (IgnoreBt.on) IgnoreBt.ignored().filter { it != BtRoute.AUTO } else emptyList()
        val mediaOff = BtIgnoreRules.mediaOff(on && supported, ignored, elsewhere, goneAt, now)
        // Connected for calls now: listed for calls, or Android Auto on (then the car is, whichever it is: Android lists
        // only the calls device in use, which may be the earbuds)
        val auto = Outputs.carProjecting
        val forCalls = BtRoute.callsHere + (if (auto) ignored else emptyList())
        if (autoAllowed) {
            val carHere = ignored.any { it in forCalls }
            if (carHere) sawCar = true
            if (mediaOff.isEmpty() || (sawCar && !carHere)) {
                autoAllowed = false
                SoundLog.note("Connect Android Auto over: the truck is kept off again while your headphones are connected")
            }
        }
        if (mediaOff.isEmpty()) forced = false   // headphones gone: the next time they come, as at first
        val want = BtIgnoreRules.keptOff(mediaOff, forCalls, autoAllowed, forced)
        callsOff = want.any { BtIgnoreRules.parseOff(it).second == BtIgnoreRules.HEADSET }
        autoButton = if (on && IgnoreBt.on && ignored.isNotEmpty())
            (if (callsOff) "Android Auto off: tap to connect" else "Android Auto on: tap to disconnect") else ""
        // Headphones gone a moment ago: the truck back once they've been gone long enough, so look again then
        h.removeCallbacks(again)
        if (!elsewhere && goneAt > 0 && now - goneAt < BtIgnoreRules.ELSEWHERE_GONE_MS)
            h.postDelayed(again, BtIgnoreRules.ELSEWHERE_GONE_MS - (now - goneAt) + 200)
        val off = (want - mine.toSet()).filter { ready(it, SeamlessBtService.FORBIDDEN, now) }
        val back = (mine - want.toSet()).filter { ready(it, SeamlessBtService.ALLOWED, now) }
        // (the button's label says what's meant; the switching below catches up)
        say(mine, want)
        if (off.isEmpty() && back.isEmpty()) return
        if (!allowed()) {
            // A device kept off can't have its connections back: say so once (it'd otherwise just never connect)
            val stuck = back.map { BtIgnoreRules.parseOff(it).first }.distinct().joinToString(", ")
            if (back.isNotEmpty() && stuck != warned && (IgnoreBt.here.isNotEmpty() || !elsewhere)) {
                warned = stuck
                Music.toast("$stuck stays disconnected until Shizuku is running: open Shizuku and tap Start (or switch its Media audio and Calls on in Bluetooth settings)")
            }
            return
        }
        val s = service ?: run { bind(); return }
        if (working) return
        working = true
        val jobs = off.map { it to SeamlessBtService.FORBIDDEN } + back.map { it to SeamlessBtService.ALLOWED }
        Music.scope.launch(Dispatchers.IO) {
            val answers = jobs.map { (key, policy) ->
                val (name, profile) = BtIgnoreRules.parseOff(key)
                Triple(key, policy, runCatching { call(s, name, policy, profile) }.getOrElse { Triple(SeamlessBtService.ERROR, -1, it.toString()) })
            }
            h.post {
                working = false
                var l = mine()
                for ((key, policy, a) in answers) {
                    val (result, before, why) = a
                    val (name, profile) = BtIgnoreRules.parseOff(key)
                    val forbid = policy == SeamlessBtService.FORBIDDEN
                    val what = if (profile == BtIgnoreRules.A2DP) "Media audio" else "connection $profile (calls, phonebook, messages)"
                    when (result) {
                        SeamlessBtService.DONE -> {
                            l = if (forbid) l + key else l - key
                            SoundLog.note("Shizuku: $name's $what " + if (forbid) "off (your headphones are connected)" else "back on")
                        }
                        // Already off: switched off by you (or before), so it isn't the app's to give back
                        SeamlessBtService.SAME -> if (!forbid) l = l - key
                        SeamlessBtService.NOT_PAIRED -> if (!forbid) l = l - key else fail(key, policy, now, why, profile)
                        else -> fail(key, policy, now, why.ifEmpty { "it didn't work (it was $before)" }, profile)
                    }
                }
                setMine(l.distinct())
                if (answers.any { it.third.first == SeamlessBtService.DONE }) runCatching { BtRoute.check() } else update()
            }
        }
    }

    /** Whether to try a switch again: a minute after Media audio or calls failed; the phonebook and messages (switched as
     *  well as the phone lets them) only every half hour, as some phones refuse those for good. */
    private fun ready(key: String, policy: Int, now: Long): Boolean {
        val minor = BtIgnoreRules.parseOff(key).second.let { it != BtIgnoreRules.A2DP && it != BtIgnoreRules.HEADSET }
        return failedAt["$key:$policy"]?.let { now - it >= if (minor) 30 * RETRY_MS else RETRY_MS } ?: true
    }

    /** A switch that didn't work: tried again a minute later. Only Media audio and calls are said in Settings (the
     *  phonebook and messages are switched as well as the phone lets them, quietly). */
    private fun fail(key: String, policy: Int, now: Long, why: String, profile: Int) {
        failedAt["$key:$policy"] = now
        val name = BtIgnoreRules.parseOff(key).first
        if (profile == BtIgnoreRules.A2DP || profile == BtIgnoreRules.HEADSET) failure = "$name: $why"
        SoundLog.note("Shizuku couldn't switch $name's connection $profile: $why")
    }

    private fun say(mine: List<String>, want: List<String>) {
        val names = mine.map { BtIgnoreRules.parseOff(it).first }.distinct().joinToString(", ")
        val whole = mine.any { BtIgnoreRules.parseOff(it).second == BtIgnoreRules.HEADSET }
        val (s, next) = when {
            !on -> (if (mine.isNotEmpty()) "Start Shizuku once more so $names connects again as before " +
                "(or switch its Media audio and Calls on yourself in Bluetooth settings)" else "") to (if (mine.isNotEmpty() && !running()) "Open Shizuku" else "")
            !supported -> "Needs Android 14 or newer" to ""
            !installed() -> "Shizuku isn't installed: get it from Google Play (free), then set it up as below" to "Get Shizuku"
            !running() -> "Shizuku isn't running: open it and tap Start (again after each restart of the phone)" to "Open Shizuku"
            !allowed() -> "Radon Player isn't allowed in Shizuku yet" to "Allow in Shizuku"
            // It's Ignore Bluetooth devices' own: nothing to do without that on and a device picked
            !IgnoreBt.on -> "Ready, but Ignore Bluetooth devices is off: switch it on above (this only takes away its stutter)" to ""
            IgnoreBt.ignored().none { it != BtRoute.AUTO } -> "Ready: pick the device to ignore above (your truck's Bluetooth)" to ""
            failure.isNotEmpty() -> "Shizuku couldn't switch it: $failure" to ""
            autoAllowed -> "Android Auto allowed: $names may connect for calls and Android Auto, the music stays on your " +
                "headphones. Kept off again once it disconnects" to ""
            whole -> "Working: $names is kept disconnected while your headphones are connected, so it can't cut the music " +
                "in and out. In the truck, tap Connect Android Auto in the player's notification" to ""
            mine.isNotEmpty() -> "Working: $names has Media audio off while your headphones are connected" to ""
            want.isNotEmpty() -> "Working: keeping ${want.map { BtIgnoreRules.parseOff(it).first }.distinct().joinToString(", ")} off…" to ""
            else -> "Ready: an ignored device is kept disconnected whenever your headphones are connected" to ""
        }
        status = s; step = next
    }

    private fun bind() {
        if (binding) return
        binding = true
        runCatching {
            val args = Shizuku.UserServiceArgs(ComponentName(Music.ctx.packageName, SeamlessBtService::class.java.name))
                .daemon(false).processNameSuffix("bt").debuggable(false).version(VERSION).tag("seamless-bt")
            Shizuku.bindUserService(args, connection)
        }.onFailure { binding = false; failure = "Shizuku wouldn't start the app's part (${it.message})" }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
            h.post { binding = false; service = b?.takeIf { it.pingBinder() }; update() }
        }
        override fun onServiceDisconnected(name: ComponentName?) { h.post { binding = false; service = null } }
    }

    /** (a background thread) One of a device's connections (BluetoothProfile's number) to one policy, inside Shizuku:
     *  (result, policy before, why not). */
    private fun call(b: IBinder, name: String, policy: Int, profile: Int): Triple<Int, Int, String> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeString(name); data.writeInt(policy); data.writeInt(profile)
            b.transact(SeamlessBtService.SET, data, reply, 0)
            return Triple(reply.readInt(), reply.readInt(), reply.readString() ?: "")
        } finally { data.recycle(); reply.recycle() }
    }
}
