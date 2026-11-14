# Radon Player: architectural audit and rebuild plan

Audited: `Radon999/radon-player`, branch `radon-player`, version 3.2.28 (`android/app/build.gradle.kts`, `android/desktop/build.gradle.kts`). Written for implementation by Opus 5.5. Companion file: `DJ_LINES_CATALOG.md` (Appendix A, every DJ line with its source line).

## 0. What this audit did and did not cover

**Access limits (read these first).**

- `git` is not installed on the audit machine, so the repository was not cloned. The default branch was downloaded as a source archive (325 files, about 3.9 MB). From `android-work` only `ci/next/result.txt` and `.github/workflows/next.yml` were read. The 300+ `rNNN.patch.b64` patches and the other branches were **not** read, so "why it works this way" comes from code comments and `docs/history.md`, not from the patch history.
- Nothing was built or run. Every bottleneck below is code-evident, never measured.

**Audit depth, per area.** The inventory in section 2 marks each row **C** (code read) or **D** (documentation and file headers only).

| Read in full | Read in part | Not read (inventory from `FEATURES.md`, `PLAN.md`, file names) |
|---|---|---|
| `Dj.kt`, `DjVoices.kt`, both `Voice.kt`, `UsbDirect.kt`, `UsbAudioSink.kt`, `Spoken.kt`, `Pronounce.kt`, `NoRepeat.kt`, `Engine.kt`, `Track.kt`, `ui/DjVoice.kt`, `DjTest.kt`, all Gradle files, `CLAUDE.md`, `README.md`, `FEATURES.md`, `PLAN.md`, `docs/history.md` | `Music.kt` (lines 22–360, 1025–1436, 3016–3516 of about 3,800), `MpvEngine.kt` (20–195, 200–330), `usbaudio.c` (20–113 plus all signatures), `EqProcessor.kt` and `ResampleProcessor.kt` (signatures and comments), `Prefs.kt`, heads of `Sync.kt` and `Remote.kt`, `SoundMood.kt` (tempo parts) | All of `ui/` except `DjVoice.kt` (about 1.6 MB), `Tidal`, `Spotify`, `AppleMusic`, `Deezer`, `SoundCloud`, `YouTubeMusic`, `Stats`, `Wrapped`, `Moods`, `Lyrics*`, `Tagger*`, `Fixup`, `Downloader`, `Stores`, `Backup`, `Chromecast`, `Discord`, `Car`, `MusicService`, widgets, `PythonImport`, and the rest |

Consequence: sections on the DJ, voice, USB output and the playback state machine are reverse-engineered from code. Everything else is a documented-behaviour inventory that Phase 0 must turn into characterization tests before any of it is rebuilt. No feature outside the code-read set should be treated as understood.

**Names in the brief that do not exist in the repository.**

| Brief says | Repository has | Treatment |
|---|---|---|
| `DjVoiceProcessor` | Nothing. There is no voice DSP at all: no filter, no limiter. Resampling is linear interpolation inside `UsbDirect.speak` (lines 494–505) and `speakAlone` (551–567) | `DjVoiceProcessor` is a **new** component (section 1.3, rule R-A1) |
| `DJSet` | `Dj.Built(kind, songs, line, fact)` and `Dj.Info(kind, line, fact)`; audio is cached separately in `Voice.ahead`, keyed by the line's text | `DjSet` is the new unified record (rule R-D1) |
| "three spare sets plus a cold-start set" | Exists: `SPARES = 3` (`Music.kt:1402`), cold start `djFirst: Warm`, fresh for 30 minutes (`Music.kt:1116–1119`) | Preserved |
| "duck to 25% over 320 ms" | Exists: `DUCK = 0.25f`, 8 steps of 40 ms (`Music.kt:1225, 1238–1240`) | Preserved, made sample-accurate |
| "existing 0.12-second USB duck envelope" | Exists: `ease = 1.0 / (rate * 0.12)` per frame (`UsbAudioSink.kt:139`). Because it only travels from 1.0 to 0.25, it takes 90 ms, not 120 | Slope preserved exactly (rule R-A6) |
| "four-second stall watchdog" | Exists: `if (still > 4000) break` (`UsbDirect.kt:512`). What follows is **not** a speaker fallback: the rest of the line goes to the DAC alone via `speakAlone`, which flushes the stream | Conflict C-17, decision D5 |
| Linux desktop | `CLAUDE.md`: "Linux is no longer a target". `PLAN.md`: "Linux MPRIS: not wanted". Linux leftovers exist (`MpvEngine` Unix socket, `TargetFormat.Deb`, CI builds a Linux jar). On Linux the DJ cannot speak at all: `Voice.canSpeak = windows \|\| elevenOn`, and `elevenOn` is hard-coded `false` | Decision D2 |
| Rust | None. The app is 100% Kotlin (Compose Multiplatform), plus one C file (`usbaudio.c`) and one C# helper (`desktop/smtc`) | Decided (D1): Kotlin-first, with one small native audio module |

---

## 1. Overview: current and unified architecture

### 1.1 Verified current architecture

**Modules** (`android/settings.gradle.kts`): `:shared` (plain JVM logic and the only tests CI runs), `:ui-common` (KMP theme, icons, widgets), `:app` (Android), `:desktop` (Compose Desktop). `core/common`, `core/android`, `core/desktop` are not modules: they are source directories compiled into `:app` and `:desktop` (`sourceSets[...].srcDirs`), with the two platform directories declaring the same names (`Voice`, `Platform`, `FileTags`, `LocalFiles`, `Services`, `BtRoute`, `AudioTools`, `ExtraTags`) as an expect/actual by convention.

**Dependencies that matter.** Android: Media3 1.4.1 (ExoPlayer, DASH, HLS, session, cast), sherpa-onnx 2.0.1 AAR (Kokoro), jaudiotagger, NewPipeExtractor, OkHttp, Coil, NDK for `usbaudio.c`. Desktop: bundled `mpv` and `ffmpeg` driven over a JSON pipe, jaudiotagger, jmdns, JNA, a .NET 8 SMTC helper, `sherpa-onnx-offline-tts.exe` downloaded at runtime, PowerShell `System.Speech` for SAPI and dictation.

**State ownership today.** One singleton, `object Music` (about 3,800 lines), owns the queue, index, playback flags, the DJ's runtime state, settings mirrors, and about 150 Compose `mutableStateOf` fields read directly by the UI. It runs on `Dispatchers.Main`; blocking work goes through `Music.bg` to `Dispatchers.IO` and returns to Main. Other singletons hold their own mutable state and reach back into `Music`: `Dj` (taste cache, histories, scope), `Voice` (turn counter, synthesis queue, cache), `UsbDirect` (DAC handle, voice buffer), `Prefs`, `Stats`, `Remote`, `Sync`.

**Playback path.**

- Android: `Music` → `Engine` (`ExoEngine`) → ExoPlayer → audio processors (`PeakRepairProcessor`, `EqProcessor`, `ResampleProcessor`) → `EqSink` → `UsbAudioSink` → either Android's `AudioSink`, or `UsbDirect` → JNI → `usbaudio.c` (mutex-guarded ring, 64 one-millisecond isochronous URBs, feedback endpoint) → DAC.
- Desktop: `Music` → `MpvEngine` → mpv over a named pipe. EQ and effects are an mpv `lavfi` graph; bit-perfect is `audio-exclusive` plus `ao-reload` (`MpvEngine.kt:313–326`). Crossfade uses a second mpv (`tail`).
- One song is loaded at a time. Between songs `Music.advancing` makes the media session report BUFFERING so Android keeps the foreground service (`CLAUDE.md`, `Music.kt:3066–3079`).

**Current flow, user action to output.**

```
tap / headset key / remote command / media session
        │
        ▼
Music (Main thread): queue, index, dj, flags  ──► Compose reads mutableStateOf directly
        │  loadCurrent(): ++loadSeq, localOf(), saveState()
        ▼
Music.bg (IO): Downloader.stream / download ──► Tidal / YouTube / file
        │  result dropped if loadSeq moved on
        ▼
Engine.load ──► ExoPlayer chain ──► AudioSink or UsbDirect ring ──► DAC
            └─► mpv pipe ──► WASAPI shared or exclusive

DJ: Music.djFillSpares ─► Music.bg ─► Dj.build (IO: Stats, Last.fm, Deezer, radios)
        ─► Dj.Built ─► Spoken.humanize ─► Voice.prepare (Android only; no-op on PC)
set starts: Music.djAnnounce ─► djSay ─► Voice.say
        Android: Kokoro (one worker thread) ─► AudioTrack, or UsbDirect.speak ─► mixed in UsbAudioSink.convert
                 else Android TTS (speak, or synthesizeToFile then DAC)
        PC:      sherpa-onnx .exe per line ─► WAV ─► javax.sound Clip; else SAPI via PowerShell
        ducking: Music.djDuck (player volume, 8×40 ms) or UsbAudioSink per-sample ramp
```

**Platform differences found in code.**

| Concern | Android | Desktop |
|---|---|---|
| Voice made ahead | Yes: `Voice.prepare`, 8-entry cache, priority queue | No: `prepare` is empty; a process is spawned per line and loads the model each time |
| `onStart` (duck begins) | When audio begins | **Before synthesis** (`Voice.kt` desktop line 324) |
| Voice output | `AudioTrack` (routed by `BtRoute`) or mixed into the DAC stream | A separate `javax.sound` `Clip` on the default device |
| Bit-perfect | Only with a USB DAC; own USB driver when Android has no bit-perfect mode | WASAPI exclusive through mpv, whenever the switch is on |
| Voice choice | Natural or phone's own; natural can be removed | Always natural once fetched; no choice |
| Kokoro model | int8, about 130 MB, in-process | Full, about 400 MB, external executable |

### 1.2 Proposed target architecture

Everything in this subsection is a proposal, not a finding.

**Language split (D1, decided by the owner: Kotlin-first).** The app stays one Kotlin codebase (Compose Multiplatform) for Android, Windows and Linux. The rebuild is a restructuring of the existing Kotlin into the services below, not a rewrite in another language. Native code is limited to one small module, `radon-audio-native` (Rust, loaded through JNI on all three platforms), for work that runs sample by sample. The reasons, from the code:

- The shared logic and UI already compile and run on all three platforms (`PLAN.md`: "tested on Linux"; CI builds a Linux jar).
- Set planning, stats, sync, tagging and the streaming services are bound by network and disk, not by the language. Nothing read in that code is CPU-limited. This is a judgement from code, not a measurement.
- A second language for logic would mean rewriting about 2 MB of working Kotlin and crossing a language boundary for every piece of UI state, with parity risk in every feature and no expected gain outside audio.
- The sample-level audio is the exception: the USB mix runs in Kotlin on the playback thread today (`UsbAudioSink.convert`), where a garbage-collection pause can glitch, and the existing C file has had use-after-free crashes (its own comments, `usbaudio.c:54–63`).

