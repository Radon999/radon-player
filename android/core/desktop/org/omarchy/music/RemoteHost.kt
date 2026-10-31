package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.InetSocketAddress

/** Phone remote (Settings › Play on, on the PC): the server the phone app talks to, as the Python app's. Pairing swaps
 *  the code the PC shows for a token the phone keeps; every other request carries it. Commands run on the screen's
 *  thread through the same Music the PC's own buttons use. */
object RemoteHost {
    const val PORT = 8897
    private const val SERVICE = "_omarchy-music._tcp.local."
    private const val PHONE_SHARE_PORT = 8899   // where the phone serves its own songs while it is the remote

    val supported = true
    var running by mutableStateOf(false)
        private set
    var code by mutableStateOf("")
        private set
    /** The phones paired: token → name. */
    var phones by mutableStateOf(mapOf<String, String>())
        private set
    private var misses = 0
    private var server: HttpServer? = null
    private var mdns: javax.jmdns.JmDNS? = null
    private lateinit var file: File

    fun init(ctx: Ctx) {
        file = File(ctx.filesDir, "remote-tokens.json")
        phones = runCatching { (JSON.parseToJsonElement(file.readText()) as JsonObject).mapValues { it.value.str ?: "Phone" } }.getOrDefault(emptyMap())
        if (Prefs.on("phone_remote")) runCatching { start() }
    }

    private fun saveTokens() = writeAtomic(file, JsonObject(phones.mapValues { JsonPrimitive(it.value) }).toString())

    private fun newCode() { code = "%06d".format(java.security.SecureRandom().nextInt(1_000_000)); misses = 0 }

    fun setOn(on: Boolean) {
        val ok = runCatching { if (on) start() else stop() }
            .onFailure { Music.toast("Couldn't ${if (on) "start" else "stop"} the phone remote: ${it.message ?: it.javaClass.simpleName}") }.isSuccess
        Prefs.set("phone_remote", on && ok && running)
    }

    fun forget(token: String) { phones = phones - token; saveTokens() }

    private fun start() {
        if (server != null) return
        newCode()
        val s = runCatching { HttpServer.create(InetSocketAddress("0.0.0.0", PORT), 16) }.getOrElse {
            Music.toast("Couldn't start the phone remote: ${it.message}"); return
        }
        s.createContext("/") { ex -> runCatching { handle(ex) }.onFailure { runCatching { answer(ex, 500, err(it.message ?: "error")) } } }
        s.executor = java.util.concurrent.Executors.newFixedThreadPool(4) { r -> Thread(r, "remote").apply { isDaemon = true } }
        s.start()
        server = s; running = true
        Thread({ announce() }, "remote-mdns").apply { isDaemon = true }.start()
    }

    private fun stop() {
        server?.stop(0); server = null; running = false
        runCatching { mdns?.unregisterAllServices(); mdns?.close() }; mdns = null
    }

    /** Tell the network, so the phone finds the PC without typing its address. */
    private fun announce() = runCatching {
        val addr = lanAddress() ?: return@runCatching
        val m = javax.jmdns.JmDNS.create(addr)
        m.registerService(javax.jmdns.ServiceInfo.create(SERVICE, "Radon Player on $deviceName", PORT, ""))
        mdns = m
    }

    private fun lanAddress(): java.net.InetAddress? = runCatching {
        java.net.DatagramSocket().use { it.connect(java.net.InetAddress.getByName("192.168.0.1"), 9); it.localAddress }
    }.getOrNull()?.takeIf { !it.isAnyLocalAddress } ?: runCatching { java.net.InetAddress.getLocalHost() }.getOrNull()

    private fun err(m: String) = buildJsonObject { put("error", m.take(200)) }

    private fun answer(ex: HttpExchange, code: Int, body: Any, type: String = "application/json") {
        val data = when (body) { is ByteArray -> body; else -> body.toString().toByteArray() }
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(code, data.size.toLong())
        ex.responseBody.use { it.write(data) }
    }

