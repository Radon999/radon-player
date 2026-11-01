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
}
