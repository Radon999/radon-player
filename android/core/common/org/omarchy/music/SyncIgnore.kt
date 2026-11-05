package org.omarchy.music

/** Folders sync leaves alone (Settings › Remote & sync): the songs in them never take tags or covers from the other device
 *  (Tags & covers match, tag changes) and never send theirs. For music that only you have the right tags for, such as
 *  unreleased songs, which a sync could otherwise match to another song of the same name. Kept on this device only. */
object SyncIgnore {
    const val KEY = "sync_ignore_folders"   // "sync_": never sent to the other device (each has its own folders)

    val all: List<String> get() = Prefs.text(KEY).lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun add(folder: String) {
        val f = folder.trim().trimEnd('/', '\\')
        if (f.isEmpty() || all.any { it.equals(f, true) }) return
        Prefs.setText(KEY, (all + f).joinToString("\n"))
    }
    fun remove(folder: String) = Prefs.setText(KEY, all.filter { it != folder }.joinToString("\n"))

    private fun norm(p: String) = p.replace('\\', '/').trimEnd('/').lowercase()

    /** Whether the song file at uri is in one of the folders (a full path, or a folder's name anywhere in its path). */
    fun ignored(uri: String): Boolean {
        val list = all
        // The song's real folder (on the phone a content:// song's, asked of Android: the picker's folderOf gives "" for those)
        val real = runCatching { FileTags.folderOf(Music.ctx, uri) }.getOrNull().orEmpty()
            .ifEmpty { runCatching { Music.ctx.folderOf(uri) }.getOrDefault("") }
        val folder = "/" + norm(real.ifEmpty { uri.substringBeforeLast('/') }) + "/"
        // A folder named Unreleased (anywhere) is always left alone, listed or not: only you have its songs' right tags
        if (folder.contains("/unreleased/")) return true
        if (list.isEmpty()) return false
        return list.any { f ->
            val n = norm(f)
            if (n.isEmpty()) false
            else if ('/' in n && (n.startsWith("/") || n.length > 2 && n[1] == ':')) folder.startsWith("/" + n.trimStart('/') + "/") || folder.startsWith(n + "/")
            else folder.contains("/$n/") || folder.contains("/$n")
        }
    }
}
