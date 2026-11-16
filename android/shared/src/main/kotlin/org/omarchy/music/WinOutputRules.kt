package org.omarchy.music

/** Small rules for what the PC says about the sound: which output is Windows' default, and a song's real bit depth. */
object WinOutputRules {
    /** A Windows sound device's id ("{0.0.0.00000000}.{6f2c1a…}") to the name of its registry key ("{6f2c1a…}", small
     *  letters); null when it isn't one. */
    fun guidOf(endpointId: String): String? =
        endpointId.trim().substringAfterLast('.', "").lowercase().takeIf { it.startsWith("{") && it.endsWith("}") && it.length > 10 }

    /** The device to go by, of the active ones (each: its key and when it was last made the default by hand, "" never):
     *  the one Windows names as its default when it's among them, else the one most recently made the default. */
    fun pick(active: List<Pair<String, String>>, asked: String?): String? =
        active.firstOrNull { asked != null && it.first.equals(asked, ignoreCase = true) }?.first
            ?: active.maxByOrNull { it.second }?.first

    /** The bit depth shown for the song playing. The player hands on what it decoded INTO, not what the file is: a
     *  24-bit FLAC is decoded into 32-bit numbers ("s32"), and MP3 and AAC into floating point, which is no bit depth
     *  at all. So: the file's own depth when it's known (fileBits, read from the file; 0 or null unknown); else 16 or
     *  24 as decoded, 24 for "s32" (a real 32-bit file says so itself), and nothing for floating point. */
    fun shownBits(decodedAs: String, fileBits: Int?): Int = when {
        fileBits != null && fileBits > 0 -> fileBits
        decodedAs.startsWith("s16") -> 16
        decodedAs.startsWith("s24") || decodedAs.startsWith("s32") -> 24
        else -> 0
    }
}
