"""2.0: Fix albums and artists (featured-artist splits, deluxe editions, singles) and Empty folders, on real files."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from app import load

om = load()


def flac(path, **tags):
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono", "-t", "0.2", str(path)], check=True)
    from mutagen.flac import FLAC
    f = FLAC(path)
    for k, v in tags.items():
        f[k] = [v]
    f.save()
    return path


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class FixupTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        r = self.root
        q = "Quinn XCII"
        for i, t in enumerate(["Coffee", "Stacy", "Am I Dreaming"], 1):
            flac(r / q / "Change of Scenery II" / f"0{i} {t}.flac", title=t, artist=q, albumartist=q, album="Change of Scenery II", tracknumber=str(i))
        flac(r / f"{q} & Jeremy Zucker" / "Change of Scenery II" / "04 Holding On.flac", title="Holding On (feat. Jeremy Zucker)",
             artist=f"{q} & Jeremy Zucker", album="Change of Scenery II", tracknumber="4")
        flac(r / "Simon & Garfunkel" / "Bookends" / "01 America.flac", title="America", artist="Simon & Garfunkel", album="Bookends")
        h = "half•alive"
        flac(r / h / "Persona" / "01 Still Feel.flac", title="Still Feel.", artist=h, albumartist=h, album="Persona", tracknumber="1")
        flac(r / h / "Persona (Extended)" / "05 Arrow.flac", title="Arrow", artist=h, albumartist=h, album="Persona (Extended)", tracknumber="5")
        flac(r / h / "Selene" / "01 Selene.flac", title="Arrow", artist=h, albumartist=h, album="Arrow")   # a single of Arrow
        for a in ("Death Race For Love (Sessions)", "Death Race For Love (Bonus Track Version)"):
            flac(r / "Juice WRLD" / a / "01 Song.flac", title=f"Song {a[-5:]}", artist="Juice WRLD", albumartist="Juice WRLD", album=a)
        (r / "Old" / "Leftover").mkdir(parents=True)
        (r / "Old" / "Leftover" / "cover.jpg").write_bytes(b"x" * 10)
        (r / "Old" / "Leftover" / "song.lrc").write_text("[00:01]hi")
        (r / "Keep").mkdir()
        (r / "Keep" / "notes.pdf").write_bytes(b"%PDF")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def songs(self):
        return [om.track_from_file(p) for p in self.root.rglob("*.flac")]

    def test_plan_and_moves(self):
        songs = self.songs()
        plan = om.fixup_plan(songs, lambda artist, album: {"still feel": 1, "arrow": 5} if album == "Persona (Extended)" else None)
        by = {Path(c["file"]).name: c for c in plan}
        self.assertEqual(by["04 Holding On.flac"]["new"], {"artist": "Quinn XCII"})
        self.assertTrue(by["01 America.flac"]["unsure"])             # a duo: offered, but Leave alone unless you say
        plan = [c for c in plan if not c.get("unsure")]
        self.assertEqual(by["01 Still Feel.flac"]["new"], {"album": "Persona (Extended)"})   # number right already: kept
        self.assertEqual(by["01 Selene.flac"]["new"]["album"], "Persona (Extended)")
        self.assertEqual(by["01 Selene.flac"]["new"]["tracknumber"], "5")
        self.assertFalse([c for c in plan if "Juice" in c["file"]])  # the Sessions are albums of their own
        for c in plan:
            self.assertNotIn("title", c["new"])
        moves = dict(om.fixup_moves(plan, songs, [str(self.root)]))
        r = self.root
        self.assertEqual(Path(moves[str(r / "Quinn XCII & Jeremy Zucker" / "Change of Scenery II" / "04 Holding On.flac")]).parent,
                         r / "Quinn XCII" / "Change of Scenery II")
        self.assertEqual(Path(moves[str(r / "half•alive" / "Persona" / "01 Still Feel.flac")]).parent, r / "half•alive" / "Persona (Extended)")
        for src, dest in moves.items():
            om.write_tags(src, next(c for c in plan if c["file"] == src)["new"])
            om.move_song(src, dest)
        self.assertTrue((r / "Quinn XCII" / "Change of Scenery II" / "04 Holding On.flac").exists())
        self.assertEqual(om.track_from_file(r / "Quinn XCII" / "Change of Scenery II" / "04 Holding On.flac")["title"],
                         "Holding On (feat. Jeremy Zucker)")
        empty = {Path(d).name for d, _n, _f in om.empty_folders([str(r)])}
        self.assertIn("Quinn XCII & Jeremy Zucker", empty)   # the artist's folder, its emptied album folder with it
        self.assertNotIn("Change of Scenery II", empty)
        self.assertIn("Old", empty)
        self.assertNotIn("Keep", empty)
        self.assertNotIn("Persona (Extended)", empty)


if __name__ == "__main__":
    unittest.main()


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class OtherArtistTest(unittest.TestCase):
    """Other artist: "Young Dolph & Key Glock" put under Key Glock instead of Young Dolph, the whole album."""
    def setUp(self):
        self.root = r = Path(tempfile.mkdtemp())
        d, k = "Young Dolph", "Key Glock"
        flac(r / d / "Rich Slave" / "01 A.flac", title="A", artist=d, albumartist=d, album="Rich Slave")
        flac(r / k / "Glockoma" / "01 B.flac", title="B", artist=k, albumartist=k, album="Glockoma")
        flac(r / f"{d} & {k}" / "Dum and Dummer" / "01 C.flac", title="C", artist=f"{d} & {k}", albumartist=f"{d} & {k}", album="Dum and Dummer")
        flac(r / f"{d} & {k}" / "Dum and Dummer" / "02 D.flac", title="D", artist=f"{d} & {k}", albumartist=f"{d} & {k}", album="Dum and Dummer")
        flac(r / d / "Dum and Dummer" / "03 E.flac", title="E", artist=d, albumartist=d, album="Dum and Dummer")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_other_artist(self):
        songs = [om.track_from_file(p) for p in self.root.rglob("*.flac")]
        plan = om.fixup_plan(songs)
        by = {Path(c["file"]).name: c for c in plan}
        self.assertEqual(by["01 C.flac"]["new"], {"artist": "Young Dolph", "albumartist": "Young Dolph"})
        self.assertEqual(by["01 C.flac"]["others"], ["Key Glock"])
        picked = ["Key Glock" if Path(c["file"]).name == "01 C.flac" else True for c in plan]
        out = {Path(c["file"]).name: c for c in om.fixup_with_others(plan, picked)}
        self.assertEqual(out["01 C.flac"]["new"], {"artist": "Key Glock", "albumartist": "Key Glock"})
        self.assertEqual(out["03 E.flac"]["new"], {"albumartist": "Key Glock"})   # Dolph's own song: his, on Glock's album
        self.assertNotIn("01 A.flac", out)
        self.assertNotIn("01 B.flac", out)
        moves = {Path(a).name: Path(b).parent for a, b in om.fixup_moves(list(out.values()), songs, [str(self.root)])}
        self.assertEqual(moves["01 C.flac"], self.root / "Key Glock" / "Dum and Dummer")
        self.assertEqual(moves["03 E.flac"], self.root / "Key Glock" / "Dum and Dummer")
        own = {Path(c["file"]).name: c for c in om.fixup_with_others(plan, [om.OwnName("Twenty One Pilots")] + [True] * (len(plan) - 1))}
        first = Path(plan[0]["file"]).name
        self.assertEqual(own[first]["new"]["artist"], "Twenty One Pilots")
        self.assertEqual(own["03 E.flac"]["new"], {"albumartist": "Twenty One Pilots"})
        self.assertEqual(om.own_artists("Tyler, The Creator"), ["Tyler, The Creator"])
        self.assertEqual(om.own_artists("Young Dolph & Key Glock"), ["Young Dolph", "Key Glock"])
        left = om.fixup_with_others(plan, [False] * len(plan))
        self.assertEqual(left, [])


class CommaNameTest(unittest.TestCase):
    def test_comma_names(self):
        songs = [{"file": "/m/a.flac", "title": "EARFQUAKE", "artist": "Tyler, The Creator", "albumartist": "Tyler, The Creator", "album": "IGOR"},
                 {"file": "/m/b.flac", "title": "Mr Blue Sky", "artist": "Simon & Garfunkel", "album": "Bookends"}]
        self.assertEqual([c["title"] for c in om.fixup_plan(songs) if not c.get("unsure")], [])
        solo = om.solo_artists(songs)
        self.assertEqual(om.main_artist("Tyler, The Creator", solo), "Tyler, The Creator")
        self.assertEqual(om.main_artist("Pusha T, Clipse & Nigo"), "Pusha T")
        self.assertEqual(om.main_artist("Drake, Future", {"drake"}), "Drake")
        self.assertNotIn("simon", solo)


class InFoldersTest(unittest.TestCase):
    def test_in_folders(self):
        import os
        base = os.path.join(os.sep, "music", "Cavetown")
        self.assertTrue(om.in_folders(os.path.join(base, "Lemon Boy", "a.flac"), [base]))
        self.assertTrue(om.in_folders(base, [base + os.sep]))
        self.assertFalse(om.in_folders(base + " offline" + os.sep + "a.flac", [base]))   # a sibling with a longer name
        self.assertFalse(om.in_folders(os.path.join(base, "a.flac"), []))


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class SkipFoldersMoveTest(FixupTest):
    def test_moves_skip(self):
        songs = self.songs()
        plan = [c for c in om.fixup_plan(songs) if not c.get("unsure")]
        r = self.root
        holding = str(r / "Quinn XCII & Jeremy Zucker" / "Change of Scenery II" / "04 Holding On.flac")
        self.assertIn(holding, dict(om.fixup_moves(plan, songs, [str(r)])))
        # out of a folder left alone: stays; into one: stays too
        self.assertNotIn(holding, dict(om.fixup_moves(plan, songs, [str(r)], [str(r / "Quinn XCII & Jeremy Zucker")])))
        self.assertNotIn(holding, dict(om.fixup_moves(plan, songs, [str(r)], [str(r / "Quinn XCII")])))


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class SinglesAndRemixesTest(unittest.TestCase):
    """2.0: a single of a song on an album you have (but not that song on it): onto the album, its edition
    when you have one; a remix: onto the album its song is on."""
    def setUp(self):
        self.root = r = Path(tempfile.mkdtemp())
        h = "half•alive"
        flac(r / h / "Persona (Extended)" / "01 Hot Mess.flac", title="Hot Mess", artist=h, albumartist=h, album="Persona (Extended)", tracknumber="1")
        flac(r / h / "Songs" / "01 Songs.flac", title="Songs", artist=h, album="Songs", tracknumber="1")
        w = "The Wombats"
        for i, t in enumerate(["Lethal Combination", "Sorry I'm Late, I Didn't Want To Come"], 1):
            flac(r / w / "Fix Yourself, Not The World" / f"0{i} {t}.flac", title=t, artist=w, albumartist=w,
                 album="Fix Yourself, Not The World", tracknumber=str(i))
        flac(r / w / "Remix" / "01 Remix.flac", title="Sorry I'm Late, I Didn't Want To Come (Roosevelt Remix)", artist=w,
             album="Sorry I'm Late, I Didn't Want To Come (Roosevelt Remix)", tracknumber="1")
        flac(r / w / "Other" / "01 Other.flac", title="Ready for the High (Some DJ Remix)", artist=w,
             album="Ready for the High (Some DJ Remix)")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_singles_and_remixes(self):
        songs = [om.track_from_file(p) for p in self.root.rglob("*.flac")]
        asked = []

        def find_album(artist, title):
            asked.append(title)
            return {"songs": ["Songs", "Persona"], "ready for the high": ["Ready for the High", "Fix Yourself, Not The World"]}.get(
                om.norm(title), [])

        def tracklist(artist, album):
            return {"hot mess": 1, "songs": 7} if "Persona" in album else None
        plan = {Path(c["file"]).name: c for c in om.fixup_plan(songs, tracklist, find_album)}
        self.assertEqual(plan["01 Songs.flac"]["new"]["album"], "Persona (Extended)")   # Persona: the extended one you have
        self.assertEqual(plan["01 Songs.flac"]["new"]["tracknumber"], "7")
        self.assertEqual(plan["01 Remix.flac"]["new"]["album"], "Fix Yourself, Not The World")   # where its song is
        self.assertEqual(plan["01 Remix.flac"]["new"]["tracknumber"], "3")   # after the album's songs
        self.assertIn("remix", plan["01 Remix.flac"]["kinds"])
        self.assertEqual(plan["01 Other.flac"]["new"]["album"], "Fix Yourself, Not The World")   # its song not here: Deezer's
        self.assertNotIn("Sorry I'm Late, I Didn't Want To Come", asked)   # found on your album: not looked up

    def test_remix_of(self):
        self.assertEqual(om.remix_of("Sorry I'm Late (Roosevelt Remix)"), "Sorry I'm Late")
        self.assertEqual(om.remix_of("Heat Waves - Diplo Remix"), "Heat Waves")
        self.assertEqual(om.remix_of("Heat Waves"), "")
        self.assertEqual(om.remix_of("Remixed Feelings"), "")


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class AlbumPartsTest(unittest.TestCase):
    """2.0: an album in parts ("…: Pt. 1", "…: Pt. 2") onto the whole one; Other album picks another."""
    def setUp(self):
        self.root = r = Path(tempfile.mkdtemp())
        s = "Saint Motel"
        base = "The Original Motion Picture Soundtrack"
        flac(r / s / base / "01 Wetlands.flac", title="Wetlands", artist=s, albumartist=s, album=base, tracknumber="1")
        flac(r / s / "Pt 1" / "01 Van Horn.flac", title="Van Horn", artist=s, albumartist=s, album=f"{base}: Pt. 1", tracknumber="1")
        flac(r / s / "Pt 1" / "02 Save Me.flac", title="Save Me", artist=s, albumartist=s, album=f"{base}: Pt. 1", tracknumber="2")
        flac(r / s / "Pt 2" / "01 Preach.flac", title="Preach", artist=s, albumartist=s, album=f"{base}: Pt. 2", tracknumber="1")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_parts(self):
        self.assertEqual(om.album_part("X: Pt. 2"), ("X", 2))
        self.assertEqual(om.album_part("X (Part II)"), ("X", 2))
        self.assertEqual(om.album_part("Part of Me"), ("Part of Me", 0))
        songs = [om.track_from_file(p) for p in self.root.rglob("*.flac")]
        plan = {Path(c["file"]).name: c for c in om.fixup_plan(songs)}
        base = "The Original Motion Picture Soundtrack"
        for n in ("01 Van Horn.flac", "02 Save Me.flac", "01 Preach.flac"):
            self.assertEqual(plan[n]["new"]["album"], base)
            self.assertTrue(om.fixup_album_row(plan[n]))
        self.assertNotIn("01 Wetlands.flac", plan)
        self.assertEqual(plan["01 Preach.flac"]["new"]["tracknumber"], "4")   # after part 1's (and the whole one's first)
        changes = list(plan.values())
        out = om.fixup_with_others(changes, [om.OwnName("Somewhere Else")] + [True] * (len(changes) - 1))
        self.assertEqual(out[0]["new"]["album"], "Somewhere Else")
        self.assertNotIn("tracknumber", out[0]["new"])


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class SameCoverTest(unittest.TestCase):
    """2.0: an artist's albums with the same cover (a single with its album's art, the album's deluxe with a
    line of text more) onto one album; a different cover, or a blank one, left alone."""
    def setUp(self):
        self.root = r = Path(tempfile.mkdtemp())

        def pic(name, src, text=None):
            out = r / name
            vf = ["-vf", f"drawbox=x=10:y=10:w=60:h=12:color=white:t=fill"] if text else []
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", src, "-frames:v", "1", *vf, str(out)], check=True)
            return out.read_bytes()
        self.art = pic("a.jpg", "testsrc2=s=300x300")
        self.art2 = pic("a2.jpg", "testsrc2=s=300x300", text=True)   # the same, a label on it
        self.other = pic("b.jpg", "mandelbrot=s=300x300")
        self.blank = pic("c.jpg", "color=gray:s=300x300")
        n = "NF"
        files = {
            ("The Search", "01 Hate Myself"): self.art, ("The Search", "02 Leave Me Alone"): self.art,
            ("The Search - Single", "01 The Search"): self.art2,
            ("Perception", "01 Outcast"): self.other,
            ("Mansion", "01 Mansion"): self.blank, ("Hope", "01 Hope"): self.blank}
        for (album, song), cover in files.items():
            f = flac(r / n / album / f"{song}.flac", title=song[3:], artist=n, albumartist=n, album=album)
            om.write_cover(f, cover, "image/jpeg")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_same_cover(self):
        self.assertIsNone(om.picture_hash(self.blank))
        a, b, c = (om.picture_hash(x) for x in (self.art, self.art2, self.other))
        self.assertLessEqual(bin(a ^ b).count("1"), om.COVER_ALIKE)
        self.assertGreater(bin(a ^ c).count("1"), om.COVER_ALIKE)
        songs = [om.track_from_file(p) for p in self.root.rglob("*.flac")]

        def cover_of(path):
            d = om.embedded_cover(path)
            return om.picture_hash(d) if d else None
        plan = {Path(c["file"]).name: c for c in om.fixup_plan(songs, cover_of=cover_of)}
        self.assertEqual(plan["01 The Search.flac"]["new"]["album"], "The Search")   # onto the album, not the other way
        self.assertIn("cover", plan["01 The Search.flac"]["kinds"])
        self.assertEqual(set(plan), {"01 The Search.flac"})   # Perception: its own cover; Mansion, Hope: blank, and no album found: left as they are
        self.assertTrue(om.is_single("Singles Collection", "x"))
        self.assertTrue(om.is_single("RUN (Single)", "x"))
        self.assertFalse(om.is_single("Singular Act I", "x"))


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class AloneSongTest(unittest.TestCase):
    """2.0: a song alone on its album (the only one of it you have), or with no album, is looked for on an album or EP of
    yours like a single; none found: left as it is."""
    def setUp(self):
        self.root = r = Path(tempfile.mkdtemp())
        s = "Sigrid"
        for i, t in enumerate(["Mirror", "Don't Feel Like Crying"], 1):
            flac(r / s / "Sucker Punch" / f"0{i} {t}.flac", title=t, artist=s, albumartist=s, album="Sucker Punch", tracknumber=str(i))
        flac(r / s / "Strangers" / "01 Strangers.flac", title="Strangers", artist=s, album="Strangers EP Radio")   # alone on its album
        flac(r / s / "Other" / "01 Sight Of You.flac", title="Sight Of You", artist=s, album="Sight Of You (Live)")   # alone, no home

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_alone(self):
        songs = [om.track_from_file(p) for p in self.root.rglob("*.flac")]
        plan = {Path(c["file"]).name: c for c in om.fixup_plan(songs, None, lambda a, t: {"strangers": ["Strangers EP Radio", "Sucker Punch"]}.get(om.norm(t), []))}
        self.assertEqual(plan["01 Strangers.flac"]["new"]["album"], "Sucker Punch")   # Deezer: on Sucker Punch, which you have
        self.assertNotIn("01 Sight Of You.flac", plan)   # nowhere to go: left as it is
        self.assertNotIn("01 Mirror.flac", plan)


@unittest.skipUnless(shutil.which("ffmpeg"), "needs ffmpeg")
class NeverOntoASingleTest(unittest.TestCase):
    """2.0: Fix albums and artists never puts a song onto a single: two singles with the same cover stay as they are, and
    an album's song never goes onto a single."""
    def setUp(self):
        self.root = r = Path(tempfile.mkdtemp())
        n = "NF"
        art = r / "a.jpg"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=s=300x300", "-frames:v", "1", str(art)], check=True)
        for album, song in (("Hope - Single", "Hope"), ("Mansion - Single", "Mansion"), ("Clouds", "Clouds")):
            f = flac(r / n / album / f"{song}.flac", title=song, artist=n, albumartist=n, album=album)
            om.write_cover(f, art.read_bytes(), "image/jpeg")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_never_a_single(self):
        songs = [om.track_from_file(p) for p in self.root.rglob("*.flac")]

        def cover_of(path):
            d = om.embedded_cover(path)
            return om.picture_hash(d) if d else None
        for c in om.fixup_plan(songs, cover_of=cover_of):
            if "album" in c["new"]:
                self.assertFalse(om.is_single(c["new"]["album"], c["title"]), c)
                self.assertNotIn(c["new"]["album"], ("Hope - Single", "Mansion - Single", "Clouds"))
