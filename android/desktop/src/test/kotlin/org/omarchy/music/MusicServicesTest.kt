package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The source bar: a service, or your own music, and where the search looks with each. */
class MusicServicesTest {
    private val services = setOf("ytm", "tidal", "spotify")
    private val onTidal = MusicServices.Browsing(local = false, source = "tidal", searchIn = "tidal", before = "tidal")

    @Test fun goingToYourOwnMusicMakesTheSearchLookThereAndRemembersWhereItLooked() {
        val local = MusicServices.browseTo(onTidal.copy(searchIn = "ytm"), "local", services)!!
        assertEquals(MusicServices.Browsing(local = true, source = "tidal", searchIn = "local", before = "ytm"), local)
        assertNull("already there: nothing changes", MusicServices.browseTo(local, "local", services))
        val was = onTidal.copy(searchIn = "local", before = "ytm")
        assertEquals("the search looked in your music already: where it looked before is kept", "ytm", MusicServices.browseTo(was, "local", services)!!.before)
    }

    @Test fun goingBackToAServicePutsTheSearchBack() {
        val local = MusicServices.Browsing(local = true, source = "tidal", searchIn = "local", before = "ytm")
        assertEquals(MusicServices.Browsing(local = false, source = "spotify", searchIn = "ytm", before = "ytm"), MusicServices.browseTo(local, "spotify", services))
        // You moved the search yourself while in your own music: it stays where you put it
        assertEquals("tidal", MusicServices.browseTo(local.copy(searchIn = "tidal"), "ytm", services)!!.searchIn)
        assertEquals("back to the service you were on, from your own music", "tidal", MusicServices.browseTo(local, "tidal", services)!!.source)
    }

    @Test fun theOneYouAreOnOrAKeyThatIsNoServiceChangesNothing() {
        assertNull(MusicServices.browseTo(onTidal, "tidal", services))
        assertNull(MusicServices.browseTo(onTidal, "napster", services))
        assertEquals("another service: just that", onTidal.copy(source = "ytm"), MusicServices.browseTo(onTidal, "ytm", services))
    }
}
