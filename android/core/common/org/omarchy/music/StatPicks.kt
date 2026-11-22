package org.omarchy.music

/** Your picks in the stats: whose plays a joint credit's are (Joint artists), pairs of similar artists kept apart,
 *  one artist merged into another, which album or song plays count for, groups marked "Not a duplicate", singles
 *  left alone. The picks themselves are kept with the app's state in [Music]; the rules are in [StatPickRules].
 *  [Music] keeps the names the screens call and hands on. */
object StatPicks {
    fun dupeKey(g: List<Track>): String = StatPickRules.dupeKey(g.map { Music.lib.items[it.id]?.file ?: it.file ?: it.id })

    fun markNotDupe(g: List<Track>) {
        Music.notDupes = Music.notDupes + dupeKey(g); Music.saveState()
        Music.dupes = Music.dupes?.filter { it !== g }
        Music.toast("“${g[0].title}”: not a duplicate, remembered")
    }

    fun addSongPicks(more: Map<String, String>) { Music.songPicks = Music.songPicks + more; Music.saveState(); Stats.refresh(force = true) }
    fun addAlbumPicks(more: Map<String, String>) { Music.albumPicks = Music.albumPicks + more; Music.saveState(); Stats.refresh(force = true) }

    fun keepApart(from: String, to: String) {
        val k = StatPickRules.apartKey(from, to)
        if (k !in Music.similarKept) { Music.similarKept = Music.similarKept + k; Music.saveState() }
    }

    fun setArtistPick(credit: String, name: String?) {
        Music.artistPicks = if (name == null) Music.artistPicks - pairKey(credit) else Music.artistPicks + (pairKey(credit) to name)
        Music.saveState()
    }

    /** Merge into…: name's plays count as to's from now on, and name's songs the app downloaded get to in their tags
     *  (name's part of a pair's credit, or the whole credit). The desktop's merge_artist. */
    fun mergeArtist(name: String, to: String) {
        setArtistPick(name, to)
        val ctx = Music.ctx
        Music.bg({
            var n = 0
            for (t in Music.mySongs()) {
                val uri = Tags.editable(t) ?: continue
                val new = StatPickRules.merged(t.artist, name, to)
                if (new == t.artist) continue
                runCatching { Tags.write(ctx, uri, mapOf("artist" to new, "albumartist" to new)) }.onSuccess { n++ }
            }
            n
        }) { n, _ ->
            Music.toast(StatPickRules.mergedText(name, to, n ?: 0))
            Stats.refresh(force = true)
            Music.musicVersion++
        }
    }

    private var resolverCache: Pair<Any, (String) -> String>? = null
    /** artistResolver for your songs and your Joint artists… picks, kept until either changes. */
    fun artistResolver(): (String) -> String {
        val sig = listOf(Music.lib.items.size, Music.local.files.size, Music.musicVersion, Music.artistPicks)
        resolverCache?.let { if (it.first == sig) return it.second }
        val r = artistResolver(runCatching { Music.mySongs() }.getOrDefault(emptyList()), Music.artistPicks)
        resolverCache = sig to r
        return r
    }
}
