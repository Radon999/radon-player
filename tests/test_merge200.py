"""2.0: editions, featured-artist splits and singles merged in Most played."""
import types
import unittest

from app import load

om = load()


class EditionTest(unittest.TestCase):
    def test_album_edition(self):
        e = om.album_edition
        self.assertEqual(e("Persona (Extended)"), ("Persona", "Extended"))
        self.assertEqual(e("Night Visions (Deluxe)"), ("Night Visions", "Deluxe"))
        self.assertEqual(e("Origins (Deluxe Edition)"), ("Origins", "Deluxe Edition"))
        self.assertEqual(e("Origins [Deluxe Version]"), ("Origins", "Deluxe Version"))
        self.assertEqual(e("Ten - 10th Anniversary Edition"), ("Ten", "10th Anniversary Edition"))
        self.assertEqual(e("Smithereens - Deluxe"), ("Smithereens", "Deluxe"))
        self.assertEqual(e("Thing (Expanded Edition)"), ("Thing", "Expanded Edition"))   # any "… Edition" (2.0)
        for same in ("Death Race For Love (Sessions)", "Death Race For Love (Bonus Track Version)", "Persona",
                     "Mercury - Acts 1 & 2", "Folklore (Deluxe Sessions)", "(Deluxe)"):
            self.assertEqual(e(same)[1], "", same)

    def test_single_and_artist(self):
        self.assertTrue(om.is_single("Selene", "Selene"))
        self.assertTrue(om.is_single("Selene - Single", "Other"))
        self.assertFalse(om.is_single("Night Visions", "Selene"))
        self.assertEqual(om.artist_key("Quinn XCII & Jeremy Zucker"), om.artist_key("Quinn XCII"))
        self.assertEqual(om.main_artist("Quinn XCII & Jeremy Zucker", {"quinnxcii"}), "Quinn XCII")
        self.assertEqual(om.main_artist("Simon & Garfunkel", {"quinnxcii"}), "Simon & Garfunkel")
        self.assertEqual(om.main_artist("Pusha T, Clipse & Nigo"), "Pusha T")
        self.assertEqual(om.main_artist("Joji feat. Diplo"), "Joji")

    def test_top_albums_merge(self):
        songs = [
            {"id": "1", "title": "Still Feel.", "artist": "half•alive", "album": "Persona", "plays": 1351},
            {"id": "2", "title": "Arrow", "artist": "half•alive", "album": "Persona (Extended)", "plays": 40},
            {"id": "3", "title": "Still Feel. (Live)", "artist": "half•alive & The Walters", "albumartist": "half•alive & The Walters",
             "album": "Persona (Extended)", "plays": 8},
            {"id": "4", "title": "Selene", "artist": "Imagine Dragons", "album": "Selene", "plays": 2},
            {"id": "5", "title": "Radioactive", "artist": "Imagine Dragons", "album": "Night Visions", "plays": 4},
            {"id": "6", "title": "Lucid", "artist": "Juice WRLD", "album": "Death Race For Love (Sessions)", "plays": 3},
            {"id": "7", "title": "Robbery", "artist": "Juice WRLD", "album": "Death Race For Love (Bonus Track Version)", "plays": 5},
        ]
        copy = {"id": "9", "title": "Selene", "artist": "Imagine Dragons", "album": "Night Visions (Deluxe)"}
        mine = {("L",) + om.stats_key("Selene", "Imagine Dragons"): [{"title": "Selene", "artist": "Imagine Dragons", "album": "Selene"}, copy]}
        app = types.SimpleNamespace(most_played=lambda period="all": [dict(s) for s in songs], song_index=lambda: mine)
        got = {a["title"]: (a["artist"], a["plays"]) for a in om.MusicApp.top_albums(app)}
        self.assertEqual(got["Persona (Extended)"], ("half•alive", 1399))
        self.assertEqual(got["Night Visions (Deluxe)"], ("Imagine Dragons", 6))
        self.assertIn("Death Race For Love (Sessions)", got)
        self.assertIn("Death Race For Love (Bonus Track Version)", got)
        self.assertNotIn("Persona", got)


if __name__ == "__main__":
    unittest.main()


class TimeAndMilestonesTest(unittest.TestCase):
    def test_listen_time(self):
        app = types.SimpleNamespace(my_songs=lambda: [{"title": "Song", "artist": "A", "duration": 200}])
        now = om.time.time()
        lt = om.ListenTime(app, {"week": now - 7 * 86400})
        lt.add(now - 3600, "Song", "A", "Album (Deluxe)", 2)            # 2 × its own 200 s
        lt.add(now - 10 * 86400, "Other", "A & B", "Album", 1, 60000)   # Spotify said 60 s; not this week
        t = lt.totals()
        self.assertEqual(t["week"]["secs"], 400)
        self.assertEqual(t["all"]["secs"], 460)
        self.assertEqual(t["all"]["artists"][0], ("A", 460))
        self.assertEqual(t["all"]["albums"][0][0], "Album (Deluxe)")

    def test_milestones(self):
        songs = [{"title": "Mercury", "artist": "Imagine Dragons", "plays": 990}]
        first = om.new_milestones(songs, [], [])
        self.assertEqual([m["at"] for m in first], [0, 0, 0])   # 100, 250 and 500   # the first count only notes where things stand
        songs[0]["plays"] = 1004
        later = om.new_milestones(songs, [], first)
        self.assertEqual(later[0]["plays"], 1000)
        self.assertGreater(later[0]["at"], 0)
        again = om.new_milestones(songs, [], later)
        self.assertEqual([m["plays"] for m in again if m["plays"] == 1000], [1000])   # once
        self.assertEqual(next(m for m in again if m["plays"] == 1000)["at"], later[0]["at"])   # its date kept

    def test_milestones_dated_from_plays(self):
        # 40 plays with no time, then 100 timed plays a day apart: the 100th play is the 60th timed one.
        songs = [{"title": "Mercury", "artist": "Imagine Dragons", "plays": 140}]
        key = om.stats_key("Mercury", "Imagine Dragons")
        times = [(1_700_000_000 + i * 86400, 1) for i in range(100)]
        ms = om.new_milestones(songs, [{"name": "Imagine Dragons", "plays": 140}], [],
                               ({key: times}, {om.artist_key("Imagine Dragons"): times}))
        song = [m for m in ms if m["what"] == "song"]
        self.assertEqual([(m["plays"], m["at"]) for m in song], [(100, 1_700_000_000 + 59 * 86400)])
        year = om.period_milestones(ms, 1_700_000_000, 1_700_000_000 + 365 * 86400)
        self.assertEqual({m["what"] for m in year}, {"song", "artist"})
        self.assertEqual(om.period_milestones(ms, 1, 2), [])   # nothing in a period long before


