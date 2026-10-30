package org.omarchy.music

// The phone's own parts the shared code talks to, as the PC has them: mostly nothing to do here. Each one is the PC's
// side of a phone feature (Auto mono, the phone's loudness, sharing the phone's files, bit-perfect on Android).

/** Auto mono goes by the phone's Bluetooth earbuds: never on the PC (Mono itself works). */
object AutoMono {
    val active = false
    fun connected(): List<String> = emptyList()
    fun check() { Music.eqProcessor.mono = Prefs.on(Prefs.MONO) }
}

/** Loudness compensation follows the phone's volume; on the PC it follows the app's own (see the engine). */
object Loudness {
    fun apply() {}
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

/** Where the sound goes on the PC: one output, the PC's own, until the engine says more. */
object Outputs {
    data class Out(val key: String, val label: String, val kind: String)
    fun now(ctx: Ctx) = Out("pc", "This PC", "speaker")
    fun kindOf(key: String) = "speaker"
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
