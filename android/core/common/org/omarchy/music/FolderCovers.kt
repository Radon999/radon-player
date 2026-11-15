package org.omarchy.music

import java.io.File

/** Covers kept as pictures in the songs' folders, so a cover lost from the files can always be put back:
 *  [save] puts one picture of each album, EP or single beside its songs (cover.jpg; when a folder holds several
 *  releases, "cover - <album>.jpg" for each), from a song's own cover (else the one picked or shown for it); [apply]
 *  puts the folder's picture into each song there that has no cover in its file. */
object FolderCovers {
    private val NAMES = listOf("cover.jpg", "folder.jpg", "front.jpg", "album.jpg", "cover.png", "folder.png", "front.png")

    data class Done(val saved: Int, val had: Int, val none: Int, val failed: Int, val notes: List<String>)

    private fun fileOf(t: Track): String? = Music.lib.items[t.id]?.file ?: t.file?.takeIf { t.isFile }
    private fun folder(f: String): File? = runCatching { FileTags.folderOf(Music.ctx, f) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { File(it) }
    private fun release(t: Track) = t.album.ifEmpty { t.title }
    private fun safe(s: String) = FolderCoverRules.safe(s)
    /** The picture for album in dir: its own ("cover - <album>.jpg") if there is one, else the folder's cover, else the
     *  folder's only picture ([FolderCoverRules.pick]). */
    private fun pictureIn(dir: File, album: String): File? = FolderCover.file(dir, album)

    /** The cover you picked for the song's release in the app (Cover…, Pick a cover), or the one saved with its
     *  download: the app's own copy of it, not in the song's file. Null if there is none (or it can't be fetched). */
    private fun pickedFor(t: Track): ByteArray? {
        val url = Pictures.cover(t.artist, release(t)).ifEmpty { Music.lib.items[t.id]?.art.orEmpty() }
        if (url.isEmpty()) return null
        return runCatching { if (url.startsWith("http")) httpBytes(url) else File(url.removePrefix("file:")).takeIf { it.isFile }?.readBytes() }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /** Your songs with files, by folder, then by release (album, EP or single). */
    private fun byFolder(progress: (String) -> Unit): Map<File, Map<String, List<Pair<Track, String>>>> {
        val songs = Music.mySongs()
        val out = LinkedHashMap<File, LinkedHashMap<String, MutableList<Pair<Track, String>>>>()
        for ((i, t) in songs.withIndex()) {
            if (i % 100 == 0) progress("Looking at your songs… %,d of %,d".format(i, songs.size))
            val f = fileOf(t) ?: continue
            val dir = folder(f) ?: continue
            out.getOrPut(dir) { LinkedHashMap() }.getOrPut(norm(release(t))) { ArrayList() } += t to f
        }
        return out
    }

    /** (blocking) One cover picture for each album, EP and single, saved in its folder beside its songs. */
    fun save(progress: (String) -> Unit = {}): Done {
        var saved = 0; var had = 0; var none = 0; var failed = 0
        val notes = ArrayList<String>()
        val all = byFolder(progress)
        var n = 0; val total = all.values.sumOf { it.size }
        for ((dir, releases) in all) for ((_, songs) in releases) {
            n++
            if (n % 10 == 0) progress("Saving covers… %,d of %,d".format(n, total))
            val album = release(songs[0].first)
            val name = if (releases.size == 1) "cover.jpg" else "cover - ${safe(album)}.jpg"
            if (File(dir, name).isFile || (releases.size == 1 && NAMES.any { File(dir, it).isFile })) { had++; continue }
            // One song's cover stands for them all (one picture a release, however many songs)
            val pic = songs.firstNotNullOfOrNull { (_, f) -> runCatching { FileTags.cover(Music.ctx, f) }.getOrNull() }
                ?: songs[0].first.let { t ->
                    val url = Pictures.cover(t.artist, album).ifEmpty { Music.lib.items[t.id]?.art.orEmpty() }
                    url.takeIf { it.isNotEmpty() }?.let { u -> runCatching { if (u.startsWith("http")) httpBytes(u) else File(u.removePrefix("file:")).readBytes() }.getOrNull() }
                }
            if (pic == null) { none++; continue }
            val ok = runCatching { File(dir, name).writeBytes(Covers.jpeg(pic)); true }.getOrDefault(false)
            if (ok) saved++ else { failed++; if (notes.size < 20) notes += "Couldn't save the cover of “$album” in ${dir.path}" }
        }
        return Done(saved, had, none, failed, notes)
    }

    /** (blocking) Each song with no cover in its file gets its folder's picture (its release's own, else the folder's). */
    fun apply(progress: (String) -> Unit = {}): Done {
        var saved = 0; var had = 0; var none = 0; var failed = 0
        val notes = ArrayList<String>()
        if (CoverGuard.prevented) return Done(0, 0, 0, 0, listOf(CoverGuard.NOTE))
        val all = byFolder(progress)
        var n = 0; val total = all.values.sumOf { r -> r.values.sumOf { it.size } }
        for ((dir, releases) in all) for ((_, songs) in releases) for ((t, f) in songs) {
            n++
            if (n % 20 == 0) progress("Putting covers into the files… %,d of %,d".format(n, total))
            if (runCatching { FileTags.hasCover(Music.ctx, f) }.getOrDefault(false)) { had++; continue }
            // The folder's picture, else the cover you picked in the app (it was shown, but only the app had it: the
            // song lost it on another device, or in the app installed afresh)
            val pic = runCatching { pictureIn(dir, release(t))?.readBytes() }.getOrNull() ?: pickedFor(t)
            if (pic == null) { none++; continue }
            if (!FileTags.canWrite(f)) { failed++; if (notes.size < 20) notes += "“${t.title}”: the app can't change its file"; continue }
            val ok = runCatching { FileTags.writeCover(Music.ctx, f, Covers.jpeg(pic)) }.getOrDefault(false)
            // Read back: said to be in only when the file now holds it
            val there = ok && runCatching { FileTags.hasCover(Music.ctx, f) }.getOrDefault(false)
            if (there) { saved++; TagMatch.touched(f) } else { failed++; if (notes.size < 20) notes += "“${t.title}” (${f.substringAfterLast('/').substringAfterLast('\\')}): couldn't put the cover in" }
        }
        return Done(saved, had, none, failed, notes)
    }
}
