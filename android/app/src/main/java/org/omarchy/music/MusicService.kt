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

    override fun onCreate() {
        super.onCreate()
        Music.init(this)
        UsbDirect.init(this)
        DacPeqUsb.init(this)
        val renderers = ExoEngine.renderers(this, Music.eqProcessor)   // the EQ for float (hi-res) music too: see EqSink
        val exo = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(false)   // its own, below: bit-perfect taking the DAC isn't an unplug
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Music.paused = !exo.playWhenReady
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Bit-perfect taking the USB DAC over makes Android think the DAC was unplugged ("becoming noisy"), which
                // paused the music; that pause is undone (a real unplug still pauses)
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY && UsbDirect.tookOverLately() && UsbDirect.device() != null) {
                    exo.play(); return
                }
                Music.paused = !playWhenReady
                if (!playWhenReady) pausedAt = android.os.SystemClock.elapsedRealtime()
            }

            override fun onPlaybackStateChanged(state: Int) {
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
        // Headphones unplugged (Android's "becoming noisy"): paused, as ExoPlayer did, except when it's bit-perfect taking
        // the USB DAC from Android or handing it back, which Android takes for the DAC being unplugged (that paused every
        // song a second in). Unplugging the DAC while the app has it: see UsbDirect.
        androidx.core.content.ContextCompat.registerReceiver(this, object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (UsbDirect.ownsOrJustMoved() && UsbDirect.device() != null) {
                    return
                }
                if (exo.playWhenReady) exo.pause()
            }
        }, android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
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
        // The notification's and lock screen's Repeat and Favorite show how they stand.
        Music.scope.launch {
            snapshotFlow { Triple(Music.repeat, Music.current()?.id, Music.musicVersion) }.collect { runCatching { s.setCustomLayout(Car.buttons()) } }
        }
        // The home screen widget: the song, playing or paused, the queue.
        Music.scope.launch {
            snapshotFlow { listOf(Music.current()?.id, Music.paused, Music.repeat, Music.index, Music.queue.size) }.collect {
                kotlinx.coroutines.delay(150)
                runCatching { PlayerWidget.update(this@MusicService) }
            }
        }
        Music.player = ExoEngine(exo, this, Music.eqProcessor)
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
        override fun setMediaItems(items: MutableList<androidx.media3.common.MediaItem>, startIndex: Int, startPositionMs: Long) {
            val id = items.getOrNull(maxOf(0, startIndex))?.mediaId
            if (id != null && Car.isCarItem(id)) Car.play(id) else super.setMediaItems(items, startIndex, startPositionMs)
        }

        override fun setMediaItems(items: MutableList<androidx.media3.common.MediaItem>, resetPosition: Boolean) {
            val id = items.firstOrNull()?.mediaId
            if (id != null && Car.isCarItem(id)) Car.play(id) else super.setMediaItems(items, resetPosition)
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
            if (p !is RemotePlayer && Music.djTalking) Music.djHush(); super.pause()
        }

        // Between songs (one ran out, the next is loading) the session hears "buffering", not "ended":
        // see Music.advancing. The listeners the session adds get the same.
        private fun masked(state: Int) = if (state == Player.STATE_ENDED && p !is RemotePlayer && Music.advancing) Player.STATE_BUFFERING else state
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
