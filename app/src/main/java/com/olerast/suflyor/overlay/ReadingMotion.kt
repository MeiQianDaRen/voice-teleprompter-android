package com.olerast.suflyor.overlay

import kotlin.math.abs
import kotlin.math.roundToInt

/** Frame-clock visual motion. Its forecast is never sent back to the speech tracker. */
class ReadingMotion {
    data class Sample(
        val position: Float = 0f,
        val rate: Float = 0f,
        val ceiling: Float = position,
        val forecast: Float = position,
        val revision: Int = 0,
    )

    private var sample: Sample? = null
    private var origin = 0f
    private var correction = 0f
    private var ceilingFrom = 0f
    private var started = 0L
    private var duration = 0L

    fun reset(position: Float = 0f) {
        sample = null
        origin = position
        correction = 0f
        ceilingFrom = position
        duration = 0L
    }

    fun follow(next: Sample, now: Long, animate: Boolean = true) {
        if (sample == next && animate) return
        val previous = sample
        val current = frame(now)
        val seek = previous != null && (next.revision != previous.revision || next.position < previous.position)
        val delta = next.forecast - current
        sample = next
        origin = if (animate && previous != null) current else next.position
        correction = if (!animate || previous == null) 0f
            else if (!seek && next.rate > 0f && (delta < 0f || abs(delta) <= 2f)) 0f else delta
        ceilingFrom = maxOf(origin, next.ceiling)
        started = now
        duration = if (!animate || previous == null) 0L else ScrollMotion.duration(if (seek) -3 else abs(delta).roundToInt())
    }

    fun frame(now: Long): Float {
        val s = sample ?: return origin
        val elapsed = (now - started).coerceAtLeast(0L)
        val fraction = ScrollMotion.position(0f, 1f, elapsed, duration)
        val ceiling = ceilingFrom + (s.ceiling - ceilingFrom) * fraction
        return (origin + s.rate.coerceAtLeast(0f) * elapsed / 1000f + correction * fraction)
            .coerceIn(0f, ceiling.coerceAtLeast(0f))
    }

    fun running(now: Long): Boolean = sample?.let {
        now - started < duration && correction != 0f || it.rate > 0f && frame(now) < it.ceiling
    } ?: false
}
