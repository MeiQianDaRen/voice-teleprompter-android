package com.olerast.suflyor.overlay

import android.content.Context
import android.view.MotionEvent
import android.graphics.Canvas
import android.graphics.Paint
import android.view.ViewGroup

/**
 * Holds one child and turns it by 0°, +90° or −90°, measuring it with swapped sides so the rotated child exactly
 * covers the frame. Touches follow the rotation (ViewGroup maps them through the child's transform).
 *
 * Needed for landscape recording: camera apps like Samsung Camera and Instagram keep the screen in portrait even
 * when the phone is held sideways, so the prompter has to turn its own content to stay readable.
 */
class RotatedFrame(context: Context) : ViewGroup(context) {
    var resizeEnabled = true
        set(value) { field = value; invalidate() }
    var onResizeStart: ((Int) -> Unit)? = null
    var onResize: ((Int, Int) -> Unit)? = null
    var onResizeEnd: (() -> Unit)? = null
    private var resizeEdges = 0
    private var downX = 0f
    private var downY = 0f
    private val edgeSize = 12 * resources.displayMetrics.density
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBBFFB020.toInt()
        strokeWidth = 3 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            resizeEdges = if (!resizeEnabled) 0 else {
                (if (event.x < edgeSize) OverlayGeometry.LEFT else if (event.x > width - edgeSize) OverlayGeometry.RIGHT else 0) or
                    (if (event.y < edgeSize) OverlayGeometry.TOP else if (event.y > height - edgeSize) OverlayGeometry.BOTTOM else 0)
            }
            if (resizeEdges != 0) {
                downX = event.rawX
                downY = event.rawY
                onResizeStart?.invoke(resizeEdges)
            }
        }
        if (resizeEdges != 0) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) onResize?.invoke((event.rawX - downX).toInt(), (event.rawY - downY).toInt())
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { resizeEdges = 0; onResizeEnd?.invoke() }
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!resizeEnabled) return
        val half = 12 * resources.displayMetrics.density
        val inset = 3 * resources.displayMetrics.density
        canvas.drawLine(width / 2f - half, inset, width / 2f + half, inset, edgePaint)
        canvas.drawLine(width / 2f - half, height - inset, width / 2f + half, height - inset, edgePaint)
        canvas.drawLine(inset, height / 2f - half, inset, height / 2f + half, edgePaint)
        canvas.drawLine(width - inset, height / 2f - half, width - inset, height / 2f + half, edgePaint)
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
