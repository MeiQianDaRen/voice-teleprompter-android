package com.olerast.suflyor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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

/** A draggable shortcut owned by the open app, with no microphone or keep-screen-on flag of its own. */
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
                if (OverlayHost.active) App.instance.exit() else {
                    context.startActivity(Intent(context, QuickStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        button.setOnTouchListener { v, event ->
            if (event.pointerCount > 1) { dragging = true; return@setOnTouchListener true }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        fitOnScreen()
                        wm.updateViewLayout(button, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) v.performClick()
            }
            true
        }
        fitOnScreen()
        wm.addView(button, params)
        view = button
        render(OverlayHost.active)
    }

    private fun fitOnScreen() {
        val bounds = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds else null
        val metrics = context.resources.displayMetrics
        params.x = params.x.coerceIn(0, ((bounds?.width() ?: metrics.widthPixels) - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, ((bounds?.height() ?: metrics.heightPixels) - params.height).coerceAtLeast(0))
    }

    fun render(active: Boolean) {
        view?.apply {
            setImageResource(if (active) R.drawable.ic_close else R.drawable.ic_layers)
            contentDescription = context.getString(if (active) R.string.floating_button_close else R.string.floating_button_open)
        }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
