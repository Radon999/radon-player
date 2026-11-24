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
    private const val VERSION = 4      // SeamlessBtService's: a new one replaces a running old one (3: disconnect, Android Auto; 4: USB debugging)
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
    private var bindingAt = 0L
    private var working = false
    private var workingAt = 0L
    private val notedAt = HashMap<String, Long>()   // failures put in Problems, and when (once an hour each)
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
        Shizuku.addBinderDeadListener { h.post {
            service = null; binding = false
            // Shizuku gone (Wireless debugging turns off when the Wi-Fi goes, and Shizuku with it: Android Auto's own
            // Wi-Fi going when it's switched off, or walking out of range) with the truck kept off: it stays off until
            // you turn Android Auto on, which then works without Shizuku (byHand)
            if (on && (mine().isNotEmpty() || linkedOff == true)) {
                SoundLog.note("Shizuku stopped with the truck kept off")
                tell("Shizuku stopped (the Wi-Fi went, so Wireless debugging did too). The truck stays off until you tap " +
                    "Android Auto on in Radon Player, which works without Shizuku")
            }
            update()
        } }
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
     *  "Android Auto off: tap to connect" while the truck's calls connection is kept off, "Android Auto on: tap to
     *  disconnect" while it may connect; "" (no button) otherwise. [autoOn]: which of the two. */
    var autoButton by mutableStateOf("")
        private set
    val autoOn: Boolean get() = autoButton.isNotEmpty() && !callsOff
    /** The button as you last set it: null not tapped (your headphones decide: off while they're connected), true on,
     *  false off (headphones or not). Kept, so off stays off until you turn it on (the app starting again too). */
    private var autoState: Boolean?
        get() = when (Prefs.text(Prefs.IGNORE_BT_AUTO)) { "on" -> true; "off" -> false; else -> null }
        set(v) = Prefs.setText(Prefs.IGNORE_BT_AUTO, when (v) { true -> "on"; false -> "off"; null -> "" })
    private var sawCar = false        // on, and the truck has connected since
    private var callsOff = false      // the truck's calls connection is meant to be off now
    /** What the truck's link was last made (true off: disconnected, Android Auto stopped; false on), null not yet. */
    private var linkedOff: Boolean? = if (Prefs.on(Prefs.IGNORE_BT_AUTO_STOPPED)) true else null   // (Android Auto stopped by the app: remembered)

    /** The button: a tap always switches it, and says what it is now (on the screen, or as Android's own message from
     *  the notification, where the app's messages don't show), and why nothing changes yet if Shizuku can't do it. On:
     *  the truck may connect for calls and Android Auto (its map); its Media audio stays off while your headphones are
     *  connected, so the music stays on them. Off: the truck's calls connection is cut off at once. On goes back by
     *  itself once the truck has connected and disconnected again (you left it: walking away it'd otherwise connect and
     *  drop at the edge of its range, the cutting in and out this is for). */
    fun toggleAuto() {
        if (autoButton.isEmpty()) return
        autoState = callsOff   // off: now on; on: now off
        sawCar = false
        val nowOn = autoState == true
        val what = if (nowOn) "Android Auto on: the truck can connect for its map and calls" +
            (if (wasElsewhere) ", the music stays on your headphones" else "")
            else "Android Auto off: the truck is disconnected and kept off, Android Auto stopped"
        val problem = when {
            nowOn && !allowed() -> if (byHand()) "Shizuku isn't running, so it's done by hand: Android Auto was opened (so it can start " +
                "again), and in Bluetooth settings tap ⚙ by ${IgnoreBt.ignored().firstOrNull { it != BtIgnoreRules.AUTO } ?: "the truck"} " +
                "and switch Calls and Media audio on" else "Shizuku isn't running: open Radon Player and tap Android Auto on there to get the truck back"
            !running() -> "Shizuku isn't running, so the truck can't be switched yet: open Shizuku and tap Start"
            !allowed() -> "Radon Player isn't allowed in Shizuku yet (Settings › Playback)"
            else -> ""
        }
        tell(if (problem.isEmpty()) what else "$what. But $problem")
        SoundLog.note("Android Auto button: ${if (nowOn) "on" else "off"}" + if (problem.isNotEmpty()) " ($problem)" else "")
        update()
    }

    /** A message you see: the app's own while it's on the screen, Android's otherwise (a tap in the notification). */
    private fun tell(text: String) {
        if (AppVisible.on) Music.toast(text)
        else runCatching { android.widget.Toast.makeText(Music.ctx, text, android.widget.Toast.LENGTH_LONG).show() }
    }

    /** Android Auto on with Shizuku not running (it stopped with the Wi-Fi): the truck let back without it. Android
     *  Auto's own screen opened (an app opened is let start by itself again, which the force-stop took away), then
     *  Bluetooth settings, to switch the truck's Calls and Media audio back on (only Shizuku could do that for you).
     *  Only while the app is on the screen (Android doesn't let an app open screens from behind). Whether it did. */
    private fun byHand(): Boolean {
        if (!AppVisible.on) return false
        val c = Music.ctx
        val pm = c.packageManager
        val auto = SeamlessBtService.ANDROID_AUTO
        val open = pm.getLaunchIntentForPackage(auto)
            ?: pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(auto), 0).firstOrNull { it.activityInfo.exported }
                ?.let { Intent(Intent.ACTION_MAIN).setClassName(auto, it.activityInfo.name) }
        open?.let { runCatching { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        runCatching { c.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        SoundLog.note("Android Auto on without Shizuku: Android Auto opened" + (if (open == null) " (couldn't find its screen)" else "") + ", Bluetooth settings shown")
        return true
    }

    private var usbDebugAsked = false
    /** USB debugging switched on once (no cable needed) while this is on and Shizuku runs: Shizuku, started through
     *  Wireless debugging, lives in Android's debugging service, which stops once neither kind of debugging is on, so
     *  the Wi-Fi going (which switches Wireless debugging off) stopped Shizuku, and the truck stayed kept off. With USB
     *  debugging on the service stays, Wi-Fi or not, any network, mobile data as it is. */
    private fun keepShizuku(now: Long) {
        if (!on || usbDebugAsked || !allowed()) return
        val usb = runCatching { android.provider.Settings.Global.getInt(Music.ctx.contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) }.getOrDefault(1)
        if (usb == 1) return
        val s = service ?: run { bind(now); return }
        usbDebugAsked = true
        Music.scope.launch(Dispatchers.IO) {
            val (r, _, why) = runCatching { ask(s, SeamlessBtService.USB_DEBUG) {} }.getOrElse { Triple(SeamlessBtService.ERROR, -1, it.toString()) }
            h.post {
                SoundLog.note(if (r == SeamlessBtService.DONE) "USB debugging switched on (Shizuku keeps running without Wi-Fi)" else "USB debugging not switched on: $why")
                if (r == SeamlessBtService.DONE) tell("USB debugging switched on, so Shizuku keeps running when the Wi-Fi goes (no cable needed)")
            }
        }
    }

    /** Look again (BtRoute.check calls it whenever the outputs or the lists change): switch connections off or on
     *  where they should be, and say how it stands. */
    fun update() {
        val mine = mine()
        // Switched off with Android Auto stopped by it: let it start again, and connect the truck
        if (!on && linkedOff == true && supported) relink(false, IgnoreBt.ignored().filter { it != BtIgnoreRules.AUTO })
        if (!on && mine.isEmpty()) { status = ""; step = ""; autoButton = ""; return }
        listen()
        val now = SystemClock.elapsedRealtime()
        val elsewhere = BtRoute.elsewhere
        if (wasElsewhere && !elsewhere) goneAt = now
        wasElsewhere = elsewhere
        val ignored = if (IgnoreBt.on) IgnoreBt.ignored().filter { it != BtIgnoreRules.AUTO } else emptyList()
        val mediaOff = BtIgnoreRules.mediaOff(on && supported, ignored, elsewhere, goneAt, now)
        // Connected for calls now: listed for calls, or Android Auto on (then the car is, whichever it is: Android lists
        // only the calls device in use, which may be the earbuds)
        val auto = Outputs.carProjecting
        val forCalls = BtRoute.callsHere + (if (auto) ignored else emptyList())
        if (autoState == true) {
            val carHere = ignored.any { it in forCalls }
            if (carHere) sawCar = true
            if (sawCar && !carHere) {
                autoState = null
                SoundLog.note("Android Auto button back off by itself: the truck disconnected (you left it)")
            }
        }
        val want = if (on && supported) BtIgnoreRules.keptOff(mediaOff, ignored, forCalls, autoState) else emptyList()
        callsOff = want.any { BtIgnoreRules.parseOff(it).second == BtIgnoreRules.HEADSET }
        autoButton = if (on && IgnoreBt.on && ignored.isNotEmpty())
            (if (callsOff) "Android Auto off: tap to connect" else "Android Auto on: tap to disconnect") else ""
        // Off: the truck disconnected outright and Android Auto stopped (switching its connections off didn't end one
        // already up, and Android Auto kept the truck connected wirelessly by itself); on again: both undone. Once each
        // time it changes (and not "on" at the start, with nothing to undo).
        if (on && supported && ignored.isNotEmpty() && callsOff != linkedOff && (callsOff || linkedOff == true)) relink(callsOff, ignored)
        // Headphones gone a moment ago: the truck back once they've been gone long enough, so look again then
        h.removeCallbacks(again)
        if (!elsewhere && goneAt > 0 && now - goneAt < BtIgnoreRules.ELSEWHERE_GONE_MS)
            h.postDelayed(again, BtIgnoreRules.ELSEWHERE_GONE_MS - (now - goneAt) + 200)
        val off = (want - mine.toSet()).filter { ready(it, SeamlessBtService.FORBIDDEN, now) }
        val back = (mine - want.toSet()).filter { ready(it, SeamlessBtService.ALLOWED, now) }
        // (the button's label says what's meant; the switching below catches up)
        say(mine, want)
        keepShizuku(now)
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
        val s = service ?: run { bind(now); return }
        // (a switch still under way: waited for, unless it's been stuck half a minute, then tried again)
        if (working && now - workingAt < 30_000L) return
        working = true; workingAt = now
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

    /** A switch that didn't work: tried again a minute later. Media audio and calls are said in Settings and put in
     *  Problems (once an hour each), with why; the phonebook and messages are switched as well as the phone lets them,
     *  quietly. */
    private fun fail(key: String, policy: Int, now: Long, why: String, profile: Int) {
        failedAt["$key:$policy"] = now
        val name = BtIgnoreRules.parseOff(key).first
        val what = if (profile == BtIgnoreRules.A2DP) "Media audio" else "calls connection ($profile)"
        SoundLog.note("Shizuku couldn't switch $name's $what: $why")
        if (profile != BtIgnoreRules.A2DP && profile != BtIgnoreRules.HEADSET) return
        failure = "$name: $why"
        if ((notedAt[key] ?: 0L).let { it == 0L || now - it >= 60 * RETRY_MS }) {
            notedAt[key] = now
            Crashes.note("Ignore Bluetooth devices: no stutter at all couldn't switch $name's $what " +
                (if (policy == SeamlessBtService.FORBIDDEN) "off" else "on") + ": $why")
        }
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
            IgnoreBt.ignored().none { it != BtIgnoreRules.AUTO } -> "Ready: pick the device to ignore above (your truck's Bluetooth)" to ""
            failure.isNotEmpty() -> "Shizuku couldn't switch it: $failure" to ""
            autoState == true -> "Android Auto on: ${IgnoreBt.ignored().filter { it != BtIgnoreRules.AUTO }.joinToString(", ")} may connect for its map and calls" +
                (if (wasElsewhere) ", the music stays on your headphones" else "") + ". Tap the Android Auto button again when you leave" to ""
            whole -> "Working: $names is kept disconnected, so it can't cut the music in and out. For its GPS, tap the " +
                "Android Auto button (in the player's notification, or below)" to ""
            mine.isNotEmpty() -> "Working: $names has Media audio off while your headphones are connected" to ""
            want.isNotEmpty() -> "Working: keeping ${want.map { BtIgnoreRules.parseOff(it).first }.distinct().joinToString(", ")} off…" to ""
            else -> "Ready: an ignored device is kept disconnected whenever your headphones are connected" to ""
        }
        status = s; step = next
    }

    private fun bind(now: Long) {
        // (asked already: waited for, unless Shizuku never answered in twenty seconds, then asked again)
        if (binding && now - bindingAt < 20_000L) return
        binding = true; bindingAt = now
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

    /** (a background thread) Any other call inside Shizuku, with what it's given: (result, a number, why not). */
    private fun ask(b: IBinder, code: Int, fill: (Parcel) -> Unit): Triple<Int, Int, String> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            fill(data)
            b.transact(code, data, reply, 0)
            return Triple(reply.readInt(), reply.readInt(), reply.readString() ?: "")
        } finally { data.recycle(); reply.recycle() }
    }

    /** The truck's link to go with the button (see update). Off: Android Auto stopped first (else it connects the truck
     *  again at once), then, once its connections are switched off (update's switching, a moment), the truck
     *  disconnected outright. On: Android Auto allowed to start again, and the truck connected (its connections are
     *  allowed again meanwhile). Needs Shizuku: without it this waits, and is done at the next look once it can be. */
    private fun relink(off: Boolean, ignored: List<String>) {
        if (!allowed()) return
        val s = service ?: run { bind(SystemClock.elapsedRealtime()); h.postDelayed(again, 2000); return }
        linkedOff = off
        Prefs.set(Prefs.IGNORE_BT_AUTO_STOPPED, off)
        val user = android.os.Process.myUid() / 100_000
        Music.scope.launch(Dispatchers.IO) {
            val notes = ArrayList<String>()
            val serious = ArrayList<String>()   // worth Problems: Android Auto not stopped, the truck not found
            fun note(what: String, a: Triple<Int, Int, String>, big: Boolean) {
                if (a.first == SeamlessBtService.DONE) return
                val line = "$what: ${a.third.ifEmpty { "it didn't work" }}"
                notes += line
                if (big || a.first == SeamlessBtService.NOT_PAIRED) serious += line
            }
            note(if (off) "stopping Android Auto" else "letting Android Auto start",
                runCatching { ask(s, SeamlessBtService.AUTO) { it.writeInt(if (off) 1 else 0); it.writeInt(user) } }
                    .getOrElse { Triple(SeamlessBtService.ERROR, -1, it.toString()) }, off)
            if (off) Thread.sleep(1500)   // the connections switched meanwhile (update), so it doesn't connect straight back
            else {
                // On: the truck's calls connection allowed again first (update's switching runs beside this, and can
                // take longer than a moment): connecting before that connected nothing, and nothing tried again, so the
                // truck never came back for Android Auto (the owner's, Shizuku running)
                val end = SystemClock.elapsedRealtime() + 15_000
                while (SystemClock.elapsedRealtime() < end && mine().any { k ->
                        BtIgnoreRules.parseOff(k).let { (n, p) -> n in ignored && p == BtIgnoreRules.HEADSET } }) Thread.sleep(300)
            }
            // (a truck out of range may answer that it isn't connected: a note in Sound lately only)
            fun link(name: String) = note((if (off) "disconnecting " else "connecting ") + name,
                runCatching { ask(s, if (off) SeamlessBtService.DROP else SeamlessBtService.CONNECT) { it.writeString(name) } }
                    .getOrElse { Triple(SeamlessBtService.ERROR, -1, it.toString()) }, false)
            for (name in ignored) link(name)
            // On, and the truck not connected for calls a few seconds on (it was busy, or still coming in range): once more
            if (!off) {
                Thread.sleep(8_000)
                if (linkedOff == false && !Outputs.carProjecting) ignored.filter { it !in BtRoute.callsHere }.forEach { link(it) }
            }
            h.post {
                SoundLog.note("Android Auto button: the truck " + (if (off) "disconnected, Android Auto stopped" else "connecting, Android Auto allowed") +
                    if (notes.isEmpty()) "" else " · " + notes.joinToString("; "))
                // Connecting a truck that's out of range can't work: only stopping and disconnecting are worth a note
                if (off && serious.isNotEmpty()) {
                    failure = serious.joinToString("; ")
                    Crashes.note("Ignore Bluetooth devices: no stutter at all, Android Auto off didn't fully work: ${serious.joinToString("; ")}")
                }
                runCatching { BtRoute.check() }
            }
        }
    }
}
