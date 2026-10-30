#!/usr/bin/env python3
"""FEATURES.md and CHANGELOG.md -> common/org/omarchy/music/ui/FeatureGuide.kt (the Features and Changelog tabs show
them). Run after each edit. One string per line, so a change changes only its own lines here.

The rules: every new feature is described in FEATURES.md, in the section it belongs with (sound with sound, tagging with
tagging); every update adds its own entry at the TOP of CHANGELOG.md ("## <version> · <date>", then a line per change),
so the newest is always first, like patch notes."""
import pathlib
here = pathlib.Path(__file__).parent
def lit(c): return '"' + c.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'
def block(name, doc, md):
    lines = (here / md).read_text().split("\n")
    return f"/** {doc} */\nval {name}: String = listOf(\n    " + ",\n    ".join(lit(l) for l in lines) + ",\n).joinToString(\"\\n\")\n"
(here / "common/org/omarchy/music/ui/FeatureGuide.kt").write_text(
    "package org.omarchy.music.ui\n\n// Made from core/FEATURES.md and core/CHANGELOG.md by core/gen-guide.py: edit those, then run it.\n\n"
    + block("FEATURE_GUIDE", "Everything the app does (the Features tab).", "FEATURES.md") + "\n"
    + block("CHANGELOG", "What each update brought, the newest first (the Changelog tab).", "CHANGELOG.md"))
