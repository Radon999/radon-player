"""1.4's stats: Apple Music's history, one row a song from every source, skips by period."""
import io
import tempfile
import time
import types
import unittest
import zipfile
from pathlib import Path

from app import load

om = load()

ACTIVITY = ("Song Name,Album Name,Artist Name,Event Start Timestamp,Play Duration Milliseconds,Media Duration In Milliseconds\n"
            "Blinding Lights,After Hours,The Weeknd,2025-03-01T10:00:00Z,200000,200000\n"
            "Blinding Lights,After Hours,The Weeknd,2025-03-02T10:00:00Z,50000,200000\n"     # a quarter: not a play
            "Save Your Tears,After Hours,The Weeknd,2025-03-03T10:00:00.123Z,120000,215000\n")
DAILY = ("Country,Track Identifier,Media type,Date Played,Hours,Play Duration Milliseconds,Source Type,Play Count,"
         "Skip Count,Ignore For Recommendations,Track Description\n"
         "US,1,AUDIO,20250301,10,600000,IPHONE,3,0,,The Weeknd - Blinding Lights\n")


class AppleHistoryTest(unittest.TestCase):
    def history(self):
        d = tempfile.mkdtemp()
        return om.AppleHistory(Path(d) / "apple.json")

    def test_play_activity_counts_from_half(self):
        rows = om.AppleHistory.parse_csv("Apple Music Play Activity.csv", ACTIVITY)
        self.assertEqual([(r[2], r[5]) for r in rows], [("Blinding Lights", 1), ("Save Your Tears", 1)])
        self.assertEqual(rows[0][3], "After Hours")

    def test_daily_tracks(self):
        rows = om.AppleHistory.parse_csv("Apple Music - Play History Daily Tracks.csv", DAILY)
        self.assertEqual(rows[0][1:3] + [rows[0][5]], ["The Weeknd", "Blinding Lights", 3])

    def test_zip_prefers_each_play_and_imports_once(self):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as z:
            z.writestr("Apple Media Services information/Apple Music Activity/Apple Music Play Activity.csv", ACTIVITY)
            z.writestr("Apple Media Services information/Apple Music Activity/Apple Music - Play History Daily Tracks.csv", DAILY)
        path = Path(tempfile.mkdtemp()) / "apple.zip"
        path.write_bytes(buf.getvalue())
        h = self.history()
        self.assertEqual(h.import_file(str(path)), 2)
        self.assertEqual(h.import_file(str(path)), 0)   # again: nothing new
        self.assertEqual(len(h.timed_plays()), 2)

    def test_library_xml(self):
        import plistlib
        data = plistlib.dumps({"Tracks": {"1": {"Name": "Starboy", "Artist": "The Weeknd", "Album": "Starboy",
                                                "Play Count": 41, "Total Time": 230000}}})
        rows = om.AppleHistory.parse_library(data)
        self.assertEqual((rows[0][0], rows[0][5]), (0, 41))


class MergeTest(unittest.TestCase):
    def test_one_row_a_song(self):
        songs = {}
        om.add_plays(songs, "Blinding Lights", "The Weeknd", "", "", 5, "lastfm")
        om.add_plays(songs, "Blinding Lights (Remastered)", "The Weeknd & Daft Punk", "After Hours", "", 2, "apple")
        om.add_plays(songs, "Blinding Lights", "The Weeknd feat. Someone", "", "", 1, "spotify")
        self.assertEqual(len(songs), 1)
        s = next(iter(songs.values()))
        self.assertEqual((s["plays"], s["apple"], s["album"]), (8, 2, "After Hours"))


class SkipPeriodTest(unittest.TestCase):
    def test_rows_since(self):
        d = tempfile.mkdtemp()
        st = om.SongStats(Path(d) / "skips.json")
        t = {"id": "a", "title": "A", "artist": "B"}
        st.skipped(t)
        e = st.items[st.key(t)]
        e["skips_at"] = [time.time() - 40 * 86400, time.time() - 86400]
        e["skips"] = 2
        self.assertEqual(st.rows()[0][1], 2)
        self.assertEqual(st.rows(time.time() - 7 * 86400)[0][1], 1)
        self.assertEqual(st.rows(time.time() - 3600), [])


if __name__ == "__main__":
    unittest.main()


class DiscoveryTest(unittest.TestCase):
    def test_heard_to_the_end_moves_on(self):
        a = types.SimpleNamespace(win=None)
        d = tempfile.mkdtemp()
        om.PLAYLISTS = Path(d) / "lists.json"
        a.lists = om.Playlists()
        disc = a.lists.create("X Discovery playlist")
        disc["discovery"] = {"artist": "X", "role": "discover"}
        done = a.lists.create("X discovered")
        done["discovery"] = {"artist": "X", "role": "discovered"}
        a.lists.add(disc["id"], [{"id": "s1", "title": "One", "artist": "X"}, {"id": "s2", "title": "Two", "artist": "X"}])
        for name in ("discovery_lists", "discovered"):
            setattr(a, name, getattr(om.MusicApp, name).__get__(a))
        a.discovered({"id": "s1", "title": "One", "artist": "X"})
        self.assertEqual([t["id"] for t in a.lists.get(disc["id"])["tracks"]], ["s2"])
        self.assertEqual([t["id"] for t in a.lists.get(done["id"])["tracks"]], ["s1"])
