package com.olerast.suflyor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.olerast.suflyor.App
import com.olerast.suflyor.R
import com.olerast.suflyor.diag.DiagLog
import com.olerast.suflyor.session.SessionEngine

/**
 * The floating prompter. In portrait it hangs from the top edge: text right under the front camera, controls below.
 * Held sideways, it moves next to the lens and turns its text (camera apps keep their screen in portrait).
 * "Lock" lets touches pass through to the camera app; a small button next to the window unlocks it.
 * Hosted by the accessibility service (TYPE_ACCESSIBILITY_OVERLAY) or by the app (TYPE_APPLICATION_OVERLAY).
 */
class OverlayController(private val context: Context, private val windowType: Int) {
    /** Where the lens is and how the content must be turned. */
    private enum class Placement {
        PORTRAIT,

        /** Screen stays portrait, phone turned counter-clockwise: lens on the left, content turned +90°. */
        TURNED_LEFT,

        /** Screen stays portrait, phone turned clockwise: lens on the right, content turned −90°. */
        TURNED_RIGHT,

        /** The screen itself rotated to landscape, lens on the left / right edge. */
        SIDE_LEFT,
        SIDE_RIGHT,
    }

    private val app = App.instance
    private val wm = context.getSystemService(WindowManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var frame: RotatedFrame? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var panel: LinearLayout
    private lateinit var topSpacer: View
    private lateinit var prompter: PrompterView
    private lateinit var countdown: TextView
    private lateinit var controls: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var level: LevelBar
    private lateinit var diag: TextView
    private lateinit var grabber: FrameLayout
    private lateinit var pauseBtn: ImageView
    private lateinit var modeBtn: ImageView

    /** The settings icon opens text size, lines, background and speed over the camera. */
    private lateinit var tuneRow: View
    private val tuneValues = ArrayList<() -> Unit>()

    /** "Lines" means nothing sideways, where the text fills the window's height. */
    private var linesGroup: View? = null
    private lateinit var background: GradientDrawable

    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var locked = false

    private var placement = Placement.PORTRAIT
    private var physical = 0
    private var pendingPhysical = 0
    private var pendingSince = 0L
    private var orientationListener: OrientationEventListener? = null
    private var fontSp = app.settings.fontSp
    private var windowBounds: OverlayGeometry.Rect? = null
    private var boundsPlacement: Placement? = null
    private var boundsScreenW = 0
    private var boundsScreenH = 0
    private var dragStart: OverlayGeometry.Rect? = null
    private var dragPlacement = Placement.PORTRAIT
    private var resizeEdges = 0
    private var resizeRequested = false
    private var boundsUpdatePosted = false
    private var safeTop = 0
    private var lastScript: Any? = null

    val isShowing: Boolean get() = frame != null

    private val listener: (SessionEngine.State) -> Unit = { render(it) }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) relayoutIfNeeded()
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (frame != null) return
        safeTop = portraitSafeTop()

