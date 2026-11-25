package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.omarchy.music.SongCreditRules.Credit

/** A song's credits: read from each service, put in groups, and the people in them matched to your songs. */
class SongCreditRulesTest {
    private fun json(s: String) = JSON.parseToJsonElement(s)

    @Test fun rolesGoInTheRightGroups() {
        assertEquals("Mixed by", SongCreditRules.group("Mixing Engineer"))
        assertEquals("Mastered by", SongCreditRules.group("Mastering Engineer"))
        assertEquals("Remixed by", SongCreditRules.group("Remixer"))
        assertEquals("Engineered by", SongCreditRules.group("Recording Engineer"))
        assertEquals("Produced by", SongCreditRules.group("Co-Producer"))
        assertEquals("Written by", SongCreditRules.group("Composer"))
        assertEquals("Written by", SongCreditRules.group("Lyricist"))
        assertEquals("Featuring", SongCreditRules.group("Featured Artist"))
        assertEquals("Performers", SongCreditRules.group("Background Vocals"))
        assertEquals("Performers", SongCreditRules.group("guitar"))
        assertEquals("Performers", SongCreditRules.group("Rap"))
        // Words that only look like a part played: a studio, the pictures
        assertEquals("Other", SongCreditRules.group("Recorded At"))
        assertEquals("Other", SongCreditRules.group("Photography"))
        assertEquals("Other", SongCreditRules.group("Arranger"))
        // Who owns or sells it isn't a credit for making it
        assertNull(SongCreditRules.group("Label"))
        assertNull(SongCreditRules.group("Phonographic Copyright ℗"))
        assertNull(SongCreditRules.group("Publisher"))
    }

    @Test fun eachPersonOnceInAGroupWithTheirRoles() {
        val groups = SongCreditRules.grouped(listOf(
            Credit("Producer", "Max Martin"), Credit("Composer", "Max Martin"), Credit("Lyricist", "MAX MARTIN"),
            Credit("Guitar", "John Mayer"), Credit("Bass", "John Mayer"), Credit("guitar", "john mayer"),
            Credit("Mixing Engineer", "Serban Ghenea"), Credit("Label", "Republic Records"), Credit("Producer", "  "),
            Credit("Featured Artist", "Lil Wayne"),
        ))
        assertEquals(listOf("Featuring", "Written by", "Produced by", "Performers", "Mixed by"), groups.map { it.title })
        val written = groups.first { it.title == "Written by" }.people
        assertEquals(listOf(SongCreditRules.Person("Max Martin", listOf("Composer", "Lyricist"))), written)
        // The roles that only repeat the heading aren't said again
        assertEquals(listOf(SongCreditRules.Person("Max Martin", emptyList())), groups.first { it.title == "Produced by" }.people)
        assertEquals(listOf(SongCreditRules.Person("John Mayer", listOf("Guitar", "Bass"))), groups.first { it.title == "Performers" }.people)
        assertTrue(groups.none { g -> g.people.any { it.name == "Republic Records" } })
    }

    @Test fun namesMatchHoweverTheyreWritten() {
        assertEquals(SongCreditRules.nameKey("The Weeknd"), SongCreditRules.nameKey("weeknd"))
        assertEquals(SongCreditRules.nameKey("K.Flay"), SongCreditRules.nameKey("KFlay"))
        assertEquals(SongCreditRules.nameKey("Beyoncé"), SongCreditRules.nameKey("BEYONCE"))
        assertEquals(SongCreditRules.nameKey("Max-Martin"), SongCreditRules.nameKey("max martin"))
        // Only a whole leading "The" goes: Theo isn't Eo
        assertFalse(SongCreditRules.nameKey("Theo Katzman") == SongCreditRules.nameKey("O Katzman"))
        // Names in other alphabets are kept, not all made empty (and so all the same)
        assertFalse(SongCreditRules.nameKey("米津玄師") == SongCreditRules.nameKey("宇多田ヒカル"))
    }

    @Test fun yourSongsAPersonWorkedOn() {
        data class S(val title: String, val artist: String, val credits: List<Credit>?)
        val songs = listOf(
            S("Blinding Lights", "The Weeknd", listOf(Credit("Producer", "Max Martin"), Credit("Main Artist", "The Weeknd"))),
            S("Save Your Tears", "Weeknd, Ariana Grande", null),
            S("Love Me Harder (feat. The Weeknd)", "Ariana Grande", listOf(Credit("Producer", "Max Martin"), Credit("Label", "the weeknd"))),
            S("Shake It Off", "Taylor Swift", listOf(Credit("Writer", "Max Martin"), Credit("Writer", "Shellback"))),
            S("Someone Else", "Weekend Band", null),
        )
        val weeknd = SongCreditRules.workedOn("the weeknd", songs, { it.title to it.artist }, { it.credits })
        assertEquals(listOf("Blinding Lights", "Save Your Tears", "Love Me Harder (feat. The Weeknd)"), weeknd.map { it.first.title })
        assertEquals(listOf("Artist"), weeknd[0].second)   // not "Artist, Main Artist"
        val max = SongCreditRules.workedOn("MAX MARTIN", songs, { it.title to it.artist }, { it.credits })
        assertEquals(listOf("Blinding Lights", "Love Me Harder (feat. The Weeknd)", "Shake It Off"), max.map { it.first.title })
        assertEquals(listOf("Writer"), max[2].second)
    }

