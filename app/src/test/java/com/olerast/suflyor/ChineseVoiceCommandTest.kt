package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.script.ChineseVoiceCommands
import com.olerast.suflyor.speech.hotwordList
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.*
import org.junit.Test

class ChineseVoiceCommandTest {
    private val text = "今天我们介绍手机拍摄的操作方法随后调整镜头构图并且检查现场光线"
    private fun tracker() = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))),
        false, lang = SpeechLang.ZH).let { ScriptTracker(it.tokens, it.lang) }

    @Test fun allSixCommandsImmediatelyRestartIncludingSpacingAndPunctuation() {
        for (command in ChineseVoiceCommands.restartPhrases.flatMap { listOf(it, it.toList().joinToString(" "), it.replace("欧了", "欧了，") + "！") }) {
            val t = tracker()
            t.reset(t.size)
            val result = t.onTranscript(command, false, 0)
            assertEquals(command, 0, result.position)
            assertTrue(result.reason.startsWith("restart"))
            assertNull(t.confirmRetake(1000))
        }
    }

    @Test fun partialAndFinalDoNotRestartTheSameCommandTwice() {
        for (command in ChineseVoiceCommands.restartPhrases) {
            val t = tracker()
            t.reset(t.size)
            t.onTranscript(command, false, 0)
            for (end in 1..12) {
                assertEquals(command, end, t.onTranscript(command + text.take(end), false, end * 100L).position)
            }
            assertEquals(12, t.onTranscript(command + text.take(12), true, 1400).position)
            assertEquals(16, t.onTranscript(text.substring(12, 16), false, 1500).position)
        }
    }

    @Test fun aSecondCommandInTheSameUtteranceCanRestartAgain() {
        val t = tracker()
        val prefix = "欧了重新开始" + text.take(8)
        assertEquals(8, t.onTranscript(prefix, false, 0).position)
        assertEquals(0, t.onTranscript(prefix + "欧了重新来", false, 100).position)
        assertEquals(4, t.onTranscript(prefix + "欧了重新来" + text.take(4), true, 200).position)
    }

    @Test fun revisingTheSpellingOfACommandDoesNotRestartFollowingTextAgain() {
        val t = tracker()
        t.reset(t.size)
        assertEquals(5, t.onTranscript("欧了重新开始" + text.take(5), false, 0).position)
        assertEquals(8, t.onTranscript("欧了从新开始" + text.take(8), false, 100).position)
        assertEquals(10, t.onTranscript("欧了从新开始" + text.take(10), true, 200).position)
    }

    @Test fun allAliasesCanRestartAfterTextInTheSameUtterance() {
        for (command in ChineseVoiceCommands.restartPhrases) {
            val t = tracker()
            val prefix = "欧了重新开始" + text.take(8)
            assertEquals(8, t.onTranscript(prefix, false, 0).position)
            assertEquals(0, t.onTranscript(prefix + command, false, 100).position)
            assertEquals(4, t.onTranscript(prefix + command + text.take(4), true, 200).position)
        }
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

    @Test fun commandRejectsMissingWakeUnknownWakeIncompleteActionAndArbitraryEdits() {
        for (phrase in listOf("重新开始", "我们重新来", "从新开始", "从头开始", "返回开始", "欧了重新开", "欧洲重新开始", "欧门重新开始", "欧了重新走", "欧了返回开", "欧了重心开使")) {
            val t = tracker()
            t.reset(t.size)
            assertEquals(phrase, t.size, t.onTranscript(phrase, true, 0).position)
            assertNull(t.confirmRetake(1000))
        }
    }

    @Test fun commandHotwordsSurviveTheVocabularyLimit() {
        val words = hotwordList(SpeechLang.ZH, List(1600) { "中文" } + listOf("欧了重新来")).split('/')
        val commands = (ChineseVoiceCommands.restartPhrases + "欧了").map { "$it :3.0" }
        assertEquals(commands + "中文", words)
        assertEquals(commands.joinToString("/"), hotwordList(SpeechLang.ZH, listOf("正文"), includeScriptWords = false))
        val large = hotwordList(SpeechLang.ZH, List(1600) { "专" + (0x4e00 + it).toChar() }).split('/')
        assertEquals(1500, large.size)
        assertEquals(commands, large.take(commands.size))
        assertFalse(hotwordList(SpeechLang.EN, listOf("English")).contains("欧了"))
    }

    @Test fun homophoneCommandsAreAcceptedOnFinalWithoutBroadEditMatching() {
        for (phrase in listOf("欧乐重新开始", "欧勒重新来", "哦了从头开始", "噢了返回开始", "欧了重新莱", "欧了重心开始", "欧了重新开使", "欧了反回开始")) {
            val t = tracker().apply { reset(size) }
            assertEquals(phrase, 0, t.onTranscript(phrase, true, 100).position)
        }
    }

    @Test fun weakPartialsNeedTwoDifferentHypothesesAndDoNotRestartAgainOnFinal() {
        val t = tracker().apply { reset(size) }
        assertEquals(t.size, t.onTranscript("欧乐重新开始", false, 100).position)
        assertEquals(t.size, t.onTranscript("欧乐重新开始", false, 200).position)
        val confirmed = t.onTranscript("欧乐重新开始" + text.take(4), false, 300)
        assertEquals(4, confirmed.position)
        assertTrue(confirmed.reason.startsWith("restart"))
        val final = t.onTranscript("欧乐重新开始" + text.take(8), true, 400)
        assertEquals(8, final.position)
        assertFalse(final.reason.startsWith("restart"))
    }

    @Test fun everyCommandCanBeSplitAcrossFinalBoundaries() {
        for (phrase in ChineseVoiceCommands.restartPhrases) for (split in 1 until phrase.length) {
            val t = tracker().apply { reset(size) }
            assertEquals(t.size, t.onTranscript(phrase.take(split), true, 100).position)
            val partial = t.onTranscript(phrase.drop(split) + text.take(4), false, 1100)
            assertEquals("$phrase at $split", 4, partial.position)
            assertTrue(partial.reason.startsWith("restart"))
            val final = t.onTranscript(phrase.drop(split) + text.take(8), true, 1300)
            assertEquals(8, final.position)
            assertFalse(final.reason.startsWith("restart"))
        }
    }

    @Test fun consecutiveCommandUtterancesDoNotSwallowTheSecondRestart() {
        for (phrase in ChineseVoiceCommands.restartPhrases) {
            val t = tracker().apply { reset(size) }
            assertEquals(5, t.onTranscript(phrase + text.take(5), true, 100).position)
            val again = t.onTranscript(phrase, true, 1200)
            assertEquals(0, again.position)
            assertTrue(again.reason.startsWith("restart"))
        }
    }

    @Test fun anAsrRevisionCannotRearmAnAlreadyHandledCommand() {
        val t = tracker().apply { reset(size) }
        assertEquals(5, t.onTranscript("欧了重新开始" + text.take(5), false, 100).position)
        t.onTranscript("欧门重新开始" + text.take(5), false, 200)
        val corrected = t.onTranscript("欧了重新开始" + text.take(8), false, 300)
        assertEquals(8, corrected.position)
        assertFalse(corrected.reason.startsWith("restart"))
    }

    @Test fun splitCommandsExpireAndManualSeeksClearTheirPrefix() {
        val expired = tracker().apply { reset(size) }
        expired.onTranscript("欧了", true, 100)
        assertEquals(expired.size, expired.onTranscript("重新开始", true, 2101).position)
        val manual = tracker().apply { reset(size) }
        manual.onTranscript("欧了", true, 100)
        manual.reset(manual.size)
        assertEquals(manual.size, manual.onTranscript("重新开始", true, 1100).position)
    }

    @Test fun commandPrefixCanSpanThreeShortUtterancesWithoutExtendingItsDeadline() {
        val t = tracker().apply { reset(size) }
        t.onTranscript("欧了", true, 100)
        t.onTranscript("重新", true, 700)
        assertEquals(0, t.onTranscript("开始", true, 1400).position)
        val expired = tracker().apply { reset(size) }
        expired.onTranscript("欧了", true, 100)
        expired.onTranscript("重新", true, 1700)
        assertEquals(expired.size, expired.onTranscript("开始", true, 2200).position)
    }

    @Test fun systemPronunciationCanSupplyAnUnlistedActionHomophone() {
        val detector = ChineseVoiceCommands.Detector { if (it == "铠") "kai" else null }
        assertTrue(detector.consume("欧了重新铠始".map(Char::toString), true, 0).restart)
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
