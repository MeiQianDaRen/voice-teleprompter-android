package com.olerast.suflyor.overlay

/** A sliding rounded focus within a line, fading across lines instead of sweeping diagonally over text. */
internal class HighlightMotion {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val row: Int)
    data class Frame(val box: Box?, val alpha: Float, val previousBox: Box? = null, val previousAlpha: Float = 0f)
    private var from: Box? = null
    private var to: Box? = null
    private var alphaFrom = 0f
    private var started = 0L
    private var duration = 0L
    private var fadingBox: Box? = null
    private var fadingAlpha = 0f

    fun move(target: Box?, now: Long, animate: Boolean) {
        if (target == to && animate) return
        val current = frame(now)
        fadingBox = current.box?.takeIf { target != null && it.row != target.row }
        fadingAlpha = current.alpha
        from = current.box?.takeIf { target == null || it.row == target.row }
        alphaFrom = if (from != null) current.alpha else 0f
        to = target
        started = now
        duration = if (animate) 180L else 0L
    }

    fun frame(now: Long): Frame {
        val t = ScrollMotion.position(0f, 1f, now - started, duration)
        val destination = to
        val origin = from
        if (destination == null) return Frame(origin, alphaFrom * (1f - t))
        if (origin == null) return Frame(destination, t, fadingBox, fadingAlpha * (1f - t))
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return Frame(Box(mix(origin.left, destination.left), mix(origin.top, destination.top),
            mix(origin.right, destination.right), mix(origin.bottom, destination.bottom), destination.row),
            alphaFrom + (1f - alphaFrom) * t)
    }

    fun running(now: Long) = now - started < duration && (to != null || from != null)
}
