package org.omarchy.music

import kotlinx.serialization.Serializable

/** A change you made to a song's tags (title, artist, album, genre, year…), kept until it has gone to your other device
 *  by Sync: which song (its title and artist before the change), the fields and their new values ("" takes one off). */
@Serializable
data class TagChange(val id: String, val at: Double, val title: String, val artist: String, val changes: Map<String, String>)

/** What goes across in a sync: this device's changes not yet taken by the other, and the ones it took from the other
 *  (so the other can let them go). */
@Serializable
data class TagChangesSent(val pending: List<TagChange> = emptyList(), val applied: List<String> = emptyList())

object TagChanges {
    /** Fields that sync their own way (moods by Sync's moods; lyrics are fetched on each device), never as tag changes. */
    private val SKIP = setOf("lyrics", "unsyncedlyrics", "mood")

    /** changes with field names in lowercase, the ones that don't go across left out. */
    fun kept(changes: Map<String, String>): Map<String, String> =
        changes.mapKeys { it.key.lowercase() }.filterKeys { it !in SKIP }

    /** Which song, however its tags are written on each device. */
    fun key(title: String, artist: String) = songPickKey(title, artist)

    /** The song's title and artist once c is made. */
    fun after(c: TagChange): Pair<String, String> = (c.changes["title"]?.takeIf { it.isNotBlank() } ?: c.title) to
        (c.changes["artist"]?.takeIf { it.isNotBlank() } ?: c.artist)

    /** The changes in the order they were made, each once. */
    fun ordered(all: List<TagChange>): List<TagChange> = all.distinctBy { it.id }.sortedBy { it.at }

    /** Which files each change goes to, in order: index (song key -> files) moves along as songs are renamed, so a song
     *  renamed twice gets both. done: the changes taken before (left out). */
    fun plan(changes: List<TagChange>, index: Map<String, List<String>>, done: Set<String>): List<Pair<TagChange, List<String>>> {
        val at = HashMap(index.mapValues { it.value.toMutableList() })
        val out = ArrayList<Pair<TagChange, List<String>>>()
        for (c in ordered(changes)) {
            if (c.id in done) continue
            val k = key(c.title, c.artist)
            val files = at[k].orEmpty().toList()
            out += c to files
            val (t, a) = after(c)
            val nk = key(t, a)
            if (nk != k && files.isNotEmpty()) {
                at[k]?.removeAll(files.toSet())
                at.getOrPut(nk) { mutableListOf() } += files
            }
        }
        return out
    }
}
