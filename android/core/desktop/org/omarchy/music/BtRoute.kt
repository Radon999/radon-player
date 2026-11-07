package org.omarchy.music

/** Ignore Bluetooth devices, the PC's side: when the PC's default output switches to an ignored Bluetooth device, mpv is
 *  kept on the output it was on before (its audio-device), and follows the default again when the device goes or
 *  ignoring is turned off. The PC's default output is looked at every few seconds, so the device can have the sound for
 *  a moment first. Runs on the main thread (the outputs watcher, mpv's device list, the switch). */
object BtRoute {
    private var previous: String? = null            // the output's name before the ignored device came
    private var previousOut: Outputs.Out? = null    // and the output itself (for the EQ)
    private var arrived: Map<String, Long> = emptyMap()   // when each Bluetooth device came (Newest Bluetooth device wins)

    /** Where the music's kept now (for the EQ: Outputs.now): null while it isn't. */
    @Volatile var routed: Outputs.Out? = null
        private set

    /** Look again: remember a Bluetooth default output, keep mpv off an ignored one if its previous output is still there. */
    fun check() {
        val sys = Outputs.system(Music.ctx)
        val bt = if (sys.kind == "bluetooth" || sys.kind == "car") listOf(sys.label) else emptyList()
        IgnoreBt.saw(bt)
        val ignored = IgnoreBt.ignored().toSet()
        val ignoring = IgnoreBt.on
        val now = listOf(sys.label)
        val devices = MpvEngine.deviceList
        // mpv's name for a device: Windows' own name for it (its description) should be the same as ours
        fun mpvName(label: String) = (devices.firstOrNull { it.second == label } ?: devices.firstOrNull { it.second.equals(label, true) })?.first
        val available = previous?.takeIf { mpvName(it) != null }?.let { listOf(it) } ?: emptyList()
        // Newest Bluetooth device wins: of the Bluetooth devices seen (the ones Windows has made the default before)
        // that mpv has now, the last to come has the music; an ignored one never does (Ignore Bluetooth devices first)
        val seenBt = IgnoreBt.seen().toSet()
        val compete = devices.map { it.second }.filter { it in seenBt }
        arrived = BtIgnoreRules.arrivals(arrived, compete, System.currentTimeMillis())
        if (IgnoreBt.newestOn) {
            val pick = BtIgnoreRules.newest(compete, arrived, if (ignoring) ignored else emptySet())
            val name = pick?.let { mpvName(it) }
            if (pick != null && name != null) {
                MpvEngine.keepOn(if (pick == sys.label) "" else name)
                routed = if (pick == sys.label) null else Outputs.Out("bt:$pick", pick, "bluetooth")
                IgnoreBt.newest(pick, compete.filter { it != pick && !(ignoring && it in ignored) }.joinToString(", "))
                IgnoreBt.report(if (ignoring && sys.label in ignored) sys.label else "", routed?.label ?: "", "")
                return
            }
        }
        IgnoreBt.newest("", "")
        val keep = BtIgnoreRules.keepOn(ignoring, ignored, now, previous, available)?.let { mpvName(it) }
        val prevName = BtIgnoreRules.previousAfter(ignoring, ignored, now, sys.label, previous)
        if (prevName != previous) { previous = prevName; previousOut = if (prevName == sys.label) sys else null }
        MpvEngine.keepOn(keep ?: "")
        routed = if (keep != null) previousOut else null
        val here = if (ignoring && sys.label in ignored) sys.label else ""
        IgnoreBt.report(here, routed?.label ?: "", if (here.isEmpty() || keep != null) "" else "the output it was on before isn't there now")
    }
}
