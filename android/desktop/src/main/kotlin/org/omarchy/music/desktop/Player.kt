package org.omarchy.music.desktop

import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

/** Playing a song through mpv, as the Python app does (the same mpv, from its installer): one mpv for each song,
 *  told to pause or seek over its IPC pipe. */
class Player {
    private var proc: Process? = null
    private val windows = System.getProperty("os.name").lowercase().contains("win")
    private val pipe = if (windows) "\\\\.\\pipe\\radon-next-mpv" else File(System.getProperty("java.io.tmpdir"), "radon-next-mpv.sock").path

    /** mpv: RADON_MPV, else the Python app's own (Omarchy Music's bin folder on Windows), else mpv on the PATH. */
    fun mpv(): String = System.getenv("RADON_MPV")
        ?: listOfNotNull(System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Omarchy Music\\bin\\mpv.exe" })
            .firstOrNull { File(it).exists() }
        ?: "mpv"

    fun play(file: File, onEnd: () -> Unit = {}) {
        stop()
        val p = ProcessBuilder(mpv(), "--no-video", "--really-quiet", "--input-ipc-server=$pipe", file.path)
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        proc = p
        p.onExit().thenRun { if (proc === p) { proc = null; onEnd() } }
    }

    val playing get() = proc?.isAlive == true

    fun togglePause() = send("""{"command":["cycle","pause"]}""")

    fun seek(seconds: Double) = send("""{"command":["seek",$seconds,"relative"]}""")

    fun stop() {
        val p = proc ?: return
        proc = null
        p.destroy()
    }

    /** One command to mpv over its pipe (a named pipe on Windows, a Unix socket elsewhere). */
    private fun send(json: String) {
        if (!playing) return
        runCatching {
            if (windows) RandomAccessFile(pipe, "rw").use { it.write((json + "\n").toByteArray()) }
            else SocketChannel.open(StandardProtocolFamily.UNIX).use {
                it.connect(UnixDomainSocketAddress.of(pipe))
                it.write(ByteBuffer.wrap((json + "\n").toByteArray()))
            }
        }
    }
}
