# Radon Player: notes for Claude

Radon Player is a music player for **Windows** and **Android**. It started as Omarchy Music / VarMusic by
the owner's brother; Radon999 carries it on here. `docs/history.md` has the
story of recent versions (what broke, why, how it was fixed, what's still open): read it before big changes.

**The rebuild (this branch, `rebuild`):** the owner decided (November 2026) that the app is for **Android, Windows
and Linux**, stays Kotlin, and is restructured into separate services with one small native audio module.
`docs/rebuild/RADON_REBUILD_PLAN.md` is the plan (its phases, rules and open decisions) and
`docs/rebuild/DJ_LINES_CATALOG.md` every DJ line. Every push here is built and tested by
`.github/workflows/rebuild.yml` (result: the `rebuild-ci` branch, `result-linux.txt`); nothing from here reaches
`radon-player` or a release until the owner says "publish it".

## How the owner wants to work

- **Every change goes to both platforms**, with the same UI and features. If something can't be the same
  on one of them, do the closest thing and **say plainly what the compromise is**.
- **Publish only when asked** ("publish it"). Releases, pushes to the default branch and anything public
  wait for that. Ask first otherwise.
- Never force-push. Never delete the owner's files or data. Don't type passwords or handle login codes or
  signing keys: the owner does those.
- The owner is not a programmer: explain results in plain words, short, with what to test.
- Before saying something is fixed, test it (unit tests, and screenshots of the desktop app where it's visual).

## Layout (3.0 on: one Kotlin codebase)

- `android/`: both apps. `shared/` and `core/common/` are shared (UI in `core/common/.../ui/`), `core/android/` and
  `app/` the phone, `core/desktop/` and `desktop/` the PC (Compose Desktop, mpv and ffmpeg bundled in the .msi).
- Version: `android/app/build.gradle.kts` (`versionCode` = major·10000 + minor·100 + patch, `versionName`) and
  `appVersion` in `android/desktop/build.gradle.kts`.
- `android/core/FEATURES.md` (the Features tab) and `android/core/CHANGELOG.md` (the Changelog tab): run
  `python3 android/core/gen-guide.py` after editing them. Every feature goes in FEATURES.md, in its section.
- `CHANGELOG.md` (here): one section per release, newest first; the release notes are copied from it.
- The Python desktop app (`omarchy-music`, 1.x–2.x) was removed in 3.0; it's in the git history and old releases.

## Making a release (only when the owner says to)

1. Bump the versions; add a CHANGELOG section (here and in the app's Changelog tab).
2. On `android-work`, `ci/next/rN.patch.b64` patches are built by `.github/workflows/next.yml`. With
   `ci/next/PUSH` (first line: the branch, `radon-player`) the built tree is committed to that branch; with
   `ci/next/RELEASE` (first line: the tag, the rest: the notes) a GitHub release is made with `RadonPlayer-Next.apk` and the PC's `.msi`.
   **The phone app is always Radon Player Next (`org.omarchy.music.next`), releases included:** the owner's phone runs it,
   and an update must always install over it (same name, same package, same key) so settings and stats are kept. Never
   release it under another name or package (3.2.18 went out as `org.omarchy.music`, "Radon Player", and couldn't update). **Delete `ci/next/PUSH` and `ci/next/RELEASE` after a release.**
3. The APK is signed with the **test key** (`ci/test.keystore.b64`, certificate SHA-256
   `ecb1d00a5af31235763dd087351edecf9237999628ad09002c481e1e152a76f9`), as every release since 1.3.0: a
   different key can't install over the owner's app.

## Things that are easy to get wrong

- **Android playback between songs:** the queue holds one song in ExoPlayer at a time. Between songs the
  player is briefly ENDED; `Music.advancing` + `MusicService`'s `queueAware` player report BUFFERING then, so
  Media3 keeps the app a foreground service. Without it Android closed the app with the screen off.
- **Settings › Problems (Android):** `Crashes.kt` saves crashes and caught errors; the owner can copy them.
- **Stats** (Most played, Artists, DJ, Wrapped) add up Last.fm scrobbles + Spotify import + Apple import
  (+ plays here only without Last.fm). One song across sources = `stats_key` / `Stats.statsKey` (title
  tidied, lead artist, punctuation dropped: K.Flay = KFlay). Imports are frozen; Last.fm brings new plays.
- **Your copy of a song:** `fm_song` / `Music.fmSong` match a played song to the owner's own file by title,
  artist and album (`best_copy`: same album first, then FLAC). Otherwise it plays a stream ("Opus 48 kHz").
- **New releases** only use the streaming services (Tidal, YouTube Music), never local files.
- Discovery playlists come in pairs (`discovery: {artist, role: discover|discovered}`); New releases makes one
  named "New releases". In Playlists they fold under one row.
