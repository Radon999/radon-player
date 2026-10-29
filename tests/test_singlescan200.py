"""2.0: Singles › Single scan: where a single belongs, from Deezer's albums, else Apple Music's."""
import unittest
from unittest import mock

from app import load

om = load()


def h(album, kind="album", date="2020-01-01", n=3, source="Deezer"):
    return {"album": album, "artist": "NF", "kind": kind, "n": n, "date": date, "cover": "https://x/c.jpg", "source": source}


class PickTest(unittest.TestCase):
    def test_album_over_ep_and_single(self):
        got = om.single_home_pick("The Search", [h("The Search", "single"), h("Remote - EP", "ep"), h("The Search", "album")])
        self.assertEqual(got["album"], "Remote - EP")   # an album named for the song is its single: the EP
        got = om.single_home_pick("Leave Me Alone", [h("Leave Me Alone - Single", "single"), h("Remote EP", "ep"), h("Perception")])
        self.assertEqual(got["album"], "Perception")

    def test_ep_when_no_album(self):
        got = om.single_home_pick("South", [h("South - Single", "single"), h("South - EP", "ep")])
        self.assertEqual((got["album"], got["kind"]), ("South - EP", "ep"))

    def test_edition_and_first_release(self):
        got = om.single_home_pick("Songs", [h("Persona"), h("Persona (Extended)", date="2021-01-01")])
        self.assertEqual(got["album"], "Persona (Extended)")
        got = om.single_home_pick("Songs", [h("Persona", date="2022-05-01"), h("Persona", date="2019-05-01", n=7)])
        self.assertEqual(got["n"], 7)

    def test_never_compilations_best_ofs_or_live(self):
        found = [h("Now 99", "compile"), h("Greatest Hits"), h("Live at Red Rocks"), h("The Sessions")]
        self.assertIsNone(om.single_home_pick("Song", found))
        self.assertEqual(om.single_home_pick("Song (Live)", [h("Live at Red Rocks")])["album"], "Live at Red Rocks")


class LookupTest(unittest.TestCase):
    def test_deezer(self):
        def get(url):
            if "/search?" in url:
                return {"data": [{"id": 5, "title": "Leave Me Alone", "artist": {"name": "NF"}, "album": {"id": 1, "title": "Leave Me Alone"}},
                                 {"id": 6, "title": "Leave Me Alone", "artist": {"name": "NF"}, "album": {"id": 2, "title": "Perception"}},
                                 {"id": 7, "title": "Other", "artist": {"name": "NF"}, "album": {"id": 3, "title": "X"}}]}
            self.assertIn("/album/2", url)   # the single and the unmatched song aren't looked at
            return {"id": 2, "title": "Perception", "artist": {"name": "NF"}, "release_date": "2017-10-06", "record_type": "album",
                    "cover_xl": "https://x/p.jpg", "nb_tracks": 1, "tracks": {"data": [{"id": 6, "track_position": 4}]}}
        with mock.patch.object(om, "deezer_get", get):
            got = om.single_home("NF", "Leave Me Alone")
        self.assertEqual((got["album"], got["n"], got["date"], got["source"]), ("Perception", 4, "2017-10-06", "Deezer"))

    def test_apple_music_when_deezer_has_none(self):
        with mock.patch.object(om, "deezer_get", lambda url: {"data": []}), \
                mock.patch.object(om, "http_json", lambda url, **k: {"results": [
                    {"trackName": "South", "artistName": "Hippo Campus", "collectionName": "South - Single", "trackNumber": 1},
                    {"trackName": "South", "artistName": "Hippo Campus", "collectionName": "South - EP", "trackNumber": 2,
                     "releaseDate": "2015-03-01T08:00:00Z", "artworkUrl100": "https://a/100x100bb.jpg"}]}):
            got = om.single_home("Hippo Campus", "South")
        self.assertEqual((got["album"], got["kind"], got["n"], got["source"], got["cover"]),
                         ("South - EP", "ep", 2, "Apple Music", "https://a/1200x1200bb.jpg"))

    def test_deezer_down_then_apple_music(self):
        with mock.patch.object(om, "deezer_get", lambda url: {"error": {"code": 4}}), \
                mock.patch.object(om, "http_json", lambda url, **k: {"results": []}):
            self.assertIsNone(om.single_home("NF", "Song"))
        def boom(*a, **k):
            raise OSError("offline")
        with mock.patch.object(om, "deezer_get", boom), mock.patch.object(om, "http_json", boom):
            with self.assertRaises(OSError):
                om.single_home("NF", "Song")


if __name__ == "__main__":
    unittest.main()
