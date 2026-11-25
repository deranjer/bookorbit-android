package com.bookorbit.feature.player

import android.content.Context
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

/** Original silent fixtures: decode/seek evidence, not audible-output acceptance. */
@androidx.annotation.OptIn(UnstableApi::class)
class SoftwareAudioTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }

    private fun await(label: String, block: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (main(block)) return
            Thread.sleep(25)
        }
        fail("Timed out: $label")
    }

    @Test fun packagedNativeLibraryProvidesTheDeclaredAudioCodecs() {
        assertTrue("JNI library must actually load", FfmpegLibrary.isAvailable())
        assertTrue(FfmpegLibrary.getVersion()!!.startsWith("Lavc"))
        for (mime in listOf(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_AC3,
            MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_TRUEHD, MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_ALAC,
            MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS, MimeTypes.AUDIO_AMR_NB,
            MimeTypes.AUDIO_AMR_WB, MimeTypes.AUDIO_MLAW, MimeTypes.AUDIO_ALAW)) {
            assertTrue("Packaged decoder missing: $mime", FfmpegLibrary.supportsFormat(mime))
        }
    }

    @Test fun defaultFactoryDecodesAlacWithoutPlatformSupportAndKeepsTrackAndSeekCoordinates() {
        playback("m4a", Base64.decode(ALAC, Base64.DEFAULT), 1_000)
    }

    @Test fun bundledRendererDecodesAc3ToPcmAndSeeksWithoutConvertingTheSource() {
        val frame = Base64.decode(AC3_FRAME, Base64.DEFAULT)
        playback("ac3", ByteArray(frame.size * 256) { frame[it % frame.size] }, 2_000, preferSoftware = true)
    }

    @Test fun injectedPlatformDecoderFailureRecoversOnSoftwareAtTheSameSourceClock() {
        val bytes = GZIPInputStream(ByteArrayInputStream(Base64.decode(AAC_GZIP, Base64.DEFAULT))).use { it.readBytes() }
        playback("m4a", bytes, 1_000, injectFailure = true)
    }

    private fun playback(extension: String, bytes: ByteArray, seekMs: Long, injectFailure: Boolean = false, preferSoftware: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "software-audio-${UUID.randomUUID()}.$extension").apply { writeBytes(bytes) }
        var player: ExoPlayer? = null
        val decoder = AtomicReference<String>()
        lateinit var fallback: SoftwareAudioFallback
        try {
            val raw = main {
                val selector = DefaultTrackSelector(context)
                val renderers = audioRenderersFactory(context).apply {
                    if (preferSoftware) setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                }
                ExoPlayer.Builder(context, renderers).setTrackSelector(selector).build().also {
                    player = it
                    fallback = SoftwareAudioFallback(it, selector, renderers)
                    it.addListener(fallback)
                    it.addAnalyticsListener(object : AnalyticsListener {
                        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
                            initializedTimestampMs: Long, initializationDurationMs: Long) { decoder.set(decoderName) }
                    })
                    it.setMediaItems(listOf("first", "second").map { id ->
                        MediaItem.Builder().setMediaId(id).setUri(file.toURI().toString()).build()
                    })
                    it.prepare()
                    it.play()
                }
            }
            await("decoder output for $extension") {
                raw.playerError?.let { fail("Decoder failed: ${it.errorCodeName}") }
                raw.audioDecoderCounters?.ensureUpdated()
                decoder.get() != null && (raw.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
            }
            if (injectFailure) {
                assertFalse("AAC should prefer the platform decoder", decoder.get().startsWith("ffmpeg"))
                main { raw.seekTo(0, 1_500) }
                await("source clock before injected fault") { raw.currentPosition >= 1_500 && raw.isPlaying }
                val before = main { raw.currentPosition }
                main {
                    val renderer = (0 until raw.rendererCount).first { raw.getRendererType(it) == C.TRACK_TYPE_AUDIO }
                    val failure = ExoPlaybackException.createForRenderer(IllegalStateException("Synthetic decoder init failure"),
                        "MediaCodecAudioRenderer", renderer, raw.audioFormat, C.FORMAT_HANDLED, false,
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
                    fallback.onPlayerError(failure)
                    fallback.onPlayerError(failure)
                }
                await("fallback after injected fault") { decoder.get().startsWith("ffmpeg") && raw.isPlaying }
                main {
                    assertEquals("first", raw.currentMediaItem!!.mediaId)
                    assertTrue(raw.currentPosition in before..(before + 1_000))
                }
            }
            assertTrue("Expected bundled decoder, got ${decoder.get()}", decoder.get()?.startsWith("ffmpeg") == true)
            main { raw.pause(); raw.seekTo(1, seekMs); raw.play() }
            await("same queue and source clock after seek") {
                raw.playerError?.let { fail("Seek failed: ${it.errorCodeName}") }
                raw.currentMediaItem?.mediaId == "second" && raw.currentPosition >= seekMs && raw.isPlaying
            }
            if (injectFailure) await("new track restores platform preference") { !decoder.get().startsWith("ffmpeg") }
            main {
                assertEquals(2, raw.mediaItemCount)
                assertEquals(file.toURI().toString(), raw.currentMediaItem!!.localConfiguration!!.uri.toString())
            }
        } finally {
            main { player?.release() }
            file.delete()
        }
    }

    companion object {
        // Encoded from anullsrc with FFmpeg 8.1.3, not from user or copyrighted sample audio.
        private const val AC3_FRAME = "C3eWuQBAL4QpA/51fPnz58+fPnz58+fPnz58+fPnz4y/upAAADa1rWta1rWta1qUAAAA2ta1rWta1rWtalAAAANrWta1rWta1rWpQAAADa1rWta1rWta1qUAAAA2ta1rWta1rWtalAAAANrWta1rWta1rWpAAAAAAAAAAAAAsrA="
        private const val AAC_GZIP = "H4sIAAAAAAAC/+VUvUoDQRCevfzYiKRI0CJihAQEiSRRgjYSSGNhJ1qozSV7R0Juc2F3ElREUvoSPoAPITZWvkPsBB/D2TVH9k4QrCxcdpmfnd39Zr65A4CSj9ejvgoFgANa0mrSaojRXp18TyIMJwAQiEmPQ2ykPoxgZi4Gi0cl7Rb8OBw68IzSHZB+iQPzZuqH242P/fpddiF43yWlJHgyrw0TevpijGqPBzLaUeF4aEeekM2P3CEPPB3DsqI/9EnJKRG7tMy//EUuPd+CtzyWQWmuvyrsBCTvFCpuxQyJCPdbSgxyC6jQ9BRXhpLpdFqhzTTJ1VbBbB5O9cqQJ1O4P3uHLGmMygz5DkoEK8YgR1QWcrJV17LzZN8kCk0x3dCyt8TEu9KlRWlkhDxJUGfMUWd2LjyMMty0y00USXc0CuxD1X6gNOjKI4b60XXumsPm8mN34jcbO3WatV3yITWv4UP4Paum7JIaTPvL+OU/WKBzZgBprRScecboc4xDZ0WUuhFS2r1J9m10Zi7T/0+yuYQHQYy8OZqLLnHR2DdcOLXWClsrLf3lInBt4UvdLdtIksU/qkR7OjNm/ivUOzLq7vYntyOwPjEFAAA="
        private const val ALAC = "AAAAIGZ0eXBpc29tAAACAGlzb21pc282aXNvMm1wNDEAAAKrbW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAAAAAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAa10cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAEBAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAFJbWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAAAfQAAAAABVxAAAAAAALWhkbHIAAAAAAAAAAHNvdW4AAAAAAAAAAAAAAABTb3VuZEhhbmRsZXIAAAAA9G1pbmYAAAAQc21oZAAAAAAAAAAAAAAAJGRpbmYAAAAcZHJlZgAAAAAAAAABAAAADHVybCAAAAABAAAAuHN0YmwAAABsc3RzZAAAAAAAAAABAAAAXGFsYWMAAAAAAAAAAQAAAAAAAAAAAAEAEAAAAAAfQAAAAAAAJGFsYWMAAAAAAAAQAAAQKAoOAQAAAAAgBAAB9AAAAB9AAAAAFGJ0cnQAAAAAAAH0AAAB9AAAAAAQc3R0cwAAAAAAAAAAAAAAEHN0c2MAAAAAAAAAAAAAABRzdHN6AAAAAAAAAAAAAAAAAAAAEHN0Y28AAAAAAAAAAAAAAChtdmV4AAAAIHRyZXgAAAAAAAAAAQAAAAEAAAAAAAAAAAAAAAAAAABidWR0YQAAAFptZXRhAAAAAAAAACFoZGxyAAAAAAAAAABtZGlyYXBwbAAAAAAAAAAAAAAAAC1pbHN0AAAAJal0b28AAAAdZGF0YQAAAAEAAAAATGF2ZjYyLjEyLjEwMwAAAKxtb29mAAAAEG1maGQAAAAAAAAAAQAAAJR0cmFmAAAAJHRmaGQAAAA5AAAAAQAAAAAAAALLAAAQAAAAABMCAAAAAAAAFHRmZHQBAAAAAAAAAAAAAAAAAABUdHJ1bgAAAwEAAAAIAAAAtAAAEAAAAAATAAAQAAAAABMAABAAAAAAEwAAEAAAAAATAAAQAAAAABMAABAAAAAAEwAAEAAAAAATAAANAAAAABcAAACkbWRhdAAAAAAADwgBAAAAAAAAAP+H//AAAAAAAA8IAQAAAAAAAAD/h//wAAAAAAAPCAEAAAAAAAAA/4f/8AAAAAAADwgBAAAAAAAAAP+H//AAAAAAAA8IAQAAAAAAAAD/h//wAAAAAAAPCAEAAAAAAAAA/4f/8AAAAAAADwgBAAAAAAAAAP+H//AAABAAABoAAAAPCAEAAAAAAAAA/4Z/8AAAAENtZnJhAAAAK3RmcmEBAAAAAAAAAQAAAAAAAAABAAAAAAAAAAAAAAAAAAACywEBAQAAABBtZnJvAAAAAAAAAEM="
    }
}
