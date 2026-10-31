package com.olerast.suflyor

import com.olerast.suflyor.overlay.HighlightMotion
import org.junit.Assert.*
import org.junit.Test

class HighlightMotionTest {
    private fun box(x: Float, row: Int = 0) = HighlightMotion.Box(x, row * 30f, x + 20f, row * 30f + 24f, row)

    @Test fun sameRowSlidesContinuouslyAndFinishes() {
        val m = HighlightMotion()
        m.move(box(0f), 0, false)
        m.move(box(20f), 1000, true)
        assertEquals(0f, m.frame(1000).box!!.left, 0f)
        assertEquals(10f, m.frame(1080).box!!.left, 0.01f)
        assertEquals(20f, m.frame(1160).box!!.left, 0f)
        assertEquals(1f, m.frame(1080).alpha, 0f)
        assertFalse(m.running(1160))
    }

    @Test fun retargetingStartsAtTheCurrentDisplayedFocus() {
        val m = HighlightMotion()
        m.move(box(0f), 0, false); m.move(box(20f), 1000, true)
        val current = m.frame(1080)
        m.move(box(40f), 1080, true)
        assertEquals(current.box, m.frame(1080).box)
        assertEquals(40f, m.frame(1240).box!!.left, 0f)
    }

    @Test fun crossingRowsFadesInAtTheNewRowWithoutADiagonalSweep() {
        val m = HighlightMotion()
        m.move(box(0f), 0, false); m.move(box(80f, 2), 1000, true)
        assertEquals(box(80f, 2), m.frame(1000).box)
        assertEquals(0f, m.frame(1000).alpha, 0f)
        assertEquals(0.5f, m.frame(1080).alpha, 0.01f)
        assertEquals(1f, m.frame(1160).alpha, 0f)
    }

    @Test fun finalCorrectionMovesBackSmoothly() {
        val m = HighlightMotion()
        m.move(box(100f), 0, false); m.move(box(40f), 1000, true)
        var previous = 100f
        for (time in 1000L..1160L step 10) {
            val value = m.frame(time).box!!.left
            assertTrue(value in 40f..100f && value <= previous)
            previous = value
        }
    }

    @Test fun finishedScriptFadesOutAndStopsDrawingAnimationFrames() {
        val m = HighlightMotion()
        m.move(box(0f), 0, false); m.move(null, 1000, true)
        assertEquals(0.5f, m.frame(1080).alpha, 0.01f)
        assertEquals(0f, m.frame(1160).alpha, 0f)
        assertFalse(m.running(1160))
    }

    @Test fun layoutChangesCanSnapToTheNewGeometry() {
        val m = HighlightMotion()
        m.move(box(0f), 0, false); m.move(box(40f, 3), 1000, false)
        assertEquals(box(40f, 3), m.frame(1000).box)
        assertEquals(1f, m.frame(1000).alpha, 0f)
    }
}
