# Changelog

What's new in each update of Radon Player, the newest at the top, from 4.0 on. Everything the app does is in **Features**; what the versions before 4.0 changed is kept on GitHub (CHANGELOG.md).

## 4.0.5 · November 2026
- **Checking for an update always finishes:** Settings could stay on "checking for a newer version…" for good. The check waited its turn among the app's other background work (all of it busy during a scan or a sync) and had no time limit of its own. It now runs by itself, ends within about a minute whatever the network does, and has a second way to find the newest version when GitHub won't give its list. Downloading the update no longer waits for other work either.

## 4.0.4 · November 2026
- **A song you have is never downloaded twice:** if you already have a song with the same title and artist (whatever its album is tagged as), Download leaves it and says so: your copy is the one that plays. Before, a download whose tags you changed (a single moved into its album) could be taken for a song you didn't have, streamed instead of played from your file, and downloaded again as a second copy. "Keep both" (Settings, or when asked about a near match) still downloads it.
- **A download follows its file:** when a downloaded song's file has moved (after a tag change put it in another folder), the app finds your file of that song again and goes on playing it, instead of streaming it.

## 4.0.3 · November 2026
- **The visualizer works for streamed songs (computer):** it stood still for any song that was streaming rather than playing from a file on that computer (a hi-res stream, or a song you only have on the phone). It listens to the stream now.
- **Pick an artist's or an album's songs whole, in Search:** hold an artist or an album in the search results (right-click on a computer) and all its songs are picked, as songs are; while picking, a tap does the same and a tick shows it. Then play, queue, download, tag or delete them together. In your music it's every song you have of them; on a service, an album's songs and the songs an artist's page lists.
- **The right bit depth under Now playing (computer):** a 24-bit FLAC was shown as "32-bit" (the player decodes it into 32-bit numbers, and that was what the app showed). It shows the file's own depth now, and none for MP3 and AAC, which have no bit depth. The 32-bit FLACs tab was right all along.
- **A USB DAC is recognised as the output (Windows):** the app worked out Windows' default output from when a device was last picked by hand, so a DAC Windows chose as it was plugged in (a FiiO KA17) wasn't seen: the Equalizer showed another device and used its settings. The app now asks Windows itself. The music always went to the right device; only the app's idea of it was wrong.

## 4.0.2 · November 2026
- **A Q button for your queues:** at the top left of Now playing, where "From the album…" stood. Beside the other buttons there was only room for "F…". Q opens your queues just as those words did.

## 4.0.1 · November 2026
- **Moods match (Settings › Remote & sync):** a new switch under Tags match, for moods only, with its own main device. At each sync the other device's songs get the main one's moods, written into their files, with nothing scanned again. For when one device's moods were lost or cleared, or when you scan on one and tag on the other.
- **Tags match leaves moods alone:** it used to make its main device lead the moods too. Now the tags and the moods are apart: each follows the device you pick for it.
- **Moods only sync when you ask:** a sync used to carry moods both ways by itself. Now they go across only with Moods match on. Turn it on if you want your two devices' moods kept the same.
- **Tidal's sign-in page opens by itself (computer),** as its text said it did.
- **The music stops after four songs in a row that can't be played** (no connection, a service turning the app away), and says why, instead of skipping on through the whole queue.
- **Linux:** links open in your browser (sign-ins, the update's page, folders); a link that can't be opened is copied instead. With no tray, closing the window minimizes it instead of hiding it for good. Software drawing (Settings › General) for a window that flickers.

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
