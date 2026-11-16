# Changelog

What's new in each update of Radon Player, the newest at the top, from 4.0 on. Everything the app does is in **Features**; what the versions before 4.0 changed is kept on GitHub (CHANGELOG.md).

## 4.0.0 · November 2026
- **Rebuilt from the inside:** the app was taken apart and put back together as separate parts (playback, the DJ, your library, downloads, the stats, the sound), each with its own tests. It does what it did, more smoothly and more reliably, and your settings, stats, playlists and presets carry over.
- **Linux:** Radon Player now runs on Linux as well as Windows and Android, as a .deb package that brings mpv and ffmpeg with it. It has the tray, Start with Linux, updates from inside the app, and the keyboard's and a headset's media keys with the desktop's own "now playing" controls.
- **Music no longer stops at the end of a song (phone):** with the DJ on, a song heard to its end could leave a long silence before the next. The next song now comes at once.
- **Bit-perfect keeps every bit:** a 24-bit song always reaches a USB DAC as 24-bit (some were brought down to 16-bit first).
- **The DJ's voice is the same everywhere:** one natural voice on the phone and the computer, fetched once from DJ › Voice settings. She no longer says things a voice can't say well (dots for pauses, signs), and places in a row are said in words.
- **Mood sets of their own:** Happy, Chill, Calm, Sad and Dark each have their own DJ set; only Energetic, Party and Hype share one.
- **Moods read better:** the sound is judged on a scale that fits real records, the words count for more, and a song gets four moods at most. Reset moods (Settings) and Scan moods put the new ones in your files.
- **The DJ's facts take turns:** what it tells you goes round by subject, and it knows more about your collection.
- **Smart shuffle knows more:** "haven't heard in a while" now goes by your plays everywhere (Last.fm and your imports), not only the plays in the app.
- **Download from the notification (phone):** with the Shuffle button switched off in Settings, a streamed song you don't have shows a Download button in the notification and on the lock screen.
- **Refresh in every tab,** an Unreleased tab, covers read from a song's folder, singles and EPs sorted by their tag, and Tidal asked again before YouTube is used.
- **Asked for at the start (phone):** everything the app needs is asked for as it starts, so the visualizer and the rest work the first time.
