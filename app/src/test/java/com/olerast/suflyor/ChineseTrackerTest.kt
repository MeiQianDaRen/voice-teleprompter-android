package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ChineseNorm
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.ScriptModel
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.script.biasWords
import com.olerast.suflyor.speech.hotwordList
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseTrackerTest {
    private val zh = SpeechLang.ZH

    private fun build(vararg lines: String, phraseMode: Boolean = true): ScriptModel =
        ScriptLayout.build(ScriptDocument("中文", "test", lines.map { Paragraph(it) }), phraseMode, lang = zh)

    private fun tracker(model: ScriptModel) = ScriptTracker(model.tokens, model.lang)

    private fun read(tracker: ScriptTracker, text: String, history: MutableList<String> = mutableListOf()) {
        for (word in zh.words(text)) {
            history += word
            tracker.onHypothesis(history.takeLast(16))
        }
    }

    @Test
    fun detectsChineseOutsideHeadingsAndNotes() {
        fun detect(vararg paragraphs: Paragraph) = SpeechLang.detect(ScriptDocument("", "test", paragraphs.toList()))
        assertEquals(zh, detect(Paragraph("这是一个中文提词稿件。")))
        assertEquals(zh, detect(Paragraph("今天我们介绍 AI 手机拍摄。")))
        assertEquals(SpeechLang.EN, detect(Paragraph("中文标题", Paragraph.Kind.HEADING), Paragraph("[微笑] Hello everyone.")))
        assertEquals(SpeechLang.EN, detect())
        assertEquals(zh, detect(Paragraph("𠀀𠀁")))
        assertEquals(zh, SpeechLang.fromCode("zh"))
        assertEquals(listOf(zh, SpeechLang.EN), SpeechLang.supported)
        assertEquals(null, SpeechLang.fromCode("ru"))
    }

    @Test
    fun tokenizesJoinedAndSpaceSeparatedModelOutputIdentically() {
        assertEquals(listOf("你", "好", "世", "界"), zh.words("你好，世界！"))
        assertEquals(zh.words("你好世界"), zh.words("你 好 世 界"))
        assertEquals(listOf("中", "文", "abc123", "零"), zh.words("中文ＡＢＣ１２３〇"))
        assertEquals(listOf("𠀀", "中"), zh.words("𠀀中"))
        assertFalse(zh.canBePrefix("中"))
        assertEquals(0f, zh.similarity("在", "再"), 0f)
        assertEquals(1f, zh.similarity("中", "中"), 0f)
    }

    @Test
    fun insertsPhraseBreaksWithoutLosingPunctuationOrFormatting() {
        val text = "大家好，今天我们演示中文提词器。[停顿，微笑]现在开始。"
        val original = Paragraph(text, emphasis = listOf(4..12))
        val m = ScriptLayout.build(ScriptDocument("", "test", listOf(original)), true, 5, zh)
        assertTrue(m.displayText.contains("，\n"))
        assertEquals(text, m.displayText.replace("\n", ""))
        assertEquals(text.substring(4..12), m.displayText.substring(m.emphasis.single()).replace("\n", ""))
        assertTrue(m.tokens.filter { !it.spoken }.map { it.norm }.containsAll(listOf("停", "顿", "微", "笑")))
        for (t in m.tokens) assertEquals(t.norm, zh.normalize(m.displayText.substring(t.start, t.end)))
        assertTrue(m.tokens.first { it.norm == "今" }.anchor)
        assertFalse(m.displayText.substringAfter('[').substringBefore(']').contains('\n'))
        assertEquals(text, build(text, phraseMode = false).displayText)
    }

    @Test
    fun followsUnspacedChineseOneCharacterAtATime() {
        val m = build("大家好，今天我们演示离线中文语音跟随提词。", "请看镜头，读错以后可以重新开始。")
        val t = tracker(m)
        val history = mutableListOf<String>()
        read(t, "大家好今天我们演示离线中文语音跟随提词", history)
        read(t, "请看镜头读错以后可以重新开始", history)
        assertEquals(m.spokenTokens, t.position)
        assertEquals(m.tokens.size, t.nextTokenIndex())
    }

    @Test
    fun handlesBatchedPartialsAndCorrectedHypotheses() {
        val m = build("今天我们介绍如何使用手机拍摄视频。")
        val t = tracker(m)
        t.onHypothesis(zh.words("今天我们介绍"))
        assertTrue(t.position >= 5)
        t.onHypothesis(zh.words("今天我们介绍如和"))
        t.onHypothesis(zh.words("今 天 我 们 介 绍 如 何 使 用 手 机"))
        t.onHypothesis(zh.words("今天我们介绍如何使用手机拍摄视频"))
        assertEquals(m.spokenTokens, t.position)
    }

    @Test
    fun silenceFillersAndIsolatedCommonCharactersDoNotJump() {
        val m = build("大家好，今天我们演示离线中文语音跟随提词。", "这是另一个段落，我们稍后再讲。")
        val t = tracker(m)
        read(t, "大家好今天我们演示")
        val before = t.position
        for (text in listOf("", "嗯 呃", "我", "的", "在", "天气不错但是晚饭还没准备好")) {
            t.onHypothesis(zh.words(text))
            assertEquals(text, before, t.position)
        }
    }

    @Test
    fun retakesAndSkippingParagraphsNeedAContinuousPhrase() {
        val m = build(
            "第一段我们讲解镜头构图和光线。",
            "第二段现在介绍手机录制的操作。",
            "最后请记得保存文件并分享作品。",
        )
        val t = tracker(m)
        val history = mutableListOf<String>()
        read(t, "第一段我们讲解镜头构图和光线第二段现在介绍手机录制的操作", history)
        val firstEnd = m.tokens.indexOfFirst { it.norm == "第" && it.start > 0 }
        assertTrue(t.position > firstEnd)
        read(t, "第一段我们讲解镜头构图和光线", history)
        assertEquals(firstEnd, t.position)
        read(t, "最后请记得保存文件并分享作品", history)
        assertEquals(m.spokenTokens, t.position)
    }

    @Test
    fun notesForeignTextAndDigitRunsKeepTheirDisplayOffsets() {
        val m = build("今天使用 Android 2026 版本进行录制，[停顿]请保持自然的语速。")
        val t = tracker(m)
        read(t, "今天使用版本进行录制请保持自然的语速")
        assertEquals(m.spokenTokens, t.position)
        assertTrue(m.tokens.first { it.norm == "android" }.weight == 0f)
        assertTrue(m.tokens.first { it.norm == "2026" }.weight == 0f)
        val target = m.tokens.indexOfFirst { it.norm == "请" }
        t.jumpToToken(target)
        assertEquals(target, t.nextTokenIndex())
    }

    @Test
    fun buildsChinesePhraseHotwordsWithoutStageDirectionsOrLatin() {
        val m = build("中文提词器[请微笑]离线识别 Android。")
        val phrases = m.biasWords()
        assertTrue(phrases.containsAll(listOf("中文", "中文提词", "离线识别")))
        assertFalse(phrases.any { it.contains("微笑") || it.contains("Android") || it.length == 1 })
        assertEquals("欧了重新开始 :3.0/欧了从新开始 :3.0/欧了重新来 :3.0/欧了从新来 :3.0/欧了从头开始 :3.0/欧了返回开始 :3.0/欧了 :3.0/中文/提词器", hotwordList(zh, listOf("中文", "提词器", "中文", "中", "Android", "中文/提词")))
        assertTrue(phrases.size <= 1500)
    }

    @Test
    fun repeatedPhrasesDoNotSendTheReaderToAnotherParagraph() {
        val m = build("今天我们准备开始录制。", "中间有一个完全不同的话题需要慢慢说明。", "今天我们准备开始录制。")
        val t = tracker(m)
        read(t, "今天我们准备开始录制")
        val firstEnd = m.tokens.indexOfFirst { it.norm == "中" }
        assertEquals(firstEnd, t.position)
        t.onHypothesis(zh.words("今天"))
        assertEquals(firstEnd, t.position)
    }
}
