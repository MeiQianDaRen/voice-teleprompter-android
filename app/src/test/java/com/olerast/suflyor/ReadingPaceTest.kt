package com.olerast.suflyor

import com.olerast.suflyor.session.ReadingPace
import org.junit.Assert.*
import org.junit.Test

class ReadingPaceTest {
    @Test fun explicitTimerModesRetainSpeedAndFineAdjustment() {
        assertEquals(65f, ReadingPace.perMinute(130, 50), 0f)
        assertEquals(136.5f, ReadingPace.perMinute(130, 105), 0.001f)
        assertEquals(390f, ReadingPace.perMinute(130, 300), 0f)
    }
    @Test fun manualSpeedAndFineAdjustmentWorkIndependentlyOfScriptLength() {
        assertEquals(136.5f, ReadingPace.perMinute(130, 105), 0.001f)
    }
    @Test fun emptyScriptsAndOutOfRangePreferencesAreSafe() {
        assertEquals(780f, ReadingPace.perMinute(999, 999), 0f)
        assertEquals(30f, ReadingPace.perMinute(-1, -1), 0f)
    }
}
