"""2.0: the tag scanner (Settings › Tagger › Scan…): missing and wrong tags, and covers, on real files."""
import shutil
import tempfile
import unittest
from pathlib import Path

from app import load
from test_fixup200 import flac

om = load()

TRACKS = {("twenty one pilots", "heavydirtysoul"): {"id": 1, "title": "Heavydirtysoul", "artist": "Twenty One Pilots",
                                                     "album_id": 10, "album": "Blurryface"},
          ("twenty one pilots", "stressed out"): {"id": 2, "title": "Stressed Out", "artist": "Twenty One Pilots",
                                                   "album_id": 10, "album": "Blurryface"}}
ALBUMS = {10: {"title": "Blurryface", "artist": "Twenty One Pilots", "date": "2015-05-15", "genre": "Alternative",
               "cover": "https://x/blurry.jpg", "positions": {1: 1, 2: 2}}}


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class TagScanTest(unittest.TestCase):
    def setUp(self):
        self.root = r = Path(tempfile.mkdtemp())
        self.a = flac(r / "Twenty One Pilots" / "Blurryface" / "01 Heavydirtysoul.flac", title="Heavydirtysoul",
                      artist="Twenty One Pilots")   # no album, year, genre, number
        self.b = flac(r / "Twenty One Pilots" / "Blurryface" / "02 Stressed Out.flac", title="Stresed Out",
                      artist="Twenty One Pilots", album="Blurryface", date="2015", genre="Rock", tracknumber="2")
        self.asked = []

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def find(self, artist, title, album):
        self.asked.append((artist, title))
        key = (om.norm(artist), om.norm(om.clean_title(title)))
        return TRACKS.get(key) or (TRACKS[("twenty one pilots", "stressed out")] if "stresed" in title.lower() else None)

    def test_plan(self):
        groups = om.tag_scan_plan([str(self.a), str(self.b)], self.find, ALBUMS.get,
                                  covers=lambda ar, al, found: found + ["https://x/2.jpg", "https://x/3.jpg", "https://x/4.jpg"])
        self.assertEqual(len(groups), 1)
        g = groups[0]
        songs = {Path(s["file"]).name: s["changes"] for s in g["songs"]}
        a = songs["01 Heavydirtysoul.flac"]
        self.assertEqual(a["album"], ("", "Blurryface", True))      # missing: Replace by default
        self.assertEqual(a["date"], ("", "2015-05-15", True))
        self.assertEqual(a["genre"], ("", "Alternative", True))
        self.assertEqual(a["tracknumber"], ("", "1", True))
        self.assertNotIn("albumartist", a)                           # the song's own artist: nothing to add
        b = songs["02 Stressed Out.flac"]
        self.assertEqual(b["title"], ("Stresed Out", "Stressed Out", False))   # different: Keep by default
        self.assertNotIn("date", b)                                  # the same year
        self.assertNotIn("genre", b)                                 # a genre the file has stays
        self.assertNotIn("tracknumber", b)
        self.assertEqual(g["cover_note"], "No cover in the files")
        self.assertEqual(g["covers"], ["https://x/blurry.jpg", "https://x/2.jpg", "https://x/3.jpg"])   # three
        tags, covers = om.scan_changes(groups, {(str(self.b), "title"): True, (str(self.a), "genre"): False},
                                       {g["key"]: "https://x/2.jpg"})
        self.assertEqual(tags[str(self.a)], {"album": "Blurryface", "date": "2015-05-15", "tracknumber": "1"})
        self.assertEqual(tags[str(self.b)], {"title": "Stressed Out"})
        self.assertEqual(covers, {g["key"]: "https://x/2.jpg"})
        for f, ch in tags.items():
            om.write_tags(f, ch)
        self.assertEqual(om.scan_read(str(self.a))["album"], "Blurryface")

    def test_untagged_file_named_from_its_place(self):
        c = flac(self.root / "Twenty One Pilots" / "Blurryface" / "02 Stressed Out (copy).flac")
        om.tag_scan_plan([str(c)], self.find, ALBUMS.get)
        self.assertEqual(self.asked[0], ("Twenty One Pilots", "Stressed Out (copy)"))

    def test_nothing_found_nothing_offered(self):
        groups = om.tag_scan_plan([str(self.b)], lambda *a: None, ALBUMS.get, read=lambda p: dict(om.scan_read(p)),
                                  cover_of=lambda p: b"x", width=lambda d: 1000)
        self.assertEqual(groups, [])


class TagScanDoneTest(unittest.TestCase):
    """2.0: the tag scanner remembers the songs it has been through: a scan again skips them, unless changed."""
    def test_done(self):
        import os
        import tempfile
        d = tempfile.mkdtemp()
        f = os.path.join(d, "a.flac")
        open(f, "wb").write(b"x" * 10)
        rec = om.TagScanDone(os.path.join(d, "done.json"))
        self.assertFalse(rec.done(f))
        rec.mark([f])
        self.assertTrue(om.TagScanDone(os.path.join(d, "done.json")).done(f))   # kept: read back next time
        open(f, "ab").write(b"y")   # changed since (tagged by something else): checked again
        self.assertFalse(rec.done(f))
        rec.mark([f])
        os.remove(f)
        rec.save()
        self.assertEqual(rec.files, {})   # a deleted song isn't kept


class LiveNamesTest(unittest.TestCase):
    """2.0: the live part off a song's name; the tag scanner never names a song that isn't live as a live one."""
    def test_strip(self):
        self.assertEqual(om.strip_live("Yellow (Live)"), "Yellow")
        self.assertEqual(om.strip_live("Radioactive - Live From Red Rocks / 2014"), "Radioactive")
        self.assertEqual(om.strip_live("Song (feat. X) [Live]"), "Song (feat. X)")
        self.assertEqual(om.strip_live("Live Forever"), "Live Forever")
        self.assertFalse(om.has_live("Alive"))

    def test_scan_never_live(self):
        def read(path):
            return {"title": "Yellow", "artist": "Coldplay", "album": "", "albumartist": "", "date": "", "genre": "", "tracknumber": ""}
        found = {"id": 1, "title": "Yellow (Live)", "artist": "Coldplay", "album_id": 9, "album": "Live 2003"}
        groups = om.tag_scan_plan(["/x/Coldplay/Yellow.flac"], lambda a, t, al: found, lambda i: {"title": "Live 2003", "artist": "Coldplay",
                                  "date": "2003", "genre": "", "cover": "", "positions": {1: 4}}, read=read, cover_of=lambda p: b"x",
                                  width=lambda b: 1000)
        changes = {k: v for g in groups for s in g["songs"] for k, v in s["changes"].items()}
        self.assertNotIn("title", changes)   # "Yellow" stays "Yellow"
        self.assertNotIn("album", changes)   # nor the live album's name
