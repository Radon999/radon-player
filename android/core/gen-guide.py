#!/usr/bin/env python3
"""FEATURES.md -> common/org/omarchy/music/ui/FeatureGuide.kt (the Changelog & features tab shows it). Run after each edit."""
import pathlib
here = pathlib.Path(__file__).parent
text = (here / "FEATURES.md").read_text()
chunks = [text[i:i + 20000] for i in range(0, len(text), 20000)]   # a JVM constant holds 64 KB
body = " +\n    ".join('"' + c.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n") + '"' for c in chunks)
(here / "common/org/omarchy/music/ui/FeatureGuide.kt").write_text(
    "package org.omarchy.music.ui\n\n// Made from core/FEATURES.md by core/gen-guide.py: edit that, then run it.\n\n"
    "/** The feature guide and changelog (the Changelog & features tab). */\nval FEATURE_GUIDE: String =\n    " + body + "\n")
