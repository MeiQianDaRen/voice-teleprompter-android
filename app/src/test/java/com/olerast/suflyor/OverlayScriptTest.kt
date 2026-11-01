package com.olerast.suflyor

import com.olerast.suflyor.doc.*
import com.olerast.suflyor.script.*
import com.olerast.suflyor.overlay.OverlayScript
import com.olerast.suflyor.overlay.KaraokeProgress
import org.junit.Assert.*
import org.junit.Test

class OverlayScriptTest {
    private fun model(vararg paragraphs: String, phraseMode: Boolean = false) =
        ScriptLayout.build(ScriptDocument("test", "TEXT", paragraphs.map { Paragraph(it) }), phraseMode, lang = SpeechLang.ZH)

    @Test fun threeParagraphsUseThreeRowsInsteadOfHidingTheNextContentRow() {
        val original = model("第一行", "第二行", "第三行", "第四行")
        val compact = OverlayScript.compact(original)
        assertEquals(listOf("第一行", "第二行", "第三行", "第四行"), compact.displayText.lines())
        assertEquals(listOf("第二行", "第三行", "第四行"), compact.displayText.lines().drop(1).take(3))
        assertTrue(original.displayText.contains("\n\n"))
        assertEquals(original.spokenTokens, compact.spokenTokens)
    }

    @Test fun whitespaceOnlyLinesAndLeadingTrailingBreaksDoNotOccupyRows() {
        val text = "\n \t\n第一行\n\u3000\u00A0\n\u200B\uFEFF\n第二行\n\n"
        assertEquals("第一行\n第二行", OverlayScript.compact(model(text)).displayText)
    }

    @Test fun lineEndingVariantsCollapseToASingleNormalLineBreak() {
        val text = "第一行\r\n\r\n第二行\r\r第三行\u2028\u2029第四行\u0085\u0085第五行"
        assertEquals("第一行\n第二行\n第三行\n第四行\n第五行", OverlayScript.compact(model(text)).displayText)
    }

    @Test fun tokenIndicesReadOffsetsAndWrittenSymbolsStayAlignedWithTheOriginalTracker() {
        val original = model("第一段36.5℃", "第二段😀中文", "第三段100%")
        val compact = OverlayScript.compact(original)
        assertEquals(original.tokens.size, compact.tokens.size)
        for (i in original.tokens.indices) {
            val a = original.tokens[i]
            val b = compact.tokens[i]
            assertEquals(a.copy(start = b.start, end = b.end), b)
            assertEquals(original.displayText.substring(a.start, a.end), compact.displayText.substring(b.start, b.end))
        }
        val spoken = compact.tokens.indices.filter { compact.tokens[it].spoken }.toIntArray()
        for (i in spoken.indices) {
            assertEquals(compact.tokens[spoken[i]].start.toFloat(), KaraokeProgress.offset(compact.tokens, spoken, i.toFloat()), 0f)
        }
    }

    @Test fun emphasisAndStageDirectionsRemainOnTheSameVisibleText() {
        val doc = ScriptDocument("test", "TEXT", listOf(
            Paragraph("标题", Paragraph.Kind.HEADING),
            Paragraph("正文\n\n[停顿]\n\n继续", emphasis = listOf(0..1, 10..11)),
        ))
        val original = ScriptLayout.build(doc, false, lang = SpeechLang.ZH)
        val compact = OverlayScript.compact(original)
        assertEquals(original.emphasis.map { original.displayText.substring(it) }, compact.emphasis.map { compact.displayText.substring(it) })
        assertEquals(original.notes.map { original.displayText.substring(it) }, compact.notes.map { compact.displayText.substring(it) })
        assertEquals(original.tokens.map { it.spoken }, compact.tokens.map { it.spoken })
    }

    @Test fun normalNewlinesInlineSpacesAndPhraseBreaksArePreserved() {
        val original = model("第一行  保留空格\n第二行")
        assertSame(original, OverlayScript.compact(original))
        val phrases = model("今天介绍手机拍摄方法，接下来调整镜头，检查现场光线。", phraseMode = true)
        val compact = OverlayScript.compact(phrases)
        assertEquals(phrases.displayText, compact.displayText)
        assertTrue(compact.displayText.contains('\n'))
    }

    @Test fun emptyOrBlankOnlyScriptsAreSafeAndCompactionIsIdempotent() {
        assertSame(ScriptModel.EMPTY, OverlayScript.compact(ScriptModel.EMPTY))
        val blank = ScriptModel("test", "\n\t\n\u3000", emptyList(), emptyList(), emptyList(), SpeechLang.ZH)
        assertEquals("", OverlayScript.compact(blank).displayText)
        val once = OverlayScript.compact(model("第一行", "第二行"))
        assertSame(once, OverlayScript.compact(once))
    }
}