    private fun phone(ex: HttpExchange): Boolean {
        val auth = ex.requestHeaders.getFirst("Authorization") ?: ""
        val tok = if (auth.startsWith("Bearer ")) auth.substring(7) else ""
        return tok.isNotEmpty() && tok in phones
    }

    /** On the screen's thread, waiting for the answer. */
    private fun <T> onUi(run: () -> T): T = runBlocking { withContext(Dispatchers.Main) { run() } }

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        val q = (ex.requestURI.rawQuery ?: "").split('&').filter { '=' in it }
            .associate { java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        if (ex.requestMethod == "GET") {
            if (path == "/hello") return answer(ex, 200, buildJsonObject { put("app", "omarchy-music"); put("version", appVersion) })
            if (!phone(ex)) return answer(ex, 401, err("pair this phone first"))
            return when (path) {
                "/status" -> answer(ex, 200, onUi { status(q["qv"]) })
                "/art" -> art(q["id"] ?: "")?.let { answer(ex, 200, it, "image/jpeg") } ?: answer(ex, 404, err("no picture"))
                else -> answer(ex, 404, err("no such thing"))
            }
        }
        val raw = ex.requestBody.use { it.readNBytes(8_000_000) }
        val body = runCatching { JSON.parseToJsonElement(String(raw).ifBlank { "{}" }) as JsonObject }.getOrElse {
            return answer(ex, 400, err("that isn't JSON"))
        }
        if (path == "/pair") {
            val (c, b) = onUi { pair(body["code"].str?.trim() ?: "", body["name"].str ?: "Phone") }
            return answer(ex, c, b)
        }
        if (!phone(ex)) return answer(ex, 401, err("pair this phone first"))
        if (path == "/cmd") {
            val phoneAddr = ex.remoteAddress.address.hostAddress
            return runCatching { onUi { command(body, phoneAddr) } }.fold(
                { answer(ex, 200, buildJsonObject { put("ok", true) }) }, { answer(ex, 400, err(it.message ?: "error")) })
        }
        answer(ex, 404, err("no such thing"))
    }

