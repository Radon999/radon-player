# Radon Player

(Formerly VarMusic, and before that Omarchy Music, made by Xavier Reid: the history of both is
kept here.)

**All your music in one place, on your phone and your PC.**

Radon Player plays your own music files and the streaming services you already use, in one app
for **Android** and **Windows**. Songs play in FLAC (hi-res where it's available), with a full
equalizer and real sound-quality tools. It sorts every song into moods, has a personal DJ that
talks between sets, keeps your listening stats, and makes you a Wrapped any time you like.

Since 3.0 the phone and PC apps are **one app built from the same code** (Kotlin and Compose
Multiplatform), so every screen and feature matches. Pair them and the phone becomes the PC's
remote, and the two keep each other in sync.

## Download

Get the latest version from the [releases page](https://github.com/Radon999/radon-player/releases):

- **Android:** `OmarchyMusic.apk`. Open it on your phone to install (allow installing from your
  browser or files app when Android asks).
- **Windows:** `Radon-Player-x.y.z.msi`. Run it to install. Updates after that come from inside
  the app (Settings › Update).

## Highlights

- **Moods.** Every song is sorted into 12 moods (Happy, Chill, Sad, Hype…) from how it sounds and
  what its words say, optionally read by Claude with your own Anthropic key. Moods are written
  into your files, so they're never worked out twice, and power Mood lists, smart playlists, the
  DJ and your stats.
- **Your own DJ.** Sets built from your listening (on repeat, throwbacks, forgotten favorites,
  your mood, new artists like yours), introduced in a natural voice with facts about your
  listening.
- **Stats and Wrapped.** Last.fm plus imports from Spotify, Apple Music, Deezer, YouTube Music and
  Tidal, counted together. Wrapped for any week, month, year or all time, with cards to share.
- **Lossless first.** Connect Tidal and everything streams and downloads in FLAC. Spotify, Apple
  Music, Deezer and YouTube Music libraries and playlists come over too, playing from Tidal.
- **Sound.** An EQ per output (headphones, car, speaker), AutoEq headphone corrections, Boost,
  crossfeed, bit-perfect, and quality tools that are on from the start. Never Dolby Atmos.
- **Phone + PC.** Phone remote, automatic sync of stats, moods, downloads, tag changes and synced
  playlists, and pick up where you left off.
- **Tidy music.** A Tagger with online tag lookup, duplicate finder, FLAC upgrades, cover and
  lyrics finders, and fixers for split albums and messy genre names.
- **Looks.** Dozens of looks (30 neon ones), app themes, and 30 visualizer styles.

The full list is in [FEATURES.md](android/core/FEATURES.md), the same text as the app's
**Features** page. What each version changed is in [CHANGELOG.md](CHANGELOG.md).

## Building it yourself

The code is in `android/`: `shared/` and `core/common/` for both apps, `core/android/` and `app/`
for the phone, `core/desktop/` and `desktop/` for the PC.

```
cd android
tools/tidal-keys.sh                  # Tidal's app keys (needs Python with tidalapi)
./gradlew :app:assembleRelease       # the phone app
./gradlew :desktop:packageMsi        # the Windows installer (on Windows)
./gradlew :shared:test               # the tests
```

Forks are welcome: ideas and changes that fit may make it into the main release.

## License

MIT. See `LICENSE`.
