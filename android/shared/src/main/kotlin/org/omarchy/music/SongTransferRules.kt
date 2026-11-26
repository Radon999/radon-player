package org.omarchy.music

/** Other device's songs: copying the song's own file from your other device, over the same connection Sync uses (the
 *  phone always starts it: it fetches files from the PC, and sends its own to the PC when the PC has asked for them).
 *  Here: which way each song comes (copied or downloaded), which files the PC may hand out, and where a copied file is
 *  saved. */
object SongTransferRules {
    /** Settings › Remote & sync › Copy the file from your other device: when. */
    const val ALWAYS = "always"
    const val NOT_FOUND = "notfound"
    const val NEVER = "never"
    val MODES = listOf(ALWAYS, NOT_FOUND, NEVER)

    /** A setting's value as one of the three (anything else: the default, only when it can't be found). */
    fun mode(v: String?): String = v?.takeIf { it in MODES } ?: NOT_FOUND

    /** How one song comes: its file copied from the other device, downloaded from the streaming service, or downloaded
     *  and copied only if the service hasn't got it. */
    enum class Way { COPY, DOWNLOAD, DOWNLOAD_ELSE_COPY }

    /** hasFile: the other device has the song as a file (and said where it is). */
    fun way(mode: String, hasFile: Boolean): Way = when {
        !hasFile || mode(mode) == NEVER -> Way.DOWNLOAD
        mode(mode) == ALWAYS -> Way.COPY
        else -> Way.DOWNLOAD_ELSE_COPY
    }

    /** What a song's row says about the way it will come: "↓ Tidal", "⇄ from your PC", "↓ Tidal, else ⇄ from your PC". */
    fun label(way: Way, service: String, device: String): String = when (way) {
        Way.COPY -> "⇄ from $device"
        Way.DOWNLOAD -> "↓ $service"
        Way.DOWNLOAD_ELSE_COPY -> "↓ $service, else ⇄ from $device"
    }

    /** The song types a copied file may be (anything else isn't taken). */
    val AUDIO = setOf("flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "aiff", "aif", "wma", "alac", "dsf")

    /** A file name's song type ("flac"), or null when it isn't a song file. */
    fun audioExt(name: String): String? = name.substringAfterLast('.', "").lowercase().takeIf { '.' in name && it in AUDIO }

    /** The track number a file's name starts with ("07 Song.flac", "7. Song.mp3", "07 - Song"), or null. */
    fun numberOf(name: String): Long? = Regex("^(\\d{1,3})(?:\\s*[.\\-_]\\s*|\\s+)\\S").find(name)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }

    /** Where a copied file is saved in the download folder root, laid out as downloads are (Artist/Album/, EPs and
     *  singles in their own folders, "NN Title.ext"): (its folder, its file name). The number comes from the original
     *  file's name, the type from its ending. Null when the original isn't a song file. */
    fun savePlace(root: String, artist: String, album: String, title: String, originalName: String): Pair<String, String>? {
        val ext = audioExt(originalName) ?: return null
        val lead = artist.split(",")[0].trim().ifEmpty { "Unknown artist" }
        val name = title.ifBlank { originalName.substringBeforeLast('.') }
        val (where, file) = downloadPlace(root, lead, album, null, numberOf(originalName), name)
        return where to file.substringBeforeLast('.') + "." + ext
    }

    /** The n-th name to try for a file when one of that name is there already: "Song.flac", "Song (2).flac", … */
    fun nameTry(name: String, n: Int): String {
        if (n <= 1) return name
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "$name ($n)" else name.substring(0, dot) + " ($n)" + name.substring(dot)
    }

    /** A request's path or address with nothing in it that could climb out of where it is: no ".." part, no NUL, not
     *  empty. Windows' backslashes count as separators too. */
    fun plainRef(ref: String): Boolean {
        if (ref.isBlank() || '\u0000' in ref) return false
        return ref.replace('\\', '/').split('/').none { it == ".." }
    }

    /** Whether a file asked for may be handed out: it must be, exactly, one of the files of this device's own music
     *  (listed: your songs' files and your downloads'), with nothing in it that climbs out of where it is. Anything
     *  else (another file on the computer, the app's own data, a path with ".." in it) is refused. */
    fun servable(ref: String, listed: Set<String>): Boolean = plainRef(ref) && ref in listed

    /** Where a fetch starts, from its Range header ("bytes=1000-"): 0 without one; null when it asks past the end or in
     *  a way that isn't understood (then nothing is sent). */
    fun rangeStart(header: String?, size: Long): Long? {
        if (header.isNullOrBlank()) return 0L
        val m = Regex("^bytes=(\\d+)-$").find(header.trim()) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: return null
        return start.takeIf { it in 0 until size }
    }

    /** A SHA-256 written as 64 hex letters. */
    fun validSha(s: String?): Boolean = s != null && Regex("^[0-9a-f]{64}$").matches(s)

    /** The song a copied file is kept as in the library: the song shown in the list (the very download the other device
     *  made, or the song found by name), else one named after the file's own checksum. */
    fun libraryId(id: String?, source: String?, sha256: String): Pair<String, String> =
        if (!id.isNullOrBlank() && !source.isNullOrBlank() && source != "file") id to source else "copy-" + sha256.take(16) to "lastfm"

    /** How far a copy is, for the line shown while it runs. */
    fun progress(verb: String, done: Int, total: Int, percent: Int?): String =
        "$verb · ${minOf(done + 1, total)} of $total" + (percent?.let { " · ${it.coerceIn(0, 100)}%" } ?: "")

    /** What is said when the other device couldn't be reached. */
    fun unreachable(device: String): String =
        "Couldn't reach $device. Open Radon Player on $device and make sure both are on the same Wi-Fi or Tailscale"

    /** The one line under the setting: what it takes. */
    const val NEEDS = "Copying works like Sync: both apps open, and able to reach each other (the same Wi-Fi or Tailscale)"

    /** What the PC says when it has asked the phone for songs (it can't reach the phone: the phone sends them). */
    fun asked(n: Int, phone: String): String =
        "Asked $phone for %,d song%s: they come the next time it checks in (within 20 seconds while Radon Player is open on it)".format(n, if (n != 1) "s" else "")

    /** How a batch of copies went. */
    fun summary(copied: Int, already: Int, failed: Int, why: String, device: String): String = buildList {
        if (copied > 0) add("Copied %,d song%s from $device".format(copied, if (copied != 1) "s" else ""))
        if (already > 0) add("%,d %s here already".format(already, if (already != 1) "were" else "was"))
        if (failed > 0) add("%,d couldn't be copied%s".format(failed, if (why.isNotEmpty()) ": $why" else ""))
    }.joinToString(". ").ifEmpty { "Nothing to copy" }
}
