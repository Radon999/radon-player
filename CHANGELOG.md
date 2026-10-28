# Changelog

Radon Player started as **Omarchy Music** (later called VarMusic), made by Xavier Reid (Varixxl).
Radon999 forked it in 1.1.x and carries it on here as Radon Player. The full history of both is kept
in this repository, so `git log` shows where every feature came from.

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
