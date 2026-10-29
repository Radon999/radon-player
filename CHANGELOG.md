# Changelog

Radon Player started as **Omarchy Music** (later called VarMusic), made by Xavier Reid (Varixxl).
Radon999 forked it in 1.1.x and carries it on here as Radon Player. The full history of both is kept
in this repository, so `git log` shows where every feature came from.

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
