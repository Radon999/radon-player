package org.omarchy.music

/** Where a line of an .m3u8 playlist says its song is, made comparable with your songs' own places. */
object M3uPaths {
    private val PHONE_ROOT = Regex("^(/storage/emulated/\\d+|/sdcard|/storage/self/primary)/")
    private val DRIVE = Regex("^[A-Za-z]:/")

    /** The place as written, tidied: a file:// address read as a path (its %20 and the like decoded; a + stays a +),
     *  backslashes as slashes, and the phone's storage root taken off the front ("/storage/emulated/0/Music/a.flac"
     *  is "Music/a.flac", the way the phone names your songs' places). */
    fun clean(loc: String): String {
        val path = if (loc.startsWith("file://")) java.net.URLDecoder.decode(loc.substring(7).replace("+", "%2B"), "UTF-8") else loc
        return path.replace('\\', '/').replace(PHONE_ROOT, "")
    }

    /** Whether a tidied place says where it is by itself ("/music/a.flac", "C:/Music/a.flac"), and not from the
     *  playlist's own folder ("../Album/01.flac", "a.flac"). */
    fun whole(where: String): Boolean = where.startsWith("/") || DRIVE.containsMatchIn(where)
}
