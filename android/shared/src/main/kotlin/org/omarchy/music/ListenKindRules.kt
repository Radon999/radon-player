package org.omarchy.music

/** Where a play came from, for Wrapped's "Where your music came from" and its top playlists: put right for the plays
 *  written down before the diary told them apart properly.
 *  - A song picked on its artist's page (or its album's) was written as "radio": that page starts a radio after its
 *    list, and the mark was looked at first. It is the artist's page (the album) when the song is the artist's (on it).
 *  - A song played from one of your playlists was written as "other", from "Playlists" (the tab's name, not the
 *    playlist's). Which playlist is told from the sitting: the songs played one after another ([sittingPlaylist]). */
object ListenKindRules {
    /** The tab's name a playlist's plays were written under. */
    const val PLAYLISTS_TAB = "Playlists"

    /** Songs played one after another from the Playlists tab (a sitting): which playlist that was. members: for each
     *  song of the sitting, the playlists of yours it is in; size: how many songs a playlist holds. The one most of
     *  the sitting's songs are in (at least six in ten of them), the smaller playlist when two tie (songs that are in
     *  "best of hoax" and in a playlist of everything were played from "best of hoax"); null when none fits. */
    fun sittingPlaylist(members: List<Set<String>>, size: (String) -> Int): String? {
        if (members.isEmpty()) return null
        val count = HashMap<String, Int>()
        for (m in members) for (id in m) count[id] = (count[id] ?: 0) + 1
        val best = count.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>>({ it.value }, { -size(it.key) }))?.key ?: return null
        return best.takeIf { count.getValue(it) * 10 >= members.size * 6 }
    }

    /** A play that may be one of a sitting from the Playlists tab (written as "other", or "radio", from that tab). */
    fun fromPlaylistsTab(kind: String, from: String) = from == PLAYLISTS_TAB && (kind == "other" || kind == "radio")

    /** (kind, playlist id or "") for a play. onlyPlaylist: the playlist its sitting was played from, when
     *  this song is in it (null otherwise). */
    fun fix(kind: String, list: String, from: String, artists: List<String>, album: String, onlyPlaylist: String?): Pair<String, String> {
        if (kind == "radio" || kind == "other") {
            if (from == PLAYLISTS_TAB && onlyPlaylist != null) return "playlist" to onlyPlaylist
        }
        if (kind == "radio" && from.isNotBlank()) {
            if (album.isNotBlank() && norm(album) == norm(from)) return "album" to list
            if (artists.any { norm(it) == norm(from) }) return "artist" to list
        }
        return kind to list
    }
}
