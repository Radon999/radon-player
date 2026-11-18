package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** When moods can't be written into some files, the reason is said, not just the number. */
class MoodWriteRulesTest {
    @Test fun aFilesNameAndKindFromItsAddress() {
        assertEquals("02 into the blackhole.flac", MoodWriteRules.fileName("/storage/emulated/0/Music/HOAX/02 into the blackhole.flac"))
        assertEquals("FLAC", MoodWriteRules.kind("/storage/emulated/0/Music/HOAX/02 into the blackhole.flac"))
        assertEquals("song.m4a", MoodWriteRules.fileName("content://media/external/audio/media/primary%3AMusic%2Fsong.m4a"))
        assertEquals("M4A", MoodWriteRules.kind("content://media/external/audio/media/primary%3AMusic%2Fsong.m4a"))
        assertEquals("an address with no name in it says no kind", "", MoodWriteRules.kind("content://media/external/audio/media/4411"))
    }

    @Test fun theReasonIsWhatTheDeviceSaidWithTheKindOfFile() {
        assertEquals("M4A: couldn't write its tags (Settings › Tagger: allow All files access)",
            MoodWriteRules.why("couldn't write its tags (Settings › Tagger: allow All files access)", "/Music/a.m4a"))
        assertEquals("MP3: its tag is of a kind the app leaves alone", MoodWriteRules.why(null, "/Music/b.mp3"))
        assertEquals("its tag is of a kind the app leaves alone", MoodWriteRules.why("  ", "content://media/external/audio/media/4411"))
    }

    @Test fun theKindOfFileFromItsFirstBytes() {
        fun head(s: String) = s.toByteArray(Charsets.ISO_8859_1)
        assertEquals("FLAC", MoodWriteRules.kindOf(head("fLaC00000000")))
        assertEquals("MP3", MoodWriteRules.kindOf(head("ID3000000000")))
        assertEquals("MP3", MoodWriteRules.kindOf(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0, 0)))
        assertEquals("WAV", MoodWriteRules.kindOf(head("RIFF0000WAVE")))
        assertEquals("M4A", MoodWriteRules.kindOf(head("0000ftypM4A ")))
        assertEquals("OGG", MoodWriteRules.kindOf(head("OggS00000000")))
        assertEquals("", MoodWriteRules.kindOf(head("??")))
        assertEquals("a reason that names the kind isn't given it twice", "M4A: All files access isn't allowed",
            MoodWriteRules.why("M4A: All files access isn't allowed", "/Music/a.m4a"))
        assertEquals("OGG: the phone doesn't say where the file is", MoodWriteRules.why("OGG: the phone doesn't say where the file is", "content://media/external/audio/media/92720"))
    }

    @Test fun theCommonestReasonIsSaidAndAllAreNoted() {
        val whys = linkedMapOf("MP3: its tag is of a kind the app leaves alone" to 3, "M4A: couldn't write its tags" to 44, "OPUS: not allowed" to 1)
        assertEquals("M4A: couldn't write its tags, and 2 other reasons", MoodWriteRules.said(whys))
        assertEquals("M4A: couldn't write its tags", MoodWriteRules.said(mapOf("M4A: couldn't write its tags" to 48)))
        assertEquals("no reason was given", MoodWriteRules.said(emptyMap()))
        assertEquals("Moods couldn't be written into 48 files. 44 × M4A: couldn't write its tags; 3 × MP3: its tag is of a kind the app leaves alone; " +
            "1 × OPUS: not allowed. For example: a.m4a, b.mp3", MoodWriteRules.note(48, whys, listOf("a.m4a", "b.mp3")))
    }
}
