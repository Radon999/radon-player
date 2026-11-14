# The voice engines: what was checked (Phase 6a)

Checked 14 November 2026. "Verified" means read from the project's own files or run in the checks; "reported" means
a web page says so and it wasn't confirmed here.

## Pocket TTS (Kyutai): go, on all three platforms

| Question | Answer | How it's known |
|---|---|---|
| Does it run in the app's own voice library? | Yes. sherpa-onnx 2.0.1, the version the phone app already ships for Kokoro (`android/app/build.gradle.kts`), has it: `OfflineTtsPocketModelConfig` in its Kotlin API | Verified: `sherpa-onnx/kotlin-api/Tts.kt` at tag `v2.0.1` |
| Android | The same AAR the app uses now | Verified (same file); **not run on a phone** |
| Windows and Linux, inside the app | sherpa-onnx publishes a Java library and native libraries for Windows x64 and Linux x64 (`sherpa-onnx-jvm-2.0.1.jar`, `sherpa-onnx-native-lib-win-x64-2.0.1.jar`, `...-linux-x64-...`), so the PC no longer needs to start a separate program for each line | Verified: the v2.0.1 release's files. Not yet built into the PC app |
| Does it actually speak? | Yes. Three of the DJ's lines were spoken on a Linux build server (`.github/workflows/tts-spike.yml`; `result-tts.txt` and `tts-sample-pocket.wav` on the `rebuild-ci` branch) | Verified: run 43565842443 |
| Output | 24 kHz, mono, 16-bit: exactly what the DJ's voice processing is specified to take in | Verified in that run |
| Speed | 2.3 to 2.4 times faster than real time with 2 threads on a 4-core server processor (a 5.5 s line made in 2.4 s). 4 threads was slower than 2 (slower than real time). 2 generation steps instead of 5 was no faster for a line that length | Verified in that run. **Phone speed is unknown** and will be slower; lines are made ahead, so it only has to keep up with one line per set |
| Size | 94 MB to download, 194 MB on disk (int8). Kokoro today: about 130 MB on the phone, 400 MB on the PC | Verified |
| Voice | It copies a voice from a short reference recording (no built-in speaker list in this package; it ships one sample, `bria.wav`). The app needs one or a few reference voices it has the right to use | Verified (the package); which voices to ship is open |
| Language | English in this package. Kyutai lists French, German, Portuguese, Italian and Spanish models too | Reported |
| Licence | Weights CC BY 4.0 (credit required). Kyutai forbids copying a real person's voice without consent | Reported (Kyutai's model page); the code's licence wasn't confirmed |

## Chatterbox Turbo (Resemble AI): possible on the PC, with real costs

| Question | Answer | How it's known |
|---|---|---|
| In sherpa-onnx? | No. Its 2.0.1 API has VITS, Matcha, Kokoro, ZipVoice, Kitten, Pocket and Supertonic | Verified: the same `Tts.kt` |
| Any way to run it in the app? | Resemble AI publishes an official ONNX version; ONNX Runtime has a Java library. But the steps around the model (turning text into tokens, the loop that generates speech tokens, the reference-voice encoder, the decoder) exist only as a Python example and would have to be written again in Kotlin and kept in step | Verified: the model page and its example. The Kotlin port is not started |
| Output | 24 kHz, English only, needs a reference voice | Verified on the model page |
| Size | Four model files. Smallest set (4-bit with 16-bit floats): about 558 MB. 8-bit: about 1.1 GB. Full: about 3.3 GB | Verified: the model's file list |
| Speed on a processor | One third-party figure: 22.7 s for a short sentence on 4 threads. Nothing official | Reported; **not measured here** |
| Licence | MIT. It can mark its audio with an inaudible watermark (off in the example) | Verified on the model page |
| Android | Not planned (the brief asks for Pocket TTS only there) | - |

## What follows from this

- **Pocket TTS first, everywhere.** It needs no new library on the phone, it removes the PC's program-per-line, it brings
  the DJ's voice to Linux, and its output matches the voice-processing input.
- **Chatterbox Turbo is a second, separate job for the PC only**: a 558 MB download at the least, a pipeline to port, and
  an unmeasured speed that may be too slow without a graphics card. The dropdown the brief asks for can be built now with
  Pocket TTS in it and Chatterbox Turbo added when it's proven.
- **Before either ships:** a reference voice (or a few) the app may use; Pocket TTS timed on the owner's phone; the
  credit line CC BY 4.0 requires, in the app's about text.

## The owner's decision (14 November 2026)

**Pocket TTS only, on the phone and the PC. No second engine, and so no engine dropdown.** Chatterbox Turbo is not
built: it would have taken longer and brought its own problems (above) for one more voice on one platform. This
replaces the plan's rule R-T1 and decision D11.

## Built

- **Phone** (`core/android/.../Voice.kt`): Pocket TTS in place of Kokoro, through the same library. Compiles; **not
  run on a phone**.
- **PC, Windows and Linux** (`core/desktop/.../Voice.kt`): Pocket TTS inside the app, through sherpa-onnx's Java
  library (`desktop/build.gradle.kts`). `PocketVoiceTest` makes it speak for real in the checks. On the Linux build
  server, with the voice loaded, a 5-second line took 0.9 seconds (5.4 times faster than real time); the first line,
  loading the voice too, took 1.1 seconds.
- Lines are made ahead on the PC too now, and the music is turned down when the voice starts, not when the line starts
  being made (the plan's C-7).

## Still open

- The voice it's modelled on is the recording the model brings (`test_wavs/bria.wav`). The app needs its own.
- Timing on the owner's phone.
- The credit CC BY 4.0 asks for, in the app; `FEATURES.md` still says "about 130 MB" for the phone's download.
- On the phone the choice "The phone's own" voice (Android's) is kept as it was, and is still what speaks when Pocket
  TTS can't.