package com.olerast.suflyor.overlay

/** All gestures use screen coordinates, so dragging stays under the finger even when content turns ±90°. */
object OverlayGeometry {
    const val LEFT = 1
    const val TOP = 2
    const val RIGHT = 4
    const val BOTTOM = 8
    const val DONE = 16
    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    /** Large controls sit inside the window, leaving the device-edge navigation area untouched. */
    fun handleAt(x: Float, y: Float, width: Int, height: Int, inset: Int, touchSize: Int): Int {
        val half = touchSize / 2f
        fun near(cx: Float, cy: Float) = kotlin.math.abs(x - cx) <= half && kotlin.math.abs(y - cy) <= half
        return when {
            near(inset.toFloat(), height / 2f) -> LEFT
            near(width - inset.toFloat(), height / 2f) -> RIGHT
            near(width / 2f, inset.toFloat()) -> TOP
            near(width / 2f, height - inset.toFloat()) -> BOTTOM
            near(width / 2f, height / 2f) -> DONE
            else -> 0
        }
    }

    fun fit(rect: Rect, screenW: Int, screenH: Int, minW: Int, minH: Int): Rect {
        val width = rect.width.coerceIn(minW.coerceIn(1, screenW), screenW)
        val height = rect.height.coerceIn(minH.coerceIn(1, screenH), screenH)
        return Rect(rect.x.coerceIn(0, screenW - width), rect.y.coerceIn(0, screenH - height), width, height)
    }

    fun resize(rect: Rect, edges: Int, dx: Int, dy: Int, screenW: Int, screenH: Int, minW: Int, minH: Int): Rect {
        val start = fit(rect, screenW, screenH, minW, minH)
        var left = start.x
        var top = start.y
        var right = left + start.width
        var bottom = top + start.height
        if (edges and LEFT != 0) left = (left + dx).coerceIn(0, right - minW.coerceAtMost(start.width))
        if (edges and RIGHT != 0) right = (right + dx).coerceIn(left + minW.coerceAtMost(start.width), screenW)
        if (edges and TOP != 0) top = (top + dy).coerceIn(0, bottom - minH.coerceAtMost(start.height))
        if (edges and BOTTOM != 0) bottom = (bottom + dy).coerceIn(top + minH.coerceAtMost(start.height), screenH)
        return Rect(left, top, right - left, bottom - top)
    }

    fun minimum(angle: Int, logicalWidth: Int, logicalHeight: Int): Pair<Int, Int> =
        if (angle == 90 || angle == -90) logicalHeight to logicalWidth else logicalWidth to logicalHeight

    fun save(rect: Rect, screenW: Int, screenH: Int): String =
        listOf(rect.x.toFloat() / screenW, rect.y.toFloat() / screenH, rect.width.toFloat() / screenW, rect.height.toFloat() / screenH).joinToString(",")

    fun restore(value: String?, screenW: Int, screenH: Int): Rect? {
        val parts = value?.split(',')?.map { it.toFloatOrNull() ?: return null } ?: return null
        if (parts.size != 4 || parts.any { !it.isFinite() || it !in 0f..1f } || parts[2] == 0f || parts[3] == 0f) return null
        return Rect((parts[0] * screenW).toInt(), (parts[1] * screenH).toInt(), (parts[2] * screenW).toInt(), (parts[3] * screenH).toInt())
    }
}
