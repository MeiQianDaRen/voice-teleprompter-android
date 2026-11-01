package com.olerast.suflyor

import com.olerast.suflyor.overlay.ReadingMotion
import com.olerast.suflyor.overlay.ViewportMotion
import org.junit.Assert.*
import org.junit.Test

class ViewportMotionTest {
    @Test fun speculativeCrossLineAndPauseDoNotMoveTheConfirmedViewport() {
        val view = ViewportMotion().apply { reset(60f) }
        val text = ReadingMotion()
        text.follow(ReadingMotion.Sample(8f, 8f, 14f, 11f), 0)
        assertTrue(text.frame(400) > 10f)
        text.follow(ReadingMotion.Sample(8f), 400)
        for (time in 400L..1000L step 16) {
            view.follow(60f, time)
            assertEquals(60f, view.frame(time), 0f)
        }
        assertEquals(8f, text.frame(1000), 0f)
    }

    @Test fun repeatedCallbacksDoNotRestartTheLineTransition() {
        val view = ViewportMotion()
        view.follow(60f, 1000)
        for (time in 1000L..1180L step 10) view.follow(60f, time)
        assertEquals(60f, view.frame(1180), 0f)
        assertFalse(view.running(1180))
    }

    @Test fun confirmedForwardProgressIsContinuousAndMonotonic() {
        val view = ViewportMotion()
        view.follow(60f, 1000)
        val before = view.frame(1080)
        view.follow(120f, 1080)
        assertEquals(before, view.frame(1080), 0f)
        var previous = before
        for (time in 1080L..1260L step 10) {
            assertTrue(view.frame(time) >= previous)
            previous = view.frame(time)
        }
        assertEquals(120f, view.frame(1260), 0f)
    }

    @Test fun confirmedRetakeStillScrollsBackSmoothly() {
        val view = ViewportMotion().apply { reset(180f) }
        view.follow(60f, 1000)
        assertEquals(180f, view.frame(1000), 0f)
        assertEquals(120f, view.frame(1175), 0f)
        assertEquals(60f, view.frame(1350), 0f)
    }
}
