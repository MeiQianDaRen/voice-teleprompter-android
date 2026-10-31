package com.olerast.suflyor

import com.olerast.suflyor.session.ReadingPace
import org.junit.Assert.*
import org.junit.Test

class ReadingPaceTest {
    @Test fun targetDurationEstimatesChineseCharactersOrEnglishWords() {
        assertEquals(200f, ReadingPace.perMinute(600, 180, 130, true, 100), 0f)
        assertEquals(100f, ReadingPace.perMinute(600, 180, 130, true, 50), 0f)
        assertEquals(600f, ReadingPace.perMinute(600, 180, 130, true, 300), 0f)
    }
    @Test fun manualSpeedAndFineAdjustmentWorkIndependentlyOfScriptLength() {
        assertEquals(136.5f, ReadingPace.perMinute(10000, 180, 130, false, 105), 0.001f)
    }
    @Test fun emptyScriptsAndOutOfRangePreferencesAreSafe() {
        assertEquals(0f, ReadingPace.perMinute(0, 0, 130, true, 100), 0f)
        assertEquals(780f, ReadingPace.perMinute(10, 30, 999, false, 999), 0f)
        assertEquals(30f, ReadingPace.perMinute(10, 30, -1, false, -1), 0f)
    }
}
