package com.olerast.suflyor

import com.olerast.suflyor.doc.*
import com.olerast.suflyor.script.*
import com.olerast.suflyor.track.*
import org.junit.Assert.*
import org.junit.Test

class ChineseObservationTest {
    private val text = "今天我们介绍手机拍摄的操作方法随后调整镜头检查现场光线"
    private fun tracker() = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))), false, lang = SpeechLang.ZH)
        .let { ScriptTracker(it.tokens, it.lang) }

    @Test fun veryLowAcousticConfidenceCannotAdvanceEvenOnFinal() {
        val t = tracker()
        assertEquals(0, t.onTranscript(text.take(6), true, 100, confidence = 0.01f).position)
        assertNull(t.confirmFollow(1000))
        assertEquals(6, t.onTranscript(text.take(6), false, 1100, confidence = 0.9f).position)
    }

    @Test fun uncertainSpeechNeedsASecondConsistentTextAnchor() {
        val t = tracker()
        assertEquals(0, t.onTranscript(text.take(4), false, 100, confidence = 0.5f).position)
        assertNull(t.confirmFollow(1000))
        assertEquals(5, t.onTranscript(text.take(5), false, 1100, confidence = 0.5f).position)
    }

    @Test fun missingScoresKeepTextAlignmentAndCommandsHaveIndependentConfirmation() {
        val t = tracker()
        assertEquals(6, t.onTranscript(text.take(6), false, 100).position)
        assertEquals(0, t.onTranscript("欧了重新开始", true, 200, confidence = 0.01f).position)
    }

    @Test fun repeatedPhrasesCannotPullThePointerToTheDistantCopy() {
        val script = "今天介绍拍摄方法中间内容今天介绍拍摄方法".map(Char::toString)
        val alignment = ChineseLocalAlignment(script, List(script.size) { 1f }, setOf(0, 14)) { null }
        val hit = alignment.find("今天介绍拍摄方法".map(Char::toString), 3)
        assertNotNull(hit)
        assertTrue(hit!!.end < 12)
    }

    @Test fun metadataOnlyUpdatesKeepThePreviouslyConfirmedLongAnchor() {
        val t = tracker()
        val first = t.onTranscript(text.take(20), false, 100, confidence = 0.9f)
        val same = t.onTranscript(text.take(20), false, 200, confidence = 0.8f)
        assertEquals(first.position, same.position)
        assertTrue(first.alignmentQuality >= 0.8f)
        assertTrue(same.alignmentQuality >= 0.8f)
    }
}
