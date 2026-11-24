package org.omarchy.music

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Parcel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Ignore Bluetooth devices with Shizuku, the part that runs inside Shizuku (its own small process, as the phone's
 *  "shell", the identity a computer gets over USB debugging): it switches one of a device's connections (Media audio,
 *  or its calls, phonebook and messages) off or on, as the switches in Android's Bluetooth settings do. Apps can't do
 *  that themselves (it needs BLUETOOTH_PRIVILEGED); the shell has it from Android 13 on. Android 14 on: before that,
 *  Bluetooth hands its connections only to real apps. Started and spoken to by [SeamlessBt]; nothing else of the app
 *  runs here (it's not an app process: no Music, no Prefs). One call: [SET] a device (by its name) and a profile
 *  (BluetoothProfile's number) to a policy; the answer is what happened, what it was before, and why not. */
class SeamlessBtService @JvmOverloads constructor(private val ctx: Context? = null) : android.os.Binder() {
    companion object {
        const val SET = 1
        /** Disconnect a device now (every connection, and the link itself). */
        const val DROP = 2
        /** Connect a device now (whatever of it may connect). */
        const val CONNECT = 3
        /** Android Auto stopped (1: it can't connect the truck from the background) or allowed to start again (0). */
        const val AUTO = 4
        /** USB debugging on (Developer options; no cable needed): Shizuku, started through Wireless debugging, lives in
         *  Android's debugging service, which stops when neither kind of debugging is on: the Wi-Fi going switches
         *  Wireless debugging off, and Shizuku stopped with it. With USB debugging on the service stays. */
        const val USB_DEBUG = 5
        /** Shizuku's own "stop" (the process ends). */
        const val DESTROY = 16777115
        const val DONE = 1
        const val SAME = 2         // it was like that already
        const val NOT_PAIRED = 3   // no paired device by that name
        const val ERROR = 0
        const val ALLOWED = 100    // BluetoothProfile.CONNECTION_POLICY_ALLOWED
        const val FORBIDDEN = 0    // ... _FORBIDDEN
        const val ANDROID_AUTO = "com.google.android.projection.gearhead"
    }

    private val proxies = java.util.concurrent.ConcurrentHashMap<Int, BluetoothProfile>()
    /** Connections this phone doesn't have (no answer in 5 s): not asked for again, so one doesn't hold the rest up. */
    private val missing = java.util.Collections.synchronizedSet(HashSet<Int>())
    private var adapter: android.bluetooth.BluetoothAdapter? = null

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val r = when (code) {
            DESTROY -> { System.exit(0); return true }
            SET -> {
                val name = data.readString() ?: ""
                val policy = data.readInt()
                val profile = data.readInt()
                runCatching { set(name, policy, profile) }
            }
            DROP, CONNECT -> { val name = data.readString() ?: ""; runCatching { link(name, code == CONNECT) } }
            AUTO -> { val stop = data.readInt() == 1; val user = data.readInt(); runCatching { auto(stop, user) } }
            USB_DEBUG -> runCatching {
                val code = Runtime.getRuntime().exec(arrayOf("settings", "put", "global", "adb_enabled", "1")).waitFor()
                Answer(if (code == 0) DONE else ERROR, code, if (code == 0) "" else "Android didn't switch USB debugging on ($code)")
            }
            else -> return super.onTransact(code, data, reply, flags)
        }.getOrElse { Answer(ERROR, -1, it.cause?.toString() ?: it.toString()) }
        reply?.writeInt(r.result); reply?.writeInt(r.before); reply?.writeString(r.why)
        return true
    }

    private class Answer(val result: Int, val before: Int, val why: String)

    /** A paired device by the name Android lists it under (its own, or the one you gave it), as the app's list has it. */
    @SuppressLint("MissingPermission")
    private fun find(name: String): Pair<BluetoothDevice?, String> {
        val all = adapter()?.bondedDevices.orEmpty()
        fun names(d: BluetoothDevice) = listOfNotNull(runCatching { d.alias }.getOrNull(), runCatching { d.name }.getOrNull()).map { it.trim() }
        val dev = all.firstOrNull { d -> names(d).any { it.equals(name.trim(), ignoreCase = true) } }
        return dev to if (dev != null) "" else "no paired device is called $name (paired: " +
            all.mapNotNull { names(it).firstOrNull() }.joinToString(", ").ifEmpty { "none" } + ")"
    }

    @SuppressLint("MissingPermission")   // the shell has BLUETOOTH_CONNECT and BLUETOOTH_PRIVILEGED
    @Synchronized private fun set(name: String, policy: Int, profile: Int): Answer {
        val p = proxy(profile) ?: return Answer(ERROR, -1, "this phone has no Bluetooth connection $profile")
        val (dev, why) = find(name)
        dev ?: return Answer(NOT_PAIRED, -1, why)
        // (system calls, not in the apps' SDK: by name)
        val get = p.javaClass.getMethod("getConnectionPolicy", BluetoothDevice::class.java)
        val put = p.javaClass.getMethod("setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
        val before = get.invoke(p, dev) as Int
        if (before == policy) return Answer(SAME, before, "")
        val ok = put.invoke(p, dev, policy) as Boolean
        return Answer(if (ok) DONE else ERROR, before, if (ok) "" else "Bluetooth didn't take the change")
    }

    /** The device disconnected now, every connection and the link itself (BluetoothDevice.disconnect), or connected
     *  (connect: whatever of it is allowed to). */
    @Synchronized private fun link(name: String, connect: Boolean): Answer {
        val (dev, why) = find(name)
        dev ?: return Answer(NOT_PAIRED, -1, why)
        val r = dev.javaClass.getMethod(if (connect) "connect" else "disconnect").invoke(dev) as Int
        return Answer(if (r == 0) DONE else ERROR, r, if (r == 0) "" else "Bluetooth answered $r")   // 0: BluetoothStatusCodes.SUCCESS
    }

    /** Android Auto stopped (as Settings › Apps › Force stop does: it then can't start by itself, so the truck isn't
     *  connected from the background for it), or allowed to start again (Android takes it out of its stopped state;
     *  it starts as usual when the truck connects). */
    private fun auto(stop: Boolean, user: Int): Answer {
        if (stop) {
            val p = Runtime.getRuntime().exec(arrayOf("am", "force-stop", "--user", user.toString(), ANDROID_AUTO))
            val code = p.waitFor()
            return Answer(if (code == 0) DONE else ERROR, code, if (code == 0) "" else "Android couldn't stop Android Auto ($code)")
        }
        val pm = Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null)
        pm.javaClass.getMethod("setPackageStoppedState", String::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(pm, ANDROID_AUTO, false, user)
        return Answer(DONE, 0, "")
    }

    private var linked = false

    /** The phone's Bluetooth, asked for as the shell itself ("com.android.shell" with the shell's own uid, which
     *  Bluetooth checks against the caller). */
    @Synchronized private fun adapter(): android.bluetooth.BluetoothAdapter? {
        adapter?.let { return it }
        val c = ctx ?: throw IllegalStateException("Shizuku is too old (update it)")
        // Bluetooth is one of Android's updatable parts: an app is linked to it as it starts (ActivityThread does
        // that), but this process isn't started as an app, so it's linked here, once (else "BluetoothServiceManager is
        // null" and no adapter). Allowed here: Shizuku's processes have no limits on Android's inner calls.
        if (!linked) {
            runCatching { Class.forName("android.app.ActivityThread").getMethod("initializeMainlineModules").invoke(null) }
            linked = true
        }
        val shell = c.createPackageContext("com.android.shell", 0)
        return shell.getSystemService(BluetoothManager::class.java)?.adapter.also { adapter = it }
    }

    /** One of Bluetooth's connections (A2DP, HEADSET…), once each; its answer comes on this process's main thread. */
    private fun proxy(profile: Int): BluetoothProfile? {
        proxies[profile]?.let { return it }
        if (profile in missing) return null
        val ad = adapter() ?: return null
        val shell = ctx!!.createPackageContext("com.android.shell", 0)
        val got = CountDownLatch(1)
        val asked = ad.getProfileProxy(shell, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) { proxies[profile] = proxy; got.countDown() }
            override fun onServiceDisconnected(p: Int) { proxies.remove(profile) }
        }, profile)
        if (asked) got.await(5, TimeUnit.SECONDS)
        return proxies[profile] ?: run { missing += profile; null }
    }
}
