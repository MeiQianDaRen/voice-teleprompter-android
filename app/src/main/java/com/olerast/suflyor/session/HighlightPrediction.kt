package com.olerast.suflyor.session

import kotlin.math.roundToInt

/** Confirmed script anchors plus acoustic timestamps; prediction never changes the tracker. */
internal class HighlightPrediction {
    private var actual = 0
    private var lastAdvance = 0L
    private var lastObserved = 0L
    private var ratePosition = 0
    private var rateAt = 0L
    private var ended = true
    private var progressSeen = false
    private var rateSpeechAt: Long? = null
    private var rateSegment = 0L
    private var updateGap = 250f
    private var reliable = false
    private var confidenceScale = 1f
    private var sentenceEnds = emptyList<Int>()
    var averageRate = 0f
        private set

    fun setSentenceEnds(ends: List<Int>) { sentenceEnds = ends.distinct().sorted() }

    fun reset(position: Int, now: Long) {
        actual = position
        lastAdvance = now
        lastObserved = now
        ratePosition = position
        rateAt = now
        ended = true
        progressSeen = false
        rateSpeechAt = null
        rateSegment = 0L
        updateGap = 250f
        reliable = false
        confidenceScale = 1f
        averageRate = 0f
    }

    fun observe(position: Int, isFinal: Boolean, now: Long, speechTimeMs: Long? = null,
                segmentId: Long = 0L, confidence: Float? = null, trusted: Boolean = true) {
        val delta = position - actual
        if (delta < 0 || delta > 12 || now < lastObserved || now - lastAdvance > 2000L) reset(position, now)
        reliable = trusted && (confidence == null || confidence >= 0.35f)
        confidenceScale = if (confidence != null && confidence < 0.75f) 0.5f else 1f
        if (position > actual && reliable) {
            if (progressSeen) updateGap = updateGap * 0.5f + (now - lastAdvance).coerceIn(80L, 900L) * 0.5f
            val acoustic = speechTimeMs?.takeIf { it >= 0L }
            val sameClock = acoustic != null && rateSpeechAt != null && rateSegment == segmentId
            val duration = if (sameClock) acoustic - rateSpeechAt!! else now - rateAt
            val measure = progressSeen && if (acoustic == null) duration >= 120L else sameClock && duration >= 80L
            if (measure) {
                val measured = ((position - ratePosition) * 1000f / duration).coerceIn(0f, 16f)
                averageRate = if (averageRate == 0f) measured else averageRate * 0.3f + measured * 0.7f
            }
            if (!progressSeen || measure || acoustic != null && (!sameClock || duration < 0)) {
                ratePosition = position
                rateAt = now
                rateSpeechAt = acoustic
                rateSegment = segmentId
            }
            progressSeen = true
            lastAdvance = now
            ended = position in sentenceEnds
        }
        actual = position
        lastObserved = now
        if (isFinal) ended = true
    }

    fun motionRate(now: Long, active: Boolean, compensationPercent: Int = 0): Float =
        if (!active || !reliable || ended || !progressSeen || now - lastAdvance > staleMs) 0f
        else averageRate * confidenceScale * (1f + compensationPercent.coerceIn(-50, 100) / 100f)

    private val staleMs: Long get() = (updateGap * 2.5f).toLong().coerceIn(500L, 900L)
    val horizonSeconds: Float get() = updateGap.coerceIn(200f, 300f) / 1000f

    fun ceiling(position: Int, total: Int, maxAhead: Int): Int {
        val boundary = sentenceEnds.firstOrNull { it > position } ?: total
        return minOf(total - 1, boundary - 1, position + maxAhead.coerceIn(0, 6)).coerceAtLeast(position)
    }

    fun target(position: Int, total: Int, now: Long, active: Boolean, maxAhead: Int, compensationPercent: Int = 0): Int {
        return forecast(position, total, now, active, maxAhead, compensationPercent).roundToInt()
    }

    /** A fixed short distance budget per anchor, shared with the renderer's hard ceiling. */
    fun forecast(position: Int, total: Int, now: Long, active: Boolean, maxAhead: Int, compensationPercent: Int = 0): Float {
        if (position != actual) reset(position, now)
        val rate = motionRate(now, active, compensationPercent)
        if (rate <= 0f || position >= total || maxAhead <= 0) {
            if (!active || now - lastAdvance > staleMs) ended = true
            return position.toFloat()
        }
        return (position + rate * horizonSeconds).coerceAtMost(ceiling(position, total, maxAhead).toFloat())
    }
}
