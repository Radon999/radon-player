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
class ExoEngine(val p: Player, private val ctx: Ctx, private val eq: EqProcessor? = null) : Engine {
    // Ignore Bluetooth devices: this player plays where the music's kept too (not the cast device's: it has no output here)
    init {
        (p as? ExoPlayer)?.let { BtRoute.attach(it) }
        // Sound only: a cover picture stored in a file as a one-frame video (or an image or lyrics track) kept the player
        // "playing" after the music had ended, its clock running on past the song's end for a second or two before
        // the next song came (Problems: "ran on past its end", nearly every song).
        runCatching {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).setTrackTypeDisabled(C.TRACK_TYPE_IMAGE, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        }
    }
    // The music is all played out: the output says so, or (an output that never says so: seen over Bluetooth with the
    // DJ on, where every song heard to its end was followed by a minute of nothing) the song's end was handed to it half
    // a second ago or more and the player's clock has reached the song's length.
    override val audioEnded: Boolean get() {
        val e = eq ?: return false
        if (e.sinkEnded) return true
        val d = p.duration
        return e.sinkEos && System.currentTimeMillis() - e.sinkEosAt >= 500 && d > 0 && p.currentPosition >= d - 50
    }
    // Music is still coming out: the output took more of it in the last second and a half, or the song's end was handed
    // to it under four seconds ago and it hasn't played out (what it still holds is a second or so, never a minute).
    override val audioPlaying: Boolean? get() = eq?.let {
        val now = System.currentTimeMillis()
        (!it.sinkEos && now - it.sinkFedAt < 1500) || (it.sinkEos && !it.sinkEnded && now - it.sinkEosAt < 4000)
    }

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
        val art = ArtProvider.uri(ctx, item.art)   // content:// so Android Auto can show it (it can't open file://)
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
        BtRoute.withoutFocus(x)
        x.setMediaItem(MediaItem.fromUri(uri), startMs)
        x.volume = 0f
        x.playWhenReady = false
        x.prepare()
        return ExoEngine(x, ctx, eq)
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

    override fun release() {
        (p as? ExoPlayer)?.let { BtRoute.detach(it) }
        p.release()
    }

    companion object {
        /** The phone's renderers: the EQ in the audio path, for 16-bit music and (EqSink) hi-res too. Smooth distortion's
         *  peak repair goes first, so resampling and the EQ get the rebuilt peaks and its 3 dB of room. */
        fun renderers(ctx: Ctx, eq: EqProcessor) = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                val inner = DefaultAudioSink.Builder(context)   // bit-perfect straight to a USB DAC: see UsbDirect
                    .setAudioProcessors(arrayOf(PeakRepairProcessor(eq), ResampleProcessor(context), eq))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                return EqSink(UsbAudioSink(inner), eq, inner)
            }

            // The decoder's renderer, with room for any FLAC frame (DecoderRoom): the stock one gives the decoder what
            // the file says its biggest frame is, or the decoder's own default when it doesn't say, and throws away
            // every frame that doesn't fit.
            override fun buildAudioRenderers(context: android.content.Context, extensionRendererMode: Int,
                                             mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector, enableDecoderFallback: Boolean,
                                             audioSink: AudioSink, eventHandler: android.os.Handler,
                                             eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                                             out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>) {
                val made = java.util.ArrayList<androidx.media3.exoplayer.Renderer>()
                super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, made)
                for (r in made) out.add(if (r is androidx.media3.exoplayer.audio.MediaCodecAudioRenderer)
                    RoomyAudioRenderer(context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink) else r)
            }
        }.setEnableAudioFloatOutput(true).setMediaCodecSelector { mime, secure, tunneling ->
            // Settings › Sound quality › Android's own FLAC decoder: the phone maker's (c2.sec…, OMX.SEC…, and any other
            // that isn't Android's) goes after Android's (c2.android…, OMX.google…), for FLAC only. Asked at each song.
            val all = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
            if (mime == androidx.media3.common.MimeTypes.AUDIO_FLAC && Prefs.on(Prefs.PLAIN_FLAC))
                all.sortedBy { if (it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google.")) 0 else 1 }
            else all
        }
    }
}

/** The stock audio renderer, giving the decoder room for any FLAC frame ([DecoderRoom]). */
class RoomyAudioRenderer(context: android.content.Context, factory: androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory,
                         selector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector, fallback: Boolean, handler: android.os.Handler?,
                         listener: androidx.media3.exoplayer.audio.AudioRendererEventListener?, sink: AudioSink) :
    androidx.media3.exoplayer.audio.MediaCodecAudioRenderer(context, factory, selector, fallback, handler, listener, sink) {
    override fun getCodecMaxInputSize(codecInfo: androidx.media3.exoplayer.mediacodec.MediaCodecInfo, format: androidx.media3.common.Format,
                                      streamFormats: Array<androidx.media3.common.Format>): Int {
        val stock = super.getCodecMaxInputSize(codecInfo, format, streamFormats)
        return if (format.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_FLAC) DecoderRoom.flac(format.channelCount, stock) else stock
    }
}
