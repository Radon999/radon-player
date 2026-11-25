package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Discord shows of the song: its title with your plays after a dash, and the kind it shows as. */
class DiscordTest {
    @Test fun theTitleHasItsPlaysAfterADash() {
        assertEquals("Halo - 12 plays", Discord.details("Halo", 12))
        assertEquals("Halo - 1 play", Discord.details("Halo", 1))
        assertEquals("Halo - 1,234 plays", Discord.details("Halo", 1234))
        assertEquals("not played yet: the title alone", "Halo", Discord.details("Halo", 0))
    }

    @Test fun aLongTitleIsCutSoThePlaysStillShow() {
        val d = Discord.details("x".repeat(200), 7)
        assertEquals("Discord takes 128 characters", 128, d.length)
        assertTrue(d.endsWith(" - 7 plays"))
    }

    @Test fun alwaysOnTopIsCompeting() {
        assertEquals("Competing: above games and Go Live", 5, Discord.typeOf(Discord.TOP))
        assertEquals(0, Discord.typeOf(Discord.GAME))
        assertEquals(2, Discord.typeOf(Discord.LISTEN))
    }
}
