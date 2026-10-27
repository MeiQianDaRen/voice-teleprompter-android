package com.olerast.suflyor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import com.olerast.suflyor.App
import com.olerast.suflyor.R
import kotlin.math.abs

/** Session shortcut: tap to show/hide the prompter, long press to exit, drag to move. */
class FloatingButton(private val context: Context, private val windowType: Int) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: ImageView? = null
    private lateinit var params: WindowManager.LayoutParams
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        val size = dp(48)
        params = WindowManager.LayoutParams(size, size, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            val metrics = context.resources.displayMetrics
            x = (metrics.widthPixels - size - dp(12)).coerceAtLeast(0)
            y = metrics.heightPixels / 2
        }
        val button = object : ImageView(context) {
            override fun onConfigurationChanged(newConfig: Configuration) {
                super.onConfigurationChanged(newConfig)
                post { if (view === this) { fitOnScreen(); wm.updateViewLayout(this, params) } }
            }
        }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 8, 8, 10))
            }
            imageTintList = ColorStateList.valueOf(Color.rgb(255, 176, 32))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(4).toFloat()
            setOnClickListener {
                OverlayHost.toggleWindow(context)
            }
            setOnLongClickListener { App.instance.exit(); true }
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        fun cancelPress(v: ImageView, event: MotionEvent) {
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            v.onTouchEvent(cancel)
            cancel.recycle()
        }
        button.setOnTouchListener { v, event ->
            if (view !== button) return@setOnTouchListener true
            if (event.pointerCount > 1) { cancelPress(button, event); dragging = true; return@setOnTouchListener true }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    v.onTouchEvent(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        cancelPress(button, event)
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        fitOnScreen()
                        wm.updateViewLayout(button, params)
                    } else v.onTouchEvent(event)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) v.onTouchEvent(event)
            }
            true
        }
        fitOnScreen()
        wm.addView(button, params)
        view = button
        render(OverlayHost.windowVisible)
    }

    private fun fitOnScreen() {
        val bounds = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds else null
        val metrics = context.resources.displayMetrics
        params.x = params.x.coerceIn(0, ((bounds?.width() ?: metrics.widthPixels) - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, ((bounds?.height() ?: metrics.heightPixels) - params.height).coerceAtLeast(0))
    }

    fun render(visible: Boolean) {
        view?.apply {
            setImageResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
            contentDescription = context.getString(if (visible) R.string.floating_button_hide else R.string.floating_button_open) +
                ". " + context.getString(R.string.floating_button_long_press)
        }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
