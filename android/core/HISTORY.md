# Radon Player Next: what was done while building it

The notes kept before the app had its own Changelog tab (they're not shown in the app). From now on, each update's notes go in CHANGELOG.md, newest first.

## Changelog

### Radon Player Next (one codebase)

The phone app and the PC app are now **one app**. Every screen (My music, Now playing, Settings, the Equalizer, Stats, Wrapped, the DJ, lyrics and the tagging tools) is built from the same shared code, so features arrive on both at once and work the same way on both.

- The PC gets the phone's full set of screens, with its own player engine carrying the same Equalizer, Boost, Detail, Tilt, Balance, crossfeed, mono, clip guard, loudness compensation and crossfade. It also gets bit-perfect output, high-quality resampling, dither, the levelling target, and a separate EQ for each output.
- The PC keeps its own extras: the tray, media keys, Start with Windows, the mini player window, Discord, right-click menus, the visualizer and Phone remote.
- Both apps now have Loudness check and Identify by sound (the phone hears songs with its own decoders and makes the same AcoustID fingerprint the PC does), Fix albums and artists, Folders to leave alone, Live in song names, Copy the names, the Lyrics mode setting, and the option to keep the selection open after an option.
- On the PC, Spotify and Last.fm sign-ins finish by themselves, with nothing to copy and paste.
- On the phone, Back on Now playing opens the ☰ menu; Back again (with the menu open) leaves the app.
- The Equalizer can **Collect all sources** of a correction (every AutoEq reviewer's), **Export** a correction, and **Save**, **Import**, **Export** and delete Sound presets of your own; new headphones get every correction and a Sound preset of their own by themselves. Backups note where songs' volumes and Custom EQs were made, and bring them back only onto that kind of device.
- Fixed on the phone: 16-bit FLACs playing unbearably loud and distorted. The phone's FLAC decoder was asked for 32-bit float samples, which came out wrong; it now gives plain 16-bit samples like the MP3 decoder (float is only asked for with bit-perfect to a USB DAC).
- Fixed: some quiet songs (game soundtracks with ReplayGain tags) playing ridiculously loud and distorted. ReplayGain could raise a song by up to +12 dB and the limiter then squashed it; now a quiet song is only raised until its loudest moment reaches full scale (by its peak tag), or +6 dB at most when it has none.
- New: **Custom EQ…** for a song or several picked songs, kept for the output you set it on, with **C EQ** in Now playing (red while in effect). Songs' volumes and Custom EQs are in your backups, and restore only on the same kind of device (phone to phone, PC to PC).
- **Volume…** now saves a song's volume for the output you're on (headphones, car, speaker…) or for every output, with a switch for all of them in Settings › Sound quality and a red speaker-with-! in Now playing while one is in effect.
- The Equalizer's **Sound** presets now include one for each of your devices, for every kind of music: **Xenns Mangird Top Pro**, **Sony WH-1000XM6 · Bluetooth**, **Sony WH-1000XM6 · cable**, **Sony WF-1000XM6 earbuds** and **Galaxy S26 Ultra speaker**. Each is a gentle shape on top of that device's Headphone correction.
- Fixed on the phone: hi-res songs (24-bit FLACs, Tidal downloads) not playing, with “AUDIO_TRACK_INIT_FAILED” or a “MediaCodecAudioRenderer error” on the phone and in the car. They went to Android as 32-bit float, which the S26 Ultra's outputs refused; now they're brought down to 16-bit (with dither) and go through the EQ and the rest like every other song. Bit-perfect to a USB DAC still sends every bit. If the sound output ever refuses a song again, it's played again straight away the plain way, and the details go to Settings › Problems.
- The Chevy shows up in the Equalizer whenever Android Auto is on, over USB or wireless (it used to be missed both ways), and is always in the output list to set up any time.
- Fixed on the phone: the app closing as soon as it opened. And if it ever closes while starting again, the next start shows what went wrong, with **Share**, **Copy** and **Try again**.
- Backups move between devices: a phone backup restores on the PC and a PC backup on the phone, with your songs matched to the same songs on the new device. Backups no longer carry the PC's YouTube Music and Apple Music sign-ins.
- The PC gets the old app's keyboard shortcuts back, and its tag editing, Tagger, covers and lyrics work on your own FLAC files again.
- **Identify by sound…** is now in a single song's menu too.
- The PC installer brings its own mpv and ffmpeg, and Windows' media controls (the flyout, widgets, wallpapers) now see what's playing.
- The PC is one app at a time again, with the old app's commands (next, play-pause, show…) and song files opened with it.
- Albums, EPs and Singles never show the same release twice, and Albums splits into **Albums** and **Low song count albums**.
- Lyrics cards take up to 15 lines. Duplicates has **Not a duplicate**, remembered for next time. Duplicates in Most played leaves out marked versions (Stripped, Remix, Live…).
- Single scan has **Change all**, **Leave all alone** and **Only albums on this phone/PC**, and finds a song's EP even when the EP has the song's name. The Tagger gets **Auto collect**.
- Select works on Genres, Folders, Artists and Playlists too (Options for all their songs); picked playlists can be deleted, renamed, opened or favorited together.
- On the PC you can sign in to YouTube Music and Apple Music, and cast to Chromecasts, Google speakers and TVs.
- Everything else from the old PC app is back: its command-line commands, the rest of its keyboard shortcuts (Alt+1…9, S, A, Ctrl+A, Ctrl/Shift+click, Wrapped's keys), Copy for pictures, the update that installs itself, the mini player's queue and the DJ's **Choose a folder…**. The installed PC app now has Tidal's keys built in, and restoring a backup closes it as it says.
- Fixed on the phone: a short loud blip at the start of each crossfade.
- Settings gets **Albums, EPs and singles**, a scan that names each release as what it really is. Wired Android Auto is its own output, **Chevrolet · USB**, starting on the Colorado tuning. A Galaxy S26 Ultra speaker correction joins the headphones list. Bit-perfect on the phone now only takes over while a USB DAC is the output.
- The phone gets **High-quality resampling**, and the Equalizer shows every Sound quality option too.
- New in Settings: **Soundtrack notes in names** and **Songs without covers**. Duplicates gets **Keep / Remove** for each copy. An artist's page is a grid with sorting (Most played first). On the PC, the Tagger can rename the file with the title.
- On the PC, **Upgrade to FLAC…** works on your own songs again, and the mini player has a seek bar and shows what's up next.
- On the PC, **Delete…** works on your own songs again (they go to the Recycle Bin), and downloads can go to any folder.
- Fixed on Windows: the window could stay hidden at start while the app froze waiting on the player, and settings weren't being saved.
- The phone keeps its own extras: Android Auto, the lock screen player, the widget, the mini player, notifications and Auto mono.
