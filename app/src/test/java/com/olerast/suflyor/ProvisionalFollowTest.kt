package com.olerast.suflyor

import com.olerast.suflyor.doc.*
import com.olerast.suflyor.script.*
import com.olerast.suflyor.track.*
import org.junit.Assert.*
import org.junit.Test

class ProvisionalFollowTest {
    private fun tracker(text: String) = ScriptLayout.build(ScriptDocument("", "test", listOf(Paragraph(text))), false, lang = SpeechLang.ZH)
        .let { ScriptTracker(it.tokens, it.lang) }

    @Test fun clearShortPartialCanHighlightBeforeTheSecondConfirmation() {
        val t = tracker("今天我们介绍手机拍摄")
        val first = t.onTranscript("今天", false, 100, confidence = 0.5f)
        assertEquals(0, first.position)
        assertEquals(2, first.provisionalPosition)
        assertEquals(0, t.position)
        assertNull(t.confirmFollow(1000))
        assertEquals(3, t.onTranscript("今天我", false, 1100, confidence = 0.5f).position)
    }

    @Test fun ambiguousRepetitionWrongTextAndWeakScoresCannotPreview() {
        assertNull(tracker("哈哈哈哈哈哈").onTranscript("哈哈", false, 100, confidence = 0.5f).provisionalPosition)
        assertNull(tracker("今天介绍手机").onTranscript("今夭", false, 100, confidence = 0.5f).provisionalPosition)
        assertNull(tracker("今天介绍手机").onTranscript("今天", false, 100, confidence = 0.01f).provisionalPosition)
    }

    @Test fun distantMatchesAndFinalsCannotBypassStrictPositionConfirmation() {
        val t = tracker("今天介绍手机拍摄方法然后调整镜头")
        assertNull(t.onTranscript("镜头", false, 100, confidence = 0.5f).provisionalPosition)
        assertEquals(0, t.position)
        assertNull(t.onTranscript("今天", true, 200, confidence = 0.5f).provisionalPosition)
    }

    @Test fun pausesRestartCommandsAndRetakesKeepTheirExistingRules() {
        val t = tracker("今天介绍手机拍摄方法然后调整镜头")
        t.onTranscript("今天介绍手机拍摄方法", true, 100)
        val back = t.onTranscript("今天介绍手", false, 200, confidence = 0.5f)
        assertNull(back.provisionalPosition)
        t.silence()
        val command = t.onTranscript("欧了重新开始", true, 300)
        assertEquals(0, command.position)
        assertNull(command.provisionalPosition)
    }
}
