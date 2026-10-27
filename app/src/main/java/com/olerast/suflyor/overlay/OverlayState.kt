package com.olerast.suflyor.overlay

/** A hidden prompter is still a running session; only ending it removes the shortcut. */
internal class OverlayState {
    var active = false
        private set
    var visible = false
        private set

    fun begin() { active = true; visible = true }
    fun end() { active = false; visible = false }
    fun show() { if (active) visible = true }
    fun toggle() { if (active) visible = !visible }
    fun showButton(appOpen: Boolean, enabled: Boolean): Boolean = active && appOpen && enabled
}
