package org.omarchy.music

/** What a picked row is known by while picking songs. In a list a song is there once, so it is the song. In the queue
 *  the same song can wait several times: there each row is its own (the song and its place), so picking one of two
 *  identical rows picks that one only. */
object SelectRules {
    private const val AT = "\u0001q"

    fun key(id: String, kind: String, queueIndex: Int): String = if (kind == "queue" && queueIndex >= 0) "$id$AT$queueIndex" else id

    /** The queue place a key was picked at, or null (picked in a list). */
    fun queueIndex(key: String): Int? = if (AT in key) key.substringAfterLast(AT).toIntOrNull() else null

    /** The queue's places to take out for what is picked: the rows picked in the queue themselves, and every place of
     *  a song picked somewhere else (a list knows only the song). */
    fun queuePlaces(keys: Collection<String>, queueIds: List<String>): List<Int> {
        val rows = keys.mapNotNull { queueIndex(it) }.filter { it in queueIds.indices }
        val songs = keys.filter { queueIndex(it) == null }.toSet()
        return (rows + queueIds.indices.filter { queueIds[it] in songs }).distinct().sorted()
    }
}
