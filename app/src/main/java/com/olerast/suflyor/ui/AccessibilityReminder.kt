package com.olerast.suflyor.ui

internal enum class AccessibilityProblem { OFF, DISCONNECTED }

/** Wait for normal system binding, then remind once per fault; no timers run in the background. */
internal class AccessibilityReminder(private val graceMs: Long = 3_000L) {
    private var problem: AccessibilityProblem? = null
    private var since: Long? = null
    private var reminded = false

    fun check(enabled: Boolean, running: Boolean, previouslyConnected: Boolean, nowMs: Long): AccessibilityProblem? {
        val next = when {
            enabled && running -> null
            enabled -> AccessibilityProblem.DISCONNECTED
            previouslyConnected -> AccessibilityProblem.OFF
            else -> null
        }
        if (next == null || next != problem) {
            problem = next
            since = null
            reminded = false
        }
        if (next == null) return null
        val started = since ?: nowMs.also { since = it }
        if (reminded || nowMs - started < graceMs) return null
        reminded = true
        return next
    }

    /** Returning from system settings must not immediately repeat a dismissed reminder. */
    fun pause() { since = null }

    fun shown(problem: AccessibilityProblem) {
        this.problem = problem
        reminded = true
    }
}
