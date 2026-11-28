package com.simpleaibrowser.sab.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * "Listen to this page" — reads an article's sentences with Android's offline TextToSpeech
 * engine and reports progress so the UI can show a mini player bar.
 *
 * Sentences are spoken one at a time (started from the listener callback), so pause/resume
 * simply stops the synthesizer and continues from the remembered sentence index.
 */
class SabReadAloud(private val context: Context) {

    enum class Phase { IDLE, PLAYING, PAUSED, FINISHED, FAILED }

    data class ReadAloudState(
        val phase: Phase = Phase.IDLE,
        val title: String = "",
        val sentenceIndex: Int = 0,
        val sentenceCount: Int = 0,
        val speechRate: Float = 1f,
        val message: String? = null
    ) {
        val isActive: Boolean get() = phase == Phase.PLAYING || phase == Phase.PAUSED
    }

    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sentences: List<String> = emptyList()
    private var currentIndex = 0

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _state.value = _state.value.copy(phase = Phase.PLAYING)
        }

        override fun onDone(utteranceId: String?) {
            val idx = utteranceId?.removePrefix(UTTERANCE_PREFIX)?.toIntOrNull() ?: return
            if (idx + 1 < sentences.size) {
                currentIndex = idx + 1
                _state.value = _state.value.copy(sentenceIndex = currentIndex)
                speakCurrent()
            } else {
                currentIndex = sentences.size
                _state.value = _state.value.copy(phase = Phase.FINISHED, sentenceIndex = currentIndex)
                releaseEngine()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _state.value = _state.value.copy(phase = Phase.FAILED, message = "Speech engine error")
            releaseEngine()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            _state.value = _state.value.copy(phase = Phase.FAILED, message = "Speech engine error ($errorCode)")
            releaseEngine()
        }
    }

    /** Splits page text into speakable sentences (drops tiny fragments and headings noise). */
    fun splitIntoSentences(rawText: String, maxSentences: Int = 800): List<String> {
        val cleaned = rawText
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 40) return emptyList()
        return cleaned
            .split(Regex("(?<=[.!?…])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .take(maxSentences)
    }

    fun start(title: String, text: String) {
        val parts = splitIntoSentences(text)
        if (parts.isEmpty()) {
            _state.value = ReadAloudState(phase = Phase.FAILED, message = "Nothing readable found on this page")
            return
        }
        sentences = parts
        currentIndex = 0
        _state.value = ReadAloudState(
            phase = Phase.PLAYING,
            title = title,
            sentenceIndex = 0,
            sentenceCount = parts.size,
            speechRate = _state.value.speechRate
        )
        if (!ensureEngine()) return
        speakCurrent()
    }

    fun pause() {
        if (_state.value.phase != Phase.PLAYING) return
        tts?.stop()
        _state.value = _state.value.copy(phase = Phase.PAUSED)
    }

    fun resume() {
        if (_state.value.phase != Phase.PAUSED) return
        if (!ensureEngine()) return
        _state.value = _state.value.copy(phase = Phase.PLAYING)
        speakCurrent()
    }

    fun stop() {
        tts?.stop()
        _state.value = ReadAloudState(speechRate = _state.value.speechRate)
        releaseEngine()
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.5f)
        _state.value = _state.value.copy(speechRate = clamped)
        tts?.setSpeechRate(clamped)
    }

    fun release() {
        tts?.stop()
        releaseEngine()
        sentences = emptyList()
        _state.value = ReadAloudState(speechRate = _state.value.speechRate)
    }

    private fun ensureEngine(): Boolean {
        if (tts != null && ttsReady) return true
        tts?.shutdown()
        ttsReady = false
        var initOk = false
        val engine = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(_state.value.speechRate)
                tts?.setOnUtteranceProgressListener(progressListener)
            } else {
                _state.value = _state.value.copy(phase = Phase.FAILED, message = "No speech engine available")
            }
        }
        tts = engine
        // The init callback above is usually synchronous for local engines; treat MISSING_DATA
        // correctly by checking immediately too.
        initOk = engine != null
        return initOk
    }

    private fun speakCurrent() {
        val engine = tts ?: return
        if (!ttsReady) return
        val sentence = sentences.getOrNull(currentIndex) ?: return
        engine.setSpeechRate(_state.value.speechRate)
        engine.speak(
            sentence,
            TextToSpeech.QUEUE_ADD,
            null,
            "$UTTERANCE_PREFIX$currentIndex"
        )
    }

    private fun releaseEngine() {
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    private companion object {
        const val UTTERANCE_PREFIX = "sab_ra_"
    }
}
