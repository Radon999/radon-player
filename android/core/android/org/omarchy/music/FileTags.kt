package org.omarchy.music

/** Your songs' files, for the tools that fix them: on the phone, the FLACs the app downloaded (Android lets an app
 *  change only its own files without asking for each); moving files and folders is the PC's. */
object FileTags {
    /** Songs are moved through Android's own list of music (MediaStore): the song keeps its address, so playlists, the
     *  queue, favorites, plays and sync never see it change. Your own files need All files access (Android 10 and up). */
    val canMove: Boolean get() = android.os.Build.VERSION.SDK_INT >= 29 && runCatching { Music.canWriteOwnFiles() }.getOrDefault(false)
    /** With All files access the music folders can be looked through (Empty folders, and tidying after a move). */
    val canListFolders: Boolean get() = canMove
    /** Your own files (not the app's downloads) are tagged through the tag library on their real path, with All files
     *  access: FLAC, MP3, M4A, Ogg, Opus, WAV and more, each in its own format. */
    const val OWN_BY_TAGLIB = true

    private val KEYS = mapOf("title" to org.jaudiotagger.tag.FieldKey.TITLE, "artist" to org.jaudiotagger.tag.FieldKey.ARTIST,
        "albumartist" to org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, "album" to org.jaudiotagger.tag.FieldKey.ALBUM,
        "tracknumber" to org.jaudiotagger.tag.FieldKey.TRACK, "date" to org.jaudiotagger.tag.FieldKey.YEAR,
        "genre" to org.jaudiotagger.tag.FieldKey.GENRE, "mood" to org.jaudiotagger.tag.FieldKey.MOOD,
        "lyrics" to org.jaudiotagger.tag.FieldKey.LYRICS)
    private val EXTS = setOf("flac", "mp3", "m4a", "mp4", "aac", "ogg", "opus", "wma", "aiff", "aif", "wav", "dsf")
    private val setUp by lazy {
        runCatching { org.jaudiotagger.tag.TagOptionSingleton.getInstance().isAndroid = true }
        runCatching { java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF }
        true
    }

    /** One of the app's own downloads (written its own way, as before). */
    private fun isDownload(uri: String) = Music.lib.items.values.any { it.file == uri }

    private val paths = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** A song's real file (/storage/emulated/0/…), from its content:// address, or null. */
    fun pathOf(uri: String): String? {
        if (uri.startsWith("/")) return uri
        if (uri.startsWith("file:")) return android.net.Uri.parse(uri).path
        if (!uri.startsWith("content:")) return null
        paths[uri]?.let { return it }
        @Suppress("DEPRECATION") val col = android.provider.MediaStore.MediaColumns.DATA
        val p = runCatching { Music.ctx.contentResolver.query(android.net.Uri.parse(uri), arrayOf(col), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null } }.getOrNull() ?: return null
        paths[uri] = p
        return p
    }
    private fun ownFile(uri: String): java.io.File? {
        if (!Music.canWriteOwnFiles()) return null   // All files access: Settings › Tagger asks for it
        val f = java.io.File(pathOf(uri) ?: return null)
        return f.takeIf { it.isFile && it.canWrite() && it.extension.lowercase() in EXTS }
    }

    /** What stood in the way the last time a file's tags couldn't be written ("" if nothing did). */
    @Volatile private var lastError = ""

    /** A song's file name (from its real path), or null when the phone doesn't say where it is. */
    fun nameOf(uri: String): String? = pathOf(uri)?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

    /** Why this file's tags can't be written here, in words ("" when nothing known stands in the way). */
    fun whyNot(uri: String): String = runCatching {
        if (!Music.canWriteOwnFiles()) return@runCatching "the app isn't allowed to change your files: allow All files access (Settings › Tagger)"
        val f = java.io.File(pathOf(uri) ?: return@runCatching "the phone doesn't say where the file is, so it can't be opened to write")
        when {
            !f.isFile -> "the file isn't where the phone says it is (${f.name})"
            !f.canWrite() -> "the phone doesn't let the app change this file (${f.name})"
            f.extension.lowercase() !in EXTS -> "." + f.extension.lowercase() + " files can't be tagged here"
            else -> lastError
        }
    }.getOrDefault("")

    /** The app's downloaded FLACs; and, with All files access, any music file of yours. */
    fun canWrite(uri: String) = Music.lib.items.values.any { it.file == uri && it.format == "flac" } ||
        (!isDownload(uri) && runCatching { ownFile(uri) != null }.getOrDefault(false))

