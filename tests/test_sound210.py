"""2.1: the sound: EQ files you import, tilt, the Mangird Top Pro's built-in correction."""
import unittest

from app import load

om = load()

SQUIG = """Preamp: -4.2 dB
Filter 1: ON LSQ Fc 105 Hz Gain -2.6 dB Q 0.700
Filter 2: ON PK Fc 310 Hz Gain -2.4 dB Q 0.800
Filter 3: ON HSQ Fc 10000 Hz Gain -7.6 dB Q 0.700
"""


class EqFileTest(unittest.TestCase):
    def test_parametric(self):
        self.assertEqual(om.parse_eq_file(SQUIG), [("ls", 105, -2.6, 0.7), ("pk", 310, -2.4, 0.8), ("hs", 10000, -7.6, 0.7)])

    def test_graphic(self):
        got = om.parse_eq_file("GraphicEQ: 20 -1.0; 1000 0.0; 20000 2.0")
        self.assertEqual(len(got), 10)
        self.assertEqual(got[5], ("pk", 1000, 0.0, om.EQ_Q))

    def test_nothing(self):
        with self.assertRaises(ValueError):
            om.parse_eq_file("hello")


class TiltTest(unittest.TestCase):
    def test_only_down(self):
        self.assertIn("lowshelf", om.tilt_graph(2))
        self.assertIn("g=-2.0", om.tilt_graph(2))
        self.assertIn("highshelf", om.tilt_graph(-1.5))
        self.assertIn("g=-1.5", om.tilt_graph(-1.5))
        self.assertEqual(om.tilt_graph(0), "")


class BuiltInTest(unittest.TestCase):
    def test_mangird(self):
        name, filters = om.HEADPHONES["mangird-top-pro"]
        self.assertIn("Mangird Top Pro", name)
        self.assertEqual(len(filters), 10)
        self.assertLessEqual(om.eq_preamp(filters), 0)


if __name__ == "__main__":
    unittest.main()
