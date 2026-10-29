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

    override fun onCreate() {
        super.onCreate()
        Music.init(this)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(Music.eqProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
        }.setEnableAudioFloatOutput(true)   // hi-res FLAC stays 24-bit through the EQ
        val exo = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Music.paused = !exo.playWhenReady
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Music.paused = !playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) Music.trackEnded()
                if (state == Player.STATE_READY) exo.duration.takeIf { it > 0 }?.let { Music.duration = it / 1000.0 }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // What's really playing: the format, bit depth and kHz (or kbps), for the mark under the seek bar.
                val f = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)
                Music.nowFormat = f?.let { formatLabel(it) } ?: ""
                Music.nowFormatOf = Music.current()?.id ?: ""
                // Bit-perfect: ask for the DAC at this song's own rate; else ReplayGain from its tags.
                if (f != null) {
                    val rate = f.sampleRate; val bits = if (f.pcmEncoding != androidx.media3.common.Format.NO_VALUE) Music.pcmBits(f.pcmEncoding) else 0
                    if (Music.bitPerfect) Music.bg({ Music.dacNote = AudioQuality.applyBitPerfect(this@MusicService, true, rate, bits) })
                    Music.eqProcessor.rgGain = if (Music.bitPerfect || Music.replayGain == "off") 1.0 else
                        (AudioQuality.replayGain(f.metadata, Music.replayGain == "album")
                            ?: AudioQuality.replayGain(f.metadata, Music.replayGain != "album"))?.let { Math.pow(10.0, it / 20) }?.coerceAtMost(1.0) ?: 1.0
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (Music.streaming) Music.streamBroke()
                else {
                    Music.toast("Couldn't play “${Music.current()?.title}”: ${error.errorCodeName}")
                    Music.paused = true
                }
            }
        })
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
        switching = Music.scope.launch {
            snapshotFlow { Remote.active }.collect { on -> s.player = if (on) remote else local }
        }
        Music.player = exo
        Music.playerReady()
    }

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

        override fun seekToNext() = Music.next()
        override fun seekToNextMediaItem() = Music.next()
        override fun seekToPrevious() = Music.previous()
        override fun seekToPreviousMediaItem() = Music.previous()
        override fun play() { if (p is RemotePlayer || p.mediaItemCount > 0) super.play() else Music.togglePause() }

        // Between songs (one ran out, the next is loading) the session hears "buffering", not "ended":
        // see Music.advancing. The listeners the session adds get the same.
        private fun masked(state: Int) = if (state == Player.STATE_ENDED && p !is RemotePlayer && Music.advancing) Player.STATE_BUFFERING else state
        override fun getPlaybackState(): Int = masked(super.getPlaybackState())
        private val wrapped = HashMap<Player.Listener, Player.Listener>()
        override fun addListener(listener: Player.Listener) {
            val w = object : Player.Listener by listener {
                override fun onPlaybackStateChanged(playbackState: Int) = listener.onPlaybackStateChanged(masked(playbackState))
                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) =
                    listener.onPlayerStateChanged(playWhenReady, masked(playbackState))
            }
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away while paused: stop; while playing, the music keeps going.
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        Music.player = null
        switching?.cancel()
        players.forEach { it.release() }
        session?.release()
        session = null
        super.onDestroy()
    }
}
