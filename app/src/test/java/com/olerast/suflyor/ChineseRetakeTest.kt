package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import com.olerast.suflyor.track.ChineseRetakePolicy
import org.junit.Test

class ChineseRetakeTest {
    private val first = "今天我们介绍手机拍摄的操作方法"
    private val second = "随后调整镜头构图并且检查现场光线"
    private fun model(text: String) = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))), false, lang = SpeechLang.ZH)
    private fun tracker(text: String) = model(text).let { ScriptTracker(it.tokens, it.lang) }
    private fun ScriptTracker.stable(text: String, final: Boolean = true): ScriptTracker.Update {
        val before = position
        val first = onTranscript(text, final, 1000)
        assertEquals("one callback must not jump back", before, first.position)
        assertNull(confirmRetake(1449))
        return confirmRetake(1450) ?: first
    }
    private fun readBoth() = tracker(first + second).also {
        it.onTranscript(first, true)
        it.onTranscript(second, true)
    }

    @Test fun aRetakeAfterAWrongPrefixInTheSameUtteranceCanGoBack() {
        val t = readBoth()
        val result = t.stable("这段完全识别错误而且不在原稿上今天我们介绍手机拍摄")
        assertEquals("今天我们介绍手机拍摄".length, result.position)
        assertTrue(result.reason.startsWith("back"))
    }

    @Test fun aRetakeWithOneWrongCharacterCanGoBack() {
        for (heard in listOf("今天我们介绍手机拍社的操作方法", "今天我们手机拍摄的操作方法", "今天我们介绍这个手机拍摄的操作方法")) {
            assertEquals(heard, first.length, readBoth().stable(heard).position)
        }
    }

    @Test fun aStableFinalCanReturnToAUniquePreviousPhrase() {
        val t = readBoth()
        assertEquals("今天我们介绍".length, t.stable("今天我们介绍").position)
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
        assertEquals(first.length, t.stable(first).position)
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
        assertEquals(first.length + middle.length + second.length, t.stable(first).position)
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
        val back = t.stable("这段完全识别错误而且不在原稿上现在温度三十六点五摄氏度")
        val next = m.tokens[back.nextToken]
        assertEquals("请", m.displayText.substring(next.start, next.end))
    }

    @Test fun defaultIsFiveAndAllConfiguredLengthsAreRequired() {
        for (length in 4..8) {
            val m = model(first + second)
            val t = ScriptTracker(m.tokens, m.lang, length)
            t.reset(t.size)
            assertEquals(t.size, t.onTranscript(first.take(length - 1), true, 0).position)
            assertNull(t.confirmRetake(1000))
            assertEquals(length, t.stable(first.take(length)).position)
        }
        val t = readBoth()
        val end = t.position
        t.onTranscript(first.take(4), true, 0)
        assertNull(t.confirmRetake(1000))
        assertEquals(end, t.position)
        assertEquals(5, t.stable(first.take(5)).position)
    }

    @Test fun oneSubstitutionInFivePassesButTwoDoNot() {
        assertEquals(5, readBoth().stable("今天我门介").position)
        val t = readBoth()
        t.onTranscript("今天窝门介", true, 0)
        assertNull(t.confirmRetake(1000))
        assertEquals(t.size, t.position)
    }

    @Test fun fourCharactersWithOneSubstitutionFailEightyPercent() {
        val m = model(first + second)
        val t = ScriptTracker(m.tokens, m.lang, 4)
        t.reset(t.size)
        t.onTranscript("今天我门", true, 0)
        assertNull(t.confirmRetake(1000))
        assertEquals(t.size, t.position)
    }

    @Test fun editDistanceEnforcesBothOneEditAndEightyPercent() {
        fun edits(heard: String, script: String) = ChineseRetakePolicy.editCount(
            heard.map(Char::toString), script.map(Char::toString), 0, script.length)
        assertEquals(1, edits("甲乙丙丁戊", "甲乙丙丁己"))
        assertEquals(1, edits("甲乙丙丁戊", "甲乙丙戊"))
        assertEquals(1, edits("甲乙丙丁戊", "甲乙己丙丁戊"))
        assertNull(edits("甲乙丙丁", "甲乙丙戊"))
        assertNull(edits("甲乙丙丁戊己庚辛", "甲乙丙丁戊己壬癸"))
    }

    @Test fun twoDistinctMatchingObservationsConfirmBeforeTheTimer() {
        val t = readBoth()
        val end = t.position
        assertEquals(end, t.onTranscript(first.take(5), false, 0).position)
        assertEquals(6, t.onTranscript(first.take(6), false, 100).position)
        assertNull(t.confirmRetake(1000))
    }

    @Test fun identicalCallbacksDoNotCountTwiceButCanBecomeStable() {
        val t = readBoth()
        val end = t.position
        assertEquals(end, t.onTranscript(first.take(5), false, 0).position)
        assertEquals(end, t.onTranscript(first.take(5), false, 100).position)
        assertNull(t.confirmRetake(449))
        assertEquals(5, t.confirmRetake(450)!!.position)
        assertNull(t.confirmRetake(900))
    }

    @Test fun finalAfterMatchingPartialCountsAsASecondObservation() {
        val t = readBoth()
        t.onTranscript(first.take(5), false, 0)
        assertEquals(5, t.onTranscript(first.take(5), true, 100).position)
    }

    @Test fun evenALongExactFinalRequiresConfirmation() {
        assertEquals(first.length, readBoth().stable(first).position)
    }

    @Test fun onlyTheConfiguredTailParticipatesInRetakeMatching() {
        val t = readBoth()
        assertEquals(5, t.stable("随便胡乱说错很长一段话" + first.take(5)).position)
        val noise = readBoth()
        noise.onTranscript(first + "随便胡乱说错很长一段话", true, 0)
        assertNull(noise.confirmRetake(1000))
        assertEquals(noise.size, noise.position)
    }

    @Test fun unrelatedOrAmbiguousUpdateCancelsThePendingCandidate() {
        val t = readBoth()
        t.onTranscript(first.take(5), false, 0)
        t.onTranscript("随便胡乱聊些事情", false, 100)
        assertNull(t.confirmRetake(1000))
        assertEquals(t.size, t.position)
    }

    @Test fun aDifferentCandidateNeedsItsOwnStablePeriod() {
        val t = readBoth()
        t.onTranscript(first.take(5), false, 0)
        t.onTranscript(first.substring(8, 13), false, 100)
        assertEquals(t.size, t.position)
        assertNull(t.confirmRetake(450))
        assertEquals(13, t.confirmRetake(550)!!.position)
    }

    @Test fun retakeWindowIncludesOneHundredButExcludesDistantText() {
        for (distance in listOf(100, 130)) {
            val m = model(first + "隔".repeat(distance - first.length))
            val t = ScriptTracker(m.tokens, m.lang)
            t.reset(t.size)
            t.onTranscript(first.take(5), true, 0)
            val result = t.confirmRetake(450)
            if (distance == 100) assertEquals(5, result!!.position)
            else { assertNull(result); assertEquals(t.size, t.position) }
        }
    }

    @Test fun resetCancellationAndSettingChangesDiscardPendingConfirmation() {
        for (cancel in listOf<(ScriptTracker) -> Unit>(
            { it.reset(it.size) }, { it.cancelRetake() }, { it.setRetakeChars(8) },
            { it.jumpToToken(it.size) })) {
            val t = readBoth()
            t.onTranscript(first.take(5), false, 0)
            cancel(t)
            assertNull(t.confirmRetake(1000))
            assertEquals(t.size, t.position)
        }
    }

    @Test fun changedSettingAppliesLiveAndClampsToSupportedRange() {
        val t = readBoth()
        t.setRetakeChars(100)
        t.onTranscript(first.take(7), true, 0)
        assertNull(t.confirmRetake(1000))
        assertEquals(8, t.stable(first.take(8)).position)
        t.reset(t.size)
        t.setRetakeChars(-1)
        assertEquals(4, t.stable(first.take(4)).position)
    }
}
