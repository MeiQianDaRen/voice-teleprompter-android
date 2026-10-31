package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.overlay.HighlightLayout
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import org.junit.Assert.*
import org.junit.Test

class HighlightLayoutTest {
    private fun model(text: String) = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))), false, lang = SpeechLang.ZH)

    @Test fun chineseFocusUsesStableGroupsRatherThanChangingEveryCharacter() {
        val m = model("今天我们一起学习自然表达")
        val ranges = HighlightLayout.words(m.displayText, m.tokens, true)
        val counts = ranges.groupingBy { it }.eachCount()
        assertTrue(counts.values.all { it in 2..4 })
        assertTrue(ranges.asList().zipWithNext().any { it.first == it.second })
    }

    @Test fun wordGroupsKeepMeasurementsWholeAndNeverIncludeNotesOrPunctuation() {
        val m = model("今天气温36.5℃，适合散步[停顿]继续录制")
        val ranges = HighlightLayout.words(m.displayText, m.tokens, true)
        m.tokens.forEachIndexed { i, t ->
            if (!t.spoken) assertNull(ranges[i])
            else assertNotNull(ranges[i])
        }
        val selected = ranges.filterNotNull().map { m.displayText.substring(it) }
        assertTrue(selected.contains("36.5℃"))
        assertFalse(selected.any { "停顿" in it || "，" in it })
    }

    @Test fun englishFocusKeepsEachWordWhole() {
        val m = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph("Read this naturally."))), false, lang = SpeechLang.EN)
        val ranges = HighlightLayout.words(m.displayText, m.tokens, false)
        assertEquals(listOf("Read", "this", "naturally"), ranges.map { m.displayText.substring(it!!) })
    }

    @Test fun phraseBandsRespectPunctuationAndOriginalOffsets() {
        val m = model("今天介绍设备，随后检查镜头")
        val ranges = HighlightLayout.phrases(m.tokens)
        assertEquals("今天介绍设备", ranges[0]!!.let { m.displayText.substring(it) })
        assertEquals("随后检查镜头", ranges.last()!!.let { m.displayText.substring(it) })
    }

    @Test fun notesAreNotHighlightedOrIncludedInsideAPhraseBand() {
        val m = model("先看镜头[停顿]再继续讲话")
        val ranges = HighlightLayout.phrases(m.tokens)
        m.tokens.forEachIndexed { index, token -> if (!token.spoken) assertNull(ranges[index]) }
        assertTrue(ranges.filterNotNull().none { m.displayText.substring(it).contains("停顿") })
    }

    @Test fun aLongUnpunctuatedSentenceUsesShortReadableBands() {
        val m = model("今天我们介绍如何使用手机拍摄视频随后检查光线")
        val ranges = HighlightLayout.phrases(m.tokens)
        assertEquals(m.tokens.size, ranges.filterNotNull().size)
        assertTrue(ranges.groupingBy { it }.eachCount().values.all { it <= 8 })
    }

    @Test fun forecastCannotLeaveTheVisibleRows() {
        assertEquals(8, HighlightLayout.visibleToken(5, 11, 20, 90f, { (it / 3 + 1) * 30 }, { true }))
        assertEquals(2, HighlightLayout.visibleToken(2, 8, 20, 30f, { (it / 3 + 1) * 30 }, { true }))
    }

    @Test fun clippingSkipsNonSpokenTokensAndNeverGoesBehindActual() {
        assertEquals(7, HighlightLayout.visibleToken(5, 11, 20, 90f, { (it / 3 + 1) * 30 }, { it != 8 }))
        assertEquals(5, HighlightLayout.visibleToken(5, 2, 20, 90f, { 30 }, { true }))
    }

    @Test fun endOfScriptDoesNotReadAnInvalidToken() {
        assertEquals(20, HighlightLayout.visibleToken(20, 25, 20, 90f, { error("past end") }, { true }))
        assertEquals(0, HighlightLayout.visibleToken(0, 0, 0, 90f, { error("empty") }, { true }))
    }
}
