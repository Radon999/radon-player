package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

/** One Radon Player at a time, whatever version: the lock in the data folder, and the port an older version holds. */
class InstanceClaimTest {
    private fun freePort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
    private fun tempDir(): File = kotlin.io.path.createTempDirectory("radon-instance").toFile()

    @Test fun aSecondStartFindsTheFirst() {
        val dir = tempDir(); val port = freePort()
        // The first start in a process of its own holds the lock (a lock is per process: a child holds it here)
        val first = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp", System.getProperty("java.class.path"),
            "org.omarchy.music.InstanceClaimTest\$Holder", dir.path, port.toString()).redirectErrorStream(true).start()
        try {
            val said = first.inputStream.bufferedReader().readLine()
            assertEquals("the first start is the one", "MINE", said)
            assertEquals("a second start, any version, finds it running", Instance.Claim.RUNNING, Instance.claim(dir, port))
        } finally {
            first.destroy(); first.waitFor()
        }
    }

    @Test fun anOlderVersionHoldingOnlyThePortIsFound() {
        val dir = tempDir()
        ServerSocket(0, 8, InetAddress.getLoopbackAddress()).use { older ->
            // An older Radon Player: no lock of its own, listening on the port
            val t = Thread { runCatching { while (true) older.accept().close() } }.apply { isDaemon = true; start() }
            assertEquals(Instance.Claim.RUNNING, Instance.claim(dir, older.localPort))
            t.interrupt()
        }
    }

    /** Holds the claim in a process of its own until it's stopped, saying MINE once it has it. */
    object Holder {
        @JvmStatic fun main(args: Array<String>) {
            println(Instance.claim(File(args[0]), args[1].toInt()).name); System.out.flush()
            Thread.sleep(60_000)
        }
    }
}
