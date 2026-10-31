package com.olerast.suflyor

import com.olerast.suflyor.session.HighlightPrediction
import org.junit.Assert.*
import org.junit.Test

class HighlightPredictionTest {
    @Test fun compensationScalesTheLearnedRateWithoutChangingTheMeasurement() {
        val p = paced()
        assertEquals(5f, p.motionRate(400, true, 0), 0f)
        assertEquals(6.5f, p.motionRate(400, true, 30), 0.001f)
        assertEquals(3.5f, p.motionRate(400, true, -30), 0.001f)
        assertEquals(5f, p.averageRate, 0f)
        assertEquals(5, paced().target(3, 100, 400, true, 6, 0))
        assertEquals(8, paced().target(3, 100, 400, true, 6, 100))
        assertEquals(4, paced().target(3, 100, 400, true, 6, -50))
    }

    @Test fun compensationClampsInvalidSavedValues() {
        val p = paced()
        assertEquals(2.5f, p.motionRate(400, true, Int.MIN_VALUE), 0f)
        assertEquals(10f, p.motionRate(400, true, Int.MAX_VALUE), 0f)
    }

    @Test fun compensationCannotBypassLeadOrSentenceLimits() {
        val p = paced()
        assertEquals(9, p.target(3, 100, 1200, true, 6, 100))
        p.setSentenceEnds(listOf(5))
        assertEquals(4, p.target(3, 100, 1200, true, 6, 100))
        assertEquals(3, p.target(3, 100, 1200, true, 0, 100))
    }

    @Test fun compensationCannotCreateSpeechProgressOrOverrideStops() {
        val cold = HighlightPrediction().apply { reset(0, 0) }
        assertEquals(0f, cold.motionRate(100, true, 100), 0f)
        assertEquals(0, cold.target(0, 100, 100, true, 6, 100))
        val p = paced()
        assertEquals(0f, p.motionRate(400, false, 100), 0f)
        assertEquals(0f, p.motionRate(1601, true, 100), 0f)
        p.observe(3, true, 500)
        assertEquals(0f, p.motionRate(500, true, 100), 0f)
        assertEquals(3, p.target(3, 100, 500, true, 6, 100))
        p.reset(0, 600)
        assertEquals(0f, p.motionRate(600, true, 100), 0f)
    }

    @Test fun cadenceReactsQuicklyToFasterAndSlowerReading() {
        val p = paced()
        p.observe(5, false, 600)
        assertTrue(p.averageRate >= 8f)
        p.observe(6, false, 1000)
        assertTrue(p.averageRate < 5f)
    }

    @Test fun rapidPartialBurstsNeedARealTimeSpanBeforeEstimatingCadence() {
        val p = HighlightPrediction()
        p.reset(0, 0)
        p.observe(1, false, 100)
        p.observe(2, false, 110)
        assertEquals(0f, p.motionRate(110, true), 0f)
        p.observe(3, false, 300)
        assertEquals(10f, p.motionRate(300, true), 0.01f)
    }
    @Test fun unmatchedSpeechCannotStartTheVisualBaseline() {
        val p = HighlightPrediction()
        p.reset(0, 0)
        p.observe(0, false, 100)
        assertEquals(0f, p.motionRate(100, true), 0f)
        p.observe(1, false, 200)
        assertEquals(0f, p.motionRate(200, true), 0f)
        p.observe(2, false, 400)
        assertEquals(5f, p.motionRate(400, true), 0f)
    }

    @Test fun baselineWaitsAtFinalPauseAndSentenceCeilings() {
        val p = paced()
        assertEquals(5f, p.motionRate(500, true), 0f)
        assertEquals(0f, p.motionRate(500, false), 0f)
        assertEquals(0f, p.motionRate(1700, true), 0f)
        p.observe(3, true, 550)
        assertEquals(0f, p.motionRate(550, true), 0f)
        p.setSentenceEnds(listOf(5, 10))
        assertEquals(4, p.ceiling(3, 10, 6))
        assertEquals(3, p.ceiling(3, 10, 0))
    }
    private fun paced() = HighlightPrediction().apply {
        reset(0, 0)
        observe(1, false, 0); observe(2, false, 200); observe(3, false, 400)
    }
    private fun HighlightPrediction.at(actual: Int, now: Long, active: Boolean = true, limit: Int = 6) =
        target(actual, 100, now, active, limit)

