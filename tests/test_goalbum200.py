"""2.0: Go to album (right-click a song, or pick some): which of your albums a song is on."""
import unittest

from app import load

om = load()


class FindAlbumGroupTest(unittest.TestCase):
    groups = {"change of scenery ii|quinn xcii": ["Change of Scenery II", "Quinn XCII", []],
              "persona (extended)|half•alive": ["Persona (Extended)", "half•alive", []],
              "greatest hits|queen": ["Greatest Hits", "Queen", []],
              "greatest hits|abba": ["Greatest Hits", "ABBA", []]}

    def test_same_key(self):
        t = {"title": "Coffee", "artist": "Quinn XCII", "albumartist": "Quinn XCII", "album": "Change of Scenery II"}
        self.assertEqual(om.find_album_group(t, self.groups), "change of scenery ii|quinn xcii")

    def test_featured_song_finds_the_album(self):   # tagged under the pair, the album is the main artist's
        t = {"title": "Holding On", "artist": "Quinn XCII & Jeremy Zucker", "album": "Change of Scenery II"}
        self.assertEqual(om.find_album_group(t, self.groups), "change of scenery ii|quinn xcii")

    def test_same_name_other_artists(self):
        t = {"title": "Waterloo", "artist": "ABBA", "album": "Greatest Hits"}
        self.assertEqual(om.find_album_group(t, self.groups), "greatest hits|abba")
        t = {"title": "x", "artist": "Someone Else", "album": "Greatest Hits"}
        self.assertIsNone(om.find_album_group(t, self.groups))   # two of them: not a guess

    def test_none(self):
        self.assertIsNone(om.find_album_group({"title": "x", "artist": "y", "album": ""}, self.groups))
        self.assertIsNone(om.find_album_group({"title": "x", "artist": "y", "album": "Nope"}, self.groups))


if __name__ == "__main__":
    unittest.main()