Binding constraints: no logic outside sample-level audio goes into the native module; the native module has no knowledge of tracks, sets or settings; it is small enough to review in one sitting.

**Services and ownership.** Each domain has exactly one owner; everything else reads snapshots or sends commands. All are Kotlin packages in the existing source layout (`shared/` for pure logic with tests, `core/common/` for services, `core/android` and `core/desktop` for platform sides) unless marked native.

| Service | Owns (authoritative state) | Interface | Replaces |
|---|---|---|---|
| `model` | `Track`, `TrackKey` (both `songKey` and `statsKey`), the existing JSON keys | Types only | `Track.kt`, key functions in `Songs.kt` |
| `store` | Every persisted file and its schema version; atomic writes | `load<T>()`, `save<T>()`, debounced writers | `Stores.kt`, `Prefs`, per-feature JSON files |
| `PlaybackCore` | Queue, index, repeat/shuffle/autoplay, load epoch, advancing, crossfade state, saved queues | Single-threaded actor (coroutine with a `Channel` mailbox): `Command` in, `StateFlow<PlaybackSnapshot>` out | Queue and playback parts of `Music.kt` |
| `AudioEngine` | Output spec, gain stages, duck requests, voice submission | `StateFlow<OutputSpec>`; `submitVoice(ProcessedVoice)`; calls into the native module | `djDuck`, the volume writes in `play()` and crossfade |
| `radon-audio-native` (Rust, JNI) | DSP state, the mix into the USB stream, the USB ring and URB loop | `processVoice(mono24k, spec)`, `usbStart/Write/Stats/Stop`, `mixVoice(handle, frames)`, stall event | `usbaudio.c`, the mixing and conversion in `UsbAudioSink.convert`, `Tones`, linear resampling in `UsbDirect` |
| `UsbDirect` (Kotlin, Android) | DAC permission, descriptors, claim, alternate setting, clock, volume | Unchanged role; delegates streaming to the native module | The non-streaming parts of today's `UsbDirect.kt`, kept |
| `TtsService` | Engine choice, model files, synthesis queue | `synth(text, priority): MonoMaster24k`; `engines()`; `install/remove` | Both `Voice.kt`, `DjVoices.kt` |
| `SpeechPrep` | Nothing; pure | `prepare(Script, VerifiedMeta): SpokenText` | `Spoken.kt`, `Pronounce.kt`, `djClean`, the text stripping in both `Voice.kt` |
| `DjDirector`, `SetPlanner`, `ScriptWriter` | DJ on/off, spares, cold-start set, kind/fact/line histories, `DjSet` lifecycle | Actor; consumes `PlaybackSnapshot` and `TasteSnapshot`; emits `QueueSets` commands | `Dj.kt`, DJ part of `Music.kt` |
| `TasteService` | Merged listening history, counts, last-played, mood plays | `snapshot(): TasteSnapshot` (immutable) | `Dj.loadTaste`, parts of `Stats`, `Wrapped`, smart shuffle weights |
| `net` | HTTP client (OkHttp, as now), per-host rate limits, in-flight request de-duplication | `getJson(key, request)` with single-flight | `Http.kt`, raw `HttpURLConnection` use |
| `Sync`, `Remote` | Pairing, merge, undo copy | Existing code, moved behind the store and `PlaybackCore` commands | Same files |
| UI state | Nothing | Per-screen `StateFlow` slices derived from service snapshots | The direct `Music.x` reads from Compose |

**Decoding and output.** Unchanged infrastructure, because there is no parity or performance case for replacing it: Android keeps ExoPlayer and its processor chain (`EqProcessor`, `PeakRepairProcessor`, `ResampleProcessor`); desktop keeps mpv and its filter graph on Windows and Linux. On Android's USB route, `UsbAudioSink` hands each PCM buffer to the native module with one JNI call (it already makes one per buffer for `UsbNative.write`). On desktop, the voice plays on its own shared-mode line (the existing `javax.sound` path, which works on Linux and Windows) fed with processed PCM, and mpv's volume is ramped by timed commands. Mixing the voice into a WASAPI-exclusive or ALSA-direct stream would need the app to own desktop output; that stays out of scope (D3, C-16).

**Target flow.**

```
UI (Compose) ──────command─────────────────► PlaybackCore actor ──snapshot──► UI
                                              │            ▲
                               Load(track) ───┘            │ QueueSets / SwitchSet
                                              ▼            │
                       Resolver (store, net) ─► Engine     DjDirector actor
                                              │            │  uses TasteSnapshot, PlaybackSnapshot
                                              ▼            ▼
                                   AudioEngine ◄── ProcessedVoice ◄── DjVoiceProcessor ◄── TtsService ◄── SpeechPrep ◄── ScriptWriter
                                   (native on USB: ring read, gain ramp, add voice, format)
                                              ▼
                                   OS mixer  /  native USB ring ─► DAC
```

**Authoritative-state rules.**

- **R-S1.** The queue and index change only inside `PlaybackCore`. The DJ never edits the queue; it sends `QueueSets` or `SwitchSet`, each carrying the `queue_epoch` it was planned against. A stale epoch is rejected and the DJ replans.
- **R-S2.** UI state is a projection of snapshots. No UI code reads a service's fields.
- **R-S3.** Settings have one writer (`store`) and are published as a watch value; services never cache a setting beyond one snapshot.
- **R-S4.** Histories that mean "this was played or said" are committed only when the thing is actually played or said (rule R-D4).

### 1.3 The `DjSet` record and its lifecycle (rule R-D1)

```kotlin
class DjSet(
    val id: Int,                         // the existing Track.dj numbering, kept for saved state
    val kind: String,                    // existing kind string: "repeat", "mood:Sad+Dark", ...
    val setType: SetType,                // TIME_AND_MOOD | NOSTALGIA_PULL | SONIC_BRIDGE | FRESH_DROP
    val tracks: List<Track>,             // final order, after arc/spread/localsOf/filters
    val script: Script,                  // segments, claims, display line (section 2.4)
    val plannedAtMs: Long, val plannedEpoch: Long,
    val pendingCommits: List<HistoryCommit>,   // applied only on Announce (R-D4)
) {
    var voice: VoiceAsset? = null        // written only by DjDirector
    var state: SetState = SetState.PLANNED   // PLANNED → SCRIPTED → VOICED → QUEUED → ANNOUNCED | DROPPED
}
class VoiceAsset(
    val engine: TtsEngineId, val textHash: Long,
    val monoMaster: List<FloatArray>,    // 24 kHz mono, unprocessed, one array per segment
    var processed: ProcessedVoice?,
)
class ProcessedVoice(
    val spec: OutputSpec,                // rate, channels, sample format
    val chainVersion: Int,               // bump when DSP constants change
    val frames: java.nio.FloatBuffer,    // direct buffer: interleaved stereo at spec.rate, limiter applied
)
```

Processed audio is held in direct buffers so the native module reads it without copying and the garbage collector never moves it. `ProcessedVoice` is valid only while `spec == AudioEngine.outputSpec` and `chainVersion` is current. When either changes, `DjDirector` re-runs `DjVoiceProcessor` from `mono_master` for every set in `Voiced` or `Queued` (rule R-A4). Format conversion to the stream's integer format happens at mix time (rule R-A5), so one `f32` buffer serves 16-bit, 24-bit and float streams.

---

## 2. Complete feature and utility inventory

Legend: **C** code read, **D** documentation only. "Parity" means the rebuild must reproduce the row exactly unless a rule in section 3 changes it.

### 2.1 Playback core (C, partial)

| Item | Observed behaviour and evidence | State and transitions | Edge cases and flaws | Parity constraint |
|---|---|---|---|---|
| Queue and "your copy first" | `loadCurrent` replaces the queue entry with `localOf(c)` before playing (`Music.kt:3370–3376`) | `queue`, `index`, `loadSeq` on Main | Rewrites the whole immutable list per change | Same substitution, same order of precedence (`best_copy`: same album, then FLAC, per `CLAUDE.md`) |
| Load and stale-result guard | `seq = ++loadSeq`; background result dropped if `seq != loadSeq` (3387–3398) | Idle → Resolving → Loaded → Playing | Failure with autoplay: waits 1.5 s then `next()` | Keep both the guard and the 1.5 s skip |
| End of song | `engineEnded` ignored while advancing to another id (3274–3277); `trackEnded` sets `advancing`, counts heard-out, handles Repeat one | Playing → Advancing → Loaded | Stream that ends more than 5 s early is treated as broken, retried once (`streamBroke`) | Keep |
| End watchdog | `endWatch` every 500 ms: stuck 2.5 s within the last 1.5 s, or 1.5 s past the end with no audio, or audio drained for 1 s (315–349) | Timers on Main | Writes a Problems note | Keep thresholds |
| `next()` precedence | Next in queue → DJ's next set → Repeat all (reshuffle) → next saved queue → Autoplay radio → stop (3016–3039) | — | — | Keep the order exactly |
| Previous | Under 10 s goes back; otherwise restarts and re-counts the listen (3044–3052) | — | Not counted as a skip (`goingBack`) | Keep |
| Skip counting | Skip only if at least 1 s played, song longer than twice `SKIP_LEFT`, and not within `SKIP_LEFT` of the end (3055–3064) | `SongStats` | — | Keep |
| Crossfade | Second engine prepared 5 s early, silent start, `alignTail` to within 40 ms, equal-power 7-step swap, then fade (3081–3231). Off for bit-perfect, Repeat one, remote, cast, streams, while ducked, same album with Smart transitions, before a new DJ set or a DJ comment | `tail`, `fadingId`, `fadingOut`, `crossTo`, `fadeIn` | Five interacting fields on Main; 1.8 s give-up timer | Keep every exclusion; consolidate into one `Crossfade` state enum |
| DJ never stalls | `djKeepGoing`: up to 8 checks, 5 s apart, restarts if the player sits ended or idle (3294–3310) | — | Writes a Problems note | Keep |
| Position tick | 500 ms loop updates position, listen time, duration, crossfade, end watch (288–302) | — | Polling on Main | Keep semantics; drive from engine events plus a 500 ms tick |
| Android foreground rule | `advancing` → session reports BUFFERING (`MusicService.queueAware`, not read) | — | **D** for the service side | Must be characterized on a device with the screen off before any change |
| Cast, Remote, saved queues, radio, per-song speed/volume/EQ, ReplayGain | Present in `Music.kt`, `Remote.kt`, `Chromecast.kt`, `CastSetup.kt` | — | **D** | Phase 0 characterization |

### 2.2 Sound (C for USB and voice mixing, D for the rest)