        topSpacer = View(context)
        prompter = PrompterView(context).apply {
            setTextSizeSp(fontSp.toFloat())
            readLines = app.settings.overlayReadLines
            lineSpacingMultiplier = app.settings.overlayLineSpacing / 100f
            wordHighlight = app.settings.wordHighlight
            paragraphAlignment = app.settings.textAlignment
            onWordTap = { app.engine.jumpToToken(it) }
        }
        countdown = TextView(context).apply {
            setTextColor(PrompterView.ACCENT)
            textSize = 64f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val textBox = FrameLayout(context).apply {
            addView(prompter, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { topMargin = dp(28) })
            addView(countdown, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply { topMargin = dp(28) })
        }

        statusDot = View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL } }
        statusText = TextView(context).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 11f
            maxLines = 1
            // The status sits above the text in the upper-right corner.
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(6), 0, dp(4), 0)
        }
        level = LevelBar(context)
        pauseBtn = icon(R.drawable.ic_pause, R.string.common_cd_pause) { app.engine.togglePause() }
        modeBtn = icon(R.drawable.ic_mic, R.string.common_cd_scroll_mode) {
            app.engine.setScroll(app.engine.state.scroll.next())
        }
        val status = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)))
                addView(statusText)
            })
            addView(level, LinearLayout.LayoutParams(dp(56), dp(3)).apply { topMargin = dp(4) })
        }
        textBox.addView(status, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END,
        ).apply { topMargin = dp(3); marginEnd = dp(12) })
        controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(6), 0)
            addView(icon(R.drawable.ic_replay, R.string.common_cd_restart) { app.engine.restart() },
                LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(icon(R.drawable.ic_up, R.string.common_cd_line_back) { app.engine.stepLine(-1) })
            addView(pauseBtn)
            addView(icon(R.drawable.ic_down, R.string.common_cd_line_forward) { app.engine.stepLine(1) })
            addView(modeBtn)
            addView(icon(R.drawable.ic_settings, R.string.overlay_cd_tune) { setTuneOpen(tuneRow.visibility != View.VISIBLE) })
            addView(icon(R.drawable.ic_lock, R.string.overlay_cd_lock) { setLocked(true) })
            addView(icon(R.drawable.ic_close, R.string.floating_button_close) { app.exit() })
        }
        tuneRow = buildTuneRow()
        diag = TextView(context).apply {
            setTextColor(Color.argb(190, 170, 240, 190))
            textSize = 10f
            maxLines = 3
            setPadding(dp(16), 0, dp(12), dp(2))
            visibility = if (app.settings.showDiagnostics) View.VISIBLE else View.GONE
        }
        grabber = FrameLayout(context).apply {
            contentDescription = context.getString(R.string.overlay_move_hint)
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(Color.argb(110, 255, 255, 255))
                }
            }, FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER))
        }

        background = GradientDrawable().apply { setColor(Color.argb(app.settings.overlayAlpha * 255 / 100, 8, 8, 10)) }
        panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            this.background = this@OverlayController.background
            addView(topSpacer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
            addView(textBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, prompterHeight()))
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
            addView(tuneRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(diag, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(grabber, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))
        }
        val root = RotatedFrame(context).apply {
            keepScreenOn = true
            onResizeStart = { edges ->
                resizeEdges = edges
                dragStart = windowBounds
                dragPlacement = placement
            }
            onResize = { dx, dy ->
                val start = dragStart
                if (start != null && dragPlacement == placement) {
                    val (minW, minH) = minimumWindowSize()
                    updateBounds(OverlayGeometry.resize(start, resizeEdges, dx, dy, boundsScreenW, boundsScreenH, minW, minH))
                }
            }
            onResizeEnd = { saveBounds() }
            onResizeDone = {
                resizeRequested = false
                resizeEnabled = false
                saveBounds()
            }
            addView(panel, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = cutoutMode()
        }
        frame = root
        placement = computePlacement()
        applyPlacement()

        var downX = 0f
        var downY = 0f
        grabber.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    dragStart = windowBounds
                    dragPlacement = placement
                }
                MotionEvent.ACTION_MOVE -> {
                    val start = dragStart
                    if (start != null && dragPlacement == placement) {
                        updateBounds(start.copy(x = start.x + (e.rawX - downX).toInt(), y = start.y + (e.rawY - downY).toInt()))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> saveBounds()
            }
            true
        }

        wm.addView(root, params)
        prompter.setScript(app.scripts.model)
        lastScript = app.scripts.model
        app.engine.addListener(listener)
        displays.registerDisplayListener(displayListener, handler)
        if (app.settings.autoRotate) startOrientationTracking()
    }

    fun hide() {
        app.engine.removeListener(listener)
        orientationListener?.disable()
        orientationListener = null
        runCatching { displays.unregisterDisplayListener(displayListener) }
        hideBubble()
        frame?.let { it.keepScreenOn = false; runCatching { wm.removeView(it) } }
        frame = null
    }

    // ---- placement ----------------------------------------------------------------------------------------------

    private fun startOrientationTracking() {
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(deg: Int) {
                if (deg == ORIENTATION_UNKNOWN) return
                val snapped = when {
                    deg >= 330 || deg <= 30 -> 0
                    deg in 60..120 -> 90
                    deg in 240..300 -> 270
                    deg in 150..210 -> 180
                    else -> return
                }
                val now = SystemClock.uptimeMillis()
                if (snapped != pendingPhysical) {
                    pendingPhysical = snapped
                    pendingSince = now
                }
                if (snapped != physical && now - pendingSince >= 700) {
                    physical = snapped
                    relayoutIfNeeded()
                }
            }
        }.also { if (it.canDetectOrientation()) it.enable() }
    }

    private fun computePlacement(): Placement {
        val rotation = displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        return when {
            rotation == Surface.ROTATION_90 -> Placement.SIDE_LEFT
            rotation == Surface.ROTATION_270 -> Placement.SIDE_RIGHT
            physical == 270 -> Placement.TURNED_LEFT
            physical == 90 -> Placement.TURNED_RIGHT
            else -> Placement.PORTRAIT
        }
    }

    private fun relayoutIfNeeded() {
        val root = frame ?: return
        val next = computePlacement()
        placement = next
        DiagLog.i("Window: ${describe(next)}")
        applyPlacement()
        wm.updateViewLayout(root, params)
        if (locked) positionBubble()
    }

    private fun describe(p: Placement) = when (p) {
        Placement.PORTRAIT -> "portrait, text under the camera"
        Placement.TURNED_LEFT, Placement.TURNED_RIGHT -> "phone sideways, camera screen portrait — text turned toward the lens"
        Placement.SIDE_LEFT, Placement.SIDE_RIGHT -> "landscape screen — window next to the lens"
    }

    private fun minimumWindowSize(): Pair<Int, Int> {
        val chrome = (if (locked) 0 else dp(84)) +
            (if (tuneRow.visibility == View.VISIBLE) tuneRow.measuredHeight.coerceAtLeast(dp(180)) else 0) +
            (if (diag.visibility == View.VISIBLE) dp(44) else 0)
        return OverlayGeometry.minimum(frame?.angle ?: 0,
            dp(if (tuneRow.visibility == View.VISIBLE) 320 else 240), prompter.lineHeightPx + dp(36) + chrome + safeTop)
    }

    private fun updateBounds(next: OverlayGeometry.Rect) {
        val (minW, minH) = minimumWindowSize()
        val fitted = OverlayGeometry.fit(next, boundsScreenW, boundsScreenH, minW, minH)
        if (fitted == windowBounds) return
        windowBounds = fitted
        val root = frame ?: return
        if (boundsUpdatePosted) return
        boundsUpdatePosted = true
        root.postOnAnimation {
            boundsUpdatePosted = false
            if (frame === root) {
                applyPlacement()
                wm.updateViewLayout(root, params)
            }
        }
    }

    private fun saveBounds() {
        windowBounds?.let { app.settings.saveOverlayBounds(placement.name, OverlayGeometry.save(it, boundsScreenW, boundsScreenH)) }
    }

    /** Physical screen coordinates avoid swapped or reversed deltas when the panel is turned. */
    private fun applyPlacement() {
        val root = frame ?: return
        val bounds = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds else null
        val screenW = (bounds?.width() ?: context.resources.displayMetrics.widthPixels).coerceAtLeast(1)
        val screenH = (bounds?.height() ?: context.resources.displayMetrics.heightPixels).coerceAtLeast(1)
        root.angle = when (placement) {
            Placement.TURNED_LEFT -> 90
            Placement.TURNED_RIGHT -> -90
            else -> 0
        }
        root.resizeEnabled = resizeRequested && !locked
        panel.layoutParams = panel.layoutParams.apply {
            width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        }
        (prompter.parent as View).layoutParams = ((prompter.parent as View).layoutParams as LinearLayout.LayoutParams).apply {
            height = 0
            weight = 1f
        }
        grabber.visibility = if (locked) View.GONE else View.VISIBLE
        linesGroup?.visibility = View.VISIBLE
        if (boundsPlacement != placement || boundsScreenW != screenW || boundsScreenH != screenH) {
            windowBounds = OverlayGeometry.restore(app.settings.overlayBounds(placement.name), screenW, screenH) ?: when (placement) {
                Placement.PORTRAIT -> OverlayGeometry.Rect(0, app.settings.overlayY.coerceAtLeast(0), screenW, prompterHeight() + dp(84) + safeTop)
                Placement.TURNED_LEFT, Placement.TURNED_RIGHT -> OverlayGeometry.Rect(0, 0, screenW, (screenH * 0.46f).toInt())
                Placement.SIDE_LEFT -> OverlayGeometry.Rect(0, 0, (screenW * 0.46f).toInt(), screenH)
                Placement.SIDE_RIGHT -> OverlayGeometry.Rect(screenW - (screenW * 0.46f).toInt(), 0, (screenW * 0.46f).toInt(), screenH)
            }
            boundsPlacement = placement
            boundsScreenW = screenW
            boundsScreenH = screenH
        }
        val (minW, minH) = minimumWindowSize()
        val rect = OverlayGeometry.fit(windowBounds!!, screenW, screenH, minW, minH)
        windowBounds = rect
        params.width = rect.width
        params.height = rect.height
        params.x = rect.x
        params.y = rect.y
        params.gravity = Gravity.TOP or Gravity.LEFT
        background.cornerRadius = dp(18).toFloat()
        panel.setPadding(0, 0, 0, 0)
        prompter.setPadding(dp(18), dp(4), dp(14), dp(2))
        topSpacer.layoutParams = topSpacer.layoutParams.apply {
            height = if (placement == Placement.PORTRAIT) (safeTop - rect.y).coerceAtLeast(0) else 0
        }
        val cut = (safeTop - rect.y).coerceAtLeast(0)
        when (placement) {
            Placement.TURNED_LEFT -> prompter.setPadding(dp(18) + cut, dp(4), dp(14), dp(2))
            Placement.TURNED_RIGHT -> prompter.setPadding(dp(18), dp(4), dp(14) + cut, dp(2))
            Placement.SIDE_LEFT -> if (rect.x == 0) panel.setPadding(sideCutout(), 0, 0, 0)
            Placement.SIDE_RIGHT -> if (rect.x + rect.width == screenW) panel.setPadding(0, 0, sideCutout(), 0)
            else -> Unit
        }
        panel.requestLayout()
    }

    private fun portraitSafeTop(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            val insets = wm.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.displayCutout() or WindowInsets.Type.statusBars())
            val v = maxOf(insets.top, insets.left, insets.right)
            if (v > 0) return v
        }
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(28)
    }

    private fun sideCutout(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            val insets = wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.displayCutout())
            return maxOf(insets.left, insets.right)
        }
        return 0
    }

    // ---- lock ---------------------------------------------------------------------------------------------------

    private fun setLocked(value: Boolean) {
        val root = frame ?: return
        locked = value
        controls.visibility = if (value) View.GONE else View.VISIBLE
        if (value) tuneRow.visibility = View.GONE
        grabber.visibility = if (value) View.GONE else View.VISIBLE
        if (value) resizeRequested = false
        root.resizeEnabled = resizeRequested && !value
        applyPlacement()
        params.flags = if (value) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        // Plain overlays let touches through only when at most 80% opaque (Android 12+); accessibility ones always.
        if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) params.alpha = if (value) 0.8f else 1f
        wm.updateViewLayout(root, params)
        if (value) showBubble() else hideBubble()
        DiagLog.i(if (value) "Window locked: touches pass to the camera app" else "Window accepts touches again")
    }

    private fun showBubble() {
        if (bubble != null) return
        val size = dp(44)
        val view = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 8, 8, 10))
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_lock_open)
                imageTintList = ColorStateList.valueOf(PrompterView.ACCENT)
                contentDescription = context.getString(R.string.overlay_cd_unlock)
            }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { setLocked(false) }
        }
        val p = WindowManager.LayoutParams(
            size, size, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            layoutInDisplayCutoutMode = cutoutMode()
        }
        bubble = view
        bubbleParams = p
        runCatching { wm.addView(view, p) }
        positionBubble()
    }

    private fun positionBubble() {
        val root = frame ?: return
        root.post {
            val view = bubble ?: return@post
            val p = bubbleParams ?: return@post
            val loc = IntArray(2)
            root.getLocationOnScreen(loc)
            val size = p.width
            when (placement) {
                Placement.SIDE_LEFT -> {
                    p.x = loc[0] + root.width + dp(6)
                    p.y = loc[1] + root.height - size - dp(24)
                }
                Placement.SIDE_RIGHT -> {
                    p.x = loc[0] - size - dp(6)
                    p.y = loc[1] + root.height - size - dp(24)
                }
                else -> {
                    p.x = loc[0] + root.width - size - dp(12)
                    p.y = loc[1] + root.height + dp(6)
                }
            }
            p.x = p.x.coerceIn(0, (boundsScreenW - size).coerceAtLeast(0))
            p.y = p.y.coerceIn(0, (boundsScreenH - size).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(view, p) }
        }
    }

    private fun hideBubble() {
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        bubbleParams = null
    }

    // ---- state --------------------------------------------------------------------------------------------------

    private fun render(s: SessionEngine.State) {
        if (frame == null) return
        prompter.paragraphAlignment = app.settings.textAlignment
        prompter.readLines = app.settings.overlayReadLines
        prompter.lineSpacingMultiplier = app.settings.overlayLineSpacing / 100f
        if (lastScript !== app.scripts.model) {
            lastScript = app.scripts.model
            prompter.setScript(app.scripts.model)
        }
        prompter.setProgress(s.nextToken, highlightTokenIndex = s.highlightToken)
        if (s.countdown > 0) {
            countdown.text = s.countdown.toString()
            countdown.visibility = View.VISIBLE
        } else {
            countdown.visibility = View.GONE
        }
        val muted = s.silencedBySystem == true || s.digitalSilence
        level.setLevel(s.levelDb, muted)
        pauseBtn.setImageResource(if (s.paused) R.drawable.ic_play else R.drawable.ic_pause)
        pauseBtn.contentDescription = context.getString(if (s.paused) R.string.common_cd_resume else R.string.common_cd_pause)
        modeBtn.setImageResource(s.scroll.icon)
        val red = Color.rgb(255, 80, 80)
        val (color, label) = when {
            s.starting || s.loadingModel -> Color.GRAY to R.string.overlay_status_starting
            !s.listening -> Color.GRAY to R.string.overlay_status_stopped
            // A failed model or microphone must not look like "listening" while the text stands still.
            s.error != null -> red to R.string.overlay_status_error
            s.paused -> PrompterView.ACCENT to R.string.overlay_status_paused
            s.scroll == SessionEngine.Scroll.AUTO -> PrompterView.ACCENT to R.string.overlay_status_auto
            // Sound mode depends on hearing too: a silenced microphone must show before "by sound".
            s.silencedBySystem == true -> red to R.string.overlay_status_mic_busy
            s.autoFallback -> red to R.string.overlay_status_cant_hear
            s.scroll == SessionEngine.Scroll.SOUND -> PrompterView.ACCENT to R.string.overlay_status_sound
            else -> Color.rgb(70, 215, 120) to R.string.overlay_status_listening
        }
        (statusDot.background as GradientDrawable).setColor(color)
        statusText.text = context.getString(label)
        // The error text shows even with diagnostics off: the status row has room for one word only.
        val showDiag = app.settings.showDiagnostics || s.error != null
        if (showDiag != (diag.visibility == View.VISIBLE)) {
            diag.visibility = if (showDiag) View.VISIBLE else View.GONE
            applyPlacement()
            frame?.let { wm.updateViewLayout(it, params) }
            // The window's height changes: the unlock button sits right under it.
            if (locked) positionBubble()
        }
        if (!app.settings.showDiagnostics && s.error != null) {
            diag.text = s.error.message(context)
        } else if (showDiag) {
            val others = s.recordings.filter { !it.ours }
            diag.text = buildString {
                append(s.partial.ifEmpty { s.lastFinal }.takeLast(70))
                if (others.isNotEmpty()) append("\n").append(others.joinToString("; ") { it.describe() })
                s.error?.let { append("\n").append(it.message(context)) }
            }
        }
    }

    private fun buildTuneRow(): View {
        val s = app.settings
        // Two controls per row keep every setting visible on a phone.
        fun row(vararg groups: View) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            groups.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), dp(4))
            visibility = View.GONE
            addView(TextView(context).apply {
                text = context.getString(R.string.overlay_resize_title)
                textSize = 15f
                setTextColor(PrompterView.ACCENT)
                gravity = Gravity.CENTER
                background = ripple()
                setOnClickListener {
                    setTuneOpen(false)
                    resizeRequested = true
                    frame?.resizeEnabled = true
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
            addView(
                row(
                    tuneGroup(R.string.overlay_tune_font, { "$fontSp" }, { changeFont(-2) }, { changeFont(2) }),
                    tuneGroup(R.string.overlay_tune_lines, { "${s.overlayLines}" }, { changeLines(-1) }, { changeLines(1) })
                        .also { linesGroup = it },
                ),
            )
            addView(
                row(
                    tuneGroup(R.string.overlay_tune_read_lines, { "${s.overlayReadLines}" }, { changeReadLines(-1) }, { changeReadLines(1) }),
                    tuneGroup(R.string.overlay_tune_line_spacing, { "${s.overlayLineSpacing}%" }, { changeLineSpacing(-4) }, { changeLineSpacing(4) }),
                ),
            )
            addView(
                row(
                    tuneGroup(R.string.overlay_tune_background, { "${s.overlayAlpha}%" }, { changeAlpha(-8) }, { changeAlpha(8) }),
                    tuneGroup(R.string.overlay_tune_speed, { "${s.autoScrollWpm}" }, { changeSpeed(-10) }, { changeSpeed(10) }),
                ),
            )
        }
    }

    /** Caption, −, the current value, +. */
    private fun tuneGroup(@StringRes caption: Int, value: () -> String, onMinus: () -> Unit, onPlus: () -> Unit): View {
        val name = context.getString(caption)
        val valueView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(34)
        }
        tuneValues += { valueView.text = value() }
        valueView.text = value()
        fun step(label: String, cd: String, action: () -> Unit) = TextView(context).apply {
            text = label
            contentDescription = cd
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = ripple()
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(36))
            setOnClickListener {
                action()
                tuneValues.forEach { it() }
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = name
                setTextColor(Color.argb(170, 255, 255, 255))
                textSize = 11f
                maxLines = 1
                setPadding(dp(4), 0, dp(2), 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(step("−", context.getString(R.string.stepper_cd_decrease, name), onMinus))
            addView(valueView)
            addView(step("+", context.getString(R.string.stepper_cd_increase, name), onPlus))
        }
    }

    private fun setTuneOpen(open: Boolean) {
        resizeRequested = false
        frame?.resizeEnabled = false
        tuneRow.visibility = if (open) View.VISIBLE else View.GONE
        tuneValues.forEach { it() }
        applyPlacement()
        frame?.let { wm.updateViewLayout(it, params) }
        if (locked) positionBubble()
    }

    private fun changeLines(delta: Int) {
        val old = app.settings.overlayLines
        app.settings.overlayLines = (old + delta).coerceIn(1, 8)
        prompter.readLines = app.settings.overlayReadLines
        val change = (app.settings.overlayLines - old) * prompter.lineHeightPx
        windowBounds?.let { rect ->
            windowBounds = if (frame?.angle == 0) rect.copy(height = rect.height + change) else rect.copy(width = rect.width + change)
        }
        applyPlacement()
        saveBounds()
        frame?.let { wm.updateViewLayout(it, params) }
    }

    /** Apply reading preferences without recreating the window or resetting recognition. */
    fun refreshTextSettings() {
        if (frame == null) return
        prompter.readLines = app.settings.overlayReadLines
        prompter.lineSpacingMultiplier = app.settings.overlayLineSpacing / 100f
        tuneValues.forEach { it() }
        applyPlacement()
        frame?.let { wm.updateViewLayout(it, params) }
        if (locked) positionBubble()
    }

    private fun changeReadLines(delta: Int) {
        app.settings.overlayReadLines += delta
        refreshTextSettings()
    }

    private fun changeLineSpacing(delta: Int) {
        app.settings.overlayLineSpacing += delta
        refreshTextSettings()
    }

    private fun changeAlpha(delta: Int) {
        app.settings.overlayAlpha = (app.settings.overlayAlpha + delta).coerceIn(24, 96)
        background.setColor(Color.argb(app.settings.overlayAlpha * 255 / 100, 8, 8, 10))
    }

    private fun changeSpeed(delta: Int) {
        app.settings.autoScrollWpm = (app.settings.autoScrollWpm + delta).coerceIn(60, 260)
    }

    private fun changeFont(delta: Int) {
        fontSp = (fontSp + delta).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        app.settings.fontSp = fontSp
        prompter.setTextSizeSp(fontSp.toFloat())
        applyPlacement()
        frame?.let { wm.updateViewLayout(it, params) }
    }

    /** ALWAYS exists from Android 11; on 10 SHORT_EDGES also lets the window reach under the camera cut-out at the top. */
    private fun cutoutMode(): Int = if (Build.VERSION.SDK_INT >= 30) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private companion object {
        /** The window sits over the camera preview: bigger text would cover the face. The full screen goes to 80. */
        const val MIN_FONT_SP = 14
        const val MAX_FONT_SP = 48
    }

    private fun prompterHeight(): Int {
        return prompter.lineHeightPx * app.settings.overlayLines + dp(36)
    }

    private fun ripple() = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).let {
        val d = it.getDrawable(0)
        it.recycle()
        d
    }

    private fun icon(res: Int, @StringRes description: Int, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = context.getString(description)
        scaleType = ImageView.ScaleType.CENTER
        background = ripple()
        layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
        setOnClickListener { onClick() }
    }

}

/** Thin level meter; red when Android is feeding us silence. */
class LevelBar(context: Context) : View(context) {
    private val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val fg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var fraction = 0f
    private var muted = false

    fun setLevel(db: Float, muted: Boolean) {
        fraction = ((db + 60f) / 60f).coerceIn(0f, 1f)
        this.muted = muted
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, bg)
        fg.color = if (muted) Color.rgb(255, 80, 80) else Color.rgb(70, 215, 120)
        val w = if (muted) width.toFloat() else width * fraction
        if (w > 0f) canvas.drawRoundRect(0f, 0f, w, height.toFloat(), r, r, fg)
    }
}
