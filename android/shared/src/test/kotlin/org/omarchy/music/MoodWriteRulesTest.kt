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

    @Test fun theCommonestReasonIsSaidAndAllAreNoted() {
        val whys = linkedMapOf("MP3: its tag is of a kind the app leaves alone" to 3, "M4A: couldn't write its tags" to 44, "OPUS: not allowed" to 1)
        assertEquals("M4A: couldn't write its tags, and 2 other reasons", MoodWriteRules.said(whys))
        assertEquals("M4A: couldn't write its tags", MoodWriteRules.said(mapOf("M4A: couldn't write its tags" to 48)))
        assertEquals("no reason was given", MoodWriteRules.said(emptyMap()))
        assertEquals("Moods couldn't be written into 48 files. 44 × M4A: couldn't write its tags; 3 × MP3: its tag is of a kind the app leaves alone; " +
            "1 × OPUS: not allowed. For example: a.m4a, b.mp3", MoodWriteRules.note(48, whys, listOf("a.m4a", "b.mp3")))
    }
}
