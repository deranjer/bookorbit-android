package com.bookorbit.feature.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

@androidx.annotation.OptIn(UnstableApi::class)
internal fun audioRenderersFactory(context: Context) = SoftwareAudioRenderersFactory(context)

@androidx.annotation.OptIn(UnstableApi::class)
internal class SoftwareAudioRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    private var codecRenderer: RetryableCodecAudioRenderer? = null
    init {
        setEnableDecoderFallback(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    }
    fun preferSoftware(mime: String?) { codecRenderer?.preferSoftware(mime) }

    override fun buildAudioRenderers(context: Context, extensionRendererMode: Int, mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean, audioSink: AudioSink, eventHandler: Handler,
        eventListener: AudioRendererEventListener, out: ArrayList<Renderer>) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
            audioSink, eventHandler, eventListener, out)
        val index = out.indexOfFirst { it is MediaCodecAudioRenderer }
        check(index >= 0)
        codecRenderer = RetryableCodecAudioRenderer(context, getCodecAdapterFactory(), mediaCodecSelector,
            enableDecoderFallback, eventHandler, eventListener, audioSink).also { out[index] = it }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class RetryableCodecAudioRenderer(context: Context, adapter: MediaCodecAdapter.Factory,
    selector: MediaCodecSelector, fallback: Boolean, handler: Handler, listener: AudioRendererEventListener, sink: AudioSink) :
    MediaCodecAudioRenderer(context, adapter, selector, fallback, handler, listener, sink) {
    @Volatile private var softwareMime: String? = null
    fun preferSoftware(mime: String?) {
        if (softwareMime == mime) return
        softwareMime = mime
        onRendererCapabilitiesChanged()
    }
    override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int =
        if (softwareMime != null && softwareMime == format.sampleMimeType && format.cryptoType == C.CRYPTO_TYPE_NONE)
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        else super.supportsFormat(mediaCodecSelector, format)
}

internal fun shouldRetrySoftwareAudio(code: Int, audio: Boolean, platformRenderer: Boolean,
    supported: Boolean, alreadyRetried: Boolean): Boolean = audio && platformRenderer && supported && !alreadyRetried &&
    code in setOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES)

/** A codec may advertise support then fail. Retry once on the same source/time, never on HTTP errors. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SoftwareAudioFallback(private val player: ExoPlayer, selector: DefaultTrackSelector,
    private val factory: SoftwareAudioRenderersFactory) : Player.Listener {
    private var failedMediaId: String? = null
    private var retried = false

    init {
        selector.parameters = selector.parameters.buildUpon().setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true).build()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem?.mediaId == failedMediaId && retried) return
        retried = false
        failedMediaId = null
        factory.preferSoftware(null)
    }

    override fun onPlayerError(error: PlaybackException) {
        val rendererError = error as? ExoPlaybackException ?: return
        val format = rendererError.rendererFormat ?: return
        val mime = format.sampleMimeType ?: return
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) return
        val platform = rendererError.rendererName == "MediaCodecAudioRenderer"
        if (!shouldRetrySoftwareAudio(error.errorCode, mime.startsWith("audio/"), platform,
                platform && FfmpegLibrary.supportsFormat(mime), retried)) return
        val index = rendererError.rendererIndex
        if (index !in 0 until player.rendererCount || player.currentMediaItem == null) return
        retried = true
        val item = player.currentMediaItemIndex
        val position = player.currentPosition.coerceAtLeast(0)
        val play = player.playWhenReady
        failedMediaId = player.currentMediaItem!!.mediaId
        // Capability invalidation re-maps the group. Disabling a renderer alone leaves it unmapped.
        factory.preferSoftware(mime)
        player.seekTo(item, position)
        player.prepare()
        player.playWhenReady = play
    }
}
