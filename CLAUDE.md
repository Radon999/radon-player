# Radon Player: notes for Claude

Radon Player is a music player for **Windows** and **Android**. It started as Omarchy Music / VarMusic by
the owner's brother; Radon999 carries it on here. Linux is no longer a target. `docs/history.md` has the
story of recent versions (what broke, why, how it was fixed, what's still open): read it before big changes.

## How the owner wants to work

- **Every change goes to both platforms**, with the same UI and features. If something can't be the same
  on one of them, do the closest thing and **say plainly what the compromise is**.
- **Publish only when asked** ("publish it"). Releases, pushes to the default branch and anything public
  wait for that. Ask first otherwise.
- Never force-push. Never delete the owner's files or data. Don't type passwords or handle login codes or
  signing keys: the owner does those.
- The owner is not a programmer: explain results in plain words, short, with what to test.
- Before saying something is fixed, test it (unit tests, and screenshots of the desktop app where it's visual).
- A tech-stack migration (probably Compose Multiplatform, one codebase for both) is planned **last**, after
  the features are done. Don't start it unasked.

## Layout

- `omarchy-music`: the whole desktop app, one Python file (GTK 4 + libadwaita, mpv for playback, ffmpeg for
  the visualizer's spectrum). `VERSION = "x.y.z"` near the top.
- `android/`: the Android app (Kotlin, Jetpack Compose, Media3/ExoPlayer). Code in
  `android/app/src/main/java/org/omarchy/music/` (UI in `ui/`). Version in `android/app/build.gradle.kts`
  (`versionCode` = major·10000 + minor·100 + patch, `versionName`).
- `tests/`: desktop unit tests. Run `cd tests && python3 -m unittest discover -s .` (`tests/app.py` loads
  the app as a module). `pyflakes omarchy-music` for mistakes. Android unit tests in `android/app/src/test`.
- `CHANGELOG.md`: one section per release, newest first; the release notes are copied from it.
- `.github/workflows/windows.yml`: builds `OmarchyMusic-Setup.exe` and attaches it to a `v*` release by itself.
- `.github/workflows/release.yml`: an automatic release flow for merged PRs into `main`. It is **not used**:
  the default branch is `radon-player`, and its Android signing secret (`ANDROID_KEYSTORE`) is broken.

## Making a release (only when the owner says to)

1. Bump `VERSION` (desktop) and `versionCode`/`versionName` (Android); add a CHANGELOG section.
2. Get the code onto the `radon-player` branch.
3. Build the Android APK. Since 1.3.0 every release has shipped the **test-key build**: package
   `org.omarchy.music.test`, app name "VarMusic Test", signer certificate SHA-256
   `ecb1d00a5af31235763dd087351edecf9237999628ad09002c481e1e152a76f9` (`ci/test.keystore.b64` on the
   `android-work` branch). Keep using it: a different key can't install over the owner's app.
   The `android-work` branch's "Android test build" workflow builds it and commits it as `ci/VarMusic-Test.apk`
   (also uploaded as the artifact `VarMusic-Test-apk`); `ci/result.txt` says BUILD OK or the errors.
4. Make the GitHub release: tag `vX.Y.Z` on `radon-player`, title "Radon Player X.Y.Z", notes from the
   CHANGELOG, and attach the APK **renamed to `OmarchyMusic.apk`** (the in-app updater looks for that name).
   The Windows installer attaches itself a few minutes later (windows.yml).

### The `android-work` branch (how builds were made from a sandbox without push access)

Patches `ci/N.patch.b64` (`git diff | gzip -9n | base64 -w0`) are applied in `sort -V` order on top of the
base commit, then the APK is built. If `ci/PUSH` exists (first line: a branch name; the rest: a commit
message), the patched tree is also committed to that branch: that's how releases reached `radon-player`.
**Delete `ci/PUSH` after a release**, or the next test build publishes by itself. With direct push access,
build however is simplest (a workflow on a branch, or `./gradlew assembleRelease` signed with the test key).

## Things that are easy to get wrong

- **Android playback between songs:** the queue holds one song in ExoPlayer at a time. Between songs the
  player is briefly ENDED; `Music.advancing` + `MusicService`'s `queueAware` player report BUFFERING then, so
  Media3 keeps the app a foreground service. Without it Android closed the app with the screen off.
- **Settings › Problems (Android):** `Crashes.kt` saves crashes and caught errors; the owner can copy them.
- **Desktop drawing:** draw with GTK snapshot nodes (`SnapPainter`, `append_color`, gradients, paths), not
  `append_cairo` over big areas: a 4K cairo surface every frame made the visualizer lag. Keep animated
  regions small (Now playing's bars are their own strip, `BackdropBars`).
- **Stats** (Most played, Artists, DJ, Wrapped) add up Last.fm scrobbles + Spotify import + Apple import
  (+ plays here only without Last.fm). One song across sources = `stats_key` / `Stats.statsKey` (title
  tidied, lead artist, punctuation dropped: K.Flay = KFlay). Imports are frozen; Last.fm brings new plays.
- **Your copy of a song:** `fm_song` / `Music.fmSong` match a played song to the owner's own file by title,
  artist and album (`best_copy`: same album first, then FLAC). Otherwise it plays a stream ("Opus 48 kHz").
- **New releases** only use the streaming services (Tidal, YouTube Music), never local files.
- Discovery playlists come in pairs (`discovery: {artist, role: discover|discovered}`); New releases makes one
  named "New releases". In Playlists they fold under one row.
