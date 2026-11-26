package org.omarchy.music

import com.sun.net.httpserver.HttpServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.security.MessageDigest

/** Other device's songs, the PC's side of copying files: the phone remote's own request handler (RemoteHost) behind a
 *  server of the test's own, asked as the phone asks it. A song file of the PC's music comes out whole with the paired
 *  phone's key, and nothing comes out without it or for a file that isn't one of the PC's songs; a file the phone sends
 *  lands once, laid out as downloads are, carried on after a drop, never over another file. */
class SongFilesTest {
    companion object {
        private const val KEY = "song-files-test-key"
        private lateinit var server: HttpServer
        private var port = 0
        private lateinit var dl: File

        @BeforeClass @JvmStatic fun start() {
            val ctx = Harness.ctx
            File(ctx.filesDir, "remote-tokens.json").writeText("{\"$KEY\":\"Test phone\"}")
            RemoteHost.init(ctx)
            dl = File(ctx.filesDir, "copied-in").apply { mkdirs() }
            Harness.ui { Music.downloadDir = dl.path }
            server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8).apply {
                createContext("/") { ex -> runCatching { RemoteHost.handle(ex) }.onFailure { runCatching { ex.sendResponseHeaders(500, -1); ex.close() } } }
                executor = java.util.concurrent.Executors.newFixedThreadPool(4)
                start()
            }
            port = server.address.port
        }

        @AfterClass @JvmStatic fun stop() { server.stop(0); Harness.ui { Music.downloadDir = "" } }
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun bytes(n: Int, seed: Long) = ByteArray(n).also { java.util.Random(seed).nextBytes(it) }

    private class Got(val code: Int, val body: ByteArray, val headers: okhttp3.Headers) { val text get() = String(body) }
    private fun call(method: String, path: String, key: String? = KEY, body: ByteArray? = null, range: String? = null): Got {
        val req = Request.Builder().url(url(path)).apply {
            if (key != null) header("Authorization", "Bearer $key")
            if (range != null) header("Range", range)
            if (method == "PUT") put((body ?: ByteArray(0)).toRequestBody("application/octet-stream".toMediaType()))
        }.build()
        HTTP.newCall(req).execute().use { r -> return Got(r.code, r.body?.bytes() ?: ByteArray(0), r.headers) }
    }

    /** A song file of the PC's music, kept as a download. */
    private fun pcSong(name: String, data: ByteArray): File {
        val dir = File(Harness.ctx.filesDir, "pc-music").apply { mkdirs() }
        val f = File(dir, name).apply { writeBytes(data) }
        Music.lib.add(Track("sf-" + name.hashCode(), "tidal", name.substringBefore('.'), "PC Band"), f.path, null, "flac")
        return f
    }

    @Test fun aSongOfThePcComesOutWholeWithTheKey() {
        val data = bytes(3_000_123, 1)
        val f = pcSong("Fetched Song.flac", data)
        val got = call("GET", "/song-file?path=" + enc(f.path))
        assertEquals(200, got.code)
        assertArrayEquals("the very same bytes", data, got.body)
        assertEquals(sha(data), got.headers["X-Sha256"])
        assertEquals(data.size.toString(), got.headers["X-Size"])
        assertEquals("Fetched+Song.flac", got.headers["X-Name"])
        // …and the rest of it, after a drop
        val rest = call("GET", "/song-file?path=" + enc(f.path), range = "bytes=1000000-")
        assertEquals(206, rest.code)
        assertArrayEquals(data.copyOfRange(1_000_000, data.size), rest.body)
        assertEquals(416, call("GET", "/song-file?path=" + enc(f.path), range = "bytes=${data.size}-").code)
    }

