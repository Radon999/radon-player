#!/usr/bin/env bash
# The Linux package asks for what the app runs but does not bring: mpv (plays the music) and ffmpeg (reads files,
# draws the spectrum). Windows' installer carries both; on Linux they come from the system, so the package must say
# it needs them, or the app installs and then cannot play (seen in the first start on Linux, 16 November 2026).
# Usage: ci/linux-deps.sh path/to/radon-player.deb   (the package is rewritten in place)
set -euo pipefail
deb="$1"
dir="$(mktemp -d)"
dpkg-deb -R "$deb" "$dir"
ctl="$dir/DEBIAN/control"
if grep -q '^Depends:.*\bmpv\b' "$ctl"; then
  echo "linux-deps: the package asks for mpv already"
elif grep -q '^Depends:' "$ctl"; then
  sed -i 's/^Depends: \(.*\)$/Depends: \1, mpv, ffmpeg/' "$ctl"
  echo "linux-deps: added mpv and ffmpeg to what the package asks for"
else
  echo "Depends: mpv, ffmpeg" >> "$ctl"
  echo "linux-deps: the package now asks for mpv and ffmpeg"
fi
dpkg-deb -b "$dir" "$deb" >/dev/null
rm -rf "$dir"
dpkg-deb -f "$deb" Depends
