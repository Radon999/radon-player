package org.omarchy.music

/** What a read of your music folders found, said when you asked for it (Refresh): how many songs are new, changed
 *  (their tags) or gone, and the folders that aren't connected. Pure. */
object ScanSummaryRules {
    data class Found(val new: Int, val changed: Int, val gone: Int)

    /** before and after: each song file with its words (title, artist, album) as read. */
    fun found(before: Map<String, String>, after: Map<String, String>): Found = Found(
        new = after.keys.count { it !in before },
        changed = after.count { (f, w) -> before[f]?.let { it != w } == true },
        gone = before.keys.count { it !in after })

    fun text(f: Found, songs: Int, notConnected: List<String> = emptyList()): String {
        fun n(count: Int, what: String) = if (count > 0) "%,d %s".format(count, what) else null
        val parts = listOfNotNull(n(f.new, "new"), n(f.changed, "changed"), n(f.gone, "gone"))
        val head = if (parts.isEmpty()) "Your music is read: nothing new (%,d song%s)".format(songs, if (songs != 1) "s" else "")
            else "Your music is read: " + parts.joinToString(", ") + " (%,d song%s now)".format(songs, if (songs != 1) "s" else "")
        val off = when (notConnected.size) {
            0 -> ""
            1 -> ". 1 folder isn't connected (${notConnected[0]}): its songs are left as they are"
            else -> ". ${notConnected.size} folders aren't connected (${notConnected.take(2).joinToString(", ")}${if (notConnected.size > 2) "…" else ""}): their songs are left as they are"
        }
        return head + off
    }
}
