# USB DAC history: the reasons recorded in the patches

Made mechanically: for every patch on `android-work` that touches `UsbDirect.kt`, `UsbAudioSink.kt` or `usbaudio.c`, the comment lines it **added** to those files, in order. These are the device-specific reasons (delays, retries, takeover order) that Phase 10 must carry over unchanged. Comments a later patch removed are still listed under the patch that added them.

## r264

- `usbaudio.c`: Bit-perfect straight to a USB DAC, around Android's sound system: the music's own samples streamed to the DAC's
- `usbaudio.c`: isochronous endpoint with the kernel's usbfs (the way USB Audio Player Pro does it), on phones whose Android
- `usbaudio.c`: mixes everything first (no AudioMixerAttributes bit-perfect mode). The Kotlin side (UsbDirect) finds the DAC, claims
- `usbaudio.c`: its streaming interface, picks the format and sets the sample rate; this file only keeps the samples flowing:
- `usbaudio.c`: a ring buffer the player writes into, a few URBs always queued on the DAC's endpoint, and the DAC's feedback
- `usbaudio.c`: endpoint (asynchronous DACs say how fast they really want samples) steering how many frames go in each packet.
- `usbaudio.c`: feedback URBs kept queued
- `usbaudio.c`: frames of music (not silence) in it
- `usbaudio.c`: the flush generation it was filled in
- `usbaudio.c`: frames per packet, Q16.16
- `usbaudio.c`: music frames the DAC has taken since the last flush
- `usbaudio.c`: music frames queued in URBs
- `usbaudio.c`: Fills a data URB: packet by packet, the frames the DAC wants now (the accumulator carries the fraction).
- `usbaudio.c`: no feedback then: nominal rate
- `usbaudio.c`: The DAC's feedback: how many frames it wants per packet. Full speed sends 10.14 in 3 bytes, high speed 16.16 in 4,
- `usbaudio.c`: but devices vary, so whichever reading lands near the nominal rate is taken.
- `usbaudio.c`: per feedback period -> per data packet
- `usbaudio.c`: Copies as much of the buffer as fits (whole frames); the bytes taken.
- `usbaudio.c`: Music frames not heard yet: in the ring and in queued URBs.
- `usbaudio.c`: Throws away what's waiting (a seek, a new song): the queued URBs finish as they are but no longer count.
- `usbaudio.c`: collect what's left so nothing points at freed memory
- `usbaudio.c`: Gives an interface back to Android's own USB audio driver once we let it go.
- `UsbAudioSink.kt`: The player's sound output: Android's (the inner sink) as always, or, with bit-perfect on and a USB DAC that Android
- `UsbAudioSink.kt`: can't play bit-perfect, straight to the DAC ([UsbDirect]). Then each song's samples go to the DAC as they are, only
- `UsbAudioSink.kt`: padded into the DAC's sample size (16-bit and 24-bit music, and the decoders' float, which holds 24 bits exactly).
- `UsbAudioSink.kt`: bytes of out already handed to the DAC
- `UsbAudioSink.kt`: input bytes per output byte
- `UsbAudioSink.kt`: this song can't go straight there: the DAC back to Android for it
- `UsbAudioSink.kt`: The buffer's samples into the DAC's sample size (little-endian), the app's own volume only if the DAC has none.
- `UsbAudioSink.kt`: the sample, left-justified in 32 bits
- `UsbAudioSink.kt`: The player's own volume: bit-perfect leaves the samples alone, so only silence (0) is heard as such.
- `UsbDirect.kt`: The native half: streams to the DAC's endpoint (usbaudio.c).
- `UsbDirect.kt`: Bit-perfect straight to a USB DAC, for phones whose Android mixes every sound first (Samsung's and many others':
- `UsbDirect.kt`: no bit-perfect mode in AudioMixerAttributes). The app takes the DAC's streaming interface over from Android, the
- `UsbDirect.kt`: way USB Audio Player Pro does, sets it to each song's own sample rate and bit depth, and streams the song's samples
- `UsbDirect.kt`: to it untouched (16-bit and 24-bit music exactly as in the file, padded into the DAC's sample size). While it does,
- `UsbDirect.kt`: Android can't play anything else through the DAC (other apps, notifications), and the phone's volume buttons set the
- `UsbDirect.kt`: DAC's own volume (its hardware volume control), so the samples stay untouched. Phones with Android's own bit-perfect
- `UsbDirect.kt`: mode (Pixels) keep using that.
- `UsbDirect.kt`: One way the DAC takes music: an alternate setting of a streaming interface.
- `UsbDirect.kt`: (unit, channels) with a volume control
- `UsbDirect.kt`: the DAC is ours, streaming
- `UsbDirect.kt`: The USB DAC plugged in (a device with an audio streaming interface), or null.
- `UsbDirect.kt`: Android's own bit-perfect mode for this DAC (Android 14+, the phone's audio system has it): then that's used.
- `UsbDirect.kt`: Whether songs should go straight to the DAC: bit-perfect is on, a DAC is plugged in, and Android has no bit-perfect
- `UsbDirect.kt`: mode of its own for it. Asks for permission to use the DAC the first time.
- `UsbDirect.kt`: ---- the DAC's descriptors ----
- `UsbDirect.kt`: terminal id -> clock source (UAC2)
- `UsbDirect.kt`: selector / multiplier -> its first source
- `UsbDirect.kt`: (unit, source, channel mask with volume: bit 0 master, 1.. channels)
- `UsbDirect.kt`: address, attributes, maxPacket, interval, synchAddress
- `UsbDirect.kt`: A terminal's clock: through selectors and multipliers to the clock source
- `UsbDirect.kt`: UAC2: the sample rates the DAC's clock offers (GET RANGE).
- `UsbDirect.kt`: What a stream is set up as.
- `UsbDirect.kt`: (playback thread) Sets the DAC up for a song: its rate, the setting with enough bits, and starts streaming.
- `UsbDirect.kt`: The format, or null (then Android's mix plays it, with the reason in [note]).
- `UsbDirect.kt`: Stops the stream (the DAC keeps its setting; the next song starts it again).
- `UsbDirect.kt`: Lets the DAC go: back to Android (bit-perfect turned off, the DAC unplugged, the app closing).
- `UsbDirect.kt`: The phone's media volume onto the DAC's own volume control (its feature unit), so the samples stay untouched;
- `UsbDirect.kt`: without one, [digitalGain] turns the music down in the app instead.
- `UsbDirect.kt`: half the steps ≈ -15 dB, a tenth ≈ -50 dB
- `UsbDirect.kt`: Muted at zero (the mute control, where the DAC has one)
- `UsbDirect.kt`: The app's own volume on the samples: 1 (untouched) unless the DAC has no volume control and the phone isn't at full.

## r273

- `usbaudio.c`: What happened, for the app's report (Problems): feedback packets, ones taken, the last one's bytes; data URBs
- `usbaudio.c`: and packets the DAC or the kernel said went wrong
- `usbaudio.c`: For the report: URBs done, URBs and packets gone wrong, feedback packets seen and taken, the last feedback (raw), its
- `usbaudio.c`: length, frames per packet now (Q16.16) and nominal.
- `UsbDirect.kt`: Read back: a DAC still at another rate would get the music too fast or too slow (static, crackling)
- `UsbDirect.kt`: A few seconds into a stream: how it's going, into Problems (Copy sends it), for the first streams of a session:
- `UsbDirect.kt`: the DAC's settings as read, what was picked, the rate it took, its feedback and any packets that went wrong.
- `UsbDirect.kt`: As Android's own curve for USB: each step the same number of dB, -60 dB at the lowest (half the steps ≈ -30 dB).
- `UsbDirect.kt`: The old curve (half ≈ -15 dB) played a lot louder than the same setting does through Android.

## r275

- `UsbDirect.kt`: the DAC's audio interfaces the app holds
- `UsbDirect.kt`: The whole DAC taken from Android, its control interface too (as USB Audio Player Pro does): with only the
- `UsbDirect.kt`: streaming interface taken, Android's own USB audio driver kept the rest and could go on setting the DAC
- `UsbDirect.kt`: (its rate, its format) under the app, which played the music as loud static.
- `UsbDirect.kt`: Set, and checked (GET_INTERFACE): the DAC must really be in the setting whose sample size the music is packed in
- `UsbDirect.kt`: The DAC was just taken from Android (it then thinks it was unplugged for a moment).

## r276

- `UsbDirect.kt`: Songs that failed while streaming straight to the DAC this time it's plugged in; after two, bit-perfect plays
- `UsbDirect.kt`: through Android (as far as it can) until the DAC is plugged in again or bit-perfect is turned off and on.

## r277

- `UsbDirect.kt`: Just given back to Android (bit-perfect off, then on again): its USB audio driver takes a few seconds to
- `UsbDirect.kt`: settle on the DAC; taken back mid-way, the DAC was left set Android's way and the music came out as static.
- `UsbDirect.kt`: Every streaming interface stopped (alternate setting 0), whatever Android had it doing

## r278

- `UsbAudioSink.kt`: the DAC stream this sink started
- `UsbAudioSink.kt`: Only the stream this player started: a preview player let go never takes the DAC from the main one
- `UsbDirect.kt`: its volume control didn't take: the app's own volume instead
- `UsbDirect.kt`: Unplugged while the app had it: Android didn't see it go (it thought it was gone already, when
- `UsbDirect.kt`: the app took it), so it never said "becoming noisy"; the music is paused here instead
- `UsbDirect.kt`: Ms since the DAC was taken from Android (a long time: never).
- `UsbDirect.kt`: The DAC is the app's, or was just taken or handed back: Android's "the headphones were unplugged" is about that.
- `UsbDirect.kt`: Read back: a DAC that turned the request down (or says it's somewhere else) gets the app's own volume instead,
- `UsbDirect.kt`: so the phone's volume buttons always do something

## r279

- `usbaudio.c`: When a data URB and a feedback packet last came back (ms, monotonic), and how often the stream was kicked
- `usbaudio.c`: The DAC stopped taking music (no data URB back for a while, its feedback still coming): the queued URBs are
- `usbaudio.c`: cancelled; the loop gets them back and sends them again, scheduled afresh.
- `UsbDirect.kt`: Watches the stream: if the DAC stops taking music (no data back for 0.4 s while its feedback still comes, the
- `UsbDirect.kt`: way songs stopped a second in), the queued transfers are sent again; noted in Problems the first times.

## r280

- `UsbAudioSink.kt`: The stream isn't this player's any more (another player, a preview, took the DAC, or it was stopped): set up
- `UsbAudioSink.kt`: again for this song and carried on where it was, rather than failing ("AudioTrack write failed: -1")
- `UsbAudioSink.kt`: The DAC stream, if it's still this player's.

## r281

- `UsbDirect.kt`: its own volume set to full (once)
- `UsbDirect.kt`: The deepest setting: 16-bit music is just as untouched in it, and the app's volume (below full) keeps every bit
- `UsbDirect.kt`: The phone's volume, followed here too (in case Android's "volume changed" never comes)
- `UsbDirect.kt`: The DAC's own volume is left at full (0 dB) and the phone's volume is the app's, on the samples, as Android
- `UsbDirect.kt`: itself does for USB DACs: the FiiO KA17 took its volume setting and read it back but didn't change how loud it
- `UsbDirect.kt`: was, so the volume buttons did nothing. Bit-perfect at full volume (the samples untouched there).

