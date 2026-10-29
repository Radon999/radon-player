# Changelog

Radon Player started as **Omarchy Music** (later called VarMusic), made by Xavier Reid (Varixxl).
Radon999 forked it in 1.1.x and carries it on here as Radon Player. The full history of both is kept
in this repository, so `git log` shows where every feature came from.

## 2.1.2: Boost (Windows and Android)
- Equalizer › Boost: a fader from normal (the bottom) up to 12 dB louder, for headphones that are too quiet. It's on everything you play, not one song, and each output keeps its own (the car stays at normal). A red line marks how far it goes cleanly: the EQ's own pre-cut back, plus a couple of dB the limiter does without being heard. Above the line the limiter squashes the peaks to keep them from clipping, and the fader turns red

## 2.1.1: Car EQ (Windows and Android)
- EQ presets for the Chevy Colorado (2023 and later), the standard system and Bose: tuned for every kind of music, not one genre (the cab's boom around 250 Hz down, the dash's glare softened, air added back). Picked on the car's Bluetooth, the phone remembers it for the car

## 2.1.0: Sound (Windows and Android)
Every new setting is off (or neutral) until you change it. Never Dolby Atmos.
- Clip guard (Equalizer on the PC, Settings › Sound on the phone): a limiter just under full scale catches a peak the EQ, ReplayGain or a song's volume would push over, instead of letting it crackle
- Android: the EQ, song volume, ReplayGain, crossfeed and mono now work on hi-res (32-bit float) playback too; before, float output skipped them
- Android: Dither: 16-bit music through the EQ is rounded with TPDF dither, so quiet fades stay smooth
- Loudness compensation: turned down, bass and treble are lifted back as far as the volume is down (not at all at full volume)
- Balance: one side a little quieter, for IEMs or an ear that hears less
- Tilt: the whole sound leant warmer or brighter around 1 kHz; only ever turns down, so it can't clip
- Crossfeed strength: Light, Medium or Strong
- Levelling target (ReplayGain): Quiet −23, Standard −18, Louder −16 or Loud −14 LUFS; louder than Standard is limited by Clip guard (without it, songs are never made louder than they are)
- Auto EQ: Import… an EQ file: AutoEq's ParametricEQ.txt, a Squiglink or Equalizer APO export, or Wavelet's GraphicEQ
- Built-in corrections for the Xenns Mangird Top Pro (AutoEq, Super Review's and Jaytiss's measurements)
- Built-in corrections for the Sony WH-1000XM6 headphones (Bluetooth, and on the cable: AutoEq, Super Review) and the WF-1000XM6 earbuds (a gentle approximation from reviews until a full measurement is out)
- Detail: background sounds, bass lines and the little things brought forward: the stereo a touch wider, presence and air lifted a little, the level lowered to match so nothing clips
- Mono button: red while mono is on, the cover's colour while it's off; with Auto mono on and your earbuds connected it stays on (the button says so). The phone's Up next shows MONO while it's on
- Android: mono, the EQ and the rest now work on hi-res songs too (2.0.1's Auto mono showed "mono is on" but those songs stayed stereo)
- Windows: High-quality resampling: a longer, sharper filter when a song's rate has to change for your output

## 2.0.1: Mono (Windows and Android)
- Mono: a Mono button on Now playing (the phone: the headphones button at the top; the PC: Mono beside Cast) mixes both sides into each ear, for listening with one earbud in. Lit while it's on, kept between starts; not in bit-perfect
- Android: Settings › Auto mono (a switch, off until you turn it on): mono on its own while the earbuds you pick are connected, off again when they go. Android can't tell apps whether one earbud or both are in (Sony's and most others keep that to their own app), so it goes by the earbuds being connected; the Mono button still works by hand

## 2.0.0: The big one (Windows and Android)
Everything new is a switch, and off until you turn it on.

