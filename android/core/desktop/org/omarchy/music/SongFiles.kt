package org.omarchy.music

import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/** Other device's songs, the PC's side of copying files ([SongCopy] is the phone's): the phone fetches a file of this
 *  PC's own music (GET /song-file; nothing else on the computer is ever handed out), and sends one of its own here
 *  (PUT /song-file), saved as downloads are (by artist and album, in the download folder), written to a hidden part
 *  file beside it first and only then given its name: never over another file, and never twice (the same file sent
 *  again is found and kept once). Each request carries the paired phone's key ([RemoteHost] checks it). */
object SongFiles {
    private fun json(ex: HttpExchange, code: Int, body: JsonObject) {
        val data = body.toString().toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(code, data.size.toLong())
        ex.responseBody.use { it.write(data) }
    }
    private fun err(ex: HttpExchange, code: Int, m: String) = json(ex, code, buildJsonObject { put("error", m) })

    /** The files of this PC's own music: your folders' songs and your downloads'. */
    internal fun listed(): Set<String> {
        val out = HashSet<String>()
        runCatching { Music.local.files.forEach { t -> t.file?.takeIf { it.isNotEmpty() }?.let { out += it } } }
        runCatching { synchronized(Music.lib) { Music.lib.items.values.forEach { if (it.file.isNotEmpty()) out += it.file } } }
        return out
    }

    private fun fileOf(ref: String): File = if (ref.startsWith("file:")) runCatching { File(java.net.URI(ref)) }.getOrElse { File(ref.removePrefix("file://")) } else File(ref)

    /** A file's SHA-256, kept while the file stays as it is (a fetch carried on after a drop doesn't read it all again). */
    private val shas = ConcurrentHashMap<String, Triple<Long, Long, String>>()
    private fun shaOf(f: File): String {
        val size = f.length(); val mod = f.lastModified()
        shas[f.path]?.let { (s, m, h) -> if (s == size && m == mod) return h }
        return SongCopy.sha256(f.inputStream()).also { shas[f.path] = Triple(size, mod, it) }
    }

    /** GET /song-file?path=…: one of this PC's songs, from the start or from where a fetch stopped (Range). */
    fun serve(ex: HttpExchange, ref: String?) {
        if (ref == null || !SongTransferRules.servable(ref, listed())) return err(ex, 403, "that isn't one of the songs on this PC")
        val f = fileOf(ref)
        if (!f.isFile) return err(ex, 404, "that song's file isn't there any more")
        val size = f.length()
        val start = SongTransferRules.rangeStart(ex.requestHeaders.getFirst("Range"), size) ?: run {
            ex.responseHeaders.add("Content-Range", "bytes */$size"); return err(ex, 416, "that part of the file isn't there")
        }
        val h = ex.responseHeaders
        h.add("Content-Type", "application/octet-stream")
        h.add("Accept-Ranges", "bytes")
        h.add("X-Sha256", shaOf(f)); h.add("X-Size", size.toString())
        h.add("X-Name", URLEncoder.encode(f.name, "UTF-8"))
        if (start > 0) h.add("Content-Range", "bytes $start-${size - 1}/$size")
        ex.sendResponseHeaders(if (start > 0) 206 else 200, size - start)
        ex.responseBody.use { out ->
            f.inputStream().use { ins ->
                ins.channel.position(start)
                ins.copyTo(out, 1 shl 16)
            }
        }
    }

    /** What a phone says about a file it sends. */
    private class Incoming(val sha: String, val size: Long, val name: String, val ref: String?, val song: Track, val id: String?, val source: String?)

    private fun incoming(q: Map<String, String>): Incoming? {
        val sha = q["sha256"]?.takeIf { SongTransferRules.validSha(it) } ?: return null
        val size = q["size"]?.toLongOrNull()?.takeIf { it in 1..MAX } ?: return null
        val name = q["name"]?.takeIf { SongTransferRules.audioExt(it) != null } ?: return null
        val song = Track("", "file", q["title"].orEmpty().take(300), q["artist"].orEmpty().take(300), q["album"].orEmpty().take(300),
            q["duration"]?.toDoubleOrNull() ?: 0.0)
        return Incoming(sha, size, name.take(300), q["ref"], song, q["id"], q["source"])
    }

    private const val MAX = 8L shl 30   // bytes a song file may be (8 GB: a long hi-res mix is far less)

    /** The folder a sent file goes in and its name, as downloads are laid out (made if it isn't there). */
    private fun place(p: Incoming): Pair<File, String> {
        val root = Music.downloadFolder()
        val (where, name) = SongTransferRules.savePlace(root, p.song.artist, p.song.album, p.song.title.ifBlank { p.name.substringBeforeLast('.') }, p.name)!!
        fun dirOf(w: String) = File(w).takeIf { it.isAbsolute } ?: File(musicRoot, w.removePrefix("Music/"))
        val dir = dirOf(where)
        // (the names were made safe, so it can't be anywhere else: checked all the same)
        val base = dirOf(root).canonicalPath
        if (!dir.canonicalPath.startsWith(base + File.separator) && dir.canonicalPath != base) throw IOException("not in the download folder")
        return dir to name
    }

