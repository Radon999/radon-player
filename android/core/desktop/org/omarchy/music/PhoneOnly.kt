package org.omarchy.music

// The phone's own parts the shared code talks to, as the PC has them: mostly nothing to do here. Each one is the PC's
// side of a phone feature (Auto mono, the phone's loudness, sharing the phone's files, bit-perfect on Android).

/** Auto mono goes by the phone's Bluetooth earbuds: never on the PC (Mono itself works). */
object AutoMono {
    val active = false
    fun connected(): List<String> = emptyList()
    fun check() { Music.eqProcessor.mono = Prefs.on(Prefs.MONO) }
}

/** Loudness compensation follows the app's own volume on the PC (mpv's, a cube law, as the Python app has it). */
object Loudness {
    fun apply() {
        val v = MpvEngine.level.toDouble()
        Music.eqProcessor.loudnessDb = if (Prefs.on(Prefs.LOUDNESS) && v > 0) maxOf(0.0, -60 * kotlin.math.log10(v)) else 0.0
    }
}

/** The phone serving its files to the PC or a cast device: the PC has its own files. */
object PhoneShare {
    fun start() {}
    fun stop() {}
    fun forDesktop(t: Track, desktop: String): Track? = null
    fun forCast(t: Track, host: String): String? = null
}

/** Bit-perfect on the PC is the engine's (exclusive mode): see the PC engine. */
object AudioQuality {
    fun applyBitPerfect(ctx: Ctx, on: Boolean, rate: Int, bits: Int): String = ""
}

/** Where the sound goes on the PC: Windows' default output (or Linux's default sink), as the Python app tells it:
 *  each one (the speakers, headphones, a USB DAC, Bluetooth, your car) is a key with its own EQ. */
object Outputs {
    data class Out(val key: String, val label: String, val kind: String)

    private val windows = System.getProperty("os.name").lowercase().contains("win")
    private val CAR = Regex("(?i)\\b(car|auto|sync|uconnect|carplay|toyota|ford|honda|bmw|audi|mazda|kia|hyundai|chevy|chevrolet|" +
        "nissan|tesla|subaru|jeep|dodge|mercedes|lexus|volvo|vw|volkswagen|peugeot|renault|skoda)\\b")
    @Volatile private var last = Out("speaker", "This PC's speakers", "speaker")
    @Volatile private var lastAt = 0L

    /** The output now (asking Windows takes a moment: the watcher asks, every few seconds, off the screen's thread). */
    fun now(ctx: Ctx): Out = if (lastAt == 0L) refresh() else last

    /** (blocking) Ask the PC again. */
    fun refresh(): Out {
        last = runCatching { if (windows) windowsOutput() else linuxOutput() }.getOrNull() ?: last
        lastAt = System.currentTimeMillis()
        return last
    }

    fun kindOf(key: String) = when {
        key.startsWith("bt:") || key.startsWith("bluetooth:") -> "bluetooth"
        key.startsWith("car:") -> "car"
        key.startsWith("usb:") -> "usb"
        key.startsWith("wired:") || key == "wired" -> "wired"
        else -> "speaker"
    }

