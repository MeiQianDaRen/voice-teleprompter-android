package com.olerast.suflyor.session

import kotlin.math.roundToInt

/** Learn cadence only from confirmed ASR progress. No manual WPM or target-duration baseline. */
internal class HighlightPrediction {
    private var actual = 0
    private var lastAdvance = 0L
    private var lastObserved = 0L
    private var ratePosition = 0
    private var rateAt = 0L
    private var ended = true
    private var progressSeen = false
    private var lastTarget = 0
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
        lastTarget = position
        averageRate = 0f
    }

    fun observe(position: Int, isFinal: Boolean, now: Long) {
        val delta = position - actual
        if (delta < 0 || delta > 12 || now < lastObserved || now - lastAdvance > 2000L) reset(position, now)
        if (position > actual) {
            val duration = now - rateAt
            if (!progressSeen) {
                ratePosition = position
                rateAt = now
            } else if (duration >= 120L) {
                val measured = ((position - ratePosition) * 1000f / duration).coerceIn(0f, 16f)
                averageRate = if (averageRate == 0f) measured else averageRate * 0.3f + measured * 0.7f
                ratePosition = position
                rateAt = now
            }
            progressSeen = true
            lastAdvance = now
            ended = position in sentenceEnds
        }
        actual = position
        lastObserved = now
        if (isFinal) ended = true
        if (ended) lastTarget = position
    }

    fun motionRate(now: Long, active: Boolean): Float =
        if (!active || ended || !progressSeen || now - lastAdvance > 1200L) 0f else averageRate

    fun ceiling(position: Int, total: Int, maxAhead: Int): Int {
        val boundary = sentenceEnds.firstOrNull { it > position } ?: total
        return minOf(total - 1, boundary - 1, position + maxAhead.coerceIn(0, 6)).coerceAtLeast(position)
    }

    fun target(position: Int, total: Int, now: Long, active: Boolean, maxAhead: Int): Int {
        if (position != actual) reset(position, now)
        val age = now - lastAdvance
        if (!active || ended || age > 1200L || position >= total || maxAhead <= 0) {
            if (!active || age > 1200L) ended = true
            lastTarget = position
            return position
        }
        val lead = (averageRate * (0.45f + age.coerceAtLeast(0) / 1000f)).roundToInt()
        lastTarget = maxOf(position, lastTarget, position + lead).coerceAtMost(ceiling(position, total, maxAhead))
        return lastTarget
    }
}
