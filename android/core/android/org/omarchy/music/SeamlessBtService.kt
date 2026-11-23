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
 *  "shell", the identity a computer gets over USB debugging): it switches one device's Media audio off or on, as the
 *  switch in Android's Bluetooth settings does. Apps can't do that themselves (it needs BLUETOOTH_PRIVILEGED); the shell
 *  has it from Android 13 on. Android 14 on: before that, Bluetooth hands its media connection only to real apps.
 *  Started and spoken to by [SeamlessBt]; nothing else of the app runs here (it's not an app process: no Music, no
 *  Prefs). One call: [SET] a device (by its name) to a policy; the answer is what happened, what it was before, and why
 *  not. Kept free of anything that would need the app's own process. */
class SeamlessBtService @JvmOverloads constructor(private val ctx: Context? = null) : android.os.Binder() {
    companion object {
        const val SET = 1
        /** Shizuku's own "stop" (the process ends). */
        const val DESTROY = 16777115
        const val DONE = 1
        const val SAME = 2         // it was like that already
        const val NOT_PAIRED = 3   // no paired device by that name
        const val ERROR = 0
        const val ALLOWED = 100    // BluetoothProfile.CONNECTION_POLICY_ALLOWED
        const val FORBIDDEN = 0    // ... _FORBIDDEN
    }

    @Volatile private var a2dp: BluetoothProfile? = null
    private var adapter: android.bluetooth.BluetoothAdapter? = null

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            DESTROY -> { System.exit(0); return true }
            SET -> {
                val name = data.readString() ?: ""
                val policy = data.readInt()
                val r = runCatching { set(name, policy) }.getOrElse { Answer(ERROR, -1, it.cause?.toString() ?: it.toString()) }
                reply?.writeInt(r.result); reply?.writeInt(r.before); reply?.writeString(r.why)
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }

    private class Answer(val result: Int, val before: Int, val why: String)

    @SuppressLint("MissingPermission")   // the shell has BLUETOOTH_CONNECT and BLUETOOTH_PRIVILEGED
    @Synchronized private fun set(name: String, policy: Int): Answer {
        val p = proxy() ?: return Answer(ERROR, -1, "Bluetooth's media connection couldn't be reached")
        val dev = adapter?.bondedDevices?.firstOrNull { (runCatching { it.alias }.getOrNull() ?: it.name) == name || it.name == name }
            ?: return Answer(NOT_PAIRED, -1, "no paired device is called $name")
        // (system calls, not in the apps' SDK: by name)
        val get = p.javaClass.getMethod("getConnectionPolicy", BluetoothDevice::class.java)
        val put = p.javaClass.getMethod("setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
        val before = get.invoke(p, dev) as Int
        if (before == policy) return Answer(SAME, before, "")
        val ok = put.invoke(p, dev, policy) as Boolean
        return Answer(if (ok) DONE else ERROR, before, if (ok) "" else "Bluetooth didn't take the change")
    }

    private var linked = false

    /** Bluetooth's media (A2DP) connection, asked for as the shell itself ("com.android.shell" with the shell's own
     *  uid, which Bluetooth checks against the caller), once; its answer comes on this process's main thread. */
    private fun proxy(): BluetoothProfile? {
        a2dp?.let { return it }
        val c = ctx ?: throw IllegalStateException("Shizuku is too old (update it)")
        // Bluetooth is one of Android's updatable parts: an app is linked to it as it starts (ActivityThread does
        // that), but this process isn't started as an app, so it's linked here, once (else "BluetoothServiceManager is
        // null" and no adapter). Allowed here: Shizuku's processes have no limits on Android's inner calls.
        if (!linked) {
            runCatching { Class.forName("android.app.ActivityThread").getMethod("initializeMainlineModules").invoke(null) }
            linked = true
        }
        val shell = c.createPackageContext("com.android.shell", 0)
        val ad = shell.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        adapter = ad
        val got = CountDownLatch(1)
        ad.getProfileProxy(shell, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) { a2dp = proxy; got.countDown() }
            override fun onServiceDisconnected(profile: Int) { a2dp = null }
        }, BluetoothProfile.A2DP)
        got.await(5, TimeUnit.SECONDS)
        return a2dp
    }
}
