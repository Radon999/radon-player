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
- [x] PC: Windows media flyout (SMTC): desktop/smtc radon-media.exe (.NET 8, built in the Windows CI job) + MediaSession.kt
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
- [x] PC: YouTube Music / Apple sign-in (paste the browser's cookies, as the PC has no web page inside the app)
- [x] PC: Cast (Chromecasts, Google speakers and TVs; its own Cast sender, the files served from the PC)
- [x] Second audit (old Android app and old Python app against both new apps): Tidal keys and the version built into the PC app, restore closing the app, Copy for pictures, the old command line and keyboard shortcuts, Ctrl/Shift+click, Wrapped keys, the self-installing update, the mini player queue, the DJ folder pick, the first-close hint
- [-] PC: Linux MPRIS: not wanted (the app is for Windows and Android)
- [x] Backups restore across devices (phone ↔ PC): device settings kept, song files matched to the same songs here
- [x] PC: the Python app's tools checked one by one (the audit: ~115 settings, menus, shortcuts, tools)
- [x] PC: every Python setting checked against the shared Settings (the same audit)
- [x] PC: the Python app's data brought over on the first start (PythonImport)
- [x] Windows: mpv's pipe with overlapped I/O (the window froze), saves replace the file, log.txt + watchdog
- [x] PC: mpv and ffmpeg inside the installer (the Windows CI job puts them in resources/windows; Bins finds them)
- [ ] Windows installer (MSI) tested on the PC; then it replaces the Python app
- [x] Feature guide (core/FEATURES.md, gen-guide.py) and the Changelog & features tab
- [x] Settings descriptions rewritten in detail
- [ ] the guide sent to the owner and put on GitHub when done