class ArtistNamedTest(unittest.TestCase):
    """A duo credit named as its lead artist when the lead is an artist of their own (2.0)."""
    def test_named(self):
        from collections import Counter
        self.assertEqual(om.artist_named(Counter({"Quinn XCII & Jeremy Zucker": 30}), {"quinnxcii"}), "Quinn XCII")
        self.assertEqual(om.artist_named(Counter({"Simon & Garfunkel": 9}), {"quinnxcii"}), "Simon & Garfunkel")
        self.assertEqual(om.artist_named(Counter({"Quinn XCII & Jeremy Zucker": 30, "Quinn XCII": 2}), set()), "Quinn XCII")
        self.assertEqual(om.artist_named(Counter({"Sody & Cavetown": 27}), {"cavetown"}), "Sody & Cavetown")


class ArtistResolverTest(unittest.TestCase):
    """2.0: a joint credit's plays count for its main artist; unclear pairs stay pairs until picked."""
    songs = [{"artist": "Quinn XCII", "albumartist": "Quinn XCII"}, {"artist": "Surfaces"}, {"artist": "BENEE"},
             {"artist": "Berner & Cozmo"}, {"artist": "Berner & Styles P"}, {"artist": "Tyler, The Creator"},
             {"artist": "Lil Nas X"}, {"artist": "Jack Harlow"}, {"artist": "K.Flay"}, {"artist": "K.Flay"}, {"artist": "KFlay"}]

    def test_resolve(self):
        r = om.artist_resolver(self.songs)
        self.assertEqual(r("Quinn XCII & Jeremy Zucker"), "Quinn XCII")
        self.assertEqual(r("Big Wild & Surfaces"), "Surfaces")
        self.assertEqual(r("hard life & BENEE"), "BENEE")
        self.assertEqual(r("Berner & Cozmo"), "Berner")
        self.assertEqual(r("Nas & 21 Savage"), "Nas & 21 Savage")          # not clear: a pair until you pick
        self.assertEqual(r("Lil Nas X & Jack Harlow"), "Lil Nas X & Jack Harlow")
        self.assertEqual(r("Joji feat. Diplo"), "Joji")
        self.assertEqual(r("Tyler, The Creator"), "Tyler, The Creator")
        self.assertEqual(r("Joji, Rei Brown"), "Joji")
        self.assertEqual(r("KFlay"), "K.Flay")                              # spelt as most of yours are
        picked = om.artist_resolver(self.songs, {om.pair_key("Jack Harlow & Lil Nas X"): "Lil Nas X"})
        self.assertEqual(picked("Lil Nas X & Jack Harlow"), "Lil Nas X")   # either way round
        self.assertNotEqual(om.resolved_key("Nas & 21 Savage"), om.resolved_key("Nas"))

    def test_joint_list(self):
        from collections import Counter
        j = om.joint_credits(Counter({"Nas & 21 Savage": 5, "21 Savage & Nas": 1, "Quinn XCII & Jeremy Zucker": 9}), self.songs)
        self.assertEqual([c for c, _p, _x in j], ["Nas & 21 Savage"])


class SimilarArtistsTest(unittest.TestCase):
    def test_similar(self):
        r = om.similar_artists({"Cavetown", "Bad Suns", "Jack Harlow", "Forrest"},
                               {"cavetown offline": 71, "Bad Sons": 3, "Jack Johnson": 44, "Forrest Frank": 400, "Young the Giant": 2,
                                "Young Dolph": 9},
                               {"cavetownoffline": {"candle"}, "cavetown": {"candle", "lemon boy"}, "forrestfrank": {"back in"},
                                "forrest": {"back in"}}, {}, set(), {"cavetown": 30, "badsuns": 4, "jackharlow": 5, "forrest": 20})
        got = {(a, b) for a, b, _p, _s in r}
        self.assertIn(("cavetown offline", "Cavetown"), got)
        self.assertIn(("Bad Sons", "Bad Suns"), got)
        self.assertIn(("Forrest Frank", "Forrest"), got)
        self.assertNotIn(("Jack Johnson", "Jack Harlow"), got)    # only a first name in common
        self.assertFalse(any("Young" in a for a, _b in got))     # a common word
        kept = om.similar_artists({"Cavetown"}, {"cavetown offline": 1}, {}, {}, {"cavetownoffline>cavetown"}, {"cavetown": 3})
        self.assertEqual(kept, [])
