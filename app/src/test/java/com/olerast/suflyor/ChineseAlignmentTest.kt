package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseAlignmentTest {
    private fun model(text: String) = ScriptLayout.build(ScriptDocument("中文", "test", listOf(Paragraph(text))), false, lang = SpeechLang.ZH)
    private fun tracker(text: String) = model(text).let { ScriptTracker(it.tokens, it.lang) }

    @Test fun partialsLocateEveryCharacterWithoutReadingAhead() {
        val text = "今天我们介绍如何使用手机拍摄视频"
        val tracker = tracker(text)
        for (end in 1..text.length) {
            val result = tracker.onTranscript(text.take(end), false)
            assertEquals(text.take(end), end, result.position)
        }
        assertEquals(text.length, tracker.onTranscript(text, true).position)
    }

    @Test fun finalsCommitTheAnchorAndPartialsReplaceEachOther() {
        val tracker = tracker("今天我们介绍如何使用手机拍摄视频")
        assertEquals(6, tracker.onTranscript("今天我们介绍", true).position)
        assertEquals(8, tracker.onTranscript("如何", false).position)
        assertEquals(8, tracker.onTranscript("如和", false).position)
        assertEquals(12, tracker.onTranscript("如何使用手机", false).position)
        assertEquals(16, tracker.onTranscript("如何使用手机拍摄视频", true).position)
    }

    @Test fun omittingOrAddingCharactersKeepsTheOriginalScriptPosition() {
        val text = "今天我们介绍如何使用手机拍摄视频"
        assertEquals(text.length, tracker(text).onTranscript("今天介绍如何使用手机拍摄视频", true).position)
        assertEquals(text.length, tracker(text).onTranscript("今天我们介绍这个如何使用手机拍摄视频", true).position)
        assertEquals(text.length, tracker(text).onTranscript("今天我们介绍如和使用手机拍摄视频", true).position)
    }

    @Test fun commonCharactersAndUnrelatedSpeechCannotSkipAhead() {
        val tracker = tracker("今天我们介绍如何使用手机拍摄视频")
        tracker.onTranscript("今天我们介绍", true)
        for (text in listOf("", "嗯呃", "我", "的", "在", "天气不错但是晚饭还没准备好")) {
            assertEquals(text, 6, tracker.onTranscript(text, false).position)
        }
    }

    @Test fun repeatedUpcomingPhraseWinsOverRetake() {
        val text = "今天我们准备开始录制"
        val tracker = tracker(text + text)
        assertEquals(text.length, tracker.onTranscript(text, true).position)
        assertEquals(text.length * 2, tracker.onTranscript(text, true).position)
    }

    @Test fun shortRevisionsNeverMoveBackwardButAnExactLongRetakeCan() {
        val first = "第一段我们讲解镜头构图和光线"
        val second = "第二段现在介绍手机录制的操作"
        val tracker = tracker(first + second)
        tracker.onTranscript(first, true)
        tracker.onTranscript(second, true)
        assertEquals(first.length + second.length, tracker.onTranscript("第一段", false).position)
        assertEquals(first.length + second.length, tracker.onTranscript(first, true, 1000).position)
        assertEquals(first.length, tracker.confirmRetake(1450)!!.position)
    }

    @Test fun punctuationNotesAndSupplementaryHanMapToDisplayOffsets() {
        val m = model("𠀀中，文[停顿]提词 Android 2026 测试。")
        val tracker = ScriptTracker(m.tokens, m.lang)
        val result = tracker.onTranscript("𠀀 中 文 提 词", false)
        val next = m.tokens[result.nextToken]
        assertEquals("测", m.displayText.substring(next.start, next.end))
        assertEquals(m.spokenTokens, tracker.onTranscript("𠀀中文提词测试", true).position)
    }

    @Test fun manualSeekAndRestartResetBothAnchorAndCursor() {
        val m = model("今天我们介绍如何使用手机拍摄视频")
        val tracker = ScriptTracker(m.tokens, m.lang)
        tracker.onTranscript(m.displayText, true)
        tracker.jumpToToken(6)
        assertEquals(10, tracker.onTranscript("如何使用", true).position)
        tracker.reset()
        assertEquals(4, tracker.onTranscript("今天我们", false).position)
    }

    @Test fun newUtteranceCanSkipAnUnreadNearbySentence() {
        val first = "今天我们介绍如何使用手机拍摄视频"
        val last = "最后请记得保存文件并分享作品"
        val tracker = tracker(first + "中间这段暂时不读" + last)
        val result = tracker.onTranscript(last, true)
        assertEquals(first.length + "中间这段暂时不读".length + last.length, result.position)
        assertTrue(result.matches >= 8)
    }
}
