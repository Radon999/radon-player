package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Windows' media controls (the volume flyout, the lock screen, widgets and wallpapers that show what's playing): the
 *  installer's radon-media.exe (desktop/smtc) shows the song there and passes the buttons back. What plays goes to it
 *  as a line of JSON whenever it changes (and every few seconds for the position); its buttons come back as words. */
object MediaSession {
    private var proc: Process? = null

    fun start() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val exe = Bins.helper("radon-media") ?: return
        val p = runCatching { ProcessBuilder(exe).redirectError(ProcessBuilder.Redirect.DISCARD).start() }
            .onFailure { logWarn("media controls", it) }.getOrNull() ?: return
        proc = p
        Thread({
            runCatching {
                p.inputStream.bufferedReader().forEachLine { w ->
                    Music.scope.launch(Dispatchers.Main) {
                        when {
                            w == "play" -> if (Music.paused) Music.togglePause()
                            w == "pause" || w == "stop" -> if (!Music.paused) Music.togglePause()
                            w == "toggle" -> Music.togglePause()
                            w == "next" -> Music.next()
                            w == "previous" -> Music.previous()
                            w.startsWith("seek ") -> w.removePrefix("seek ").toDoubleOrNull()?.let { Music.seek(it) }
                        }
                    }
                }
            }
        }, "media-controls").apply { isDaemon = true }.start()
        val out = p.outputStream.bufferedWriter()
        Music.scope.launch {
            var last = ""; var sentAt = 0L
            while (p.isAlive) {
                val t = Music.current()
                val key = "${t?.id}|${Music.paused}|${Music.duration}"
                val now = System.currentTimeMillis()
                if (key != last || now - sentAt > 5000) {
                    last = key; sentAt = now
                    val line = buildJsonObject {
                        put("title", t?.title ?: ""); put("artist", t?.artist ?: ""); put("album", t?.album ?: "")
                        put("art", t?.thumb?.takeIf { it.startsWith("http") } ?: "")
                        put("playing", t != null && !Music.paused)
                        put("position", Music.position); put("duration", Music.duration.takeIf { it > 0 } ?: (t?.duration ?: 0.0))
                    }.toString()
                    val ok = kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { out.write(line + "\n"); out.flush() }.isSuccess }
                    if (!ok) break
                }
                delay(500)
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { out.write("quit\n"); out.flush() }; p.destroy() })
    }
}
