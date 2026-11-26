package org.omarchy.music

/** The PC's widgets and its one-click shortcuts, in rules that need no window: which widgets there are, how each one's
 *  place is kept, how a playlist or a pin is found from the words of a command, and what goes into a shortcut's file
 *  (a Linux desktop entry, a Windows shortcut made by PowerShell). */
object DesktopWidgetRules {
    /** The widgets, in the order the tray and Settings list them: (its key, its name). */
    val KINDS = listOf("queue" to "Now playing + queue", "playlists" to "Now playing + playlists", "dj" to "DJ",
        "playlist" to "Playlist shortcut", "pinned" to "Pinned", "top" to "Most played", "wrapped" to "Wrapped", "lyrics" to "Lyrics",
        "releases" to "New releases", "visualizer" to "Visualizer", "shortcuts" to "Shortcuts")

    /** The smallest a widget can be made, so its buttons still fit (dp). */
    const val MIN_W = 200
    const val MIN_H = 110

    /** Each widget's size the first time it opens (dp): the ones with a list are taller. */
    fun startSize(kind: String): Pair<Int, Int> = when (kind) {
        "queue", "playlists" -> 320 to 420
        "lyrics" -> 320 to 380
        "pinned", "top", "releases" -> 280 to 300
        "visualizer" -> 360 to 220
        "shortcuts" -> 330 to 230
        "dj" -> 280 to 190
        "wrapped" -> 280 to 170
        else -> 280 to 240
    }

    /** A widget's place: open or not, where on the screen (null: not moved yet), its size (dp), whether it stays above
     *  the other windows (off unless you switch it on), and its own choice (Playlist shortcut: the playlist and In order
     *  or Shuffled; Most played: the period). hypr: x and y are Hyprland's own (its layout's), kept there, as Hyprland
     *  places the window itself; anywhere else they're the window's place as Java has it. */
    data class Place(val open: Boolean = false, val x: Int? = null, val y: Int? = null, val w: Int, val h: Int,
                     val onTop: Boolean = false, val choice: String = "", val shuffled: Boolean = false, val hypr: Boolean = false)

    fun start(kind: String) = startSize(kind).let { (w, h) -> Place(w = w, h = h) }

