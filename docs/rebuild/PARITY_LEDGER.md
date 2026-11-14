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
| A song skipped while loading never reaches the player | covered | P `aSongSkippedWhileItLoadsIsNeverLoaded` | |
| Queue ends: player pauses, stays on the song | covered | P `theQueueEndingStopsThePlayer` | Autoplay off, no other queue |
| Repeat one restarts from 0 | covered | P `repeatOnePlaysTheSongAgainFromItsStart` | |
| Previous: well into a song restarts it; in its first 10 s goes back | covered | P `previousWellIntoASongStartsItAgain`, `previousAtASongsStartGoesToTheSongBefore` | That going back isn't counted as a skip: not yet |
| Repeat all goes round to the first song | covered | P `repeatAllGoesRoundToTheFirstSong` | Reshuffling on the way round: not yet |
| After the queue: next saved queue, Autoplay radio | not yet | | Radio needs a service stand-in |
| Your copy first (`localOf`) | not yet | | |
| A load that fails: skip after 1.5 s | covered | P `aSongThatCantBeGotIsSkippedAfterAMoment` | With a stand-in for the services (`Downloader.standIn`, null in the app) |
| A stream that breaks: one retry from the same place, then the next song | covered | P `aStreamThatBreaksIsGotBackOnceFromTheSamePlace` | Same |
| A song that ends is counted as heard to the end | covered | P `aSongThatEndsIsCountedAsHeardOut` | |
| Skip counting: leaving part way is a skip, in its last seconds it isn't | covered | P `leavingASongPartWayIsASkipButNotInItsLastSeconds` | Going back isn't a skip, a song under a second in isn't: not yet |
| A play counts at half the song | covered | P `aPlayCountsOnceHalfTheSongIsHeard` | The Last.fm rule (half, or four minutes): not yet (needs a Last.fm stand-in) |
| A song stuck at its end moves on by itself | covered | P `aSongStuckAtItsEndMovesOnByItself` | Ran past its end, audio drained: not yet |
| Crossfade: the ending in a second player, the next comes in, ends at full volume | covered | P `crossfadeFadesTheEndingOutInAPlayerOfItsOwnWhileTheNextComesIn` | The shape of the fade itself: not yet |
| No crossfade on Repeat one or with bit-perfect | covered | P `noCrossfadeOnRepeatOneOrWithBitPerfect` | Streams, casting, same album, before a DJ set or comment, while ducked: not yet |
| Saved queues: the one you leave is kept with your place; going back; a queue that ends goes on with the other | covered | P `theQueueYouLeaveIsKeptAndTakenUpWhereYouLeftIt` | Moving, renaming and deleting queues, the most kept, Queues off: not yet |
| Shuffle, smart shuffle | partly | A `ShuffleTest` | The weights only |
| Per-song speed, volume, EQ | partly | A `SongSpeedsTest`, S `SongGainsTest` | Storage only |
| Android: stays a foreground service between songs | blocked | | Needs a phone, screen off |
| Cast, phone remote | not yet | | |

**Phase 9 (the playback service), begun 14 November 2026.** Moved out of `Music.kt` so far, each a part of its own
with its own tests and `Music` calling it, nothing behaving differently: what counts as a play, a scrobble and a skip
(S `PlayRulesTest`); what comes after a song, what Previous does, how other queues are kept (S `QueueRulesTest`); the
watch for a song stuck at its end (S `EndWatchTest`). Still in `Music.kt`: the queue's own state, loading and moving
on (`loadCurrent`, `play`, `next`, `trackEnded`), crossfade, saved state.

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
| Songs played stay out (300-song gap), kept across restarts | covered | P `songsTheDjPlayedDontComeBackYet` | The shrinking window for few songs: not yet |
| Kinds of set take turns | covered | P `kindsOfSetTakeTurns` | Three kinds only (what test songs can make) |
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
| Each of the 26 kinds of set: its own rule | not yet | | Most need a listening history stand-in |
| Mood arc, artist spread | not yet | | Private today; tested once the planner is its own part |
| The DJ never stalls (`djKeepGoing`) | not yet | | |
| Facts and milestones | not yet | | |

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
| USB DAC: voice mixed in, duck slope, 4 s stall | blocked | | Needs a phone and a DAC. The voice now reaches the DAC already at the DAC's rate (`Voice.dacRate`), so the straight-line rate change in `UsbDirect.speak` has nothing left to do. The mixing itself (`UsbAudioSink.convert`), the 0.12 s slope, the -1 dB headroom (R-A7) and the 4 s stall: as they were, unverified |
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