    @Test fun creditsNoneFoundAreLookedUpAgainLater() {
        val day = 86400.0
        assertFalse(SongCreditRules.lookAgain(true, 0.0, 400 * day))
        assertFalse(SongCreditRules.lookAgain(false, 0.0, 5 * day))
        assertTrue(SongCreditRules.lookAgain(false, 0.0, 31 * day))
    }

    @Test fun readsTidalsCredits() {
        val credits = SongCreditRules.fromTidal(json("""[
            {"type":"Producer","contributors":[{"name":"Finneas","id":1}]},
            {"type":"Lyricist","contributors":[{"name":"Billie Eilish"},{"name":"Finneas"}]},
            {"type":"Record Label","contributors":[{"name":"Interscope"}]}]"""))
        assertEquals(listOf(Credit("Producer", "Finneas"), Credit("Lyricist", "Billie Eilish"), Credit("Lyricist", "Finneas"),
            Credit("Record Label", "Interscope")), credits)
        // The contributors answer instead
        assertEquals(listOf(Credit("Composer", "Finneas")),
            SongCreditRules.fromTidal(json("""{"items":[{"name":"Finneas","role":"Composer"}],"totalNumberOfItems":1}""")))
    }

    @Test fun readsGeniusSong() {
        val credits = SongCreditRules.fromGenius(json("""{"title":"x",
            "featured_artists":[{"name":"Kendrick Lamar"}],
            "producer_artists":[{"name":"Mustard"}],
            "writer_artists":[{"name":"Kendrick Lamar"},{"name":"Mustard"}],
            "custom_performances":[{"label":"Mixing Engineer","artists":[{"name":"Derek Ali"}]},{"label":"Label","artists":[{"name":"TDE"}]}]}"""))
        val groups = SongCreditRules.grouped(credits).associate { g -> g.title to g.people.map { it.name } }
        assertEquals(listOf("Kendrick Lamar"), groups["Featuring"])
        assertEquals(listOf("Kendrick Lamar", "Mustard"), groups["Written by"])
        assertEquals(listOf("Mustard"), groups["Produced by"])
        assertEquals(listOf("Derek Ali"), groups["Mixed by"])
        assertFalse(groups.values.flatten().contains("TDE"))
    }

    @Test fun readsMusicBrainzRecording() {
        val credits = SongCreditRules.fromMusicBrainz(json("""{
            "artist-credit":[{"name":"Calvin Harris","joinphrase":" feat. "},{"name":"Rihanna","joinphrase":""}],
            "relations":[
              {"target-type":"artist","type":"producer","attributes":["co"],"artist":{"name":"Someone"}},
              {"target-type":"artist","type":"instrument","attributes":["additional","guitar"],"artist":{"name":"Player"}},
              {"target-type":"artist","type":"vocal","attributes":["background vocals"],"artist":{"name":"Singer"}},
              {"target-type":"artist","type":"mix","attributes":[],"artist":{"name":"Mixer Person"}},
              {"target-type":"work","type":"performance","work":{"relations":[
                {"target-type":"artist","type":"composer","artist":{"name":"Calvin Harris"}},
                {"target-type":"artist","type":"lyricist","artist":{"name":"Calvin Harris"}}]}}]}"""))
        val groups = SongCreditRules.grouped(credits).associate { g -> g.title to g.people }
        assertEquals(listOf("Rihanna"), groups["Featuring"]?.map { it.name })   // Calvin Harris is the artist, not featured
        assertEquals(listOf(SongCreditRules.Person("Calvin Harris", listOf("Composer", "Lyricist"))), groups["Written by"])
        assertEquals(listOf(SongCreditRules.Person("Someone", listOf("Co-producer"))), groups["Produced by"])
        assertEquals(listOf(SongCreditRules.Person("Player", listOf("Guitar")), SongCreditRules.Person("Singer", listOf("Background vocals"))),
            groups["Performers"])
        assertEquals(listOf("Mixer Person"), groups["Mixed by"]?.map { it.name })
    }
}
