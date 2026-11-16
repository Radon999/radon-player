package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Looking artists up: their pictures and bios (kept on this device), a picture of your own choosing, and an
 *  artist's page from the first service that knows them. The rules are in [ArtistLookupRules]. [Music] keeps the
 *  names the screens call and hands on. */
object ArtistLookups {
    private val lookingUp = HashSet<String>()

    /** Look up the pictures and bios of the artists named, one after another off the main thread. */
    fun lookUp(names: List<String>) {
        val each = ArtistLookupRules.once(names) { norm(it) }
        val todo = each.filter { n -> !Pictures.settled(Pictures.known(n)) && synchronized(lookingUp) { lookingUp.add(norm(n)) } }
        if (todo.isEmpty()) return
        Music.scope.launch(Dispatchers.IO) {
            for (n in todo) {
                runCatching { Pictures.artist(n) }
                runCatching { Pictures.keepOnPhone(n) }   // the picture itself kept on the phone: not loaded again
                synchronized(lookingUp) { lookingUp.remove(norm(n)) }
                withContext(Dispatchers.Main) { Music.artistsVersion++ }
            }
        }
        // Ones looked up before pictures were kept on the phone: kept now (no lookup, just the picture)
        val unsaved = each.filter { n -> Pictures.known(n)?.let { it.saved.isEmpty() && it.image.startsWith("http") } == true }
        if (unsaved.isNotEmpty()) Music.scope.launch(Dispatchers.IO) {
            var got = 0
            for (n in unsaved) if (runCatching { Pictures.keepOnPhone(n) }.getOrNull() != null) got++
            if (got > 0) withContext(Dispatchers.Main) { Music.artistsVersion++ }
        }
    }

    /** The pictures of artists gone from your music (no songs of theirs left, and no plays in your stats): forgotten,
     *  their files deleted. */
    fun prunePictures() {
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList())
        if (songs.isEmpty()) return
        val keep = (songs.flatMap { ownArtists(it.artist) } + Stats.artists.map { it.name }).map { norm(it) }.toSet()
        Music.bg({ Pictures.prune(keep) })
    }

    /** Keep source (a picture's web address, or a content:// one you picked) as the artist's picture. */
    fun setPicture(name: String, source: String) {
        val ctx = Music.ctx
        Music.bg({
            val url = if (source.startsWith("content://")) {
                val f = File(File(ctx.filesDir, "artists").apply { mkdirs() }, "${randomHex(8)}.jpg")
                ctx.openIn(source)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                f.path
            } else source
            Pictures.chooseArtist(name, url)
        }) { _, err ->
            Music.toast(if (err != null) "Couldn't use it: ${err.message?.take(100)}" else "New picture for $name")
            Music.artistsVersion++
        }
    }

    /** (blocking) The artist's page from the first service that knows them. */
    fun info(name: String, prefer: String?): ArtistInfo? {
        var info: ArtistInfo? = null
        var last: Throwable? = null
        val names = ArtistLookupRules.names(name)
        for (key in ArtistLookupRules.order(prefer, Music.source)) {
            val src = Music.sources[key] ?: continue
            if (!ArtistLookupRules.asked(key, src.signedIn)) continue
            var found: ArtistInfo? = null
            for (n in names) {
                found = try { src.artist(n) } catch (e: Exception) { last = e; null }
                if (found != null) break
            }
            found ?: continue
            if (info == null) info = found.copy(source = key, topSource = key)
            else if (found.top.isNotEmpty()) info = info.copy(top = found.top, topSource = key)
            if (info.top.isNotEmpty()) break
        }
        if (info == null && last != null) throw last!!
        return info
    }
}
