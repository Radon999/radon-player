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

    // Bluetooth's profiles, as Android numbers them (BluetoothProfile): the media one, and the ones a car connects for
    // calls (Android Auto starts over the calls one), its phonebook and its messages.
    const val A2DP = 2
    const val HEADSET = 1
    const val PBAP = 6
    const val MAP = 9
    val CALL_PROFILES = listOf(HEADSET, PBAP, MAP)

    /** How the app remembers a device's profile it switched off: the name alone for Media audio (as kept before
     *  calls were switched too), "name|profile" for the others. */
    fun offKey(name: String, profile: Int): String = if (profile == A2DP) name else "$name|$profile"
    fun parseOff(key: String): Pair<String, Int> {
        val p = key.substringAfterLast('|', "").toIntOrNull()
        return if (p == null) key to A2DP else key.substringBeforeLast('|') to p
    }

    /** Ignore Bluetooth devices with Shizuku: every (device, profile) to have switched off now. mediaOff: the devices
     *  kept from the music ([mediaOff]); their calls connection too (so the truck doesn't connect at all and can't
     *  cut the music in and out at the edge of its range), but not while Android Auto is allowed (the notification's
     *  Connect Android Auto). A device connected for calls right now (in the truck already, Android Auto on) is left
     *  connected, so putting the earbuds in doesn't cut it off; unless forced: Disconnect Android Auto was tapped. */
    fun keptOff(mediaOff: List<String>, connectedForCalls: Set<String>, autoAllowed: Boolean, forced: Boolean = false): List<String> =
        mediaOff.map { offKey(it, A2DP) } +
            (if (autoAllowed) emptyList() else mediaOff.filter { forced || it !in connectedForCalls }.flatMap { n -> CALL_PROFILES.map { offKey(n, it) } })
}
