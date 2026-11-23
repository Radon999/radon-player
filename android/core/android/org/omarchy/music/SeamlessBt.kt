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

/** Ignore Bluetooth devices with no gaps at all (Settings › Playback, the switch under it; the phone, Android 14 on):
 *  with Shizuku (a free app that lends other apps the powers of USB debugging) the app switches an ignored device's
 *  Media audio off while you have something else to listen on (your earbuds), so it never takes the sound, not even
 *  for the split second Android otherwise moves it there and back. With nothing else connected it's switched on again
 *  (after [BtIgnoreRules.ELSEWHERE_GONE_MS]), so the car still plays the music when it's all there is, as without
 *  Shizuku. Calls and Android Auto never change: only Media audio is switched. Switching the feature off, or a device
 *  off the list, gives Media audio back; a device whose Media audio you'd switched off yourself is left alone. The
 *  switching itself happens in [SeamlessBtService], inside Shizuku. Main thread. */
object SeamlessBt {
    const val SHIZUKU = "moe.shizuku.privileged.api"
    private const val ASK = 4517       // Shizuku's permission question
    private const val VERSION = 1      // SeamlessBtService's: a new one replaces a running old one
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

    /** Look again (BtRoute.check calls it whenever the outputs or the lists change): switch Media audio off or on where
     *  it should be, and say how it stands. */
    fun update() {
        val mine = mine()
        if (!on && mine.isEmpty()) { status = ""; step = ""; return }
        listen()
        val now = SystemClock.elapsedRealtime()
        val elsewhere = BtRoute.elsewhere
        if (wasElsewhere && !elsewhere) goneAt = now
        wasElsewhere = elsewhere
        val ignored = if (IgnoreBt.on) IgnoreBt.ignored().filter { it != BtRoute.AUTO } else emptyList()
        val want = BtIgnoreRules.mediaOff(on && supported, ignored, elsewhere, goneAt, now)
        // Headphones gone a moment ago: Media audio back once they've been gone long enough, so look again then
        h.removeCallbacks(again)
        if (!elsewhere && goneAt > 0 && now - goneAt < BtIgnoreRules.ELSEWHERE_GONE_MS)
            h.postDelayed(again, BtIgnoreRules.ELSEWHERE_GONE_MS - (now - goneAt) + 200)
        val off = (want - mine.toSet()).filter { ready(it, SeamlessBtService.FORBIDDEN, now) }
        val back = (mine - want.toSet()).filter { ready(it, SeamlessBtService.ALLOWED, now) }
        say(mine, want)
        if (off.isEmpty() && back.isEmpty()) return
        if (!allowed()) {
            // A device kept from the music can't have it back: say so once (it'd otherwise just never play music)
            val stuck = back.joinToString(", ")
            if (back.isNotEmpty() && stuck != warned && IgnoreBt.here.isNotEmpty()) {
                warned = stuck
                Music.toast("$stuck can't play music until Shizuku is running: open Shizuku and tap Start (or switch its Media audio on in Bluetooth settings)")
            }
            return
        }
        val s = service ?: run { bind(); return }
        if (working) return
        working = true
        val jobs = off.map { it to SeamlessBtService.FORBIDDEN } + back.map { it to SeamlessBtService.ALLOWED }
        Music.scope.launch(Dispatchers.IO) {
            val answers = jobs.map { (name, policy) -> Triple(name, policy, runCatching { call(s, name, policy) }
                .getOrElse { Triple(SeamlessBtService.ERROR, -1, it.toString()) }) }
            h.post {
                working = false
                var l = mine()
                for ((name, policy, a) in answers) {
                    val (result, before, why) = a
                    val forbid = policy == SeamlessBtService.FORBIDDEN
                    when (result) {
                        SeamlessBtService.DONE -> {
                            l = if (forbid) l + name else l - name
                            SoundLog.note("Shizuku: $name's Media audio " + if (forbid) "off (your headphones are connected)" else "back on")
                        }
                        // Already off: switched off by you (or before), so it isn't the app's to give back
                        SeamlessBtService.SAME -> if (!forbid) l = l - name
                        SeamlessBtService.NOT_PAIRED -> if (!forbid) l = l - name else fail(name, policy, now, why)
                        else -> fail(name, policy, now, why.ifEmpty { "it didn't work (it was $before)" })
                    }
                }
                setMine(l.distinct())
                if (answers.any { it.third.first == SeamlessBtService.DONE }) runCatching { BtRoute.check() } else update()
            }
        }
    }

    private fun ready(name: String, policy: Int, now: Long): Boolean =
        failedAt["$name:$policy"]?.let { now - it >= RETRY_MS } ?: true

    private fun fail(name: String, policy: Int, now: Long, why: String) {
        failedAt["$name:$policy"] = now
        failure = "$name: $why"
        SoundLog.note("Shizuku couldn't switch $name's Media audio: $why")
    }

    private fun say(mine: List<String>, want: List<String>) {
        val (s, next) = when {
            !on -> (if (mine.isNotEmpty()) "Start Shizuku once more so ${mine.joinToString(", ")} gets its Media audio back " +
                "(or switch it on yourself in Bluetooth settings)" else "") to (if (mine.isNotEmpty() && !running()) "Open Shizuku" else "")
            !supported -> "Needs Android 14 or newer" to ""
            !installed() -> "Shizuku isn't installed: get it from Google Play (free), then set it up as below" to "Get Shizuku"
            !running() -> "Shizuku isn't running: open it and tap Start (again after each restart of the phone)" to "Open Shizuku"
            !allowed() -> "Radon Player isn't allowed in Shizuku yet" to "Allow in Shizuku"
            // It's Ignore Bluetooth devices' own: nothing to do without that on and a device picked
            !IgnoreBt.on -> "Ready, but Ignore Bluetooth devices is off: switch it on above (this only takes away its stutter)" to ""
            IgnoreBt.ignored().none { it != BtRoute.AUTO } -> "Ready: pick the device to ignore above (your truck's Bluetooth)" to ""
            failure.isNotEmpty() -> "Shizuku couldn't switch it: $failure" to ""
            mine.isNotEmpty() -> "Working: ${mine.joinToString(", ")} has Media audio off while your headphones are connected, " +
                "so it can't take the music" to ""
            want.isNotEmpty() -> "Working: switching Media audio off for ${want.joinToString(", ")}…" to ""
            else -> "Ready: an ignored device has its Media audio switched off whenever your headphones are connected" to ""
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

    /** (a background thread) One device to one policy, inside Shizuku: (result, policy before, why not). */
    private fun call(b: IBinder, name: String, policy: Int): Triple<Int, Int, String> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeString(name); data.writeInt(policy)
            b.transact(SeamlessBtService.SET, data, reply, 0)
            return Triple(reply.readInt(), reply.readInt(), reply.readString() ?: "")
        } finally { data.recycle(); reply.recycle() }
    }
}