| Item | Observed behaviour and evidence | Flaws or uncertainty | Parity constraint |
|---|---|---|---|
| EQ, Boost, tilt, balance, crossfeed, mono, detail, clip guard (limiter, 80 ms release), dither, loudness compensation | `EqProcessor.kt` on Android; mpv `lavfi` graph on desktop (`MpvEngine.applyGraph`) | Two implementations of one feature; desktop re-evaluates `applySound()` every 300 ms (`MpvEngine.kt:73`). **D** for the maths | Both kept (no case for replacing either). Add a cross-check test that the two produce the same frequency response for the same settings; rebuild the mpv graph on change instead of polling |
| Declipping ("Smooth distortion") | `Declip.kt` (shared, 7 tests), `PeakRepairProcessor`, 5 ms lookahead | — | Port with its tests |
| HQ resampling | Android: 48-tap Kaiser (beta 8) windowed sinc (`ResampleProcessor.kt:14–59`). Desktop: swresample options | — | Reuse this kernel family for the voice resampler |
| Per-output EQ, AutoEq, song EQ, DAC PEQ (FiiO KA17 over USB/HID) | `Eq.kt`, `AutoEq.kt`, `SongEqs.kt`, `DacPeq*.kt` | **D** | Phase 0 |
| Bit-perfect to USB DAC (Android) | `UsbDirect`: claims every audio interface, picks the deepest alternate setting, sets and reads back the clock, 400 ms ring, stream watch at 100 ms (restart if no URB returns for 150 ms), two-failure cut-off, takeover and release settling delays (3.5 s, 150 ms) | Volume is digital gain (`digitalGain`) unless at full, so it is bit-perfect only at full volume, as the UI note says (`UsbDirect.kt:355–357`) | Every delay and retry count is device-tuned: carry them over unchanged and keep the Problems reports |
| Bass and Treble with bit-perfect | `Tones` in `UsbAudioSink.kt`: two shelves in double precision with pre-attenuation | Already "not bit-perfect" by its own Prefs comment | Keep |
| Bit-perfect (desktop) | mpv `audio-exclusive`, turned off automatically when a device is unplugged (`MpvEngine.kt:151–169`) | DJ voice conflict C-16 | Keep |
| Output routing | `BtRoute` (ignore Bluetooth devices, newest wins, car wins), `AutoMono`, `IgnoreBt` | **D** | Phase 0 |
| Visualizer tap | `AudioTap.push` per frame from both sinks | Called per sample frame from the playback thread | Unchanged on the standard path; on the USB path fed by the native mixer through a lock-free ring, decimated |

### 2.3 The DJ: sets (C)

Common rules, all in `Dj.kt`: sets of `SET = 6` songs; a set needs at least 3 songs; `SONG_GAP = 300` (no DJ-played song returns within 300 songs, window shrinks for small scopes, persisted in Prefs `dj_recent_songs`); kinds rotate by least-recently-played (`kindsToTry`), a kind that failed is deprioritized for 10 minutes (`dead`); every set is ordered by `arc` (mood energy) and `spread` (no artist twice in a row, at most two per set except artist sets); album sets keep album order; a fact is added to 40% of non-first sets; `timeFlavor` prefixes 30% of non-first sets.

| Existing kind (display name) | Selection (function, lines) | Eligibility and fallback | Canonical type |
|---|---|---|---|
| first-set hello (`hello`, 124–180) | Day-specific half the time, else by hour | Always available | `time_and_mood` |
| `hour` (This time of day) | Plays within ±1 hour of now over a year (570–587) | ≥3 songs | `time_and_mood` |
| `mood:*` ×7 (… songs) | `setMoodMix` with a group; known songs first, up to 2 new via radios and similar artists (689–750) | ≥2 known and ≥3 total | `time_and_mood` |
| `repeat` (On repeat) | Top of last 30 days (503–517) | ≥3 | `time_and_mood` |
| `streak` (Streaks) | Played on ≥4 of the last 7 days (1421–1432) | ≥3 | `time_and_mood` |
| `throwback` | A past year's top songs not played for 60 days (520–546) | Year needs ≥30 plays | `nostalgia_pull` |
| `rediscover` | ≥3 plays, none for 90 days (549–567) | ≥3 | `nostalgia_pull` |
| `yearago` (On this day) | Same week, 1–15 years ago (1307–1320) | ≥8 plays that week | `nostalgia_pull` |
| `decade` (Decade trip) | Own files by year tag, rotating decades (1324–1350) | ≥20 songs, decade needs ≥8 | `nostalgia_pull` |
| `mostplayed` | Top 150 with plays, shuffled (754–765) | ≥3 | `nostalgia_pull` |
| `neverskip` | Heard out this month, zero skips (1367–1390) | ≥3 | `nostalgia_pull` |
| `album` (Album moment) | Three consecutive tracks of a much-played album (1282–1304) | ≥4 songs, ≥3 with track numbers | `nostalgia_pull` |
| `spotlight` | One artist with ≥5 songs: hits plus deeper cuts (1252–1279) | ≥4 songs | `nostalgia_pull` |
| `onehit`, `second`, `unreleased` | 1353–1418 | ≥3 (onehit needs ≥6 candidates) | `nostalgia_pull` |
| scope kinds `loved`/`mostplayed`, `throwback`, `artist`, `mix`, `hour` (`fromSet`, 1523–1605) | From the scoped songs only | `least = min(3, available)` | `nostalgia_pull` (`hour`: `time_and_mood`) |
| `like` (More like this) | Radio of the current or a recent song (848–862) | Needs a service | `sonic_bridge` |
| `genre` / `genre-mine` | Own genre tags first, else Last.fm tags (825–882) | ≥3 | `sonic_bridge` |
| `ask` (You asked) | Mood, artist, genre, tag, service mix, in that order (1608–1687) | — | `sonic_bridge` (artist request with new songs: `fresh_drop`) |
| `fresh` (New for you) | Radios of two recent songs, unknown songs only (783–805) | Needs a service | `fresh_drop` |
| `deep` (Deep cuts) | Top artists' songs you have not played (807–823) | ≥3 | `fresh_drop` |
| `discover` (Artists for you) | Similar artists (Last.fm, else Deezer), two artists (1196–1219) | ≥3 songs | `fresh_drop` |
| scope kind `unplayed` | Zero plays anywhere (1553–1561) | — | `fresh_drop` |

The four canonical types are **framings**, not new selectors: each existing kind keeps its selector, its history key, its display name in `KINDS`, and its place in rotation. `SetType` chooses the script family and the claim gates. No kind is added or removed.

**Canonical type contracts.**

| | `time_and_mood` | `nostalgia_pull` | `sonic_bridge` | `fresh_drop` |
|---|---|---|---|---|
| Trigger | First set; kinds above | Kinds above | Kinds above; also chosen as the framing for any set whose first track differs from the last played track in mood group, genre or tempo (see gates) | Kinds above |
| Required context | Local time at **announce**; recent energy = dominant mood group of the last 40 minutes of plays, if ≥5 plays have a mood | `last_played`, `count`, `first` from `TasteSnapshot` | Last played track and next track, both with at least one shared or contrasting verified attribute | An anchor artist the listener loves; a track that is unplayed or new |
| Exclusions | No day claim outside the windows in `dayHello`; no energy claim without the 5-play minimum | No "months back" unless `last_played` exists; no time-of-day claim unless the track is in `taste.hour` | No producer claims (no such metadata exists); no era claim unless both have a year tag; no tempo claim unless both have `bpm > 0` | Never "new release" or "just out" unless the track came from `NewReleases` with a date in range |
| Fallback | Time only, no energy clause | Plain "one from the shelf" line with no number or date | The kind's neutral line without a thread claim | "New to you" framing; if no loved anchor, the kind's neutral line |

**Definitions from existing data.**

- **Unplayed**: `playsOf == 0 && lastPlayed == 0 && statsKey ∉ playedHere()` (the test `fromSet` already uses, `Dj.kt:1555`).
- **New to you**: `newToYou(t, known(taste) + used)` (`Dj.kt:478–481`).
- **New (release)**: a `NewReleases.Release` whose `date` is inside the user's chosen range (`RANGES`: week, month, year). Missing signal: `Track` has no release date, and radio results carry none, so nothing else may be called a new release. `NewReleases.kt` was not read beyond its declarations; wiring it into the DJ is new work and uses streaming services only (`CLAUDE.md`).
- **Loves (artist)**: `artistAll[artist] ≥ 20` (the threshold `setSpotlight` and `artistRank` already use), or `artistMonth[artist] ≥ 10`, or the listener starred one of the artist's songs (`Music.favorites`). This combination is a proposal built from existing thresholds.
- **Tempo and brightness**: `SoundFeatures.bpm` exists (`SoundMood.kt:87`). Whether it is stored per song after a mood scan was not verified. Until Phase 0 confirms a persisted value, tempo gates evaluate false.

**Dead or apparently unused DJ code.**

| Code | Status | Evidence |
|---|---|---|
| `setFavorites` (768–781) and its 7 lines | Confirmed unreachable | `private`, no caller; `regularSet` has no `"favorites"` case |
| `pickMood` (591–601) | Confirmed unreachable | `private`, no caller |
| `moodIntro` branches Happy, Energetic, Party, Hype, Chill, Calm, Sad, Dark, and `else` | Confirmed unreachable from `build` | `setMoodMix` is only called with a group; single-mood groups are only Dreamy, Focus, Romantic, Intense |
| `KINDS` entries `mood`, `moodmix`, `favorites` | Appear unused | May be read by saved state or the UI (not read): keep for decoding old state |
| ElevenLabs (`DjVoices.eleven`, `sayEleven`) | Disabled, kept on purpose | `elevenOn = false` with the comment "the ElevenLabs code stays, unused" |
| `Dj.From` kinds other than `library` | Appear unused at runtime | `djStart` and `djWarm` always use `DJ_LIBRARY`; saved state and Remote may still carry others |

Do not delete any of these until a parity check confirms no saved state, sync payload or UI path reads them. Their lines are still catalogued.

### 2.4 The DJ: lines, speech and voice (C)

**Line catalogue.** `DJ_LINES_CATALOG.md` lists every spoken literal in `Dj.kt` with its source line, bag key, flags and disposition: 264 keep, 3 light edit, 85 rewrite. Limits of that catalogue, stated plainly: it is mechanical; lines built from several literals appear as fragments (for example line 140); "keep" means "passed the pattern checks", not "editorially approved"; unreachable lines are included. Spoken text outside `Dj.kt`: `DjVoices.SAMPLE` (rewrite: it says "it's your DJ") and the fillers in `Spoken.FILLERS` ("Alright, ", "Okay so, ", "So, ", "Right, ", "Okay, ": keep).

**Disposition rules applied.**

