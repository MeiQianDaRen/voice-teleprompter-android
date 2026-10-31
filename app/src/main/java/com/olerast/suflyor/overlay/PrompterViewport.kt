package com.olerast.suflyor.overlay

/** Uses actual layout bounds, including line spacing, so the current reading line is never pushed out. */
internal object PrompterViewport {
    fun readOffset(currentLine: Int, requested: Int, height: Int,
        lineTop: (Int) -> Int, lineBottom: (Int) -> Int): Int {
        var low = 0
        var high = requested.coerceIn(0, currentLine.coerceAtLeast(0))
        val bottom = lineBottom(currentLine)
        while (low < high) {
            val count = low + (high - low + 1) / 2
            if (bottom - lineTop(currentLine - count) <= height) low = count else high = count - 1
        }
        return lineTop(currentLine) - lineTop(currentLine - low)
    }
}
