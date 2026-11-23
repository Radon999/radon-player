package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** Queued mood edits (Utilities): songs put aside to look at their moods later (selecting songs › Queue a mood edit, say
 *  in the middle of a DJ set), and the ones the sound check found whose moods the sound plainly says otherwise of, each
 *  with what it suggests. Looked at all at once there: tick and untick moods, written into the file as the Tagger does.
 *  Kept as a setting, one song a line ("title\tartist\tsuggested moods;…\twhy"), so it goes across with Sync. */
object MoodQueue {
    const val PREF = "mood_queue"

    class Item(val title: String, val artist: String, val suggest: List<String> = emptyList(), val why: String = "") {
        val key get() = statsKey(title, artist)
    }

    fun items(): List<Item> = Prefs.text(PREF).lines().mapNotNull { l ->
        val f = l.split("\t")
        if (f.size < 2 || f[0].isBlank()) null
        else Item(f[0], f[1], f.getOrNull(2).orEmpty().split(';').filter { it.isNotBlank() }, f.getOrNull(3).orEmpty())
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
    private fun save(list: List<Item>) = Prefs.setText(PREF, list.distinctBy { it.key }.joinToString("\n") {
        listOf(clean(it.title), clean(it.artist), it.suggest.joinToString(";"), clean(it.why)).joinToString("\t") })

    /** Songs put on the list (once each: one already there keeps its place, and a suggestion it had). */
    fun add(tracks: List<Track>) {
        val now = items()
        val have = now.map { it.key }.toSet()
        val new = tracks.filter { it.title.isNotBlank() }.distinctBy { statsKey(it.title, it.artist) }.filter { statsKey(it.title, it.artist) !in have }
        save(now + new.map { Item(it.title, it.artist) })
        Music.toast(if (new.isEmpty()) "Already in Queued mood edits" else
            "%,d song%s put in Queued mood edits (Utilities)".format(new.size, if (new.size != 1) "s" else ""))
    }

    /** The sound check's finds: put on the list with what they should be (a song already there gets the suggestion). */
    fun suggest(found: List<Triple<Track, List<String>, String>>) {
        val byKey = items().associateBy { it.key }.toMutableMap()
        for ((t, moods, why) in found) byKey[statsKey(t.title, t.artist)] = Item(t.title, t.artist, moods, why)
        save(byKey.values.toList())
    }

    fun remove(i: Item) = save(items().filter { it.key != i.key })
    fun clear() = Prefs.setText(PREF, "")

    /** Your copy of the song (the one whose file gets the moods), or null when it isn't on this device. */
    fun song(i: Item): Track? = runCatching { Music.songIndex()[i.key] }.getOrNull()?.takeIf { it.isFile }

    // ---- what the screen shows ----
    /** A song on the list as the screen shows it: your copy of it (null: not on this device) and its moods now. */
    class Shown(val item: Item, val track: Track?, val now: List<String>)
    /** The list as the screen shows it, worked out off the screen's thread (finding each song's copy goes through every
     *  song of yours, and its moods may be read from its file): done there, each change of the list, the moods or your
     *  songs (a play counted too) froze scrolling and playing. null: not worked out yet. */
    var shown by mutableStateOf<List<Shown>?>(null)
        private set
    private var shownFor: Any? = null
    private var building = false
    /** The list as the screen shows it, asked for again when what it's made from changed (stamp: the list, the moods'
     *  and your songs' versions): what was worked out last stays shown meanwhile. Called from the screen's thread. */
    fun shownNow(stamp: Any): List<Shown>? {
        if (stamp != shownFor && !building) {
            shownFor = stamp; building = true
            Music.bg({
                val index = runCatching { Music.songIndex() }.getOrDefault(emptyMap())
                items().map { i ->
                    val t = index[i.key]?.takeIf { it.isFile }
                    Shown(i, t, t?.let { runCatching { Moods.all(it) }.getOrDefault(emptyList()) }.orEmpty())
                }
            }) { list, _ ->
                // (anything that changed meanwhile: the screen looks again with a new stamp, and that is worked out next)
                building = false
                if (list != null) shown = list
            }
        }
        return shown
    }

    /** The moods written into the song's file (Moods.pick, as the Tagger does), and it's off the list. t: the song's copy
     *  when already known (the screen has it), so it isn't looked for again. */
    fun write(i: Item, moods: List<String>, t0: Track? = null) {
        val t = t0 ?: song(i) ?: run { Music.toast("“${i.title}” isn't on this device"); return }
        Moods.pick(listOf(t), moods.ifEmpty { null })
        remove(i)
    }

    // ---- the sound check ----
    /** Where the sound check is: (done, of) while it runs, null otherwise. */
    var checking by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    @Volatile private var stop = false
    fun stopCheck() { stop = true }

    /** Goes through every song of yours with moods, hears the ones whose sound isn't known yet (no words read, nothing
     *  asked of the AI or Last.fm: only the sound), and puts on the list the ones whose sound plainly says otherwise of
     *  their moods (MoodRules.soundCheck), with what they should be. */
    fun checkSound() {
        if (checking != null) return
        stop = false
        Music.bg({
            val songs = Music.mySongs().filter { it.isFile }
            val found = ArrayList<Triple<Track, List<String>, String>>()
            for ((i, t) in songs.withIndex()) {
                if (stop) break
                if (i % 10 == 0) Music.scope.launch { checking = i to songs.size }
                val moods = runCatching { Moods.all(t) }.getOrDefault(emptyList())
                if (moods.isEmpty()) continue
                val sound = Moods.soundOrHear(t) ?: continue
                MoodRules.soundCheck(moods, sound)?.let { found += Triple(t, it.moods, it.why) }
            }
            Moods.keepNow()
            if (found.isNotEmpty()) suggest(found)
            found.size
        }) { n, err ->
            checking = null
            Music.toast(when {
                err != null -> "The sound check stopped: ${err.message?.take(80)}"
                (n ?: 0) == 0 -> "Every song's moods fit its sound"
                else -> "%,d song%s whose sound says otherwise of their moods: in Queued mood edits".format(n, if (n != 1) "s" else "")
            })
        }
    }
}
