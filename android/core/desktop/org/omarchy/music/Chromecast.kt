package org.omarchy.music

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Cast… on the PC: the Chromecasts, Google speakers and TVs on the network (found the way the phone's Cast button
 *  finds them, by mDNS), and one of them as the player while casting. Google's Cast protocol spoken directly: a TLS
 *  connection to the device, its Default Media Receiver started, the song loaded from this PC (CastShare serves your
 *  files to it) and played, paused, sought and its volume set as the app asks. */
object Chromecast {
    data class Device(val name: String, val host: String, val port: Int)

    @Volatile private var mdns: javax.jmdns.JmDNS? = null
    private val found = java.util.concurrent.ConcurrentHashMap<String, Device>()

    /** Start looking; changed() each time the list changes. */
    fun discover(changed: (List<Device>) -> Unit) {
        Thread({
            runCatching {
                val addr = CastShare.lanAddress() ?: return@runCatching
                val m = mdns ?: javax.jmdns.JmDNS.create(addr).also { mdns = it }
                fun add(info: javax.jmdns.ServiceInfo?) {
                    val i = info ?: return
                    val host = i.inet4Addresses.firstOrNull()?.hostAddress ?: return
                    val name = i.getPropertyString("fn")?.takeIf { it.isNotBlank() } ?: i.name
                    found[i.name] = Device(name, host, i.port.takeIf { it > 0 } ?: 8009)
                    changed(found.values.sortedBy { it.name.lowercase() })
                }
                m.addServiceListener("_googlecast._tcp.local.", object : javax.jmdns.ServiceListener {
                    override fun serviceAdded(e: javax.jmdns.ServiceEvent) { m.requestServiceInfo(e.type, e.name, 3000) }
                    override fun serviceRemoved(e: javax.jmdns.ServiceEvent) { found.remove(e.name); changed(found.values.sortedBy { it.name.lowercase() }) }
                    override fun serviceResolved(e: javax.jmdns.ServiceEvent) = add(e.info)
                })
                m.list("_googlecast._tcp.local.", 4000).forEach { add(it) }
                changed(found.values.sortedBy { it.name.lowercase() })
            }
        }, "cast-find").apply { isDaemon = true }.start()
    }

    fun known() = found.values.sortedBy { it.name.lowercase() }

    /** (blocking) Connect to d and start its media receiver: the player while casting. */
    fun connect(d: Device): CastEngine = CastEngine(d).also { it.open() }
}

/** A cast device as the player (Engine): what the app asks, sent as Cast messages; where it is, from its status. */
class CastEngine(private val d: Chromecast.Device) : Engine {
    private lateinit var sock: SSLSocket
    private lateinit var out: OutputStream
    private val me = "sender-" + (1000..9999).random()
    @Volatile private var transport: String? = null
    @Volatile private var session: Long? = null
    @Volatile private var playerState = "IDLE"
    @Volatile private var idleReason = ""
    @Volatile private var at = 0.0            // seconds, at the time of the last status
    @Volatile private var atWhen = 0L
    @Volatile private var dur = 0.0
    @Volatile private var uri: String? = null
    @Volatile private var closed = false
    @Volatile private var loading = false
    private var reqId = 1
    private val ready = java.util.concurrent.CountDownLatch(1)
    private var sounding: (() -> Unit)? = null

    private val NS_CONN = "urn:x-cast:com.google.cast.tp.connection"
    private val NS_BEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
    private val NS_RECV = "urn:x-cast:com.google.cast.receiver"
    private val NS_MEDIA = "urn:x-cast:com.google.cast.media"
    private val APP = "CC1AD845"   // the Default Media Receiver

