package org.omarchy.music

/** Ignore Bluetooth devices: the plain rules behind it (no Android or PC parts, so they can be tested). Devices go by
 *  their names; the lists are kept as one line per name. */
object BtIgnoreRules {
    /** How many devices "seen before" are kept (the oldest drop off). */
    const val MAX_SEEN = 30

    /** A kept list ("one\ntwo") back into names. */
    fun parse(s: String): List<String> = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** Names into a kept list. */
    fun join(names: Collection<String>): String = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

    /** The devices seen before, with the ones seen now added (newest last; the oldest dropped past [max]). */
    fun remember(seen: List<String>, now: Collection<String>, max: Int = MAX_SEEN): List<String> {
        val add = now.map { it.trim() }.filter { it.isNotEmpty() && it !in seen }.distinct()
        return (seen + add).takeLast(max)
    }

    /** Where to keep the app's sound, or null to leave it where the system puts it.
     *  - [intruding]: the outputs that are taking (or could take) the sound now (the phone: its Bluetooth outputs
     *    connected; the PC: its default output).
     *  - [previous]: the output the sound was on before an ignored device came.
     *  - [available]: the outputs the app could play on now.
     *  Only when ignoring is on, an ignored device is there, and the previous output is still there (and isn't ignored). */
    fun keepOn(ignoring: Boolean, ignored: Set<String>, intruding: Collection<String>, previous: String?,
               available: Collection<String>): String? {
        if (!ignoring || ignored.isEmpty() || intruding.none { it in ignored }) return null
        if (previous.isNullOrEmpty() || previous in ignored || previous !in available) return null
        return previous
    }

    /** The output to remember as "previous": what plays now, unless an ignored device is there (then the one from
     *  before stays remembered). [natural] is where the system plays when nothing's ignored (null: nowhere to keep it). */
    fun previousAfter(ignoring: Boolean, ignored: Set<String>, intruding: Collection<String>, natural: String?,
                      previous: String?): String? =
        if (ignoring && intruding.any { it in ignored }) previous else natural?.takeIf { it !in ignored }

    /** Newest Bluetooth device wins: when outputs that [compete] are connected together, the one that connected last
     *  ([since]: when each came, by name), leaving out ignored ones (Ignore Bluetooth devices comes first). Null when
     *  fewer than two are left to choose between (nothing to decide), or the newest two came at the same moment. */
    fun newest(compete: Collection<String>, since: Map<String, Long>, ignored: Set<String>): String? {
        val left = compete.filter { it !in ignored }.distinct()
        if (left.size < 2) return null
        val by = left.sortedByDescending { since[it] ?: 0L }
        // Came together (both there when the app started): no telling which was last, so it doesn't decide
        return if ((since[by[0]] ?: 0L) == (since[by[1]] ?: 0L)) null else by[0]
    }

    /** When each output came: kept for the ones still here, now for the new ones. */
    fun arrivals(was: Map<String, Long>, here: Collection<String>, now: Long): Map<String, Long> =
        here.distinct().associateWith { was[it] ?: now }

    /** Whether Android Auto, while it's on, is the ignored car (its sound kept off, the music held on your headphones).
     *  on: Ignore Bluetooth devices; ignored: the names ignored; here: the Bluetooth devices connected now (for music or
     *  calls); isCar: a name that's a car's (learnt beside Android Auto, or plainly a car's). The ignored car counts
     *  whether its Bluetooth is still connected or not: wireless Android Auto stays up for most of a minute after the
     *  truck's Bluetooth has gone, and the music went to it then, silent in your earbuds until it ended. */
    fun autoIgnored(auto: Boolean, on: Boolean, ignored: Set<String>, here: Collection<String>, isCar: (String) -> Boolean): Boolean =
        auto && on && (AUTO in ignored || here.any { it in ignored } || ignored.any(isCar))

    /** The name Android Auto goes by in the list of devices to ignore. */
    const val AUTO = "Android Auto"

    /** The moment an ignored device comes or goes (the car connecting, Android Auto starting or ending with the car
     *  ignored): this long either side of it. Cars and earbuds send a Pause of their own then (the car as it connects
     *  or lets go, the earbuds as the car takes the sound from them), which the app can't tell from you pressing it. */
    const val STIR_MS = 6_000L

