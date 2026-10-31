package com.olerast.suflyor.session

import kotlin.math.roundToInt

/** Visual look-ahead only. Never supplies a position to the speech aligner. */
internal class HighlightPrediction {
    private data class Sample(val position: Int, val at: Long)
    private val samples = ArrayDeque<Sample>()
    private var actual = 0
    private var lastAdvance = 0L
    private var lastObserved = 0L
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
        lastTarget = position
        samples.clear()
        averageRate = 0f
        ended = true
        progressSeen = false
    }

    fun observe(position: Int, isFinal: Boolean, now: Long) {
        val delta = position - actual
        if (delta < 0 || delta > 12 || now < lastObserved || now - lastAdvance > 2000L) reset(position, now)
        if (position > actual) progressSeen = true
        if (position > actual || samples.isEmpty() && !isFinal) {
            samples.addLast(Sample(position, now))
            while (samples.size > 1 && now - samples.first().at > 4000L) samples.removeFirst()
            val first = samples.first()
            val duration = now - first.at
            if (samples.size >= 3 && duration >= 400L) {
                val measured = ((position - first.position) * 1000f / duration).coerceIn(0f, 12f)
                averageRate = if (averageRate == 0f) measured else averageRate * 0.7f + measured * 0.3f
            }
            lastAdvance = now
            ended = position in sentenceEnds
        }
        actual = position
        lastObserved = now
        if (isFinal) ended = true
        if (ended) lastTarget = position
    }

    fun motionRate(now: Long, active: Boolean, fallback: Float): Float =
        if (!active || ended || !progressSeen || now - lastAdvance > 1200L) 0f else averageRate.takeIf { it > 0f } ?: fallback

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
        val ceiling = ceiling(position, total, maxAhead)
        val lead = (averageRate * (0.45f + age.coerceAtLeast(0) / 1000f)).roundToInt()
        lastTarget = maxOf(position, lastTarget, position + lead).coerceAtMost(ceiling)
        return lastTarget
    }
}
