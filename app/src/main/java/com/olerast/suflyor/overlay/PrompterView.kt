package com.olerast.suflyor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.olerast.suflyor.script.ScriptModel
import com.olerast.suflyor.R
import kotlin.math.floor

/** Karaoke text-only colour wipes, with confirmed reading separated from cross-line prediction. */
class PrompterView(context: Context) : View(context) {
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private var model: ScriptModel = ScriptModel.EMPTY
    private var text = SpannableString("")
    private var layout: StaticLayout? = null

    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val noticePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HIGHLIGHT; textSize = dp(13f) }
    private val colourPath = Path()
    private var readProgress = KaraokeProgress()
    private val readingMotion = ReadingMotion()
    private var spoken = IntArray(0)
    private var rowFirst = IntArray(0)
    private var rowEnd = IntArray(0)
    private var motionSample = ReadingMotion.Sample()
    private var noticeUntil = 0L
    private val noteColor = Color.argb(170, 140, 200, 255)

    /** Lines of already-read text kept visible above the reading line. */
    var linesAbove: Float = 0f
        set(v) {
            if (field == v) return
            field = v
            applyProgress(false)
        }

    /** Complete read lines for overlays; null keeps the full-screen fractional reading position. */
    var readLines: Int? = null
        set(value) {
            val next = value?.coerceAtLeast(0)
            if (field == next) return
            field = next
            applyProgress(false)
        }

    var lineSpacingMultiplier: Float = LINE_SPACING
        set(value) {
            val next = value.coerceIn(1f, 2f)
            if (field == next) return
            field = next
            rebuildLayout()
        }

    var paragraphAlignment: Int = 1
        set(value) {
            val next = value.coerceIn(0, 2)
            if (field == next) return
            field = next
            rebuildLayout()
        }

    var onWordTap: ((tokenIndex: Int) -> Unit)? = null

    /** For teleprompter glass: the reflection reads correctly when the screen shows the text mirrored. */
    var mirrorX = false
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }
    var mirrorY = false
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    private var nextToken = 0
    private var currentLine = 0
    private var posY = 0f
    private var targetY = 0f
    private var scrollFrom = 0f
    private var scrollStarted = 0L
    private var scrollDuration = 0L
    private var moveDelta = 0
    private var wasManual = false
    private var manualUntil = 0L

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    fun setTextSizeSp(sp: Float) {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        if (px == paint.textSize && layout != null) return
        paint.textSize = px
        rebuildLayout()
    }

    val lineHeightPx: Int get() = layout?.takeIf { it.lineCount > 1 }?.let { it.getLineTop(1) - it.getLineTop(0) }
        ?: (paint.fontMetricsInt.let { it.descent - it.ascent } * lineSpacingMultiplier).toInt().coerceAtLeast(1)

    fun setScript(m: ScriptModel) {
        model = m
        text = SpannableString(m.displayText)
        for (r in m.emphasis) {
            if (r.first < 0 || r.last >= text.length) continue
            text.setSpan(StyleSpan(Typeface.BOLD), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (r in m.notes) {
            if (r.first < 0 || r.last >= text.length) continue
            text.setSpan(ForegroundColorSpan(noteColor), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(StyleSpan(Typeface.ITALIC), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        nextToken = 0
        spoken = m.tokens.indices.filter { m.tokens[it].spoken }.toIntArray()
        readProgress = KaraokeProgress()
        motionSample = ReadingMotion.Sample()
        readingMotion.reset()
        manualUntil = 0L
        wasManual = false
        noticeUntil = 0L
        rebuildLayout()
        posY = targetY
    }

    fun setProgress(nextTokenIndex: Int, animate: Boolean = true, highlightTokenIndex: Int = nextTokenIndex,
        motion: ReadingMotion.Sample? = null) {
        val now = SystemClock.uptimeMillis()
        moveDelta = nextTokenIndex - nextToken
        if (moveDelta < 0 && animate) noticeUntil = now + 1100L
        val changed = nextToken != nextTokenIndex
        nextToken = nextTokenIndex.coerceIn(0, model.tokens.size)
        val ordinal = spoken.binarySearch(nextToken).let { if (it < 0) -it - 1 else it }
        val source = motion ?: ReadingMotion.Sample(ordinal.toFloat())
        motionSample = source
        readProgress.update(ordinal.toFloat(), source.revision, now, animate)
        readingMotion.follow(motionSample, now, animate)
        if (changed || !animate) applyProgress(animate)
        invalidate()
    }

    private fun applyProgress(animate: Boolean) {
        val l = layout ?: return
        currentLine = if (nextToken >= model.tokens.size) l.lineCount - 1 else l.getLineForOffset(model.tokens[nextToken].start)
        if (!animate) {
            targetY = scrollTarget(l, readingMotion.frame(SystemClock.uptimeMillis()))
            posY = targetY
            scrollDuration = 0L
        }
        invalidate()
    }

    private fun fixedReadingRow() = readLines == 0 || height - paddingTop - paddingBottom < lineHeightPx * 1.5f

    private fun scrollTarget(l: StaticLayout, position: Float): Float {
        if (spoken.isEmpty()) return 0f
        if (fixedReadingRow()) return l.getLineTop(currentLine).toFloat()
        val at = floor(position).toInt().coerceIn(0, spoken.size - 1)
        val row = l.getLineForOffset(model.tokens[spoken[at]].start)
        val nextRow = spoken.getOrNull(rowEnd[at])?.let { l.getLineForOffset(model.tokens[it].start) } ?: row
        val y = ScrollMotion.rowPosition(position, rowFirst[at], rowEnd[at], l.getLineTop(row).toFloat(), l.getLineTop(nextRow).toFloat())
        val above = readLines?.let {
            PrompterViewport.readOffset(l.lineCount - 1, it, (height - paddingTop - paddingBottom).coerceAtLeast(0),
                l::getLineTop, l::getLineBottom).toFloat()
        } ?: (linesAbove * lineHeightPx)
        return (y - above).coerceAtLeast(0f)
    }

    private data class TextPoint(val row: Int, val x: Float)

    private fun textPoint(l: StaticLayout, offset: Float): TextPoint {
        val at = offset.coerceIn(0f, text.length.toFloat())
        var start = floor(at).toInt()
        if (start < text.length && Character.isLowSurrogate(text[start])) start--
        val end = if (start < text.length) (start + Character.charCount(Character.codePointAt(text, start))).coerceAtMost(text.length) else start
        val row = l.getLineForOffset(start)
        val a = l.getPrimaryHorizontal(start)
        val b = if (l.getLineForOffset(end) != row) l.getLineRight(row) else l.getPrimaryHorizontal(end)
        val fraction = if (end == start) 0f else (at - start) / (end - start)
        return TextPoint(row, a + (b - a) * fraction)
    }

    /** Repaint glyphs inside a fractional selection; no coloured rectangles behind the text. */
    private fun drawColour(canvas: Canvas, l: StaticLayout, start: Float, end: Float, colour: Int) {
        if (start >= end || text.isEmpty()) return
        val a = textPoint(l, start)
        val b = textPoint(l, end)
        val first = maxOf(a.row, l.getLineForVertical((posY - paddingTop).toInt().coerceAtLeast(0)))
        val last = minOf(b.row, l.getLineForVertical((posY + height).toInt()))
        colourPath.reset()
        for (row in first..last) {
            val left = if (row == a.row) a.x else l.getLineLeft(row)
            val right = if (row == b.row) b.x else l.getLineRight(row)
            colourPath.addRect(minOf(left, right), l.getLineTop(row).toFloat(), maxOf(left, right), l.getLineBottom(row).toFloat(), Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(colourPath)
        paint.color = colour
        l.draw(canvas)
        paint.color = Color.WHITE
        canvas.restore()
    }

    private fun drawEdgeFade(canvas: Canvas, l: StaticLayout) {
        val h = height.toFloat()
        if (h < lineHeightPx * 1.5f) return // A one-line window must keep its reading line fully visible.
        val top = posY - paddingTop
        val fm = paint.fontMetrics
        val baseline = l.getLineBaseline(currentLine)
        val fade = minOf(lineHeightPx * 0.8f, h * 0.18f)
        val clearTop = minOf(fade, (baseline + fm.ascent - top - dp(7f)).coerceAtLeast(0f)) / h
        val clearBottom = maxOf(1f - fade / h, (baseline + fm.descent - top + dp(7f)) / h).coerceAtMost(1f)
        fadePaint.shader = LinearGradient(0f, top, 0f, top + h,
            intArrayOf(0x40FFFFFF, Color.WHITE, Color.WHITE, 0x40FFFFFF),
            floatArrayOf(0f, clearTop, clearBottom, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(-paddingLeft.toFloat(), top, width.toFloat(), top + h, fadePaint)
    }

    private fun rebuildLayout() {
        val w = width - paddingLeft - paddingRight
        if (w <= 0) {
            layout = null
            return
        }
        layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(when (paragraphAlignment) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL })
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
        val rows = IntArray(spoken.size) { layout!!.getLineForOffset(model.tokens[spoken[it]].start) }
        rowFirst = IntArray(spoken.size)
        rowEnd = IntArray(spoken.size)
        var i = 0
        while (i < rows.size) {
            var end = i + 1
            while (end < rows.size && rows[end] == rows[i]) end++
            for (j in i until end) { rowFirst[j] = i; rowEnd[j] = end }
            i = end
        }
        applyProgress(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        val now = SystemClock.uptimeMillis()
        val position = readingMotion.frame(now)
        val at = floor(position).toInt().coerceIn(0, spoken.size.coerceAtLeast(1) - 1)
        val token = spoken.getOrNull(at)
        val previousLine = currentLine
        currentLine = token?.let { l.getLineForOffset(model.tokens[it].start) } ?: 0
        val nextY = scrollTarget(l, position)
        if (fixedReadingRow() && nextY != targetY && now >= manualUntil && !wasManual) {
            scrollFrom = posY
            scrollStarted = now
            scrollDuration = ScrollMotion.duration(if (currentLine < previousLine) -3 else 3)
        }
        targetY = nextY
        if (now >= manualUntil) {
            if (wasManual) {
                scrollFrom = posY
                scrollStarted = now
                scrollDuration = 350L
                wasManual = false
            }
            posY = if (now - scrollStarted < scrollDuration)
                ScrollMotion.position(scrollFrom, targetY, now - scrollStarted, scrollDuration) else targetY
        }
        val read = readProgress.frame(now).coerceIn(0f, spoken.size.toFloat())
        val cue = maxOf(read + 1f, position + 1f).coerceAtMost(spoken.size.toFloat())
        val readOffset = KaraokeProgress.offset(model.tokens, spoken, read)
        val cueOffset = KaraokeProgress.offset(model.tokens, spoken, cue)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.save()
        if (mirrorX || mirrorY) canvas.scale(if (mirrorX) -1f else 1f, if (mirrorY) -1f else 1f, width / 2f, height / 2f)
        canvas.clipRect(0, 0, width, height)
        canvas.translate(paddingLeft.toFloat(), paddingTop - posY)
        paint.color = Color.WHITE
        l.draw(canvas)
        drawColour(canvas, l, 0f, readOffset, READ_COLOUR)
        drawColour(canvas, l, readOffset, cueOffset, HIGHLIGHT)
        drawEdgeFade(canvas, l)
        canvas.restore()
        canvas.restoreToCount(layer)
        if (now < noticeUntil) {
            val message = context.getString(R.string.reading_retreated)
            noticePaint.alpha = (255f * minOf(1f, (noticeUntil - now) / 180f)).toInt()
            canvas.drawText(message, width - paddingRight - noticePaint.measureText(message), paddingTop + dp(16f), noticePaint)
        }
        if (readingMotion.running(now) || readProgress.running(now) || now < manualUntil ||
            now - scrollStarted < scrollDuration || now < noticeUntil) postInvalidateOnAnimation()
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val l = layout ?: return false
            wasManual = true
            manualUntil = SystemClock.uptimeMillis() + MANUAL_HOLD_MS
            posY = (posY + dy).coerceIn(0f, l.height.toFloat())
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val l = layout ?: return false
            val y = (e.y - paddingTop + posY).toInt()
            val line = l.getLineForVertical(y)
            val offset = l.getOffsetForHorizontal(line, e.x - paddingLeft)
            // The word under the finger, staying on the tapped line: the end of a word or the empty space to the
            // right of a line must not select the next word or line.
            val ls = l.getLineStart(line)
            val le = l.getLineEnd(line)
            val tokens = model.tokens
            val onLine = tokens.indices.filter { tokens[it].start in ls until le }
            val idx = onLine.lastOrNull { tokens[it].start <= offset } ?: onLine.firstOrNull()
                ?: tokens.indexOfFirst { it.end > offset }
            if (idx >= 0) {
                manualUntil = 0L
                onWordTap?.invoke(idx)
            }
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mirrorX && !mirrorY) return gestures.onTouchEvent(event) || super.onTouchEvent(event)
        // Taps and drags land on the mirrored picture: map them back onto the text as it is laid out.
        val e = MotionEvent.obtain(event)
        e.setLocation(if (mirrorX) width - event.x else event.x, if (mirrorY) height - event.y else event.y)
        val handled = gestures.onTouchEvent(e)
        e.recycle()
        return handled || super.onTouchEvent(event)
    }

    companion object {
        const val LINE_SPACING = 1.12f
        const val MANUAL_HOLD_MS = 3000L
        val READ_COLOUR: Int = Color.rgb(116, 126, 125)
        val HIGHLIGHT: Int = Color.rgb(142, 196, 186)
        val ACCENT: Int = Color.rgb(255, 176, 32)
    }
}
