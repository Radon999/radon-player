package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Jump to a letter: where each letter starts in a list in alphabetical order, and no strip for any other list. */
class JumpRulesTest {
    private fun names(vararg groups: Pair<String, Int>) = groups.flatMap { (start, n) -> (1..n).map { "$start song $it" } }

    @Test fun aNamesLetter() {
        assertEquals("D", JumpRules.letter("drew")); assertEquals("E", JumpRules.letter("Éclair")); assertEquals("#", JumpRules.letter("99 Problems"))
        assertEquals("#", JumpRules.letter("(untitled)" .replace("untitled", "…"))); assertEquals("U", JumpRules.letter("(Untitled)"))
        assertEquals("T", JumpRules.letter("The Comedown")); assertEquals("C", JumpRules.letter("The Comedown", skipArticle = true))
    }

    @Test fun whereEachLetterStarts() {
        val list = names("1" to 3, "Apple" to 10, "Banana" to 10, "cherry" to 10, "Drew" to 10, "Eel" to 10)
        assertEquals(listOf("#" to 0, "A" to 3, "B" to 13, "C" to 23, "D" to 33, "E" to 43), JumpRules.anchors(list.size) { list[it] })
    }

    @Test fun aListSortedWithoutItsArticles() {
        // "The Beatles" among the Bs, as a list that skips "The" sorts it
        val list = names("Abba" to 10, "The Beatles" to 10, "Cher" to 10, "Dido" to 10, "Eels" to 10)
        assertEquals(listOf("A" to 0, "B" to 10, "C" to 20, "D" to 30, "E" to 40), JumpRules.anchors(list.size) { list[it] })
    }

    @Test fun noStripForAListInAnotherOrderOrAShortOne() {
        val byDate = names("Zed" to 10, "Apple" to 10, "Mango" to 10, "Banana" to 10, "Kiwi" to 10)
        assertNull("not in alphabetical order", JumpRules.anchors(byDate.size) { byDate[it] })
        val short = names("Apple" to 3, "Banana" to 3, "Cherry" to 3, "Drew" to 3, "Eel" to 3)
        assertNull("too short to need one", JumpRules.anchors(short.size) { short[it] })
        val oneLetter = names("Apple" to 60)
        assertNull("all one letter", JumpRules.anchors(oneLetter.size) { oneLetter[it] })
    }

    @Test fun aRealLibraryWithAFewNamesOutOfPlaceStillHasItsStrip() {
        // filed under their bracket or quote at the top, an accent after Z, another alphabet and numbers at the end
        val list = listOf("(Don't Fear) The Reaper", "'Till I Collapse", "1979") + names("Apple" to 10, "Banana" to 10, "cherry" to 10, "Drew" to 10, "Eel" to 10) +
            listOf("Zebra", "Édith", "Привет", "99 Luftballons")
        assertEquals(listOf("#" to 2, "A" to 3, "B" to 13, "C" to 23, "D" to 33, "E" to 43, "Z" to 53), JumpRules.anchors(list.size) { list[it] })
    }

    @Test fun aListFromZToAHasItsStripTheOtherWayUp() {
        // 60 names: 10 each of F, E, D, C, B, A, in that order down the screen
        val names = "FEDCBA".flatMap { c -> List(10) { "$c song $it" } }
        assertEquals(null, JumpRules.anchors(names.size) { names[it] })
        assertEquals(listOf("F" to 0, "E" to 10, "D" to 20, "C" to 30, "B" to 40, "A" to 50), JumpRules.anchorsEitherWay(names.size) { names[it] })
        // A to Z stays as it was
        val up = names.reversed()
        assertEquals(JumpRules.anchors(up.size) { up[it] }, JumpRules.anchorsEitherWay(up.size) { up[it] })
    }

    @Test fun theRowOfAPlaceInAListOrAGrid() {
        assertEquals(12, JumpRules.row(2, 10, 1))
        assertEquals("the fourth row of a grid three across, under two rows of header", 5, JumpRules.row(2, 10, 3))
    }
}
