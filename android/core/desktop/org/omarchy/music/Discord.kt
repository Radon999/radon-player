package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.RandomAccessFile

/** What you're playing on your Discord profile, as Spotify does ("Listening to …"), through the Discord app's own Rich
 *  Presence socket on this computer (nothing goes anywhere else), as the Python app does. Paused or stopped, it's
 *  cleared. With Discord closed it looks for it again every so often, and it finds Discord again after a restart.
 *  The name Discord shows ("Listening to VarMusic") is the name of the Discord application whose ID is used: it's set
 *  on Discord's developer portal, not here, so the ID is a setting ([setAppId]). */
object Discord {
    val supported = true
    /** The app's built-in Discord application, "Radon Player" (public; an application ID is no secret). */
    const val DEFAULT_APP_ID = "1566634240345383148"
    private const val APP_ID_KEY = "discord_app_id"
    private const val ICON = "https://raw.githubusercontent.com/Radon999/radon-player/radon-player/docs/icon.png"
    private const val DOWNLOAD = "https://github.com/Radon999/radon-player/releases/latest"
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    private var onNow by mutableStateOf(false)
    val on: Boolean get() = onNow
    /** For Settings: "" (not tried yet), "on", or why not. */
    var status by mutableStateOf("")
        private set

    private var pipe: RandomAccessFile? = null
    private var sock: java.nio.channels.SocketChannel? = null
    private var sent: JsonObject? = null
    @Volatile private var sentKey = ""
    @Volatile private var redo = false          // reconnect (the application ID changed)
    @Volatile private var plain = false         // Discord refused the full activity: send the basic one
    private var logged = ""                     // the last problem written to Settings › Problems (once each)

    private var idNow by mutableStateOf("")
    /** The Discord application ID in use: yours (Settings) or the built-in one. */
    val appId: String get() = idNow.ifEmpty { DEFAULT_APP_ID }
    /** Whether it's one of your own (not the built-in one). */
    val ownAppId: Boolean get() = idNow.isNotEmpty()

    fun init() {
        onNow = Prefs.on("discord")
        idNow = Prefs.text(APP_ID_KEY).trim()
        Thread({ run() }, "discord").apply { isDaemon = true }.start()
    }

    fun setOn(v: Boolean) { onNow = v; Prefs.set("discord", v) }

    /** Use the Discord application with this ID ("" for the built-in one): Discord shows its name. False if it isn't
     *  one (an application ID is a number of 17 to 20 digits). */
    fun setAppId(v: String): Boolean {
        val id = v.trim()
        if (id.isNotEmpty() && !Regex("\\d{17,20}").matches(id)) return false
        idNow = id; Prefs.setText(APP_ID_KEY, id)
        redo = true; plain = false; status = ""
        return true
    }

    private fun paths(): List<String> = if (windows) (0 until 10).map { "\\\\.\\pipe\\discord-ipc-$it" } else {
        val bases = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").mapNotNull { System.getenv(it) } + "/tmp"
        val subs = listOf("", "app/com.discordapp.Discord/", "app/dev.vencord.Vesktop/", "snap.discord/")
        (0 until 10).flatMap { i -> bases.distinct().flatMap { b -> subs.map { s -> "${b.trimEnd('/')}/${s}discord-ipc-$i" } } }
    }