Your library
- Windows: Settings › Library › Fix albums and artists: finds songs split off by a featured artist, regular editions next to a deluxe (or extended, or anniversary) one, and singles that are on one of your albums. It opens in a borderless window filling the screen (Esc closes it): a list of every change, each one Change or Leave alone (or Other artist, with a dropdown: the album's other credited artist, e.g. Key Glock instead of Young Dolph, or "Choose your own…" to type any artist, with your local artists suggested as you type; picked for one song, it's picked for the whole album). Names with a comma in them ("Tyler, The Creator") are no longer cut down to the first part; the tags are written right away, then a second list offers to move the files into the right Artist/Album folder (nothing is ever overwritten). Track numbers are checked against the album's real tracklist
- Fix albums and artists also finds an album credited to two artists ("Big Wild & Surfaces", "Bas & J Cole", "hard life & BENEE") and puts it under the one of them with songs of their own (Surfaces, J Cole, BENEE), or the one they share (Berner); K.Flay and KFlay become one. Where it can't tell (Nas & 21 Savage), the pair is listed with Leave alone ticked, for you to pick with Change or Other artist
- Windows: Settings › Library › Clean up empty folders: folders with no music left (only covers, lyrics and the like), with their size and what's in them, in a borderless window filling the screen (Esc closes it), each one Delete or Leave alone (they go to the Recycle Bin)
- Albums: the songs an album has that you haven't got show dimmed at the very bottom, with Download missing tracks… (each one Download or Leave alone)
- Identify by sound… (Windows, song options): AcoustID names a song from how it sounds
- Loudness check (Windows): finds songs that clip or are much quieter than the rest

