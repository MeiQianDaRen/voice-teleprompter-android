package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseRetakeTest {
    private val first = "今天我们介绍手机拍摄的操作方法"
    private val second = "随后调整镜头构图并且检查现场光线"
    private fun model(text: String) = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))), false, lang = SpeechLang.ZH)
    private fun tracker(text: String) = model(text).let { ScriptTracker(it.tokens, it.lang) }
    private fun readBoth() = tracker(first + second).also {
        it.onTranscript(first, true)
        it.onTranscript(second, true)
    }

    @Test fun aRetakeAfterAWrongPrefixInTheSameUtteranceCanGoBack() {
        val t = readBoth()
        val result = t.onTranscript("这段完全识别错误而且不在原稿上今天我们介绍手机拍摄", true)
        assertEquals("今天我们介绍手机拍摄".length, result.position)
        assertTrue(result.reason.startsWith("back"))
    }

    @Test fun aRetakeWithOneWrongCharacterCanGoBack() {
        for (heard in listOf("今天我们介绍手机拍社的操作方法", "今天我们手机拍摄的操作方法", "今天我们介绍这个手机拍摄的操作方法")) {
            assertEquals(heard, first.length, readBoth().onTranscript(heard, true).position)
        }
    }

    @Test fun aSixCharacterFinalCanReturnToAUniquePreviousPhrase() {
        val t = readBoth()
        assertEquals("今天我们介绍".length, t.onTranscript("今天我们介绍", true).position)
    }

    @Test fun shortPartialsNeedGrowingConfirmationAndIdenticalCallbacksDoNotCount() {
        val t = readBoth()
        val end = t.position
        assertEquals(end, t.onTranscript("今天我们介绍", false).position)
        assertEquals(end, t.onTranscript("今天我们介绍", false).position)
        assertEquals("今天我们介绍手".length, t.onTranscript("今天我们介绍手", false).position)
    }

    @Test fun acceptedPartialRetakeKeepsFollowingTheGrowingSameUtterance() {
        val t = readBoth()
        val wrong = "这段完全识别错误而且不在原稿上"
        val retake = "今天我们介绍手机拍摄"
        t.onTranscript(wrong + retake.take(6), false)
        val back = t.onTranscript(wrong + retake.take(7), false)
        assertEquals(7, back.position)
        for (end in 8..retake.length) assertEquals(end, t.onTranscript(wrong + retake.take(end), false).position)
        assertEquals(first.length, t.onTranscript(wrong + first, true).position)
        assertEquals(first.length + second.length, t.onTranscript(second, true).position)
    }

    @Test fun shorteningAPartialCannotPretendToBeARetake() {
        val t = tracker(first + second)
        t.onTranscript(first + second, false)
        assertEquals(first.length + second.length, t.onTranscript(first.take(10), false).position)
        assertEquals(first.length + second.length, t.onTranscript(first.take(11), false).position)
    }

    @Test fun aStableFinalRetakeCanReturnInsideTheCurrentUncommittedUtterance() {
        val t = tracker(first + second)
        t.onTranscript(first + second, false)
        assertEquals(first.length, t.onTranscript(first, true).position)
    }

    @Test fun aSuffixAlreadyAtTheCurrentPositionDoesNotChooseItsEarlierDuplicate() {
        val repeated = "请看镜头自然说话"
        val text = repeated + "中间换个不同话题" + repeated
        val t = tracker(text)
        t.reset(text.length)
        assertEquals(text.length, t.onTranscript("这段完全识别错误而且不在原稿上" + repeated, true).position)
    }

    @Test fun aRepeatedUpcomingPhraseStillWinsOverThePreviousOne() {
        for ((heard, count) in listOf(first.take(10) to 10, first to first.length,
            "今天我们介绍这个手机拍摄的操作方法" to first.length)) {
            val t = tracker(first + second + first)
            t.onTranscript(first, true)
            t.onTranscript(second, true)
            assertEquals(heard, first.length + second.length + count,
                t.onTranscript("这段完全识别错误而且不在原稿上" + heard, true).position)
        }
    }

    @Test fun anAmbiguousShortRepeatedPhraseDoesNotChooseThePreviousParagraph() {
        val repeated = "请看镜头自然说话"
        val t = tracker(repeated + "中间换个不同话题" + repeated + second)
        t.onTranscript(repeated + "中间换个不同话题" + repeated, true)
        t.onTranscript(second, true)
        val end = t.position
        assertEquals(end, t.onTranscript("这段完全识别错误而且不在原稿上" + repeated, true).position)
    }

    @Test fun aShortFragmentOrUnrelatedTailCannotJumpBack() {
        val t = readBoth()
        val end = t.position
        for (heard in listOf("今天我们", "我", "嗯呃", "今天我们介绍随后说些其他事情")) {
            assertEquals(heard, end, t.onTranscript(heard, true).position)
        }
    }

    @Test fun backwardSearchIsBoundedToTheAlreadyReadNearbyText() {
        val middle = "中间还有其他未完的话题需要继续讲解".repeat(20)
        val t = tracker(first + middle + second)
        t.jumpToToken(first.length + middle.length)
        t.onTranscript(second, true)
        assertEquals(first.length + middle.length + second.length, t.onTranscript(first, true).position)
    }

    @Test fun resetClearsPendingRetakeConfirmation() {
        val t = readBoth()
        t.onTranscript(first.take(6), false)
        t.reset(first.length + second.length)
        assertEquals(first.length + second.length, t.onTranscript(first.take(7), false).position)
    }

    @Test fun symbolReadingsMapBackToOriginalTokensDuringRetake() {
        val start = "现在温度36.5℃请保持通风"
        val m = model(start + second)
        val t = ScriptTracker(m.tokens, m.lang)
        t.onTranscript("现在温度三十六点五摄氏度请保持通风", true)
        t.onTranscript(second, true)
        val back = t.onTranscript("这段完全识别错误而且不在原稿上现在温度三十六点五摄氏度", true)
        val next = m.tokens[back.nextToken]
        assertEquals("请", m.displayText.substring(next.start, next.end))
    }
}
