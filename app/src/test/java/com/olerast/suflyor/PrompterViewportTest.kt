package com.olerast.suflyor

import com.olerast.suflyor.overlay.PrompterViewport
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class PrompterViewportTest {
    private fun offset(read: Int, height: Int, current: Int = 10, pitch: Int = 30, ink: Int = pitch) =
        PrompterViewport.readOffset(current, read, height, { it * pitch }, { it * pitch + ink })

    @Test fun defaultZeroKeepsCurrentLineAtTheTop() {
        assertEquals(0, offset(0, 300))
        assertEquals(0, offset(-10, 300))
    }

    @Test fun requestedWholeReadLinesAreKeptWhenTheyFit() {
        assertEquals(60, offset(2, 150))
        assertEquals(90, offset(3, 150))
    }

    @Test fun oversizedRequestAlwaysReservesOneCurrentLine() {
        assertEquals(60, offset(Int.MAX_VALUE, 90))
        assertEquals(0, offset(100, 30))
        assertEquals(0, offset(100, 0))
        assertEquals(0, offset(100, 20))
    }

    @Test fun actualLayoutBottomControlsTheExactFitBoundary() {
        assertEquals(60, offset(9, 80, ink = 20))
        assertEquals(30, offset(9, 79, ink = 20))
    }

    @Test fun fewerReadLinesAreShownAfterResizingOrRotating() {
        assertEquals(120, offset(4, 150))
        assertEquals(30, offset(4, 80))
        assertEquals(120, offset(4, 150))
    }

    @Test fun largerFontAndSpacingReduceReadLinesWithoutClippingCurrentLine() {
        assertEquals(60, offset(8, 90, pitch = 30))
        assertEquals(40, offset(8, 90, pitch = 40))
        assertEquals(0, offset(8, 90, pitch = 60))
    }

    @Test fun beginningOfScriptDoesNotInventReadLines() {
        assertEquals(0, offset(10, 500, current = 0))
        assertEquals(30, offset(10, 500, current = 1))
    }

    @Test fun differentParagraphHeightsUseTheirRealOffsets() {
        val tops = listOf(0, 25, 80, 130)
        assertEquals(50, PrompterViewport.readOffset(3, 10, 75, tops::get, { tops[it] + 20 }))
    }

    @Test fun variedViewportsKeepTheMaximumFittingReadRegionAndTheCurrentLine() {
        val random = Random(731)
        repeat(1000) {
            val current = random.nextInt(31)
            val tops = IntArray(current + 2)
            for (i in 1 until tops.size) tops[i] = tops[i - 1] + 10 + random.nextInt(90)
            val height = random.nextInt(700)
            val requested = random.nextInt(50)
            val offset = PrompterViewport.readOffset(current, requested, height, { tops[it] }, { tops[it + 1] })
            val top = tops[current] - offset
            assertTrue(top >= 0 && top <= tops[current])
            val first = tops.indexOf(top)
            assertTrue(first >= 0)
            val read = current - first
            assertTrue(read <= requested)
            if (tops[current + 1] - tops[current] <= height) {
                assertTrue(tops[current + 1] - top <= height)
            } else assertEquals(0, read)
            if (read < minOf(requested, current)) {
                assertTrue(tops[current + 1] - tops[first - 1] > height)
            }
        }
    }
}