    /** WAVs are tagged by hand ([WavTags], as on the PC): their ID3 chunk (where the cover is) and LIST INFO, every other
     *  chunk kept. The tag library can read a WAV's LIST INFO only and write it back without its ID3 chunk, cover and all. */
    private fun isWav(f: java.io.File) = f.extension.equals("wav", true)

    fun read(ctx: Ctx, uri: String): Map<String, String>? = runCatching {
        if (!isDownload(uri)) ownFile(uri)?.let { f ->
            if (isWav(f)) WavTags.read(f)?.let { w -> return@runCatching KEYS.keys.filter { it != "lyrics" }.associateWith { w[it].orEmpty() } }
            setUp
            val tag = org.jaudiotagger.audio.AudioFileIO.read(f).tag ?: return@runCatching KEYS.mapValues { "" }
            return@runCatching KEYS.filterKeys { it != "lyrics" }.mapValues { (_, k) -> runCatching { tag.getAll(k).filter { it.isNotBlank() }.joinToString(", ") }.getOrDefault("") }
        }
        val tags = TagScan.read(uri).first
        listOf("title", "artist", "albumartist", "album", "tracknumber", "date", "genre").associateWith { tags[it] ?: "" }
    }.getOrNull()

    /** The disc the song is on, from its tags ("2", "2/3"); 0 when they don't say or it can't be read (your own
     *  files need All files access; the app's downloads are read their own way). */
    fun disc(ctx: Ctx, uri: String): Int = runCatching {
        fun num(s: String?) = s?.trim()?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
        if (isDownload(uri)) return@runCatching num(Music.ctx.openIn(uri)!!.buffered().use { s -> Flac.comments(Flac.readHeader(s))["DISCNUMBER"]?.firstOrNull() })
        val f = ownFile(uri) ?: return@runCatching 0
        if (isWav(f)) return@runCatching 0
        setUp
        val tag = org.jaudiotagger.audio.AudioFileIO.read(f).tag ?: return@runCatching 0
        num(tag.getFirst(org.jaudiotagger.tag.FieldKey.DISC_NO))
    }.getOrDefault(0)

    /** Your own file's tags written in its own format ("" clears a field); true if it worked. */
    fun writeOwn(uri: String, changes: Map<String, String>): Boolean = runCatching {
        val real = fileOf(uri) ?: error("can't write it")
        // On a copy, checked, then put over yours (SafeWrite): never half a file
        SafeWrite.edit(Music.ctx, real.path, real.extension.lowercase()) { f -> writeInto(f, changes) }
        // Android's list of songs reads the new tags
        runCatching { android.media.MediaScannerConnection.scanFile(Music.ctx, arrayOf(real.path), null, null) }
        lastError = ""
        true
    }.getOrElse { lastError = "the tag library said: " + (it.message?.take(90) ?: it.javaClass.simpleName); false }

    /** The changes written into f (a copy of your file). */
    private fun writeInto(f: java.io.File, changes: Map<String, String>) {
        if (isWav(f)) {
            val rest = changes.filterKeys { it != "lyrics" }
            val ok = (rest.isEmpty() || runCatching { WavTags.write(f, rest); true }.getOrDefault(false)) &&
                (changes["lyrics"] == null || runCatching { TagWrite.wav(f, changes["lyrics"], emptyMap()) }.getOrDefault(false))
            if (ok) return
        }
        setUp
        val af = org.jaudiotagger.audio.AudioFileIO.read(f)
        val tag = af.tagOrCreateAndSetDefault
        // The cover (and every other picture) as it was: a tag write never takes it off
        val pictures = runCatching { tag.artworkList.toList() }.getOrDefault(emptyList())
        for ((k, v) in changes) {
            val key = KEYS[k] ?: continue
            if (v.isEmpty()) runCatching { tag.deleteField(key) } else tag.setField(key, v)
        }
        af.commit()
        if (pictures.isNotEmpty()) keepPictures(f, pictures)
    }

    /** After a write: the file's pictures put back if the tag library dropped them (it has with some files), so a
     *  song's cover never goes when its other tags change. */
    private fun keepPictures(f: java.io.File, pictures: List<org.jaudiotagger.tag.images.Artwork>) = runCatching {
        val af = org.jaudiotagger.audio.AudioFileIO.read(f)
        val tag = af.tagOrCreateAndSetDefault
        if (runCatching { tag.artworkList.isNotEmpty() }.getOrDefault(false)) return@runCatching
        for (p in pictures) tag.addField(p)
        af.commit()
        Crashes.note("Put the cover back into ${f.name}: the tag write had dropped it")
    }

