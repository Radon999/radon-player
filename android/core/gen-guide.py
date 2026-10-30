#!/usr/bin/env python3
"""FEATURES.md -> common/org/omarchy/music/ui/FeatureGuide.kt (the Changelog & features tab shows it). Run after each edit.
One string per line of the guide, so a change to the guide changes only its own lines here."""
import pathlib
here = pathlib.Path(__file__).parent
lines = (here / "FEATURES.md").read_text().split("\n")
def lit(c): return '"' + c.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'
body = ",\n    ".join(lit(l) for l in lines)
(here / "common/org/omarchy/music/ui/FeatureGuide.kt").write_text(
    "package org.omarchy.music.ui\n\n// Made from core/FEATURES.md by core/gen-guide.py: edit that, then run it.\n\n"
    "/** The feature guide and changelog (the Changelog & features tab). */\nval FEATURE_GUIDE: String = listOf(\n    " + body + ",\n).joinToString(\"\\n\")\n")