## r282

- `usbaudio.c`: data URBs kept queued (1 ms each, as Linux's own USB audio driver does: 24 ms ahead)
- `usbaudio.c`: 1 ms a URB (8 packets at high speed), as Linux's own USB audio driver sends: the 8 ms ones (64 packets) stopped
- `usbaudio.c`: going out about 1.6 s in on a Samsung phone's USB controller (the FiiO KA17), though its feedback kept coming
- `UsbAudioSink.kt`: The DAC stopped taking music and sending the transfers again didn't help: set up again from scratch here
- `UsbDirect.kt`: First the transfers sent again; if it's stuck again straight after, the stream is set up from scratch
- `UsbDirect.kt`: (the player's own thread does it: see restartWanted)
- `UsbDirect.kt`: The watch's ask for the stream to be set up from scratch (its handle), done by the player's own thread.
- `UsbDirect.kt`: (playback thread) The stream stopped and its setting forgotten, so the next begin sets the DAC up afresh.

## r283

- `UsbAudioSink.kt`: Set up again quietly (it just carries on); one song needing it again and again is noted, once
- `UsbDirect.kt`: Only when something's wrong: the music not getting through while it plays (it used to be noted every time)
- `UsbDirect.kt`: Watches the stream while it plays: if the DAC stops taking music (no data back for 0.4 s), the queued transfers
- `UsbDirect.kt`: are sent again, and if that doesn't help the stream is set up from scratch (the player's own thread does it: see
- `UsbDirect.kt`: restartWanted). Quiet: it works on its own; only a stream that keeps stopping is noted (UsbAudioSink). Paused,
- `UsbDirect.kt`: it's left alone: the phone may sleep then, and the stream with it, which is no problem.
- `UsbDirect.kt`: one watch for each stream
- `UsbDirect.kt`: The phone's volume, followed here too (in case Android's "volume changed" never comes)
- `UsbDirect.kt`: Only while it plays, and not just after it started again (the stream wakes up with it)

