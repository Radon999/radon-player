# Parity ledger

One row per feature or behaviour of the app as it is (3.2.28), with the test that pins it. The rebuild may only say a
feature is kept once its row is **covered** on every platform it runs on, or **changed by rule** (a rule in
`RADON_REBUILD_PLAN.md`, section 3). Rows are added as the audit goes on; a feature with no row is not covered.

Status: **covered** (a test runs in the checks), **fault pinned** (a test shows a fault the plan fixes; it is turned
round with the fix), **partly** (some of it), **not yet**, **blocked** (needs a device, an account or the owner).

Where tests run: S = `shared` tests, A = the app's own tests, P = the PC logic tests (`android/desktop/src/test`, the
whole app's logic started without a window; on Linux and Windows in the checks). Nothing runs on an Android device
yet: every row is unverified there.

## Playback and the queue

| Behaviour | Status | Test | Notes |
|---|---|---|---|
| Playing a song loads it and plays | covered | P `PlaybackBehaviourTest.playingASongLoadsItAndPlays` | Own files only |
| Next | covered | P `nextPlaysTheNextSong` | |
| A song skipped while loading never reaches the player | covered | P `aSongSkippedWhileItLoadsIsNeverLoaded`, `PlaybackGapsTest.aStreamSkippedWhileItIsStillBeingGotNeverPlays` | A file, and a stream that is slow to come |
| Queue ends: player pauses, stays on the song | covered | P `theQueueEndingStopsThePlayer` | Autoplay off, no other queue |
| Repeat one restarts from 0 | covered | P `repeatOnePlaysTheSongAgainFromItsStart` | |
| Previous: well into a song restarts it; in its first 10 s goes back | covered | P `previousWellIntoASongStartsItAgain`, `previousAtASongsStartGoesToTheSongBefore` | That going back isn't counted as a skip: not yet |
| Repeat all goes round to the first song; shuffled, in a new order | covered | P `repeatAllGoesRoundToTheFirstSong`, `PlaybackGapsTest.repeatAllGoesRoundInANewOrderWhenShuffled` | |
| After the queue: next saved queue, Autoplay radio | not yet | | Radio needs a service stand-in |
| Your copy first (`localOf`) | covered | P `PlaybackGapsTest.aSongYouHaveYourselfPlaysFromYourOwnCopy` | Picked from a list, and in a queue kept from before. Same album first, then FLAC (`bestCopy`), a live version never your studio copy, a song you don't have left for a service: P `YourCopiesTest` |
| Play next goes ahead of what you queued, Add to queue behind it; queued songs come along when other songs are picked | covered | P `PlaybackGapsTest.playNextGoesAheadOfWhatYouQueuedAndAddToQueueBehindIt` | |
| The queue after a restart: the same songs, place, Repeat, name and other queues; the song in the player, not playing | covered | P `PlaybackGapsTest.theQueueComesBackAfterARestart` | Reads `state.json` as written and back in. A song that must come from a service (waits for the player): not yet |
| A load that fails: skip after 1.5 s | covered | P `aSongThatCantBeGotIsSkippedAfterAMoment` | With a stand-in for the services (`Downloader.standIn`, null in the app) |
| A stream that breaks: one retry from the same place, then the next song | covered | P `aStreamThatBreaksIsGotBackOnceFromTheSamePlace` | Same |
| A song that ends is counted as heard to the end | covered | P `aSongThatEndsIsCountedAsHeardOut` | |
| Skip counting: leaving part way is a skip, in its last seconds it isn't | covered | P `leavingASongPartWayIsASkipButNotInItsLastSeconds` | Going back isn't a skip: P `goingBackToTheSongBeforeIsNotASkip`. The rule itself: S `PlayRulesTest` |
| A play counts at half the song | covered | P `aPlayCountsOnceHalfTheSongIsHeard` | The Last.fm rule (half, or four minutes): not yet (needs a Last.fm stand-in) |
| A song stuck at its end moves on by itself | covered | P `aSongStuckAtItsEndMovesOnByItself` | Ran past its end, audio drained: not yet |
| Crossfade: the ending in a second player, the next comes in, ends at full volume | covered | P `crossfadeFadesTheEndingOutInAPlayerOfItsOwnWhileTheNextComesIn` | The shape of the fade itself: not yet |
| No crossfade on Repeat one or with bit-perfect | covered | P `noCrossfadeOnRepeatOneOrWithBitPerfect` | Streams, casting, same album, before a DJ set or comment, while ducked: not yet |
| Saved queues: the one you leave is kept with your place; going back; a queue that ends goes on with the other | covered | P `theQueueYouLeaveIsKeptAndTakenUpWhereYouLeftIt` | Moving, renaming and deleting queues, the most kept, Queues off: not yet |
| Shuffle, smart shuffle | partly | A `ShuffleTest` | The weights only |
| Per-song speed, volume, EQ | partly | A `SongSpeedsTest`, S `SongGainsTest` | Storage only |
| Android: stays a foreground service between songs | **tried by the owner** (14 November 2026, test build 12, Bluetooth) | none (needs a phone) | Songs go on to the next with the screen off; next and previous work from the headphone buttons and the lock screen, screen off too. How long it ran with the screen off was not said: an hour's soak is still to do (Phase 11) |
| Cast, phone remote | not yet | | |
| **New (D17):** Download in the notification and on the lock screen, in Shuffle's place when Settings › Shuffle button in the notification is off (phone) | **tried by the owner** (14 November 2026, test build 15, a YouTube Music song) | none (needs a phone) | The Download button shows for a streamed song and the song is downloaded (it took a long time, from YouTube without Tidal). Build 14 greyed the button for songs that can't be downloaded; Android left it out and Favorite took its place: now Shuffle shows for those. Not confirmed yet: that the button turns to Shuffle the moment it is pressed. The button turns to Shuffle once the song is downloaded (the owner) |
| Signing in to Tidal (and Spotify) on the phone when the app is left during the sign-in (for the code Tidal mails) | **fixed** (a fault of 3.2.28: the sign-in page's address, and with it the secret half of the sign-in, was made again whenever the screen was drawn again, so Tidal's answer no longer fitted; the owner could not connect Tidal in the test app) | none (a screen; needs a phone and an account) | `Sheets`: the address is made once, as SoundCloud's already was. **Confirmed by the owner** (14 November 2026, test build 16): Tidal connected, with the mailed code. Spotify not tried |
| PC: the switches' file saved from several threads at once | **fixed** (a fault of 3.2.28 found by the checks: two saves at one moment failed, or could leave the file half written) | P `PrefsStoreTest` | The phone keeps its switches with Android, which was never affected |
| Skip counts read while a song that ends is being counted (the DJ's Not skipped and Second chances sets, Wrapped: read off the main thread) | **fixed** (a fault of 3.2.28 found by the checks: the read could fail at that moment, and the DJ's set of that kind was not made) | S `SongStatsRaceTest` | `SongStats`: readers take the counts as they are under the same lock the counting holds. Phone and PC alike |

