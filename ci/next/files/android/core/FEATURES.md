# Radon Player

**All your music in one place, on your phone and your PC.**

Radon Player plays your own music files *and* the streaming services you already use, in one app. Songs play in FLAC (hi-res where it's available), with a full equalizer and real sound-quality tools. It also sorts every song into moods, has a personal DJ that talks between sets, keeps your listening stats, and makes you a Wrapped any time you like.

The Android app and the Windows app are **one app built from the same code**, so the screens, settings and features match. Pair them and your phone becomes a remote for the PC, and the two keep each other in sync.

**Works best with:** a Tidal subscription plus your own song files. Tidal is the only service that streams and downloads in FLAC. Spotify, Apple Music, Deezer and YouTube Music can be signed in to bring your playlists, library and listening history over; their songs then play from Tidal.

---

## Moods

Radon Player listens to every song you have and sorts it into moods, so you can play "something chill" or "something sad" from your own music without building playlists by hand.

### The 12 moods

Happy, Energetic, Party, Hype, Chill, Calm, Dreamy, Focus, Romantic, Sad, Dark and Intense. Each song gets a score for every mood and goes into each one that fits it (usually 1 to 4). Its best fit is its **main mood**. ☰ › **Moods** shows each mood as a coloured row of its songs; tap one to see them all.

### How a song's mood is worked out

- **The sound:** The app listens to the song on your device: how loud and energetic it is, its tempo, how bright or warm it sounds, how busy it is, and whether it leans major or minor. **Scan moods** hears a minute from the middle of each song. **Full scan** hears every song from start to end, including how its energy changes, so it's surer. Both show how far they've got and can be stopped and picked up later.
- **The words:** **Read moods from lyrics** reads each song's lyrics for what they're about. It reads whole lines, not single words: it knows "wash my blues away" is relief, not sadness, and understands "not", "used to… but now" and stories about someone else. Instrumentals use the sound alone.
- **AI reading (optional):** Paste your own Anthropic API key (Settings › Scanner › Lyrics & moods) and Claude reads the lyrics instead, catching irony, sayings and sad stories told cheerfully. It costs a little per song on your Anthropic account. The key stays on your device, never in backups or sync. **Test the key** checks it works.
- **Put together:** The sound counts more for Energetic, Party, Chill and Focus; the words count more for Sad, Romantic and Dark. A clearly upbeat song isn't made sad by one sad line. The song's genre and, with Last.fm, the tags people gave it help too.

### Fixing and checking moods

- **Fix a mood:** Long-press a song (right-click on the PC) › **Tagger…** › Tags › **Mood**, and tick every mood that fits. It's saved straight away.
- **It learns:** When you fix a song, songs that sound like it and the same artist's other songs move a little the same way.
- **Check these:** The Moods tab lists songs it isn't sure about (or whose sound and words disagree). Play each one, tick the right moods and Save, or tap **Looks right**.

### Moods stay with your songs

- **In your files:** **Write moods into the files** puts each song's moods in its file's MOOD tag (FLAC, MP3, WAV, M4A and more). A song that already has a MOOD tag is never scanned again, and moved or renamed files keep their moods. Only the mood tags change; titles, covers and every other tag stay as they are. On the phone this covers the songs the app downloaded; other files keep their moods in the app.
- **Synced and backed up:** Moods go to your other device at each sync, and into backups, so a scan done once (on the PC, say) never has to be done again.

### Where moods are used

- **Mood lists:** ☰ › Mood lists has a playlist for every mood. **Refresh** picks up new songs and says what changed.
- **Smart playlists** can filter by mood, the **DJ** plays mood sets, **Most played › Moods** shows how much you've played each mood (from all your history), and **Wrapped** shows your top moods.

---

## The DJ

Like Spotify's DJ, but built only from *your* listening. It plays short sets of songs and talks between them like a radio host.

- **Sets made for you:** What you've had on repeat, what you usually play at this time of day, favorites, throwbacks to a year you played something to death, songs you loved but haven't heard in months, your current mood, a genre you've been into lately, and (if you like) new songs and new artists like yours. Songs you keep skipping are left out.
- **Never repetitive:** A song the DJ plays can't come back for 20 sets: when its kind of set comes round again (the same mood, This time of day…), another song of that kind takes its place. A kind of set doesn't come back for about 15 sets, and the same goes for its fun facts. All of this is remembered after the app closes.
- **It talks:** With **Voice on**, the DJ speaks between sets, never over a song: what's coming up and why ("Let's take it back to 2020…"), and now and then a fact about your listening (hours on a song, when you first played it, your top mood this week, your most played artist this month). **Skip the talk** goes straight to the music.
- **The voice:** One natural female voice. On the PC, **Get the natural voice** downloads it once (about 400 MB) and it runs on your PC. The phone uses Google's natural voice. Unusual names are said the way they sound.
- **Ask for something:** Type or say an artist, a genre, a decade ("80s"), a mood ("sad songs") or a vibe ("workout"), and the DJ builds a set for it from your own songs first.
- **Play from:** Everything, all your songs, or one playlist, folder or album. The **DJ** button on My music and playlists starts it there.
- **Switch it up** starts a different kind of set; **⏭ set** on Now playing skips to the next set.
- **Your copy first:** Every song the DJ plays uses your own file when you have one.
- **In the queue:** While the DJ plays, Up next and Now playing's queue show what it said last, the set's category (on each song too) and any stat it gave, as on the DJ tab. Other queues don't.

---

## Stats & Wrapped

### Where your plays come from

Your stats add up every play from everywhere you've listened:

- **Last.fm:** Connect it and everything you play is scrobbled; all your scrobbles from all time are the base of your stats. Without Last.fm, the app counts its own plays.
- **Imports:** Spotify, Apple Music, Deezer, YouTube Music and Tidal histories (the data files each service lets you download). The steps are shown next to each Import button. Importing the same file twice never counts a play twice.
- **Don't double up:** Don't import a service's history if you already scrobbled it to Last.fm. Each import has a **Clear** button if you need to start over.

### Your stats

- **Most played:** Top songs, albums, artists and moods for this week, month, year or all time, by plays or by listening time, with charts of when you listen.
- **Repeat, Milestones and Skips:** What you've had on repeat, songs and artists that passed 100, 250, 500… plays, and what you skip most or always hear to the end.
- **Tidy stats:** Merge an artist written two ways, or a song counted under two names, and pick whose plays a duet counts for.
- **Share card and Collage:** Your top 15 on one card, or your top covers as a grid (5×5 up to 25×25), to save or share.

### Wrapped

Your week, month, year or all time in music, as full-screen slides. Tap right to go on, left to go back.

- **What's in it:** Top songs, artists, albums, genres, moods, singles and EPs (top 15 each), minutes listened, month by month, when you listen, your best day and streaks, new discoveries, most and never skipped, the playlists and devices you listened on, and a fun listener type (night owl or early bird, shuffler or in order, mainstream or underground…). It ends with a summary card.
- **Your covers:** Every song shows the cover from your own copy first.
- **Styles:** Classic, Neon, Minimal, Paper, Mono and more.
- **Save and share:** Every slide, the summary card, collages, share cards and lyrics cards can be saved as a picture (phone: Pictures › Radon Player; PC: Downloads › Radon Player) or shared.
- **What Wrapped counts:** A button explains every number and where it came from.

---

## Playing music

- **Now playing:** A big cover on its own colours, with ☆ to favorite and ⋯ for options. Swipe the cover for the next or previous song, swipe up for the queue, tap for lyrics, double-tap to pause.
- **Quality badge:** Shows what you're hearing ("FLAC 24-bit/96 kHz", a stream's quality, or "Offline"). On a stream, tap **⤓ FLAC** to save it.
- **Queue:** What you added first, then the rest of the album, playlist or folder you started. Drag to reorder. **Queues** (Settings) can keep a separate queue for each album, playlist and folder, right where you left it.
- **Shuffle, Repeat and Autoplay:** **Smart shuffle** favors songs you love but haven't heard in a while. Autoplay keeps similar songs coming when the queue ends.
- **Per song:** **Volume…** (−12 to +12 dB, for one output or all), **Custom EQ…** (its own EQ on this output) and **Speed…** (half to double, with or without pitch). Now playing marks a song that has one.
- **Mono:** The headphone button at the top puts both sides in each ear, for one earbud in.
- **Casting:** Send music to a Chromecast, Google speaker or TV on your network.
- **Your copy first:** Wherever a song shows up (playlists, search, artist pages, radios, stats, the DJ), if you have it as a file or download, your copy plays with its cover and tags. A live or remix version is never swapped for your studio one.