    private fun run(vararg cmd: String): String = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); out
    }.getOrDefault("")

    private fun linuxOutput(): Out? {
        val out = run("wpctl", "inspect", "@DEFAULT_AUDIO_SINK@")
        val props = Regex("""(?m)^\s*\*?\s*([\w.\-]+) = "([^"]*)"""").findAll(out).associate { it.groupValues[1] to it.groupValues[2] }
        val name = props["node.description"] ?: props["device.description"] ?: props["node.nick"] ?: return null
        val bus = props["device.bus"] ?: ""; val ff = props["device.form-factor"] ?: ""
        if (bus == "bluetooth" || props["device.api"] == "bluez5")
            return Out("bt:$name", name, if (ff == "car" || CAR.containsMatchIn(name)) "car" else "bluetooth")
        if (bus == "usb") return Out("usb:$name", name, "usb")
        var port = ""
        val sink = run("pactl", "get-default-sink").trim()
        for (block in run("pactl", "list", "sinks").split("\nSink #"))
            if ("Name: $sink" in block) port = Regex("Active Port:\\s*(\\S+)").find(block)?.groupValues?.get(1) ?: ""
        if ("headphone" in port || ff == "headphone" || ff == "headset") return Out("wired:$name", "$name · headphones", "wired")
        return Out("out:$name" + if (port.isNotEmpty()) ":$port" else "", name, "speaker")
    }

    /** Windows' default output, from the registry: the active one most recently made the default. */
    private fun windowsOutput(): Out? {
        val base = "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\MMDevices\\Audio\\Render"
        val dump = run("reg", "query", base, "/s")
        // keys: ...\Render\{guid} (DeviceState, Role:0) and ...\Render\{guid}\Properties (the names)
        class Dev { var state = 0; var role = ""; val props = HashMap<String, String>() }
        val devs = LinkedHashMap<String, Dev>()
        var cur: Dev? = null; var inProps = false
        for (raw in dump.lines()) {
            val line = raw.trimEnd()
            if (line.startsWith("HKEY_")) {
                val rest = line.substringAfter("Render\\", "")
                if (rest.isEmpty()) { cur = null; continue }
                val guid = rest.substringBefore('\\')
                cur = devs.getOrPut(guid) { Dev() }
                inProps = rest.substringAfter('\\', "") == "Properties"
                continue
            }
            val d = cur ?: continue
            val m = Regex("^\\s+(\\S+)\\s+(REG_\\w+)\\s*(.*)$").find(line) ?: continue
            val (name, type, value) = m.destructured
            if (inProps) d.props[name] = value
            else when (name) {
                "DeviceState" -> d.state = value.removePrefix("0x").toIntOrNull(16) ?: 0
                "Role:0" -> d.role = value
            }
        }
        fun stamp(hex: String): String = runCatching {   // a SYSTEMTIME: when it was last made the default
            val b = hex.chunked(2).map { it.toInt(16) }
            val w = (0 until 8).map { b[it * 2] or (b[it * 2 + 1] shl 8) }
            listOf(w[0], w[1], w[3], w[4], w[5], w[6], w[7]).joinToString("") { "%05d".format(it) }
        }.getOrDefault("")
        val best = devs.values.filter { it.state == 1 }.maxByOrNull { stamp(it.role) } ?: return null
        fun p(k: String) = best.props[k]?.takeIf { it.isNotBlank() }
        val desc = p("{a45c254e-df1c-4efd-8020-67d146a850e0},2") ?: "Speakers"
        val iface = p("{b3f8fa53-0004-438e-9003-51a46e139bfc},6") ?: ""
        val form = p("{1da5d803-d492-4edd-8c23-e0c0ffee7f0e},0")?.removePrefix("0x")?.toIntOrNull(16)
        val everything = listOf("{b3f8fa53-0004-438e-9003-51a46e139bfc},2", "{a45c254e-df1c-4efd-8020-67d146a850e0},24",
            "{233164c8-1b2c-4c7d-bc68-b671687a2567},1").joinToString(" ") { p(it) ?: "" } + " " + iface
        val name = if (iface.isNotEmpty()) "$desc ($iface)" else desc
        val low = everything.lowercase()
        val kind = when {
            "bth" in low || "bluetooth" in low -> if (CAR.containsMatchIn(iface)) "car" else "bluetooth"
            "usb" in low -> "usb"
            form == 3 || form == 5 -> "wired"
            else -> "speaker"
        }
        return Out("$kind:$name", name, kind)
    }
}

/** The sound settings the shared code sets; the PC engine turns them into its filters. */
class EqProcessor {
    @Volatile var settings = EqSettings(); private set
    @Volatile var songGain = 1.0
    @Volatile var rgGain = 1.0
    @Volatile var bypass = false
    @Volatile var crossfeed = false
    @Volatile var mono = false
    @Volatile var limiter = false
    @Volatile var dither = false
    @Volatile var balance = 0.0
    @Volatile var crossfeedK = 0.3
    @Volatile var loudnessDb = 0.0
    @Volatile var tilt = 0.0
    @Volatile var detail = false
    /** Bumps when anything changes: the engine rebuilds its filters. */
    @Volatile var version = 0
    fun update(s: EqSettings) { settings = s; version++ }
}


/** The lock screen player is the phone's (Android's lock screen). */
object LockScreen {
    fun allowed(ctx: Ctx) = true
    fun askAllowed(ctx: Ctx) {}
}