**Phase 9 (the playback service), done 14 November 2026 (`68e5074`).** The queue and playback are out of `Music.kt`
(3,868 lines before the phase, 3,289 after) with nothing behaving differently. The rules are in the shared code, each
tested alone: what counts as a play, a scrobble and a skip (S `PlayRulesTest`); what comes after a song, what Previous
does, how other queues are kept (S `QueueRulesTest`); the watch for a song stuck at its end (S `EndWatchTest`); when a
song fades into the next and when it doesn't (S `CrossfadeRulesTest`). The doing is `PlaybackCore`
(`core/common/.../PlaybackCore.kt`, about 800 lines), moved in six steps, each checked on Linux and Windows before
the next: the queue's own state; starting, switching and changing queues; loading a song and moving on (next,
previous, a song's end, a stream that breaks, what counts as played, the radio); the clock and the end watch;
crossfade; the queue's part of `state.json` (same names, same file). `Music` keeps every name the screens, the
phone's service and the remote use and hands on to it. Of the DJ the core knows only `PlaybackCore.DjSide` (ten
questions and requests, answered by the DJ's part of `Music`). **Not done, on purpose:** it is an object on the main
thread, as `Music` is, not yet the actor with a mailbox and snapshots the plan describes (rules R-S1 and R-S2: that
changes how every screen reads the queue, a step of its own); it still asks `Music` for the app's own things (your
music, the services, the settings file, the player); the DJ's own part (`djAnnounce`, `djMore`, `djSwitch`, the
voice) stays in `Music` until the DJ becomes its own service. Untouched: the phone's foreground rule between songs
(`advancing`, the masked ENDED in `MusicService`), `ExoEngine`, `EqSink`, the USB path, what any DJ line says.

**Run in the real PC app after the move (14 November 2026; after the load-and-advance step `2fe1c31`, after the
crossfade step `90073f3`, and the finished code `68e5074`; `android/tools/pc-test`).** The same each time, and the
same as before the move: three short songs left alone hand over by themselves, two with a crossfade about six
seconds before the end and two of one album with no fade; skip; Previous both ways; pause and play; the queue from
the run before back after a restart, paused; the DJ starts, its sets follow, Switch it up, a song under the DJ left
to its end hands over (one of the DJ's own files faded into the next; a streamed one changed at its end with no
fade, as the rule for streams has it), the DJ stopped leaves the music playing; `problems.txt` gained no line.
Judged by what the app reports, not by ear. Nothing run on a phone: there the move is covered only by the app
compiling and its own tests.

