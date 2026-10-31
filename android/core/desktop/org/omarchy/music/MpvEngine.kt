package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicInteger

/** The PC's engine: one mpv (the Python app's own, from its installer, or mpv on the PATH) running idle and told
 *  what to play over its JSON pipe. The sound settings (EQ, Boost, Detail, Tilt, Balance, crossfeed, mono, Clip guard)
 *  become mpv's audio filters, rebuilt whenever they change; the song's own volume and ReplayGain go in as a gain. */
class MpvEngine(private val main: Boolean = true) : Engine {
    private val windows = System.getProperty("os.name").lowercase().contains("win")
    private val id = COUNT.incrementAndGet()
    private val pipe = if (windows) "\\\\.\\pipe\\radon-mpv-$id-${ProcessHandle.current().pid()}"
        else File(System.getProperty("java.io.tmpdir"), "radon-mpv-$id-${ProcessHandle.current().pid()}.sock").path
    private var proc: Process? = null
    private var out: java.io.OutputStream? = null
    private val lock = Any()
    /** Commands go out on mpv's own writer thread, in order, so the window never waits on mpv. */
    private val connected = java.util.concurrent.CountDownLatch(1)
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "mpv-$id-out").apply { isDaemon = true } }

    @Volatile private var missing = false
    @Volatile private var paused = true
    @Volatile private var idle = true
    @Volatile private var eof = false
    @Volatile private var pos = 0.0
    @Volatile private var dur = 0.0
    @Volatile private var path: String? = null
    @Volatile private var rate = 0
    @Volatile private var bits = 0
    @Volatile private var vol = 1f
    @Volatile private var filters = ""
    private var sounding: (() -> Unit)? = null

    /** The volume you set in the app (0 to 1), on top of crossfade's. */
    @Volatile var master = level
        set(v) { field = v; send("set_property", "volume", (vol * v * 100).toDouble()) }

    init { synchronized(all) { all += this }; start() }

    private fun mpvPath(): String = Bins.mpv

    private fun start() {
        val p = try {
            ProcessBuilder(mpvPath(), "--idle=yes", "--no-video", "--really-quiet", "--audio-display=no", "--keep-open=no",
                "--input-ipc-server=$pipe", "--volume=${(level * 100).toInt()}", "--replaygain=no")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: java.io.IOException) {
            // No mpv on this PC: the app stays open and says so, instead of closing
            missing = true; connected.countDown()
            logWarn("mpv isn't there", e)
            if (main) Music.scope.launch { delay(1500); Music.toast(MISSING) }
            return
        }
        proc = p
        Thread({ connect() }, "mpv-$id").apply { isDaemon = true }.start()
        if (main) Music.scope.launch { while (proc?.isAlive == true) { applySound(); delay(300) } }
    }

    private fun connect() {
        var input: java.io.InputStream? = null
        for (i in 0 until 100) {
            try {
                if (windows) {
                    // Overlapped I/O (Java's asynchronous channel): a plain handle to a pipe can't read and write at once,
                    // so with one thread waiting for mpv's next line, anything sent would wait forever (the window with it)
                    val ch = java.nio.channels.AsynchronousFileChannel.open(java.nio.file.Paths.get(pipe),
                        java.nio.file.StandardOpenOption.READ, java.nio.file.StandardOpenOption.WRITE)
                    input = object : java.io.InputStream() {
                        override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) <= 0) -1 else b[0].toInt() and 255 }
                        override fun read(b: ByteArray, off: Int, len: Int): Int =
                            runCatching { ch.read(ByteBuffer.wrap(b, off, len), 0).get() }.getOrDefault(-1).let { if (it == 0) read(b, off, len) else it }
                    }
                    out = object : java.io.OutputStream() {
                        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            val buf = ByteBuffer.wrap(b, off, len)
                            while (buf.hasRemaining()) ch.write(buf, 0).get(3, java.util.concurrent.TimeUnit.SECONDS)
                        }
                    }
                } else {
                    val ch = SocketChannel.open(StandardProtocolFamily.UNIX).apply { connect(UnixDomainSocketAddress.of(pipe)) }
                    input = Channels.newInputStream(ch); out = Channels.newOutputStream(ch)
                }
                break
            } catch (_: Exception) { Thread.sleep(100) }
        }
        connected.countDown()
        val ins = input ?: return
        for ((n, prop) in listOf("time-pos", "duration", "pause", "idle-active", "eof-reached", "path", "audio-params", "audio-device-list").withIndex())
            sendRaw("""{"command":["observe_property",${n + 1},"$prop"]}""")
        ins.bufferedReader().forEachLine { line -> runCatching { event(JSON.parseToJsonElement(line) as JsonObject) } }
    }

    private fun event(e: JsonObject) {
        when (e["event"]?.jsonPrimitive?.content) {
            "property-change" -> {
                val d = e["data"]
                when (e["name"]?.jsonPrimitive?.content) {
                    "time-pos" -> (d as? JsonPrimitive)?.doubleOrNull?.let { val was = pos; pos = it
                        if (it > was && !paused) sounding?.let { s -> sounding = null; Music.scope.launch(Dispatchers.Main) { s() } } }
                    "duration" -> dur = (d as? JsonPrimitive)?.doubleOrNull ?: 0.0
                    "pause" -> { paused = (d as? JsonPrimitive)?.booleanOrNull ?: true
                        if (main) Music.scope.launch(Dispatchers.Main) { if (Music.player === this@MpvEngine) Music.paused = paused } }
                    "idle-active" -> idle = (d as? JsonPrimitive)?.booleanOrNull ?: true
                    "eof-reached" -> eof = (d as? JsonPrimitive)?.booleanOrNull ?: false
                    "path" -> path = (d as? JsonPrimitive)?.takeIf { it.isString }?.content
                    "audio-params" -> (d as? JsonObject)?.let { a ->
                        rate = a["samplerate"]?.jsonPrimitive?.intOrNull ?: 0
                        val fmt = a["format"]?.jsonPrimitive?.content ?: ""
                        bits = when { fmt.startsWith("s16") -> 16; fmt.startsWith("s24") -> 24; fmt.startsWith("s32") || fmt.startsWith("float") -> 32; else -> 0 }
                        showFormat()
                    }
                    "audio-device-list" -> (d as? kotlinx.serialization.json.JsonArray)?.let { list -> devicesNow(list) }
                }
            }
            "end-file" -> if (main && e["reason"]?.jsonPrimitive?.content == "eof") {
                eof = true
                Music.scope.launch(Dispatchers.Main) { if (Music.player === this@MpvEngine) Music.trackEnded() }
            } else if (main && e["reason"]?.jsonPrimitive?.content == "error") {
                Music.scope.launch(Dispatchers.Main) {
                    // A hi-res (DASH) stream mpv couldn't play: CD-quality FLAC (one plain file) from then on.
                    if (Music.streaming && path?.let { it.startsWith("data:application/dash") || it.endsWith(".mpd") } == true) Music.dl.dashFailed = true
                    if (Music.streaming) Music.streamBroke() else { Music.toast("Couldn't play “${Music.current()?.title}”"); Music.paused = true }
                }
            }
            "file-loaded" -> { eof = false; showFormat()
                if (main) Music.scope.launch(Dispatchers.Main) { dur.takeIf { it > 0 }?.let { Music.duration = it } } }
        }
    }

    private var devices: Set<String>? = null   // the sound devices last seen (mpv's names)

    /** The PC's sound devices changed: one unplugged (a DAC, a deck, headphones) while bit-perfect is on turns bit-perfect
     *  off, so its exclusive hold doesn't move to the PC's other output and silence everything else. */
    private fun devicesNow(list: kotlinx.serialization.json.JsonArray) {
        val now = list.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.content }.filter { it != "auto" }.toSet()
        val was = devices
        devices = now
        if (!main || was == null || (was - now).isEmpty()) return
        Music.scope.launch(Dispatchers.Main) {
            if (Music.player !== this@MpvEngine || !Music.bitPerfect) return@launch
            Music.chooseBitPerfect(false)
            Music.toast("A sound device was unplugged, so bit-perfect is off: turn it on again in Settings › Sound quality")
        }
    }

    /** The song's format under Now playing ("FLAC · 24-bit · 96 kHz"), for the song now loaded. */
    private fun showFormat() {
        if (!main) return
        Music.scope.launch(Dispatchers.Main) {
            if (Music.player !== this@MpvEngine) return@launch
            // The file's kind from its name (a Tidal hi-res stream's .mpd is FLAC; a web address with no kind shows none)
            val ext = (path ?: "").substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").uppercase()
                .let { if (it == "MPD") "FLAC" else it }.takeIf { it.length in 2..4 && it.all { c -> c.isLetterOrDigit() } } ?: ""
            Music.nowFormat = listOf(ext,
                if (bits > 0) "$bits-bit" else "", if (rate > 0) "${"%.1f".format(rate / 1000.0).removeSuffix(".0")} kHz" else "")
                .filter { it.isNotEmpty() }.joinToString(" · ")
            Music.nowFormatOf = Music.current()?.id ?: ""
        }
    }


    private fun sendRaw(json: String) {
        runCatching { writer.execute { connected.await(10, java.util.concurrent.TimeUnit.SECONDS); synchronized(lock) { runCatching { out?.write((json + "\n").toByteArray()); out?.flush() } } } }
    }

    private fun send(vararg cmd: Any?) {
        val arr = JsonArray(cmd.map { when (it) { null -> kotlinx.serialization.json.JsonNull; is Number -> JsonPrimitive(it)
            is Boolean -> JsonPrimitive(it); else -> JsonPrimitive(it.toString()) } })
        sendRaw(JsonObject(mapOf("command" to arr)).toString())
    }

    override val isPlaying get() = !paused && !idle && !eof
    override val positionMs get() = (pos * 1000).toLong()
    override val durationMs get() = (dur * 1000).toLong()
    override val state get() = when { idle && path == null -> Engine.IDLE; eof -> Engine.ENDED; else -> Engine.READY }
    override val hasItem get() = path != null && !idle
    override val currentUri get() = path
    override var volume: Float
        get() = vol
        set(v) { vol = v; send("set_property", "volume", (v * master * 100).toDouble()) }
    override var playWhenReady: Boolean
        get() = !paused
        set(v) { paused = !v; send("set_property", "pause", !v) }

    override fun play() { playWhenReady = true }
    override fun pause() { playWhenReady = false }
    override fun seekTo(ms: Long) { pos = ms / 1000.0; send("seek", ms / 1000.0, "absolute") }

    override fun load(item: PlayItem, startMs: Long) {
        if (missing) { if (main) Music.toast(MISSING); return }
        eof = false; pos = startMs / 1000.0; path = item.uri
        send("set_property", "start", if (startMs > 0) "+${startMs / 1000.0}" else "none")
        send("loadfile", playable(item.uri), "replace")
    }

    /** What mpv is given to play: Tidal's hi-res streams come as a DASH manifest inside a data: address, which mpv
     *  can't open (it stopped those songs with "Lost the stream", one after another): the manifest goes into a file
     *  of its own (.mpd), which it plays. */
    private fun playable(uri: String): String {
        val tag = "data:application/dash+xml;base64,"
        if (!uri.startsWith(tag)) return uri.removePrefix("file://")
        return runCatching {
            val f = File(System.getProperty("java.io.tmpdir"), "radon-stream-${uri.hashCode().toUInt()}.mpd")
            if (!f.exists()) f.writeBytes(java.util.Base64.getDecoder().decode(uri.removePrefix(tag)))
            f.deleteOnExit()
            f.path
        }.getOrDefault(uri)
    }

    override fun setSpeed(speed: Float, pitch: Float) {
        send("set_property", "speed", speed.toDouble())
        send("set_property", "audio-pitch-correction", pitch == 1f)
    }

    override val audioRate get() = rate
    override val audioBits get() = bits

    override fun tail(uri: String, startMs: Long): Engine? = runCatching {
        MpvEngine(main = false).also { x ->
            x.filters = ""; x.applyGraph(graph())
            x.volume = 0f; x.playWhenReady = false
            x.load(PlayItem(uri, "", null, "", "", "", null), startMs)
        }
    }.getOrNull()

    override fun onceSounding(run: () -> Unit) { sounding = run }

    override fun release() {
        send("quit")
        proc?.let { p -> Thread { runCatching { if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) p.destroy() } }.start() }
        proc = null
        writer.shutdown()
    }

    // ---- the sound: the settings as mpv's audio filters ----

    /** The filters for the settings as they are now (the same as the Python app's apply_eq). */
    private fun graph(): String {
        val q = Music.eqProcessor
        if (q.bypass) return ""
        val s = q.settings
        val parts = ArrayList<String>()
        val gain = 20 * kotlin.math.log10((q.songGain * q.rgGain).coerceAtLeast(1e-6))
        if (s.on) {
            val fs = Eq.filters(s)
            parts += "volume=volume=${"%.2f".format(java.util.Locale.US, Eq.preamp(fs) + gain)}dB:precision=double"
            for (f in fs) if (f.gain != 0.0) parts += when (f.kind) {
                "ls" -> "lowshelf=f=${f.hz}:t=q:w=${f.q}:g=${f.gain}"
                "hs" -> "highshelf=f=${f.hz}:t=q:w=${f.q}:g=${f.gain}"
                else -> "equalizer=f=${f.hz}:t=q:w=${f.q}:g=${f.gain}"
            }
        } else if (gain != 0.0) parts += "volume=volume=${"%.2f".format(java.util.Locale.US, gain)}dB:precision=double"
        q.loudnessDb.takeIf { it >= 0.5 }?.let { d ->   // loudness compensation: bass and treble back as the volume goes down
            parts += "lowshelf=f=90:t=q:w=0.71:g=${"%.1f".format(java.util.Locale.US, minOf(10.0, d * 0.3))}"
            parts += "highshelf=f=9000:t=q:w=0.71:g=${"%.1f".format(java.util.Locale.US, minOf(4.0, d * 0.12))}"
        }
        if (s.boost > 0) parts += "volume=volume=${s.boost}dB:precision=double"
        if (q.detail) parts += "volume=volume=-2.5dB,extrastereo=m=1.25:c=false,equalizer=f=3000:t=q:w=1.0:g=1.5,highshelf=f=10000:t=q:w=0.7:g=1.5"
        if (q.tilt > 0) parts += "lowshelf=f=1000:t=q:w=0.5:g=${-q.tilt}" else if (q.tilt < 0) parts += "highshelf=f=1000:t=q:w=0.5:g=${q.tilt}"
        if (q.balance != 0.0) {
            val b = q.balance
            parts += "pan=stereo|c0=${"%.3f".format(java.util.Locale.US, if (b > 0) 1 - b else 1.0)}*c0|c1=${"%.3f".format(java.util.Locale.US, if (b < 0) 1 + b else 1.0)}*c1"
        }
        if (q.crossfeed) parts += "crossfeed=strength=${q.crossfeedK}:range=0.5"
        if (q.mono) parts += "pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1"
        if (q.limiter || s.boost > 0) parts += "alimiter=limit=0.97:attack=1:release=80:level=disabled"
        return parts.joinToString(",")
    }

    private fun applyGraph(g: String) {
        if (g == filters) return
        filters = g
        send("set_property", "af", if (g.isEmpty()) "" else "lavfi=[$g]")
    }

    /** The settings changed (or a new song's gain): the filters again, and how mpv resamples and opens the output. */
    private fun applySound() {
        applyGraph(graph())
        val q = Music.eqProcessor
        val sw = listOfNotNull(if (Prefs.on(Prefs.HQ_RESAMPLE)) "filter_size=64,phase_shift=12,cutoff=0.97" else null,
            if (q.dither && !q.bypass) "dither_method=shibata" else null).joinToString(",")
        if (sw != resample) { resample = sw; send("set_property", "audio-swresample-o", sw) }
        val ex = Music.bitPerfect
        if (ex != exclusive) {   // bit-perfect: the output to itself at the song's rate (WASAPI exclusive on Windows)
            exclusive = ex
            send("set_property", "audio-exclusive", ex)
            send("set_property", "audio-samplerate", 0)
            send("ao-reload")
        }
    }
    private var resample = ""
    private var exclusive = false

    companion object {
        const val MISSING = "Can't play: mpv (the player) isn't on this PC. The next Radon Player installer brings its own"
        private val COUNT = AtomicInteger()
        private val all = java.util.Collections.newSetFromMap(java.util.WeakHashMap<MpvEngine, Boolean>())
        init {   // however the app ends, its players end with it (one left running would keep bit-perfect's hold on the sound card)
            Runtime.getRuntime().addShutdownHook(Thread {
                val players = synchronized(all) { all.toList() }
                players.forEach { e -> runCatching { e.proc?.destroy() } }
            })
        }
        /** The app's volume (0 to 1), for every player. */
        @Volatile var level = 1f
            private set
        fun setLevel(v: Float, save: Boolean = true) {
            level = v.coerceIn(0f, 1f)
            if (save) Prefs.setNum("pc_volume", level)
            synchronized(all) { all.toList() }.forEach { it.master = level }
            Loudness.apply()
        }
    }
}