    @Test fun nothingComesOutWithoutTheKeyOrOutsideTheMusic() {
        val f = pcSong("Guarded Song.flac", bytes(5000, 2))
        assertEquals("no key", 401, call("GET", "/song-file?path=" + enc(f.path), key = null).code)
        assertEquals("a key that isn't a paired phone's", 401, call("GET", "/song-file?path=" + enc(f.path), key = "guess").code)
        val tokens = File(Harness.ctx.filesDir, "remote-tokens.json")
        val r = call("GET", "/song-file?path=" + enc(tokens.path))
        assertEquals("the app's own data", 403, r.code)
        assertFalse("not a byte of it", r.text.contains(KEY))
        val stray = File(f.parentFile, "not-a-song-of-yours.flac").apply { writeBytes(bytes(100, 3)) }
        assertEquals("a file beside your songs that isn't one of them", 403, call("GET", "/song-file?path=" + enc(stray.path)).code)
        val climb = f.parentFile.path + "/../pc-music/" + f.name
        assertEquals("the same song by a path that climbs", 403, call("GET", "/song-file?path=" + enc(climb)).code)
        assertEquals("nothing asked", 403, call("GET", "/song-file").code)
    }

    private fun q(data: ByteArray, name: String, title: String, artist: String, album: String, ref: String = "content://test/1", id: String = "lfm-upload-test") =
        "sha256=${sha(data)}&size=${data.size}&name=${enc(name)}&title=${enc(title)}&artist=${enc(artist)}&album=${enc(album)}" +
            "&ref=${enc(ref)}&id=${enc(id)}&source=lastfm&duration=200"

    @Test fun aFileFromThePhoneLandsOnceAsDownloadsAre() {
        val data = bytes(2_500_000, 4)
        val q = q(data, "03 Upload Song.flac", "Upload Song", "Phone Band", "Phone Album")
        assertEquals(401, call("PUT", "/song-file?$q&offset=0", key = null, body = data.copyOfRange(0, 100)).code)
        assertEquals("""{"have":0,"done":false}""", call("GET", "/song-file/part?$q").text)
        // The connection drops part-way: what came is kept, and it carries on from there
        val half = call("PUT", "/song-file?$q&offset=0", body = data.copyOfRange(0, 1_000_000))
        assertEquals(400, half.code)
        assertEquals("""{"have":1000000,"done":false}""", call("GET", "/song-file/part?$q").text)
        assertEquals("not from where it stopped", 409, call("PUT", "/song-file?$q&offset=0", body = data).code)
        val done = call("PUT", "/song-file?$q&offset=1000000", body = data.copyOfRange(1_000_000, data.size))
        assertEquals(done.text, 200, done.code)
        val f = File(dl, "Phone Band/Phone Album/03 Upload Song.flac")
        assertTrue(done.text.contains("\"already\":false"))
        assertArrayEquals(data, f.readBytes())
        assertEquals("in your music, as the song listed", f.path, Music.lib.items["lfm-upload-test"]?.file)
        // The same file again: found, not saved twice
        val again = call("PUT", "/song-file?$q&offset=0", body = data)
        assertEquals(200, again.code)
        assertTrue(again.text.contains("\"already\":true"))
        assertTrue(call("GET", "/song-file/part?$q").text.contains("\"done\":true"))
        assertEquals("one file, nothing half-written left", listOf("03 Upload Song.flac"), f.parentFile.list()!!.sorted())
        // Another file of the same name never goes over it
        val other = bytes(4000, 5)
        val o = call("PUT", "/song-file?" + q(other, "03 Upload Song.flac", "Upload Song", "Phone Band", "Phone Album", id = "lfm-upload-other") + "&offset=0", body = other)
        assertEquals(200, o.code)
        assertArrayEquals("yours as it was", data, f.readBytes())
        assertArrayEquals(other, File(f.parentFile, "03 Upload Song (2).flac").readBytes())
    }