**Run in the real PC app (14 November 2026, the code as of `0b94078`, in a copy of a test build with its own data,
driven through the app's own command socket: `android/tools/pc-test`).** All as they should be: a song left to end
by itself hands over to the next (crossfade on); skip; Previous in a song's first seconds goes back, later starts it
again; pause and play; skipping past the last song (Autoplay finds more); a play counted, skips counted, going back
not counted as a skip. With the DJ: starts in a few seconds, its sets follow one another (a Sad set, then a Happy
one: the moods apart, as decided), Switch it up brings another, a song under the DJ left to its end hands over, the
DJ stopped leaves the music playing; nothing written to Problems. Judged by what the app reports, not by ear.

## The DJ

| Behaviour | Status | Test | Notes |
|---|---|---|---|
| Starting plays a first set (3 to 6 songs) from the library | covered | P `DjFlowTest.startingTheDjPlaysAFirstSetFromTheLibrary` | |
| Starting turns Repeat all off | covered | same | |
| Next set queued before this one ends; introduced at its first song | covered | P `theNextSetIsQueuedBeforeThisOneRunsOutAndIntroducedWhenItStarts` | |
| No song twice in the DJ's queue | covered | same | |
| Picking other songs stops the DJ | covered | P `pickingOtherSongsStopsTheDj` | |
| Stopping leaves the music as it is | covered | P `stoppingTheDjLeavesTheMusicAsItIs` | |
| Switch it up replaces the rest of the set | covered | P `switchItUpPutsANewSetInPlaceOfTheRestOfThisOne` | That it is instant (a set kept ready): not yet |
| With the DJ: Repeat is one or off; shuffle does nothing | covered | P `withTheDjRepeatIsOneOrOffAndShuffleDoesNothing` | |
| A set never has a song still to come | covered | P `DjBehaviourTest.aSetNeverHasASongStillToCome` | |
| Songs played stay out (300-song gap), kept across restarts | covered | P `songsTheDjPlayedDontComeBackYet` | The shrinking window for few songs: S `SetRulesTest.theLastThreeHundredOrFewerFromAShortPlaylist` |
| Kinds of set take turns | covered | P `kindsOfSetTakeTurns`; S `SetRulesTest` (the one played longest ago first, a kind never played among the rest, kinds that could make no set after them, the one playing last) | In the app's own run three kinds only (what test songs can make); the rule itself with any |
| Requests: tags from words | covered | A `DjTest.tagsOfMoodsDecadesAndGenres` | |
| Picking leans to the top, no repeats | covered | A `DjTest.pick*` | |
| Not saying the same thing twice (bags, histories) | covered | S `NoRepeatTest` | |
| A line only names songs its set has (C-1) | **fixed**, changed by rule R-D2 | P `aLineOnlyNamesSongsItsSetHas` | Songs still to come are kept out before the line is written. With Only my songs, streams are left out of what the DJ plays from. Verified for one kind of set; the rule is shared by all |
| A set's kind has its turn only when the set plays (C-4) | **fixed**, changed by rule R-D4 | P `aSetsKindHasItsTurnOnlyWhenTheSetIsPlayed`, `DjFlowTest.aSetThatPlaysTakesItsKindsTurn` | Held too: facts and milestones, mood, decade, spotlight artist (same mechanism, not tested one by one). Still written when a set is made: the lines' shuffle bags, the artists already introduced |
| The DJ never names itself; a break is 45 words at most | **fixed**, changed by the plan's speech rules (2.4) | P `theFirstLineNeverNamesTheDjAndABreakIs45WordsAtMost` | Over 45: the word about the day goes, then the fact (it keeps its turn). A set line that is over 45 by itself (a very long title) is not shortened |
| Every line keeps to the rules (no self-naming, no shouting, no machine phrases, no sources) | covered | P `DjLinesTest.everyLineKeepsToTheRulesForHowTheDjTalks` | Reads every line from the source. 89 lines rewritten; `DJ_LINES_CATALOG.md` has them as they were. The owner hasn't heard them yet |
| The first set is made ahead: starting the DJ is instant | covered | P `DjFlowTest.aFirstSetMadeAheadGreetsWithTheTimeItIsStartedAt` | |
| A set made ahead greets with the time it's heard at (C-3) | **fixed**, changed by rule R-D3 | same | The first set gets a new hello; other sets drop their word about the day; a "this time of day" set from another part of the day is let go. Still as it was: a set queued behind the one playing keeps its words for the minutes until it starts; "tonight" inside other lines |
| Every kind of set has one of the four set types | covered (new) | P `everyKindOfSetHasItsType` | `Dj.typeOf`. The type isn't shown or used for framing yet beyond the thread below |
| The thread from the song before a set to its first song (same artist, genre or decade, from the tags) | covered (new) | P `theThreadFromTheSongBeforeIsSaidOnlyWhenThereIsOne` | Tested for the same artist; genre and decade need tagged files. Only for sets queued behind the one playing, not Switch it up (its voice is made ahead). Mood and tempo threads: not yet |
| All 320 lines written again in a radio host's voice | changed at the owner's request (2026-11-14) | P `DjLinesTest` (the rules) | `DJ_LINES_CATALOG.md` shows the lines as they were in 3.2.28, not as they are now |
| Stale "more like" (C-2) | not yet | | Needs a service stand-in |
| Three sets kept ready; Switch it up is instant | not yet | | |
| Ask for something | partly | A `DjTest` | Words only |
| Each kind of set: its own rule | partly | P `DjKindsTest` (On repeat, streaks, this time of day, on this day, throwback: each picks only the songs its rule is about, from a listening history of the test's own; none of the last three from no listening); P `DjFromKindsTest` (playing from a playlist: most played, not played yet, throwback, this time of day, an artist's set, the unreleased corner, mix); P `DjMoreKindsTest` (not skipped this month, second chances, a mood's own set, the set the loud moods share, a decade trip, an album moment, an artist spotlight: `Dj.tagStandIn` for the files' tags); P `DjNewSongKindsTest` (new for you, deep cuts, more like this, from a service of the test's own put in `Music.sources`; none from a service with nothing to offer; artists for you, with `Dj.similarStandIn` for Last.fm's and Deezer's similar artists); P `DjLastKindsTest` (rediscover, most played from everything, your genre from your own songs' genre tags: songs put in `Music.plays` and `Music.lib` for the test and taken out after) | `Dj.historyStandIn` stands in for Last.fm and the imports (null in the app). Also covered elsewhere: one-hit wonders, mix, not played yet. 27 kinds in all. Not yet: a genre set from Last.fm's tags (only reached when your own songs have no genres), and the mood picked from your listening |
| Mood arc, artist spread | covered | S `SetRulesTest` (building up from a calm first song, easing down from a loud one, a song with no mood in the middle; no artist twice in a row, two of an artist at most but in an artist's own set) | The rules are `SetRules.arc` and `SetRules.spread` in the shared code |
| The DJ never stalls (`djKeepGoing`) | not yet | | |
| Facts and milestones | not yet | | |

**Heard by the owner, 14 November 2026 (test build 12).** The DJ on the PC: "running extremely well, very smooth, and way better than the previous version". The DJ on the phone through the FiiO KA17 with bit-perfect: see the USB DAC row below. Bluetooth on the phone: "working great" (see the foreground-service row above).

**Phase 8 (the DJ's planner and scripts), first part, 14 November 2026.** Out of `Dj.kt` into the shared code, as
they were, each tested alone: `SetRules` (S `SetRulesTest`: the mood arc, artists spread out, a line and the songs
it names, picking, how many of the last songs are kept out, whose turn it is, the part of the day) and `ScriptRules`
(S `ScriptRulesTest`: what fits in a break of 45 words as said: the hello stays, a word about the day goes first,
then the fact; the thread from the song before; a break made ahead said for the part of the day it is heard in).
`Dj` hands on to both. **Not done:** each kind of set's own rule (they need your listening and the services: a
stand-in for both comes first), the lines themselves, the `DjDirector` (the DJ's part of `Music`: done since, see the next paragraph), the plan's
`DjSet` record. Checked on Linux and Windows; run in the real PC app (`1ad5a3e`): the DJ starts with its hello, five sets of different kinds follow one another (On this day, Mix with the thread from the song before, Not played yet, Not skipped this month), Switch it up, the DJ stopped leaves the music; nothing written to Problems.

**Phase 8, second part: the DJ's own part out of `Music.kt` (`DjDirector`), 14 November 2026 (`5a02b57`).** Moved
as it was, in five steps, each checked on Linux and Windows before the next (253 shared, 56 app, 65 PC tests, none
failing): the DJ's state and the sets made ahead (the first one while it is off, three spares while it plays); its
voice (a line said over the music, the music turned down under it and back up, hush); a set's line as it begins and
the word between songs; starting and stopping, what it plays from, the next set, Switch it up and asking for
something; and last what playback knows of the DJ (`PlaybackCore.DjSide`, answered by `DjDirector.side`), the DJ's
part of Play and its watch that the music goes on after a song. `Music` keeps every name the screens, the phone's
service, the remote and the tests use and hands on; `Music.kt` went from 3,302 lines to 2,881. `DjDirector` reaches
playback only through `PlaybackCore`, and asks `Music` for the app's own things (your music, the settings, the
player's volume, notes to you), each named at its top. No line of the DJ's words changed; the phone's rule between
songs and the USB path were not touched. Run in the real PC app after the voice step (`ca5dc7e`) and at the end
(`5a02b57`): the DJ starts with its line, seven skips go through a set and into the next kind, Switch it up brings
a set made ahead at once, a song under the DJ hands over by itself, Pause and Play, the DJ stopped leaves the music;
nothing written to Problems; the app quit after. **Not done:** each kind of set's own rule is still in `Dj.kt`;
there is no `DjSet` record; `DjDirector` is an object on the main thread like `Music`, not the plan's actor with a
mailbox; nothing was run on a phone (it compiles and its tests pass).

**Phase 8, third part: the kinds' own rules, 15 November 2026 (`5d80a90`).** What makes a song fit a kind of set is
out of `Dj.kt` for nine kinds, as it was, in the shared code: `KindRules` (S `KindRulesTest`: rediscover's three plays
and three months away, a throwback's two months, not skipped, how likely a song heard out comes up, second chances,
one-hit wonders, a decade and its name, the decades with eight songs, an album's order and its runs of three, the
artists the spotlight waits for, unreleased by the path; and since (`7800136`) how artists for you are scored from
the artists you play, and where the new songs go in a mood's set). `Dj` hands on; the PC tests of the same kinds (27 kinds in
all, see the DJ kinds row) pass as before on Linux and Windows. **Not done:** the kinds that are made from the
services and from Last.fm (new for you, deep cuts, more like this, artists for you, genre), the mood sets and
spotlight's choice of artist still have their rules in `Dj.kt`; no `DjSet` record; not run in the real PC app (no
behaviour was meant to change, and the tests of each kind passed).

**Phase 8, fourth part: the DJ's smaller rules, 16 November 2026 (`3c676ba`).** More out of `Dj.kt` as it was, each
tested alone (S `DjSmallRulesTest`): in `KindRules` the part of the day a "usual for this hour" set names, which mood
a mood set takes (this hour's plays counted double, the top three, the heavier likelier, and when the mood is said to
be this hour's), new-for-you's songs taken in turns from their seeds, which word about the day fits now, and what may
be said before a song (the milestone plays, your top ten, never skipped, a new obsession, an old friend); in `SetRules`
how a song the DJ played is kept and read back (the line in `dj_recent_songs`, unchanged); in `ScriptRules` how
counts, hours, months and places are said; in `TasteRules` the outside-plays count. **Not done:** the lines
themselves and the kinds made from the services and Last.fm (deep cuts, more like this, genre by tag) are still in
`Dj.kt`, as are the facts between sets; no `DjSet` record; no actor.

**Phase 8, fifth part: when a fact is worth telling, 16 November 2026 (`ca2cb7d`).** The bars the facts between sets
have to clear are out of `Dj.fact` as they were, in `FactRules` in the shared code (S `FactRulesTest`): an artist's
place among your five most played, the month's top mood and its share, an artist you've known for years or found this
month, the hours or minutes spent with a song, a first play long ago or a new find, never skipped or often skipped,
the hour you listen most and the shares after midnight and before ten, how many years it goes back, the biggest year,
the percent heard out, a song unheard for half a year. Then (the commit after): the collection's counts (songs by
artist, by genre, its hours, what came in lately, songs still unplayed) in `FactRules`, and in `KindRules` a genre
set's tag weights from your artists' Last.fm tags, the three heaviest to choose among, and a tag's songs with your
artists' first. **Not done:** what each fact says and the turn-taking stay in `Dj.kt`, with the smaller bars of the
collection facts (ten songs by an artist, twenty albums…) and the mood facts (mostly `MoodTally`'s already); deep
cuts and more-like-this are calls to the services with nothing of their own to split out.

**Phase 7, second part: `TasteService`, 16 November 2026 (`983eda7`).** What your listening adds up to (the plays it
goes by, the counting in `TasteRules`, the picture kept fresh by `KeptFresh`) is a service of its own, moved out of
`Dj.kt` as it was; `Dj` reads it, and Sort › Recently played, smart playlists and the mood pages ask it directly
instead of through the DJ (P `TasteServiceTest`: plays counted, the picture kept and given again, made again once
cleared). Checked on Linux and Windows. **One thing changed on purpose (`adb9dc4`):** smart shuffle's "not heard in
a while" now also goes by when you last heard a song anywhere (Last.fm, your imports), read from what `TasteService`
has at that moment and never waited for (before: only the plays in the app); P `PlaySortsTest`. Not heard by Claude.

**Phase 8, sixth part: a set's life, 16 November 2026 (`adb9dc4`).** `SetLife` in the shared code (S `SetLifeTest`):
planned, scripted, voiced, queued, announced or dropped, only forward, dropped any time before it's announced. `Dj`
keeps where each set made lately is, as the DJ's own steps happen (made; its line made as speech ahead; queued as set
n; its line shown; let go), and can be asked (`Dj.stateOf`; P `DjKindsTest`). Nothing the sets do goes by it yet.
**Not done, on purpose:** the record's voice part (the native module's, D18) and the DJ as an actor.

**Phase 12, fourth step: Linux starts with the system and updates, 16 November 2026 (`a0fe9fe`).** Start with Linux
(Settings › General: an autostart file, `~/.config/autostart/radon-player.desktop`, starting the app in the tray; where
a desktop has no tray its window opens instead of starting hidden), and Update on Linux (the release's .deb to
Downloads, opened with the system's installer; before, only the release's page opened). The rules are `StartupRules`
(S `StartupRulesTest`). Test builds make the Linux package too (`result-testbuild-linux.txt`). **Not tried on a real
Linux desktop** (the checks have no desktop, no tray and no release to update from). Not done: media keys and MPRIS
on Linux.

**Phase 2: a song's two keys in one place, 16 November 2026 (`8f55322`).** `TrackKey` (a song's own key and Most
played's, together) and `KeySet` in the shared code (S `TrackKeyTest`: the same answers as asking each key by hand,
which is what the code did). The DJ's `isUsed`, `inScope`, `newToYou` and the songs it keeps as played go through
them. Checked on Linux. Not done: the key kept with the track (it is worked out at each asking, as before); `store`.