    private fun connect() {
        for (p in paths()) {
            runCatching {
                if (windows) pipe = RandomAccessFile(p, "rw")
                else if (File(p).exists()) sock = java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX)
                    .apply { connect(java.net.UnixDomainSocketAddress.of(p)) }
            }
            if (pipe != null || sock != null) break
        }
        if (pipe == null && sock == null) throw java.io.IOException("Discord isn't open")
        send(0, buildJsonObject { put("v", 1); put("client_id", appId) })
        reply()   // READY, or an error (a wrong application ID: "Invalid Client ID")
    }

    private fun close() {
        runCatching { pipe?.close() }; runCatching { sock?.close() }
        pipe = null; sock = null; sent = null; sentKey = ""
    }

    private fun send(op: Int, data: JsonObject) {
        val body = data.toString().toByteArray()
        val frame = java.nio.ByteBuffer.allocate(8 + body.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(op).putInt(body.size).put(body).array()
        pipe?.write(frame) ?: sock?.write(java.nio.ByteBuffer.wrap(frame))
    }

    private fun read(n: Int): ByteArray {
        val out = ByteArray(n); var got = 0
        while (got < n) {
            val k = pipe?.read(out, got, n - got) ?: sock!!.read(java.nio.ByteBuffer.wrap(out, got, n - got))
            if (k <= 0) throw java.io.IOException("Discord closed the connection")
            got += k
        }
        return out
    }

    private fun reply(): JsonObject {
        val head = java.nio.ByteBuffer.wrap(read(8)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val op = head.int; val n = head.int
        val data = runCatching { JSON.parseToJsonElement(String(read(n))) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
        if (op == 2 || data["evt"].str == "ERROR") {
            val msg = (data["data"] as? JsonObject)?.get("message").str ?: data["message"].str ?: "Discord said no"
            throw Refused(if (op == 2) "Discord closed the connection: $msg" else msg)
        }
        return data
    }

    /** Discord said no to what was sent (not a lost connection). */
    private class Refused(msg: String) : Exception(msg)

    private fun text(v: String?) = (v ?: "").trim().take(128).padEnd(2, '⠀')

    /** The song's page on its service, for the button. */
    private fun page(t: Track): Pair<String, String>? = when {
        t.source == "ytm" -> "YouTube Music" to "https://music.youtube.com/watch?v=${t.id}"
        t.source == "tidal" && t.tidal != null -> "Tidal" to "https://tidal.com/browse/track/${t.tidal}"
        t.source == "apple" && t.apple != null -> "Apple Music" to "https://music.apple.com/song/${t.apple}"
        t.source == "spotify" && t.spotify != null -> "Spotify" to "https://open.spotify.com/track/${t.spotify}"
        else -> null
    }

    /** What to show now (null: nothing), and a key that changes only when it should be sent again. basic: only what
     *  every Discord takes (no buttons, links or the newer fields), for when it refused the full one. */
    private fun wanted(basic: Boolean): Pair<JsonObject?, String> {
        val t = Music.current()
        if (!on || t == null || Music.paused) return null to ""
        val start = System.currentTimeMillis() - (Music.position * 1000).toLong()
        val dur = Music.duration.takeIf { it > 0 } ?: t.duration
        val p = page(t)
        // The song's cover (Discord shows only pictures on the web: a file of yours gets its cover's address looked
        // up once, as Most played does), the app's icon until it's found; the icon stays small in the corner
        val cover = coverUrl(t) ?: ICON
        val act = buildJsonObject {
            put("type", 2)   // "Listening to …", as Spotify shows
            if (!basic) put("status_display_type", 0)
            put("details", text(t.title)); put("state", text(t.artist))
            putJsonObject("timestamps") {
                put("start", start)
                if (dur > 0) put("end", start + (dur * 1000).toLong().coerceAtLeast(1000))
            }
            putJsonObject("assets") {
                put("large_image", cover)
                if (t.album.isNotEmpty()) put("large_text", text(t.album))
                if (!basic) { put("small_image", ICON); put("small_text", "Radon Player") }
            }
            if (!basic) {
                p?.let { if (it.second.length <= 256) put("details_url", it.second) }
                put("buttons", buildJsonArray {
                    p?.let { if (it.second.length <= 512) add(buildJsonObject { put("label", "Play on ${it.first}"); put("url", it.second) }) }
                    add(buildJsonObject { put("label", "Get Radon Player"); put("url", DOWNLOAD) })
                })
            }
        }
        return act to "${t.id}|${start / 3000}|$cover"   // the bar a few seconds off isn't worth an update; a cover found is
    }

    /** A picture's address Discord takes: on the web, 256 characters at most (a longer one makes it refuse the whole activity). */
    private fun usable(u: String?) = u?.takeIf { it.startsWith("https://") && it.length <= 256 }

    /** Covers looked up for songs without a web picture (your files): song -> address ("" while looking, or none). */
    private val covers = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The song's cover as an address Discord can show, or null (not known yet: looked up in the background, and the
     *  activity is sent again once it's found). Streamed songs have theirs; your files' covers are found online
     *  (their service, Deezer, Last.fm, iTunes), once, and kept (SongCovers). */
    private fun coverUrl(t: Track): String? {
        usable(t.thumb)?.let { return it }
        usable(runCatching { sizedThumb(t.thumb, 640) }.getOrNull())?.let { return it }
        val k = norm(t.artist) + "|" + norm(t.title)
        covers[k]?.let { return usable(it) }
        usable(runCatching { SongCovers.known(t.title, t.artist) }.getOrNull())?.let { covers[k] = it; return it }
        covers[k] = ""
        Thread({
            val u = runCatching { SongCovers.look(t.copy(source = if (t.isFile) "lastfm" else t.source)) }.getOrDefault("")
            covers[k] = usable(u).orEmpty()
        }, "discord-cover").apply { isDaemon = true }.start()
        return null
    }

    /** A problem written to Settings › Problems (Crashes), once each, so it can be seen and sent; Discord simply not
     *  being open isn't one. */
    private fun log(e: Exception) {
        val msg = e.message ?: e.javaClass.simpleName
        if (msg == "Discord isn't open" || msg == logged) return
        logged = msg
        Crashes.caught(e, "Discord Rich Presence (application $appId)")
    }

    private fun setActivity(want: JsonObject?) {
        send(1, buildJsonObject {
            put("cmd", "SET_ACTIVITY"); put("nonce", java.util.UUID.randomUUID().toString())
            putJsonObject("args") { put("pid", ProcessHandle.current().pid()); want?.let { put("activity", it) } }
        })
        reply()
    }

    private fun run() {
        var last = 0L
        while (true) {
            Thread.sleep(1500)
            if (redo) { redo = false; close() }   // a new application ID: a new handshake
            val (want, key) = runCatching { wanted(plain) }.getOrElse { null to "" }
            // Sent again every half minute while playing even if nothing changed: a Discord that restarted (or a lost
            // connection) is found and shown the song again, not only at the next song
            val stale = want != null && System.currentTimeMillis() - last > 30_000
            if (key == sentKey && !stale) continue
            if (System.currentTimeMillis() - last < 4000) continue   // Discord takes 5 updates in 20 s
            last = System.currentTimeMillis()
            try {
                if (pipe == null && sock == null) { if (want == null) { sentKey = key; continue }; connect() }
                try { setActivity(want) } catch (e: Refused) {
                    if (want == null || plain) throw e
                    // Refused the full one (a newer field, a button, a picture): the basic one, from now on
                    log(e); plain = true
                    setActivity(wanted(true).first)
                }
                sent = want; sentKey = key; status = "on"
            } catch (e: Exception) {
                close()
                status = (e.message ?: "Discord isn't open").take(80)
                log(e)
                Thread.sleep(if (e is Refused) 60_000 else 15_000)
            }
        }
    }
}
