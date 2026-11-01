package com.olerast.suflyor.overlay

/** Vertical motion accepts confirmed line targets only, independently of speculative text colour. */
internal class ViewportMotion {
    private var from = 0f
    private var target = 0f
    private var started = 0L
    private var duration = 0L

    fun reset(position: Float) {
        from = position
        target = position
        duration = 0L
    }

    fun follow(position: Float, now: Long) {
        if (position == target) return
        from = frame(now)
        duration = if (position < target) 350L else 180L
        target = position
        started = now
    }

    fun frame(now: Long) = ScrollMotion.position(from, target, now - started, duration)
    fun running(now: Long) = now - started < duration
}