| Rule | Lines affected | Action |
|---|---|---|
| Names itself ("It's your DJ", "Your DJ here") | All 26 hello lines, `SAMPLE` | Rewrite: the host never names the role |
| Banned phrase | `from-mix` line 1599 ("Up next, …") | Rewrite |
| Reads like a data report ("I've counted", "I'm keeping track", "I took notes", "I've been reading all of it", "I see you") | Facts at 1001, 1023, 1032, 1036, 1038 and similar | Rewrite as an observation |
| Cheesy or forced ("rent free", "whole personality", "Coffee is optional", "Candles optional", "tissues ready", "All killer, no filler", "Variety is the spice of life", "… alert!") | See flags | Rewrite |
| Source wording ("from your library", "from your music", "straight from your collection") | `genre-mine`, `decade`, `ask*`, `spotlight` | Normalize to "your collection" or "your records"; never name a service or "files" |
| Exclamation only | 3 lines | Replace with a full stop or ellipsis |

**Rewrites supplied now (openers and the highest-traffic bags).** Each stays under 20 words so a set line and one fact still fit the cap.

| Bag | New lines |
|---|---|
| `hello-friday` | "Happy Friday evening... you made it. Let's ease into the weekend." · "Friday night. Nothing to do now but listen." · "It's Friday, it's late enough... let's get into it." |
| `hello-weekend-morning` | "Slow morning, no clock to watch. Let's keep it gentle." · "Weekend morning... take your time. I've got the first few." |
| `hello-saturday-night` | "Saturday night. Let's give it some weight." · "It's Saturday, and the night's wide open." |
| `hello-sunday-night` | "Sunday evening... the quiet before the week. Let's stretch it out a little." |
| `hello-monday` | "Monday morning. We'll take it one song at a time." |
| `hello-morning` | "Morning. Let's start easy and see where it goes." · "Good morning... I've pulled a few to get you moving." |
| `hello-afternoon` | "Afternoon. Let's keep the day rolling." · "Good afternoon... here's something to carry you through." |
| `hello-evening` | "Evening. Settle in... I've got the next little while covered." · "Good evening. Let's wind it down the right way." |
| `hello-night` | "It's late... and you're still here. Good. So am I." · "Late one tonight. Let's keep it low and keep it going." |
| `repeat` (replace 513, 516) | "You've been wearing these out lately... no reason to stop now." |
| `throwback-plays` (replace 531, 533, 536) | "Going back to {year}. You had {title} on constantly... {n} times that year." |
| `rediscover` (replace 562) | "Pulling one off the shelf you haven't touched since {month}... {title}." |
| `fresh` (replace 799, 800) | "Something you haven't heard yet... it sits right next to {artists}." |
| `discover` (replace 1213, 1216, 1218) | "If {like} is your thing, give {artist} a few minutes." |
| `from-mix` (replace 1599) | "Back into {name}... {title}, by {artist}." |
| `fact-milestone` (replace 945–947) | "Small thing... this'll be play number {n} for {title}." |
| facts 1023, 1032, 1036, 1038 | "You do most of your listening around {hour}." · "{n} plays this month, and it's not over." · "That's {n} plays, all told." · "You've been at this since {month}... {years} years of records." |
| `SAMPLE` | "Hey. This is how I sound... not bad, right? Okay, back to the music." |

**Rewrites still owed.** The remaining lines flagged **R** in Appendix A (about 50 after the table above) are rewritten in Phase 8 under the same rules. They are tracked there line by line; none may ship unrewritten, and none is dropped.

