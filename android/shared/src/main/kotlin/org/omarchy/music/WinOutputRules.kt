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

    /** What the PC's visualizer listens to (it decodes the song a second time, beside the player): your own file of
     *  the song when there is one ON THIS PC, else whatever the player itself is playing (a stream). Before, a file's
     *  name was taken even when the file was only on the phone, and then nothing could be decoded. */
    fun spectrumSource(files: List<String?>, playing: String?, isHere: (String) -> Boolean): String? =
        files.firstOrNull { !it.isNullOrBlank() && !it.startsWith("http") && !it.startsWith("content:") && isHere(it) } ?: playing?.takeIf { it.isNotBlank() }

    /** How ffmpeg is asked to decode it for the visualizer, from pos seconds in: one channel of plain numbers. A
     *  hi-res stream is a small list on this PC (.mpd) that names parts on the web: ffmpeg only follows such a list
     *  when told the web is allowed (-protocol_whitelist). Without that it read nothing, and the visualizer stood
     *  still for every hi-res stream. */
    fun spectrumArgs(ffmpeg: String, src: String, pos: Double): List<String> =
        listOf(ffmpeg, "-v", "quiet", "-nostdin", "-protocol_whitelist", "file,http,https,tcp,tls,crypto,data",
            "-ss", String.format(java.util.Locale.US, "%.2f", pos), "-i", src, "-ac", "1", "-ar", "44100", "-f", "f32le", "-")

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