---

## Your library

Everything is in the ☰ menu. Every page can be a list, a grid or a compact grid, has its own search and **Sort** menu, and remembers your choice.

- **My music:** Every song in your folders plus your downloads, with Play, Shuffle and DJ.
- **Albums, EPs and Singles:** Your releases as cover grids, sorted by their album tags ("- EP" and "- Single" as taggers name them). Small albums sit under **Low song count albums**. **Missing from this album** shows the songs of an album you don't have, with a button to download them.
- **Artists:** Ranked by all your plays. An artist's page has your songs, **Change picture…**, a **Discovery playlist**, **Create artist playlist** and **Merge into…**. Band names like "Of Monsters and Men" stay whole.
- **Genres and Folders:** Your songs by genre tag, or by the folders they're in.
- **Playlists:** Make, sort, fold into folders, export and import (.m3u8).
- **Smart playlists:** Fill themselves by rules: genre, mood, artist, format, plays, last played, favorites, skips, length, folder and more. Ready-made ones (Never played, Forgotten favorites, Hi-res, Chill evening, Workout…) are one tap away.
- **More lists:** Favorites, Not favorited, Recently played, Downloads, 24-bit and 32-bit FLACs, Skips, and Pinned items at the top of the menu.
- **Format on every song:** Each of your songs shows its file type and bit depth ("FLAC 24", "MP3"); downloads show a ⤓.
- **Select:** Long-press to pick songs (or albums, artists, playlists), then Play, Queue, Add to playlist, Favorite, Download, Tagger, Volume, Speed, Upgrade, Share or Delete.
- **Search:** Ignores symbols and punctuation ("acdc" finds AC/DC), and every search bar has a microphone for voice typing.
- **Covers everywhere:** Songs without a picture get one looked up online (or the artist's picture), and covers are kept on your device once loaded.
- **Moved files follow:** Move or rename a music folder and your playlists, queue and favorites find the songs where they are now.
- **Nothing deleted without asking:** Anything that removes something asks "Are you sure?" first.

---

## Streaming & downloads

- **Search:** Tidal, YouTube Music, Deezer, Apple Music, Spotify or your own music, from the menu by the search bar. Paste a link from another app and it opens.
- **Tidal:** Streams in FLAC (hi-res where Tidal has it). Its favorites, playlists and radios work as a library too.
- **SoundCloud (streaming only):** Sign in under Settings › Accounts with your own SoundCloud app's Client ID and secret to search, play your likes and playlists, and open artists and radio. SoundCloud is its own section: its songs can't be downloaded, added to your playlists or favorites (a star likes it on SoundCloud instead), or mixed into a queue with other music. Spotify, Deezer, YouTube, Tidal and Apple Music can all be mixed with your offline music.
- **Spotify, Apple Music, Deezer, YouTube Music:** Sign in to browse your library and playlists and bring them over. Their songs play from Tidal in FLAC, or YouTube when Tidal doesn't have one. **Import all into my playlists** copies their playlists in. Settings › Accounts explains how to switch to Tidal without losing anything.
- **Stream and download quality:** FLAC 24-bit, FLAC 16-bit or 320 kbps, set separately.
- **Downloads:** Any song from any service downloads from Tidal (YouTube when Tidal doesn't have it), with full tags, cover and genre. It's taken from the artist's own album first (deluxe edition first), then EP, then single, and saved in **Music › Artists**, in the artist's own folder (Artists / Sody / Let Go - Single). Settings › Downloads can pick another folder. Download whole playlists too; songs you already have are skipped.
- **Copies:** When you download a song you already have: Ask, Keep both or Replace.
- **Discovery playlists:** For any artist, every song of theirs you don't have yet. Songs you hear to the end go to a "discovered" list to download or let go.
- **New releases:** **Refresh…** checks every artist in your music for new albums, EPs and singles from the last week, month or year.

---

## Lyrics

- **Synced or Genius:** Synced lyrics light up line by line (tap a line to jump there); Genius shows the lyrics as written, with annotations to tap.
- **Search again:** If nothing's found, search by a different title or artist. Found lyrics are kept for offline.
- **Lyrics card:** Tick up to 15 lines for a picture with the cover, to save or share.
- **Lyrics in your files:** Lyrics are written into the song's file once it's played. The **Lyrics scanner** (Moods tab) finds and writes lyrics for every song at once, and lists the ones it couldn't find so you can type them, mark them instrumental or search again.

---

## Sound

### The Equalizer

- **One EQ per output:** The phone speaker, wired headphones, each Bluetooth device, a USB DAC and your car each have their own EQ, switched in automatically.
- **Bands:** 3, 5, 10, 15 or 31 bands, ±12 dB, plus Bass and Treble.
- **Presets:** Flat, Bass boost, Warm, Vocal, Bright, V-shape, and tunings for the Chevy Colorado 2023+ (standard and Bose) and several headphones. Save, import and export your own.
- **Headphones correction (Auto EQ):** Type your headphones' name or tap **Detect** to load their correction from AutoEq's database, or import one.
- **Boost:** Up to 12 dB louder for quiet headphones; a red line shows how far it stays clean.
- **Crossfeed, Crossfade and Smart transitions:** Crossfeed softens hard-panned old records; crossfade blends songs (1 to 12 seconds) but never inside an album that flows.

### Sound quality

On from the start (each can be turned off in Settings › Sound quality):

- **Clip guard:** Catches any peak that would crackle.
- **Smooth distortion on loud songs:** Rounds off the harsh, clipped peaks of over-loud masters.
- **Dither:** Keeps quiet fades smooth instead of grainy.
- **High-quality resampling:** Cleaner highs when a song's sample rate doesn't match your device's.
- **Loudness compensation:** Lifts bass and treble back at low volume, as your ears lose them first.
- **Detail:** Brings out background sounds and small details.

Also: **Bit-perfect** sends songs to a USB DAC (phone, Android 14+) or the PC's sound card untouched, and **Ignore Bluetooth devices** keeps the music on your headphones when the car connects. Never Dolby Atmos.

---

## Looks & visualizer

- **Looks:** Light, Dark, **Now playing** (every screen takes the playing song's colours), 35 more themes and 30 dark **neon** looks. Settings › Personalization, or the palette button on Now playing to see changes live.
- **Layouts:** 15 ways to lay out the app (Settings › Personalization › Layout): the ☰ menu, a tab bar, a side rail or tabs along the top; the menu on the left or right; the mini player floating, as a bar, a pill or at the top; and six ways to arrange Now playing (classic, centred, split, info on top, full bleed, headline). Every feature can be reached from all of them.
- **Make your own:** **New look** (your own background, card, text and accent colours, a gradient, a neon glow, stars), **New layout** (pick and order the slots, the bar and the mini player) and **New visualizer** (a style from any of the 30, mirrored, flipped, spun or echoed, or your own colour blend). Each shows live while you make it, and yours can be changed or deleted any time.
- **Now playing in your look:** The look's background, gradient and stars show behind Now playing. The song name, buttons, seek bar, volume, tools, visualizer and lyrics take the look's colour, made brighter or darker when needed so they always stand out from the background. The Now playing look uses the cover's colours instead.
- **Themes:** Change how the app is drawn (Rounded, Sharp, Compact, Glass, Retro, Minimal and more), with any look.
- **Visualizer:** 30 styles (bars, waves, circles, starfield, galaxy, fireworks, tunnel…) and dozens of colour blends. Any style can run on Now playing, round ones inside the cover. It only listens to the app's own sound, and on the phone remote it moves with the PC's music.

---

## Tags & tidying tools

Every scan is in **Settings › Scanner**. Run one when something looks wrong, then forget about it. On the PC they work on all your music; on the phone, on the songs the app downloaded (Android doesn't let apps change other apps' files).

- **Tagger…:** Everything about the songs you picked in one place: **Tags** (with Mood and **Find tags online**, which shows every change before applying), **Cover**, **Info**, **Lyrics**, and **Tab** to move a release between Albums, EPs and Singles. A Play button lets you listen while you work. Changes to the song that's playing are saved the moment it ends. Change a download's artist or album and its file moves to match: into Artists / *the artist* / *the album* (folders made as needed, EPs and singles in their own), and the folder it left goes once no music is left in it. Your own files move the same way when they were in the folder their old tags gave them; folders you arranged yourself (like unreleased songs) stay as they are.
- **Fix genre names:** Puts "Hip-Hop/Rap" and "Hip-Hop-Rap" under one name.
- **Duplicates:** Finds songs you have twice and lets you keep the best copy.
- **Upgrade to FLAC:** Swaps MP3s (and 16-bit files, where Tidal has 24-bit) for Tidal's FLAC; old files go to the trash.
- **Songs without covers**, **Identify by sound** and **Similar artists** fill in what's missing.
- **PC only:** Fix albums and artists (split albums, deluxe editions, singles already on an album), Empty folders, Live in song names, Soundtrack notes in names, and Folders to leave alone.
- **Never show:** Hide something from a scan for good, with Undo and Show them again.

---

## Phone + PC together

- **Phone remote:** On the PC, turn on **Phone remote** (Settings › Play on) for a pairing code; enter it on the phone. The phone then plays, queues, skips and sets the volume on the PC, with the visualizer moving to the PC's music.
- **Sync:** Paired devices sync by themselves whenever they connect (and **Sync now** any time): plays, skips, listening time, favorites, pins, moods, EQs and settings, nothing counted twice. **Downloads** made on one are downloaded on the other, and **tag changes** made on one are made on the other. **Synced playlists (⇄)** stay exactly the same on both; other playlists stay where they were made. **Undo last sync** puts things back.
- **Tags & covers match:** Turn it on in Settings › Sync and pick the main device (the PC or the phone). At each sync, the songs that are on both are made the same on the other device as on the main one: title, artist, album, album artist, genre, year, track and album art. The main device always sends, the other always takes; tag edits made on either still go to both. Songs are matched by ISRC, title and artist, or file name, when the lengths agree. On the phone only the songs the app downloaded can be changed.
- **Continue where you left off:** Stop on one and the other offers the same song at the same spot.
- **Back up and restore:** One file with your playlists, favorites, plays, moods, settings and EQs, which restores on either device.

---

## Phone only

- **Android Auto:** Browse and play on the car's screen with covers, Favorite and Shuffle buttons, and the car gets its own EQ.
- **Lock screen player, notification and home screen widget,** all in the song's colours.
- **Mini player:** Picture-in-picture over other apps.
- **Auto mono:** Turns mono on while your chosen earbuds are connected.

## PC only

- **System tray, media keys and Windows' media controls;** **Start with Windows**; and a **Mini player** window that stays on top.
- **Discord:** Shows what you're playing on your profile, with the album cover, and clears it when you pause.
- **Keyboard shortcuts:** Space plays/pauses, ← and → skip, W opens Wrapped, E the Equalizer, / search, and more. Right-click anything for its menu.
- **Your own files:** The Tagger, covers and lyrics write into your own files (FLAC, MP3, M4A, WAV and more); Delete sends files to the Recycle Bin.

---

## Help

- **Updates:** Settings shows your version and checks for a new one; **Update** downloads and installs it.
- **Changelog and Features:** In the ☰ menu: what each update changed, and this page.
- **Problems:** Settings shows anything that went wrong recently, with a **Copy** button to send it on.