    @Test fun cadenceStartsAfterTwoReliableAdvancesWithoutAManualFallback() {
        val p = HighlightPrediction()
        p.reset(0, 0)
        assertEquals(0, p.at(0, 0))
        p.observe(1, false, 100)
        assertEquals(1, p.at(1, 100))
        p.observe(2, false, 300)
        assertTrue(p.at(2, 300) > 2)
        assertEquals(5f, p.averageRate, 0.01f)
    }

    @Test fun averageSpeedCompensatesLatencyAndExtrapolatesBetweenUpdates() {
        val p = paced()
        assertEquals(5f, p.averageRate, 0.01f)
        assertEquals(5, p.at(3, 400))
        assertEquals(6, p.at(3, 600))
        assertEquals(7, p.at(3, 800))
    }

    @Test fun predictionHasAHardLeadLimitEvenAtHighSpeed() {
        val p = HighlightPrediction()
        p.observe(3, false, 0); p.observe(6, false, 200); p.observe(9, false, 400)
        assertTrue(p.at(9, 1000) <= 15)
        assertEquals(10, p.at(9, 1000, limit = 1))
        assertEquals(9, p.at(9, 1000, limit = 0))
    }

    @Test fun finalReturnsToActualAndCannotPredictAgainWithoutNewProgress() {
        val p = paced()
        assertTrue(p.at(3, 500) > 3)
        p.observe(3, true, 550)
        assertEquals(3, p.at(3, 550))
        assertEquals(3, p.at(3, 700))
        p.observe(4, false, 800)
        assertTrue(p.at(4, 800) > 4)
    }

    @Test fun textualSentenceBoundaryCapsAndSettlesTheForecast() {
        val p = HighlightPrediction()
        p.setSentenceEnds(listOf(10, 20))
        p.observe(6, false, 0); p.observe(7, false, 200); p.observe(8, false, 400)
        assertEquals(9, p.at(8, 700))
        p.observe(10, false, 800)
        assertEquals(10, p.at(10, 800))
        p.observe(11, false, 1000)
        assertTrue(p.at(11, 1000) > 11)
        assertTrue(p.at(11, 1000) < 20)
    }

    @Test fun staleOrInactiveSpeechSettlesWithoutDriftingOnATimer() {
        val p = paced()
        assertTrue(p.at(3, 1600) > 3)
        assertEquals(3, p.at(3, 1601))
        assertEquals(3, p.at(3, 1700))
        val paused = paced()
        assertEquals(3, paused.at(3, 500, active = false))
        assertEquals(3, paused.at(3, 600))
    }

    @Test fun manualSeekAndRetakeDiscardTheOldRateAndForecast() {
        val p = paced()
        p.at(3, 600)
        p.reset(20, 700)
        assertEquals(20, p.at(20, 700))
        assertEquals(0f, p.averageRate, 0f)
        val back = paced()
        back.observe(1, false, 600)
        assertEquals(1, back.at(1, 600))
        assertEquals(0f, back.averageRate, 0f)
    }

    @Test fun largeRecognitionJumpsAreNotMistakenForFastSpeaking() {
        val p = paced()
        p.observe(50, false, 600)
        assertEquals(50, p.at(50, 600))
        assertEquals(0f, p.averageRate, 0f)
    }

    @Test fun unevenBatchSizesProduceTheSameAverageRate() {
        val batch = HighlightPrediction()
        batch.observe(2, false, 0); batch.observe(4, false, 400); batch.observe(6, false, 800)
        assertEquals(paced().averageRate, batch.averageRate, 0.01f)
    }

    @Test fun slowerNewSpeechDoesNotMakeFocusWobbleBackwardMidSentence() {
        val p = paced()
        val ahead = p.at(3, 800)
        p.observe(4, false, 900)
        assertTrue(p.at(4, 900) >= ahead)
        assertTrue(p.at(4, 900) <= 10)
    }

    @Test fun endOfScriptAndUnexpectedPositionAreBounded() {
        val p = paced()
        assertEquals(3, p.target(3, 4, 700, true, 6))
        assertEquals(4, p.target(4, 4, 700, true, 6))
        assertEquals(60, p.at(60, 700))
    }

    @Test fun aLongPauseRequiresFreshSpeedSamples() {
        val p = paced()
        p.observe(4, false, 3000)
        assertEquals(4, p.at(4, 3000))
        assertEquals(0f, p.averageRate, 0f)
    }
}
