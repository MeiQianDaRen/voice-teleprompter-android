package com.olerast.suflyor.speech

import com.olerast.suflyor.script.SpeechLang

/** Streaming speech recognizer: feed audio, get the current utterance text; [AsrUpdate.isFinal] ends the utterance. */
interface AsrEngine {
    val description: String

    /** Language of the model: its text is normalized with [SpeechLang.words], and a script in another one needs another engine. */
    val lang: SpeechLang

    /** The "boost script words" setting the engine was created with; a change of the setting needs a new engine. */
    val hotwordsSetting: Boolean

    /** Audio thread: publish changed text, token metadata, or an utterance boundary. */
    fun accept(samples: FloatArray, sampleRate: Int): AsrUpdate?

    /** Words of the current script to bias recognition towards (may be ignored by the engine). */
    fun setBiasWords(words: List<String>)

    fun reset()

    /** Frees the model. Safe to call twice; afterwards [accept] returns null and the other calls do nothing. */
    fun release()
}

/** Scores are sherpa token log probabilities, not calibrated transcript correctness. */
data class AsrToken(val text: String, val timestampSeconds: Float? = null, val logProbability: Float? = null)

data class AsrUpdate(val text: String, val isFinal: Boolean, val tokens: List<AsrToken> = emptyList(), val segmentId: Long = 0L) {
    private val spoken: List<AsrToken> get() = tokens.filter { token -> token.text.any { it.isLetterOrDigit() } && !token.text.startsWith("<") }

    /** Geometric mean of the recent acoustic scores; absent or invalid metadata stays unknown. */
    val confidence: Float? get() {
        val recent = spoken.takeLast(8)
        val scores = recent.mapNotNull { it.logProbability?.takeIf { p -> !p.isNaN() && p <= 0f } }
        return if (scores.isEmpty() || scores.size != recent.size) null else kotlin.math.exp(scores.average()).toFloat().coerceIn(0f, 1f)
    }

    val speechTimeMs: Long? get() {
        val times = spoken.map { it.timestampSeconds ?: return null }
        if (times.isEmpty() || times.any { !it.isFinite() || it < 0f } || times.zipWithNext().any { it.second < it.first }) return null
        return (times.last() * 1000.0).toLong()
    }
}

/** Relative acoustic reliability, learned only from accepted strong text anchors; not a correctness probability. */
internal class AcousticConfidence {
    private val anchors = ArrayDeque<Float>()
    fun reset() = anchors.clear()
    fun score(raw: Float?): Float? {
        if (raw == null || !raw.isFinite() || raw !in 0f..1f) return null
        val reference = if (anchors.size < 4) 0.75f else anchors.sorted()[anchors.size / 2].coerceIn(0.1f, 0.95f)
        return (raw / reference * 0.85f).coerceIn(0f, 1f)
    }
    fun observe(raw: Float?, textQuality: Float) {
        if (raw == null || !raw.isFinite() || raw !in 0.05f..1f || textQuality < 0.95f) return
        anchors.addLast(raw)
        while (anchors.size > 32) anchors.removeFirst()
    }
}
