package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.speech.hotwordList
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.*
import org.junit.Test

class ChineseVoiceCommandTest {
    private val text = "今天我们介绍手机拍摄的操作方法随后调整镜头构图并且检查现场光线"
    private fun tracker() = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))),
        false, lang = SpeechLang.ZH).let { ScriptTracker(it.tokens, it.lang) }

    @Test fun eitherExactCommandImmediatelyRestarts() {
        for (command in listOf("欧了重新开始", "欧了重新来", "欧了，重新开始！", "欧 了 重 新 来")) {
            val t = tracker()
            t.reset(t.size)
            val result = t.onTranscript(command, false, 0)
            assertEquals(command, 0, result.position)
            assertTrue(result.reason.startsWith("restart"))
            assertNull(t.confirmRetake(1000))
        }
    }

    @Test fun partialAndFinalDoNotRestartTheSameCommandTwice() {
        val t = tracker()
        t.reset(t.size)
        t.onTranscript("欧了重新开始", false, 0)
        for (end in 1..12) {
            assertEquals(end, t.onTranscript("欧了重新开始" + text.take(end), false, end * 100L).position)
        }
        assertEquals(12, t.onTranscript("欧了重新开始" + text.take(12), true, 1400).position)
        assertEquals(16, t.onTranscript(text.substring(12, 16), false, 1500).position)
    }

    @Test fun aSecondCommandInTheSameUtteranceCanRestartAgain() {
        val t = tracker()
        val prefix = "欧了重新开始" + text.take(8)
        assertEquals(8, t.onTranscript(prefix, false, 0).position)
        assertEquals(0, t.onTranscript(prefix + "欧了重新来", false, 100).position)
        assertEquals(4, t.onTranscript(prefix + "欧了重新来" + text.take(4), true, 200).position)
    }

    @Test fun commandCanRestartAfterAnotherUtteranceAndCancelsRetake() {
        val t = tracker()
        t.reset(t.size)
        t.onTranscript(text.take(5), false, 0)
        assertEquals(0, t.onTranscript("欧了重新来", true, 100).position)
        assertNull(t.confirmRetake(1000))
        t.onTranscript(text.take(12), true, 1100)
        assertEquals(0, t.onTranscript("欧了重新来", true, 1200).position)
    }

    @Test fun commandRequiresTheExactWakePhrase() {
        for (phrase in listOf("重新开始", "我们重新来", "欧了重新开", "欧乐重新开始", "欧了重新莱")) {
            val t = tracker()
            t.reset(t.size)
            assertEquals(phrase, t.size, t.onTranscript(phrase, true, 0).position)
            assertNull(t.confirmRetake(1000))
        }
    }

    @Test fun commandHotwordsSurviveTheVocabularyLimit() {
        val words = hotwordList(SpeechLang.ZH, List(1600) { "中文" } + listOf("欧了重新来")).split('/')
        assertEquals(listOf("欧了重新开始", "欧了重新来", "中文"), words)
        assertFalse(hotwordList(SpeechLang.EN, listOf("English")).contains("欧了"))
    }

    @Test fun replacingAWordTemporarilyStillFollowsCorrectLaterWords() {
        for (replacement in listOf("相机", "设备", "智能相机")) {
            val t = tracker()
            val heard = text.replace("手机", replacement)
            var before = 0
            for (end in 1..heard.length) {
                val result = t.onTranscript(heard.take(end), false, end * 100L)
                assertTrue("$replacement at $end", result.position >= before)
                before = result.position
                if (end >= 20) assertTrue("correct suffix must keep up", result.position >= end - 3)
            }
            assertEquals(text.length, t.onTranscript(heard, true, 10000).position)
        }
    }
}
