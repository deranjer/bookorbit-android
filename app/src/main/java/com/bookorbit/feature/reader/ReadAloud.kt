package com.bookorbit.feature.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Reads the book aloud with the phone's text-to-speech engine. The page hands over one block at a
 * time as sentences; each is queued as its own utterance so the page can mark it as it starts
 * ([onMark]), and when the block's last sentence ends [onBlockDone] asks the page for the next.
 *
 * The web reader speaks through the server's voices instead; this works offline and needs no setup.
 * All callbacks arrive on the main thread.
 */
class ReadAloud(
    context: Context,
    private val onMark: (String) -> Unit,
    private val onBlockDone: () -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var failed = false
    private var segments: List<SpeechSegment> = emptyList()
    private var language: String? = null

    /** The sentence playing (or to resume from) in [segments]. */
    private var current = 0

    /** Bumped on every flush, so callbacks from utterances already dropped are ignored. */
    private var generation = 0
    private var queued: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { onInit(status) }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                main.post { started(utteranceId) }
            }

            override fun onDone(utteranceId: String) {
                main.post { finished(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                main.post { finished(utteranceId) }
            }
        })
    }

    /** Speak [block] from its first sentence, replacing anything playing. */
    fun speak(block: List<SpeechSegment>, lang: String?) {
        segments = block
        current = 0
        if (lang != language) {
            language = lang
            if (ready) applyLanguage()
        }
        play()
    }

    fun pause() {
        generation++
        tts.stop()
    }

    fun resume() = play()

    fun stop() {
        generation++
        segments = emptyList()
        queued = null
        tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun play() {
        if (failed) return onUnavailable()
        if (!ready) {
            queued = ::play
            return
        }
        val gen = ++generation
        segments.drop(current).forEachIndexed { i, s ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(s.text, mode, null, "$gen:${current + i}")
        }
        if (current >= segments.size) onBlockDone()
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            if (queued != null) onUnavailable()
            queued = null
            return
        }
        ready = true
        applyLanguage()
        queued?.invoke()
        queued = null
    }

    private fun applyLanguage() {
        val locale = language?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag)
        if (locale != null && tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(locale)
        else tts.setLanguage(Locale.getDefault())
    }

    private fun parse(utteranceId: String): Int? {
        val (gen, index) = utteranceId.split(':').takeIf { it.size == 2 } ?: return null
        return index.toIntOrNull()?.takeIf { gen.toIntOrNull() == generation }
    }

    private fun started(utteranceId: String) {
        val index = parse(utteranceId) ?: return
        current = index
        segments.getOrNull(index)?.mark?.let(onMark)
    }

    private fun finished(utteranceId: String) {
        val index = parse(utteranceId) ?: return
        if (index == segments.lastIndex) {
            current = segments.size
            onBlockDone()
        }
    }
}
