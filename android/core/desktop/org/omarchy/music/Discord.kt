package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.RandomAccessFile

/** What you're playing on your Discord profile, as Spotify does, through the Discord app's own Rich Presence socket on
 *  this computer (nothing goes anywhere else), as the Python app does. Paused or stopped, it's cleared. With Discord
 *  closed it looks for it again every so often. */
object Discord {
    val supported = true
    private const val APP_ID = "1564098839475126292"   // the "Radon Player" app on Discord's developer portal (public)
    private const val ICON = "https://raw.githubusercontent.com/Radon999/radon-player/radon-player/docs/icon.png"
    private const val DOWNLOAD = "https://github.com/Radon999/radon-player/releases/latest"
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    private var onNow by mutableStateOf(false)
    val on: Boolean get() = onNow
    /** For Settings: "" (not tried yet), "on", or why not. */
    var status by mutableStateOf("")
        private set

    private var pipe: RandomAccessFile? = null
    private var sock: java.nio.channels.SocketChannel? = null
    private var sent: JsonObject? = null
    private var sentKey = ""

    fun init() {
        onNow = Prefs.on("discord")
        Thread({ run() }, "discord").apply { isDaemon = true }.start()
    }

    fun setOn(v: Boolean) { onNow = v; Prefs.set("discord", v) }

    private fun appId() = Music.apiKeys["discord_app_id"] ?: APP_ID

    private fun paths(): List<String> = if (windows) (0 until 10).map { "\\\\.\\pipe\\discord-ipc-$it" } else {
        val bases = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").mapNotNull { System.getenv(it) } + "/tmp"
        val subs = listOf("", "app/com.discordapp.Discord/", "app/dev.vencord.Vesktop/", "snap.discord/")
        (0 until 10).flatMap { i -> bases.distinct().flatMap { b -> subs.map { s -> "${b.trimEnd('/')}/${s}discord-ipc-$i" } } }
    }

    private fun connect() {
        for (p in paths()) {
            runCatching {
                if (windows) pipe = RandomAccessFile(p, "rw")
                else if (File(p).exists()) sock = java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX)
                    .apply { connect(java.net.UnixDomainSocketAddress.of(p)) }
            }
            if (pipe != null || sock != null) break
        }
        if (pipe == null && sock == null) throw java.io.IOException("Discord isn't open")
        send(0, buildJsonObject { put("v", 1); put("client_id", appId()) })
        reply()
    }

    private fun close() {
        runCatching { pipe?.close() }; runCatching { sock?.close() }
        pipe = null; sock = null; sent = null; sentKey = ""
    }

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
        if (op == 2 || data["evt"].str == "ERROR")
            throw IllegalStateException((data["data"] as? JsonObject)?.get("message").str ?: data["message"].str ?: "Discord said no")
        return data
    }

    private fun text(v: String?) = (v ?: "").trim().take(128).padEnd(2, '⠀')

    /** The song's page on its service, for the button. */
    private fun page(t: Track): Pair<String, String>? = when {
        t.source == "ytm" -> "YouTube Music" to "https://music.youtube.com/watch?v=${t.id}"
        t.source == "tidal" && t.tidal != null -> "Tidal" to "https://tidal.com/browse/track/${t.tidal}"
        t.source == "apple" && t.apple != null -> "Apple Music" to "https://music.apple.com/song/${t.apple}"
        t.source == "spotify" && t.spotify != null -> "Spotify" to "https://open.spotify.com/track/${t.spotify}"
        else -> null
    }

    /** What to show now (null: nothing), and a key that changes only when it should be sent again. */
    private fun wanted(): Pair<JsonObject?, String> {
        val t = Music.current()
        if (!on || t == null || Music.paused) return null to ""
        val start = System.currentTimeMillis() - (Music.position * 1000).toLong()
        val dur = Music.duration.takeIf { it > 0 } ?: t.duration
        val p = page(t)
        val act = buildJsonObject {
            put("type", 2); put("status_display_type", 0)
            put("details", text(t.title)); put("state", text(t.artist))
            putJsonObject("timestamps") { put("start", start); if (dur > 0) put("end", start + (dur * 1000).toLong()) }
            putJsonObject("assets") {
                put("large_image", if (t.thumb.startsWith("https://")) t.thumb else ICON)
                put("small_image", ICON); put("small_text", "Radon Player")
                if (t.album.isNotEmpty()) put("large_text", text(t.album))
            }
            p?.let { put("details_url", it.second) }
            put("buttons", buildJsonArray {
                p?.let { add(buildJsonObject { put("label", "Play on ${it.first}"); put("url", it.second) }) }
                add(buildJsonObject { put("label", "Get Radon Player"); put("url", DOWNLOAD) })
            })
        }
        return act to "${t.id}|${start / 3000}"   // the bar a few seconds off isn't worth an update
    }

    private fun run() {
        var last = 0L
        while (true) {
            Thread.sleep(1500)
            val (want, key) = runCatching { wanted() }.getOrElse { null to "" }
            if (key == sentKey) continue
            if (System.currentTimeMillis() - last < 4000) continue   // Discord takes 5 updates in 20 s
            last = System.currentTimeMillis()
            try {
                if (pipe == null && sock == null) { if (want == null) { sentKey = key; continue }; connect() }
                send(1, buildJsonObject {
                    put("cmd", "SET_ACTIVITY"); put("nonce", java.util.UUID.randomUUID().toString())
                    putJsonObject("args") { put("pid", ProcessHandle.current().pid()); want?.let { put("activity", it) } }
                })
                reply()
                sent = want; sentKey = key; status = "on"
            } catch (e: Exception) {
                close()
                status = (e.message ?: "Discord isn't open").take(80)
                Thread.sleep(20_000)
            }
        }
    }
}
