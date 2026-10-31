package com.olerast.suflyor

import com.olerast.suflyor.overlay.ReadingMotion
import com.olerast.suflyor.overlay.ScrollMotion
import org.junit.Assert.*
import org.junit.Test

class ReadingMotionTest {
    @Test fun steadyScrollingUsesTheFrameClockAndHasFractionalPositions() {
        val m = ReadingMotion()
        m.follow(ReadingMotion.Sample(0f, 5f, 100f), 0)
        assertEquals(0.08f, m.frame(16), 0.001f)
        assertEquals(2.5f, m.frame(500), 0.001f)
        assertEquals(5f, m.frame(1000), 0.001f)
        assertEquals(m.frame(1000), m.frame(1000), 0f)
    }

    @Test fun smallAsrCorrectionsDoNotInterruptTheSteadyBaseline() {
        val m = ReadingMotion()
        m.follow(ReadingMotion.Sample(0f, 5f, 100f), 0)
        m.follow(ReadingMotion.Sample(4f, 5f, 10f, 5.5f), 1000)
        assertEquals(5f, m.frame(1000), 0f)
        assertEquals(5.5f, m.frame(1100), 0.001f)
    }

    @Test fun predictionNeverRunsAwayBeyondItsSixCharacterCeiling() {
        val m = ReadingMotion()
        m.follow(ReadingMotion.Sample(20f, 12f, 26f, 25f), 0)
        for (t in 0L..3000L step 16) assertTrue(m.frame(t) in 20f..26f)
        assertFalse(m.running(3000))
    }

    @Test fun pauseSettlesSmoothlyAndStaysAtConfirmedProgress() {
        val m = ReadingMotion()
        m.follow(ReadingMotion.Sample(0f, 5f, 10f), 0)
        m.follow(ReadingMotion.Sample(2f), 800)
        assertEquals(4f, m.frame(800), 0f)
        assertTrue(m.frame(865) in 2f..4f)
        assertEquals(2f, m.frame(930), 0f)
        assertEquals(2f, m.frame(5000), 0f)
        assertFalse(m.running(5000))
    }

    @Test fun retakeStartsFromTheDisplayedPositionAndTakes350ms() {
        val m = ReadingMotion()
        m.follow(ReadingMotion.Sample(80f), 0)
        m.follow(ReadingMotion.Sample(20f, revision = 1), 1000)
        assertEquals(80f, m.frame(1000), 0f)
        assertEquals(50f, m.frame(1175), 0.01f)
        assertEquals(20f, m.frame(1350), 0f)
    }

    @Test fun aNewAsrCorrectionStartsAtTheExactCurrentFrame() {
        val m = ReadingMotion()
        m.follow(ReadingMotion.Sample(0f), 0)
        m.follow(ReadingMotion.Sample(10f), 1000)
        val before = m.frame(1100)
        m.follow(ReadingMotion.Sample(15f), 1100)
        assertEquals(before, m.frame(1100), 0f)
        assertEquals(15f, m.frame(1450), 0.01f)
    }

    @Test fun blankParagraphRowsAreTraversedContinuously() {
        assertEquals(90f, ScrollMotion.rowPosition(5f, 0, 10, 0f, 180f), 0f)
        assertEquals(180f, ScrollMotion.rowPosition(10f, 0, 10, 0f, 180f), 0f)
        assertEquals(180f, ScrollMotion.rowPosition(10f, 10, 20, 180f, 240f), 0f)
    }

    @Test fun endingTheLastRowNeverSnapsBackByOneLine() {
        for (p in listOf(20f, 25f, 29.9f, 30f))
            assertEquals(180f, ScrollMotion.rowPosition(p, 20, 30, 180f, 180f), 0f)
    }
}
