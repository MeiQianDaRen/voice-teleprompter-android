package com.olerast.suflyor.overlay

import android.content.Context
import android.view.MotionEvent
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.ViewGroup
import com.olerast.suflyor.R

/**
 * Holds one child and turns it by 0°, +90° or −90°, measuring it with swapped sides so the rotated child exactly
 * covers the frame. Touches follow the rotation (ViewGroup maps them through the child's transform).
 *
 * Needed for landscape recording: camera apps like Samsung Camera and Instagram keep the screen in portrait even
 * when the phone is held sideways, so the prompter has to turn its own content to stay readable.
 */
class RotatedFrame(context: Context) : ViewGroup(context) {
    var resizeEnabled = false
        set(value) { field = value; invalidate() }
    var onResizeStart: ((Int) -> Unit)? = null
    var onResize: ((Int, Int) -> Unit)? = null
    var onResizeEnd: (() -> Unit)? = null
    var onResizeDone: (() -> Unit)? = null
    private var resizeEdges = 0
    private var downX = 0f
    private var downY = 0f
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val handleBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEE202024.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFB020.toInt()
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBBFFB020.toInt()
        strokeWidth = 3 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            resizeEdges = if (!resizeEnabled) 0 else OverlayGeometry.handleAt(event.x, event.y, width, height, dp(48), dp(48))
            if (resizeEdges != 0) {
                downX = event.rawX
                downY = event.rawY
                if (resizeEdges != OverlayGeometry.DONE) onResizeStart?.invoke(resizeEdges)
            }
        }
        if (resizeEdges != 0) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && resizeEdges != OverlayGeometry.DONE) onResize?.invoke((event.rawX - downX).toInt(), (event.rawY - downY).toInt())
                MotionEvent.ACTION_UP -> {
                    if (resizeEdges == OverlayGeometry.DONE) {
                        if (OverlayGeometry.handleAt(event.x, event.y, width, height, dp(48), dp(48)) == OverlayGeometry.DONE) onResizeDone?.invoke()
                    } else onResizeEnd?.invoke()
                    resizeEdges = 0
                }
                MotionEvent.ACTION_CANCEL -> { resizeEdges = 0; onResizeEnd?.invoke() }
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!resizeEnabled) return
        val inset = dp(48).toFloat()
        fun handle(cx: Float, cy: Float, horizontal: Boolean) {
            canvas.drawCircle(cx, cy, dp(24).toFloat(), handleBackground)
            val length = dp(10).toFloat()
            val head = dp(5).toFloat()
            if (horizontal) {
                canvas.drawLine(cx - length, cy, cx + length, cy, edgePaint)
                for (sign in listOf(-1, 1)) {
                    canvas.drawLine(cx + sign * length, cy, cx + sign * (length - head), cy - head, edgePaint)
                    canvas.drawLine(cx + sign * length, cy, cx + sign * (length - head), cy + head, edgePaint)
                }
            } else {
                canvas.drawLine(cx, cy - length, cx, cy + length, edgePaint)
                for (sign in listOf(-1, 1)) {
                    canvas.drawLine(cx, cy + sign * length, cx - head, cy + sign * (length - head), edgePaint)
                    canvas.drawLine(cx, cy + sign * length, cx + head, cy + sign * (length - head), edgePaint)
                }
            }
        }
        handle(inset, height / 2f, true)
        handle(width - inset, height / 2f, true)
        handle(width / 2f, inset, false)
        handle(width / 2f, height - inset, false)
        canvas.drawCircle(width / 2f, height / 2f, dp(24).toFloat(), handleBackground)
        val baseline = height / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2
        canvas.drawText(context.getString(R.string.overlay_resize_done), width / 2f, baseline, labelPaint)
    }

    /** 0, 90 (content turned clockwise) or −90. */
    var angle: Int = 0
        set(v) {
            if (field == v) return
            field = v
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        if (child == null) {
            setMeasuredDimension(0, 0)
            return
        }
        if (angle == 0) {
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(
                resolveSize(child.measuredWidth, widthMeasureSpec),
                resolveSize(child.measuredHeight, heightMeasureSpec),
            )
        } else {
            measureChild(child, heightMeasureSpec, widthMeasureSpec)
            setMeasuredDimension(
                resolveSize(child.measuredHeight, widthMeasureSpec),
                resolveSize(child.measuredWidth, heightMeasureSpec),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val child = getChildAt(0) ?: return
        val w = r - l
        val h = b - t
        child.pivotX = 0f
        child.pivotY = 0f
        when (angle) {
            90 -> {
                child.layout(0, 0, h, w)
                child.rotation = 90f
                child.translationX = w.toFloat()
                child.translationY = 0f
            }
            -90 -> {
                child.layout(0, 0, h, w)
                child.rotation = -90f
                child.translationX = 0f
                child.translationY = h.toFloat()
            }
            else -> {
                child.layout(0, 0, w, h)
                child.rotation = 0f
                child.translationX = 0f
                child.translationY = 0f
            }
        }
    }
}