    /** The same file here already (same name or a numbered one, same size and checksum), and the first free name. */
    private fun lookFor(dir: File, name: String, p: Incoming): Pair<File?, File> {
        for (n in 1..200) {
            val f = File(dir, SongTransferRules.nameTry(name, n))
            if (!f.exists()) return null to f
            if (f.isFile && f.length() == p.size && shaOf(f) == p.sha) return f to f
        }
        throw IOException("too many files named $name")
    }

    private fun partOf(dir: File, p: Incoming) = File(dir, ".radon-${p.sha.take(16)}.part")

    /** GET /song-file/part: how much of a file the PC has (to carry on from), or that it has it all already. */
    fun partState(ex: HttpExchange, q: Map<String, String>) {
        val p = incoming(q) ?: return err(ex, 400, "that isn't a song file")
        val (dir, name) = place(p)
        val (same, _) = synchronized(this) { lookFor(dir, name, p) }
        if (same != null) {
            land(same, p)
            return json(ex, 200, buildJsonObject { put("have", p.size); put("done", true); put("path", same.path) })
        }
        val part = partOf(dir, p)
        json(ex, 200, buildJsonObject { put("have", if (part.isFile) part.length() else 0L); put("done", false) })
    }

    /** PUT /song-file?…&offset=N: a file from the phone (or the rest of it), checked, then saved and added to your music. */
    fun receive(ex: HttpExchange, q: Map<String, String>, phone: String) {
        val started = System.currentTimeMillis()
        val p = incoming(q) ?: run { drain(ex); return err(ex, 400, "that isn't a song file") }
        val (dir, name) = place(p)
        synchronized(this) { lookFor(dir, name, p) }.first?.let { same ->
            drain(ex)
            land(same, p)
            return json(ex, 200, buildJsonObject { put("ok", true); put("already", true); put("path", same.path) })
        }
        dir.mkdirs()
        val part = partOf(dir, p)
        val offset = q["offset"]?.toLongOrNull() ?: 0L
        val have = if (part.isFile) part.length() else 0L
        if (offset != have || offset >= p.size) { drain(ex); return json(ex, 409, buildJsonObject { put("have", have) }) }
        val title = p.song.title.ifBlank { p.name }
        var got = offset
        var shown = -1
        try {
            FileOutputStream(part, offset > 0).use { out ->
                ex.requestBody.use { ins ->
                    val buf = ByteArray(1 shl 16)
                    while (got < p.size) {
                        if (SongCopy.cancelledAt > started) throw IOException("stopped")
                        val n = ins.read(buf, 0, minOf(buf.size.toLong(), p.size - got).toInt()); if (n < 0) break
                        out.write(buf, 0, n); got += n
                        val pct = (got * 100 / p.size).toInt()
                        if (pct != shown) { shown = pct; Music.scope.launch { SongCopy.note = "Receiving “$title” from $phone · $pct%" } }
                    }
                }
            }
        } catch (e: IOException) {
            if (SongCopy.cancelledAt > started) { part.delete(); Music.scope.launch { SongCopy.note = "" }; return err(ex, 410, "copying was stopped on the PC") }
            Music.scope.launch { SongCopy.note = "" }
            throw e   // (the phone went: what came is kept, and the phone carries on from there)
        }
        Music.scope.launch { SongCopy.note = "" }
        if (got != p.size) return err(ex, 400, "the file didn't come whole")
        if (shaOf(part) != p.sha) { part.delete(); return err(ex, 422, "the file didn't come through whole (its checksum differs)") }
        val dest = synchronized(this) {
            val (same, free) = lookFor(dir, name, p)
            if (same != null) { part.delete(); same }
            else Files.move(part.toPath(), free.toPath()).toFile()   // never over a file that is there
        }
        land(dest, p)
        json(ex, 200, buildJsonObject { put("ok", true); put("already", false); put("path", dest.path) })
    }

    /** The file in your music (once: the same song again is the same entry), and off the list asked of the phone. */
    private fun land(f: File, p: Incoming) {
        if (synchronized(Music.lib) { Music.lib.items.values.none { it.file == f.path } })
            SongCopy.addToLibrary(p.id, p.source, p.sha, p.song, f.path, f.name)
        SongCopy.received(p.ref)
    }

    /** POST /song-file/drop: the phone can't send a file asked for (it isn't there any more): off the list, and said. */
    fun dropped(body: JsonObject) = SongCopy.received(body["ref"].str, body["why"].str ?: "the phone couldn't send it")

    /** What the phone is sending, read and let go (it's sending it already; the answer is read once it's sent). */
    private fun drain(ex: HttpExchange) = runCatching { ex.requestBody.use { it.copyTo(java.io.OutputStream.nullOutputStream(), 1 shl 16) } }
}
