package org.omarchy.music

/** The PC's widgets and its one-click shortcuts, in rules that need no window: which widgets there are, how each one's
 *  place is kept, how a playlist or a pin is found from the words of a command, and what goes into a shortcut's file
 *  (a Linux desktop entry, a Windows shortcut made by PowerShell). */
object DesktopWidgetRules {
    /** The widgets, in the order the tray and Settings list them: (its key, its name). */
    val KINDS = listOf("queue" to "Now playing + queue", "playlists" to "Now playing + playlists", "dj" to "DJ",
        "playlist" to "Playlist shortcut", "pinned" to "Pinned", "top" to "Most played", "wrapped" to "Wrapped", "lyrics" to "Lyrics")

    /** The smallest a widget can be made, so its buttons still fit (dp). */
    const val MIN_W = 200
    const val MIN_H = 110

    /** Each widget's size the first time it opens (dp): the ones with a list are taller. */
    fun startSize(kind: String): Pair<Int, Int> = when (kind) {
        "queue", "playlists" -> 320 to 420
        "lyrics" -> 320 to 380
        "pinned", "top" -> 280 to 300
        "dj" -> 280 to 190
        "wrapped" -> 280 to 170
        else -> 280 to 240
    }

    /** A widget's place: open or not, where on the screen (null: not moved yet), its size (dp), whether it stays above
     *  the other windows, and its own choice (Playlist shortcut: the playlist and In order or Shuffled; Most played: the
     *  period). */
    data class Place(val open: Boolean = false, val x: Int? = null, val y: Int? = null, val w: Int, val h: Int,
                     val onTop: Boolean = true, val choice: String = "", val shuffled: Boolean = false)

    fun start(kind: String) = startSize(kind).let { (w, h) -> Place(w = w, h = h) }

    /** How a place is kept in the settings: one line of key=value pairs, the choice last (it may hold any letters). */
    fun encode(p: Place): String = listOfNotNull("open=" + (if (p.open) 1 else 0), p.x?.let { "x=$it" }, p.y?.let { "y=$it" },
        "w=${p.w}", "h=${p.h}", "top=" + (if (p.onTop) 1 else 0), "shuffle=" + (if (p.shuffled) 1 else 0), "choice=${p.choice}").joinToString(";")

    /** A kept place read back. Anything missing or garbled takes the first-time value; the size is kept to what fits
     *  on a screen, never smaller than the smallest. */
    fun decode(kind: String, text: String): Place {
        val d = start(kind)
        if (text.isBlank()) return d
        val choice = text.substringAfter("choice=", "")
        val m = text.substringBefore("choice=").split(';').mapNotNull { kv ->
            kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }.toMap()
        fun num(k: String) = m[k]?.toIntOrNull()
        return Place(open = m["open"] == "1", x = num("x"), y = num("y"),
            w = (num("w") ?: d.w).coerceIn(MIN_W, 4000), h = (num("h") ?: d.h).coerceIn(MIN_H, 4000),
            onTop = m["top"]?.let { it == "1" } ?: d.onTop, choice = choice, shuffled = m["shuffle"] == "1")
    }

    // ---- commands ----

    /** The one of names that words mean: the same name, then the same ignoring case, then one starting with them, then
     *  one holding them. null: none (or no words). */
    fun named(words: String, names: List<String>): String? {
        val w = words.trim()
        if (w.isEmpty()) return null
        return names.firstOrNull { it == w } ?: names.firstOrNull { it.equals(w, true) }
            ?: names.firstOrNull { it.startsWith(w, true) } ?: names.firstOrNull { it.contains(w, true) }
    }

    /** "play playlist Road trip shuffle": the playlist meant, and whether shuffled (a last word shuffle or shuffled,
     *  unless that word is part of the playlist's own name). */
    fun playlistAsked(words: String, names: List<String>): Pair<String, Boolean>? {
        val w = words.trim()
        val last = w.substringAfterLast(' ', "").lowercase()
        if ((last == "shuffle" || last == "shuffled") && names.none { it.equals(w, true) })
            named(w.substringBeforeLast(' '), names)?.let { return it to true }
        return named(w, names)?.let { it to false }
    }

    /** A one-click shortcut: what it's called, and the command the app is started with. */
    data class Shortcut(val name: String, val args: List<String>)

    /** The shortcuts Make desktop shortcuts offers (besides a playlist's: [playlistShortcut]). */
    val SHORTCUTS = listOf(
        Shortcut("Play or pause", listOf("play-pause")),
        Shortcut("Next song", listOf("skip")),
        Shortcut("Previous song", listOf("prev")),
        Shortcut("Start the DJ", listOf("dj", "on")),
        Shortcut("Stop the DJ", listOf("dj", "off")),
        Shortcut("DJ with new songs too", listOf("dj", "new")),
        Shortcut("DJ with only my songs", listOf("dj", "mine")),
        Shortcut("Most played", listOf("show", "top")),
        Shortcut("Pinned", listOf("show", "pinned")),
        Shortcut("Lyrics", listOf("show", "lyrics")),
        Shortcut("Wrapped this week", listOf("show", "wrapped", "week")),
        Shortcut("Wrapped this month", listOf("show", "wrapped", "month")),
        Shortcut("Wrapped all time", listOf("show", "wrapped", "all")),
    )

    fun playlistShortcut(name: String, shuffled: Boolean) = Shortcut((if (shuffled) "Shuffle " else "Play ") + name,
        listOf("play", "playlist", name) + if (shuffled) listOf("shuffle") else emptyList())

