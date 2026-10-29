# How Radon Player got here (1.3 to 1.4.2)

What was asked for, what went wrong and why, what was decided, and what's still open. CHANGELOG.md has
the user-facing lists; this is the background a new session needs. Newest first.

## Still open

- **Android release signing is broken**: the repository's `ANDROID_KEYSTORE` secret doesn't decode, so
  `release.yml` fails at android-sign. Every release since 1.3.0 ships the test-key build instead (see
  CLAUDE.md). Moving to a real release key means a different package or key: the owner would have to
  uninstall and reinstall (losing app data). Don't do it without asking.
- **Not tested on real devices or services by Claude**: New releases' "open a release" and its discovery
  playlist (Deezer and the streaming services were unreachable from the build sandbox); the Android
  between-songs fix (needs a phone with the screen off); the desktop visualizer at 4K (only tested small).
  Ask the owner how they went.
- **"Cigarettes" by Juice WRLD** wasn't in the PC's library: Most played can't play the owner's copy there
  until the file is on the PC.
- **Picture-in-picture on Android** can't show an interactive Up next list (the desktop mini player has one).
- **Tech-stack migration**: planned for last (see CLAUDE.md).

## 1.4.2 (released): steadier, smoother, Most played by period

Asked for, after a long day of testing at work:

- **Android app closing every two or three songs** (the owner had to press play on their headphones to
  start it again; the PC never crashed). Cause: between songs ExoPlayer is ENDED while the next song loads.
  Media3 then takes the service out of the foreground; with the screen off, Android can't put it back
  (ForegroundServiceStartNotAllowedException, caught by Media3) and kills the background process about a
  minute later. Fix: `Music.advancing` is set while moving on, and the session's player reports BUFFERING
  instead of ENDED meanwhile (`MusicService.queueAware`, which also wraps the session's listeners). At the
  end of the queue with Autoplay, it stays "playing" until the radio's songs arrive. Also added: a coroutine
  exception handler on `Music.scope` (errors there no longer crash the app) and `Crashes.kt` (Settings ›
  Problems, with Copy).
- **Wrapped should use Most played's data** (Last.fm + Spotify + Apple). It already added the Spotify and
  Apple plays; the owner's Spotify export only covers 2021-2025, so the 2026 Wrapped is Last.fm only.
  Changed: a song now counts once across sources (`year_stats` / `yearStats` use the stats key).
- **Visualizer lagging on PC** (the phone's was fine; the PC mini player's was smooth). Cause: the Visualizer
  tab drew with cairo into a window-sized picture every frame (32 MB at 4K) handed to the GPU each time.
  Now `SnapPainter` draws the same shapes as GTK render nodes. Now playing's bars were redrawing the whole
  window 30 times a second: they're now `BackdropBars`, a strip only as tall as the bars.
- **Most played: This week, This month, This year, All time** (rolling 7/30/365 days), each adding up every
  source with a time. `Stats.periods` (desktop and Android), `STATS_VERSION` 3.
- **Bigger grids on the phone**: the layout button cycles list, small, grid (as before), big (two across),
  biggest (one across). The owner first described these as "15 by 15" and "25 by 25" (or 20 by 20): bigger
  covers. On a phone two and one across are the biggest there are.
- **New releases**: tapping a release opens it straight on Tidal or YouTube Music (`release_find`: an album
  by name and artist, else the single's song), never searching local files. **Select** picks releases and
  artists across the tabs; "Discovery playlist" makes "New releases Discovery playlist" / "New releases
  discovered" from their songs the owner hasn't got, working like an artist's discovery playlist.
- **Playlists**: discovery playlists fold under one "Discovery playlists" row.
- The DJ already used every source (Last.fm, Spotify, Apple); Spotify's import is just much bigger.
- A failed stats refresh retried in a loop: it now waits two minutes.

## 1.4.1 (released): Most played, fixed

The owner's most anticipated feature. Decisions they made:

- Plays from Last.fm, Spotify and Apple Music are **always added up** (50 + 30 = 80), even where Spotify
  also scrobbled to Last.fm. Imports are frozen; new plays come only from Last.fm. The old "counts from
  halfway" rule is gone. Most played updates on its **Refresh** button.
- Every song shows, each once. "Next Semester" was missing because Spotify plays were counted against the
  longest play (401 plays became 3). Rows that are the same song of the owner's are merged.
- **"Only songs on this device"** (on by default): only songs that match the owner's files, matched by title,
  artist and album like the duplicates check. All plays still count.
- Most played and artist pages played the wrong copy ("Opus 48 kHz", no cover), e.g. "Thicker Than Dust"
  (K.Flay tagged KFlay) and Sigrid's songs: fixed by the stats key ignoring punctuation and by `fm_song`
  trying the owner's copies first (same album, then FLAC).
- Removed at the owner's request: Moods (tab and menu), smart playlists (the Repeat lists stay),
  "Is it really lossless?". The DJ was reworked like Spotify's from stats, without moods.
- New releases: every artist with songs on the device, looked up only when Refresh is pressed.
- PC: Wrapped's "list index out of range" (an artist named "Unknown artist": `lead_artist`), the grid
  growing into a line that widened the window (it measured itself), the EQ's own Full screen toggle replaced
  by the window's maximize, Wrapped's full screen = maximize on Windows (the owner is fine with that).
- Mini player (PC): an Up next view with Shuffle, Repeat and Autoplay.

## 1.4.0 (released)

Sound quality, stats and discovery: see CHANGELOG. This was when the release-signing secret turned out to be
broken and the test-key APK was found to be what 1.3.0 had shipped, so 1.4.0 shipped it too.

## 1.3.0

The Apple Music-style redesign on both platforms; the first release with the Android app as an equal.
