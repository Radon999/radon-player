package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which output is Windows' default, and a song's real bit depth. */
class WinOutputRulesTest {
    @Test fun aDevicesIdGivesItsRegistryKey() {
        assertEquals("{6f2c1a3b-0000-4a2b-9c1d-aabbccddeeff}", WinOutputRules.guidOf("{0.0.0.00000000}.{6F2C1A3B-0000-4A2B-9C1D-AABBCCDDEEFF}"))
        assertNull(WinOutputRules.guidOf("not an id"))
        assertNull(WinOutputRules.guidOf(""))
    }

    @Test fun theDeviceWindowsNamesWinsOverTheOneLastPickedByHand() {
        val active = listOf("{monitor}" to "2026-11-01", "{ka17}" to "", "{realtek}" to "2025-01-01")
        assertEquals("the DAC Windows chose as it was plugged in", "{ka17}", WinOutputRules.pick(active, "{KA17}"))
        assertEquals("Windows couldn't be asked: as before", "{monitor}", WinOutputRules.pick(active, null))
        assertEquals("it names one that isn't active: as before", "{monitor}", WinOutputRules.pick(active, "{gone}"))
        assertNull(WinOutputRules.pick(emptyList(), "{ka17}"))
    }

    @Test fun theVisualizerListensToYourFileOnlyWhenItIsOnThisPc() {
        val here = setOf("C:/Music/a.flac")
        assertEquals("C:/Music/a.flac", WinOutputRules.spectrumSource(listOf("C:/Music/a.flac"), "C:/Temp/radon-stream-1.mpd") { it in here })
        assertEquals("the file is only on the phone: what the player plays", "C:/Temp/radon-stream-1.mpd",
            WinOutputRules.spectrumSource(listOf("/storage/emulated/0/Music/a.flac", null), "C:/Temp/radon-stream-1.mpd") { it in here })
        assertEquals("a phone's own address is never a file here", "https://x/stream",
            WinOutputRules.spectrumSource(listOf("content://media/1"), "https://x/stream") { true })
        assertEquals("the second name is the one that's here", "C:/Music/a.flac",
            WinOutputRules.spectrumSource(listOf("D:/gone.flac", "C:/Music/a.flac"), null) { it in here })
        assertNull(WinOutputRules.spectrumSource(listOf(null, ""), null) { true })
    }

    @Test fun aStreamsListMayNameTheWeb() {
        val a = WinOutputRules.spectrumArgs("ffmpeg", "C:/Temp/radon-stream-1.mpd", 12.5)
        val w = a.indexOf("-protocol_whitelist"); val i = a.indexOf("-i")
        assertEquals("ffmpeg", a.first())
        assertEquals(true, w in 1 until i)
        assertEquals(true, a[w + 1].split(",").containsAll(listOf("file", "https", "tls", "tcp", "crypto")))
        assertEquals("12.50", a[a.indexOf("-ss") + 1])
        assertEquals("C:/Temp/radon-stream-1.mpd", a[i + 1])
        assertEquals("-", a.last())
    }

    @Test fun aSongsDepthIsItsFilesNotTheDecoders() {
        assertEquals("a 24-bit FLAC, decoded into 32-bit numbers", 24, WinOutputRules.shownBits("s32", 24))
        assertEquals("a stream: no file to ask", 24, WinOutputRules.shownBits("s32", null))
        assertEquals("a real 32-bit file says so itself", 32, WinOutputRules.shownBits("s32", 32))
        assertEquals(16, WinOutputRules.shownBits("s16", 0))
        assertEquals(16, WinOutputRules.shownBits("s16p", null))
        assertEquals("MP3 and AAC have no bit depth", 0, WinOutputRules.shownBits("floatp", null))
        assertEquals("a WAV's own depth, whatever it was decoded into", 32, WinOutputRules.shownBits("float", 32))
    }
}
