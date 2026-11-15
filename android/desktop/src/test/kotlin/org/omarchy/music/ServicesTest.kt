package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The source bar: a service, or your own music, and where the search looks with each. */
class ServicesTest {
    private val services = setOf("ytm", "tidal", "spotify")
    private val onTidal = Services.Browsing(local = false, source = "tidal", searchIn = "tidal", before = "tidal")

    @Test fun goingToYourOwnMusicMakesTheSearchLookThereAndRemembersWhereItLooked() {
        val local = Services.browseTo(onTidal.copy(searchIn = "ytm"), "local", services)!!
        assertEquals(Services.Browsing(local = true, source = "tidal", searchIn = "local", before = "ytm"), local)
        assertNull("already there: nothing changes", Services.browseTo(local, "local", services))
        val was = onTidal.copy(searchIn = "local", before = "ytm")
        assertEquals("the search looked in your music already: where it looked before is kept", "ytm", Services.browseTo(was, "local", services)!!.before)
    }

    @Test fun goingBackToAServicePutsTheSearchBack() {
        val local = Services.Browsing(local = true, source = "tidal", searchIn = "local", before = "ytm")
        assertEquals(Services.Browsing(local = false, source = "spotify", searchIn = "ytm", before = "ytm"), Services.browseTo(local, "spotify", services))
        // You moved the search yourself while in your own music: it stays where you put it
        assertEquals("tidal", Services.browseTo(local.copy(searchIn = "tidal"), "ytm", services)!!.searchIn)
        assertEquals("back to the service you were on, from your own music", "tidal", Services.browseTo(local, "tidal", services)!!.source)
    }

    @Test fun theOneYouAreOnOrAKeyThatIsNoServiceChangesNothing() {
        assertNull(Services.browseTo(onTidal, "tidal", services))
        assertNull(Services.browseTo(onTidal, "napster", services))
        assertEquals("another service: just that", onTidal.copy(source = "ytm"), Services.browseTo(onTidal, "ytm", services))
    }
}
