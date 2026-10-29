"""1.4.3: the Tagger reads and writes a file's lyrics and shows what the file is."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from app import load

om = load()


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg to make test files")
class TaggerTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def make(self, ext):
        p = self.dir / f"t.{ext}"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=d=1", str(p)], check=True)
        return p

    def test_lyrics_round_trip(self):
        for ext in ("flac", "mp3", "m4a", "ogg"):
            with self.subTest(ext=ext):
                p = self.make(ext)
                self.assertEqual(om.file_lyrics(p), "")
                om.set_file_lyrics(p, "line one\nline two")
                self.assertEqual(om.file_lyrics(p), "line one\nline two")
                om.set_file_lyrics(p, "new words")   # replaces, unlike put_lyrics
                self.assertEqual(om.file_lyrics(p), "new words")
                om.set_file_lyrics(p, "")
                self.assertEqual(om.file_lyrics(p), "")

    def test_metadata(self):
        p = self.make("flac")
        om.write_tags(p, {"title": "Halo", "artist": "K.Flay"})
        om.set_file_lyrics(p, "secret words")
        md = dict(om.file_metadata(p))
        self.assertEqual(md["Format"], "FLAC")
        self.assertIn("Halo", md.values())
        self.assertNotIn("secret words", " ".join(md.values()))   # lyrics have their own tab
