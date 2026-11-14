package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Names and capitals as the DJ's voice gets them: nothing spelt out by accident, nothing left that a voice can't read. */
class SpeechPrepTest {
    private fun say(s: String) = Spoken.humanize(s, filler = 0.0)

    @Test fun initialsAreSpeltOnPurpose() {
        assertEquals("A J R", SpeechPrep.capital("AJR"))
        assertEquals("B T S", SpeechPrep.capital("BTS"))
        assertEquals("E L O", SpeechPrep.capital("ELO"))
        assertEquals("D N A", SpeechPrep.capital("DNA"))
        assertEquals("U K", SpeechPrep.capital("UK"))
        assertEquals("R H C P", SpeechPrep.capital("RHCP"))
        assertEquals("Here's R E M.", say("Here's R.E.M."))
        assertEquals("More M I A to start.", say("More M.I.A. to start."))
    }

    @Test fun wordsInCapitalsAreSaidAsWords() {
        assertEquals("Humble", SpeechPrep.capital("HUMBLE"))
        assertEquals("Nasa", SpeechPrep.capital("NASA"))
        assertEquals("Abba", SpeechPrep.capital("ABBA"))
        assertEquals("Nas", SpeechPrep.capital("NAS"))
        assertEquals("Go", SpeechPrep.capital("GO"))
        assertEquals("Why", SpeechPrep.capital("WHY"))
        assertEquals("Rhythm", SpeechPrep.capital("RHYTHM"))
        val s = say("Starting with “HUMBLE.” by Kendrick Lamar.")
        assertTrue(s, "Humble" in s && "HUMBLE" !in s)
    }

    @Test fun stylisedNamesAreSaidAsTheyAreSpoken() {
        assertEquals("Here's A C D C.", say("Here's AC/DC."))
        assertEquals("Here's Churches.", say("Here's CHVRCHES."))
        assertEquals("Here's M G M T.", say("Here's MGMT."))
        assertEquals("Here's Juice World.", say("Here's Juice WRLD."))
        assertEquals("Here's Pink and Kesha.", say("Here's P!nk & Ke\$ha."))
        assertEquals("Here's Blink one eighty-two.", say("Here's blink-182."))
        assertEquals("Here's Dead Mouse.", say("Here's deadmau5."))
        assertEquals("Some R and B.", say("Some R&B."))
        assertEquals("Here's Sizza.", say("Here's SZA."))
    }

    @Test fun lettersAndNumbers() {
        assertEquals("Here's U two.", say("Here's U2."))
        assertEquals("Here's M eighty-three.", say("Here's M83."))
        assertEquals("Here's U B forty.", say("Here's UB40."))
        assertEquals("Here's Boyz two Men.", say("Here's Boyz II Men."))
        assertEquals("Here's Sum forty-one.", say("Here's Sum 41."))
    }

    @Test fun dotsForAPauseAreNeverSaid() {
        assertEquals("Wait, what.", say("Wait... what"))
        assertEquals("Here's Baby One More Time.", say("Here's ...Baby One More Time"))
        assertEquals("And then.", say("And then…"))
        assertFalse(".." in say("One.. two... three…. four"))
    }

    @Test fun whatWasRightStaysRight() {
        assertEquals("Around ten at night.", say("Around 10 PM."))
        assertEquals("About twenty percent of your listening happens before ten in the morning.", say("About 20 percent of your listening happens before 10 AM."))
        assertEquals("Back to the eighties.", say("Back to the 80s."))
        assertEquals("Good evening. Let's settle in with some music.", say("Good evening. Let's settle in with some music."))
        val s = say("Three in a row from Loveless by My Bloody Valentine, in order, the way the record runs.")
        assertFalse(s, Regex("\\b[A-Z] [A-Z]\\b").containsMatchIn(s))   // nothing spelt out that wasn't initials
    }
}
