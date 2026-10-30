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
- [x] PC: tray (close keeps playing; Play/Pause, Next, Previous, Mini player, Quit), media keys (Windows), mini player window
- [x] PC: Discord (what you play on your profile), right-click = long-press menus, PC wording (click, Recycle Bin, …)
- [x] PC: a note on a new song while in the tray, Start with Windows (in the tray)
- [ ] PC: Windows media flyout (SMTC)
- [x] Both: Fix albums and artists, Folders to leave alone, Empty folders (PC), Live in song names, Copy the names
- [x] PC: Loudness check (ffmpeg), Identify by sound (AcoustID); both: Lyrics mode in Settings, «Close selection after an option» kept
- [x] Phone: Loudness check (MediaCodec, only while charging) and Identify by sound (Chromaprint ported to Kotlin, same
      fingerprint as ffmpeg's); both: Tagger «Scan all again» (the scan skips songs checked before); lyrics into files (Tagger › Lyrics)
- [x] PC: visualizer and Now playing bars (the song decoded again by ffmpeg in step)
- [x] PC: sign-ins: Spotify caught on 127.0.0.1:8898, Last.fm asked until approved (as the Python app); Tidal pastes the "Oops" address, as the Python app does
- [x] PC: Add a folder… (a folder picker)
- [x] PC: tags, Tagger, covers and lyrics into your own FLACs (the content:// gates now ask FileTags); keyboard shortcuts; Identify in the song menu;
      Delete your own files (Recycle Bin); downloads to any folder;
      one instance (Instance.kt, port 8896 + key) with the command-line verbs and song files
- [ ] PC (from the audit): Upgrade to FLAC for your own files,
      Cast, mini player's seek and Up next, Linux MPRIS, YouTube Music / Apple sign-in on the PC
- [ ] PC: the Python app's tools (Fix albums and artists, …) checked one by one
- [ ] PC: every Python setting checked against the shared Settings (wording for the PC where it says Android)
- [x] PC: the Python app's data brought over on the first start (PythonImport)
- [x] Windows: mpv's pipe with overlapped I/O (the window froze), saves replace the file, log.txt + watchdog
- [ ] PC: mpv and ffmpeg inside the installer (Bins finds them; the CI step is waiting for the owner's OK)
- [ ] Windows installer (MSI) tested on the PC; then it replaces the Python app
- [x] Feature guide (core/FEATURES.md, gen-guide.py) and the Changelog & features tab
- [x] Settings descriptions rewritten in detail
- [ ] the guide sent to the owner and put on GitHub when done
