package org.omarchy.music

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.RandomAccessFile

/** The PC's way to the Discord app: its Rich Presence socket on this computer (a named pipe on Windows, a socket file
 *  on Linux), as the Python app does. [Discord] says what goes over it. */
object DiscordLink {
    /** On at first on the PC (as it has always been). */
    const val DEFAULT_ON = true
    /** How often it looks while there is nothing to show (ms). */
    const val IDLE_MS = 1500L
    /** The PC keeps the connection (0: never let go of). */
    const val LET_GO_MS = 0L
    /** Reasons that are no problem to write down (Settings › Problems). */
    val QUIET = emptySet<String>()

    private val windows = System.getProperty("os.name").lowercase().contains("win")
    private var pipe: RandomAccessFile? = null
    private var sock: java.nio.channels.SocketChannel? = null

    val isOpen: Boolean get() = pipe != null || sock != null
    fun pid(): Long = ProcessHandle.current().pid()

    private fun paths(): List<String> = if (windows) (0 until 10).map { "\\\\.\\pipe\\discord-ipc-$it" } else {
        val bases = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").mapNotNull { System.getenv(it) } + "/tmp"
        val subs = listOf("", "app/com.discordapp.Discord/", "app/dev.vencord.Vesktop/", "snap.discord/")
        (0 until 10).flatMap { i -> bases.distinct().flatMap { b -> subs.map { s -> "${b.trimEnd('/')}/${s}discord-ipc-$i" } } }
    }

    /** Connects as the application with this ID; Discord not open, or its refusal (a wrong ID), is thrown. */
    fun open(appId: String) {
        for (p in paths()) {
            runCatching {
                if (windows) pipe = RandomAccessFile(p, "rw")
                else if (File(p).exists()) sock = java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX)
                    .apply { connect(java.net.UnixDomainSocketAddress.of(p)) }
            }
            if (pipe != null || sock != null) break
        }
        if (pipe == null && sock == null) throw java.io.IOException("Discord isn't open")
        send(0, buildJsonObject { put("v", 1); put("client_id", appId) })
        reply()   // READY, or an error (a wrong application ID: "Invalid Client ID")
    }

    fun close() {
        runCatching { pipe?.close() }; runCatching { sock?.close() }
        pipe = null; sock = null
    }

    /** Sends a command; Discord's answer back (its refusal is thrown as [DiscordRefused]). */
    fun command(frame: JsonObject): JsonObject { send(1, frame); return reply() }

    private fun send(op: Int, data: JsonObject) {
        val body = data.toString().toByteArray()
        val frame = java.nio.ByteBuffer.allocate(8 + body.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(op).putInt(body.size).put(body).array()
        pipe?.write(frame) ?: sock?.write(java.nio.ByteBuffer.wrap(frame))
    }

    private fun read(n: Int): ByteArray {
        val out = ByteArray(n); var got = 0
        while (got < n) {
            val k = pipe?.read(out, got, n - got) ?: sock!!.read(java.nio.ByteBuffer.wrap(out, got, n - got))
            if (k <= 0) throw java.io.IOException("Discord closed the connection")
            got += k
        }
        return out
    }

    private fun reply(): JsonObject {
        val head = java.nio.ByteBuffer.wrap(read(8)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val op = head.int; val n = head.int
        val data = runCatching { JSON.parseToJsonElement(String(read(n))) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
        if (op == 2 || data["evt"].str == "ERROR") {
            val msg = (data["data"] as? JsonObject)?.get("message").str ?: data["message"].str ?: "Discord said no"
            throw DiscordRefused(if (op == 2) "Discord closed the connection: $msg" else msg)
        }
        return data
    }
}
