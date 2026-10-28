package com.olerast.suflyor

import com.olerast.suflyor.doc.ScanText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTextTest {
    @Test fun removesChineseWordSeparatorsWithoutJoiningEnglishOrParagraphs() {
        assertEquals("这是中文 OCR test\n第二段\n\n新段落", ScanText.clean(" 这 是 中 文 OCR test\r\n第 二 段\r\n\r\n新 段 落\u000c "))
    }

    @Test fun preservesTraditionalChinesePunctuationAndLiteralMarkup() {
        assertEquals("繁體中文，測試。\n# [原文] **文字**", ScanText.clean("繁 體 中 文，測 試。\n# [原文] **文字**"))
    }

    @Test fun emptyImageTextStaysEmpty() { assertEquals("", ScanText.clean("\n\t\u000c ")) }

    @Test fun smallPhotoIsNotUpscaled() { assertEquals(800 to 1200, ScanText.imageSize(800, 1200)) }

    @Test fun largePhotosAreBoundedWithoutChangingAspectRatio() {
        for ((width, height) in listOf(12000 to 9000, 9000 to 12000, 100000 to 80, 5000 to 5000, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val (w, h) = ScanText.imageSize(width, height)
            assertTrue(w in 1..ScanText.MAX_EDGE && h in 1..ScanText.MAX_EDGE)
            assertTrue(w.toLong() * h <= ScanText.MAX_PIXELS)
            // At most one output pixel of rounding, even for extremely thin panoramas.
            assertTrue(kotlin.math.abs(w * height.toDouble() - h * width.toDouble()) <= maxOf(width, height).toDouble())
        }
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidDimensions() { ScanText.imageSize(0, 100) }
}
