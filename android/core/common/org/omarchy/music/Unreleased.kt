package org.omarchy.music

/** The Unreleased tab: your songs in any folder named "Unreleased" (and everything in it), and in the folders you
 *  chose for it (Folders… in the tab). Which folders belong and what each is called there are [UnreleasedRules]'.
 *  The folders you chose are this device's own (its folders aren't the other device's): never sent by a sync. */
object Unreleased {
    const val PREF = "sync_unreleased_folders"   // "sync_": stays on this device

    private val picked = androidx.compose.runtime.mutableStateListOf<String>()
    private var loaded = false

    /** The folders you chose (not the ones named Unreleased: those are in by themselves). */
    val chosen: List<String> get() {
        if (!loaded) { loaded = true; picked.addAll(Prefs.text(PREF, "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }) }
        return picked
    }

    private fun tidy(folder: String) = folder.replace('\\', '/').trimEnd('/')
    private fun save() = Prefs.setText(PREF, picked.joinToString("\n"))

    fun add(folder: String) { val f = tidy(folder); if (f.isNotEmpty() && chosen.none { it.equals(f, true) }) { picked += f; save(); Music.musicVersion++ } }
    fun remove(folder: String) { chosen; if (picked.removeAll { it.equals(tidy(folder), true) }) { save(); Music.musicVersion++ } }

    /** Whether a song of yours is unreleased music (by its folder). */
    fun has(t: Track): Boolean = UnreleasedRules.has(Music.folderOf(t), chosen)

    /** The tab's folders, each with its songs, in folder order. */
    fun folders(): List<Pair<String, List<Track>>> = chosen.toList().let { mine -> Music.folderSongs().filter { UnreleasedRules.has(it.first, mine) } }

    /** Folders you could add: every folder with songs of yours and the folders those are in, the ones in the tab
     *  already left out; in name order. */
    fun candidates(): List<String> {
        val mine = chosen.toList()
        val out = LinkedHashSet<String>()
        for ((folder, _) in Music.folderSongs()) {
            val parts = tidy(folder).split('/').filter { it.isNotEmpty() }
            for (n in parts.size downTo 2) out += parts.take(n).joinToString("/")
        }
        return out.filter { !UnreleasedRules.has(it, mine) }.sortedBy { it.lowercase() }
    }
}