    fun open() {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {   // the devices' own certificates are self-signed
            override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        val raw = java.net.Socket().apply { connect(InetSocketAddress(d.host, d.port), 5000); soTimeout = 0 }
        sock = ctx.socketFactory.createSocket(raw, d.host, d.port, true) as SSLSocket
        sock.startHandshake()
        out = sock.outputStream
        Thread({ readLoop() }, "cast-read").apply { isDaemon = true }.start()
        send("receiver-0", NS_CONN, buildJsonObject { put("type", "CONNECT") })
        send("receiver-0", NS_RECV, buildJsonObject { put("type", "LAUNCH"); put("appId", APP); put("requestId", reqId++) })
        Thread({   // the heartbeat, and the status every second (where it is)
            var n = 0
            while (!closed) {
                Thread.sleep(1000)
                runCatching {
                    if (n++ % 5 == 0) send("receiver-0", NS_BEAT, buildJsonObject { put("type", "PING") })
                    transport?.let { if (session != null) send(it, NS_MEDIA, buildJsonObject { put("type", "GET_STATUS"); put("requestId", reqId++) }) }
                }.onFailure { if (!closed) lost() }
            }
        }, "cast-beat").apply { isDaemon = true }.start()
        if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) { release(); error("${d.name} didn't start its player") }
    }

    private fun lost() {
        if (closed) return
        closed = true
        runCatching { sock.close() }
        Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { if (Music.cast === this@CastEngine) Music.castStopped() }
    }

    // ---- the Cast messages: a 4-byte length, then a CastMessage (protobuf), its payload JSON ----

    private fun varint(b: java.io.ByteArrayOutputStream, v: Long) {
        var x = v
        while (x and 0x7FL.inv() != 0L) { b.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }
        b.write(x.toInt())
    }
    private fun field(b: java.io.ByteArrayOutputStream, no: Int, s: String) {
        val bytes = s.toByteArray(); varint(b, (no shl 3 or 2).toLong()); varint(b, bytes.size.toLong()); b.write(bytes)
    }

