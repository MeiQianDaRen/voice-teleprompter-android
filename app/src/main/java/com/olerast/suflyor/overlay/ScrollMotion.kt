package com.olerast.suflyor.overlay

import kotlin.math.abs

internal object ScrollMotion {
    fun duration(characterDelta: Int): Long = when {
        characterDelta < 0 || abs(characterDelta) > 10 -> 350L
        abs(characterDelta) > 3 -> 250L
        else -> 130L
    }
    // Keep exact highlighting; the dead zone must never suppress a necessary line change.
    fun shouldScroll(characterDelta: Int, lineChanged: Boolean) = lineChanged || abs(characterDelta) > 2
    fun position(from: Float, to: Float, elapsed: Long, duration: Long): Float {
        if (duration <= 0 || elapsed >= duration) return to
        val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
        return from + (to - from) * t * t * (3f - 2f * t)
    }
}
