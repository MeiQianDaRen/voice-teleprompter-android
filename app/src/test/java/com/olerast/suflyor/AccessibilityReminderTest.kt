package com.olerast.suflyor

import com.olerast.suflyor.ui.AccessibilityProblem
import com.olerast.suflyor.ui.AccessibilityReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityReminderTest {
    @Test fun explicitStartReminderDoesNotRepeatAutomatically() {
        val reminder = AccessibilityReminder()
        reminder.shown(AccessibilityProblem.DISCONNECTED)
        assertNull(reminder.check(true, false, true, 0))
        assertNull(reminder.check(true, false, true, 3_000))
    }

    @Test fun firstInstallDoesNotPromptBeforeTheUserChoosesAccessibility() {
        val reminder = AccessibilityReminder()
        assertNull(reminder.check(false, false, false, 0))
        assertNull(reminder.check(false, false, false, 60_000))
    }

    @Test fun enabledButDisconnectedWaitsForNormalBindingThenWarnsOnce() {
        val reminder = AccessibilityReminder()
        assertNull(reminder.check(true, false, false, 0))
        assertNull(reminder.check(true, false, false, 2_999))
        assertEquals(AccessibilityProblem.DISCONNECTED, reminder.check(true, false, false, 3_000))
        assertNull(reminder.check(true, false, false, 90_000))
    }

    @Test fun healthyConnectionDuringGraceCancelsTheWarning() {
        val reminder = AccessibilityReminder()
        reminder.check(true, false, true, 0)
        assertNull(reminder.check(true, true, true, 1_500))
        assertNull(reminder.check(true, true, true, 30_000))
    }

    @Test fun previouslyWorkingServiceThatWasTurnedOffIsAlsoReported() {
        val reminder = AccessibilityReminder()
        assertNull(reminder.check(false, false, true, 1_000))
        assertEquals(AccessibilityProblem.OFF, reminder.check(false, false, true, 4_000))
    }

    @Test fun disabledSystemSettingWinsOverAStaleRunningFlag() {
        val reminder = AccessibilityReminder()
        reminder.check(false, true, true, 0)
        assertEquals(AccessibilityProblem.OFF, reminder.check(false, true, true, 3_000))
    }

    @Test fun returningFromSettingsDoesNotRepeatADismissedWarning() {
        val reminder = AccessibilityReminder()
        reminder.check(true, false, true, 0)
        reminder.check(true, false, true, 3_000)
        reminder.pause()
        assertNull(reminder.check(true, false, true, 10_000))
        assertNull(reminder.check(true, false, true, 20_000))
    }

    @Test fun timeInBackgroundDoesNotCountAsBindingGrace() {
        val reminder = AccessibilityReminder()
        reminder.check(true, false, true, 0)
        reminder.pause()
        assertNull(reminder.check(true, false, true, 100_000))
        assertNull(reminder.check(true, false, true, 102_999))
        assertEquals(AccessibilityProblem.DISCONNECTED, reminder.check(true, false, true, 103_000))
    }

    @Test fun aNewFailureAfterRecoveryCanBeReportedAgain() {
        val reminder = AccessibilityReminder()
        reminder.check(true, false, true, 0)
        reminder.check(true, false, true, 3_000)
        reminder.check(true, true, true, 4_000)
        assertNull(reminder.check(true, false, true, 5_000))
        assertEquals(AccessibilityProblem.DISCONNECTED, reminder.check(true, false, true, 8_000))
    }

    @Test fun changingFaultTypeStartsANewGracePeriod() {
        val reminder = AccessibilityReminder()
        reminder.check(true, false, true, 0)
        assertNull(reminder.check(false, false, true, 2_000))
        assertNull(reminder.check(false, false, true, 3_000))
        assertEquals(AccessibilityProblem.OFF, reminder.check(false, false, true, 5_000))
    }
}