    @Synchronized private fun send(dest: String, ns: String, payload: JsonObject) {
        val b = java.io.ByteArrayOutputStream()
        varint(b, (1 shl 3).toLong()); varint(b, 0)            // protocol_version CASTV2_1_0
        field(b, 2, if (ns == NS_BEAT || dest == "receiver-0") "sender-0" else me)
        field(b, 3, dest); field(b, 4, ns)
        varint(b, (5 shl 3).toLong()); varint(b, 0)            // payload_type STRING
        field(b, 6, payload.toString())
        val msg = b.toByteArray()
        val len = msg.size
        out.write(byteArrayOf((len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()))
        out.write(msg); out.flush()
    }

    private fun readLoop() {
        val inp = DataInputStream(sock.inputStream)
        try {
            while (!closed) {
                val len = inp.readInt()
                val msg = ByteArray(len); inp.readFully(msg)
                var i = 0; var ns = ""; var src = ""; var payload = ""
                fun vint(): Long { var r = 0L; var s = 0; while (true) { val c = msg[i++].toInt() and 0xFF; r = r or ((c and 0x7F).toLong() shl s); if (c < 0x80) return r; s += 7 } }
                while (i < len) {
                    val key = vint().toInt(); val no = key ushr 3
                    when (key and 7) {
                        0 -> vint()
                        2 -> { val l = vint().toInt(); val s = String(msg, i, l); i += l
                            when (no) { 2 -> src = s; 4 -> ns = s; 6 -> payload = s } }
                        else -> return
                    }
                }
                runCatching { onMessage(src, ns, Json.parseToJsonElement(payload)) }
            }
        } catch (e: Exception) { lost() }
    }

    private fun onMessage(src: String, ns: String, j: JsonElement) {
        when (j["type"].str) {
            "PING" -> send(src, NS_BEAT, buildJsonObject { put("type", "PONG") })
            "CLOSE" -> if (src == transport) lost()
            "RECEIVER_STATUS" -> {
                val app = j.path("status", "applications").list.firstOrNull { it["appId"].str == APP }
                val t = app["transportId"].str
                if (t != null && t != transport) {
                    transport = t
                    send(t, NS_CONN, buildJsonObject { put("type", "CONNECT") })
                    ready.countDown()
                }
                j.path("status", "volume", "level").dbl?.let { vol = it.toFloat() }
            }
            "MEDIA_STATUS" -> {
                val s = j["status"].list.firstOrNull() ?: return
                s["mediaSessionId"].long?.let { session = it }
                val was = playerState
                s["playerState"].str?.let { playerState = it }
                idleReason = s["idleReason"].str ?: if (playerState == "IDLE") idleReason else ""
                s["currentTime"].dbl?.let { at = it; atWhen = System.currentTimeMillis() }
                s.path("media", "duration").dbl?.let { dur = it }
                if (playerState == "PLAYING") { loading = false; if (was != "PLAYING") sounding?.let { sounding = null; it() } }
            }
            "LOAD_FAILED", "INVALID_REQUEST" -> { loading = false; Music.toast("${d.name} couldn't play that song") }
        }
    }

    private fun media(type: String, extra: (kotlinx.serialization.json.JsonObjectBuilder) -> Unit = {}) {
        val t = transport ?: return; val s = session ?: return
        send(t, NS_MEDIA, buildJsonObject { put("type", type); put("mediaSessionId", s); put("requestId", reqId++); extra(this) })
    }

    // ---- Engine ----
    override val isPlaying get() = playerState == "PLAYING"
    override val positionMs: Long get() {
        val extra = if (playerState == "PLAYING") (System.currentTimeMillis() - atWhen) / 1000.0 else 0.0
        return ((at + extra) * 1000).toLong().coerceAtMost(if (dur > 0) (dur * 1000).toLong() else Long.MAX_VALUE)
    }
    override val durationMs get() = (dur * 1000).toLong()
    override val state get() = when {
        loading || playerState == "BUFFERING" || playerState == "LOADING" -> Engine.BUFFERING
        playerState == "IDLE" && idleReason == "FINISHED" -> Engine.ENDED
        playerState == "IDLE" -> Engine.IDLE
        else -> Engine.READY
    }
    override val hasItem get() = uri != null
    override val currentUri get() = uri
    private var vol = 1f
    override var volume: Float
        get() = vol
        set(v) { vol = v; runCatching { send("receiver-0", NS_RECV, buildJsonObject {
            put("type", "SET_VOLUME"); putJsonObject("volume") { put("level", v.coerceIn(0f, 1f).toDouble()) }; put("requestId", reqId++) }) } }
    override var playWhenReady = true
        set(v) { field = v; runCatching { if (v) play() else pause() } }

    override fun play() { runCatching { media("PLAY") } }
    override fun pause() { runCatching { media("PAUSE") } }
    override fun seekTo(ms: Long) { at = ms / 1000.0; atWhen = System.currentTimeMillis(); runCatching { media("SEEK") { it.put("currentTime", ms / 1000.0) } } }

    override fun load(item: PlayItem, startMs: Long) {
        val t = transport ?: return
        uri = item.uri; loading = true; idleReason = ""; at = startMs / 1000.0; atWhen = System.currentTimeMillis(); dur = 0.0
        val art = item.art?.let { a -> if (a.startsWith("http")) a else CastShare.urlFor(File(a), d.host) }
        Thread({ runCatching {
            send(t, NS_MEDIA, buildJsonObject {
                put("type", "LOAD"); put("requestId", reqId++); put("autoplay", playWhenReady); put("currentTime", startMs / 1000.0)
                putJsonObject("media") {
                    put("contentId", item.uri); put("contentType", item.mime ?: "audio/flac"); put("streamType", "BUFFERED")
                    putJsonObject("metadata") {
                        put("metadataType", 3); put("title", item.title); put("artist", item.artist); put("albumName", item.album)
                        if (art != null) put("images", buildJsonArray { add(buildJsonObject { put("url", art) }) })
                    }
                }
            })
        } }, "cast-load").apply { isDaemon = true }.start()
    }

    override fun setSpeed(speed: Float, pitch: Float) {
        if (speed != 1f) runCatching { media("SET_PLAYBACK_RATE") { it.put("playbackRate", speed.toDouble()) } }
    }
    override val audioRate = 0
    override val audioBits = 0
    override fun tail(uri: String, startMs: Long): Engine? = null
    override fun onceSounding(run: () -> Unit) { if (isPlaying) run() else sounding = run }

    /** Leave the device: its player stopped, the connection closed. */
    override fun release() {
        if (closed) return
        runCatching {
            transport?.let { media("STOP"); send(it, NS_CONN, buildJsonObject { put("type", "CLOSE") }) }
            send("receiver-0", NS_CONN, buildJsonObject { put("type", "CLOSE") })
        }
        closed = true
        runCatching { sock.close() }
    }
}

/** Your files, served to the cast device while casting (the PC's side of the phone's PhoneShare): each file gets an
 *  address with a random key, good only for this run of the app; seeking works (Range). */
object CastShare {
    private const val PORT = 8900
    private var server: com.sun.net.httpserver.HttpServer? = null
    private val files = java.util.concurrent.ConcurrentHashMap<String, File>()
    private val keys = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun lanAddress(): java.net.InetAddress? = runCatching {
        java.net.DatagramSocket().use { it.connect(java.net.InetAddress.getByName("192.168.0.1"), 9); it.localAddress }
    }.getOrNull()?.takeIf { !it.isAnyLocalAddress } ?: runCatching { java.net.InetAddress.getLocalHost() }.getOrNull()

