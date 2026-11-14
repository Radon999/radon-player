package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** "a" or "an" before a genre, a mood or a decade in the DJ's lines. */
class ArticleTest {
    @Test fun byHowTheNextWordIsSaid() {
        for (w in listOf("alternative", "indie", "energetic", "Intense", "electronic", "upbeat", "R&B", "80s", "18th century", "hour-long", "emo"))
            assertEquals(w, "an", article(w))
        for (w in listOf("rock", "happy", "sad", "hip-hop", "90s", "2000s", "1980s", "110 bpm", "unique", "European", "one-off", "house", "", "folk"))
            assertEquals(w, "a", article(w))
    }

    @Test fun withTheWordAndACapitalToBeginWith() {
        assertEquals("An alternative", withArticle("alternative", capital = true))
        assertEquals("a rock", withArticle("rock"))
        assertEquals("an energetic", withArticle("energetic"))
    }
}
