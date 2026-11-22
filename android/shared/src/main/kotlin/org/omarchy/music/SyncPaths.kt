package org.omarchy.music

/** The scans' choices that name a file or a folder, made the other device's own at a sync. The phone and the PC write
 *  the music folder differently (/storage/emulated/0/Music/… and C:/Users/…/Music/…), so a choice sent as it is
 *  (a song kept as live, a folder left alone, a file marked Never show, two files that aren't duplicates) named a
 *  file the other device doesn't have. Going out, an address inside one of your music folders is written from that
 *  folder on ([MARK] + the rest); coming in, it is put under this device's music folder that has it (else the
 *  first). Addresses outside your music folders, and ones that aren't paths (content:// on the phone), go as they are. */
object SyncPaths {
    /** Where a music folder's address was taken off. */
    const val MARK = "<music>/"

    /** The settings (switches) that hold addresses, one per line: Folders to leave alone, the songs kept as live, as a
     *  soundtrack note or alone in their album, and the Never show lists of the scans that name files and folders. */
    val PREFS = setOf("fixup_skip", "live_kept", "movie_kept", "lone_kept") + ScanIgnoreRules.BY_ADDRESS.map { "ignore_$it" }

    private fun slashes(p: String) = p.replace('\\', '/')

    /** One address (and whatever follows it after a "|") as it goes out. */
    fun out(line: String, roots: List<String>): String {
        val bar = line.indexOf('|')
        val path = if (bar < 0) line else line.substring(0, bar)
        val rest = if (bar < 0) "" else line.substring(bar)
        val p = slashes(path)
        // The deepest music folder it is in (one may be inside another)
        val root = roots.map { slashes(it).trimEnd('/') }.filter { it.isNotEmpty() }
            .filter { r -> p.startsWith("$r/", ignoreCase = true) }.maxByOrNull { it.length } ?: return line
        return MARK + p.substring(root.length + 1) + rest
    }

    /** One address as it comes in: under this device's music folder that has it ([exists]), else the first. */
    fun into(line: String, roots: List<String>, exists: (String) -> Boolean): String {
        if (!line.startsWith(MARK) || roots.isEmpty()) return line
        val bar = line.indexOf('|')
        val rel = (if (bar < 0) line else line.substring(0, bar)).removePrefix(MARK)
        val rest = if (bar < 0) "" else line.substring(bar)
        val made = roots.map { slashes(it).trimEnd('/') + "/" + rel }
        return (made.firstOrNull { runCatching { exists(it) }.getOrDefault(false) } ?: made.first()) + rest
    }

    /** A setting's lines, going out. */
    fun outLines(text: String, roots: List<String>): String = text.split('\n').joinToString("\n") { if (it.isBlank()) it else out(it, roots) }

    /** A setting's lines, coming in. */
    fun intoLines(text: String, roots: List<String>, exists: (String) -> Boolean): String =
        text.split('\n').joinToString("\n") { if (it.isBlank()) it else into(it, roots, exists) }

    /** Duplicates' Not a duplicate: a group's files in one key ([StatPickRules.dupeKey]), each made the other device's
     *  own, and put in order again. */
    fun outDupeKey(key: String, roots: List<String>): String = key.split('\u0001').map { out(it, roots) }.sorted().joinToString("\u0001")

    fun intoDupeKey(key: String, roots: List<String>, exists: (String) -> Boolean): String =
        StatPickRules.dupeKey(key.split('\u0001').map { into(it, roots, exists) })
}