    /** How a place is kept in the settings: one line of key=value pairs, the choice last (it may hold any letters). */
    fun encode(p: Place): String = listOfNotNull("open=" + (if (p.open) 1 else 0), p.x?.let { "x=$it" }, p.y?.let { "y=$it" },
        "w=${p.w}", "h=${p.h}", "top=" + (if (p.onTop) 1 else 0), "shuffle=" + (if (p.shuffled) 1 else 0),
        if (p.hypr) "hypr=1" else null, "choice=${p.choice}").joinToString(";")

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
            onTop = m["top"]?.let { it == "1" } ?: d.onTop, choice = choice, shuffled = m["shuffle"] == "1", hypr = m["hypr"] == "1")
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

    /** A one-click shortcut: what it's called (plainly: "Wrapped all time", a playlist's own name), and the command the
     *  app is started with. */
    data class Shortcut(val name: String, val args: List<String>) {
        /** What tells two apart: the command (two can share a name, never a command). */
        val key: String get() = args.joinToString("\u0000")
    }

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
        Shortcut("New releases", listOf("show", "new releases")),
        Shortcut("Wrapped this week", listOf("show", "wrapped", "week")),
        Shortcut("Wrapped this month", listOf("show", "wrapped", "month")),
        Shortcut("Wrapped all time", listOf("show", "wrapped", "all")),
    )

    /** A playlist's: its own name (shuffled: with "(shuffled)" after it). */
    fun playlistShortcut(name: String, shuffled: Boolean) = Shortcut(if (shuffled) "$name (shuffled)" else name,
        listOf("play", "playlist", name) + if (shuffled) listOf("shuffle") else emptyList())

    /** The picture a shortcut gets, by what it does: one of play-pause, next, prev, dj, dj-off, dj-new, dj-mine, top,
     *  pinned, lyrics, releases, wrapped-week, wrapped-month, wrapped-all, wrapped, playlist, playlist-shuffle; "app"
     *  for anything else (the app's own). */
    fun iconKind(args: List<String>): String {
        val a = args.map { it.lowercase() }
        return when {
            a == listOf("play-pause") || a == listOf("toggle") || a == listOf("play") || a == listOf("pause") -> "play-pause"
            a == listOf("skip") || a == listOf("next") -> "next"
            a == listOf("prev") || a == listOf("previous") -> "prev"
            a.firstOrNull() == "dj" -> when (a.getOrNull(1)) { "off", "stop" -> "dj-off"; "new" -> "dj-new"; "mine", "only", "my" -> "dj-mine"; else -> "dj" }
            a.take(2) == listOf("play", "playlist") -> if (a.size > 3 && a.last() in setOf("shuffle", "shuffled")) "playlist-shuffle" else "playlist"
            a.firstOrNull() in setOf("show", "open") -> when (a.drop(1).joinToString(" ")) {
                "top" -> "top"; "pinned" -> "pinned"; "lyrics" -> "lyrics"
                "new releases", "newreleases", "new-releases", "releases" -> "releases"
                "wrapped week" -> "wrapped-week"; "wrapped month" -> "wrapped-month"; "wrapped all" -> "wrapped-all"; "wrapped" -> "wrapped"
                else -> "app"
            }
            a.firstOrNull() == "wrapped" -> when (a.getOrNull(1)) { "week" -> "wrapped-week"; "month" -> "wrapped-month"; "all" -> "wrapped-all"; else -> "wrapped" }
            else -> "app"
        }
    }

    /** A shortcut's own file name, without its ending (its launcher, its picture): its command in small letters, other
     *  characters as dashes, and a short fingerprint of the exact command, so two never share one ("Road trip" and
     *  "Road-trip", a playlist called Pinned and Pinned). */
    fun fileBase(s: Shortcut): String {
        val slug = s.args.joinToString("-").lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
            .split('-').filter { it.isNotEmpty() }.joinToString("-").take(60)
        val print = java.security.MessageDigest.getInstance("SHA-1").digest(s.key.toByteArray()).take(3).joinToString("") { "%02x".format(it) }
        return "$slug-$print"
    }

    /** The shortcuts made, as the settings keep them (the Shortcuts widget shows them): one a line, its name and its
     *  command's words between tabs. */
    fun encodeList(list: List<Shortcut>): String = list.joinToString("\n") { s ->
        (listOf(s.name) + s.args).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }
    }
    fun decodeList(text: String): List<Shortcut> = text.lines().mapNotNull { l ->
        l.split('\t').takeIf { it.size >= 2 && it[0].isNotBlank() }?.let { Shortcut(it[0], it.drop(1)) }
    }
    /** The ones made before, with the new ones (a command made again: its newer name, in its old place). */
    fun merged(old: List<Shortcut>, new: List<Shortcut>): List<Shortcut> {
        val byKey = new.associateBy { it.key }
        return old.map { byKey[it.key] ?: it } + new.filter { n -> old.none { it.key == n.key } }
    }

    /** Whether files on the desktop folder show (and run) on this Linux desktop: not on the ones that place windows
     *  themselves and draw no desktop (Hyprland, Sway, i3, niri, river), where a file there is never seen, and double-
     *  clicking it in a file manager opens it as text. */
    fun desktopIconsWork(env: Map<String, String>): Boolean {
        if (!env["HYPRLAND_INSTANCE_SIGNATURE"].isNullOrBlank() || !env["SWAYSOCK"].isNullOrBlank() || !env["NIRI_SOCKET"].isNullOrBlank()) return false
        val desk = (env["XDG_CURRENT_DESKTOP"].orEmpty() + ":" + env["XDG_SESSION_DESKTOP"].orEmpty()).lowercase()
        return listOf("hyprland", "sway", "i3", "niri", "river").none { it in desk }
    }

    /** A .ico holding one PNG picture (Windows reads PNG inside .ico since Vista): its 6-byte header, its one 16-byte
     *  entry, then the PNG. size: the picture's side (256 is written as 0, as the format asks). */
    fun icoOf(png: ByteArray, size: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun le32(v: Int) { le16(v and 0xFFFF); le16((v ushr 16) and 0xFFFF) }
        le16(0); le16(1); le16(1)
        out.write(if (size >= 256) 0 else size); out.write(if (size >= 256) 0 else size); out.write(0); out.write(0)
        le16(1); le16(32); le32(png.size); le32(6 + 16)
        out.write(png)
        return out.toByteArray()
    }

    // ---- New releases ----

    /** The newest first, each once (by key), at most n: dates as the catalogue writes them (2026-11-20), so the newest
     *  sorts last as text; one with no date goes after the dated ones. */
    fun <T> newest(items: List<T>, key: (T) -> String, date: (T) -> String, n: Int): List<T> =
        items.distinctBy(key).sortedWith(compareByDescending<T> { date(it).isNotBlank() }.thenByDescending { date(it) }).take(n)

    /** When a release came out, said as a person would: Today, Yesterday, 3 days ago, Last week, 5 weeks ago, then the
     *  date (with its year when it isn't this year's); one still to come: "Out Dec 4". Anything not a date: as it is. */
    fun whenOut(date: String, today: java.time.LocalDate): String {
        val d = runCatching { java.time.LocalDate.parse(date.trim().take(10)) }.getOrNull() ?: return date
        val days = java.time.temporal.ChronoUnit.DAYS.between(d, today)
        val month = d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        val shown = "$month ${d.dayOfMonth}" + if (d.year != today.year) ", ${d.year}" else ""
        return when {
            days < 0 -> "Out $shown"
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "Last week"
            days < 60 -> "${days / 7} weeks ago"
            else -> shown
        }
    }

    // ---- Linux: a desktop entry ----

    /** The mark in the files made here, so taking them away again never touches anyone else's. */
    const val MARK = "X-Radon-Player-Shortcut=true"
    const val LINUX_PREFIX = "radon-player-shortcut-"

    /** A shortcut's launcher file name. */
    fun linuxFile(s: Shortcut): String = LINUX_PREFIX + fileBase(s) + ".desktop"

    /** One word of the Exec line, as the desktop-entry rules ask: quoted when it holds a space or a sign the line would
     *  read otherwise (a quote, a backtick, a dollar sign and a backslash inside each behind a backslash); then every
     *  backslash doubled (the file's own escaping, read first) and a percent sign doubled (it starts a field code). */
    fun execArg(a: String): String {
        val reserved = " \t\n\"'\\><~|&;$*?#()`"
        val quoted = if (a.isNotEmpty() && a.none { it in reserved }) a
            else "\"" + a.map { if (it in "\"`$\\") "\\$it" else "$it" }.joinToString("") + "\""
        return quoted.replace("\\", "\\\\").replace("%", "%%")
    }

    /** The desktop entry: named plainly, the app started with the shortcut's command (it hands it to the one running,
     *  or starts), its own picture. */
    fun desktopEntry(exe: String, s: Shortcut, icon: String?): String {
        val name = s.name.replace('\n', ' ').replace("\\", "\\\\")   // (a backslash is the file's escape)
        return "[Desktop Entry]\nType=Application\nName=$name\nComment=Radon Player: one click does this\n" +
            "Exec=" + (listOf(exe) + s.args).joinToString(" ") { execArg(it) } + "\n" +
            (if (icon.isNullOrEmpty()) "" else "Icon=$icon\n") + "Terminal=false\nCategories=AudioVideo;Audio;Player;\n$MARK\n"
    }

    /** An Exec line's words read back as the desktop reads them (the file's own escaping first, then the quotes): the
     *  program and the command. null: not an Exec line. */
    fun execWords(line: String): List<String>? {
        if (!line.startsWith("Exec=")) return null
        val raw = StringBuilder()
        val v = line.removePrefix("Exec=")
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                raw.append(when (val n = v[i + 1]) { 's' -> ' '; 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; else -> if (n == '\\') '\\' else { raw.append('\\'); n } }); i += 2
            } else if (c == '%' && i + 1 < v.length && v[i + 1] == '%') { raw.append('%'); i += 2 } else { raw.append(c); i++ }
        }
        val words = ArrayList<String>(); val w = StringBuilder(); var quoted = false; var any = false
        i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                quoted && c == '\\' && i + 1 < raw.length && raw[i + 1] in "\"`$\\" -> { w.append(raw[i + 1]); i++ }
                c == '"' -> { quoted = !quoted; any = true }
                !quoted && c.isWhitespace() -> { if (w.isNotEmpty() || any) words += w.toString(); w.clear(); any = false }
                else -> w.append(c)
            }
            i++
        }
        if (w.isNotEmpty() || any) words += w.toString()
        return words
    }

    /** A shortcut made before plain names (its name "Radon Player: …"), as it's named now: the offered one of the same
     *  command, a playlist's own name; else its old name without the app's. null: not one of the old ones. */
    fun renamed(oldName: String, args: List<String>): Shortcut? {
        if (!oldName.startsWith("Radon Player: ") || args.isEmpty()) return null
        SHORTCUTS.firstOrNull { it.args == args }?.let { return it }
        if (args.size >= 3 && args[0] == "play" && args[1] == "playlist")
            return playlistShortcut(args[2], args.size > 3 && args.last() in setOf("shuffle", "shuffled")).copy(args = args)
        return Shortcut(oldName.removePrefix("Radon Player: "), args)
    }

    /** The desktop folder: the one ~/.config/user-dirs.dirs names (XDG_DESKTOP_DIR, "$HOME/…" or a whole path), else
     *  ~/Desktop. */
    fun desktopDir(userDirs: String?, home: String): String {
        val line = userDirs?.lines()?.firstOrNull { it.trim().startsWith("XDG_DESKTOP_DIR=") }
        val v = line?.substringAfter('=')?.trim()?.trim('"')?.replace("\$HOME", home)
        return v?.takeIf { it.startsWith("/") && it.trimEnd('/') != home.trimEnd('/') } ?: "$home/Desktop"
    }

    // ---- Windows: a .lnk made by PowerShell ----

    /** How the shortcuts made here were named before (still taken away with the rest). */
    const val WINDOWS_PREFIX = "Radon Player - "
    /** The shortcuts' own description, which marks them as made here. */
    const val WINDOWS_MARK = "Radon Player shortcut"

    /** Each shortcut's file name, in order: its plain name (the signs Windows doesn't allow in a name left out), with
     *  " (2)", " (3)"… for a name already taken by one before it. */
    fun windowsFiles(list: List<Shortcut>): List<String> {
        val seen = HashMap<String, Int>()
        return list.map { s ->
            val base = s.name.filter { it !in "\\/:*?\"<>|\n\r\t" }.trim().trimEnd('.').ifEmpty { "Radon Player" }
            val n = (seen[base.lowercase()] ?: 0) + 1
            seen[base.lowercase()] = n
            (if (n == 1) base else "$base ($n)") + ".lnk"
        }
    }

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
     *  [Environment]::GetFolderPath('Desktop')), each starting exe with its command, with its own picture (icons: a
     *  shortcut's .ico by its key; none: the app's). A file of the same name that isn't one of ours is left alone: ours
     *  then gets " (Radon Player)" after its name. */
    fun windowsScript(exe: String, shortcuts: List<Shortcut>, folders: List<String>, icons: Map<String, String> = emptyMap()): String = buildString {
        append("\$ErrorActionPreference = 'Stop'\n\$w = New-Object -ComObject WScript.Shell\n")
        append("foreach (\$d in @(${folders.joinToString(", ")})) {\n  New-Item -ItemType Directory -Force -Path \$d | Out-Null\n")
        val files = windowsFiles(shortcuts)
        shortcuts.forEachIndexed { i, s ->
            val file = files[i]
            append("  \$p = Join-Path \$d ${ps(file)}\n")
            append("  if ((Test-Path \$p) -and -not (\$w.CreateShortcut(\$p).Description -like ${ps("$WINDOWS_MARK*")})) { \$p = Join-Path \$d ${ps(file.removeSuffix(".lnk") + " (Radon Player).lnk")} }\n")
            append("  \$s = \$w.CreateShortcut(\$p)\n")
            append("  \$s.TargetPath = ${ps(exe)}\n  \$s.Arguments = ${ps(windowsArgs(s.args))}\n")
            append("  \$s.WorkingDirectory = ${ps(exe.substringBeforeLast('\\', ""))}\n  \$s.IconLocation = ${ps(icons[s.key]?.let { "$it,0" } ?: "$exe,0")}\n")
            append("  \$s.Description = ${ps("$WINDOWS_MARK: " + s.name)}\n  \$s.Save()\n")
        }
        append("}\n")
    }

    /** The PowerShell that takes away the shortcuts made here (their description says so, or their name has the old
     *  "Radon Player - " in front), in the same folders. */
    fun windowsRemoveScript(folders: List<String>): String =
        "\$w = New-Object -ComObject WScript.Shell\n" +
        "foreach (\$d in @(${folders.joinToString(", ")})) { if (Test-Path \$d) { Get-ChildItem -Path \$d -Filter '*.lnk' | " +
            "Where-Object { \$_.Name -like ${ps("$WINDOWS_PREFIX*")} -or \$w.CreateShortcut(\$_.FullName).Description -like ${ps("$WINDOWS_MARK*")} } | Remove-Item -Force } }\n"

    // ---- Linux: the taskbar (a dock's pins) ----
    /** Putting launchers on a Linux taskbar: Omarchy's dock (the omarchy-dock command keeps its pins, by desktop id, in
     *  order) or GNOME's dash (gsettings, org.gnome.shell favorite-apps: "<id>.desktop" in a list). Elsewhere there's no
     *  way an app can, and the option isn't offered. Only ever adds at the end or takes away one of ours: the order and
     *  the pins of anyone else stay as they are. */
    object Taskbar {
        const val OMARCHY = "omarchy"
        const val GNOME = "gnome"

        /** Which one this desktop has: Omarchy's dock first (its command is there), else GNOME's (its favourites can be
         *  read), else none. */
        fun kind(hasOmarchyDock: Boolean, gnomeFavourites: Boolean): String? = when {
            hasOmarchyDock -> OMARCHY
            gnomeFavourites -> GNOME
            else -> null
        }

        /** A launcher's desktop id: its file name without ".desktop". */
        fun id(fileName: String) = fileName.trim().removeSuffix(".desktop")

        /** omarchy-dock pinned's answer: one desktop id a line. */
        fun parseOmarchy(text: String): List<String> = text.lines().map { id(it) }.filter { it.isNotEmpty() }

        /** gsettings get org.gnome.shell favorite-apps's answer (['a.desktop', 'b.desktop'], or @as [] when empty): the
         *  desktop ids. */
        fun parseGnome(text: String): List<String> =
            Regex("'((?:[^'\\\\]|\\\\.)*)'").findAll(text).map { id(it.groupValues[1].replace("\\'", "'").replace("\\\\", "\\")) }.toList()

        /** The list gsettings set takes: each id as "<id>.desktop", quoted. */
        fun gnomeValue(ids: List<String>): String =
            ids.joinToString(", ", "[", "]") { "'" + (it + ".desktop").replace("\\", "\\\\").replace("'", "\\'") + "'" }

        /** Pinned: added at the end (where it is, if it's there already). */
        fun withPin(pins: List<String>, id: String): List<String> = if (id in pins) pins else pins + id
        /** Unpinned: only that one goes. */
        fun withoutPin(pins: List<String>, id: String): List<String> = pins.filter { it != id }
        /** One of ours made again under a new id (its file renamed): the new one in the old one's place. */
        fun replaced(pins: List<String>, old: String, new: String): List<String> =
            if (old !in pins) pins else pins.map { if (it == old) new else it }.distinct()

        /** The launcher of the app itself among a folder's (file name, text): the one starting the app's program (as
         *  the installer made it), not a shortcut of ours. */
        fun appEntry(entries: List<Pair<String, String>>, exe: String): String? = entries.firstOrNull { (name, text) ->
            name.endsWith(".desktop") && !name.startsWith(LINUX_PREFIX) && MARK !in text.lines() &&
                text.lines().firstNotNullOfOrNull { execWords(it) }?.firstOrNull() == exe
        }?.let { id(it.first) }
    }

    // ---- Hyprland: it places a new window itself (a floating workspace's rule gives every new one its own size and
    // place), so a widget is put back where it was kept, at its size, once it's on the screen ----
    object Hypr {
        /** A window as hyprctl clients -j says it: where (Hyprland's layout), how big (its logical pixels), on which
         *  monitor (its id). */
        data class Client(val address: String, val pid: Long, val title: String, val x: Int, val y: Int, val w: Int, val h: Int,
                          val monitor: Int, val floating: Boolean, val pinned: Boolean)
        /** A monitor as hyprctl monitors -j says it: its place in the layout, its size in real pixels, its scale, and the
         *  edges bars keep (left, top, right, bottom). */
        data class Monitor(val id: Int, val x: Int, val y: Int, val w: Int, val h: Int, val scale: Double, val focused: Boolean,
                           val reserved: List<Int> = listOf(0, 0, 0, 0)) {
            val logicalW get() = (w / scale).toInt()
            val logicalH get() = (h / scale).toInt()
        }

        private fun json(text: String) = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonArray }.getOrNull()
        private fun kotlinx.serialization.json.JsonElement?.int() = (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
        private fun kotlinx.serialization.json.JsonElement?.str() = (this as? kotlinx.serialization.json.JsonPrimitive)?.content
        private fun kotlinx.serialization.json.JsonElement?.arr() = this as? kotlinx.serialization.json.JsonArray

        fun clients(text: String): List<Client> = json(text).orEmpty().mapNotNull { e ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val at = o["at"].arr(); val size = o["size"].arr()
            Client(o["address"].str() ?: return@mapNotNull null, o["pid"].int()?.toLong() ?: -1, o["title"].str().orEmpty(),
                at?.getOrNull(0).int() ?: 0, at?.getOrNull(1).int() ?: 0, size?.getOrNull(0).int() ?: 0, size?.getOrNull(1).int() ?: 0,
                o["monitor"].int() ?: -1, o["floating"].str() == "true", o["pinned"].str() == "true")
        }

        fun monitors(text: String): List<Monitor> = json(text).orEmpty().mapNotNull { e ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            Monitor(o["id"].int() ?: return@mapNotNull null, o["x"].int() ?: 0, o["y"].int() ?: 0, o["width"].int() ?: 0, o["height"].int() ?: 0,
                (o["scale"].str()?.toDoubleOrNull() ?: 1.0).takeIf { it > 0 } ?: 1.0, o["focused"].str() == "true",
                o["reserved"].arr()?.map { it.int() ?: 0 }?.takeIf { it.size == 4 } ?: listOf(0, 0, 0, 0))
        }

        /** Our window of that title (the installed app's widgets, in another process, have the same titles). */
        fun find(clients: List<Client>, pid: Long, title: String): Client? = clients.firstOrNull { it.pid == pid && it.title == title }

        /** A widget's size in dp as Hyprland's logical pixels on a monitor: Java draws dp at its own scale (the
         *  desktop's, 2 here) in real pixels, which Hyprland counts at the monitor's scale. */
        fun logical(dp: Int, javaScale: Double, monitorScale: Double): Int = Math.round(dp * javaScale / monitorScale).toInt()
        fun dp(logical: Int, javaScale: Double, monitorScale: Double): Int = Math.round(logical * monitorScale / javaScale).toInt()

        /** Where a widget opens the first time, in Hyprland's layout: the top right of the monitor, clear of its bars, each
         *  widget (i: its place in the list) a little below and left of the one before. */
        fun firstPlace(m: Monitor, w: Int, h: Int, i: Int): Pair<Int, Int> {
            val x = m.x + m.logicalW - m.reserved[2] - w - 24 - i * 28
            val y = m.y + m.reserved[1] + 24 + i * 36
            return x.coerceAtLeast(m.x + m.reserved[0]) to y.coerceAtMost(m.y + m.logicalH - m.reserved[3] - h).coerceAtLeast(m.y + m.reserved[1])
        }

        /** Whether a kept place is on a monitor that's there (one unplugged since: no, it opens at its first place). */
        fun onSomeMonitor(x: Int, y: Int, monitors: List<Monitor>): Boolean =
            monitors.any { x >= it.x - 40 && x < it.x + it.logicalW && y >= it.y - 40 && y < it.y + it.logicalH }

        /** The hyprctl dispatches that float it, size it and put it in place, and pin it or not (Always on top: Hyprland
         *  keeps a floating window above tiled ones anyway; pinned, it also stays in view on every workspace). */
        fun dispatches(c: Client, x: Int, y: Int, w: Int, h: Int, onTop: Boolean): List<String> = listOfNotNull(
            if (!c.floating) "setfloating address:${c.address}" else null,
            "resizewindowpixel exact $w $h,address:${c.address}",
            "movewindowpixel exact $x $y,address:${c.address}",
            if (onTop != c.pinned) "pin address:${c.address}" else null)

        /** One of those dispatches as Hyprland's Lua configuration takes it (hyprctl dispatch hl.dsp.window.…), for a
         *  Hyprland set up in Lua, which no longer reads the older words. pinned: whether it's pinned now (pin turns it
         *  the other way). */
        fun lua(cmd: String, pinned: Boolean): String {
            val who = "window = \"address:" + cmd.substringAfter("address:") + "\""
            val nums = cmd.substringAfter("exact ", "").substringBefore(',').split(' ').mapNotNull { it.toIntOrNull() }
            return when (cmd.substringBefore(' ')) {
                "setfloating" -> "hl.dsp.window.float({ action = \"on\", $who })"
                "resizewindowpixel" -> "hl.dsp.window.resize({ x = ${nums[0]}, y = ${nums[1]}, relative = false, $who })"
                "movewindowpixel" -> "hl.dsp.window.move({ x = ${nums[0]}, y = ${nums[1]}, relative = false, $who })"
                "pin" -> "hl.dsp.window.pin({ action = \"${if (pinned) "off" else "on"}\", $who })"
                else -> cmd
            }
        }
    }
}
