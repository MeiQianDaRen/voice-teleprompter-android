package com.olerast.suflyor.doc

/** Bounds decoding before allocating a camera bitmap, including very wide panoramas. */
internal object ScanText {
    const val MAX_EDGE = 3200
    const val MAX_PIXELS = 8_000_000

    fun imageSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height),
            kotlin.math.sqrt(MAX_PIXELS.toDouble() / (width.toDouble() * height)))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    // Tesseract can insert word separators between Chinese characters. Keep English spacing and paragraphs.
    fun clean(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n').replace("\u000c", "")
        .replace(Regex("(?<=\\p{IsHan})[ \\t]+(?=\\p{IsHan})"), "").trim()
}
