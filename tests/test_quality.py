"""Is it really lossless?: lossy-made FLACs and upsampled hi-res, from made-up music."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from app import load

om = load()


def make(path, *args):
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args, str(path)], check=True)


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class TrueQualityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.d = Path(tempfile.mkdtemp())
        om.QUALITY_CHECKS = cls.d / "checks.json"
        noise = ["-f", "lavfi", "-i", "anoisesrc=d=40:c=pink:r=44100:a=0.3"]
        make(cls.d / "cd.flac", *noise, "-c:a", "flac", "-sample_fmt", "s16")
        make(cls.d / "m.mp3", "-i", str(cls.d / "cd.flac"), "-c:a", "libmp3lame", "-b:a", "320k")
        make(cls.d / "fake.flac", "-i", str(cls.d / "m.mp3"), "-c:a", "flac")
        make(cls.d / "up96.flac", *noise, "-ar", "96000", "-c:a", "flac", "-sample_fmt", "s32")
        make(cls.d / "real96.flac", "-f", "lavfi", "-i", "anoisesrc=d=40:c=pink:r=96000:a=0.3", "-c:a", "flac", "-sample_fmt", "s32")

    def test_verdicts(self):
        v = {n: om.true_quality(self.d / f"{n}.flac")["verdict"] for n in ("cd", "fake", "up96", "real96")}
        self.assertEqual(v, {"cd": "ok", "fake": "lossy", "up96": "upsampled", "real96": "ok"})


if __name__ == "__main__":
    unittest.main()
