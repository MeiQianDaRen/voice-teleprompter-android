package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.overlay.KaraokeProgress
import com.olerast.suflyor.overlay.ReadingMotion
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import org.junit.Assert.*
import org.junit.Test

class KaraokeProgressTest {
    private fun model(text: String) = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))), false, lang = SpeechLang.ZH)

    @Test fun confirmedTextWipesContinuouslyWithinACharacter() {
        val p = KaraokeProgress()
        p.update(0f, 1, 0); p.update(1f, 1, 1000)
        assertEquals(0f, p.frame(1000), 0f)
        assertEquals(0.5f, p.frame(1065), 0.01f)
        assertEquals(1f, p.frame(1130), 0f)
        assertFalse(p.running(1130))
    }

    @Test fun newRecognitionContinuesFromTheDisplayedReadEdge() {
        val p = KaraokeProgress()
        p.update(0f, 1, 0); p.update(3f, 1, 1000)
        val visible = p.frame(1065)
        p.update(5f, 1, 1065)
        assertEquals(visible, p.frame(1065), 0f)
        assertEquals(5f, p.frame(1195), 0f)
    }

    @Test fun forecastCanCrossANewlineWithoutMarkingItConfirmed() {
        val m = model("今天我们\n一起学习")
        val spoken = m.tokens.indices.filter { m.tokens[it].spoken }.toIntArray()
        val motion = ReadingMotion()
        motion.follow(ReadingMotion.Sample(2f, 5f, 8f, 4f), 0)
        val read = KaraokeProgress().apply { update(2f, 1, 0) }
        assertTrue(KaraokeProgress.offset(m.tokens, spoken, motion.frame(1000)) > m.displayText.indexOf('\n'))
        assertEquals(2f, read.frame(1000), 0f)
    }

    @Test fun readOffsetsKeepSymbolsWholeAndSkipStageDirections() {
        val m = model("温度36.5℃[停顿]适合录制")
        val spoken = m.tokens.indices.filter { m.tokens[it].spoken }.toIntArray()
        val symbol = spoken.indexOfFirst { m.displayText.substring(m.tokens[it].start, m.tokens[it].end) == "36.5℃" }
        val token = m.tokens[spoken[symbol]]
        assertEquals(token.start + (token.end - token.start) / 2f, KaraokeProgress.offset(m.tokens, spoken, symbol + 0.5f), 0f)
        assertTrue(KaraokeProgress.offset(m.tokens, spoken, symbol + 1f) >= m.displayText.indexOf('适'))
        assertEquals(m.tokens[spoken.last()].end.toFloat(), KaraokeProgress.offset(m.tokens, spoken, 1000f), 0f)
    }

    @Test fun aRetakeRestoresUnreadColourSmoothly() {
        val p = KaraokeProgress()
        p.update(20f, 1, 0); p.update(5f, 1, 1000)
        assertEquals(20f, p.frame(1000), 0f)
        assertEquals(12.5f, p.frame(1090), 0.01f)
        assertEquals(5f, p.frame(1180), 0f)
    }

    @Test fun restartingAndEmptyScriptsHaveValidOffsets() {
        val p = KaraokeProgress()
        p.update(20f, 1, 0); p.update(0f, 2, 1000)
        assertEquals(0f, p.frame(1180), 0f)
        assertEquals(0f, KaraokeProgress.offset(emptyList(), intArrayOf(), 100f), 0f)
    }
}
