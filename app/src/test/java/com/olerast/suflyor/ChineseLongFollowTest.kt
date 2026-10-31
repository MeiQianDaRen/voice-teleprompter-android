package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.track.ChineseAlignment
import com.olerast.suflyor.track.ChineseLocalAlignment
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.*
import org.junit.Test

class ChineseLongFollowTest {
    private val text = "今天我们先介绍手机拍摄视频的操作方法随后调整镜头构图并且检查现场光线接着打开录制页面选择合适的分辨率和画面比例如果周围环境比较嘈杂可以先找到安静的位置再开始讲话最后请记得检查录音效果保存文件并分享自己的作品"
    private fun model(value: String = text) = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(value))), false, lang = SpeechLang.ZH)
    private fun tracker(value: String = text) = model(value).let { ScriptTracker(it.tokens, it.lang) }

    @Test fun longUnpunctuatedPartialAdvancesEveryCharacter() {
        val t = tracker()
        for (end in 1..text.length) assertEquals("at $end", end, t.onTranscript(text.take(end), false, end * 100L).position)
        assertEquals(text.length, t.onTranscript(text, true, 20000).position)
    }

    @Test fun aLargeFirstPacketIsProcessedInShortSuccessiveWindows() {
        assertEquals(text.length, tracker().onTranscript(text, true, 0).position)
    }

    @Test fun severalWrongCharactersInTheMiddleDoNotPoisonTheRestOfTheSentence() {
        val t = tracker()
        val heard = text.replace("现场光线", "完全错词")
        var position = 0
        for (end in 1..heard.length) {
            val result = t.onTranscript(heard.take(end), false, end * 100L)
            assertTrue(result.position >= position)
            position = result.position
            if (end > 45) assertTrue("lag at $end: $position", end - position <= 4)
        }
        assertEquals(text.length, position)
    }

    @Test fun fillerInsertedInALongPartialDoesNotCauseBacktracking() {
        val t = tracker()
        val heard = text.replace("接着", "那个嗯啊接着")
        var position = 0
        for (end in 1..heard.length) {
            val result = t.onTranscript(heard.take(end), false, end * 100L)
            assertTrue(result.position >= position)
            position = result.position
        }
        assertEquals(text.length, position)
    }

    @Test fun revisingAnOldPrefixKeepsTheLivePointerAndContinuesFromTheTail() {
        val t = tracker()
        t.onTranscript(text.take(65), false, 0)
        assertEquals(65, t.onTranscript("错误开头" + text.substring(4, 65), false, 100).position)
        assertEquals(66, t.onTranscript("错误开头" + text.substring(4, 66), false, 200).position)
    }

    @Test fun oneHomophoneCanHelpButCannotBeTheOnlyEvidence() {
        val script = "我们拍摄镜头".map(Char::toString)
        val provider: (String) -> String? = { if (it in setOf("摄", "社")) "she" else null }
        val local = ChineseLocalAlignment(script, List(script.size) { 1f }, emptySet(), provider)
        assertEquals(4, local.find("我们拍社".map(Char::toString), 0)!!.end)
        assertNull(local.find("窝门拍社".map(Char::toString), 0))
        val exactOnly = ChineseLocalAlignment(script, List(script.size) { 1f }, emptySet()) { null }
        assertNull(exactOnly.find("我们拍社".map(Char::toString), 0))
    }

    @Test fun functionWordOnlyPhrasesCannotAnchorThePointer() {
        val script = "的是在和了着过也就都".map(Char::toString)
        val local = ChineseLocalAlignment(script, List(script.size) { 0.5f }, emptySet()) { null }
        assertNull(local.find(script.takeLast(8), 0))
        assertTrue(local.phrases.all { it.lowInformation })
    }

    @Test fun anchorsKeepCharacterOffsetsAndPhoneticsAreCached() {
        val chars = "今天我们介绍如何使用手机拍摄视频随后请看镜头".map(Char::toString)
        var calls = 0
        val local = ChineseLocalAlignment(chars, List(chars.size) { 1f }, setOf(16)) { calls++; it }
        assertEquals(0, local.phrases.first().start)
        assertEquals(chars.size, local.phrases.last().end)
        for (i in 1 until local.phrases.size) assertEquals(local.phrases[i - 1].end, local.phrases[i].start)
        assertTrue(local.phrases.all { it.end - it.start <= 8 })
        local.phrases.forEach { assertEquals(it.end - it.start, it.pinyin.size) }
        val count = calls
        local.phrases.forEach { it.pinyin }
        assertEquals(count, calls)
    }

    @Test fun normalLocalSearchCannotJumpToADistantSentence() {
        val chars = ("隔".repeat(40) + "请看镜头自然讲话").map(Char::toString)
        val local = ChineseLocalAlignment(chars, List(chars.size) { 1f }, emptySet()) { null }
        assertNull(local.find("请看镜头自然讲话".map(Char::toString), 0))
    }

    @Test fun silenceCancelsAnUnconfirmedRetakeAndHoldsThePosition() {
        val t = tracker(text.take(60))
        t.reset(t.size)
        t.onTranscript(text.take(5), false, 0)
        assertEquals(t.size, t.onTranscript(text.take(5), true, 1600, speechActive = false).position)
        assertNull(t.confirmRetake(2000))
        t.onTranscript(text.take(5), false, 2100)
        assertEquals(5, t.confirmRetake(2550)!!.position)
    }

    @Test fun threeLowConfidenceUpdatesHoldAndThenReleaseAConfidentTarget() {
        val alignment = ChineseAlignment(model().tokens)
        alignment.consume("完全无关杂音", false, 0)
        alignment.consume("额外错误内容", false, 100)
        alignment.consume("别的话题干扰", false, 200)
        assertEquals(ChineseAlignment.Phase.LOW_CONFIDENCE, alignment.phase)
        assertEquals(0, alignment.consume(text.take(6), false, 300).position)
        assertNull(alignment.confirmFollow(599))
        assertEquals(6, alignment.confirmFollow(600)!!.position)
        assertEquals(ChineseAlignment.Phase.FOLLOWING, alignment.phase)
    }

    @Test fun aLostFollowerCanReanchorAfterTwoSecondsWithinABoundedWindow() {
        val value = "隔".repeat(40) + "请看镜头保持自然语速"
        val t = tracker(value)
        t.onTranscript("完全无关杂音", false, 0)
        t.onTranscript("额外错误内容", false, 100)
        t.onTranscript("别的话题干扰", false, 200)
        assertEquals(value.length, t.onTranscript("请看镜头保持自然语速", false, 2500).position)
    }

    @Test fun aDeferredFinalCommitsItsAnchorAndCanContinueTheNextUtterance() {
        val t = tracker()
        t.onTranscript("完全无关杂音", false, 0)
        t.onTranscript("额外错误内容", false, 100)
        t.onTranscript("别的话题干扰", false, 200)
        assertEquals(0, t.onTranscript(text.take(6), true, 300).position)
        assertEquals(6, t.confirmFollow(600)!!.position)
        assertEquals(10, t.onTranscript(text.substring(6, 10), false, 700).position)
    }

    @Test fun aNewUnrelatedUpdateOrManualSeekCancelsADeferredFollow() {
        for (manual in listOf(false, true)) {
            val t = tracker()
            t.onTranscript("完全无关杂音", false, 0)
            t.onTranscript("额外错误内容", false, 100)
            t.onTranscript("别的话题干扰", false, 200)
            t.onTranscript(text.take(6), false, 300)
            if (manual) t.reset() else t.onTranscript("又是无关杂音", false, 400)
            assertNull(t.confirmFollow(900))
            assertEquals(0, t.position)
        }
    }
}
