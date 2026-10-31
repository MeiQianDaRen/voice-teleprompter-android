package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ChineseNorm
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseReadingsTest {
    @Test fun numbersDatesRangesUnitsAndMathUseSpokenMeaning() {
        for ((written, spoken) in listOf(
            "36.5" to "三十六点五", "−5" to "负五", "1,234.5" to "一千二百三十四点五",
            "5～8" to "五到八", "5–8kg" to "五至八公斤", "−5℃" to "零下五度",
            "2026-10-31" to "二零二六年十月三十一日", "2026年" to "二零二六年",
            "09:05" to "九点零五分", "3×2=6" to "三乘二等于六", "3+2=5" to "三加二等于五",
            "3-2=1" to "三减二等于一", "1/4" to "四分之一", "￥50" to "五十元",
            "2.5km/h" to "二点五千米每小时", "10cm" to "十厘米", "20m²" to "二十平方米",
            "1.4.7" to "一点四点七", "2公斤" to "两千克",
            "3 + 2 = 5" to "三加二等于五", "3 - 2 = 1" to "三减二等于一", "㎡" to "平方米", "m²" to "平方米",
            "5±1" to "五正负一", "3≥2" to "三大于等于二",
        )) {
            assertEquals(written, ChineseNorm.words(spoken), ChineseNorm.words(written))
            val m = model("现在读数为${written}随后请看镜头继续朗读")
            assertEquals(written, m.spokenTokens, ScriptTracker(m.tokens, m.lang).onTranscript("现在读数为${spoken}随后请看镜头继续朗读", true).position)
        }
    }

    @Test fun colonsAndIdentifiersUseNearbyContext() {
        for ((written, spoken) in listOf("时长3:20" to "时长三分二十秒", "比例3:20" to "比例三比二十", "编号2026" to "编号二零二六")) {
            assertEquals(written, ChineseNorm.words(spoken), ChineseNorm.words(written))
            val m = model(written + "请继续看镜头自然说话")
            assertEquals(m.spokenTokens, ScriptTracker(m.tokens, m.lang).onTranscript(spoken + "请继续看镜头自然说话", true).position)
        }
    }

    @Test fun punctuationEmojiAndNotesPreserveOriginalOffsetsWithoutDemandingAPause() {
        val text = "开始，价格￥50；温度−5℃！[请微笑]（请看镜头）✨然后继续说话。"
        val m = model(text)
        assertEquals(text, m.displayText)
        for (t in m.tokens) assertTrue(t.start >= 0 && t.end <= text.length && t.end > t.start)
        val result = ScriptTracker(m.tokens, m.lang).onTranscript("开始价格五十元温度零下五度请看镜头然后继续说话", true)
        assertEquals(m.spokenTokens, result.position)
        assertTrue(m.tokens.filter { it.start in text.indexOf('[')..text.indexOf(']') }.none { it.spoken })
    }

    @Test fun invalidDatesAndTimesAreNotTurnedIntoValidReadings() {
        assertEquals("2026-02-31", ChineseNorm.normalizeWord("2026-02-31"))
        assertEquals("25:99", ChineseNorm.normalizeWord("25:99"))
    }

    @Test fun unknownDecorationsAndUrlsDoNotSwallowFollowingChinese() {
        val m = model("开始★ https://example.com/path 然后继续说话")
        assertEquals(m.spokenTokens, ScriptTracker(m.tokens, m.lang).onTranscript("开始然后继续说话", true).position)
        assertEquals(listOf("https://example.com", "请", "继", "续"), ChineseNorm.words("https://example.com请继续"))
        assertEquals(listOf("标", "题"), ChineseNorm.words("- 标题").filter { it.isNotEmpty() })
    }

    @Test fun clocksDatesAndFullWidthNotesAcceptCommonReadingVariants() {
        for ((written, spoken) in listOf("09:05" to "九点零五", "03:00" to "三点", "2026-10-31" to "二零二六年十月三十一号")) {
            val m = model(written + "继续")
            val t = ScriptTracker(m.tokens, m.lang)
            assertEquals(written, "继", m.tokens[t.onTranscript(spoken, false).nextToken].norm)
        }
        for (phrase in listOf(false, true)) {
            val m = model("开始［停顿］请看镜头", phrase)
            assertEquals(m.spokenTokens, ScriptTracker(m.tokens, m.lang).onTranscript("开始请看镜头", true).position)
            assertEquals("开始［停顿］请看镜头", m.displayText.replace("\n", ""))
        }
    }

    private fun model(text: String, phraseMode: Boolean = false) =
        ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))), phraseMode, lang = SpeechLang.ZH)

    @Test fun measurementsMatchSpokenForms() {
        for ((written, spoken) in listOf(
            "℃" to "摄氏度", "° C" to "摄氏度", "℉" to "华氏度", "°F" to "华氏度",
            "25℃" to "二十五摄氏度", "３６．５℃" to "三十六点五摄氏度", "0℃" to "零摄氏度",
            "-5℃" to "负五摄氏度", "−0.5℃" to "负零点五摄氏度", "+10°C" to "正十摄氏度",
            "25 %" to "百分之二十五", "2.5‰" to "千分之二点五", "0.05%" to "百分之零点零五",
            "10010%" to "百分之一万零一十", "100000001%" to "百分之一亿零一",
            "25摄氏度" to "二十五摄氏度", "百分之25" to "百分之二十五",
        )) {
            assertEquals(written, ChineseNorm.words(spoken), ChineseNorm.words(written))
            val m = model("现在读数为${written}请注意安全")
            val tracker = ScriptTracker(m.tokens, m.lang)
            assertEquals(written, m.spokenTokens, tracker.onTranscript("现在读数为${spoken}请注意安全", true).position)
        }
    }

    @Test fun temperatureAndPercentFinishAtTheSymbolWithoutWaitingForAnotherSentence() {
        for ((written, spoken) in listOf("25℃" to "二十五摄氏度", "25%" to "百分之二十五")) {
            val m = model(written + "请继续")
            val result = ScriptTracker(m.tokens, m.lang).onTranscript(spoken, false)
            assertEquals(written, "请", m.displayText.substring(m.tokens[result.nextToken].start, m.tokens[result.nextToken].end))
        }
    }

    @Test fun partialUnitReadingsWaitUntilTheWholeSymbolHasBeenRead() {
        val m = model("℃继续")
        val tracker = ScriptTracker(m.tokens, m.lang)
        assertEquals(0, tracker.onTranscript("摄", false).nextToken)
        assertEquals(0, tracker.onTranscript("摄氏", false).nextToken)
        assertEquals(1, tracker.onTranscript("摄氏度", true).nextToken)
        assertEquals(m.spokenTokens, tracker.onTranscript("继续", true).position)
    }

    @Test fun manualSeekAndRestartStartAtTheFirstSpokenCharacterOfTheSymbol() {
        val m = model("温度25℃请保持稳定")
        val tracker = ScriptTracker(m.tokens, m.lang)
        val symbol = m.tokens.indexOfFirst { it.norm == "二十五摄氏度" }
        tracker.onTranscript("温度二十五摄氏度请保持稳定", true)
        tracker.jumpToToken(symbol)
        assertEquals(symbol, tracker.onTranscript("二", false).nextToken)
        assertEquals(symbol + 1, tracker.onTranscript("二十五摄氏度", true).nextToken)
        tracker.reset()
        assertEquals(m.spokenTokens, tracker.onTranscript("温度二十五摄氏度请保持稳定", true).position)
    }

    @Test fun phraseBreaksNotesAndSupplementaryHanKeepOriginalDisplayOffsets() {
        val source = "𠀀中，温度３６．５℃。[停顿25℃]湿度25%请继续。"
        for (phraseMode in listOf(false, true)) {
            val m = model(source, phraseMode)
            assertEquals(source, m.displayText.replace("\n", ""))
            for (token in m.tokens) assertEquals(token.norm, ChineseNorm.normalizeWord(m.displayText.substring(token.start, token.end)))
            assertTrue(m.tokens.filter { !it.spoken }.any { it.norm == "二十五摄氏度" })
            val result = ScriptTracker(m.tokens, m.lang).onTranscript("𠀀中温度三十六点五摄氏度湿度百分之二十五请继续", true)
            assertEquals(m.spokenTokens, result.position)
        }
    }

    @Test fun recognizerMayPrintDigitMeasurementsOrSeparateChineseCharacters() {
        val m = model("温度25℃湿度25%继续")
        for (heard in listOf("温度25摄氏度湿度百分之25继续", "温 度 二 十 五 摄 氏 度 湿 度 百 分 之 二 十 五 继 续")) {
            assertEquals(m.spokenTokens, ScriptTracker(m.tokens, m.lang).onTranscript(heard, true).position)
        }
    }

    @Test fun temperatureCanBeReadWithTheScaleBeforeTheNumberOrWithJustDegrees() {
        val m = model("温度36.5℃请继续")
        for (heard in listOf("温度摄氏三十六点五度请继续", "温度摄氏36.5度请继续", "温 度 摄 氏 三 十 六 点 五 度 请 继 续", "温度三十六点五度请继续")) {
            assertEquals(heard, m.spokenTokens, ScriptTracker(m.tokens, m.lang).onTranscript(heard, true).position)
        }
        val written = model("温度摄氏三十六点五度请继续")
        assertEquals(written.spokenTokens, ScriptTracker(written.tokens, written.lang).onTranscript("温度三十六点五摄氏度请继续", true).position)
    }

    @Test fun unsupportedHugeMeasurementsAreBoundedAndDoNotThrow() {
        val text = "9".repeat(1000) + "℃"
        val m = model("开始" + text + "随后请看镜头自然说话")
        val tracker = ScriptTracker(m.tokens, m.lang)
        assertEquals(m.spokenTokens, tracker.onTranscript("开始随后请看镜头自然说话", true).position)
    }

    @Test fun aLongWrongPrefixRecoversOnANewExactPhrase() {
        val m = model("今天我们介绍拍摄技巧随后请看镜头保持自然语速")
        val tracker = ScriptTracker(m.tokens, m.lang)
        tracker.onTranscript("今天我们介绍", true)
        val result = tracker.onTranscript("这部分连续识别错误完全不对应稿件随后请看镜头保持自然语速", false)
        assertEquals(m.spokenTokens, result.position)
        assertTrue(result.reason.startsWith("follow"))
    }

    @Test fun aWrongPrefixWithoutSixCorrectTrailingCharactersCannotRecover() {
        val m = model("今天我们介绍拍摄技巧随后请看镜头保持自然语速")
        val tracker = ScriptTracker(m.tokens, m.lang)
        tracker.onTranscript("今天我们介绍", true)
        val before = tracker.position
        for (heard in listOf("完全无关的内容的我在", "完全无关的内容自然语速", "随后请看镜头完全无关的内容")) {
            assertEquals(heard, before, tracker.onTranscript(heard, false).position)
        }
    }

    @Test fun ambiguousRecoveryDoesNotChooseARepeatedPhrase() {
        val m = model("今天我们介绍请看镜头自然说话中间换个话题请看镜头自然说话")
        val tracker = ScriptTracker(m.tokens, m.lang)
        tracker.onTranscript("今天我们介绍", true)
        val before = tracker.position
        assertEquals(before, tracker.onTranscript("完全无关的一长段识别结果请看镜头自然说话", false).position)
    }

    @Test fun recoveryStaysInsideTheNearbySearchWindow() {
        val m = model("今天我们介绍" + "中间尚未朗读的内容".repeat(10) + "请看镜头保持自然语速")
        val tracker = ScriptTracker(m.tokens, m.lang)
        tracker.onTranscript("今天我们介绍", true)
        val before = tracker.position
        assertEquals(before, tracker.onTranscript("完全无关的一长段识别结果请看镜头保持自然语速", false).position)
    }

    @Test fun aWrongFinalDoesNotBlockTheFollowingUtterance() {
        val m = model("今天我们介绍拍摄技巧随后请看镜头保持自然语速")
        val tracker = ScriptTracker(m.tokens, m.lang)
        tracker.onTranscript("今天我们介绍", true)
        tracker.onTranscript("完全无关的一长段识别结果", true)
        assertEquals(m.spokenTokens, tracker.onTranscript("随后请看镜头保持自然语速", true).position)
    }
}