Most played and Wrapped
- An album's editions count as one (under the deluxe name), featured-artist splits count as the artist (and are named as them: "Quinn XCII & Jeremy Zucker" shows as Quinn XCII, while a real duo like Simon & Garfunkel stays one), and singles count toward their album; Collage, Wrapped and Artists use the same numbers
- Albums and Artists show three lines: the name, the artist and the plays
- Songs time, Albums time and Artists time: how long you listened (and when: by hour of the day and day of the week), then your songs, albums or artists by listening time, as a grid with their covers and each one's time showing
- Milestones: a song or artist reaching 100, 250, 500, 1,000… plays, dated by the play that got it there: the last month's in Most played, and a slide in Wrapped for the year, month, week or all time
- Share card…: your top five (or your listening time) as a picture for a story or a chat
- Merge into… (an artist's page, or right-click an artist in Artists): the artist is another one really (cavetown offline is Cavetown): their songs get that artist in the files' tags (on Android, the songs the app downloaded), and their plays count as that artist's from then on, each song's too (Candle's plays as cavetown offline are Candle's as Cavetown)
- Similar artists (Settings › Last.fm & your stats › Scan…): artists that look like another of yours (cavetown offline and Cavetown, Bad Sons and Bad Suns, Forrest Frank and Forrest) by a name inside the other's, a near-same spelling, or a word that names something plus a song of the same name, never a common word ("the", "young", "lil"). Each one Merge, the other way, or Keep apart (not asked again); ticked to Merge where they have songs of the same name
- Folders to leave alone (Settings, next to Fix albums and artists; PC): pick folders Fix albums and artists never touches. Their songs aren't retagged or moved, and no songs are moved into them. The scan says how many it skipped
- Fix albums and artists, more: singles of a song on an album you have (Deezer says which), even when you don't have that song on it, onto the album, its deluxe or extended edition when you have that ("Songs" onto Persona (Extended)); remixes onto the album their song is on, after its songs; albums in parts ("…: Pt. 1", "…: Pt. 2") all onto one album with no part in its name ("The Original Motion Picture Soundtrack"), track numbers carrying on from one part to the next
- Fix albums and artists: rows that change an album have Other album (a dropdown of the artist's albums, and Choose your own… with your albums suggested as you type) in place of Other artist
- Fix albums and artists: albums of an artist's with the same cover (a single with its album's art, a deluxe beside the album, an album's parts) onto one of them: the album over a single, an edition over the plain one. Blank or grey covers are never matched
- Albums (Genres, Folders): Select, then tap albums, then Options for all their songs (Tagger, Cover, a playlist…); right-click (long-press) an album for Options… or Select
- A cover changed on any song goes on every song of its album, songs with a featured artist too; with songs of several albums picked, on every song of each
- Tagger: a save that failed ("not a Frame instance" on some files) can be saved again; before, it then said nothing changed
- Tag scanner (Settings › Tagger › Scan…): remembers the songs it has been through (nothing to change, or you picked Replace or Keep), kept between runs; a scan again checks only new songs and ones changed since. Scan all again checks every one
- Artist pictures: kept on the phone once found (not loaded again); an artist gone from your music (no songs left, no plays in your stats) has their picture and its file deleted. On the PC they were kept already; now they're cleaned up the same way, after your folders are read (never while a folder can't be, a drive unplugged)
- Picking songs (or albums): a right-click (a long-press on the phone) opens Options for them, the one you clicked picked too; the ⋯ button still does
- Plays on singles and albums (Settings › Last.fm & your stats › Scan…): from your Last.fm scrobbles and Spotify, Apple Music or Deezer imports, songs whose plays are on their single, an EP and the album: each Merge plays (all of them counted for the album; a deluxe or extended edition first, then one you have), Other album (a dropdown, or one you type with yours suggested), or Leave alone (not asked again). A song's play count was one already; this is which album they count for (Most played albums, Albums time)
- Duplicates in Most played (Settings › Last.fm & your stats › Scan…): songs counted twice under different names ("N.P.D" and "NPD", "Zero (From 'Ralph Breaks the Internet')" and "Zero - From the Original Motion Picture…", "Man of the Year (v3)") and versions of a song (acoustic, live, remix, demo): each Merge (counted as the one shown), Other song (a dropdown, or one you type with theirs suggested), or Leave alone (not asked again). Nothing is merged unless you tick it; versions start on Leave alone
- Most played albums: an album's parts ("…: Pt. 1", "…: Pt. 2") count as one album; sessions records and best-ofs rank under the album a song is from
- Fix albums and artists: any album with the word "single" in it ("Talk - Single", "RUN (Single)", "Singles Collection") is a single, and so is any song alone on its album (the only song of it you have) or with no album: each looked for on an album or EP of yours (your songs, then Deezer's), never off a real album onto a sessions record or a best-of; one with nowhere to go is left as it is. Each one Change, Leave alone, or Other album (a dropdown, or one you type with yours suggested)
- Editions: any "… Edition" (Moon Music (Full Moon Edition), Viva la Vida (Prospekt's March Edition), Expanded, Special, Complete, Tour Edition) is an edition of the album, like a deluxe: Fix albums and artists puts the plain album's songs on it, Most played albums counts them as one, and plays go to it first
- Most played (and Artists): right-click (long-press) an artist for Change picture… (Deezer's, Google's, Genius's and Wikipedia's to pick from, or one of yours) and Merge into…. Pictures are still found on their own; one you pick is never replaced, even if a lookup was still going when you picked it. Wrapped uses the pictures and covers you picked too
- EPs (under Albums in the menu): only your EPs ("EP" in their name), like Albums: a grid or list, Collage of your top EPs, Select and right-click (long-press) for Options. They're still in Albums too
- Singles (under EPs in the menu): only your singles ("single" in their name, named for their song, or any song with no album at all), like Albums and EPs: a grid or list, Collage of your top singles, Select and right-click (long-press) for Options. They're still in Albums too
- Singles › Single scan…: each of your singles looked up on Deezer (else Apple Music) for the album or EP it's on (an album over an EP, the deluxe or extended edition first; never a compilation, best-of or live album). Each one Change (its album, album artist, track number, year and the album's cover; your own copy of the album's name and cover when you have it), Leave alone (not asked again) or Other album: the artist's albums and EPs in a dropdown, or one you type with albums and EPs suggested as you type. Android: the songs the app downloaded
- Folders to leave alone (Settings, next to Fix albums and artists) now count for every scan: Fix albums and artists, the tag scanner, Empty folders, and Merge into / Similar artists' retagging never look in them or change their songs
- Fix albums and artists never puts a song onto a single: songs only go onto albums and EPs (a single onto its album or EP, never an album's song or another single onto a single), and Other album only offers albums and EPs. An album named for one of its songs (The Search) is still an album
- Live in song names (Settings › Tags › Scan…): songs named as live recordings ("Song (Live)", "Song - Live at Red Rocks") get the live part taken off the name, the rest kept; each Change, Leave alone (not asked again) or Other name. Songs in Folders to leave alone are skipped
- Tag scanner: a live recording on Deezer is never offered for a song that isn't live (its name without "(Live)", and not the live album's name, number or year); the studio recording is matched first
- Go to album: right-click a song (long-press on the phone), or pick some and open Options (up to three albums): yours opens in Albums, else the album's page on the song's service
- Tagger: Artist, Album artist and Album suggest yours as you type (Artist: the name after the last comma, too); tap or Enter to fill it in
- Cover… with songs from several albums picked: one cover for all of them at once (ticked), or album by album as before
- WAV and AIFF files: their tags can be read and written (Tagger, Fix albums and artists, the tag scanner) and they take a cover, on the PC. Before, saving their tags did nothing
- Artist pictures (Most played and everywhere): Deezer is asked at a steady pace, shared with the tag scanner. Hundreds asked at once went over its limit and came back as "no picture", kept for two weeks; those are looked for again now, and a busy Deezer is asked again in an hour
- Artists tab: an artist you merged shows under the one you merged it into, even where the files keep the old name (songs the phone app didn't download)
- Joint artists (Settings › Last.fm & your stats): a pair's plays from Last.fm, Spotify, Deezer and Apple Music go to its main artist in Most played, Artists and the time tabs, the same way (Quinn XCII & Jeremy Zucker: Quinn XCII). The pairs it can't tell are listed: pick whose plays they are, or keep them as a pair
- Deezer history: import the file from Deezer's privacy settings (My personal data), and its plays count in Most played, Artists, Time, Milestones and Wrapped, as the Spotify and Apple Music imports do

Listening
- Crossfade, with how long, and Smart transitions (no crossfade inside an album): in the Equalizer. The ending song fades out while the next one fades in, at the same time, with no gap
- Crossfeed for headphones (a little of each side in the other, like speakers): in the Equalizer
- Smart shuffle: favorites and songs you finish come up more, songs you skip less
- Continue where you left off: open the app on the other device and carry on from there
- Cast to a Chromecast, Google speaker or TV (Now playing's Cast button on Android, the player bar's on Windows)
- Back up and restore: your playlists, stats, settings and the rest in one file

New releases
- Pin and Add to folder are gone from a release's menu; it has Discovery playlist… and Select
- Discovery playlist… (one release, or several picked with Select): one release at a time, all its songs, each one Add or Leave out; its own playlist, “<release> from <artist>”, is made, then on to the next
- Review is on a discovered playlist itself (next to Rename, Export and Delete) and goes through just that one's songs; it's no longer in the Discovery tab

Android
- The music keeps playing when you leave the app (the home screen, another app), with the player in your notifications, as it always should have
- The media notification has shuffle, previous, play/pause, next and repeat, coloured by the song; Settings › Notifications turns it on
- Lock screen player: Now playing over your lock screen, blurred, with the queue; the arrow takes you to the real lock screen
- A home screen widget: the cover, the controls and the queue, in the song's colour

My music
- Play, Shuffle and DJ at the top, as a playlist has
- Artists lists every artist you have songs of (your most played first, then the rest), not only your top 120
- Albums, Genres and Folders: the search finds every one, not only those scrolled past already; their grid (and compact grid) works, as Recently played's does, each cover made as it comes into view
- New releases' covers show on Windows (they couldn't be saved: the file's name had the album's web address in it)
- Recently played: a Refresh button, and the last 180 songs (a new one pushes the oldest off the bottom)
- Artists' pictures from Deezer first (the artist's own photo, no key needed), everywhere: Artists, Most played, the time tabs, artist pages. Pictures found before are looked for again once
- Settings › Tagger › Scan… (in place of Retag): finds songs with missing tags (year, genre, track number, album…) or ones that differ from Deezer's, and albums with no cover or a small one. An album at a time, each tag Replace (missing ones, ticked) or Keep (different ones), each cover one of up to three found or Keep. On Android, the songs the app downloaded only

Settings
- Every section folds away and starts closed
- A first start is on the Now playing look (the app takes the colours of the song playing); a look you picked before stays

## 1.4.3: The Tagger, collages and most played albums (Windows and Android)
- Song options (one song or several): Cover… and Edit tags… are now one **Tagger…**, with four tabs:
  - Tags: change the title, artist, album, album artist, genre, year and track (with several songs, a field they disagree on stays as it is unless you type in it)
  - Cover: the cover, and Find a new cover… (searching for it, or a picture of yours, as before)
  - Info: what the file is: format, quality, size, length, bitrate and every tag in it
  - Lyrics: the lyrics in the file; Find the lyrics, edit them, and Save them into the file (several songs: put each one's lyrics into its file)
- Android: tags and lyrics can be written into the songs the app downloaded (Android keeps your own files to other apps)
- Most played: Songs, Albums or Artists (an album's plays are its songs' added up), for this week, month, year or all time
- Collage…: your top songs, albums or artists (in Most played, and in the Albums and Artists tabs) as a grid of covers, 5×5 up to 25×25, with each one's name and plays; save it, copy it (Windows) or share it (Android)

## 1.4.2: Steadier, smoother, and Most played by week, month and year (Windows and Android)
- Most played: This week, This month, This year and All time, each adding up Last.fm, Spotify and Apple Music (every play with a time)
- Wrapped counts a song once across Last.fm, Spotify and Apple Music, as Most played does
- New releases: tap an album, single or EP to open it straight on Tidal or YouTube Music (no search, no looking through your files); an artist opens their page
- New releases: Select, pick releases and artists on any of its tabs, and make a discovery playlist of their songs you haven't got (“New releases Discovery playlist”; the ones you hear to the end go to “New releases discovered”, to download or let go)
- Android: the app no longer closes after a few songs with the screen off (between songs it stayed a "finished" player, and Android shut it down)
- Android: Settings › Problems keeps what went wrong (if the app ever closes) with a Copy button
- Android: bigger grids: the layout button goes list, small, grid, big (two across) and biggest (one across)
- Windows: the Visualizer and Now playing's bars run smoothly in a big or maximized window (drawn by the graphics card, and only the bars' strip each frame)
- Playlists: the discovery playlists fold away under one “Discovery playlists” row, opened when you want them
- A failed stats refresh waits two minutes before trying again

## 1.4.1: Most played, fixed (Windows and Android)
- Most played: every play counted and added up: all your Last.fm scrobbles, plus every play in your Spotify and Apple Music imports. Songs that went missing (one long play made the rest look too short) are back
- Refresh on Most played reads your newest Last.fm scrobbles; plays now come from Last.fm (the imports stay as they were)
- Most played plays your own file: the same song and artist however they're written (K.Flay or KFlay), the copy on the same album first, then the FLAC
- The DJ, like Spotify's: what you've had on repeat, what you play at this time of day, throwbacks to a year, songs you haven't heard in months, your favourites and new songs, leaving out the ones you keep skipping
- New releases: Refresh looks at every artist with songs on your device
- Mini player (Windows): Up next, with Shuffle, Repeat and Autoplay
- Windows: grids no longer widen the window off the screen; the EQ fills a maximized window; Wrapped no longer stops with "list index out of range"
- Removed: Moods, smart playlists and "Is it really lossless?"

## 1.4.0: Sound quality, stats and discovery (Windows and Android)
- Looks: Light, Dark and Now playing (the cover's colours), with a look for when nothing plays
- The menu: Pinned, Now playing, Search, Up next & queues; Music, Statistics and favorites, Utilities; Settings, Equalizer and the mini player
- Pin anything to Pinned; search a list page for just its songs; list, grid or compact layouts
- Lyrics on Now playing, in the cover's place; lyrics cards of up to a dozen lines to save, copy or post (Instagram, Facebook)
- The quality of what's playing (FLAC 24-bit/96 kHz…) under the seek bar; Previous restarts the song after 10 seconds
- Bit-perfect: each song straight to your DAC at its own rate (Windows: exclusive mode; Android 14+: USB DACs), with a check that it got there untouched; ReplayGain by song or album
- "Is it really lossless?" finds FLACs made from MP3s, upsampled hi-res and padded 24-bit; Upgrade swaps them
- Stats: import your Apple Music history; plays counted once across Last.fm, Spotify, Apple Music and here; a play counts from halfway; Most played shows every song; Skips by week, month, year or all time
- Repeat: your top 50 of the week, top 100 of the month and top 200 of the year; smart playlists by mood and genre
- Discovery playlists: an artist's songs you don't have, by album; heard ones move to "discovered" to keep or let go
- New releases from your artists (albums, singles, EPs), when you scan: nothing checks on its own
- Tag now shows what changes first; the tagger adds Genius's notes; downloads and upgrades are tagged together at the end
- Upgrade: new files go beside yours, the album's version over the single's, and the song playing is swapped where you are
- The DJ plays only your songs or brings new ones too
- Equalizer: Full screen (Compact on Android); headphone corrections only from AutoEq
- Wrapped: a Back button, your own covers first, no laggy swipes; Android has it too
- A quicker visualizer; Windows: closing goes to the tray, and a mini player (Android: picture in picture)
- Android in the car: Android Auto browses Up next, Favorites, Playlists, Recently played and your music

## 1.3.0: Apple Music-style redesign (Windows and Android)
- Now Playing takes the album art's colours over a lightly blurred background
- A slim bar visualizer behind the controls, always on, in the same colours
- The queue opens in the cover's place: Shuffle, Repeat and Autoplay, then Continue Playing and the other queues
- Autoplay keeps music going when the queue runs out
- Settings grouped Apple-style (General, Play on, Accounts, Your music…)
- Apple Music and Apple Music dark looks; livelier song and album lists
- PC: much less lag (menu, starting songs, fast scrolling, Settings, switching tabs); ☰ stays open until tapped
- Android: tapping a song's title plays it instead of sometimes opening the artist
- Linux builds dropped: Windows and Android only

## 1.2.0: Radon Player, a Stellio-style redesign
- Player is the main screen; ☰ side menu holds every tab; Settings (⚙) is a full page
- Looks: Stellio, Galaxy, Aurora, Sunset, Ocean, Midnight, Forest, Sakura, Candy (old themes kept)
- EQ per output device (headphones, speakers, car, Bluetooth, USB) with 3/5/10/15/31 bands, bass and treble
- Multi-select across lists; multiple queues per folder, album, artist and playlist that resume where they left off
- Albums, Genres and Folders tabs
- Lyrics search, saved offline and written into the songs' tags; cover search, with "apply to the whole album"
- Favorites, skips, a song's own volume, moods by hand, .m3u8 export, visualizer and Wrapped styles
- Download folder of your choice; ask about copies and likely duplicates; Upgrade songs to Tidal FLAC where it's better
- Updates come from Radon999's repository

## 1.1.9
- Show what's playing on Discord; the app is called VarMusic

## 1.1.8
- Most played adds your Spotify history to Last.fm
- Artists' pictures and bios, album cover choices, change an album's cover
- Moods for each song (the DJ and mood playlists use them)
- Truly random shuffle; sort playlists; Shift+click range select on the desktop; search as you type
- Local music in the source bar; Library folders; edit tags; delete songs; play faster or slower
- Artists tab and artist page; Visualizer tab; Auto EQ from AutoEq; share any Wrapped slide
- Playlist imports say what they didn't find; Genius annotations in Lyrics

## 1.1.7
- Android performance work; Wrapped week and month tabs on Android

## 1.1.6
- Downloads report how they went; retry songs Tidal didn't have; three at once, hi-res segments fetched together
- Android: YouTube songs download again; desktop smooth scrolling, no stall when a song starts
- Releases made automatically from merged pull requests

## 1.1.5
- Phone remote: the volume buttons set the desktop's volume
- Windows updates no longer roll back (installer waits for the old copy to close)
- Wrapped: week and month tabs, more detail, full screen

## 1.1.4
- A DJ, like Spotify's, on desktop and Android, playing from a playlist, folder or album

## 1.1.3
- Update is always a tap away; phone remote explains refused commands; long lists build rows as you scroll

## 1.1.2
- Search menu with artists and albums, .m3u8 import, Last.fm Most played; play the phone's songs on the desktop

## 1.1.1
- Android: pick the folders the Music tab reads; import Spotify listening into Wrapped
- Fall back to Tidal AAC, then YouTube, when Tidal fails; show downloads lost after a reinstall

## 1.1.0
- Lock-screen player, lossy fallback, sorting, in-app updates, new icon, smaller Windows build

## 1.0
- YouTube Music library, Tidal streaming in FLAC, EQ, local files, playlists, Music tab, artist pages, Spotify, tone EQ
- Android app with a phone remote for the desktop; Windows version, release signing and a download page
