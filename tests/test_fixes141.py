"""1.4.1: songs whose artist is "Unknown artist" broke Wrapped, New releases and the song menu;
Most played shows one row a song of yours."""
import types
import unittest

from app import load

om = load()


class UnknownArtistTest(unittest.TestCase):
    def test_lead_artist(self):
        self.assertEqual(om.lead_artist("Unknown artist"), "")
        self.assertEqual(om.lead_artist(""), "")
        self.assertEqual(om.lead_artist("Joji, Rei Brown"), "Joji")

    def test_wrapped_covers_with_unknown_artists(self):
        songs = [{"id": "a", "title": "wav Blade", "artist": "Unknown artist", "album": "x"},
                 {"id": "b", "title": "Hit", "artist": "Joji", "album": "Nectar"}]
        app = types.SimpleNamespace(my_songs=lambda: songs, lib=types.SimpleNamespace(items={}),
                                    art=types.SimpleNamespace(paths={}))
        find = om.my_covers(app)
        self.assertEqual(find("Unknown artist", "wav Blade"), "")   # no crash (it was "list index out of range")

    def test_new_releases_skip_unknown_artists(self):
        import tempfile
        from pathlib import Path
        nr = om.NewReleases(Path(tempfile.mkdtemp()) / "nr.json")
        n = nr.read_artists([{"artist": "Unknown artist"}, {"artist": "Joji"}, {"artist": "Joji, Rei Brown"}])
        self.assertEqual((n, nr.artists), (1, ["Joji"]))


class MostPlayedTest(unittest.TestCase):
    def test_one_row_a_song_plays_added_up(self):
        mine = {"id": "f1", "title": "Next Semester", "artist": "Twenty One Pilots"}
        stats = types.SimpleNamespace(refresh=lambda: None, songs=[
            {"title": "Next Semester", "artist": "Twenty One Pilots", "thumb": "", "plays": 401, "spotify": 401},
            {"title": "Next Semester (Live)", "artist": "twenty one pilots", "thumb": "", "plays": 6, "lastfm": 6},
            {"title": "Other", "artist": "Band", "thumb": "", "plays": 50, "lastfm": 50}])
        index = {om.stats_key(mine["title"], mine["artist"]): mine,
                 om.stats_key("Next Semester (Live)", "twenty one pilots"): mine}   # say it's the copy you have
        app = types.SimpleNamespace(stats=stats, song_index=lambda: index,
                                    fm_song=om.MusicApp.fm_song)
        got = om.MusicApp.most_played(app)
        self.assertEqual([(t["title"], t["plays"]) for t in got], [("Next Semester", 407), ("Other", 50)])
        self.assertEqual(got[0]["plays_from"], {"spotify": 401, "lastfm": 6})


if __name__ == "__main__":
    unittest.main()


class YourCopyTest(unittest.TestCase):
    def index(self, songs):
        app = types.SimpleNamespace(plays=types.SimpleNamespace(items={"yt1": {"track": {
            "id": "yt1", "source": "ytm", "title": "Thicker Than Dust", "artist": "K.Flay"}}}), my_songs=lambda: songs)
        return om.MusicApp.song_index(app)

    def test_your_file_over_a_stream_however_the_artist_is_written(self):
        mine = self.index([{"id": "f1", "source": "file", "file": "/m/09 Thicker Than Dust.flac",
                            "title": "Thicker Than Dust", "artist": "KFlay", "album": "Life as a Dog (Deluxe Version)"}])
        self.assertEqual(om.MusicApp.fm_song("Thicker Than Dust", "K.Flay", "", mine, "Life as a Dog")["id"], "f1")

    def test_the_copy_on_the_same_album_then_flac(self):
        songs = [{"id": "a", "source": "file", "file": "/m/a.mp3", "title": "Hit", "artist": "X", "album": "Single"},
                 {"id": "b", "source": "file", "file": "/m/b.flac", "title": "Hit", "artist": "X", "album": "Best Of"},
                 {"id": "c", "source": "file", "file": "/m/c.flac", "title": "Hit", "artist": "X", "album": "Album"}]
        mine = self.index(songs)
        self.assertEqual(om.MusicApp.fm_song("Hit", "X", "", mine, "Album")["id"], "c")
        self.assertEqual(om.MusicApp.fm_song("Hit", "X", "", mine, "Elsewhere")["id"], "b")   # no album match: FLAC

    def test_a_feature_credit(self):
        mine = self.index([{"id": "f", "source": "file", "file": "/m/f.flac", "title": "Smile", "artist": "Juice WRLD & The Weeknd"}])
        self.assertEqual(om.MusicApp.fm_song("Smile", "The Weeknd", "", mine)["id"], "f")


class SameAlbumTest(unittest.TestCase):
    def test_the_song_on_the_same_album_when_the_artist_differs(self):
        app = types.SimpleNamespace(plays=types.SimpleNamespace(items={}), my_songs=lambda: [
            {"id": "f", "source": "file", "file": "/m/f.flac", "title": "Mine Right Now", "artist": "SIGRID.", "album": "How To Let Go"}])
        mine = om.MusicApp.song_index(app)
        self.assertEqual(om.MusicApp.fm_song("Mine Right Now", "Sigrid", "", mine, "How To Let Go")["id"], "f")
        self.assertEqual(om.MusicApp.fm_song("Mine Right Now", "Someone", "", mine, "Other")["source"], "lastfm")