**Test build 32 (16 November 2026): everything up to here**, for the phone (the side-by-side test app), the PC (the
folder that runs without installing) and, for the first time, Linux (the .deb). What it has that build 31 hadn't:
only moves and the three small things named above (smart shuffle also going by Last.fm's last play, Start with
Linux, Update on Linux). The `YouTubeAudioTest` in the checks now asks three times before failing (YouTube turns the
build server away now and then; `3a72b9b` failed on that alone).

**Phase 12, fifth step: the media keys on Linux, 16 November 2026 (`ecc0db1`, `747ae57`).** Asked for by the owner
as the one thing still needed. On Linux the app is a media player on the session's D-Bus (MPRIS,
`org.mpris.MediaPlayer2.radonplayer`): the song, its artists, album, length and cover, playing or paused, and Play,
Pause, Play/Pause, Next, Previous, Stop and seeks taken from the keyboard's and a headset's keys and the desktop's
own controls (`Mpris` on the PC's side with dbus-java; the values in `MprisRules`, S `MprisRulesTest`). **Tried for
real in the Linux check**, on a session bus started there, asked the way a desktop asks: the app is on the bus;
it says "Playing"; the song shown is the tone with its artist and length (40 s); after the Pause key it says
"Paused" and the app itself says Paused; after Play/Pause both say Playing again; its name is "Radon Player". (The
first try failed: the packaged app lacked `jdk.security.auth`, which D-Bus signs in with; added.) **Not tried:** a
real desktop (GNOME's or KDE's own controls, a real keyboard's keys, a headset), Next and Previous, the cover shown.
Raise and Quit from the desktop do nothing (it is told so).

**Test build 33 and the first install-over build (16 November 2026).** Build 33: build 32 with the Linux media keys.
The install-over build (`candidate.yml`, `ci/CANDIDATE`: 3.3.0) is the real apps as a release would make them, kept
with the run and not released: Radon Player Next for the phone with the test key (checked: name, package, version,
key, before it is handed out), the .msi, the .deb.

**The owner on build 32 (16 November 2026):** "Everything is working as intended and is way smoother than the
previous version of the app"; the settings and favourited presets of the app before came across. The hour with the
screen off was still to do. **Decided the same day:** the DAC phases dropped, the actors, `store` and the
measurements closed (plan D18, D19).

**4.0.0 released (16 November 2026, `648e1f6`, tag `v4.0.0`):** the phone's app, the .msi and the .deb, by
`candidate.yml` with `ci/RELEASE`; the same commit is `radon-player`. The README and the app's Features page are one
text; the app's Changelog starts at 4.0.

**After 4.0: Linux, from the owner's first report** ("settings not working correctly, logging into Tidal, not opening
up the link, the window is flashing weirdly"; no more detail yet). Found and changed, for a release after 4.0:
links went through Java's own opener, which on Linux often does nothing and says nothing: they go through the
desktop's own (xdg-open, else gio), and a link that can't be opened is copied with a word about it (`Opening`,
`9d6b778`); Tidal's sign-in said its page opens in your browser but only opened it from its button: it opens by
itself now (`5d7bd2f`, every system). **Seen in the check:** asked for Tidal's sign-in, the desktop's opener was
handed `https://login.tidal.com/authorize`. With no tray (GNOME) closing the window left the app playing with no way
back: it minimizes there. Settings › General › Software drawing (Linux; or `RADON_RENDER=software`): the window drawn
without the graphics card, for where it flickers. Songs that can't be had one after another made the player skip on
through the whole queue, a note for each and the window changing colour with every cover (seen in the check's own
pictures, where YouTube turns the server away): it stops after four in a row now and says why (`PlayRules.givesUp`,
every system). The check pictures each of Settings' ten sections (`linux-settings.jpg`): all draw, Start with Linux
and Software drawing are there. **Not known:** whether the flashing the owner saw was this or the graphics card
(not reproducible in the check, which has none), and which settings misbehaved.

**Where the plan stood before those decisions.** Done or done as far as it can be without the owner: Phases 0, 2 (but `store`), 6,
7, 8 (but the actor), 9 (but the actor), 12 (Linux but media keys; Windows' install-over is the owner's), 13.
**Waiting for the owner:** Phase 11 (the phone: an hour's soak with the screen off, Android Auto, the widget,
install-over), Windows install-over, a real Linux desktop, the DAC (D18: Phases 3, 4, 5 and 10 postponed until then).
**Not done, on purpose:** the services as actors with mailboxes (R-S1, R-S2), which changes the thread the queue and
the DJ run on and how every screen reads them: a step to take with the owner listening, not the night before a
test; `store` as the one writer; Phase 1's measurements on 3.2.28 and Phase 14's comparison with them (they need the
devices); MPRIS on Linux.

**Phase 13 (the remaining services out of `Music.kt`), begun 15 November 2026.** First: your copy of a song
(`YourCopies`, `830f3d4`): which of your files a song named by Last.fm, an import, a service or the queue is
(`songIndex`, `mineOf`, `localOf`, `fmSong`, `yourCopies`, the best copy). Moved as it was; `Music` keeps the names
and hands on; P `YourCopiesTest`. Second: Most played's lists (`MostPlayed`, `c2a53cf`, `44c6bc7`): the songs,
artists and albums of a period (`songs`, `artists`, `albums`), each with its rule apart from where the counts come
from (`songsOf`, `artistsOf`, `albumsOf`), so it is tested without Last.fm; P `MostPlayedTest` (two names for one
song of yours are one row; only your songs; an album and its deluxe edition are one; a single's plays count for
the album you have it on; a pair's plays are the lead artist's). What you chose (the period, only songs on this
phone, your picks) stays `Music`'s. One thing changed on purpose: the songs last made and the counts they were made
from are kept as one value, so the DJ reading off the main thread can't get one period's songs for another's
counts. Third: discovery playlists (`Discoveries`, `220c48e`): the pairs, making one for an artist or for the new
releases you pick, a song heard to the end moving to the discovered one, the review. Moved as it was, with the two
rules that were inside other functions given names (`notYours`: the artist's songs you haven't got, album by
album, then their top songs, none twice; `keep`: the pair made once, the songs added); P `DiscoveriesTest`. Checked
on Linux and Windows. Fourth: an album's songs you haven't got (`AlbumMissing`, `e8b9686`): the tracklist read
from Deezer and Download missing tracks…; its rules are `MissingSongs` in the shared code (a release's name without
its kind; which listed songs aren't among yours, by name, then by length), S `MissingSongsTest`. Two things changed
on purpose: a listed song named with "&" is your song named with "and" (3.2.28 meant this, but swapped the "&"
after the name was tidied, when it was gone already; such a song was only told by its length); and the tracklists
read already are kept in a map safe to read from several album pages at once. Checked on Linux and Windows. Fifth:
your playlists and their folders (`YourPlaylists`, `24010bf`): making, filling, renaming, syncing and deleting a
playlist, the folders of playlists, copying a service's playlists into yours, downloading whole playlists; moved as
they were, with what adding and importing say given names (`addedText`, `importedText`, `importedAllText`) and the
songs of playlists still to download (`toDownload`); P `YourPlaylistsTest` (a song is in a playlist once and
SoundCloud songs stay out; a deleted playlist leaves its folders and a deleted folder keeps its playlists; a
service's playlist is copied once and imported again only gets what's new; an unreadable one makes nothing).
Nothing changed on purpose. Checked on Linux and Windows. Test build 17 (`55d195e`) holds everything up to here.
Sixth: Download (`Downloads`, `d206e09`): which of the songs asked for are new (`newOf`), the asking about copies
you have, the songs waiting saved a batch at a time, why the song playing can't be downloaded (`whyNot`) and how
downloads are said to come (`qualityText`); P `DownloadsTest`. One thing changed on purpose: with Copies set to
replace, Download noted your copies to put away without the lock the other two places take (a service's sync
notes them from another thread); all three go through `Music.replaceLater` now. The saving itself (a real download)
has no test: it needs the services. Checked on Linux and Windows; not in build 17.

**From the owner's test of build 17 (15 November 2026), `6cb5941`, test build 18.** What the owner found and asked
for, all on phone and PC alike: (1) a song they have under another name of its artist (a scan had made "Forrest
Frank, …" into "Forrest") was put in a Discovery playlist and called missing from its album: `SameArtist.alias`
(shared; S `SameArtistTest`) now tells the two names are one artist (a shared artist, or one name the first words
of the other), used by `Discoveries` (`yourTitles`; made again, a discovery playlist also lets go of songs that are
yours after all; P `DiscoveriesTest`) and `AlbumMissing`. (2) An album or EP of yours lists its songs by track
number (`AlbumSongs`, `AlbumOrder`), reads itself again as your music changes, and has Refresh on its page; Downloads
shows Refresh when empty too. (3) A playlist in a folder shows in its folder only (`YourPlaylists.outsideFolders`);
folders can be selected, deleted, renamed and downloaded together (P `YourPlaylistsTest`). Compromises said to the
owner: the order is by track number only (a two-disc album's discs are not told apart; the tags read here carry no
disc number); the order is the album page's (an album played from elsewhere, or by the DJ, is as before); none of
the screens were looked at by Claude, only compiled and the rules tested. Their download without Tidal that never
came was YouTube not being signed in (the owner's own finding; no change made).

Seventh service: copies of a song and Duplicates (`Copies`, `3ac5f8b`): telling a download is a song you have, your
copy put away once the new one is in, a song going away for another in playlists, queue and plays, Android's trash
asks, the Duplicates list; moved as they were, with `groupsOf` (the Duplicates list) and `swapped` (the copy kept
takes the others' place in a playlist, once) named; P `CopiesTest`. Nothing changed on purpose. Checked on Linux and
Windows; in build 19.

**Covers beside a song (the owner's report from build 18), `901e401`, test build 19.** Songs showing no cover on the
phone (unreleased ones, a few singles) hold no picture in their files (looked at, read-only, in the owner's Music
folder on the PC): their cover is a picture in the folder (`cover.jpg`, or one lone picture under another name).
The PC read the named ones; the phone read none (a fault of 3.2.28 too, not of the rebuild: no cover code had
changed). `FolderCover` with `FolderCoverRules.pick` (shared; S `FolderCoverRulesTest`) is used on both now: the
lists, Now playing, the notification. Not known: whether those pictures are on the phone beside the songs, and
whether the phone has All files access (without it the folder can't be read); not seen on a screen by Claude.

**The owner's asks of 15 November (evening), `8129e17`, test build 20.** (1) Two-disc albums by disc, then track
(`AlbumOrder.sorted` with a disc, `FileTags.disc` on both; S `SameArtistTest`). (2) Songs that lose their cover
when the app is changed: the files hold no picture (seen in the owner's files; the log shows none on 7 November
either). The cover was one picked in the app, kept by the app and shown by it, so a fresh app (a test build, a new
install) has none. Tried on copies of those very files (MP3, M4A, WAV) with the installed app's own jars: a cover
written the app's way goes in, and stays through a tag write by the tag library and by `TagWrite`; so the writer is
not at fault for these formats on the PC. What changed: Covers from folders (`FolderCovers.apply`) also uses the
cover picked in the app and a folder's only picture, and counts a cover only when read back from the file. Not
found: why the pick was never written into these files when it was made (the code that does so is there now; it
may be newer than the picks); the phone's writer was not tried (no phone here). (3) Unreleased tab (`Unreleased`,
`UnreleasedRules`; S tests): folders named Unreleased and the folders chosen with Folders…. (4) A list's search is
kept for its place: Back returns to it, Back again closes it (every tab; goTo forgets them). The Folders tab is a
flat list (each folder with songs is a row), so there are no folders within folders to step back through there;
pages opened one from another already go back one at a time. None of the screens seen by Claude. The owner
confirmed Covers from folders put the covers in (build 20).

**Test build 22 (`908d110`, `c0f8814`), the owner's next asks.** (1) The phone asks for All files access at each
start without it (a dialog; Allow opens Android's page; the PC needs none). (2) The Tagger's fields keep what was
saved: Save cleared what was typed and showed the tags read before the save, so a saved name looked reverted (a
fault of 3.2.28 too). (3) A release tagged "… - Single" or "… - EP" is in Singles or EPs even in a plainly named
folder under Artists (the owner's HandClap - Single was in Albums): `releaseKindOf` (shared; S `ArtistsFolderTest`);
a folder named as an EP or a single still decides. This changes which tab some releases are in, on purpose. Checked
on Linux and Windows; none of it seen on a screen by Claude.

Eighth service: where a new release is on the services (`ReleaseLookup`, `57c178f`): `releaseFind`, `releaseTracks`
moved as they were, with `albumIn` and `songIn` named; P `ReleaseLookupTest`. **Test build 23 (`a7578e0`):** an
album's page shows its new names as soon as its tags are saved (the owner renamed an artist, and an album, in the
Tagger and Back showed the old ones until the page was left): the page drew the album as it was when opened, and a
renamed album is another album by its id; `AlbumSongs.follow` finds it again by its songs (P `AlbumSongsTest`).
Checked on Linux and Windows; not seen on a screen by Claude.

Ninth service: better copies of what you have (`Upgrades`, `a6432dc`): Upgrade (`songs`), Downloads' Upgrade
(`toFlac`, `youtubeDownloads`) and Refresh (`refresh`), moved as they were with what each says at its end named
(`doneText`, `flacText`, `refreshedText`); `swapPlaying` stays in `Music` (it is playback's). One thing changed on
purpose: the YouTube downloads already looked up on Tidal were a plain set read by the screen while a run added to
it from another thread; it is a concurrent set now. P `UpgradesTest`. The fetching itself has no test (it needs
Tidal). Checked on Linux and Windows; not in build 23.

Tenth service: a favorite and a song's own volume (`SongMarks`, `1ddd500`): `setFavorite`, `setGain`,
`refreshSongVolume`, `gainNow`, `gainOf` moved as they were, `favoriteText` and `gainText` named; P `SongMarksTest`.

**Test build 24 (`8b11858`): Tidal first.** The owner heard DJ songs (Include new songs) in YouTube's Opus that Tidal
has in FLAC (one downloaded as FLAC right after). Read in `Downloader.find`: any error asking Tidal for the stream
sent the song to Tidal's AAC or YouTube's audio, and that stream was used for 15 minutes. Now Tidal is asked twice
more first, a stream that is less only for such a hiccup is kept a minute, and Settings › Problems says when it
happened (`StreamRules`, shared; S `StreamRulesTest`). Not proven: that a hiccup (Tidal's "too many asks", as when
the DJ looks its next songs up together) was the owner's cause; it is the path the code shows, and Problems will
now say. A song already playing from YouTube is not swapped mid-song. Not run against Tidal by Claude. The owner
confirmed on build 24: streams show as FLAC now.

**Test build 25 (`05c4ee7`): the DJ's facts take turns.** The owner heard the facts between songs come back to
skips again and again. The subjects now take turns (`FactTopics`, `NoRepeat.pickTurn`, shared; S `FactTurnsTest`:
with six things to say about skips and one each about eight other subjects, skips come up once in nine) and
`Dj.collectionFacts` adds eighteen things to tell (listed at the end of `DJ_LINES_CATALOG.md`; `DjLinesTest` holds
them to the voice rules). Checked on Linux and Windows; not heard by Claude. The owner on build 25: the new ones are
good, but how many songs of an album you have says little: that one is out (`531a442`, test build 26).

Eleventh service: Sound presets and headphones corrections you keep (`SoundPresets`, `4a89b74`): saved, imported,
exported, deleted, an AutoEq one added, every one collected, a new output's starting preset; moved as they were, with
`presetKey`, `correctionKey` and `nameFromFile` named; P `SoundPresetsTest`. The equalizer itself (what plays, each
output's settings, the switch between outputs) is still in `Music.kt`. Checked on Linux and Windows.

Twelfth service: importing an .m3u8 playlist (`PlaylistImport`, `5e143cf`): `importM3u`, `matchSong`,
`reportSources`, `placeMissed`, `importPlaylist` moved as they were; how a line's place is tidied is `M3uPaths`
(shared; S `M3uPathsTest`); P `PlaylistImportTest` imports a playlist file with songs and a service of the test's
own. (Its first check run failed before building: the build server was refused by Maven Central, "too many
requests"; run again: green on Linux and Windows at `052abbc`.)

**Test build 27 (`90158cd`): Refresh in every tab's header.** Asked for by the owner (the statistics above all). One
button, on each tab with something to read again; which tab gets which reading is `RefreshRules` (shared; S
`RefreshRulesTest`): your listening added up again, your files read again, or the tab's own refresh (Downloads, Smart
playlists, Mood lists, Duplicates). Not on Now playing, Search, the queue, lyrics, the visualizer, New releases (its
own asks how far back), a service's library, or further in than a tab. A new icon (`Ico.REFRESH`), drawn by hand and
**not seen by Claude**. Checked on Linux and Windows. The owner on build 27: the icon looks right.

Thirteenth service: what is an artist's among your music, and Create artist playlist (`ArtistSongs`, `cb3814a`):
`creditsArtist`, `artistOwnSongs`, `artistOwnAlbums`, `makeArtistPlaylist` moved as they were, with `isTheirs`,
`inOrder` and `madeText` named; P `ArtistSongsTest`. Checked on Linux and Windows. Still in `Music.kt` (about 1,880
lines, from about 2,450 when Phase 13 began): the equalizer itself, tag and cover editing, the services' part,
remembering (state), your music's reading (rescan, relink), and the hand-ons.

Fourteenth: changing tags and covers by hand (`TagEdits`, `73b483a`): `setCover`, `editTags`, what waits for the song
playing, and `releaseFolder` (where a file belongs once its artist or album changed; P `TagEditsTest`). Fifteenth: the
streaming services' part (`MusicServices`, `2f9f1ad`, renamed in `8f36f0c`: the phone side has a `Services`
already, which only the phone's build could show): `browse` (its rule `browseTo`; P `MusicServicesTest`),
`sourceReady`, `tidalLoggedIn`, `tidalDisconnect`, `syncLibrary`, `setSyncSource`. Signing in and the sync have no
test (they need the services). At `8f36f0c` the app's own `YouTubeAudioTest.findsAudio` failed on Linux: it asks the
real YouTube for a song's audio, and the answer could not be read (not touched by these moves; watched). It passed
at `26488dd` and failed again at `d4afc7b`: two of three runs in an hour, the same error (`ParsingException` from the
extractor), after passing all day. Not known which: YouTube turning the build server away now and then (it does
that to servers), or its page having changed so that the extractor in the app needs a newer version. To tell: a song
played from YouTube without Tidal on the owner's phone. **The owner tried it (15 November 2026): it plays.** So the
app's YouTube reading works, and the failures are YouTube turning the build server away now and then: a failure of
that one test alone is run again, not taken for a fault.

Sixteenth: deleting songs (`DeletingSongs`, `d4afc7b`): `deleteDownloads`, `phoneFilesDeleted`, `removeDownload`,
`phoneFile`, `deletable`; `downloadsToDelete` named; P `DeletingSongsTest`. Checked on Linux and Windows (the PC
tests; the app's tests but for the YouTube one above). **Plan decision D18 (the owner, 15 November 2026): Phases 3,
4, 5 and 10 (the native audio module, the USB path rebuilt on it) are postponed**; the DAC is tested again once all
else is done, and then it is decided whether they are needed.

Seventeenth: the equalizer as you use it (`EqControl`, `3ba0a51`): changing its settings, what it plays, each
output keeping its own, the switch between outputs, a song's own EQ, Boost, Dual volume, Bass and Treble with
bit-perfect, ReplayGain's factor. Its rules are named and tested (P `EqControlTest`: `kept`, `played`, `moved`,
`boost`, `rgFactor`, `outLabel`); the settings and what the screens read stay in `Music`. Written out again by hand
from the code as it was, not moved line for line: **not heard**, and an output switch not tried on a device. Checked
on Linux and Windows. `Music.kt` is about 1,510 lines: what the screens show (state), remembering (saving and
loading), your music's reading (rescan, relink, files moved), playback's hand-ons, and the hand-ons to the parts
above. The owner on build 28: everything works as intended.

Eighteenth: your music folders (`YourFolders`, `f7035fd`): reading them (`rescan`), choosing and removing them, and
keeping the queue, your playlists and favorites on the right song when a file moves (`relink`, `filesMoved`,
`fileGone`), is replaced by another version, or is the other device's (`adoptFiles`, `standIn`); genres filled into
downloads that have none. The rules are shared (S `MusicFolderRulesTest`: which folders are kept, a moved song's
address and id, where a song that isn't here is looked for, what is said); P `YourFoldersTest`. Checked on Linux
and Windows. With it the Linux check's own lines (the app started, played, the 12 tabs) are in its report again.

Nineteenth: looking artists up (`ArtistLookups`, `b9fca81`): their pictures and bios, a picture of your own, an
artist's page from the first service that knows them. S `ArtistLookupRulesTest` (the names tried, the services
asked and their order); P `ArtistLookupsTest` (the page from the service asked for, its songs from the next).
Checked on Linux.

Twentieth: your music as the tabs show it (`YourMusic`): every song you have, by album, genre, folder and artist,
and which tab (Albums, EPs, Singles) a release is in, with a tab picked by hand winning. S `ReleaseTabRulesTest`;
P `YourMusicTest`. Checked on Linux and Windows (`20108f5`).

Twenty-first: your picks in the stats (`StatPicks`, `cdac635`: Joint artists, similar artists kept apart, Merge
into, plays counted for another album or song, Not a duplicate, singles left alone; S `StatPickRulesTest`, P
`StatPicksTest`) and the playing song's cover for the lock screen and notification (`NowArt`). Twenty-second:
sorting by how you listen (`PlaySorts`: each tab's sort, play counts from the stats and the plays here, a
playlist's sort, Smart shuffle's weights; P `PlaySortsTest`). `Music.kt` is about 1,080 lines: what the screens
show (state), remembering (saving and loading), playback's hand-ons, and the hand-ons to the parts above.

Twenty-third: what the app remembers between starts (`Remembered`, `ebf97e5`: state.json's shape, reading it at the
start, writing it; the checks on what is read are `RememberedRules`; S `RememberedRulesTest`, P `RememberedTest`).
Twenty-fourth: the last small parts (`82a3a87`): the play buttons (`PlayButtons`: Play, Pause, Next, seek, the heart),
casting (`Casting`), a song's radio (`SongRadio`), Settings › Sound's switches and bit-perfect (`SoundSwitches`); a
song's speed went to `SongMarks`, lyrics into a file to `TagEdits`, the upgraded song swapped in to `Upgrades`, the
.m3u8 export to `YourPlaylists` (S `PlayButtonRulesTest`, P `PlayLeftoversTest`). `Music.kt` is about 830 lines, and
what is left is what it is meant to keep: what the screens show (state), starting the app (`init`), and the names the
screens call, each handing on to its part.

**Test build 29 (`90862a8`): asked for at the start.** The owner: the visualizer on Now playing did nothing until the
Visualizer tab had been opened and its permission given. The phone now asks for all it needs as it starts, at every
start until allowed (`StartupPermissions`: Android's questions for your music, the microphone, notifications; then a
box for All files access or one refused twice); Now playing and the Visualizer tab look again once answered. And a
folder of music is asked for at each start while none is chosen (`AskForMusicFolder`; a fresh install starts with
the Music folder chosen, so it is not asked then). Screens only: no test, **not seen by Claude**.

**Test build 30 (`650867e`): bit-perfect was not bit-perfect for some songs. Found by the owner.** Settings said
16-bit/48 kHz for a 24-bit/48 kHz FLAC, bit-perfect on, FiiO KA17. It was true. `EqSink` brings hi-res down to 16-bit
unless `Music.bitPerfectNow()`, which on the phone means a USB output listed by Android or a stream straight to the
DAC already running; it is asked before each song's stream is set up. With the DAC's stream closed in between (and
Android kept off the DAC, so no USB output listed), it was false: the decoder was not asked for all the bits and the
song reached the DAC as dithered 16-bit, under the words "Bit-perfect". A fault of 3.2.28 too (`EqSink`, `UsbDirect`
and `bitPerfectNow` are as they were). Fixed: the song also goes on as it is when it is about to go straight to the
DAC (`UsbDirect.wanted()`; `SinkRules`, shared, S `SinkRulesTest`), the EQ is bypassed then, and Problems gets a line
if a hi-res song still goes out as 16-bit with a DAC plugged in. **Not proven by Claude** (no phone, no DAC): the
owner checks that Settings says 24-bit for 24-bit songs, over several songs and after a pause. How often it happened
before is not known; the earlier "works perfect with the DAC" was about noise and song changes, not bit depth.
**Confirmed by the owner on build 30 (16 November 2026): Settings says 24-bit now.**

**Phase 12, first step: the app run on Linux (`87b915f`).** The Linux check now installs the package and starts the
real app on a screen nobody sees (Xvfb), with data of its own, and keeps what it found on `rebuild-ci`
(`linux-start.txt`, `linux-start.jpg`). First run: **it starts and stays up** (45 s), its window draws (Now playing,
"Nothing playing": the first picture of any screen of the rebuild Claude has seen), no Problems written. Found:
**mpv is not in the Linux package** ("mpv isn't there": nothing can play until mpv is installed or bundled; plan D2);
no tray on that screen (expected without a desktop); the drawing falls back from OpenGL (expected there).
**Second step (`06dfb05`): it plays.** `ci/linux-deps.sh` adds mpv and ffmpeg to what the .deb asks for; the check
installs the package with apt, as a person would, and mpv comes with it (0.41). The running app is then asked from a
command line to play a 40 s tone and says "Playing: Linux test tone · Radon checks (0:07 of 0:40)", then 0:14: the
song loads and its time moves. The picture shows Now playing with the song, its format (FLAC · 16-bit · 44.1 kHz), the
time bar part way and the visualizer's bars drawn. (The build server has no sound card: mpv plays into nothing there,
so that it is heard is not shown.) Without mpv the app now says how to install it on Linux. The Linux package made
for a release must go through `ci/linux-deps.sh` too (there is no Linux release job yet). Not tried: a real desktop
(tray, media keys, MPRIS), the updater, sound out of a real card, a streamed song.
**Third step (`e11b463`): the screens, seen.** The check gives the app a little music of its own (two albums, a
single, a song in a folder named Unreleased), opens twelve tabs in turn from the command line and keeps one sheet of
pictures (`linux-screens.jpg` on `rebuild-ci`). Seen by Claude for the first time, and as meant: the Refresh button in
each tab's header (and none in the queue or Settings); **Unreleased** with its song, its folder, Play, Shuffle and
Folders…; Downloads with Refresh while empty; Folders, Singles (a song with no album, and "Lone - Single"), Artists,
Playlists, Most played and Skips (the tone, played once, never skipped), the queue, Settings' sections. Also seen:
with the tone over, Autoplay's radio queued songs from YouTube and each failed with "YouTube probably temporarily
blocked anonymous watch access with this IP… Sign in to confirm that you're not a bot": YouTube turns the build
server away, which is what fails `YouTubeAudioTest` there now and then (the owner's phone plays YouTube). Albums
showed "0 albums" with "Low song count albums (2)": the two test albums have three and two songs (as 3.2.28 files
them). Not looked at: every other screen, the phone's layout, anything pressed with a mouse.

**Test build 31 (`012a851`): Smoothest screen.** Asked for by the owner: a switch (Settings › Playback, phone, only
where the screen has a rate above 60) that asks for the screen's highest refresh rate (`ScreenRate`); off by default.
The PC draws at its monitor's rate already. No test (a window's attribute). **The owner on build 31: it shows, and it is smoother.** **Not
done (Phase 13):** the rest. **Not
done:** every other service in the plan's row 13 (the streaming sources' part of `Music`, the tagger's
part, sync, the remote, favourites, the equalizer); not run in the real PC app or on a phone; the Deezer lookup
itself has no test (it needs the network).

**Phase 7 (what your listening adds up to), first part, 14 November 2026.** The counting that the DJ's sets and facts
are made from is out of `Dj.kt`: `TasteRules.count` in the shared code, the same loop as it was, now tested alone (S
`TasteRulesTest`: a song's plays, first and last; one song however its artist is written; the last 30 and 90 days;
around this hour, round midnight; on this day in past years; the days of the last week; this month; moods). One
thing is quicker with the same result: a song with no mood was asked for its mood at every one of its plays, now
once. What is made is kept by `KeptFresh` (S `KeptFreshTest`): under ten minutes old it is used as it is, as before;
older, it is **still used at once while a new one is made in the background** (before: whoever asked waited for the
whole history to be read again); the first time, or past half an hour (so "what you play around this hour" is never
from another part of the day), the asker waits as before. A new one that fails leaves the old one in place. **Not
done:** `Stats`, `Wrapped` and smart shuffle still add up for themselves; the rest of `loadTaste` (the songs lists,
Most played's counts laid over) is still in `Dj.kt`; nothing compared against a real history; not run on a phone.

## The DJ's voice and speech

| Behaviour | Status | Test | Notes |
|---|---|---|---|
| Numbers, years, symbols, contractions said as words | covered | S `SpokenTest` | |
| Names and capitals made sayable: stylised names, initials spelt on purpose, words in capitals as words, Roman numbers, letter-number names | covered (new) | S `SpeechPrepTest` | In `Spoken.humanize`, for any voice. The list of stylised names is short (about 30) and grows as wrong ones are heard. Not heard through the voice yet |
| Words Kokoro didn't know were respelt (`Pronounce`) | no longer used | S `PronounceTest` (still runs) | Pocket TTS has no word list to miss from. The code is kept, unused, until that is confirmed by ear |
| Line length (45 words) and banned phrases | not yet | | New rules; a check over every line comes with the rewrite |
| The natural voice is Pocket TTS (was Kokoro), on the phone and the PC, Linux too | changed at the owner's request | P `PocketVoiceTest.theVoiceSpeaksALine` (Linux and Windows, made for real) | The phone's side compiles and is **not run**. The voice it copies is the model's own sample |
| Voice made ahead, one line at a time, hush | partly | same (a line stopped before it starts makes nothing) | Made ahead on the PC too now. The queue and the cache: not yet |
| The voice is made ready for the music (`DjVoiceProcessor`): proper rate change, rumble out at 80 Hz, +2.5 dB under 180 Hz, +2 dB at 3.5 kHz, peaks held under -1.5 dB with a 2 ms look ahead, 5 ms fades | covered (new), rule R-A1 | S `VoiceDspTest` (14 tests: each part and the whole, at 44.1, 48, 96 and 192 kHz) | In Kotlin, not in a native module: it runs once per line before it's said, never while sound plays, so it needs no native code. Used for Pocket TTS on the PC (48 kHz) and the phone (the phone's rate, or the DAC's). Not applied to ElevenLabs or the system voices. **Not heard yet** |
| Ducking (standard, 320 ms) | partly | | Now 32 steps of 10 ms (was 8 of 40): same length, same end points, smoother. Not tested (needs a player stand-in with volume over time) |
| USB DAC: voice mixed in, duck slope, 4 s stall | **heard by the owner** (14 November 2026, FiiO KA17, bit-perfect on, test build 12) | none (needs the device) | The owner: the DJ "works perfect with the DAC", no noise, songs hand over as they should, the problems 3.2.28 had with the DAC gone. Unplugging the DAC mid-song pauses the music: **the owner wants it kept so** (decided the same day; not to be changed to carry on through the phone). After that test: the voice on its own gives a line up when the DAC takes nothing for 4 s (before: it waited with no end, the music held behind it), and the "music not getting through" note is no longer written while the DJ's first words have the stream (a false alarm in the owner's Problems list). Neither is tried on a DAC yet. Bit-perfect is the normal case for anything to do with the DAC. The voice now reaches the DAC already at the DAC's rate (`Voice.dacRate`), so the straight-line rate change in `UsbDirect.speak` has nothing left to do. The mixing itself (`UsbAudioSink.convert`), the 0.12 s slope, the -1 dB headroom (R-A7) and the 4 s stall: as they were, unverified |
| Desktop: the music is turned down when the voice starts (C-7) | **fixed**, changed by rule R-A2 | none yet | By the code: `onStart` runs when the sound begins. Not tested (needs a sound device) |

## Everything else

Not yet audited in code (see the plan, section 2.5). Each area below has only the tests listed; all else is **not yet**.

| Area | Tests today |
|---|---|
| Library, names, sorting, search | S `NamesTest`, `SortingTest`, `SearchNormTest`, `ArtistsFolderTest`, `OwnArtistsTest`; A `SortSongsTest`, `EditionsTest`, `LiveSearchTest`, `RegexTest` |
| Playlists, smart playlists, import | S `SmartRulesTest`, `SyncPlaylistsTest`; A `PlaylistFoldersTest`, `ImportTest` |
| Streaming and downloads | S `ReleasePickTest`, `FlacFormatTest`; A `YouTubeAudioTest` |
| Moods | S nine `Mood*`/`*MoodTest` files |
| Stats, Wrapped, histories | S `ListeningTest`; A `StatsTest`, `SpotifyHistoryTest`, `AlbumPlaysTest` |
| Lyrics | A `GeniusNotesTest` |
| Tags, covers, tidying | S `TagWriteTest`, `TagKeepTest`, `TagChangesTest`, `WavTagsTest`, `OggOpusTest`, `EmbeddedArtTest`, `CoverMatchTest`, `CoverTrustTest`, `FolderCleanupTest`, `GenreDupesTest`, `GenreFitsTest`; A `TagsTest`, `CoversTest`, `PicturesTest` |
| Sound | S `DeclipTest`; A `AutoEqTest`, `SpectrumTest` |
| Phone + PC | S `SyncMergeTest`, `BackupMergeTest`, `BtIgnoreRulesTest`; A `PhoneShareTest` |
| Personalization, visualizer, every screen | none |
| Android only (Auto, widget, lock screen), PC only (tray, media keys, Discord) | none |
| Linux | The installer builds in the checks; nothing is run |