    fun write(ctx: Ctx, uri: String, changes: Map<String, String>): Boolean {
        // A download that isn't a FLAC (an Opus or M4A from YouTube) has no writer of the app's own: it is written
        // as a file of yours is, through the tag library. (It was refused outright: "only FLAC files…")
        val download = Music.lib.items.values.firstOrNull { it.file == uri }
        val other = download != null && download.format != "flac"
        return if ((download == null && ownFile(uri) != null) || (other && fileOf(uri) != null)) {
            val before = runCatching { read(ctx, uri) }.getOrNull()
            writeOwn(uri, changes).also { ok -> if (ok && before != null) TagSync.note(before["title"].orEmpty(), before["artist"].orEmpty(), changes) }
        }
        // (such a download whose file can't be reached: said so. Handing it to Tags.write sent it straight back here,
        // round and round until the app ran out of room, which is how it came to be refused without a word)
        else if (other) { lastError = ""; false }
        else runCatching { Tags.write(ctx, uri, changes); lastError = ""; true }.getOrElse { lastError = it.message?.take(90).orEmpty(); false }
    }

    /** The file to write: a file of yours (with All files access), or one of the app's own downloads, which it may
     *  always change. */
    private fun fileOf(uri: String): java.io.File? = ownFile(uri) ?: runCatching {
        if (!isDownload(uri)) return@runCatching null
        java.io.File(pathOf(uri) ?: return@runCatching null).takeIf { it.isFile && it.canWrite() && it.extension.lowercase() in EXTS }
    }.getOrNull()

    /** The picture inside the file, whatever its format (it read FLACs' only, so a sync took every MP3's, M4A's and
     *  WAV's for one with no cover and put the other device's in its place). */
    fun cover(ctx: Ctx, uri: String): ByteArray? = runCatching { embeddedPicture(ctx, uri) }.getOrNull()
        ?: runCatching { TagScan.read(uri).second }.getOrNull()
    fun hasCover(ctx: Ctx, uri: String) = cover(ctx, uri) != null
    fun writeCover(ctx: Ctx, uri: String, jpeg: ByteArray): Boolean =
        if (!CoverGuard.allowed()) false   // Prevent album cover changes
        else if (!isDownload(uri) && ownFile(uri) != null) runCatching {
            setUp
            val real = ownFile(uri)!!
            SafeWrite.edit(Music.ctx, real.path, real.extension.lowercase()) { f ->   // on a copy, checked, then put over yours
                if (isWav(f) && runCatching { WavTags.write(f, emptyMap(), jpeg); true }.getOrDefault(false)) return@edit
                val af = org.jaudiotagger.audio.AudioFileIO.read(f)
                val tag = af.tagOrCreateAndSetDefault
                val art = org.jaudiotagger.tag.images.ArtworkFactory.getNew().apply { binaryData = jpeg; mimeType = "image/jpeg"; pictureType = 3 }
                tag.deleteArtworkField(); if (jpeg.isNotEmpty()) tag.setField(art)   // (an empty picture: the cover taken off)
                af.commit()
            }
            runCatching { android.media.MediaScannerConnection.scanFile(Music.ctx, arrayOf(real.path), null, null) }
            true
        }.getOrDefault(false)
        else runCatching { Covers.write(ctx, uri, jpeg); true }.getOrDefault(false)

