package com.olerast.suflyor

import com.olerast.suflyor.overlay.ScrollMotion
import org.junit.Assert.*
import org.junit.Test

class ScrollMotionTest {
    @Test fun correctionsUseDifferentDurations() {
        assertEquals(130L, ScrollMotion.duration(3))
        assertEquals(250L, ScrollMotion.duration(8))
        assertEquals(350L, ScrollMotion.duration(20))
        assertEquals(350L, ScrollMotion.duration(-4))
    }
    @Test fun deadZoneNeverSuppressesANecessaryLineChange() {
        assertFalse(ScrollMotion.shouldScroll(2, false))
        assertTrue(ScrollMotion.shouldScroll(1, true))
        assertTrue(ScrollMotion.shouldScroll(4, false))
    }
    @Test fun smoothMotionIsBoundedMonotonicAndFinishesAtItsDeadline() {
        for ((start, end) in listOf(0f to 100f, 500f to 10f)) {
            var previous = start
            for (time in 0L..350L step 7) {
                val value = ScrollMotion.position(start, end, time, 350)
                assertTrue(value in minOf(start, end)..maxOf(start, end))
                assertTrue(if (end > start) value >= previous else value <= previous)
                previous = value
            }
            assertEquals(end, ScrollMotion.position(start, end, 350, 350), 0f)
        }
    }
    @Test fun interruptionsCanRestartFromTheExactDisplayedPosition() {
        val halfway = ScrollMotion.position(0f, 100f, 125, 250)
        assertEquals(halfway, ScrollMotion.position(halfway, 200f, 0, 130), 0f)
        assertEquals(200f, ScrollMotion.position(halfway, 200f, 130, 130), 0f)
    }
}