    @Test fun aFileThatDidntComeWholeOrIsntASongIsntKept() {
        val data = bytes(10_000, 6)
        val wrong = data.copyOf().also { it[5000] = (it[5000] + 1).toByte() }
        val r = call("PUT", "/song-file?" + q(data, "Broken.flac", "Broken", "Check Band", "Check Album") + "&offset=0", body = wrong)
        assertEquals(422, r.code)
        assertFalse(File(dl, "Check Band").walkTopDown().any { it.isFile })
        assertEquals("not a song file", 400, call("PUT", "/song-file?" + q(data, "notes.txt", "N", "Check Band", "A") + "&offset=0", body = data).code)
        // Names that try to climb out stay in the download folder
        val sneaky = bytes(3000, 7)
        val s = call("PUT", "/song-file?" + q(sneaky, "x.flac", "../../../escape", "../..", "..", id = "lfm-sneaky") + "&offset=0", body = sneaky)
        assertEquals(s.text, 200, s.code)
        val landed = File(Music.lib.items["lfm-sneaky"]!!.file)
        assertTrue(landed.canonicalPath, landed.canonicalPath.startsWith(dl.canonicalPath + File.separator))
    }

    @Test fun thePcAsksThePhoneAndTheFileComes() {
        val ref = "content://media/external/audio/media/4242"
        val theirs = "{\"songs\":[{\"id\":\"\",\"source\":\"file\",\"title\":\"Wanted Song\",\"artist\":\"Want Band\",\"album\":\"Want Album\",\"file\":\"$ref\"}]}"
        LibraryMatch.take(theirs, "Test phone")
        val shown = LibraryMatch.missing().single { it.title == "Wanted Song" }
        assertEquals("never a file here", null, shown.file)
        Harness.ui {
            assertEquals("always copied, never streamed: ⇄ from your phone", "⇄ from your phone", SongCopy.how(shown))
            SongCopy.get(listOf(shown))
        }
        assertTrue(call("GET", "/sync/wanted").text.contains("\"copyWanted\":1"))
        assertTrue(call("GET", "/song-file/wanted").text.contains(ref))
        val data = bytes(6000, 8)
        val r = call("PUT", "/song-file?" + q(data, "Wanted Song.mp3", "Wanted Song", "Want Band", "Want Album", ref = ref, id = shown.id) + "&offset=0", body = data)
        assertEquals(r.text, 200, r.code)
        assertTrue("asked no more", call("GET", "/sync/wanted").text.contains("\"copyWanted\":0"))
        assertArrayEquals(data, File(dl, "Want Band/Want Album/Wanted Song.mp3").readBytes())
        Harness.until("off the list") { LibraryMatch.missing().none { it.title == "Wanted Song" } }
    }

    @Test fun aSongWithoutAFileOverThereIsNeverDownloaded() {
        val theirs = "{\"songs\":[{\"id\":\"\",\"source\":\"file\",\"title\":\"Fileless Song\",\"artist\":\"Nofile Band\",\"album\":\"Gone\"}]}"
        LibraryMatch.take(theirs, "Test phone")
        val shown = LibraryMatch.missing().single { it.title == "Fileless Song" }
        val before = Music.downloads.size
        Harness.ui {
            SongCopy.get(listOf(shown))
            assertEquals("nothing is sent to Downloads", before, Music.downloads.size)
            assertTrue(SongCopy.how(shown), SongCopy.how(shown).contains("Sync again"))
        }
    }

    @Test fun aSongYouHaveHereAsAFileIsntOffered() {
        // The other device has it as a Tidal download; this one has the same song as its own file under another name
        val mine = Music.mySongs().firstOrNull { it.isFile } ?: return
        val theirs = "{\"songs\":[{\"id\":\"123456\",\"source\":\"tidal\",\"title\":\"${mine.title}\",\"artist\":\"${mine.artist}\",\"album\":\"${mine.album}\",\"file\":\"content://x/1\"}]}"
        LibraryMatch.take(theirs, "Test phone")
        assertTrue("yours already: not listed as a streaming song to get", LibraryMatch.missing().none { it.title == mine.title })
    }
}
