package com.olerast.suflyor.overlay

import com.olerast.suflyor.script.Token
import kotlin.math.floor

/** Confirmed reading is animated separately from the visual forecast. */
internal class KaraokeProgress {
    private var from = 0f
    private var target = 0f
    private var revision = -1
    private var started = 0L
    private var duration = 0L

    fun update(position: Float, epoch: Int, now: Long, animate: Boolean = true) {
        if (position == target && epoch == revision && animate) return
        from = if (revision >= 0 && animate) frame(now) else position
        duration = if (!animate || revision < 0) 0L else if (position < target || epoch != revision) 180L else 130L
        target = position
        revision = epoch
        started = now
    }

    fun frame(now: Long) = ScrollMotion.position(from, target, now - started, duration)
    fun running(now: Long) = now - started < duration

    companion object {
        /** Preserve original UTF-16 offsets, including symbols and skipped stage directions. */
        fun offset(tokens: List<Token>, spoken: IntArray, position: Float): Float {
            if (spoken.isEmpty()) return 0f
            if (position >= spoken.size) return tokens[spoken.last()].end.toFloat()
            val at = floor(position).toInt().coerceAtLeast(0)
            val token = tokens[spoken[at]]
            return token.start + (token.end - token.start) * (position - at).coerceIn(0f, 1f)
        }
    }
}