    private fun pair(c: String, name: String): Pair<Int, JsonObject> {
        if (code.isEmpty() || c != code) {
            if (++misses >= 5) newCode()   // guessing: a new code
            return 403 to err("that isn't the code the desktop shows")
        }
        val tok = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also { java.security.SecureRandom().nextBytes(it) })
        phones = phones + (tok to name.take(60)); saveTokens()
        newCode()   // one code, one phone
        Music.toast("Paired with ${name.take(60)}: it can play music here now")
        return 200 to buildJsonObject { put("token", tok); put("name", deviceName) }
    }

    private fun tracksJson(ts: List<Track>) = JSON.encodeToJsonElement(ListSerializer(Track.serializer()), ts.map { it.copy(file = null) })

    private fun status(qv: String?): JsonObject {
        val m = Music
        val t = m.current()
        val version = java.security.MessageDigest.getInstance("SHA-1").digest(m.queue.joinToString("|") { "${it.id}:${it.queued}" }.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        val dj = m.shownDj()
        return buildJsonObject {
            put("index", m.index); put("paused", m.paused); put("position", m.position)
            put("duration", m.duration.takeIf { it > 0 } ?: t?.duration ?: 0.0); put("repeat", m.repeat)
            put("liked", t != null && t.id in m.liked); put("radio", false); put("qv", version)
            put("volume", (MpvEngine.level * 100).toInt()); put("muted", MpvEngine.level == 0f)
            put("message", ""); put("quality", if (m.nowFormatOf == t?.id) m.nowFormat else "")
            put("dj", buildJsonObject {
                put("on", dj.on); put("busy", dj.busy); put("kind", dj.kind); put("line", dj.line); put("from", dj.from)
                put("kinds", JsonObject(dj.kinds.mapValues { JsonPrimitive(it.value) }))
            })
            if (qv != version) put("queue", tracksJson(m.queue))
        }
    }

    /** A song's cover: the saved one, else the file's own. */
    private fun art(id: String): ByteArray? {
        if (id.isEmpty() || '/' in id) return null
        Music.lib.items[id]?.art?.let { File(it) }?.takeIf { it.isFile }?.let { return it.readBytes() }
        val t = Music.queue.firstOrNull { it.id == id } ?: return null
        return t.file?.let { embeddedPicture(Music.ctx, it) }
    }

    /** Whether t is a song the phone may send to play here: a service's, or one of that phone's own (streamed from it). */
    private fun fromPhone(t: Track, phone: String): Track? {
        val f = t.file ?: return t
        val u = runCatching { java.net.URI(f) }.getOrNull() ?: return null
        val ok = u.scheme == "http" && u.host == phone && u.port == PHONE_SHARE_PORT && Regex("/song/\\d+").matches(u.path ?: "")
        return if (ok) t else null
    }

    private var muteFrom = 1f

    private fun command(b: JsonObject, phone: String) {
        val m = Music
        val got: List<Track> = (b["tracks"] as? JsonArray)?.let { runCatching { JSON.decodeFromJsonElement(ListSerializer(Track.serializer()), it) }.getOrNull() } ?: emptyList()
        val tracks = got.mapNotNull { fromPhone(it, phone) }
        fun need() { if (tracks.isEmpty()) error("no songs the desktop can play") }
        when (val verb = b["verb"].str) {
            "play" -> if (m.paused) m.togglePause()
            "pause" -> if (!m.paused) m.togglePause()
            "next" -> m.next()
            "prev" -> m.previous()
            "seek" -> m.seek(maxOf(0.0, b["position"].dbl ?: 0.0))
            "repeat" -> m.cycleRepeat()
            "shuffle" -> m.shuffle()
            "like" -> m.toggleLike()
            "play_index" -> m.playIndex(b["index"].long?.toInt() ?: -1)
            "unqueue" -> m.unqueueMany((b["indices"] as? JsonArray)?.mapNotNull { it.long?.toInt() } ?: emptyList())
            "play_from" -> { need(); m.playFrom(tracks, (b["index"].long?.toInt() ?: 0).coerceIn(0, tracks.size - 1), b["radio"].bool ?: false) }
            "enqueue" -> { need(); m.enqueue(tracks, if (b["how"].str == "next") "next" else "queue") }
            "handoff" -> got.firstOrNull()?.let { t ->   // Continue where you left off: the phone stopped in this song
                val pos = b["position"].dbl ?: 0.0
                if (Prefs.on(Prefs.CONTINUE) && m.paused) m.offers.trySend(Triple("Continue “${t.title}” at ${fmtTime(pos)}, where the phone stopped?", "Play") {
                    val mine = m.fmSong(t.title, t.artist, t.thumb, m.songIndex(), t.album)
                    m.resumeAt = maxOf(0.0, pos - 2)
                    m.playFrom(listOf(mine), 0, false)
                })
            }
            "volume" -> MpvEngine.setLevel(((b["volume"].dbl ?: (MpvEngine.level * 100.0)) / 100).toFloat())
            "mute" -> if (b["muted"].bool == true) { if (MpvEngine.level > 0f) { muteFrom = MpvEngine.level; MpvEngine.setLevel(0f) } }
                else if (MpvEngine.level == 0f) MpvEngine.setLevel(muteFrom)
            "dj" -> when (b["do"].str) {
                "start" -> when {
                    b["from"].str == "all" -> m.djStart(Dj.From("all"))
                    b["tracks"] != null -> { need(); m.djStart(Dj.From("songs", name = (b["name"].str ?: "the phone's songs").take(80), songs = tracks)) }
                    else -> m.djStart()
                }
                "stop" -> m.djStop()
                "switch" -> m.djSwitch()
                "ask" -> b["text"].str?.takeIf { it.isNotBlank() }?.let { m.djSwitch(ask = it.take(200)) }
                else -> error("unknown DJ command ${b["do"].str}")
            }
            else -> error("unknown command $verb")
        }
    }
}
