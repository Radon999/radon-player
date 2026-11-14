package org.omarchy.music

import org.junit.Assert.assertTrue
import org.junit.Test

/** Every line the DJ can say (each sentence written in Dj.kt, and the line that tries a voice), read from the source
 *  and held to the rules for how it talks (docs/rebuild/RADON_REBUILD_PLAN.md, section 2.4): it never names itself,
 *  never shouts, never says one of the phrases a playlist machine says, and never names where a song comes from (a
 *  service, a file, a stream, a download): your music is one collection. A new line that breaks a rule fails here. */
class DjLinesTest {
    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** The sentences in a source file: (line number, text) of every quoted text of three words or more that isn't in a comment. */
    private fun sentences(path: String): List<Pair<Int, String>> {
        val f = java.io.File(path)
        assertTrue("the source is where the test looks for it: ${f.absolutePath}", f.isFile)
        val out = ArrayList<Pair<Int, String>>()
        f.readLines().forEachIndexed { i, l ->
            val s = l.trimStart()
            if (s.startsWith("//") || s.startsWith("*") || s.startsWith("/**")) return@forEachIndexed
            for (m in literal.findAll(l)) {
                val text = m.groupValues[1]
                if (text.split(Regex("\\s+")).count { it.isNotBlank() } >= 3) out += (i + 1) to text
            }
        }
        return out
    }

    @Test fun everyLineKeepsToTheRulesForHowTheDjTalks() {
        val all = sentences("../core/common/org/omarchy/music/Dj.kt") + (0 to DjVoices.SAMPLE)
        assertTrue("the DJ's lines were found (${all.size})", all.size > 300)
        val itself = Regex("\\bDJ\\b")
        val machine = Regex("next up|up next|here is a playlist|as an ai|based on your listening", RegexOption.IGNORE_CASE)
        val where = Regex("tidal|spotify|youtube|soundcloud|deezer|last\\.fm|\\bfiles?\\b|\\bstream|\\bdownload", RegexOption.IGNORE_CASE)
        val broken = ArrayList<String>()
        for ((n, s) in all) {
            if (itself.containsMatchIn(s)) broken += "line $n names itself: $s"
            if ('!' in s) broken += "line $n shouts: $s"
            if (machine.containsMatchIn(s)) broken += "line $n talks like a playlist machine: $s"
            if (where.containsMatchIn(s)) broken += "line $n names where the music comes from: $s"
        }
        assertTrue(broken.joinToString("\n"), broken.isEmpty())
    }
}
