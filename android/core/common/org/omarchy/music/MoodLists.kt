package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.io.File

/** One mood list as kept on disk: its songs (by file, or id when a song has no file) and when it was last made or refreshed. */
@Serializable
data class MoodList(val keys: List<String> = emptyList(), val refreshed: Double = 0.0)

@Serializable
private data class MoodListStore(val lists: Map<String, MoodList> = emptyMap())

/** Mood lists (☰ › Mood lists): one playlist per mood ([Moods.ALL]), each with your songs in that mood as they were
 *  when it was made or last refreshed. They don't change by themselves: Refresh takes the mood's songs again. Kept in
 *  mood-lists.json; songs that left your music drop out as the lists are read back. */
object MoodLists {
    /** Bumps when a list changes. */
    var version by mutableStateOf(0)
        private set
    /** Making or refreshing lists now. */
    var busy by mutableStateOf(false)
        private set

    private val lock = Any()
    private val lists = HashMap<String, MoodList>()
    @Volatile private var loaded = false
    private val file get() = File(Music.ctx.filesDir, "mood-lists.json")

    /** The key a song is kept by: its file (as the moods are), else its id. */
    fun keyOf(t: Track): String = t.file?.takeIf { it.isNotEmpty() } ?: t.id

    private fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            runCatching { readText(file)?.let { JSON.decodeFromString(MoodListStore.serializer(), it) } }.getOrNull()?.let { lists.putAll(it.lists) }
            loaded = true
        }
    }

    private fun save() = runCatching {
        val text = synchronized(lock) { JSON.encodeToString(MoodListStore.serializer(), MoodListStore(lists.toMap())) }
        writeAtomic(file, text)
    }.onFailure { logWarn("mood lists", it) }

    /** The list kept for a mood (null: never made). */
    fun list(mood: String): MoodList? { load(); return synchronized(lock) { lists[mood] } }

    /** When a mood's list was last made or refreshed (seconds; 0: never). */
    fun refreshed(mood: String): Double = list(mood)?.refreshed ?: 0.0

    // The lists' songs as your Track objects, worked out again when a list or your music changes.
    private var memo: Map<String, List<Track>> = emptyMap()
    private var memoFor: Pair<Int, Int>? = null

    /** A mood list's songs, in its order; songs no longer in your music are left out. */
    fun songs(mood: String): List<Track> {
        val stamp = version to Music.musicVersion
        if (memoFor != stamp) {
            load()
            val kept = synchronized(lock) { lists.toMap() }
            val mine = runCatching { Music.mySongs() }.getOrDefault(emptyList())
            val byKey = HashMap<String, Track>(mine.size * 2)
            for (t in mine) byKey.putIfAbsent(keyOf(t), t)
            memo = kept.mapValues { (_, l) -> l.keys.mapNotNull { byKey[it] }.distinctBy { it.id } }
            memoFor = stamp
        }
        return memo[mood].orEmpty()
    }

    /** Make, once, the lists never made yet (in the background). A mood with no songs yet isn't made: it's tried again next time. */
    fun makeMissing() {
        load()
        val todo = Moods.ALL.filter { list(it) == null }
        if (todo.isEmpty() || busy) return
        busy = true
        Music.bg({
            var made = 0
            for (mood in todo) {
                val keys = Moods.songs(mood).map { keyOf(it) }.distinct()
                if (keys.isEmpty()) continue
                synchronized(lock) { if (lists[mood] == null) { lists[mood] = MoodList(keys, now()); made++ } }
            }
            if (made > 0) save()
            made
        }) { _, err ->
            busy = false
            if (err != null) logWarn("mood lists", err)
            version++
        }
    }

    /** Take each mood's songs again, and say what changed ("Chill: 12 new songs, 3 gone"). */
    fun refresh(moods: List<String>) {
        if (moods.isEmpty()) return
        if (busy) { Music.toast("Mood lists are being made: try again in a moment"); return }
        busy = true
        Music.bg({
            load()
            val mine = Music.mySongs().mapTo(HashSet()) { keyOf(it) }
            moods.map { mood ->
                val keys = Moods.songs(mood).map { keyOf(it) }.distinct()
                // Songs that were in it and still are in your music: those not in it now are "gone" (left the mood or your music).
                val before = (list(mood)?.keys.orEmpty()).filter { it in mine }.toSet()
                val after = keys.toSet()
                synchronized(lock) { lists[mood] = MoodList(keys, now()) }
                Triple(mood, (after - before).size, (before - after).size)
            }.also { save() }
        }) { res, err ->
            busy = false
            version++
            if (err != null || res == null) { logWarn("mood lists", err); Music.toast("Couldn't refresh: ${err?.message?.take(80) ?: "something went wrong"}"); return@bg }
            Music.toast(if (res.size == 1) res[0].let { (m, added, gone) -> "$m: " + changes(added, gone) }
                else "Mood lists refreshed: " + changes(res.sumOf { it.second }, res.sumOf { it.third }))
        }
    }

    /** "12 new songs, 3 gone", "1 new song", "no changes". */
    fun changes(added: Int, gone: Int): String = listOfNotNull(
        if (added > 0) "$added new song${if (added != 1) "s" else ""}" else null,
        if (gone > 0) "$gone gone" else null,
    ).joinToString(", ").ifEmpty { "no changes" }
}