**Speech preparation today.** `Spoken.humanize` (numbers to words, years, decades, AM/PM, contractions, clutter such as "(Remastered 2011)" removed, colons to full stops, optional filler) then, for Kokoro only, `Pronounce.sayable` (words missing from Kokoro's lexicon are respelt; all-caps names of 2–4 letters are spelt out). Tests: `SpokenTest` (6), `PronounceTest` (2), `NoRepeatTest` (7); `DjTest` (5) exists but CI never runs it (C-22).

**Voice generation today.** Preference `dj_voice` on/off; Android `dj_use_natural`; Kokoro voice fixed to `af_heart`. Android synthesizes ahead on one worker with a priority queue (a line needed now jumps ahead), caches 8 results by text, and falls back to Android TTS. Desktop synthesizes on demand in a child process and falls back to SAPI. Guards: a line not started within 15 s is dropped; a line running past `25 s + 90 ms × characters` is cut (`Music.kt:1219–1221`). Failures surface as a toast once per run and as `Voice.lastError` in Voice settings.

**Target speech pipeline (rule R-P1).** One boundary: `SpeechPrep::prepare` is the only producer of text for any TTS engine, and nothing downstream edits text.

1. `ScriptWriter` produces a `Script`: ordered `Segment`s, each `Frame` (no names), `Name { track_key }` (the only place a title or artist may appear), or `Fact`.
2. `SpeechPrep` normalizes each segment: existing `Spoken` rules, then a pronunciation pass.
3. Pronunciation order: user override table (new, persisted, synced) → built-in table (`Pronounce.SAID` extended: "AC/DC", "MGMT", "R&B", "feat.", "w/", "No. 1") → acronym rule → number rule → engine lexicon respelling (kept for engines that skip unknown words) → unchanged.
4. Acronym rule: an all-caps token of 2–5 letters is spelt as letters **only if** it has no vowel or is in the initialism list; otherwise it is lower-cased and read as a word ("NASA", "ABBA", "SZA" stay words). Mixed-case stylizations ("WRLD", "CHVRCHES", "BLK") go through the table or the existing `pieces`/`syllables` respelling. This is what stops accidental letter-by-letter reading.
5. Fallback: if a `Name` segment still contains a token that no rule can make speakable (no letters, or only symbols), that segment is replaced by the title-free variant; the track is never renamed or guessed.
6. Budget: the whole break is at most 45 words after normalization. Priority when over budget: drop `Fact`, then drop the `timeFlavor` prefix, then use the kind's short line. Target 35–45 words when a fact is present; a plain set line may be shorter.
7. Style lint (hard failures in tests): banned phrases; any service or source word ("Tidal", "Spotify", "YouTube", "SoundCloud", "Deezer", "local", "file", "stream", "download"); "DJ" as a self-reference; more than one exclamation mark; a sentence over 18 words.

**Title verification (rule R-D2).** At announce time `DjDirector` compares each `Name` segment's `track_key` with the track actually at the queue position being announced (both keys, after `localOf`). On a match, and only then, the title and artist text are taken from that track's current metadata. On a mismatch, or if title or artist is empty, "Unknown" or "Unknown artist" (the `Track` defaults), the `Name` segment is dropped and the frame is spoken alone. If the voice was pre-rendered with the name, the segment's audio is dropped the same way: segments are synthesized separately so this costs nothing at playback.

**TTS engines (rule R-T1).**

| Platform | Engines offered | Default | Setting |
|---|---|---|---|
| Windows, Linux | Chatterbox Turbo, Pocket TTS, in a dropdown in DJ › Voice settings | Pocket TTS (smaller download; proposal) | `dj_tts_engine` = `chatterbox_turbo` \| `pocket` |
| Android | Pocket TTS | Pocket TTS | none |

Neither engine is in the repository, and their runtimes, model sizes, licences, output sample rate and Android feasibility were **not verified** in this audit. Phase 6a is a spike that must confirm, before anything else depends on it: a runtime callable from Kotlin on Android, Windows and Linux for each (working assumptions, in order: sherpa-onnx, which the app already ships on Android and whose Java API also runs on desktop, if it supports these models; else ONNX Runtime's Java and Android packages), 24 kHz mono output or a defined conversion to it, real-time factor on a mid-range phone, and redistribution terms. In-process synthesis on desktop also removes today's process spawn per line. `TtsEngine` is an interface so the answer does not change the rest of the design:

```kotlin
interface TtsEngine {
    val id: TtsEngineId
    val status: StateFlow<EngineStatus>      // NotInstalled | Installing(pct) | Ready | Failed(reason)
    suspend fun synth(text: String): MonoMaster24k   // cancellable; throws TtsError
}
```

Migration of existing settings: `dj_voice` (on/off) is kept as is. `dj_use_natural = false` on Android currently means "use the phone's own voice"; with Android TTS replaced, that choice has no target (decision D4). Kokoro and Piper files in `dj-voice-kokoro/` and `dj-voice/` are removed only after the new engine has spoken successfully once, reusing the careful own-folder-only deletion in `removePiper`. Fallback chain when the chosen engine fails: the other installed engine (desktop) → no voice for that break, music untouched, one toast per run and the reason in Voice settings (as today). Whether the OS voice remains as a last fallback is part of D4.

### 2.5 Everything else (D unless noted)

Each row is taken from `FEATURES.md` and file names. For all of them: inputs, state, side effects and failure behaviour are **not yet audited**; the parity constraint is "characterize in Phase 0, then port unchanged".

| Area | Features | Main files | Tests today |
|---|---|---|---|
| Library | My music, Albums, EPs, Singles, Artists, Genres, Folders, list/grid layouts, per-page search and sort, pins, selection mode, moved-file following, "nothing is deleted without asking" | `ui/Library.kt`, `LocalFiles`, `Stores.kt`, `Sorting.kt`, `Names.kt`, `Songs.kt` | `NamesTest`, `SortingTest`, `SearchNormTest`, `ArtistsFolderTest`, `OwnArtistsTest` |
| Playlists | Create, sort, folders, m3u8 import/export, smart playlists, discovery playlists in pairs | `SmartRules.kt`, `SmartLists.kt`, `ui/SmartScreens.kt`, `Music.kt:1438+` | `SmartRulesTest`, `PlaylistFoldersTest`, `ImportTest`, `SyncPlaylistsTest` |
| Streaming | Tidal (FLAC, DASH), Spotify/Apple/Deezer/YouTube Music library import, SoundCloud (own queue, no downloads), YouTube audio fallback, link paste | `Tidal.kt`, `Spotify.kt`, `AppleMusic.kt`, `Deezer.kt`, `YouTubeMusic.kt`, `SoundCloud.kt`, `YouTubeAudio.kt`, `Source.kt` | `YouTubeAudioTest`, `LiveSearchTest` |
| Downloads | Tidal FLAC with tags and cover, quality tiers, clean/explicit, duplicate prompt, Upgrade to FLAC, "Missing from this album" | `Downloader.kt`, `DownloadSync.kt`, `QualityCheck.kt`, `FlacDepths.kt` | `ReleasePickTest`, `FlacFormatTest`, `EditionsTest` |
| New releases | Per-artist check over week/month/year, open on a service, discovery playlist | `NewReleases.kt`, `ui/Discovery.kt` | none |
| Moods | 12 moods from sound (energy, tempo, brightness, key) and lyrics, optional Claude reading with the user's key, MOOD tag written once both are done, resets, learning from fixes | `Moods.kt`, `SoundMood.kt`, `LyricsMood.kt`, `MoodRules.kt`, `MoodTag.kt`, `MoodLearn.kt`, `MoodWipe.kt`, `MoodBackup.kt`, `MoodTally.kt` | 9 mood test files, 60+ cases |
| Stats and Wrapped | Last.fm plus frozen imports, `statsKey` merging, periods, Repeat, Milestones, Skips, Wrapped slides and styles | `Stats.kt`, `LastFm.kt`, `*History.kt`, `Wrapped.kt`, `ui/WrappedScreen.kt`, `ListenLog.kt` | `StatsTest`, `SpotifyHistoryTest`, `AlbumPlaysTest`, `ListeningTest` |
| Lyrics | Synced, Genius with annotations, lyrics card (30 styles), scanner writing into files | `Lyrics.kt`, `LyricsScan.kt`, `LyricsTags.kt`, `LrcFiles.kt`, `ui/Lyrics*.kt` | `GeniusNotesTest` |
| Tags and tidying | Tagger with online lookup, file moves on rename, duplicates, covers, Identify by sound (Chromaprint port), genre fixes, folder cleanup, PC-only fixers | `Tagger.kt`, `TagScan.kt`, `TagMatch.kt`, `TagWrite.kt`, `Chromaprint.kt`, `Fixup.kt`, `FolderCleanup.kt`, `Flac.kt`, `OggOpus.kt`, `WavTags.kt`, `EmbeddedArt.kt` | 12 test files |
| Personalization | Looks, themes, layouts, 61 effects, presets, preset shuffle, makers, saved colours | `ui/Makers.kt`, `ui/Effects.kt`, `ui/Layouts.kt`, `ui/Previews.kt`, `ui-common/Theme.kt` | none |
| Visualizer | 60 styles, Dual, full screen on Now playing | `ui/VisualizerTab.kt`, `AudioTap.kt`, `PcSpectrum.kt`, `Spectrum.kt` | `SpectrumTest` |
| Phone + PC | Pairing with a code and mDNS, remote control, Sync now with undo, main-device tag matching, continue where you left off, backup and restore | `Remote.kt`, `RemoteHost.kt`, `Sync.kt`, `SyncMerge.kt`, `TagSync.kt`, `Backup.kt`, `PhoneShare.kt` | `SyncMergeTest` (12), `BackupMergeTest`, `TagChangesTest`, `TagKeepTest`, `PhoneShareTest` |
| Android only | Android Auto, lock-screen player, notification, widget, picture-in-picture, Auto mono, updater | `Car.kt`, `LockScreen.kt`, `PlayerWidget.kt`, `MusicService.kt`, `AutoMono.kt`, `Updater.kt` | none |
| Desktop only | Tray, media keys, SMTC helper, mini player, Discord presence, single instance on port 8896 with command-line verbs, Start with Windows, Python-app data import, dictation | `Instance.kt`, `MediaKeys.kt`, `MediaSession.kt`, `Discord.kt`, `Startup.kt`, `PythonImport.kt`, `desktop/Main.kt` | none |
| Help | Update check, Features and Changelog tabs (generated by `gen-guide.py`), Problems log with Copy | `Updater.kt`, `Crashes.kt`, `ui/FeatureGuide.kt`, `ui/GuideScreen.kt` | none |

---

## 3. Conflict matrix

Each resolution rule is stated once here and referenced by its ID elsewhere.

| ID | Conflict or risk | Evidence | Root cause | Consequence | Resolution rule and owner | Migration constraint | Verification test |
|---|---|---|---|---|---|---|---|
| C-1 | DJ line names a song that is no longer first, or not in the set | Line composed in `setX`, then songs filtered in `build` (`Dj.kt:1748–1750`) and again for "only my songs" in `djTag` (`Music.kt:1156`) | Text bound to a list that is edited afterwards | DJ announces a title that does not play | **R-D2** (verify at announce). Owner: `DjDirector`. Also: scripts are written after all filters, from `DjSet.tracks` | None | Property test: for random filter outcomes, every spoken title equals the track at the announced queue position |
| C-2 | "More like X" refers to an old song | `setLike` seeds from `Music.current()` at build time (`Dj.kt:849`); spares are built up to three sets ahead | Build-time context used at play time | Wrong reference | **R-D3**: a set whose script depends on "now" (current track, clock) stores that dependency; at announce the dependency is rechecked, and on mismatch the neutral variant is used. Owner: `DjDirector` | None | Build with track A current, announce with track B current: the name clause is absent |
| C-3 | Greeting or day wording is stale | `hello`, `timeFlavor`, `setHour` use the clock at build; cold-start set lives 30 minutes; spares never expire | Same | "Good morning" after noon; "Friday night" on Saturday | **R-D3**; the time clause is its own segment, chosen at announce from per-daypart variants synthesized ahead (current and next daypart only) | None | Fake clock crossing 11:59→12:00 and Friday 23:59→Saturday 00:00 |
| C-4 | Histories consumed by sets that never play | `remember(KIND_HISTORY…)` in `build` (`Dj.kt:1757`), `SPOT_HISTORY`, `MOODMIX_HISTORY`, `DECADE_HISTORY`, `FACT_HISTORY`, `introduced`, line bags: all written at build; `djMakeSpare` and `djStop` discard spares | Commit at plan time | Kinds, facts, milestones and lines are skipped without ever being heard (a 100th-play milestone can be lost) | **R-D4**: plan-time picks go into `DjSet.pending_commits`; applied on `Announced`; discarded on `Dropped`. Planning excludes the pending commits of live spares. Owner: `DjDirector` | Existing Prefs keys and encodings kept | Build three spares, drop them, rebuild: the same milestone is offered again; play them: histories match today's format byte for byte |
| C-5 | DJ scope is a global changed during builds | `Dj.scope`, `withNew`, `whole` set on Main by `djWarm`/`djStart` while `build` on IO reads `scope` twice (`Dj.kt:1720, 1725`) | Shared mutable globals | A set built half from one scope | **R-D5**: `SetPlanner::plan(&PlanInput)` is a pure function of an immutable input (scope, flags, `TasteSnapshot`, `used`, `coming`, clock). Owner: `DjDirector` | None | Loom or stress test: concurrent scope change never yields a mixed set |
| C-6 | Duplicate and overlapping set builds | `djWarm` every 10 minutes, `djFillSpares`, `djMore`, `djSwitch` each launch `Dj.build`; guards are three separate flags (`djWarming`, `djSpareBusy`, `djBusy`) plus `djSpareJob` | No single scheduler | Repeated Last.fm, Deezer and radio calls; `loadTaste` is `@Synchronized` so builds queue behind each other | **R-D6**: one planning queue with a concurrency of 1 for background fills and one pre-empting slot for user actions (switch, ask). Owner: `DjDirector` | None | Count network calls for start + three spares; assert no identical request twice (via `net` single-flight log) |
| C-7 | Desktop ducks before the voice exists | `onStart()` is called before synthesis (desktop `Voice.kt:324`) | Desktop has no "audio began" signal | Music sits at 25% in silence during synthesis | **R-A2**: ducking is driven only by the audio engine at the first voice frame. Owner: `AudioEngine` | None | Offline render: the first sample of the gain ramp coincides with the first voice frame (±1 buffer) |
| C-8 | Two ducking mechanisms with different shapes | `djDuck` (8 volume steps over 320 ms) and `UsbAudioSink` (per-sample ramp, slope `1/(0.12·fs)`) | Two code paths | Zipper steps on the standard path; different timing by route | **R-A6**: two named envelopes, both per-sample and linear in amplitude. `Standard`: 1.0→0.25 in 320 ms, back in 320 ms. `UsbDirect`: slope `1/(0.12·fs)` per frame, so 90 ms down and 90 ms up. Never merged. Owner: `AudioEngine` | None | Golden envelope tests for both, at 44.1, 48 and 96 kHz |
| C-9 | Crossfade and ducking fight over one volume | `maybeCrossfade` checks `ducked` (`Music.kt:3103`); `play()` sets `p.volume = DUCK` (3474); crossfade loop writes `player.volume` | One `volume` field, several writers | A fade can overwrite the duck or the reverse | **R-A3**: final gain = `master × crossfade × duck`, three independent smoothed stages inside `AudioEngine`. Existing exclusions (no crossfade while ducked or before a DJ set) are kept as behaviour. Owner: `AudioEngine` | None | Start a duck during a fade in a simulation: both curves are present in the output, product only |
| C-10 | Voice cache keyed by text with arbitrary eviction | `Voice.ahead` (`ConcurrentHashMap`), "last eight", evicts `keys.firstOrNull()` | Cache separate from the set | A needed line can be evicted; same text for two sets shares one entry | **R-D1**: audio lives in its `DjSet`; dropped with it. Mid-set comments get their own small `DjSet`-like record | None | Drop a set: its buffers are freed (allocation counter) |
| C-11 | Voice mixed with linear interpolation, unfiltered, can hard-clip | `UsbDirect.speak` 494–505; sum then `coerceIn` (`UsbAudioSink.kt:153`) | No voice DSP | Aliasing; clipping when loud music and voice peaks coincide | **R-A1** (chain) and **R-A7** (headroom). Owner: native module, `AudioEngine` | None | Section 5 DSP tests |
| C-12 | Voice silently cut when the stream rate changes | `if (voice != null && UsbDirect.voiceRate != rate) voice = null` (`UsbAudioSink.kt:135`) | Voice rendered for one rate | Line stops mid-word at a track change | **R-A4**: on an `OutputSpec` change the remaining voice is re-rendered from `mono_master` at the new rate and resumes at the same source position after a 10 ms fade | None | Change spec at 40% of a line: output contains the full line; no discontinuity above the click threshold |
| C-13 | Voice can set up or flush the DAC stream | `speakAlone` calls `begin0(…48000, 2, 24)` when no stream exists and `UsbNative.flush(h)` always (`UsbDirect.kt:536–548`) | Voice-alone path predates mixing | A voice-initiated hardware setup and a flush of queued music | **R-U1**: the voice never calls stream setup, alt-setting, clock or flush. With no active stream it waits for the next track's stream until the existing 15 s start guard, then is dropped. Owner: the native module exposes no such call to the voice path | Behaviour change for "DJ before the first song" on USB: decision D6 | Trace test: zero control transfers and zero flushes between voice submit and voice end |
| C-14 | `begin()` waits only for `speaking`, not for a mixed voice | `waitSpeech` checks `speaking` (set only in `speakAlone`) | Two voice modes | Next track's rate switch proceeds mid-line (leads to C-12) | Covered by R-A4; `waitSpeech` is removed with `speakAlone` | None | Same as C-12 |
| C-15 | USB ring shared under a mutex with the URB thread | `fill()` locks `s->mu` (`usbaudio.c:84`); `write`, `pending`, `flush` take the same lock | Simple design | Priority inversion can starve the URB loop; the 64 ms queue hides most of it | **R-U2**: SPSC lock-free ring; `paused`, `muted`, `gen` stay atomics; the URB thread never blocks | Keep `NURB = 64`, 1 ms packets, feedback handling, `outstanding` accounting | Stress: writer at low priority, URB thread at audio priority, zero silent packets over 30 minutes on a device |
| C-16 | Desktop bit-perfect and the DJ voice | `djDuck` returns early when `bitPerfectNow()` (true on PC whenever the switch is on); voice plays through a separate `Clip` while mpv holds the device exclusively | Separate output streams | Code-evident: no ducking and a voice that is probably inaudible or blocked in exclusive mode. Not verified on hardware | **R-A8**: on desktop, while exclusive mode is on, the DJ shows its line as text and does not speak, unless the app later owns desktop output (D3). Owner: `DjDirector` | Decision D7 | Windows: exclusive on, DJ on: no second stream opened, line shown |
| C-17 | Stall watchdog fallback differs from the brief | After 4 s without progress the code sends the rest to the DAC alone (`UsbDirect.kt:512–521`) | Brief describes another fallback | — | **R-U3**: stall = voice read position unchanged for 4,000 ms while the sink is playing. On stall: cancel the mixed voice, ramp the duck back up, and play the remainder through the platform route (speaker or headphones) if one exists that is not the claimed DAC; otherwise drop it. Owner: `AudioEngine` | Decision D5 (voice from the phone speaker may be unwanted) | Fault injection: stop consuming the ring; fallback fires between 4.0 and 4.1 s |
| C-18 | "Bit-perfect" while mixing and resampling | Feature text says bit-perfect "too"; mixing multiplies and adds samples | Terminology | A false claim if taken literally | **R-U4**: contract below (section 3.1) | UI wording change: decision D8 | Null test: with no voice active and volume at full, output equals input bit for bit; with voice active the status reads "Direct" not "Bit-perfect" |
| C-19 | Queue edited from many places | `queue = queue + songs` in `djMore`; replaced in `djSwitchTo`, `loadCurrent`, `shuffle`, `next` | No single writer | A set appended after the user switched context (guarded by `ctxNow == context` only in one path) | **R-S1** | Saved state format kept | Epoch test: a late `QueueSets` with an old epoch is rejected |
| C-20 | Track identity has two keys | `songKey` and `Stats.statsKey` are both checked almost everywhere (`isUsed`, `inScope`, `newToYou`) | History matching vs library matching | Easy to forget one; sets rebuilt per call (`known(taste)` unions five collections) | **R-M1**: `TrackKey { song, stats }` computed once per track; `KeySet` answers "either matches" | `dj_recent_songs` format kept | Port `NamesTest`, `StatsTest`; equivalence test against Kotlin outputs |
| C-21 | Taste cache | `loadTaste` rebuilds everything every 10 minutes under a lock; `lastHeard` and `moodPlays` call it from UI paths | Time-based invalidation | Long blocking rebuilds; stale for up to 10 minutes after a play | **R-T2**: `TasteService` holds an immutable snapshot, rebuilt in the background on a version change (new scrobbles, import, library change) or after 10 minutes; readers never block | None | Readers during a rebuild get the previous snapshot; rebuild count per trigger = 1 |
| C-22 | Tests that never run | CI runs only `:shared:test` (`next.yml`); `app/src/test` (20 files, including `DjTest`) is not run; `result.txt` shows 37 suites, matching `shared/` only | Workflow | Regressions in those areas go unseen | Phase 0 adds `:app:testReleaseUnitTest` to CI before anything else | None | CI result lists 57 suites |
| C-23 | Prefs written from background via Main hops | `Dj.keep` launches on `Music.scope` per line; `say` rewrites every bag on each line | No store layer | Many small writes; ordering not guaranteed | `store` debounced writer (R-S3) | Same keys and encodings | Kill-and-restart test: state equals last committed |
| C-24 | Same feature, different behaviour by platform | Voice made ahead (Android only); voice choice (Android only); Kokoro speed flag retry (desktop only) | Two `Voice.kt` files | "Instant" DJ is not instant on PC | One `TtsService`; made-ahead on both | None | Desktop: time from set start to first voice frame under one buffer when voiced |
| C-25 | Release identity | `applicationId = "org.omarchy.music"` in Gradle, but the shipped phone app must be `org.omarchy.music.next` with the test key (`CLAUDE.md`) | CI rewrites it | A rebuild under the wrong id cannot update the owner's install | Rebuild keeps package id, signing key, MSI `upgradeUuid` (`8f3b3a52-…`), data directory names and Prefs store name `radon-2` | Hard constraint | Install-over test on a device and on Windows |

### 3.1 The bit-perfect contract (rule R-U4)

Bit-perfect means the DAC receives the file's sample values unchanged. Mixing a voice multiplies the music by a gain and adds another signal; software resampling of the voice is harmless to the music, but the sum is not the source. So the two cannot be true at the same instant. The contract:

| Mode | When | Guarantee | UI status text |
|---|---|---|---|
| **Direct, bit-exact** | USB direct output, no voice or duck active, phone volume at full, Bass/Treble at 0 dB | Output samples equal source samples (padded to the DAC's slot size) | "Bit-perfect" |
| **Direct, mixed** | Same stream while a DJ break is audible, including its two ramps | No hardware rate switch, no alternate-setting change, no stream reset or flush; music samples are scaled and summed with the voice in software at the stream's own rate | "Direct to DAC (DJ speaking)" |
| **Direct, scaled** | Volume below full or Bass/Treble active (already the case today) | Same stream guarantees; samples scaled | Existing wording |

The stream returns to bit-exact on the first sample after the up-ramp ends; a test asserts that. Offering a "never alter samples" switch that silences the DJ on this route is decision D8.

---

## 4. Consolidation and performance plan

### 4.1 Duplicated responsibilities and their single owner

| Duplicated today | Where | Single owner | Input → output |
|---|---|---|---|
| Voice synthesis, caching, fallback, hush | `core/android/Voice.kt`, `core/desktop/Voice.kt` | `TtsService` | text, priority → `MonoMaster24k`; cancel token |
| Ducking | `Music.djDuck`, `UsbAudioSink.convert`, `play()` line 3474 | `AudioEngine` gain stage | `DuckRequest { envelope, on }` → per-sample gain |
| Resampling | `ResampleProcessor` (sinc), `UsbDirect` (linear, twice), swresample options | `Resampler` (native) | `(in_rate, out_rate)` → polyphase kernel, cached per pair |
| Biquads | `Eq.coeffs` + `EqProcessor`, `Tones`, mpv `lavfi` | `Biquad` (native) | RBJ parameters → coefficients; `f32` and `f64` state variants |
| Sample format conversion | `UsbAudioSink.convert`, `speakAlone`, `EqProcessor.process` | native format converter | `f32` ↔ s16/s24-in-3/s24-in-4/s32, with the existing dither rule |
| Track-key matching | `isUsed`, `inScope`, `newToYou`, `known`, `fmSong`, smart shuffle | `TrackKey`, `KeySet` | track → both keys once |
| History aggregation | `Dj.loadTaste`, `Stats`, `Wrapped.allScrobbles`, `shuffleWeight` | `TasteService` | sources → `TasteSnapshot` |
| "Not again too soon" | `NoRepeat` used ad hoc with six Prefs keys | `Rotation` (port of `NoRepeat`, same encodings) | key, candidates → pick; commit on announce |
| HTTP | `Http.kt` (OkHttp), raw `HttpURLConnection` in `DjVoices`, both `Voice.kt` | `net` | request → response, single-flight by key |
| Text for speech | `Spoken.humanize`, `djClean`, NFD stripping in both `Voice.kt`, ASCII stripping on desktop | `SpeechPrep` | `Script` → `SpokenText` |
| Stale-result guards | `loadSeq`, `djSpareJob`, `djTurn`, `Voice.turn`, `context`, `streamRetry` | One `Epoch` type per actor; cancellation tokens | — |

### 4.2 Bottlenecks

None of these is measured. All are read from code; treat the ranking as a hypothesis for Phase 1.

| Area | Code-evident cost | Proposed change | Preserves behaviour because |
|---|---|---|---|
| Desktop voice latency | A process spawn and full model load per line; no make-ahead | In-process engine, loaded once, lines made ahead | Same text, same fallback order |
| Taste rebuild | Full pass over all history, per-play `ZonedDateTime` allocation, file tag reads for moods, under a lock | Incremental snapshot keyed by source versions; integer time arithmetic; mood lookups from the store | Same counts: asserted against Kotlin `loadTaste` on recorded histories |
| Set planning | `known(taste)` rebuilt per call and per artist loop; `used` recomputed; `Music.songStats.rows()` scanned linearly per comment | Precomputed `KeySet`s in the snapshot; indexed skip stats | Pure refactor, covered by planner golden tests |
| Queue updates | Immutable list copied on every change; whole queue serialized on every `loadCurrent` and queue change | Persistent vector or indexed deque in `PlaybackCore`; debounced save (flush on pause, stop and background) | Saved file format unchanged |
| UI recomposition | About 150 global state fields read across screens; several are bumped as version counters (`musicVersion`, `savedVersion`) | Per-screen `StateFlow` slices; version counters become per-domain | UI reads the same values |
| USB conversion | Per-sample `when`, `roundToLong`, bounds-checked `ByteBuffer` puts in Kotlin on the playback thread; a second pass for the visualizer | Native loop specialized per (input format, slot size), visualizer fed by decimation | Bit-exact tests against the Kotlin converter |
| `Music` singleton | About 3,800 lines and 150 state fields on one thread; every feature edits it | Split into the services in 1.2, one actor each | Behaviour traces from Phase 0 replayed against the new services |
| Polling | 500 ms position loop, 300 ms `applySound` on desktop, 100 ms USB watch, 20 ms voice progress poll | Event-driven where the engine reports changes; keep the 100 ms USB watch (device-tuned) | Timers that encode device workarounds are kept as is |
| Locks | `Dj.lock`, `recentLock`, `@Synchronized loadTaste`, `synchronized(UsbDirect)`, USB ring mutex | Actors own their state; the only shared structures are immutable snapshots and SPSC rings | — |

**Baseline and profiling plan (Phase 1).** Capture on the current 3.2.28 build, on one mid-range Android phone with a USB DAC, and one Windows PC: cold start to first frame; DJ tap to first music sample; set start to first voice frame; `Dj.build` wall time and network call count for start + three spares; `loadTaste` wall time and allocations with a 100k-play history; URB underruns per hour (`UsbNative.stats`); dropped frames in Now playing with a visualizer; resident memory after one hour. Tools: Perfetto and `simpleperf` on Android, Android Studio memory profiler, JFR on Windows and Linux (plus ETW/WPA and `perf`), `kotlinx-benchmark` for hot Kotlin paths, `criterion` for the native module. The same captures are taken on one Linux PC. Targets are set as "no worse than baseline" for parity paths and are only tightened after the baseline exists. No numeric target is proposed here.

**Avoiding a central bottleneck.** `PlaybackCore`, `DjDirector`, `TtsService` and `TasteService` are separate actors with bounded mailboxes; none calls another synchronously. Audio never waits on any of them: the real-time thread reads rings and atomics only. If `DjDirector` or `TtsService` fails or stalls, playback continues and the DJ degrades to silence (the existing behaviour for a late voice). If `TasteService` fails, planners use the last snapshot. Each actor restarts independently under a supervisor that logs to the existing Problems list.

### 4.3 `DjVoiceProcessor` specification (rule R-A1)

**Contract.** Input: 24 kHz mono, `f32` in [-1, 1] or `i16` (converted by `x / 32768.0`). Output: interleaved stereo `f32` at the active stream's rate, already limited, plus conversion to the stream's sample format at mix time. Runs entirely off the real-time thread.

**Order.** Exactly as specified: resample → upmix → high-pass → low shelf → peak → limiter. Since L and R are identical after the upmix and stages 1–3 are linear, the implementation processes one channel carrying `mono × 0.7071` through the filters and limiter and duplicates it at the end. This is sample-identical to processing both channels and halves the work.

**1. Resampler.** Polyphase windowed-sinc FIR, Kaiser window, the same family as `ResampleProcessor` (48 taps, beta 8 there). Ratio as a reduced fraction L/M: 24 k→48 k is 2/1, →96 k is 4/1, →44.1 k is 147/80. Proposed parameters: 32 taps per phase, beta 9, cutoff at 0.45 of the lower rate; exact phases (2, 4, 147), no interpolation between phases. Group delay is constant and removed by trimming, so the output is time-aligned. For other rates (88.2, 176.4, 192 kHz), the same builder generates the table from the reduced ratio; cache tables per rate pair.

**2. Upmix.** `v = mono × 0.70710678`.

**3. Biquads (RBJ cookbook).** Coefficients are computed in `f64` for the **output** rate `fs` and stored as `f32`. With `w0 = 2π·f0/fs`, `c = cos w0`, `s = sin w0`, `α = s/(2Q)`:

- High-pass, 80 Hz, Q 0.7071: `b0 = (1+c)/2`, `b1 = −(1+c)`, `b2 = (1+c)/2`, `a0 = 1+α`, `a1 = −2c`, `a2 = 1−α`.
- Low shelf, 180 Hz, +2.5 dB, Q 0.7071, with `A = 10^(2.5/40)`, `β = 2·√A·α`: `b0 = A[(A+1) − (A−1)c + β]`, `b1 = 2A[(A−1) − (A+1)c]`, `b2 = A[(A+1) − (A−1)c − β]`, `a0 = (A+1) + (A−1)c + β`, `a1 = −2[(A−1) + (A+1)c]`, `a2 = (A+1) + (A−1)c − β`.
- Peaking, 3.5 kHz, +2.0 dB, Q 1.2, with `A = 10^(2.0/40)`: `b0 = 1+αA`, `b1 = −2c`, `b2 = 1−αA`, `a0 = 1+α/A`, `a1 = −2c`, `a2 = 1−α/A`.

All divided by `a0`. Structure: transposed direct form II, `f32` state (`y = b0·x + z1; z1 = b1·x − a1·y + z2; z2 = b2·x − a2·y`). No heap, no branches, no external crates. Risk to check: at 96 kHz the 80 Hz high-pass has poles very close to z = 1; the test compares the `f32` filter against an `f64` reference and fails above a set error floor. If it fails, that one stage uses `f64` state (still dependency-free).

**4. Limiter.** Ceiling `C = 10^(−1.5/20) = 0.8414`. Lookahead `N = ceil(0.002·fs)` samples (89, 96, 192 at 44.1, 48, 96 kHz). Peak detector: sliding maximum of `|x|` over `N+1` samples (monotonic deque). Gain computer with a soft knee of width `W` below the ceiling: below `C·10^(−W/20)` gain is 1; inside the knee the gain follows a quadratic blend; above, `g = C/peak`. `W` is not given in the brief: 3 dB is assumed (decision D9, low impact). Smoothing: attack is a linear ramp completed within the lookahead, so the gain has reached its target when the peak arrives; release is one-pole with `coef = exp(−1/(0.050·fs))`. The signal is delayed by `N` and multiplied. A final clamp to ±C exists as a guard; a test asserts it never engages. Because processing is offline, the `N`-sample delay is trimmed from the cached buffer: the limiter adds 2 ms of lookahead to processing, and zero latency to playback.

**State continuity.** A break is one continuous signal: filter and limiter state are zeroed at its start and run through to its end, with `N` zeros appended to flush the limiter and a 5 ms fade in and out to avoid edge clicks. When segments are dropped at announce (R-D2), the processor is run per segment with a 5 ms equal-gain join, so no filter state is shared across a removed segment.

**Re-render on spec change (R-A4).** Trigger: `OutputSpec` differs from `ProcessedVoice.spec`. Idle sets are re-rendered in the background, nearest to playback first. For a break that is already playing, the remaining part of `mono_master` (from the current source position minus 20 ms of pre-roll to settle the filters) is re-rendered; the mixer fades the old buffer out over 10 ms and the new one in.

### 4.4 Mixing and real-time rules

- **R-A5 (format).** The mix bus is `f32`. Conversion to the stream format is the last step: s16 with the existing TPDF dither rule when the dither setting is on and the path is not bit-exact; s24 and s32 by scaling and rounding; float passed through. In `Direct, bit-exact` mode the music bypasses the bus entirely (integer passthrough, as today); the bus is engaged only from the first to the last sample of a break's envelope.
- **R-A7 (headroom).** Music at full scale × 0.25 plus voice at 0.8414 sums to 1.09. On in-app mix paths the voice is scaled by 0.891 (−1.0 dB) at mix time so the worst case is exactly 1.0. The limiter ceiling is a maximum, so this stays within the specification. On paths where the OS mixes two streams, no trim is applied. The alternative, keeping 0.8414 and accepting rare hard clipping as the code does today, is decision D10.
- **Where the work runs.** `DjVoiceProcessor` (resampler, biquads, limiter) lives in the native module and is called from a background coroutine, never from a playback thread. The USB mix (gain ramp, add voice, format conversion, ring write) runs natively inside the one JNI call `UsbAudioSink` makes per buffer; the URB thread is native and never enters the JVM.
- **Native audio threads may**: read SPSC rings, read atomics, multiply, add, convert formats, advance a read index. **They may not**: allocate, lock, log, call back into Kotlin, touch files, or run TTS, resampler design or coefficient calculation. Enforcement: `assert_no_alloc` in debug and test builds; voice audio is a direct buffer pinned by a reference held in `DjSet` until the native side reports the break finished; commands reach the native side through atomics and a fixed-capacity ring.
- **Standard path.** Voice plays as its own stream (Android `AudioTrack` with the `BtRoute` voice attributes and preferred device, as today; desktop a shared-mode `SourceDataLine`). Ducking starts at the first voice frame written and ends at the last (R-A2, `Standard` envelope in R-A6). On Android the ramp is a per-sample gain stage added to the existing processor chain. On desktop mpv owns the samples, so the ramp is 32 volume commands 10 ms apart: the same 320 ms and end points, finer than today's 8 steps, but not sample-accurate. That limit is stated here rather than hidden.
- **USB path.** Voice is added into the PCM written to the existing ring, at the ring's rate, with the `UsbDirect` envelope (R-A6), under R-U1 to R-U4.

---

## 5. Atomic implementation phases and verification

Every phase is one reviewable change set with its own tests. A phase is done only when its acceptance criteria pass on Windows and Android (and Linux if D2 says so). Each phase moves existing Kotlin behind a service boundary or adds new code; the old path stays in the tree behind a flag until the phase has passed parity, and rollback is the flag. Work goes out through the project's existing patch-series CI.

| # | Phase | Scope | Prerequisites | Invariants | Rollback | Acceptance |
|---|---|---|---|---|---|---|
| 0 | Characterization | Run all 57 test files in CI (C-22). Decode the `android-work` patches for DJ, USB and playback. Write characterization tests for every row in 2.1–2.5 not yet covered: golden JSON for every persisted file, recorded sync and remote exchanges, screenshot goldens per screen and per look on desktop. Complete the code audit of unread files and extend this document | Repository clone with all branches | No product code changes | n/a | A parity ledger (below) with one row per feature and a linked test or a "blocked" reason |
| 1 | Baselines | Measurements in 4.2 on 3.2.28 | 0 | — | n/a | Baseline report committed |
| 2 | Model, store and Linux build | `TrackKey` and `KeySet` (R-M1); `store` with debounced atomic writes over the existing files; a Linux package built and smoke-tested in CI next to the APK and MSI | 0 | Every persisted file byte-compatible; Prefs store name `radon-2` | Flag | Round-trip of real data directories from phone and PC; app starts and plays on Linux |
| 3 | Native module skeleton | `radon-audio-native` crate, JNI bindings, CI builds for Android (arm64-v8a, armeabi-v7a), Windows x64, Linux x64; packaged in the APK, MSI and Linux package | 0 | No behaviour change; nothing calls it yet | Not loaded | Library loads on all three; a round-trip call test |
| 4 | DSP | `Biquad`, `Resampler`, `Limiter`, `DjVoiceProcessor`, format conversion, in the native module | 3 | — | Not wired yet | DSP test suite below |
| 5 | Audio engine, standard path | Gain stages (R-A3), `Standard` envelope, voice stream fed with processed PCM, duck at first voice frame (R-A2) | 4 | Crossfade exclusions unchanged | Flag | Envelope goldens; crossfade-plus-duck simulation |
| 6a | TTS spike | Prove Chatterbox Turbo and Pocket TTS runtimes from Kotlin on Windows, Linux, Android | — | — | n/a | Written result: runtime, sizes, speed, licence, rate; go or no-go per engine |
| 6b | `TtsService` | Engines behind the trait, install and remove, dropdown, made-ahead queue with priority, migration of voice settings (D4) | 6a | `dj_voice` semantics; one line at a time; hush is immediate | Flag: old `Voice` | Failure-injection tests for download, load, synth, cancel |
| 7 | `TasteService` | Extract `Dj.loadTaste` into an immutable snapshot with background rebuild (R-T2) | 2 | Counts equal to `loadTaste` | Flag | Equality on recorded histories; concurrency test |
| 8 | DJ planner and scripts | Restructure `Dj.kt` into `SetPlanner` (pure, R-D5), `ScriptWriter`, `DjDirector`; `SetType` mapping; rewrites; `SpeechPrep` boundary; R-D2 to R-D6 | 7 | Kind rotation, 300-song gap, arc and spread, display names, Prefs encodings | Flag: old `Dj` | Planner goldens; line lint; staleness tests |
| 9 | `PlaybackCore` | Extract queue and playback from `Music.kt` into an actor: epochs, crossfade state enum, watchdogs, saved state | 2, 5 | Section 2.1 rows | Flag | Model-based tests against the behaviour traces from Phase 0 |
| 10 | USB path | Replace `usbaudio.c` and the mixing in `UsbAudioSink.convert` with the native module: SPSC ring, mixed path, R-U1 to R-U4, stall fallback. `UsbDirect`'s device handling stays Kotlin | 4, 5, D5, D6 | Every device-tuned delay and retry; Problems reports | Flag: old sink and C library | USB test suite below, on at least the FiiO KA17 and one UAC1 device |
| 11 | Android verification | Service and session untouched; Android Auto, widget, routing | 9, 10 | Foreground rule; package id and key (C-25) | Flag | Screen-off soak, install-over test |
| 12 | Desktop verification, Windows and Linux | mpv kept; tray, media keys, single instance; Linux gaps from D2 closed (mpv and ffmpeg packaging, media keys and MPRIS, dictation or its documented absence, updater) | 9 | MSI upgrade id; Windows behaviour unchanged | Flag | Install-over test on Windows; Linux ledger rows green |
| 13 | Remaining services | Streaming sources, downloader, tagger, sync, remote, stats, wrapped, lyrics moved behind `store`, `net` and `PlaybackCore` commands, one phase each; `Music.kt` shrinks to nothing | 0, 2, 9 | Per-row parity | Per-service flag | Ledger rows green |
| 14 | Performance pass and clean-up | Compare with Phase 1; delete flagged-off code only after a full release cycle | all | — | Revert | No regression against baseline; ledger complete |

### 5.1 Test strategies

**State races.** Actors are tested with a deterministic scheduler (`kotlinx-coroutines-test` with seeded message orders; Lincheck for the few shared structures; `loom` for the native ring). Required cases: scope change during planning (C-5); skip during load (`load_seq`); `SwitchSet` racing `QueueSets` (R-S1); hush during synthesis; DJ stop while three spares are building; engine end event during advancing.

**Duplicate requests.** `net` records every request key. Tests assert, for DJ start plus three spares, no key appears twice within its cache lifetime, and that two concurrent callers for one key produce one network call.

**Stale queue metadata.** Property tests generate a planned set, then mutate the world before announce (filter tracks, reorder the queue, change the current track, advance the clock, replace a stream by a local copy with a different title). Assert R-D2 and R-D3: every spoken title and artist equals the announced track's current metadata, or the name segment is absent; never a guess. A separate test feeds `Track` defaults ("Unknown", "Unknown artist") and empty strings.

**TTS normalization.** Table-driven tests extending `SpokenTest` and `PronounceTest`: numbers (1,234; 2015 as a year and as a count; 4.5; 100th), symbols (&, /, +, #, %, @, w/), clutter removal, and at least 200 real artist and title strings drawn from the owner's library covering all-caps words, vowel-less stylizations, digits in names ("blink-182", "The 1975", "6LACK", "3OH!3"), and non-Latin scripts. Assertions: no single-letter runs unless the token is in the initialism list; no digits or symbols reach the engine; output is stable (same input, same output).

**Speech length and style.** For every line in Appendix A and every rewrite, expanded with worst-case placeholders (longest title and artist in the test library, largest numbers): word count ≤ 45 after normalization; lint in 2.4 passes. A duration check on synthesized audio flags any break over 18 s per engine.

**Both ducking paths.** Offline renders with constant-amplitude music. `Standard`: gain reaches 0.25 at 320 ms ±1 sample from the first voice frame, returns to 1.0 at 320 ms after the last. `UsbDirect`: per-frame slope equals `1/(0.12·fs)` exactly; 0.25 reached after `0.09·fs` frames. Both: monotonic, no step larger than the slope. A test asserts the two envelopes are distinct types that cannot be passed to the wrong path.

**Sample-rate changes.** Render a break at 44.1 kHz, switch the spec to 96 kHz at 40%, and to 48 kHz at 80%: the concatenated output, resampled back to 24 kHz, correlates with the master above a set threshold; no sample-to-sample jump above the click threshold; no hardware call is made by the voice path.

**Output-format conversions.** For s16, s24 in 3 bytes, s24 in 4, s32 and float: round-trip error within one LSB; full-scale inputs do not wrap; bit-exact passthrough when no voice is active (compared with the Kotlin `convert` on the same buffers).

**DSP correctness.** Biquads: magnitude response at 20 Hz, 80 Hz, 180 Hz, 1 kHz, 3.5 kHz and 10 kHz within tolerance of the analytic RBJ response at 44.1, 48 and 96 kHz; `f32` versus `f64` error floor. Resampler: passband ripple, stopband attenuation, and an alias test with a swept sine. Limiter: no output sample above 0.8414 for white noise, +6 dB sines and impulses; gain reduction begins no later than 2 ms before a peak; recovery follows the 50 ms time constant; the safety clamp counter is zero. Whole chain: golden output hashes per rate.

**USB stall fallback.** A fake ring consumer stops reading: the stall event fires between 4.0 and 4.1 s; the mixed voice is cancelled; the duck returns to 1.0 with the `UsbDirect` slope; the remainder is routed per R-U3; the music stream is untouched (no flush, no setup). On hardware: unplug-and-replug, and a throttled URB thread.

**Audio continuity.** Thirty-minute soak on each path with DJ breaks every set: zero underruns reported by the engine and by `UsbNative`-equivalent stats; recorded loopback audio scanned for gaps longer than one buffer and for clicks. Transition cases: break across a track change, across a crossfade exclusion, during pause and resume, during a skip.

**Platforms.** Windows: MSI install over 3.2.28, shared and exclusive output, tray, media keys, SMTC. Android: install over the owner's package, screen-off playback for an hour, Android Auto, Bluetooth routing rules, USB DAC with and without Android's own bit-perfect mode. Linux: only if D2 keeps it.

### 5.2 Evidence of 100% parity

A **parity ledger** (one file in the repository) has one row per inventory item: feature, source evidence, characterization test, rebuild test, platforms verified, status (`verified`, `unverified`, `blocked: reason`, `changed by rule R-x`). Parity may be claimed only when every row is `verified` or `changed by rule`, each change traces to a rule in section 3 or a decision in section 6, and the owner has signed off the items that cannot be automated (real services, real devices). Rows that need credentials, hardware or the owner's library stay `blocked` and are listed in every status report; they are never counted as done.

---

## 6. Decisions that need the product owner

| ID | Decision | Safest default | Consequence of the default |
|---|---|---|---|
| D1 | Implementation language | **Decided: Kotlin-first on all three platforms, with one small native audio module (Rust)** | The brief's "Rust only on desktop" is set aside; existing logic is restructured, not rewritten |
| D2 | Is Linux a target again? The repository says no | **Decided: Linux is a target, alongside Windows and Android** | Linux gaps found in code must be closed and added to the parity ledger: the DJ cannot speak (`Voice.canSpeak` is Windows-only), no dictation (`Voice.listen`), no media keys or MPRIS (`PLAN.md`: "not wanted", now reversed), mpv and ffmpeg are bundled only in the Windows installer (`Bins`), no Start-with-system or media flyout equivalent, DAC PEQ is `DacPeqWin` over HID, updater is MSI-only. Already present: Compose Desktop runs on Linux (`PLAN.md`: "tested on Linux"), `MpvEngine` Unix socket, default-output detection through `wpctl`, a `.deb` target, and CI builds a Linux jar. `CLAUDE.md`'s "Linux is no longer a target" must be updated |
| D3 | Replace mpv on desktop with the app's own output | Keep mpv | Voice cannot be mixed into an exclusive stream (D7); desktop ducking is stepped, not sample-accurate |
| D4 | What replaces "the phone's own voice" and the OS-voice fallback | Pocket TTS only on Android; no OS voice; `dj_use_natural` ignored | Users who chose the phone's voice get the new one; no voice if the model is missing |
| D5 | USB stall fallback target | As the brief says: speaker or headphones | A break may come out of the phone speaker while a DAC is connected |
| D6 | DJ speaking on USB before any stream exists | Wait for the first track's stream, drop after 15 s | The opener is never spoken into silence on USB |
| D7 | DJ on desktop in exclusive mode | Text only | No spoken DJ with bit-perfect on PC until D3 changes |
| D8 | Status wording and an optional "never alter samples" switch | Reword the status; no switch | The DJ keeps talking over direct output |
| D9 | Limiter knee width | 3 dB | Slightly earlier, gentler gain reduction |
| D10 | Mix headroom | Trim voice by 1 dB on in-app mixes | Voice is 1 dB quieter on USB than on the standard path |
| D11 | Dropdown default engine on desktop | Pocket TTS | Smaller first download |
| D12 | ElevenLabs code (disabled today) | Not ported; settings keys preserved | Cannot be re-enabled without new work |

---

## 7. Highest-risk unknowns, and where to start

1. **TTS runtimes.** Whether Chatterbox Turbo and Pocket TTS can run from Kotlin on all three platforms, at what size and speed, was not verified. Everything in the voice path hangs on Phase 6a.
2. **Unaudited two-thirds of the code.** UI, streaming services, sync, stats and tagging were inventoried from documentation only. Hidden behaviour there is the main threat to the 100% claim.
3. **Patch history unread.** The device-specific USB workarounds have reasons recorded only in `android-work` patches.
4. **Splitting `Music.kt`.** About 3,800 lines and 150 state fields that the UI reads directly must be divided among services without changing behaviour; this is where regressions are most likely, and why Phase 0's behaviour traces come first.
5. **USB behaviour on real DACs.** The timing constants in `UsbDirect` were tuned on hardware; the native streaming rewrite can only be proven on the same devices.
6. **Android foreground-service behaviour** between songs, which previously killed the app with the screen off, must survive the new audio path.
7. **Desktop exclusive mode with the DJ** (C-16) is inferred from code and needs a hardware check before R-A8 is accepted.
8. **Release identity** (C-25): a wrong package id, key or MSI upgrade id strands the owner's installed app and data.

**First phase to begin: Phase 0**, starting with its smallest step: add `:app:testReleaseUnitTest` to the CI workflow so all 57 existing test files run, then clone with full history and decode the patches. Phase 6a (the TTS spike) can run in parallel, since it touches no product code.
