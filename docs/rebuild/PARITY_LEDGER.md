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
| Your copy first (`localOf`) | covered | P `PlaybackGapsTest.aSongYouHaveYourselfPlaysFromYourOwnCopy` | Picked from a list, and in a queue kept from before. Same album first, then FLAC (`bestCopy`): not yet |
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
| Each kind of set: its own rule | partly | P `DjKindsTest` (On repeat, streaks, this time of day, on this day, throwback: each picks only the songs its rule is about, from a listening history of the test's own; none of the last three from no listening) | `Dj.historyStandIn` stands in for Last.fm and the imports (null in the app). Also covered elsewhere: one-hit wonders, mix, not played yet. Not yet: the kinds that go by Most played (rediscover, most played, favourites: need a stand-in for `Stats`), by skips (not skipped, second chances), by the files' tags (decade, album, genre, unreleased), by moods, and the kinds of new songs (need a stand-in for the services) |
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
stand-in for both comes first), the lines themselves, the `DjDirector` (the DJ's part of `Music`), the plan's
`DjSet` record. Checked on Linux and Windows; run in the real PC app (`1ad5a3e`): the DJ starts with its hello, five sets of different kinds follow one another (On this day, Mix with the thread from the song before, Not played yet, Not skipped this month), Switch it up, the DJ stopped leaves the music; nothing written to Problems.

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
