package org.omarchy.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.compose.runtime.snapshotFlow
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Plays the music: ExoPlayer with the EQ in its audio path, and a media session so the
 *  notification, lock screen, Bluetooth buttons and Android Auto see what's playing.
 *  Next and previous go to [Music]'s queue, which holds one song in the player at a time.
 *  While the phone is the desktop's remote, the session shows the desktop instead ([RemotePlayer]). */
class MusicService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var switching: Job? = null
    private var players = emptyList<Player>()
    private var retriedFor: String? = null   // the song played again once after an audio error
    private var reparsedFor: String? = null   // the song read again once after its file read as broken
    private var noisyPause = 0L              // when Android's "becoming noisy" last paused the music (0: not waiting on one)
    private var outsidePauseAt = 0L          // when a Pause from outside the app (earbuds, car, lock screen) last paused it (elapsed ms)

    /** Something other than the phone's own speaker is connected to play through: Bluetooth (earbuds, a car),
     *  wired headphones, a USB DAC, a hearing aid. */
    private fun stillPlugged(am: android.media.AudioManager): Boolean = runCatching {
        val kinds = setOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_HEARING_AID,
            26 /* BLE headset */, 27 /* BLE speaker */)
        am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in kinds }
    }.getOrDefault(false)

    override fun onCreate() {
        super.onCreate()
        Music.init(this)
        UsbDirect.init(this)
        DacPeqUsb.init(this)
        val renderers = ExoEngine.renderers(this, Music.eqProcessor)   // the EQ for float (hi-res) music too: see EqSink
        val exo = ExoPlayer.Builder(this, renderers)
            // (Android's way of handling the sound, unless Keep playing holds it: see KeepPlaying)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                !Prefs.on(Prefs.KEEP_PLAYING, true))
            .setHandleAudioBecomingNoisy(false)   // its own, below: bit-perfect taking the DAC isn't an unplug
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val keep = KeepPlaying(this, exo)
        // What the sound output itself does (a new one made, one let go, it running dry, it failing): into Sound lately,
        // so a song gone silent can be put beside what the output did just before.
        var madeFor: String? = null
        var decoderFor: String? = null
        var movedAt = 0L   // when the song was last moved (by you, or the app starting it again in place): that makes a new output too
        exo.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onPositionDiscontinuity(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                                                 oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                // 5: the player itself jumped (not a seek, not the next song): heard as a skip in the sound
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) movedAt = System.currentTimeMillis()
                if (reason == Player.DISCONTINUITY_REASON_INTERNAL)
                    SoundLog.hiccup("the player jumped by itself from ${oldPosition.positionMs / 1000}s to ${newPosition.positionMs / 1000}s")
            }
            // The decoder (the part of the phone that turns the file into sound) made, let go or failing. One made
            // again in the middle of a song is the phone having taken the first one away: what it had decoded and not
            // yet played is lost (a jump of a second or two), and the sound output is left holding a buffer of the old
            // one's (seen 21 Nov 2026 on “Wake Up Call”: held an old buffer, then a 2-second jump in the timestamps).
            override fun onAudioDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, decoderName: String,
                                                   initializedTimestampMs: Long, initializationDurationMs: Long) {
                val id = Music.current()?.id
                val again = id != null && id == decoderFor && exo.currentPosition > 3000 && System.currentTimeMillis() - movedAt > 3000
                decoderFor = id
                val what = "a decoder made ($decoderName, in $initializationDurationMs ms)"
                if (again) SoundLog.hiccup("the decoder was made again mid-song: $what") else SoundLog.note(what)
            }
            override fun onAudioDecoderReleased(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, decoderName: String) {
                SoundLog.note("a decoder let go ($decoderName)")
            }
            override fun onAudioCodecError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioCodecError: Exception) {
                SoundLog.hiccup("the decoder failed: ${audioCodecError.javaClass.simpleName}: ${audioCodecError.message?.take(160)}")
            }
            override fun onAudioUnderrun(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                SoundLog.hiccup("the sound output ran dry (not fed for $elapsedSinceLastFeedMs ms, buffer $bufferSizeMs ms)")
            }
            override fun onAudioSinkError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioSinkError: Exception) {
                SoundLog.note("the sound output failed: ${audioSinkError.message?.take(80)}")
                Crashes.note("The sound output reported a fault while “${Music.current()?.title}” played: ${audioSinkError.message?.take(120)}")
            }
            override fun onAudioTrackInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, c: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig) {
                // (a second one for the same song is the output made again mid-song: a gap you can hear)
                val id = Music.current()?.id
                val again = id != null && id == madeFor && exo.currentPosition > 3000 && System.currentTimeMillis() - movedAt > 3000
                madeFor = id
                val what = "a sound output made (${c.sampleRate} Hz, encoding ${c.encoding}, offload ${c.offload}, tunneling ${c.tunneling})"
                if (again) SoundLog.hiccup("the sound output was made again mid-song: $what") else SoundLog.note(what)
            }
            override fun onAudioTrackReleased(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, c: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig) {
                SoundLog.note("a sound output let go")
            }
            override fun onAudioSessionIdChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioSessionId: Int) {
                SoundLog.note("the sound's session changed ($audioSessionId)")
            }
        })
        var heldAt = 0L   // when another app last took the sound for a moment
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Music.trace("player: playing=$isPlaying (held back: ${exo.playbackSuppressionReason})")
                Music.paused = !exo.playWhenReady
            }

            override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                Music.trace("player: held back, reason $reason")
                // Another app taking the sound for a moment (voice typing, a call, a spoken direction) is the phone doing
                // what it should: the music waits and comes back. Kept in Sound lately, with how long it lasted, but not
                // in Problems: on the owner's phone it filled Problems (each stretch of voice typing was one) and would
                // have buried a real fault. No output it may use (2) is still one.
                if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) { heldAt = System.currentTimeMillis(); SoundLog.note("another app took the sound for a moment: the music waits") }
                else if (reason != 0) SoundLog.hiccup("the player was held back, reason $reason (2 no output it may use)")
                else SoundLog.note("the sound is the music's again" + if (heldAt > 0) " (after ${(System.currentTimeMillis() - heldAt) / 1000} s)" else "").also { heldAt = 0L }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Music.trace("player: play-when-ready=$playWhenReady (reason $reason)")
                // (2 and 3 are Android taking the sound for another app, for good or for a moment; 1 is the app or you)
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
                    // (the phone doing what it should: another app started playing, a call, the car or the headphones
                    // switched off. Kept in Sound lately, not in Problems, where it read as a fault of the music's)
                    SoundLog.note("play=$playWhenReady, reason $reason (2 another app took the sound, 3 the output went)")
                else if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) SoundLog.note("play=$playWhenReady, reason $reason (5 the song ended)")
                // Bit-perfect taking the USB DAC over makes Android think the DAC was unplugged ("becoming noisy"), which
                // paused the music; that pause is undone (a real unplug still pauses)
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY && UsbDirect.tookOverLately() && UsbDirect.device() != null) {
                    exo.play(); return
                }
                Music.paused = !playWhenReady
                if (playWhenReady) { noisyPause = 0L; outsidePauseAt = 0L }   // playing again (by you, or by itself): nothing left to undo
                if (!playWhenReady) pausedAt = android.os.SystemClock.elapsedRealtime()
                // Keep playing: the app holds the sound while the music plays, so no other app taking it pauses it
                keep.playChanged(playWhenReady)
            }

            override fun onPlaybackStateChanged(state: Int) {
                Music.trace("player: state $state")   // 1 idle, 2 buffering, 3 ready, 4 ended
                if (state == Player.STATE_ENDED) Music.engineEnded()
                if (state == Player.STATE_READY) exo.duration.takeIf { it > 0 }?.let { Music.duration = it / 1000.0 }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // What's really playing: the format, bit depth and kHz (or kbps), for the mark under the seek bar.
                val f = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)
                Music.nowFormat = f?.let { formatLabel(it) } ?: ""
                Music.nowFormatOf = Music.current()?.id ?: ""
                // Bit-perfect: ask for the DAC at this song's own rate; else ReplayGain from its tags.
                if (f != null) {
                    val rate = f.sampleRate; val bits = if (f.pcmEncoding != androidx.media3.common.Format.NO_VALUE) pcmBits(f.pcmEncoding) else 0
                    if (Music.bitPerfect) Music.bg({ Music.dacNote = AudioQuality.applyBitPerfect(this@MusicService, true, rate, bits) })
                    Music.eqProcessor.rgGain = if (Music.bitPerfect || Music.replayGain == "off") 1.0 else
                        run {
                            val album = Music.replayGain == "album"
                            val (db, peak) = AudioQuality.replayGain(f.metadata, album)?.let { it to AudioQuality.replayPeak(f.metadata, album) }
                                ?: AudioQuality.replayGain(f.metadata, !album)?.let { it to AudioQuality.replayPeak(f.metadata, !album) } ?: (null to null)
                            db?.let { Music.rgFactor(it, peak) } ?: 1.0
                        }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // The sound output refused the song (an audio error, as Android Auto's output gave on some songs): tried
                // again at once without HQ resampling, before anything is said. Noted in Settings › Problems.
                val id = Music.current()?.id
                Music.trace("player: error ${error.errorCodeName}")
                // Streaming straight to a USB DAC (bit-perfect): noted with how the DAC was set up, the DAC handed back to
                // Android, and the song tried again (after two failures, through Android: see UsbDirect.failed)
                if (Music.usbDirect || UsbDirect.active) {
                    Crashes.caught(error, "Bit-perfect to the USB DAC failed on “${Music.current()?.title}” (${error.errorCodeName}) · ${UsbDirect.lastSetup}")
                    UsbDirect.failed(error.errorCodeName)
                    exo.prepare(); exo.play()
                    return
                }
                val audio = error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                    (error as? androidx.media3.exoplayer.ExoPlaybackException)?.type == androidx.media3.exoplayer.ExoPlaybackException.TYPE_RENDERER
                if (audio && id != null && retriedFor != id) {
                    retriedFor = id
                    Crashes.caught(error, "The sound output refused “${Music.current()?.title}” (${error.errorCodeName}): played again without HQ resampling")
                    ResampleProcessor.plain = true
                    exo.prepare()
                    return
                }
                // A file of yours read as broken ("malformed"): it may be being written this moment (its tags, by a scan
                // or by you). Tried once more two seconds on, before anything is said; a file really broken says so then.
                val parsing = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                if (parsing && !Music.streaming && id != null && reparsedFor != id) {
                    reparsedFor = id
                    val t = Music.current()
                    SoundLog.note("“${t?.title}” read as broken (${error.errorCodeName}): looked at, and tried again")
                    // A FLAC of yours left half-written by a tag write that was interrupted: its sound is all there but
                    // its blocks don't lead to it. Put right in place (FlacRepair: four bytes, nothing moved, what was in
                    // front of the sound saved first), its names written back if they were lost, and played.
                    Music.bg({
                        val path = t?.file?.let { FileTags.pathOf(it) } ?: Music.lib.items[id]?.file?.let { FileTags.pathOf(it) }
                        val f = path?.let { java.io.File(it) }?.takeIf { it.isFile && it.canWrite() && it.extension.equals("flac", true) }
                        val done = f?.let { SafeWrite.alone { FlacRepair.repair(it, java.io.File(filesDir, "repaired/${it.name}.${System.currentTimeMillis()}.front")) } }
                        if (done != null && f != null && t != null) {
                            Crashes.note("“${t.title}” was a damaged file (its tags and cover no longer led to its music, as a tag write cut short leaves it): " +
                                "put right in place, the music untouched" + (if (done.tagsKept) ", its tags kept" else ", its title and artist written back") +
                                ". Its cover may be gone: Songs without covers, or a Sync, brings it back. What was in front of the music is saved in the app's folder (repaired/)")
                            if (!done.tagsKept) runCatching { FileTags.writeOwn(f.path, mapOf("title" to t.title, "artist" to t.artist, "album" to t.album).filterValues { it.isNotBlank() }) }
                            runCatching { android.media.MediaScannerConnection.scanFile(this@MusicService, arrayOf(f.path), null, null) }
                        }
                        done != null
                    }) { repaired, _ ->
                        // (repaired, or perhaps only being written this moment: either way read again, a little later)
                        android.os.Handler(mainLooper).postDelayed({ if (Music.current()?.id == id) { exo.prepare(); exo.play() } }, if (repaired == true) 300 else 2000)
                    }
                    return
                }
                if (Music.streaming) Music.streamBroke()
                // The song's file isn't where it was (moved or renamed): your copy of it found, linked everywhere, played
                else if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND && Music.fileGone()) Unit
                else {
                    Crashes.caught(error, "Couldn't play “${Music.current()?.title}” (${error.errorCodeName})")
                    Music.toast("Couldn't play “${Music.current()?.title}”: ${error.errorCodeName}")
                    Music.paused = true
                }
            }
        })
        // Wi-Fi kept fully awake while the music plays only when the music may need the network to go on (a stream,
        // the next song not on the phone, the DJ on: PowerRules.needsNetwork); a queue of your own files lets Wi-Fi keep
        // to its own power saving. The phone itself is kept awake while it plays either way. Looked at again with each
        // song, and when the queue or the DJ changes.
        var wakeNet = true   // (as built: WAKE_MODE_NETWORK)
        fun wakeMode() {
            val nxt = Music.queue.getOrNull(Music.index + 1)
            val net = PowerRules.needsNetwork(exo.currentMediaItem?.localConfiguration?.uri?.toString(),
                nxt?.let { it.isFile || Music.lib.has(it.id) }, Music.dj.on)
            if (net == wakeNet) return
            wakeNet = net
            exo.setWakeMode(if (net) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
        }
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = wakeMode()
        })
        Music.scope.launch { snapshotFlow { Triple(Music.queue, Music.index, Music.dj.on) }.collect { runCatching { wakeMode() } } }
        // A song gone silent partway though it plays on (seen with one 24-bit FLAC: sound for twenty seconds, then none
        // until the song was moved): moving it brings the sound back, so the app does that itself, in place, and notes
        // where the sound stopped. At most twice a song (a song with real silence in it is left alone after that).
        run {
            val h = android.os.Handler(mainLooper)
            var deadFor: String? = null
            var heals = 0
            // The same for a song whose music has stopped reaching the sound output at all while it "plays" (seen 21 Nov
            // 2026 on “Wake Up Call”: after the output let go of an old buffer the song counted on for 40 seconds with
            // nothing going out, and no watch saw it, because they all look at the music as it passes and none was
            // passing). Four seconds with nothing taken by the output, the song not at its end: started again in place.
            var stuckFor: String? = null
            var stuckHeals = 0
            val fedWatch = object : Runnable {
                override fun run() {
                    if (!exo.isPlaying) return   // it only ever acts on music playing: looked at again once it plays (below)
                    h.postDelayed(this, 2000)
                    runCatching {
                        val eq = Music.eqProcessor
                        val t = Music.current() ?: return@runCatching
                        if (!exo.isPlaying || eq.sinkEos || eq.sinkFedAt == 0L || Music.usbDirect || Music.cast != null) return@runCatching
                        val quiet = System.currentTimeMillis() - eq.sinkFedAt
                        if (quiet < 4000) return@runCatching
                        if (t.id != stuckFor) { stuckFor = t.id; stuckHeals = 0 }
                        if (stuckHeals >= 3) return@runCatching
                        stuckHeals++
                        val at = exo.currentPosition
                        eq.sinkFedAt = System.currentTimeMillis()   // (counted afresh from this try)
                        SoundLog.hiccup("no music reached the sound output for ${quiet / 1000} s while “${t.title}” played: started again at ${at / 1000}s (time $stuckHeals)")
                        if (stuckHeals == 1) exo.seekTo(at) else { exo.stop(); exo.prepare(); exo.seekTo(at); exo.play() }
                    }
                }
            }
            // Every 2 seconds while the music plays, and not at all while it doesn't: started again 2 seconds after it
            // plays again (before, it looked every 2 seconds for as long as the app ran, paused or not).
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) { h.removeCallbacks(fedWatch); h.postDelayed(fedWatch, 2000) }
                }
            })
            Music.eqProcessor.onDead = { where ->
                h.post {
                    val t = Music.current()
                    if (t?.id != deadFor) { deadFor = t?.id; heals = 0 }
                    if (exo.playWhenReady && heals < 2) {
                        heals++
                        val at = exo.currentPosition
                        Crashes.note("“${t?.title}” went silent at ${at / 1000}s though it was playing: the sound stopped $where. Started again from the same place.")
                        SoundLog.note("silent at ${at / 1000}s, the sound stopped $where: started again in place (time $heals)")
                        exo.seekTo(at)
                    }
                }
            }
        }
        // Headphones unplugged (Android's "becoming noisy"): paused, as ExoPlayer did, except when it's bit-perfect taking
        // the USB DAC from Android or handing it back, which Android takes for the DAC being unplugged (that paused every
        // song a second in). Unplugging the DAC while the app has it: see UsbDirect.
        androidx.core.content.ContextCompat.registerReceiver(this, object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (UsbDirect.ownsOrJustMoved() && UsbDirect.device() != null) {
                    return
                }
                if (!exo.playWhenReady) return
                // The device going isn't the one the music is held on (the car leaving while it plays on your earbuds,
                // Ignore Bluetooth devices): not a pause at all, not even the second it took to look before
                if (BtRoute.heldThroughNoisy()) { SoundLog.note("another device went (not the one the music is on): the music plays on"); return }
                exo.pause()
                // With two things connected (earbuds and the car), the music moving from one to the other is told to
                // the app the same way as headphones being unplugged, and the song stopped partway for no reason you
                // could see. So: paused at once, as for an unplug (nothing ever blares from the phone's speaker), and a
                // moment later, if headphones, a car or a DAC are still connected to play through, it goes on by itself.
                // Around an ignored device coming or going (BtRoute.stirred) it keeps looking for twenty seconds, not
                // four: earbuds the car pushed off come back after several seconds, and the music stayed paused.
                val stoppedAt = android.os.SystemClock.elapsedRealtime()
                noisyPause = stoppedAt
                val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val h = android.os.Handler(mainLooper)
                fun look(tries: Int) {
                    if (noisyPause != stoppedAt || exo.playWhenReady) return   // started again, or paused again, meanwhile
                    if (stillPlugged(am)) {
                        SoundLog.note("the sound moved to another device mid-song (not an unplug): the music went on by itself")
                        noisyPause = 0L
                        exo.play()
                    } else if (tries < 3 || (tries < 25 && BtRoute.stirred(BtIgnoreRules.NOISY_STIRRED_MS))) h.postDelayed({ look(tries + 1) }, 900)
                }
                h.postDelayed({ look(0) }, 1200)
            }
        }, android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        // A Pause from outside the app (see queueAware's pause) a moment before an ignored device came or went was the
        // car's or the earbuds' doing (Android Auto ending sends one, then lets go): the music plays on by itself, as
        // long as there's somewhere other than the phone's speaker to play.
        BtRoute.onStir = {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (!exo.playWhenReady && BtIgnoreRules.near(outsidePauseAt, BtRoute.stirAt) && stillPlugged(am)) {
                outsidePauseAt = 0L
                SoundLog.note("a Pause from outside the app just as a device you ignore came or went: the music plays on")
                exo.play()
            }
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val local = queueAware(exo)
        val remote = queueAware(RemotePlayer(mainLooper))
        players = listOf(local, remote)
        // A library session: the car (Android Auto) browses your songs through it (see Car).
        // If Android still won't let it into the foreground, note it (Settings › Problems).
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() =
                Crashes.note("Android didn't let the music keep the app in the foreground")
        })
        val s = MediaLibrarySession.Builder(this, if (Remote.active) remote else local, Car.callback).setSessionActivity(open).build()
        session = s
        val cast = castPlayer()?.let { queueAware(it) }
        if (cast != null) players = players + cast
        switching = Music.scope.launch {
            snapshotFlow { Remote.active to (Music.cast != null) }.collect { (on, casting) ->
                s.player = if (on) remote else if (casting && cast != null) cast else local
            }
        }
        // The notification's and lock screen's Repeat and Favorite show how they stand, and the Android Auto button
        // (Ignore Bluetooth devices: no stutter at all) whether it's on or off.
        Music.scope.launch {
            snapshotFlow { listOf(Music.repeat, Music.current()?.id, Music.musicVersion, Music.savedVersion, Music.downloads.size, Prefs.on(Prefs.NOTIF_SHUFFLE, true), SeamlessBt.autoButton) }.collect { runCatching { s.setCustomLayout(Car.buttons()) } }
        }
        // The home screen widget: the song, playing or paused, the queue.
        Music.scope.launch {
            snapshotFlow { listOf(Music.current()?.id, Music.paused, Music.repeat, Music.index, Music.queue.size) }.collect {
                kotlinx.coroutines.delay(150)
                runCatching { PlayerWidget.update(this@MusicService) }
            }
        }
        Music.player = ExoEngine(exo, this, Music.eqProcessor)
        // Keep playing (Settings › Playback): the sound held by the app, not the player; set again when it's switched
        keep.apply()
        Music.scope.launch { snapshotFlow { Prefs.on(Prefs.KEEP_PLAYING, true) }.collect { keep.apply() } }
        Music.playerReady()
        LockScreen.register(this)
    }

    /** Cast: a Chromecast, Google speaker or TV picked (Now playing's Cast button) takes over from where the phone was;
     *  disconnected, the phone takes it back. Null without Google Play services. */
    @Suppress("DEPRECATION")
    private fun castPlayer(): CastPlayer? = runCatching {
        val ctx = CastContext.getSharedInstance(this)
        val cp = CastPlayer(ctx)
        cp.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if ((Music.cast as? ExoEngine)?.p === cp) Music.paused = !playWhenReady
            }
            override fun onPlaybackStateChanged(state: Int) {
                if ((Music.cast as? ExoEngine)?.p !== cp) return
                if (state == Player.STATE_ENDED) Music.trackEnded()
                if (state == Player.STATE_READY) cp.duration.takeIf { it > 0 }?.let { Music.duration = it / 1000.0 }
            }
            override fun onPlayerError(error: PlaybackException) {
                if ((Music.cast as? ExoEngine)?.p === cp) Music.toast("The cast device couldn't play “${Music.current()?.title}”: ${error.errorCodeName}")
            }
        })
        cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                val dev = ctx.sessionManager.currentCastSession?.castDevice
                Music.castHost = dev?.ipAddress?.hostAddress ?: ""
                Music.castStarted(ExoEngine(cp, this@MusicService), dev?.friendlyName ?: "the cast device")
            }
            override fun onCastSessionUnavailable() = Music.castStopped()
        })
        cp
    }.onFailure { Crashes.note("Cast isn't available: ${it.message}") }.getOrNull()

    /** The session's buttons: next and previous always there, and they go to our queue (or the desktop's). */
    private fun queueAware(p: Player) = object : ForwardingPlayer(p) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()

        override fun isCommandAvailable(command: Int) = command in setOf(Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) || super.isCommandAvailable(command)

        // A song picked on the car's screen (its id names the list and the place): played through Music.
        // The phone's own player holds one song, put there by the app. Anything outside the app that talks to the session
        // (the earbuds, a watch, the lock screen, the system) could until 4.0.22 also tell that player to Stop, or empty
        // or replace what it holds: the song then went silent partway while the app still took it for playing, and Play
        // started it from its beginning. From outside, Stop is now a Pause, and the song in the player is left alone.
        private val own get() = p is androidx.media3.exoplayer.ExoPlayer
        private fun outside(what: String) {
            org.omarchy.music.SoundLog.note("from outside the app: $what")
            Crashes.note("Something outside the app (the earbuds, a watch, the lock screen or the system) told the player: $what")
        }

        override fun stop() { if (own) { outside("Stop (taken as Pause)"); pause() } else super.stop() }
        override fun clearMediaItems() { if (own) outside("empty the player (left alone)") else super.clearMediaItems() }
        override fun removeMediaItem(index: Int) { if (own) outside("take the song out (left alone)") else super.removeMediaItem(index) }
        override fun removeMediaItems(fromIndex: Int, toIndex: Int) { if (own) outside("take the songs out (left alone)") else super.removeMediaItems(fromIndex, toIndex) }

        override fun setMediaItems(items: MutableList<androidx.media3.common.MediaItem>, startIndex: Int, startPositionMs: Long) {
            val id = items.getOrNull(maxOf(0, startIndex))?.mediaId
            if (id != null && Car.isCarItem(id)) Car.play(id)
            else if (own) outside("play something else (${items.size} items; left alone)")
            else super.setMediaItems(items, startIndex, startPositionMs)
        }

        override fun setMediaItems(items: MutableList<androidx.media3.common.MediaItem>, resetPosition: Boolean) {
            val id = items.firstOrNull()?.mediaId
            if (id != null && Car.isCarItem(id)) Car.play(id)
            else if (own) outside("play something else (${items.size} items; left alone)")
            else super.setMediaItems(items, resetPosition)
        }

        override fun seekToNext() = Music.sessionNext()
        override fun seekToNextMediaItem() = Music.sessionNext()
        override fun seekToPrevious() = Music.previous()
        override fun seekToPreviousMediaItem() = Music.previous()
        // Play from the headphones, the car or the lock screen: the app's own Play (so the DJ never stays stuck)
        override fun play() { if (p is RemotePlayer || Music.current() == null) super.play() else Music.sessionPlay() }
        // Pause there while the DJ talks: she stops too, and the music stays paused
        override fun pause() {
            // Some phones send a Pause when the USB DAC "goes away" (bit-perfect taking it from Android looks like an
            // unplug): one in the first seconds after the app took it is that, not you, and is left alone
            if (p !is RemotePlayer && UsbDirect.active && UsbDirect.sinceTakeover() < 4000) return
            // Ignore Bluetooth devices: cars and earbuds send a Pause of their own as an ignored device comes or goes
            // (the car connecting or letting go, the earbuds as the car takes the sound). Seconds after such a moment
            // it's left alone; seconds before one, it's undone when the moment comes (BtRoute.onStir, above).
            if (p is androidx.media3.exoplayer.ExoPlayer && p.playWhenReady && IgnoreBt.on) {
                val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                if (BtRoute.stirred() && stillPlugged(am)) {
                    SoundLog.note("a Pause from outside the app just as a device you ignore came or went: left alone, the music plays on")
                    return
                }
                outsidePauseAt = android.os.SystemClock.elapsedRealtime()
            }
            if (p !is RemotePlayer && Music.djTalking) Music.djHush(); super.pause()
        }

        // Between songs (one ran out, the next is loading) the session hears "buffering", not "ended":
        // see Music.advancing. The listeners the session adds get the same.
        private fun masked(state: Int) = if (p !is RemotePlayer && ((state == Player.STATE_ENDED && Music.advancing) ||
            (state == Player.STATE_IDLE && runCatching { Music.aboutToReload() }.getOrDefault(false)))) Player.STATE_BUFFERING else state
        override fun getPlaybackState(): Int = masked(super.getPlaybackState())
        private val wrapped = HashMap<Player.Listener, Player.Listener>()
        override fun addListener(listener: Player.Listener) {
            val w = MaskedListener(listener, ::masked)
            synchronized(wrapped) { wrapped[listener] = w }
            super.addListener(w)
        }
        override fun removeListener(listener: Player.Listener) {
            super.removeListener(synchronized(wrapped) { wrapped.remove(listener) } ?: listener)
        }
    }

    /** "FLAC 24-bit/96 kHz", "AAC 256 kbps/44.1 kHz". */
    private fun formatLabel(f: androidx.media3.common.Format): String {
        val mime = f.sampleMimeType ?: ""
        val name = when {
            "flac" in mime -> "FLAC"; "alac" in mime -> "ALAC"; "mpeg" in mime && "L3" in (f.codecs ?: "L3") -> "MP3"
            "mp4a" in mime || "aac" in mime -> "AAC"; "opus" in mime -> "Opus"; "vorbis" in mime -> "Vorbis"
            "raw" in mime || "wav" in mime -> "WAV"; else -> mime.substringAfter('/').uppercase()
        }
        val bits = when (f.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> 8; C.ENCODING_PCM_16BIT -> 16; C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32; else -> 0
        }
        val khz = if (f.sampleRate > 0) "%s kHz".format((f.sampleRate / 1000.0).toString().removeSuffix(".0")) else ""
        val rate = (if (f.averageBitrate > 0) f.averageBitrate else f.bitrate).takeIf { it > 0 }?.let { "${it / 1000} kbps" }
        val q = when {
            bits > 0 && khz.isNotEmpty() -> "$bits-bit/$khz"
            rate != null && name !in setOf("FLAC", "ALAC", "WAV") -> listOf(rate, khz).filter { it.isNotEmpty() }.joinToString("/")
            else -> khz
        }
        return listOf(name, q).filter { it.isNotEmpty() }.joinToString(" ")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** When the music was last paused (elapsed ms). */
    private var pausedAt = 0L
    private val later = Handler(android.os.Looper.getMainLooper())

    /** The app stays a foreground music player for [KEEP_PAUSED] after a pause (a pause between the DJ's sets, a call,
     *  you pausing for a moment): Android doesn't let an app in the background become one again (it said
     *  "startForegroundService() not allowed" and the music stopped), so Play from the headphones, the car or the
     *  DJ going on after a pause would fail with the screen off. After that long paused, it lets go as before. */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val p = session.player
        val since = android.os.SystemClock.elapsedRealtime() - pausedAt
        val keep = startInForegroundRequired || (p.mediaItemCount > 0 && !p.playWhenReady && pausedAt > 0 && since < KEEP_PAUSED &&
            p.playbackState != Player.STATE_IDLE)
        try {
            super.onUpdateNotification(session, keep)
        } catch (e: Exception) {
            Crashes.note("The music's notification couldn't be shown: ${e.message}")
        }
        if (keep && !startInForegroundRequired) {   // let go once it's been paused that long
            later.removeCallbacksAndMessages(null)
            later.postDelayed({ this.session?.let { s -> if (!s.player.playWhenReady) onUpdateNotification(s, false) } }, KEEP_PAUSED - since + 1000)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away while paused: stop; while playing, the music keeps going.
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    companion object { private const val KEEP_PAUSED = 10 * 60 * 1000L }

    override fun onDestroy() {
        later.removeCallbacksAndMessages(null)
        LockScreen.unregister(this)
        Music.player = null
        UsbDirect.closeAll()   // the DAC back to Android
        switching?.cancel()
        players.forEach { it.release() }
        session?.release()
        session = null
        super.onDestroy()
    }
}

/** A player listener passed on as it is, but for the playback state, which goes through mask. Every callback is passed
 *  on by hand: Kotlin's "by" doesn't pass on a Java interface's default methods (all of Player.Listener's), and without
 *  them the media session never heard the music start, so Android got no notification and stopped the music in the
 *  background. */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private class MaskedListener(val l: Player.Listener, val mask: (Int) -> Int) : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) = l.onEvents(player, events)
    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = l.onTimelineChanged(timeline, reason)
    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = l.onMediaItemTransition(mediaItem, reason)
    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = l.onTracksChanged(tracks)
    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = l.onMediaMetadataChanged(mediaMetadata)
    override fun onPlaylistMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = l.onPlaylistMetadataChanged(mediaMetadata)
    override fun onIsLoadingChanged(isLoading: Boolean) = l.onIsLoadingChanged(isLoading)
    override fun onLoadingChanged(isLoading: Boolean) = l.onLoadingChanged(isLoading)
    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = l.onAvailableCommandsChanged(availableCommands)
    override fun onTrackSelectionParametersChanged(parameters: androidx.media3.common.TrackSelectionParameters) = l.onTrackSelectionParametersChanged(parameters)
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = l.onPlayerStateChanged(playWhenReady, mask(playbackState))
    override fun onPlaybackStateChanged(playbackState: Int) = l.onPlaybackStateChanged(mask(playbackState))
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = l.onPlayWhenReadyChanged(playWhenReady, reason)
    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = l.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
    override fun onIsPlayingChanged(isPlaying: Boolean) = l.onIsPlayingChanged(isPlaying)
    override fun onRepeatModeChanged(repeatMode: Int) = l.onRepeatModeChanged(repeatMode)
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = l.onShuffleModeEnabledChanged(shuffleModeEnabled)
    override fun onPlayerError(error: PlaybackException) = l.onPlayerError(error)
    override fun onPlayerErrorChanged(error: PlaybackException?) = l.onPlayerErrorChanged(error)
    override fun onPositionDiscontinuity(reason: Int) = l.onPositionDiscontinuity(reason)
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) =
        l.onPositionDiscontinuity(oldPosition, newPosition, reason)
    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = l.onPlaybackParametersChanged(playbackParameters)
    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) = l.onSeekBackIncrementChanged(seekBackIncrementMs)
    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) = l.onSeekForwardIncrementChanged(seekForwardIncrementMs)
    override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) = l.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
    override fun onAudioSessionIdChanged(audioSessionId: Int) = l.onAudioSessionIdChanged(audioSessionId)
    override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) = l.onAudioAttributesChanged(audioAttributes)
    override fun onVolumeChanged(volume: Float) = l.onVolumeChanged(volume)
    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = l.onSkipSilenceEnabledChanged(skipSilenceEnabled)
    override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) = l.onDeviceInfoChanged(deviceInfo)
    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = l.onDeviceVolumeChanged(volume, muted)
    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = l.onVideoSizeChanged(videoSize)
    override fun onSurfaceSizeChanged(width: Int, height: Int) = l.onSurfaceSizeChanged(width, height)
    override fun onRenderedFirstFrame() = l.onRenderedFirstFrame()
    override fun onCues(cues: MutableList<androidx.media3.common.text.Cue>) = l.onCues(cues)
    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) = l.onCues(cueGroup)
    override fun onMetadata(metadata: androidx.media3.common.Metadata) = l.onMetadata(metadata)
}
