package com.olerast.suflyor

import com.olerast.suflyor.overlay.OverlayState
import org.junit.Assert.*
import org.junit.Test

class OverlayStateTest {
    @Test fun openingTheAppAloneDoesNotShowAShortcut() {
        assertFalse(OverlayState().showButton(appOpen = true, enabled = true))
    }

    @Test fun hidingTheTextKeepsTheSessionAndShortcutRunning() {
        val state = OverlayState()
        state.begin()
        state.toggle()
        assertTrue(state.active)
        assertFalse(state.visible)
        assertTrue(state.showButton(appOpen = true, enabled = true))
        state.toggle()
        assertTrue(state.visible)
    }

    @Test fun disablingTheShortcutDoesNotChangeTheSessionOrText() {
        val state = OverlayState()
        state.begin()
        assertFalse(state.showButton(appOpen = true, enabled = false))
        assertTrue(state.active)
        assertTrue(state.visible)
    }

    @Test fun exitClearsHiddenTextAndShortcutAndNextSessionStartsVisible() {
        val state = OverlayState()
        state.begin()
        state.toggle()
        state.end()
        state.toggle()
        state.show()
        assertFalse(state.active)
        assertFalse(state.visible)
        assertFalse(state.showButton(appOpen = true, enabled = true))
        state.begin()
        assertTrue(state.visible)
    }

    @Test fun explicitRestartShowsTheExistingSessionWithoutEndingIt() {
        val state = OverlayState()
        state.begin()
        state.toggle()
        state.show()
        assertTrue(state.active)
        assertTrue(state.visible)
        assertFalse(state.showButton(appOpen = false, enabled = true))
    }
}
