package org.omarchy.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The phone's way to the Discord app: the Rich Presence service inside it (see IDiscordRpcService.aidl). The Discord
 *  app must be installed, up to date and signed in on this phone; nothing is signed in to here, and what's sent is the
 *  same as the PC sends. [Discord] says what goes over it. Every call blocks, and is made from Discord's own thread. */
object DiscordLink {
    /** Off until you turn it on (the phone's is new, and needs the Discord app set up). */
    const val DEFAULT_ON = false
    /** How often it looks while there is nothing to show (ms): seldom, for the battery. */
    const val IDLE_MS = 6000L
    /** With nothing to show for this long, the Discord app is let go of (it needn't stay awake). */
    const val LET_GO_MS = 60_000L
    const val NOT_INSTALLED = "The Discord app isn't on this phone"
    const val TOO_OLD = "The Discord app on this phone is too old: update it"
    /** Reasons that are no problem to write down (Settings › Problems): Discord just isn't set up yet. */
    val QUIET = setOf(NOT_INSTALLED, TOO_OLD)

    private const val ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
    private const val PACKAGE = "com.discord"
    private const val CLOSED = "\u0000closed"

    private val frames = LinkedBlockingQueue<String>()
    @Volatile private var conn: IDiscordRpcConnection? = null
    @Volatile private var bound: ServiceConnection? = null
    @Volatile private var why = ""   // why Discord closed it

    val isOpen: Boolean get() = conn != null
    fun pid(): Long = android.os.Process.myPid().toLong()

    private val callback = object : IDiscordRpcCallback.Stub() {
        override fun onFrame(frame: String?) { if (frame != null) frames.offer(frame) }
        override fun onClose(code: Int, reason: String?) {
            why = if (code == 4000) "Discord doesn't know this application ID" else "Discord closed the connection" + (reason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
            frames.offer(CLOSED)
        }
    }

    /** Connects as the application with this ID and waits until Discord is ready; why it couldn't is thrown. */
    fun open(appId: String) {
        close()
        val ctx: Context = Music.ctx.applicationContext
        val intent = Intent(ACTION).setPackage(PACKAGE)
        if (ctx.packageManager.resolveService(intent, 0) == null) {
            val there = runCatching { ctx.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
            throw IOException(if (there) TOO_OLD else NOT_INSTALLED)
        }
        val up = CountDownLatch(1)
        var binder: IBinder? = null
        val sc = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) { binder = service; up.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) { why = "The Discord app stopped"; frames.offer(CLOSED) }
            override fun onBindingDied(name: ComponentName?) { why = "The Discord app stopped"; frames.offer(CLOSED) }
            override fun onNullBinding(name: ComponentName?) { up.countDown() }
        }
        frames.clear(); why = ""
        val ok = try { ctx.bindService(intent, sc, Context.BIND_AUTO_CREATE) } catch (e: SecurityException) { false }
        bound = sc   // (even a refused bind stays registered until it's unbound)
        if (!ok) { close(); throw IOException("Android didn't let the app reach Discord") }
        if (!up.await(15, TimeUnit.SECONDS) || binder == null) { close(); throw IOException("The Discord app didn't answer") }
        val c = try {
            IDiscordRpcService.Stub.asInterface(binder).connect(appId.toLongOrNull() ?: 0L, "1", callback)
        } catch (e: Exception) { close(); throw IOException("The Discord app wouldn't connect: ${e.message ?: e.javaClass.simpleName}") }
        if (c == null) { close(); throw IOException("The Discord app wouldn't connect") }
        conn = c
        // READY: signed in and listening (not signed in, or its activity sharing refused, it closes instead)
        try {
            while (true) {
                val f = next(20_000)
                val o = runCatching { JSON.parseToJsonElement(f) as JsonObject }.getOrNull() ?: continue
                if (o["evt"].str == "READY") break
                if (o["evt"].str == "ERROR") throw DiscordRefused(message(o))
            }
        } catch (e: Exception) { close(); throw e }
    }

    fun close() {
        val c = conn; conn = null
        runCatching { c?.disconnect() }
        val sc = bound; bound = null
        if (sc != null) runCatching { Music.ctx.applicationContext.unbindService(sc) }
        frames.clear()
    }

    /** Sends a command; Discord's answer to it back (its refusal is thrown as [DiscordRefused]). */
    fun command(frame: JsonObject): JsonObject {
        val c = conn ?: throw IOException("Discord isn't open")
        val nonce = frame["nonce"].str
        try { c.sendFrame(frame.toString()) } catch (e: Exception) { throw IOException("Discord closed the connection") }
        while (true) {
            val o = runCatching { JSON.parseToJsonElement(next(12_000)) as JsonObject }.getOrNull() ?: continue
            if (nonce != null && o["nonce"].str != nonce) continue   // something else it said meanwhile
            if (o["evt"].str == "ERROR") throw DiscordRefused(message(o))
            return o
        }
    }

    private fun message(o: JsonObject) = (o["data"] as? JsonObject)?.get("message").str ?: o["message"].str ?: "Discord said no"

    private fun next(ms: Long): String {
        val f = frames.poll(ms, TimeUnit.MILLISECONDS) ?: throw IOException("The Discord app didn't answer (is it signed in?)")
        if (f == CLOSED) throw IOException(why.ifEmpty { "Discord closed the connection" })
        return f
    }
}
