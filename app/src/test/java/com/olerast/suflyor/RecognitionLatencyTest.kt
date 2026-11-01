package com.olerast.suflyor

import com.olerast.suflyor.session.RecognitionLatency
import com.olerast.suflyor.session.HighlightPrediction
import com.olerast.suflyor.session.FollowTiming
import org.junit.Assert.*
import org.junit.Test

class RecognitionLatencyTest {
    @Test fun measuresAudioEmissionLagPlusQueueAndDecodeTime() {
        val l = RecognitionLatency()
        assertEquals(600L, l.observe(1, 800, 300, 1000, 1100, true, false))
        assertEquals(600L, l.observe(1, 1000, 500, 1200, 1300, true, false))
        assertEquals(600L, l.observe(2, 600, 100, 3000, 3100, true, false))
    }

    @Test fun sameTokenAndFinalSilenceDoNotInflateTheEstimate() {
        val l = RecognitionLatency()
        assertEquals(600L, l.observe(1, 800, 300, 1000, 1100, true, false))
        assertEquals(600L, l.observe(1, 1600, 300, 1800, 1900, true, false))
        assertEquals(600L, l.observe(1, 1600, 500, 1800, 1900, true, true))
    }

    @Test fun invalidUnknownAndExpiredTimesAreNeverInvented() {
        val l = RecognitionLatency()
        assertNull(l.observe(1, null, 300, 1000, 1100, true, false))
        assertNull(l.observe(1, 200, 300, 1000, 1100, true, false))
        assertNull(l.observe(1, 800, 300, 1200, 1100, true, false))
        assertNull(l.observe(1, 800, 300, 1000, 1100, false, false))
        assertEquals(600L, l.observe(1, 800, 300, 1000, 1100, true, false))
        assertNull(l.observe(1, null, null, 4000, 4000, true, false))
        l.reset()
        assertNull(l.observe(1, null, null, 5000, 5000, true, false))
    }

    @Test fun measuredLatencyCompensatesTheActualDelayWithTheExistingLeadLimit() {
        val p = HighlightPrediction()
        p.observe(1, false, 0); p.observe(2, false, 200); p.observe(3, false, 400, latencyMs = 600)
        assertEquals(6f, p.forecast(3, 100, 400, true, 6), 0f)
        assertEquals(7f, p.forecast(3, 100, 600, true, 6), 0.001f)
        assertEquals(4f, p.forecast(3, 100, 600, true, 1), 0f)
        assertEquals(3f, p.forecast(3, 100, 600, false, 6), 0f)
    }

    @Test fun previewColoursTextBeforeAConfirmedSpeedSampleWithoutCommittingProgress() {
        val p = HighlightPrediction()
        p.reset(10, 100)
        p.preview(13, 100)
        assertEquals(13f, p.forecast(10, 100, 100, true, 6), 0f)
        assertEquals(0f, p.averageRate, 0f)
        assertEquals(10f, p.forecast(10, 100, 351, true, 6), 0f)
        p.preview(20, 400)
        assertEquals(10f, p.forecast(10, 100, 400, true, 6), 0f)
    }

    @Test fun finalPauseSentenceBoundaryAndManualSeekClearOrBoundPreviews() {
        val p = HighlightPrediction()
        p.reset(10, 100); p.setSentenceEnds(listOf(12, 30)); p.preview(14, 100)
        assertEquals(11f, p.forecast(10, 100, 100, true, 6), 0f)
        assertEquals(10f, p.forecast(10, 100, 101, false, 6), 0f)
        p.observe(10, true, 200)
        assertEquals(10f, p.forecast(10, 100, 200, true, 6), 0f)
        p.preview(11, 300); p.reset(0, 301)
        assertEquals(0f, p.forecast(0, 100, 301, true, 6), 0f)
    }

    @Test fun timingDiagnosticsKeepPhasesSeparateAndResetTheirWindow() {
        val t = FollowTiming()
        t.record(40, 20, 2); t.record(60, 40, 4); t.publish(8)
        assertEquals("Follow ms: queue=50 (max=60), decode=30, align=3, main=8", t.drain())
        assertEquals("Follow ms: queue=0 (max=0), decode=0, align=0, main=0", t.drain())
    }
}
