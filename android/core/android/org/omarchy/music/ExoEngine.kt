package org.omarchy.music

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** The phone's engine: an ExoPlayer (or the cast device's player) under the app. */
class ExoEngine(val p: Player, private val ctx: Ctx) : Engine {
    override val isPlaying get() = p.isPlaying
    override val positionMs get() = p.currentPosition
    override val durationMs get() = p.duration.coerceAtLeast(0)
    override val state get() = p.playbackState
    override val hasItem get() = p.mediaItemCount > 0
    override val currentUri get() = p.currentMediaItem?.localConfiguration?.uri?.toString()
    override var volume: Float
        get() = p.volume
        set(v) { p.volume = v }
    override var playWhenReady: Boolean
        get() = p.playWhenReady
        set(v) { p.playWhenReady = v }

    override fun play() = p.play()
    override fun pause() = p.pause()
    override fun seekTo(ms: Long) = p.seekTo(ms)

    override fun load(item: PlayItem, startMs: Long) {
        val art = item.art?.let { if (it.startsWith("/")) android.net.Uri.fromFile(java.io.File(it)) else android.net.Uri.parse(it) }
        val mi = MediaItem.Builder().setUri(item.uri).setMediaId(item.id)
            .apply { item.mime?.let { setMimeType(it) } }
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album)
                .setArtworkUri(art).build())
            .build()
        p.setMediaItem(mi, startMs)
        p.prepare()
    }

    override fun setSpeed(speed: Float, pitch: Float) { p.playbackParameters = PlaybackParameters(speed, pitch) }

    override val audioRate get() = (p as? ExoPlayer)?.audioFormat?.sampleRate ?: 0
    override val audioBits get() = (p as? ExoPlayer)?.audioFormat?.let { pcmBits(it.pcmEncoding) } ?: 0

    /** Crossfade's ending song: a player of its own with the same EQ, Boost, limiter and the rest as the main one
     *  (a copy of its processor), so the ending sounds just as it did; before, it played without them and came in
     *  loud for a moment. */
    override fun tail(uri: String, startMs: Long): Engine? {
        if (p !is ExoPlayer) return null
        val eq = EqProcessor().apply { copyFrom(Music.eqProcessor) }
        val x = ExoPlayer.Builder(ctx, renderers(ctx, eq))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            .build()
        x.setMediaItem(MediaItem.fromUri(uri), startMs)
        x.volume = 0f
        x.playWhenReady = false
        x.prepare()
        return ExoEngine(x, ctx)
    }

    override fun onceSounding(run: () -> Unit) {
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) return
                p.removeListener(this)
                run()
            }
        })
    }

    override fun release() = p.release()

    companion object {
        /** The phone's renderers: the EQ in the audio path, for 16-bit music and (EqSink) hi-res too. */
        fun renderers(ctx: Ctx, eq: EqProcessor) = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                EqSink(DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(eq))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build(), eq)
        }.setEnableAudioFloatOutput(true)
    }
}
