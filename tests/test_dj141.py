"""1.4.1: the DJ without moods, from every play Most played counts (Spotify-style sets)."""
import random
import time
import types
import unittest
from datetime import datetime

from app import load

om = load()


def fake_app(history, favorites=(), skips=()):
    songs = {}
    for when, artist, title in history:
        songs.setdefault((artist, title), {"id": f"{artist}-{title}", "title": title, "artist": artist, "source": "file"})
    mine = list(songs.values())
    hist = types.SimpleNamespace(plays=[(w, a, t, "", 200000) for w, a, t in history])
    app = types.SimpleNamespace(
        plays=types.SimpleNamespace(items={}), my_songs=lambda: mine, spotify_history=hist,
        song_index=lambda: {om.song_key(t): t for t in mine},
        apple_history=types.SimpleNamespace(timed_plays=lambda: []), lastfm=types.SimpleNamespace(connected=False, key=""),
        most_played=lambda: [], fm_song=om.MusicApp.fm_song, current_track=lambda: None,
        imported_plays=lambda: list(hist.plays),
        favorites=types.SimpleNamespace(tracks=lambda: [songs[k] for k in favorites]),
        song_stats=types.SimpleNamespace(rows=lambda since=0: [(songs[k], 5, 0) for k in skips]))
    return app


class DJTest(unittest.TestCase):
    def setUp(self):
        random.seed(1)
        now = time.time()
        old = datetime(datetime.now().year - 2, 6, 1, datetime.now().hour).timestamp()
        self.history = ([(old + i * 3600 * 24, "Twenty One Pilots", f"Old {i % 6}") for i in range(60)] +
                        [(now - 3600 * 24 * (i % 20) - 60, "Hippo Campus", f"New {i % 8}") for i in range(80)])

    def test_sets_from_your_history(self):
        dj = om.DJ(fake_app(self.history, skips=[("Hippo Campus", "New 0")]))
        taste = dj.load_taste()
        got = dj.set_throwback(taste, False)
        self.assertIsNotNone(got)
        self.assertTrue(all(t["title"].startswith("Old") for t in got[0]))
        self.assertIn(f"Throwback to {datetime.now().year - 2}", got[1])
        rep = dj.set_repeat(taste, True)
        self.assertTrue(all(t["title"].startswith("New") for t in rep[0]))
        self.assertNotIn("New 0", [t["title"] for t in rep[0]])   # the one you keep skipping
        self.assertIsNotNone(dj.set_hour(taste, False))
        self.assertIsNone(dj.set_rediscover(taste, False))        # no "all-time" counts in this fake

    def test_no_moods_left(self):
        self.assertFalse(hasattr(om, "Moods"))
        self.assertNotIn("mood", om.DJ_ORDER + om.DJ_FROM_ORDER + om.DJ_FROM_NEW_ORDER)


if __name__ == "__main__":
    unittest.main()