    // ---- Linux: a desktop entry ----

    /** The mark in the files made here, so taking them away again never touches anyone else's. */
    const val MARK = "X-Radon-Player-Shortcut=true"
    const val LINUX_PREFIX = "radon-player-shortcut-"

    /** A shortcut's file name: its name in small letters, other characters as dashes. */
    fun linuxFile(name: String): String =
        LINUX_PREFIX + name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").split('-').filter { it.isNotEmpty() }.joinToString("-") + ".desktop"

    /** One word of the Exec line, as the desktop-entry rules ask: quoted when it holds a space or a sign the line would
     *  read otherwise (a quote, a backtick, a dollar sign and a backslash inside each behind a backslash); then every
     *  backslash doubled (the file's own escaping, read first) and a percent sign doubled (it starts a field code). */
    fun execArg(a: String): String {
        val reserved = " \t\n\"'\\><~|&;$*?#()`"
        val quoted = if (a.isNotEmpty() && a.none { it in reserved }) a
            else "\"" + a.map { if (it in "\"`$\\") "\\$it" else "$it" }.joinToString("") + "\""
        return quoted.replace("\\", "\\\\").replace("%", "%%")
    }

    /** The desktop entry: the app started with the shortcut's command (it hands it to the one running, or starts). */
    fun desktopEntry(exe: String, s: Shortcut, icon: String?): String {
        val name = "Radon Player: " + s.name.replace('\n', ' ').replace("\\", "\\\\")   // (a backslash is the file's escape)
        return "[Desktop Entry]\nType=Application\nName=$name\nComment=One click: Radon Player does this\n" +
            "Exec=" + (listOf(exe) + s.args).joinToString(" ") { execArg(it) } + "\n" +
            (if (icon.isNullOrEmpty()) "" else "Icon=$icon\n") + "Terminal=false\nCategories=AudioVideo;Audio;Player;\n$MARK\n"
    }

    /** The desktop folder: the one ~/.config/user-dirs.dirs names (XDG_DESKTOP_DIR, "$HOME/…" or a whole path), else
     *  ~/Desktop. */
    fun desktopDir(userDirs: String?, home: String): String {
        val line = userDirs?.lines()?.firstOrNull { it.trim().startsWith("XDG_DESKTOP_DIR=") }
        val v = line?.substringAfter('=')?.trim()?.trim('"')?.replace("\$HOME", home)
        return v?.takeIf { it.startsWith("/") && it.trimEnd('/') != home.trimEnd('/') } ?: "$home/Desktop"
    }

    // ---- Windows: a .lnk made by PowerShell ----

    const val WINDOWS_PREFIX = "Radon Player - "

    /** A shortcut's file name (the signs Windows doesn't allow in a name left out). */
    fun windowsFile(name: String): String = WINDOWS_PREFIX + name.filter { it !in "\\/:*?\"<>|\n\r\t" }.trim() + ".lnk"

    /** The arguments as one line that Windows splits back into the same words: a word with a space or a quote in
     *  quotes, a quote inside behind a backslash, and the backslashes just before a quote doubled. */
    fun windowsArgs(args: List<String>): String = args.joinToString(" ") { a ->
        if (a.isNotEmpty() && a.none { it == ' ' || it == '\t' || it == '"' }) a else buildString {
            append('"')
            var slashes = 0
            for (c in a) {
                when (c) {
                    '\\' -> slashes++
                    '"' -> { append("\\".repeat(slashes * 2 + 1)); append('"'); slashes = 0 }
                    else -> { append("\\".repeat(slashes)); append(c); slashes = 0 }
                }
            }
            append("\\".repeat(slashes * 2)); append('"')
        }
    }

    /** Text inside PowerShell's single quotes. */
    fun ps(s: String) = "'" + s.replace("'", "''") + "'"

    /** The PowerShell that makes the shortcuts in each folder (folders: PowerShell expressions, as
     *  [Environment]::GetFolderPath('Desktop')), each starting exe with its command. */
    fun windowsScript(exe: String, shortcuts: List<Shortcut>, folders: List<String>): String = buildString {
        append("\$ErrorActionPreference = 'Stop'\n\$w = New-Object -ComObject WScript.Shell\n")
        append("foreach (\$d in @(${folders.joinToString(", ")})) {\n  New-Item -ItemType Directory -Force -Path \$d | Out-Null\n")
        for (s in shortcuts) {
            append("  \$s = \$w.CreateShortcut((Join-Path \$d ${ps(windowsFile(s.name))}))\n")
            append("  \$s.TargetPath = ${ps(exe)}\n  \$s.Arguments = ${ps(windowsArgs(s.args))}\n")
            append("  \$s.WorkingDirectory = ${ps(exe.substringBeforeLast('\\', ""))}\n  \$s.IconLocation = ${ps("$exe,0")}\n")
            append("  \$s.Description = ${ps("Radon Player: " + s.name)}\n  \$s.Save()\n")
        }
        append("}\n")
    }

    /** The PowerShell that takes away the shortcuts made here, in the same folders. */
    fun windowsRemoveScript(folders: List<String>): String =
        "foreach (\$d in @(${folders.joinToString(", ")})) { if (Test-Path \$d) { Get-ChildItem -Path \$d -Filter ${ps("$WINDOWS_PREFIX*.lnk")} | Remove-Item -Force } }\n"
}
