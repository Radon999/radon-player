"""New releases: Deezer's answers into albums, singles and EPs, newest first."""
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from app import load

om = load()


class NewReleasesTest(unittest.TestCase):
    def test_scan(self):
        nr = om.NewReleases(Path(tempfile.mkdtemp()) / "nr.json")
        nr.read_artists([{"artist": "The Weeknd"}, {"artist": "The Weeknd, Daft Punk"}, {"artist": "SZA"}])
        self.assertEqual(nr.artists, ["The Weeknd", "SZA"])
        today = om.datetime.now().date().isoformat()

        def fake(url, *a, **k):
            if "search/artist" in url:
                return {"data": [{"id": 1 if "Weeknd" in url else 2, "name": "The Weeknd" if "Weeknd" in url else "SZA"}]}
            if "/artist/1/" in url:
                return {"data": [{"title": "New One", "release_date": today, "record_type": "single", "link": "x"},
                                 {"title": "Old", "release_date": "2001-01-01", "record_type": "album"}]}
            return {"data": [{"title": "SOS Deluxe", "release_date": today, "record_type": "ep"}]}
        with mock.patch.object(om, "http_json", fake), mock.patch.object(om.time, "sleep", lambda s: None):
            self.assertEqual(nr.scan("week"), 2)
        self.assertEqual(sorted(r["type"] for r in nr.found), ["ep", "single"])
        self.assertEqual(om.fmt_date("2026-10-12"), "12 Oct 2026")


if __name__ == "__main__":
    unittest.main()