    /** This PC's address as the device sees it (the way out to it). */
    private fun addressFor(host: String): String? = runCatching {
        java.net.DatagramSocket().use { it.connect(java.net.InetAddress.getByName(host), 8009); it.localAddress.hostAddress }
    }.getOrNull() ?: lanAddress()?.hostAddress

    @Synchronized private fun start(): Boolean {
        if (server != null) return true
        val s = runCatching { com.sun.net.httpserver.HttpServer.create(InetSocketAddress("0.0.0.0", PORT), 8) }.getOrNull() ?: return false
        s.createContext("/f/") { ex -> runCatching { serve(ex) }; runCatching { ex.close() } }
        s.executor = java.util.concurrent.Executors.newFixedThreadPool(4) { r -> Thread(r, "cast-share").apply { isDaemon = true } }
        s.start(); server = s
        return true
    }

    /** The address for file, for the device at host; null if it can't be served. */
    fun urlFor(f: File, host: String): String? {
        if (!f.isFile || !start()) return null
        val key = keys.getOrPut(f.path) { java.util.UUID.randomUUID().toString().replace("-", "") }
        files[key] = f
        val ext = f.extension.lowercase().ifEmpty { "bin" }
        return "http://${addressFor(host) ?: return null}:$PORT/f/$key.$ext"
    }

    private fun serve(ex: com.sun.net.httpserver.HttpExchange) {
        val key = ex.requestURI.path.removePrefix("/f/").substringBefore('.')
        val f = files[key]
        if (f == null || !f.isFile) { ex.sendResponseHeaders(404, -1); return }
        val size = f.length()
        val type = when (f.extension.lowercase()) {
            "mp3" -> "audio/mpeg"; "m4a", "aac", "mp4" -> "audio/mp4"; "ogg", "opus" -> "audio/ogg"; "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; else -> "audio/flac"
        }
        ex.responseHeaders.add("Content-Type", type)
        ex.responseHeaders.add("Accept-Ranges", "bytes")
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        val range = Regex("bytes=(\\d*)-(\\d*)").find(ex.requestHeaders.getFirst("Range") ?: "")
        var from = 0L; var to = size - 1
        if (range != null) {
            val (a, b) = range.destructured
            if (a.isNotEmpty()) { from = a.toLong(); if (b.isNotEmpty()) to = minOf(b.toLong(), size - 1) }
            else if (b.isNotEmpty()) from = maxOf(0, size - b.toLong())
            ex.responseHeaders.add("Content-Range", "bytes $from-$to/$size")
        }
        val n = to - from + 1
        if (ex.requestMethod == "HEAD") { ex.sendResponseHeaders(if (range != null) 206 else 200, -1); return }
        ex.sendResponseHeaders(if (range != null) 206 else 200, n)
        java.io.RandomAccessFile(f, "r").use { raf ->
            raf.seek(from)
            val buf = ByteArray(64 * 1024); var left = n
            ex.responseBody.use { o ->
                while (left > 0) {
                    val r = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (r < 0) break
                    o.write(buf, 0, r); left -= r
                }
            }
        }
    }
}
