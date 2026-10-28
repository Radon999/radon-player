"""The Apple Music update: the queue view's Clear, dragging songs in the queue, Autoplay, and the
backdrop's colours from the cover."""
import tempfile
import types
import unittest
from pathlib import Path

from app import load

om = load()


def app_with(queue, index):
    a = types.SimpleNamespace(queue=[dict(t) for t in queue], index=index, autoplay=True, repeat="off",
                              radio_from=None, ended=False, win=None, changes=[], radios=[], paused=False)
    a.after_queue_change = lambda: a.changes.append([t["id"] for t in a.queue])
    a.extend_radio = lambda t: a.radios.append(t["id"])
    a.load_current = lambda: None
    a.set_paused = lambda p: setattr(a, "paused", p)
    a.save_soon = lambda: None
    a.current_track = lambda: a.queue[a.index] if 0 <= a.index < len(a.queue) else None
    for name in ("clear_queued", "move_in_queue", "autoplay_soon", "next", "set_autoplay"):
        setattr(a, name, getattr(om.MusicApp, name).__get__(a))
    return a


class QueueViewTest(unittest.TestCase):
    def test_clear_takes_only_what_you_queued(self):
        a = app_with([{"id": "now"}, {"id": "q1", "queued": True}, {"id": "n1"}, {"id": "q2", "queued": True}], 0)
        a.clear_queued()
        self.assertEqual([t["id"] for t in a.queue], ["now", "n1"])

    def test_drag_moves_a_song(self):
        a = app_with([{"id": x} for x in "abcde"], 0)
        a.move_in_queue(4, 1)
        self.assertEqual([t["id"] for t in a.queue], list("aebcd"))
        a.move_in_queue(0, 2)   # the one playing stays put
        self.assertEqual([t["id"] for t in a.queue], list("aebcd"))

    def test_autoplay_carries_on(self):
        a = app_with([{"id": "a"}, {"id": "b"}, {"id": "c"}], 0)
        a.next()                            # two songs left: songs like the last are fetched
        self.assertEqual(a.radios, ["c"])
        a.index = 2
        a.radio_from = "x"
        a.next()                            # at the end: it stops and asks for more
        self.assertTrue(a.ended)
        self.assertEqual(a.radios[-1], "c")

    def test_autoplay_off(self):
        a = app_with([{"id": "a"}], 0)
        a.autoplay = False
        a.next()
        self.assertEqual(a.radios, [])


class BackdropColoursTest(unittest.TestCase):
    def test_colours_from_a_cover(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("no PIL")
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "c.png"
            Image.new("RGB", (80, 80), (30, 90, 200)).save(p)
            data, w, h, top, bottom, bars = om.blurred_cover(p)
            self.assertEqual(len(data), w * h * 3)
            self.assertGreater(top[2], top[0])          # blue, as the cover is
            self.assertLess(sum(bottom), sum(top))      # deeper at the bottom
            self.assertGreater(sum(bars), sum(top))     # the bars a light shade of it


if __name__ == "__main__":
    unittest.main()
