package com.olerast.suflyor.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.StringRes
import com.olerast.suflyor.App
import com.olerast.suflyor.R
import com.olerast.suflyor.diag.DiagLog
import com.olerast.suflyor.session.SessionEngine
import com.olerast.suflyor.session.SessionError
import com.olerast.suflyor.session.SessionService

/** Why a session over other apps could not start; [message] tells the user what to do. */
enum class StartProblem(@StringRes val message: Int) {
    NO_HOST(R.string.start_problem_no_host),
    WINDOW_FAILED(R.string.start_problem_window_failed),
}

/** Starts and stops the "over other apps" session: foreground service + engine + floating window. */
object OverlayHost {
    private var controller: OverlayController? = null
    private var button: FloatingButton? = null
    private var buttonHost: Context? = null
    private var watching = false

    /** A session over other apps is running (independent of whether its window currently exists). */
    private val state = OverlayState()
    val active: Boolean get() = state.active
    val windowVisible: Boolean get() = state.visible

    private var screenOffReceiver: BroadcastReceiver? = null

    /** @return null on success, otherwise what the user has to do first. */
    fun startSession(context: Context): StartProblem? {
        val a11y = PrompterAccessibilityService.instance
        val canDraw = Settings.canDrawOverlays(context)
        if (a11y == null && !canDraw) {
            return StartProblem.NO_HOST
        }
        val app = App.instance
        val appContext = context.applicationContext
        // Already running (second tap, tile + app): keep the service, microphone and window; just make sure the
        // window is there. Restarting would publish a transient IDLE and tear the foreground service down.
        if (active && app.engine.state.mode == SessionEngine.Mode.OVERLAY) {
            state.show()
            if (controller == null) showOrStop(context)
            refreshButton(context)
            PrompterTileService.requestUpdate(appContext)
            return null
        }
        if (!watching) {
            watching = true
            app.engine.addListener { s ->
                if (active && s.mode == SessionEngine.Mode.IDLE && !s.starting) {
                    // The microphone failed to start: say so, or the window would just vanish.
                    (s.error as? SessionError.Capture)?.let {
                        Toast.makeText(appContext, it.message(appContext), Toast.LENGTH_LONG).show()
                    }
                    endSession(appContext)
                }
            }
        }
        // Start the engine first: stopping a running rehearsal publishes IDLE, which must not end this new session.
        app.engine.start(SessionEngine.Mode.OVERLAY)
        state.begin()
        // The window first: if it can't be shown, nothing else (foreground service, key filtering) is started.
        if (!showWindow(context)) {
            stopSession(context)
            return StartProblem.WINDOW_FAILED
        }
        context.startForegroundService(Intent(context, SessionService::class.java))
        watchScreen(appContext)
        PrompterAccessibilityService.instance?.onSessionChanged(true)
        refreshButton(context)
        PrompterTileService.requestUpdate(appContext)
        return null
    }

    fun stopSession(context: Context) {
        App.instance.engine.stop()
        endSession(context.applicationContext)
    }

    /** Idempotent cleanup after the engine went idle, whoever stopped it. */
    private fun endSession(appContext: Context) {
        state.end()
        hideWindow()
        screenOffReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        screenOffReceiver = null
        // A service that hasn't reached startForeground yet must not be stopped from outside (the system would
        // kill the app); its pending onStartCommand sees the session is over and stops itself.
        SessionService.stop(appContext)
        PrompterAccessibilityService.instance?.onSessionChanged(false)
        refreshButton(appContext)
        PrompterTileService.requestUpdate(appContext)
    }