    /** One of the app's own downloads moved: dest is its new folder ("Music/Artists/Sody/Let Go - Single/", the name
     *  kept) or folder and name. Its address stays the same. Only the app's own files (Android 10 and up). */
    fun move(ctx: Ctx, src: String, dest: String): String? = runCatching {
        if (!src.startsWith("content:") || android.os.Build.VERSION.SDK_INT < 29) return null
        // Never over a file that's there already: Android would name it "… (1)", or refuse
        if (java.io.File(dest).exists()) return null
        val was = pathOf(src)   // (where it was: its lyrics file beside it goes with it)
        val rel = dest.removePrefix("/storage/emulated/0/").substringBeforeLast('/').trim('/') + "/"
        val name = dest.substringAfterLast('/')
        val cv = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, rel)
            if (name.isNotBlank()) put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
        }
        if (ctx.contentResolver.update(android.net.Uri.parse(src), cv, null, null) <= 0) return null
        // Checked: the song is where it was asked to go (its address unchanged), else it's said to have failed
        paths.remove(src)
        val now = pathOf(src)
        if (now == null || now.trimEnd('/') != dest.trimEnd('/')) return null
        // A lyrics file of the same name beside it (.lrc, .txt) goes with it, renamed to match, as on the PC
        if (was != null && canMove) runCatching {
            val from = java.io.File(was); val to = java.io.File(now)
            for (ext in listOf("lrc", "txt")) java.io.File(from.parentFile, from.nameWithoutExtension + ".$ext").takeIf { it.isFile }?.let { l ->
                val t = java.io.File(to.parentFile, to.nameWithoutExtension + ".$ext")
                if (!t.exists()) l.renameTo(t)
            }
        }
        src
    }.getOrNull()
    /** With All files access the folders can be looked in (for where a moved song goes, never over another file). */
    fun exists(path: String) = runCatching { canMove && java.io.File(path).exists() }.getOrDefault(false)
    fun subfolders(folder: String): List<String> = runCatching {
        if (!canMove) emptyList() else java.io.File(folder).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty() }.getOrDefault(emptyList())
    /** Every file under the folders (path -> size) and every folder, as the PC's. */
    fun listTree(roots: List<String>): Pair<Map<String, Long>, List<String>> {
        if (!canListFolders) return emptyMap<String, Long>() to emptyList()
        val files = LinkedHashMap<String, Long>(); val dirs = ArrayList<String>()
        for (r in roots) runCatching { java.io.File(r).walkTopDown().forEach { f -> if (f.isDirectory) dirs += f.path else files[f.path] = f.length() } }
        return files to dirs
    }

    /** Empty folders' delete: a folder and what's left in it (pictures, lyrics files…), deleted for good (a phone has no
     *  Recycle Bin), and only when no music is in it, looked at again here. */
    fun removeFolder(path: String): Boolean = runCatching {
        if (!canListFolders) return false
        val top = java.io.File(path)
        val p = path.trimEnd('/')
        if (!top.isDirectory || p == "/storage/emulated/0" || p.endsWith("/storage/emulated/0/Music")) return false
        val names = top.walkTopDown().filter { it.isFile }.map { it.name }.toList()
        if (hasMusic(names)) return false
        val ok = top.deleteRecursively()
        runCatching { android.media.MediaScannerConnection.scanFile(Music.ctx, arrayOf(path), null, null) }
        ok
    }.getOrDefault(false)

    /** The folder a song's file is in, as a full path ("/storage/emulated/0/Music/…"), asked of Android before the
     *  file goes; null if unknown. */
    fun folderOf(ctx: Ctx, uri: String): String? = runCatching {
        if (uri.startsWith("/")) return@runCatching java.io.File(uri).parent
        if (!uri.startsWith("content:")) return@runCatching null
        val col = android.provider.MediaStore.MediaColumns.RELATIVE_PATH
        ctx.contentResolver.query(android.net.Uri.parse(uri), arrayOf(col), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
        }?.let { org.omarchy.music.ui.fullFolderPath(it.trimEnd('/')) }
    }.getOrNull()

    /** Every file in a folder and below (paths inside it), or null if it isn't there or can't be read. */
    fun filesUnder(folder: String): List<String>? = runCatching {
        val top = java.io.File(folder)
        if (!top.isDirectory) return null
        val out = ArrayList<String>()
        fun walk(d: java.io.File, rel: String): Boolean {
            val kids = d.listFiles() ?: return false
            for (k in kids) {
                val p = if (rel.isEmpty()) k.name else "$rel/${k.name}"
                if (k.isDirectory) { if (!walk(k, p)) return false } else out += p
            }
            return true
        }
        if (walk(top, "")) out else null
    }.getOrNull()

    /** A folder with nothing left in it, deleted (the phone has no Recycle Bin for folders): only empty folders go,
     *  deepest first, so no file is ever deleted here, and Android refuses what the app may not remove. True if it went. */
    fun cleanFolder(path: String): Boolean = runCatching {
        fun rm(d: java.io.File): Boolean {
            d.listFiles()?.forEach { if (it.isDirectory) rm(it) }
            return d.delete()   // fails while anything is left in it
        }
        val top = java.io.File(path)
        top.isDirectory && rm(top)
    }.getOrDefault(false)
}
