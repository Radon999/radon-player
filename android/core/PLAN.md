# One codebase: the plan (kept up as the work goes)

Both apps are JVM (the phone: Android; the PC: Compose Desktop), so the shared code is plain source compiled
into both, not a separate library:

- `core/common`: the shared code (the app's logic, then its screens). Compiled by `app` (with `core/android`)
  and by `desktop` (with `core/desktop`).
- `core/android`, `core/desktop`: each platform's side, the same names: `Ctx` (where files live; Android's Context
  on the phone), `openIn`, `uriExists`, `deleteUri`, `logWarn`, `Keys`, `appVersion`; more as files move.
- `shared` (plain JVM module) and `ui-common` (KMP: theme, icons, widgets) stay as they are.

Order: data and services (Stores, Tidal, Spotify, Apple, Meta, Crashes: moved), then Prefs, Stats, Lyrics,
LastFm and the histories, then Music itself with the player behind an engine interface (ExoPlayer on the phone,
mpv on the PC), then the screens (ui/) one by one, then the PC-only tools (folders, Fix albums and artists, …).

## Checklist (ported to core = works on both)
- [x] Library, Plays, Playlists, playlist folders (Stores)
- [x] Tidal, Spotify, Apple Music services
- [x] Song tags (Meta), Crashes
- [x] Widgets (ui-common)
- [x] Prefs, Stats, Lyrics, Last.fm, Spotify/Deezer/Apple histories, Wrapped, Repeat, DJ, scans, Downloader, Tagger
- [x] Music (queues, playback, stats) + Engine (ExoEngine on the phone; MpvEngine on the PC)
- [x] Crossfade fix: the ending song's player gets the same EQ/Boost/limiter (it came in loud before)
- [x] PC: folder scan (LocalFiles with jaudiotagger), services, PcApp start; first PC screen plays through Music
- [x] Screens: every one of the phone's screens is shared (core/common/ui) and runs on the PC (tested on Linux: my music,
      play, Now playing, Settings, Equalizer, Esc = back). Android bits behind ui/PlatformUi (pictures, file pickers,
      permissions, share, clipboard, mini player, volume, visualizer) and PlatformSheets (sign-in, removing files)
- [x] Pictures drawn the same on both (Painter: collage, share card, lyrics card)
- [x] PC engine: EQ, Boost, Detail, Tilt, Balance, crossfeed, mono, clip guard, loudness compensation, crossfade, volume (kept)
- [x] PC outputs: Windows' default output from the registry (Linux: wpctl), a separate EQ for each, as the Python app
- [x] PC: bit-perfect (exclusive mode through mpv), HQ resampling, dither (swresample), levelling target (shared rgGain)
- [x] PC: phone remote server (Settings › Play on › Phone remote: code, pairing, mDNS, status, commands, handoff)
- [ ] PC: tray, media keys, notifications (now playing), mini player window, run at start-up
- [ ] PC: visualizer and Now playing bars (spectrum from mpv), sign-in pages (now: paste the address it ends on)
- [ ] PC: Choose folders as a folder picker; the Python app's tools (Fix albums and artists, …) checked one by one
- [ ] PC: every Python setting checked against the shared Settings (wording for the PC where it says Android)
- [ ] Windows installer (MSI) tested on the PC; then it replaces the Python app
- [ ] Settings descriptions rewritten; feature guide; Changelog & features tab
