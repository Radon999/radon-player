"""1.4.2: Most played this week, month and year (every source with a time), and Wrapped counting a song once."""
import tempfile
import time
import types
import unittest
from pathlib import Path

from app import load

om = load()


class PeriodsTest(unittest.TestCase):
    def test_periods(self):
        now = time.time()
        day = 86400
        plays = [(now - 2 * day, "Mk.gee", "Alesis", "Two Star", 200000),
                 (now - 20 * day, "Mk.gee", "Alesis", "Two Star", 200000),
                 (now - 200 * day, "Joji", "Glimpse of Us", "Smithereens", 200000),
                 (now - 900 * day, "Joji", "Slow Dancing in the Dark", "Ballads 1", 200000)]
        app = types.SimpleNamespace(
            lastfm=types.SimpleNamespace(connected=False, user=""),
            plays=types.SimpleNamespace(items={}),
            spotify_history=types.SimpleNamespace(plays=plays),
            apple_history=types.SimpleNamespace(rows=[], plays=0))
        om.STATS = Path(tempfile.mkdtemp()) / "stats.json"
        st = om.Stats(app)
        st.build()
        names = {k: [(s["title"], s["plays"]) for s in v] for k, v in st.periods.items()}
        self.assertEqual(names["week"], [("Alesis", 1)])
        self.assertEqual(names["month"], [("Alesis", 2)])
        self.assertEqual(sorted(names["year"]), [("Alesis", 2), ("Glimpse of Us", 1)])
        self.assertEqual(len(st.songs), 3)
        again = om.Stats(app)   # kept
        self.assertEqual(len(again.periods["year"]), 2)

    def test_wrapped_counts_a_song_once(self):
        t = time.time()
        rows = [(t, "K.Flay", "Thicker Than Dust", "Every Where Is Some Where", ""),
                (t + 1, "KFlay", "Thicker Than Dust - Remastered", "", ""),
                (t + 2, "K.Flay, Someone", "Thicker Than Dust", "", ""),
                (t + 3, "Joji", "Glimpse of Us", "Smithereens", "")]
        s = om.year_stats(rows)
        self.assertEqual(s["songs"][0][:3], ("Thicker Than Dust", "K.Flay", 3))
        self.assertEqual(s["artists"][0], ("K.Flay", 3))
        self.assertEqual(s["top_artist_song"], ("Thicker Than Dust", 3))