    /**
     * Screen off = the take is over (camera apps keep the screen on while recording). Stop listening instead of
     * leaving the microphone open with the prompter over the lock screen.
     */
    private fun watchScreen(appContext: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF && active) {
                    DiagLog.i("Screen off — session stopped")
                    App.instance.exit()
                }
            }
        }
        appContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        screenOffReceiver = receiver
    }

    /** @return whether the prompter window is on screen now. */
    private fun showWindow(context: Context): Boolean {
        hideWindow()
        val a11y = PrompterAccessibilityService.instance
        val next = when {
            a11y != null -> {
                DiagLog.i("Overlay: via the accessibility service (TYPE_ACCESSIBILITY_OVERLAY)")
                OverlayController(a11y, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            }
            Settings.canDrawOverlays(context) -> {
                DiagLog.i("Overlay: plain floating window (TYPE_APPLICATION_OVERLAY), accessibility off")
                OverlayController(context.applicationContext, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            else -> null
        }
        val shown = next != null && runCatching { next.show() }.onFailure { DiagLog.e("Couldn't show the overlay window", it) }.isSuccess
        if (shown) controller = next
        return shown
    }

    /** Mid-session window re-creation. */
    private fun showOrStop(context: Context) {
        if (showWindow(context) || !active) return
        // No way to show the prompter any more (e.g. the accessibility service was turned off mid-session and
        // there is no overlay permission): do not keep the microphone running invisibly.
        Toast.makeText(context.applicationContext, R.string.overlay_toast_closed_a11y_off, Toast.LENGTH_LONG).show()
        stopSession(context)
    }

    fun hideWindow() {
        controller?.hide()
        controller = null
    }

    fun toggleWindow(context: Context) {
        if (!active) return
        state.toggle()
        if (windowVisible) showOrStop(context) else hideWindow()
        refreshButton(context)
    }

    /** App-owned shortcut: unlike a system accessibility shortcut, it has an app-controlled lifetime. */
    fun refreshButton(context: Context) {
        val app = App.instance
        val host: Context? = if (!state.showButton(app.isOpen, app.settings.floatingButton)) null else
            PrompterAccessibilityService.instance ?: context.applicationContext.takeIf { Settings.canDrawOverlays(it) }
        if (host !== buttonHost) {
            button?.hide()
            button = null
            buttonHost = null
            if (host != null) {
                val type = if (host is PrompterAccessibilityService) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                val next = FloatingButton(host, type)
                if (runCatching { next.show() }.onFailure { DiagLog.e("Couldn't show floating button", it) }.isSuccess) {
                    button = next
                    buttonHost = host
                }
            }
        }
        button?.render(windowVisible)
    }

    /** Re-creates the window with the right host when the accessibility service connects or disconnects mid-session. */
    fun onHostChanged(context: Context) {
        if (active && App.instance.engine.state.mode == SessionEngine.Mode.OVERLAY) {
            if (windowVisible) showOrStop(context)
            else if (PrompterAccessibilityService.instance == null && !Settings.canDrawOverlays(context)) stopSession(context)
        }
        refreshButton(context)
    }
}

/**
 * Accessibility service of the app. It hosts the app-owned shortcut while the app is open and marks our microphone
 * capture as an accessibility capture (allowed concurrently with a camera app, CDD 5.4.5), host the floating window
 * as a trusted accessibility overlay, and — only while a session runs over another app — report which app is on
 * screen and let bound hardware keys (volume, Bluetooth remote, keyboard) control the prompter.
 * It never reads window content.
 */
class PrompterAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        current = this
        App.instance.settings.a11yWasConnected = true
        DiagLog.i("Accessibility service connected")
        onSessionChanged(OverlayHost.active)
        OverlayHost.onHostChanged(this)
        SessionService.refreshNotification(this)
        stateListeners.forEach { it() }
    }

    /** Listen to window changes and filter keys only during a session; stay deaf otherwise. */
    fun onSessionChanged(active: Boolean) {
        val info = serviceInfo ?: return
        val keys = active && App.instance.settings.keyControl
        info.eventTypes = if (active) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
        info.flags = if (keys) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        runCatching { serviceInfo = info }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!OverlayHost.active || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        App.instance.engine.setForegroundApp(pkg)
    }

    /** Volume keys, Bluetooth selfie remotes, clickers, rings and keyboards control the prompter during a session. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val app = App.instance
        val s = app.settings
        if (!OverlayHost.active || app.engine.state.mode != SessionEngine.Mode.OVERLAY || !s.keyControl) return false
        if (KeyBindings.isVolume(event.keyCode) && !s.volumeKeys) return false
        val action = s.keyBindings[event.keyCode] ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) action.perform(app.engine)
        return true
    }

    override fun onInterrupt() = Unit

    private fun disconnect() {
        // An old service can be destroyed after a replacement has already connected.
        if (current !== this) return
        current = null
        DiagLog.i("Accessibility service disconnected")
        OverlayHost.onHostChanged(applicationContext)
        SessionService.refreshNotification(applicationContext)
        stateListeners.forEach { it() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var current: PrompterAccessibilityService? = null

        /** A surviving Java object is not proof that the system connection is still alive. */
        val instance: PrompterAccessibilityService?
            get() = current?.takeIf { runCatching { it.serviceInfo != null }.getOrDefault(false) }

        /** Called on the main thread when the service connects or disconnects (the app's readiness changes). */
        val stateListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

        fun isEnabledInSettings(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, PrompterAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