## r284

- `UsbDirect.kt`: The DAC was just taken from Android (it then thinks it was unplugged for a moment).
- `UsbDirect.kt`: Ms since the DAC was taken from Android (a long time: never).
- `UsbDirect.kt`: The DAC is the app's, or was just taken or handed back: Android's "the headphones were unplugged" is about that.

## r285

- `usbaudio.c`: moment the phone is busy elsewhere never leaves the DAC without music (a split-second gap)
- `usbaudio.c`: Audio priority (Android's THREAD_PRIORITY_URGENT_AUDIO, as its own audio threads have): the DAC is kept fed
- `usbaudio.c`: even when the phone is busy; where it isn't allowed, the normal priority it had
- `usbaudio.c`: The music in the ring not sent yet (for a stream set up again: it goes on in the new one, nothing skipped).
- `UsbAudioSink.kt`: The stream stopped on its own (its transfers refused: "AudioTrack write failed: 32") while the DAC's still
- `UsbAudioSink.kt`: plugged in: set up again and carried on, not a failed song
- `UsbAudioSink.kt`: What the old stream hadn't sent yet goes first, so nothing is skipped
- `UsbAudioSink.kt`: Bit-perfect with the equalizer's Bass and Treble kept (Equalizer › Bass and Treble with bit-perfect): the two
- `UsbAudioSink.kt`: shelves (100 Hz and 8 kHz, as the equalizer's), turned down first by what they boost so nothing clips, and the
- `UsbAudioSink.kt`: phone's volume, in double precision on the samples as they go to the DAC. Only while either isn't 0 dB.
- `UsbAudioSink.kt`: each shelf's b0, b1, b2, a1, a2
- `UsbAudioSink.kt`: each channel's x1, x2, y1, y2 for each shelf (2 shelves)
- `UsbAudioSink.kt`: Whether the tones are on, set up for this rate and these channels (and the current Bass and Treble).
- `UsbAudioSink.kt`: One sample (left-justified in 32 bits) on channel ch, through both shelves and the gain.
- `UsbDirect.kt`: Nothing back from the DAC for 0.15 s (64 ms of music is queued): set up again at once, the shortest gap
- `UsbDirect.kt`: (sending the transfers again only got it going for a moment)
- `UsbDirect.kt`: The music a stream set up again still had to send: the new one sends it first (see UsbAudioSink).

## r286

- `usbaudio.c`: Live streams: every call with a handle checks it's still alive under a shared lock, and stop takes it out under the
- `usbaudio.c`: exclusive one before freeing it, so a call that comes late (the watch, a report, a player skipping songs quickly)
- `usbaudio.c`: finds nothing instead of freed memory (that crashed the app when songs were skipped fast).
- `usbaudio.c`: (never: a few streams at most)
- `usbaudio.c`: The calls Java makes with a handle: only on a live stream (see g_live).
- `UsbAudioSink.kt`: all of float's precision (24-bit music exactly as it was)

## r287

- `UsbAudioSink.kt`: Whether the player has this sink playing (play/pause), kept here: the DAC's stream can be (re)started while the
- `UsbAudioSink.kt`: player is paused or playing, and it must follow that. Before, a stream left paused (an error and its retry, the
- `UsbAudioSink.kt`: app coming back after a crash) stayed paused while the player played, and the music went on and off until you
- `UsbDirect.kt`: The DAC dropping off the phone's USB on its own (not unplugged by you) looks the same to the app as unplugging
- `UsbDirect.kt`: it: the music pauses. When it happens again within two minutes, Problems says so, so a loose cable or a DAC
- `UsbDirect.kt`: that can't get enough power from the phone can be told from the app.

## r288

- `usbaudio.c`: URBs handed to the kernel and not yet back: every one must come back before the stream's memory is freed, or
- `usbaudio.c`: the next stream on the same DAC got them back instead (and the kernel wrote into freed memory: the crash after
- `usbaudio.c`: skipping many songs, and starting the DJ, when there were more URBs than the old clean-up waited for)
- `usbaudio.c`: Which of this stream's URBs it is, by its address (never read from one that isn't ours)
- `usbaudio.c`: not ours (an old stream's, long gone): left alone
- `usbaudio.c`: Every URB still out collected (they come back cancelled), so nothing points at freed memory; at most a second
- `usbaudio.c`: the DAC's gone: the kernel let them all go
- `usbaudio.c`: Any still out after that (never seen): their memory kept rather than freed under the kernel

## r289

- `UsbDirect.kt`: the DJ heard through the DAC while the app has it
- `UsbDirect.kt`: Played the whole time (not paused for the DJ, say) and the song hardly moved on: then it's worth noting
- `UsbDirect.kt`: Taken out first, then stopped: a stop takes a moment, and a new stream started meanwhile (another thread)
- `UsbDirect.kt`: was then forgotten and left running beside the next one
- `UsbDirect.kt`: Whether the player has the DAC's stream playing (see UsbAudioSink): the DJ's voice leaves it as it found it.
- `UsbDirect.kt`: Something to be heard while the app has the DAC (the DJ's voice, with the music paused under it): Android can't
- `UsbDirect.kt`: play anything through the DAC then, so the voice went nowhere and the DJ never finished. samples are floats
- `UsbDirect.kt`: (-1..1, channels interleaved) at rate; they go to the DAC at its rate and sample size, at the phone's volume.
- `UsbDirect.kt`: Blocking; false when the DAC isn't the app's (then Android plays it as before). Stops early once go is false.
- `UsbDirect.kt`: whatever of the song was waiting (it starts again from the top after the DJ)
- `UsbDirect.kt`: mono to both sides; stereo as it is
- `UsbDirect.kt`: the stream's gone: nothing more to say through it
- `UsbDirect.kt`: What's still queued, heard to its end

## r292

- `UsbAudioSink.kt`: the DJ's voice has the DAC for a moment (see UsbDirect.speak)
- `UsbDirect.kt`: The DJ's voice is going to the DAC: the music player's samples wait (they'd be heard under it).
- `UsbDirect.kt`: No stream yet (the DJ before the first song, or between songs): one set up for the voice, at the rate the
- `UsbDirect.kt`: DAC was last at (else 48 kHz), which the next song takes over (or sets up again at its own)
- `UsbDirect.kt`: (the music pausing for the DJ can come in after the voice began)

## r293

- `UsbDirect.kt`: (the player's thread) While the DJ's voice is going to the DAC, the player leaves the stream alone: the next
- `UsbDirect.kt`: song set up meanwhile (a new set, maybe at another sample rate) replaced the stream, or flushed it, and the voice
- `UsbDirect.kt`: was never heard. At most 40 seconds.
- `UsbDirect.kt`: How the DJ's last line went to the DAC, in words (for Problems).

## r294

- `UsbAudioSink.kt`: the DJ's voice has the stream: it isn't muted under her
- `UsbDirect.kt`: The player has its volume at nothing (the DJ talking, a fade's start): the stream mutes the music then.
- `UsbDirect.kt`: (the music pausing for the DJ, and its volume going to nothing, can come in after the voice began:
- `UsbDirect.kt`: the stream was muted with it, and the voice went out as silence)

## r307

- `UsbAudioSink.kt`: the music's gain under the DJ's voice (1: as it is)
- `UsbAudioSink.kt`: The DJ's voice over the music (see UsbDirect.speak): mixed in, the music eased down under it and back up after
- `UsbAudioSink.kt`: another rate now: she stops
- `UsbAudioSink.kt`: about a tenth of a second to go down or up
- `UsbDirect.kt`: The music playing: her voice mixed into it as it goes to the DAC, the music turned down under her (as a radio
- `UsbDirect.kt`: DJ talks over the song), so nothing waits or starts again. The music not flowing for a while (stopped,
- `UsbDirect.kt`: paused): the rest of the line said on its own, as below.
- `UsbDirect.kt`: The music stopped flowing under her: the rest of the line on its own
- `UsbDirect.kt`: The DJ's voice over the music (bit-perfect): stereo frames at [voiceRate], mixed in by the sink as it sends the
- `UsbDirect.kt`: music (see UsbAudioSink.convert), which turns the music down under it; null once it's all been mixed in.
- `UsbDirect.kt`: frames of it mixed in so far
- `UsbDirect.kt`: The voice alone to the DAC, the music held for it (the DJ before the first song, or with the music stopped).
