package com.olerast.suflyor

import com.olerast.suflyor.overlay.OverlayGeometry as G
import org.junit.Assert.*
import org.junit.Test

class OverlayGeometryTest {
    private val rect = G.Rect(100, 120, 400, 300)

    @Test fun horizontalEdgesChangeWidthWithoutChangingHeight() {
        assertEquals(G.Rect(70, 120, 430, 300), G.resize(rect, G.LEFT, -30, 90, 1000, 800, 240, 180))
        assertEquals(G.Rect(100, 120, 460, 300), G.resize(rect, G.RIGHT, 60, -80, 1000, 800, 240, 180))
    }

    @Test fun verticalEdgesChangeHeightWithoutChangingWidth() {
        assertEquals(G.Rect(100, 70, 400, 350), G.resize(rect, G.TOP, 90, -50, 1000, 800, 240, 180))
        assertEquals(G.Rect(100, 120, 400, 360), G.resize(rect, G.BOTTOM, -80, 60, 1000, 800, 240, 180))
    }

    @Test fun cornerResizeKeepsOppositeCornerFixedAndRespectsMinimum() {
        assertEquals(G.Rect(260, 240, 240, 180), G.resize(rect, G.LEFT or G.TOP, 1000, 1000, 1000, 800, 240, 180))
        assertEquals(G.Rect(100, 120, 900, 680), G.resize(rect, G.RIGHT or G.BOTTOM, 1000, 1000, 1000, 800, 240, 180))
    }

    @Test fun movingInEitherDirectionKeepsSizeAndClampsToScreen() {
        assertEquals(G.Rect(0, 0, 400, 300), G.fit(rect.copy(x = -200, y = -200), 1000, 800, 240, 180))
        assertEquals(G.Rect(600, 500, 400, 300), G.fit(rect.copy(x = 1200, y = 1200), 1000, 800, 240, 180))
    }

    @Test fun bothLandscapeTurnsSwapMinimumAxesAndUseScreenDeltasWithoutSignInversion() {
        for (angle in listOf(90, -90)) {
            val (minW, minH) = G.minimum(angle, 240, 180)
            assertEquals(180 to 240, minW to minH)
            val turned = G.Rect(20, 30, 200, 400)
            assertEquals(G.Rect(20, 30, 230, 400), G.resize(turned, G.RIGHT, 30, 0, 600, 1000, minW, minH))
            assertEquals(G.Rect(20, 10, 200, 420), G.resize(turned, G.TOP, 0, -20, 600, 1000, minW, minH))
        }
        assertEquals(240 to 180, G.minimum(0, 240, 180))
    }

    @Test fun savedBoundsFollowViewportSizeAndInvalidPreferencesAreIgnored() {
        val saved = G.save(G.Rect(100, 160, 400, 320), 1000, 800)
        assertEquals(G.Rect(200, 320, 800, 640), G.restore(saved, 2000, 1600))
        for (value in listOf(null, "", "1,2", "NaN,0,1,1", "0,0,Infinity,1", "-1,0,1,1", "0,0,0,1")) {
            assertNull(value, G.restore(value, 1000, 800))
        }
        assertEquals(G.Rect(0, 0, 100, 80), G.fit(rect, 100, 80, 240, 180))
    }
}
