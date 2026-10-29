"""2.0: Plays on singles and albums: a song's imported plays on its single, an EP and its album, onto the album."""
import unittest

from app import load

om = load()


class AlbumPlaysTest(unittest.TestCase):
    def test_kinds(self):
        self.assertEqual(om.album_kind("Songs", "Songs"), 0)
        self.assertEqual(om.album_kind("Talk - Single", "Talk"), 0)
        self.assertEqual(om.album_kind("South - EP"), 1)
        self.assertEqual(om.album_kind("Persona"), 2)
        self.assertEqual(om.album_kind("Persona (Extended)"), 3)
        self.assertEqual(om.album_kind("Epic Album"), 2)   # "EP" only as a word

    def test_plan(self):
        h = "half•alive"
        plays = [("Songs", h, "Songs", 5), ("Songs", h, "Persona", 7), ("Hot Mess", h, "Persona", 3),
                 ("Talk", "Coldplay", "Talk - Single", 2), ("Talk", "Coldplay", "X&Y", 9),
                 ("Ruby", "Bad Suns", "Ruby EP", 4), ("Ruby", "Bad Suns", "Ruby - Single", 1)]
        mine = [{"title": "Songs", "artist": h, "album": "Persona (Extended)"}]
        rows = {r["title"]: r for r in om.album_plays_plan(plays, mine)}
        self.assertEqual(rows["Songs"]["to"], "Persona (Extended)")   # the extended one you have, over the album
        self.assertEqual(rows["Talk"]["to"], "X&Y")
        self.assertEqual(rows["Ruby"]["to"], "Ruby EP")   # an EP over a single
        self.assertNotIn("Hot Mess", rows)   # on one album only
        picks = {om.album_play_key("Songs", h, "Songs"): "Persona (Extended)"}
        self.assertNotIn("Songs", {r["title"] for r in om.album_plays_plan(plays, mine, picks)})   # decided: not asked again


class DuplicateSongsTest(unittest.TestCase):
    def test_plan(self):
        songs = [{"title": "N.P.D", "artist": "Last Dinosaurs", "plays": 52}, {"title": "NPD", "artist": "Last Dinosaurs", "plays": 2},
                 {"title": "Zero - From the Original Motion Picture \"Ralph Breaks The Internet\"", "artist": "Imagine Dragons", "plays": 72},
                 {"title": "Zero (From 'Ralph Breaks the Internet')", "artist": "Imagine Dragons", "plays": 10},
                 {"title": "creature", "artist": "half•alive", "plays": 84}, {"title": "Creature - Acoustic", "artist": "half•alive", "plays": 106},
                 {"title": "Songs", "artist": "half•alive", "plays": 5}]
        rows = {r["title"]: r for r in om.duplicate_songs_plan(songs)}
        self.assertEqual(rows["NPD"]["to"][0], "N.P.D")
        self.assertFalse(rows["NPD"]["version"])
        self.assertIn("Zero (From 'Ralph Breaks the Internet')", rows)
        self.assertEqual(rows["Creature - Acoustic"]["to"][0], "creature")   # onto the one that isn't a version, more played or not
        self.assertTrue(rows["Creature - Acoustic"]["version"])
        self.assertNotIn("Songs", rows)
        picks = {om.song_pick_key("NPD", "Last Dinosaurs"): ""}
        self.assertNotIn("NPD", {r["title"] for r in om.duplicate_songs_plan(songs, picks)})   # left alone: not asked again

    def test_sessions_under_the_album(self):
        self.assertEqual(om.album_kind("Evolve - Sessions"), 1)
        rows = om.album_plays_plan([("Rise Up", "Imagine Dragons", "It's Time - Sessions", 9), ("Rise Up", "Imagine Dragons", "Evolve", 5)])
        self.assertEqual(rows[0]["to"], "Evolve")


class EditionWordTest(unittest.TestCase):
    """2.0: an "… Edition" is an edition of the album (Moon Music (Full Moon Edition) of Moon Music)."""
    def test_edition(self):
        self.assertEqual(om.album_edition("Moon Music (Full Moon Edition)"), ("Moon Music", "Full Moon Edition"))
        self.assertEqual(om.album_edition("Viva la Vida (Prospekt's March Edition)")[0], "Viva la Vida")
        self.assertEqual(om.album_edition("Moon Music - Full Moon Edition")[0], "Moon Music")
        self.assertEqual(om.album_edition("Evolve - Sessions"), ("Evolve - Sessions", ""))
        self.assertEqual(om.album_kind("Moon Music (Full Moon Edition)"), 3)
