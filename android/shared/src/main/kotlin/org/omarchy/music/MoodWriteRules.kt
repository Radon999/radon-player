package org.omarchy.music

/** What is said when moods couldn't be written into some files. A count alone ("48 couldn't be written") tells
 *  nothing; the reason does: the kind of file (one whose tags this device can't change), or what the device
 *  answered. The reasons are counted, the commonest said, and all of them noted with a few of the files by name. */
object MoodWriteRules {
    /** A file's name from its address (a path, or a content address that ends in one). */
    fun fileName(address: String): String =
        runCatching { java.net.URLDecoder.decode(address, "UTF-8") }.getOrDefault(address).substringAfterLast('/').substringAfterLast(':').ifEmpty { address }

    /** Its kind: "FLAC", "M4A"… ("" when the address doesn't say). */
    fun kind(address: String): String = fileName(address).substringAfterLast('.', "").takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }?.uppercase().orEmpty()

    /** Its kind from its first 12 bytes, which a phone's address for a song (a number) doesn't say: "FLAC", "MP3",
     *  "WAV", "M4A", "OGG" (Ogg Vorbis and Opus), or "". */
    fun kindOf(head: ByteArray): String {
        fun at(i: Int, n: Int) = if (head.size >= i + n) String(head, i, n, Charsets.ISO_8859_1) else ""
        return when {
            at(0, 4) == "fLaC" -> "FLAC"
            at(0, 3) == "ID3" -> "MP3"
            at(0, 4) == "RIFF" && at(8, 4) == "WAVE" -> "WAV"
            at(4, 4) == "ftyp" -> "M4A"
            at(0, 4) == "OggS" -> "OGG"
            head.size >= 2 && head[0].toInt() and 0xFF == 0xFF && head[1].toInt() and 0xE0 == 0xE0 -> "MP3"
            else -> ""
        }
    }

    /** Why one file couldn't be written: what the device said (kept short), with the file's kind. No message means the
     *  writer declined it without an error: a tag of a kind it leaves alone. */
    fun why(message: String?, address: String): String {
        val k = kind(address)
        val said = message?.trim()?.replace(Regex("\\s+"), " ")?.take(90).orEmpty()
        val what = said.ifEmpty { "its tag is of a kind the app leaves alone" }
        // (the writer may have said the kind itself, from the file's first bytes)
        return if (k.isEmpty() || Regex("^[A-Z0-9]{2,5}: ").containsMatchIn(what)) what else "$k: $what"
    }

    /** The reasons in a line for the message: the commonest, and how many others there are. */
    fun said(whys: Map<String, Int>): String {
        val top = whys.entries.sortedByDescending { it.value }
        if (top.isEmpty()) return "no reason was given"
        val first = top[0]
        return first.key + (if (top.size > 1) ", and %,d other reason%s".format(top.size - 1, if (top.size > 2) "s" else "") else "")
    }

    /** The whole of it, for Settings › Problems. */
    fun note(failed: Int, whys: Map<String, Int>, examples: List<String>): String =
        "Moods couldn't be written into %,d file%s. ".format(failed, if (failed != 1) "s" else "") +
            whys.entries.sortedByDescending { it.value }.joinToString("; ") { "%,d × %s".format(it.value, it.key) } +
            (if (examples.isNotEmpty()) ". For example: " + examples.joinToString(", ") else "")
}
