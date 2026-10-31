package com.olerast.suflyor

import com.olerast.suflyor.speech.*
import com.olerast.suflyor.session.HighlightPrediction
import com.olerast.suflyor.overlay.ReadingMotion
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ln

class AsrObservationTest {
    @Test fun tokenLogScoresAreConvertedAndInvalidMetadataIsNotInvented() {
        val update = AsrUpdate("今天", false, listOf(AsrToken("今", 0.1f, ln(0.8f)), AsrToken("天", 0.3f, ln(0.5f))))
        assertEquals(kotlin.math.sqrt(0.4f), update.confidence!!, 0.001f)
        assertEquals(300L, update.speechTimeMs)
        assertNull(AsrUpdate("今天", false).confidence)
        assertNull(AsrUpdate("今天", false, listOf(AsrToken("今", 0f, -0.1f), AsrToken("天", 0.1f))).confidence)
        assertNull(AsrUpdate("今", false, listOf(AsrToken("今", -1f, 1f))).confidence)
        assertNull(AsrUpdate("今天", false, listOf(AsrToken("今", 0.3f), AsrToken("天", 0.1f))).speechTimeMs)
        assertNull(AsrUpdate("今", false, listOf(AsrToken("今", Float.NaN))).speechTimeMs)
        assertEquals(0f, AsrUpdate("今", false, listOf(AsrToken("今", 0f, Float.NEGATIVE_INFINITY))).confidence!!, 0f)
    }

    @Test fun relativeReliabilityLearnsOnlyConfirmedStrongAnchorsAndResets() {
        val c = AcousticConfidence()
        repeat(10) { c.observe(0.2f, 0.8f) }
        assertTrue(c.score(0.2f)!! < 0.75f)
        repeat(4) { c.observe(0.2f, 1f) }
        assertEquals(0.85f, c.score(0.2f)!!, 0.001f)
        assertTrue(c.score(0.01f)!! < 0.1f)
        assertNull(c.score(null))
        assertNull(c.score(Float.NaN))
        c.reset()
        assertTrue(c.score(0.2f)!! < 0.75f)
    }

    @Test fun acousticTimeMakesBatchedCallbacksProduceTheSameSpeechRate() {
        val p = HighlightPrediction()
        p.reset(0, 0)
        p.observe(1, false, 100, 1000, 1)
        p.observe(2, false, 600, 1200, 1)
        p.observe(3, false, 650, 1400, 1)
        assertEquals(5f, p.averageRate, 0.001f)
        p.observe(4, false, 700, 0, 2)
        p.observe(5, false, 900, 200, 2)
        assertEquals(5f, p.averageRate, 0.001f)
    }

    @Test fun revisedTimestampsAndMissingTimesNeverCreateAnExtremeRate() {
        val p = HighlightPrediction()
        p.observe(1, false, 100, 500, 1)
        p.observe(2, false, 200, 400, 1)
        assertEquals(0f, p.averageRate, 0f)
        p.observe(3, false, 400, 600, 1)
        assertEquals(5f, p.averageRate, 0.001f)
        p.observe(4, false, 600)
        assertEquals(5f, p.averageRate, 0.001f)
    }

    @Test fun weakOrUnmatchedObservationsStopSpeculationImmediately() {
        val p = HighlightPrediction()
        p.observe(1, false, 100); p.observe(2, false, 300)
        assertTrue(p.motionRate(300, true) > 0)
        p.observe(2, false, 400, confidence = 0.01f, trusted = false)
        assertEquals(2, p.target(2, 100, 400, true, 6))
        assertEquals(0f, p.motionRate(400, true), 0f)
        p.observe(3, false, 500, confidence = 0.5f)
        assertTrue(p.motionRate(500, true) <= p.averageRate * 0.5f)
    }

    @Test fun rendererCannotExtrapolateBeyondTheSameShortForecastBudget() {
        val p = HighlightPrediction()
        p.observe(1, false, 100); p.observe(2, false, 300); p.observe(3, false, 500)
        assertTrue(p.horizonSeconds in 0.2f..0.3f)
        val limit = p.forecast(3, 100, 500, true, 6)
        val view = ReadingMotion()
        view.follow(ReadingMotion.Sample(3f, p.motionRate(500, true), limit), 500)
        for (t in 500L..2000L step 16) assertTrue(view.frame(t) <= limit)
        assertEquals(3, p.target(3, 100, 2000, true, 6))
    }
}