    /** Whether the connected devices changing is such a moment. all: every Bluetooth device connected (for music or
     *  calls), and Android Auto; ignored: the ignored ones of them (Android Auto when the car is ignored). Any
     *  change that brings or takes an ignored one, or any change at all while one is there (the earbuds dropping
     *  out as the car takes over). */
    fun stirs(wasAll: Set<String>, nowAll: Set<String>, wasIgnored: Set<String>, nowIgnored: Set<String>): Boolean =
        wasIgnored != nowIgnored || (wasAll != nowAll && (wasIgnored.isNotEmpty() || nowIgnored.isNotEmpty()))

    /** Whether two moments (a Pause from outside the app, the devices stirring) are close enough to be one. */
    fun near(a: Long, b: Long): Boolean = a > 0 && b > 0 && kotlin.math.abs(a - b) < STIR_MS

    /** After Android's "headphones unplugged" pause, how long it keeps looking for somewhere to play on (headphones,
     *  the car) when the devices just stirred: earbuds the car pushed off take several seconds to come back. Any other
     *  time it looks for about four seconds, as before. */
    const val NOISY_STIRRED_MS = 20_000L

    /** Ignore Bluetooth devices with Shizuku (the phone): how long your headphones must be gone before an ignored
     *  device has its Media audio back (an earbud out for a moment mustn't hand it the music and take it back). */
    const val ELSEWHERE_GONE_MS = 8_000L

    /** Ignore Bluetooth devices with Shizuku: the ignored devices to have Media audio off now. While you have
     *  something else to listen on (headphones, the car not ignored), so they never take the sound; with nothing
     *  else, theirs again (as Ignore Bluetooth devices does without Shizuku: never the phone's own speaker).
     *  elsewhereGoneAt: when the last something else went (0: never there). */
    fun mediaOff(on: Boolean, ignored: List<String>, elsewhere: Boolean, elsewhereGoneAt: Long, now: Long): List<String> =
        if (on && (elsewhere || (elsewhereGoneAt > 0 && now - elsewhereGoneAt < ELSEWHERE_GONE_MS))) ignored else emptyList()

    // Bluetooth's profiles, as Android numbers them (BluetoothProfile): the media one, and calls (Android Auto starts
    // over it; before 4.5.19 the app kept it off too, with the truck's phonebook, messages, SIM and LE Audio: those
    // are given back wherever they're still off)
    const val A2DP = 2
    const val HEADSET = 1

    /** How the app remembers a device's profile it switched off: the name alone for Media audio (as kept before
     *  calls were switched too), "name|profile" for the others. */
    fun offKey(name: String, profile: Int): String = if (profile == A2DP) name else "$name|$profile"
    fun parseOff(key: String): Pair<String, Int> {
        val p = key.substringAfterLast('|', "").toIntOrNull()
        return if (p == null) key to A2DP else key.substringBeforeLast('|') to p
    }

    /** Ignore Bluetooth devices with Shizuku: the ignored devices to have Media audio switched off now, the one thing
     *  the app keeps switched off (it outlasts Shizuku stopping, and the truck needs nothing of it: Android Auto brings
     *  its own sound). autoOn false (the Android Auto button off): every ignored device's, headphones or not; else
     *  while your headphones have the music ([mediaOff]). The calls connection is never kept off: when Shizuku had
     *  stopped, the truck then couldn't connect for Android Auto until Shizuku ran again (the owner's, needing USB). */
    fun keptOff(mediaOff: List<String>, ignored: List<String>, autoOn: Boolean?): List<String> =
        (if (autoOn == false) ignored else mediaOff).map { offKey(it, A2DP) }

    /** Whether Android Auto is kept off now (stopped, the truck disconnected once): the button off; on, not. Not
     *  tapped: while your headphones have the music and the truck isn't connected for calls already (putting the
     *  earbuds in while driving doesn't cut Android Auto off), and once kept off, until your headphones go (the truck
     *  connecting by itself for calls doesn't let Android Auto back). */
    fun autoKeptOff(mediaOff: List<String>, connectedForCalls: Set<String>, autoOn: Boolean?, keptOffAlready: Boolean): Boolean = when (autoOn) {
        false -> true
        true -> false
        null -> mediaOff.isNotEmpty() && (keptOffAlready || mediaOff.any { it !in connectedForCalls })
    }
}
