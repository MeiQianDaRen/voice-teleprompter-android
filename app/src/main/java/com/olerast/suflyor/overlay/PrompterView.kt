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
import android.graphics.RectF
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

/** Steady frame-clock scrolling, a clear reading-line band and a gently moving word-group focus. */
class PrompterView(context: Context) : View(context) {
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val markRect = RectF()
    private var model: ScriptModel = ScriptModel.EMPTY
    private var text = SpannableString("")
    private var layout: StaticLayout? = null

    private val spokenSpan = ForegroundColorSpan(Color.argb(95, 255, 255, 255))
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HIGHLIGHT }
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HIGHLIGHT }
    private val rowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HIGHLIGHT; style = Paint.Style.STROKE }
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val noticePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HIGHLIGHT; textSize = dp(13f) }
    private val highlightRect = RectF()
    private val focusMotion = HighlightMotion()
    private val rowMotion = HighlightMotion()
    private val readingMotion = ReadingMotion()
    private var wordByToken = emptyArray<IntRange?>()
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

    var wordHighlight = false
        set(v) {
            if (field == v) return
            field = v
            applyProgress(false)
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
        wordByToken = HighlightLayout.words(m.displayText, m.tokens, m.lang == com.olerast.suflyor.script.SpeechLang.ZH)
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
        // Do not forecast past the confirmed reading row: short rows must not scroll out of view.
        val rowCeiling = rowEnd.getOrNull(ordinal)?.toFloat()?.minus(0.001f) ?: source.ceiling
        val ceiling = if (source.rate > 0f) minOf(source.ceiling, rowCeiling.coerceAtLeast(source.position)) else source.ceiling
        motionSample = source.copy(ceiling = ceiling, forecast = source.forecast.coerceAtMost(ceiling))
        readingMotion.follow(motionSample, now, animate)
        if (changed || !animate) applyProgress(animate)
        invalidate()
    }

    private fun applyProgress(animate: Boolean) {
        val l = layout ?: return
        text.removeSpan(spokenSpan)
        val previousLine = currentLine
        currentLine = if (nextToken >= model.tokens.size) l.lineCount - 1 else l.getLineForOffset(model.tokens[nextToken].start)
        // Keep the whole reading line bright. Updating individual character colours makes partials flicker.
        val dimEnd = if (nextToken >= model.tokens.size) text.length else l.getLineStart(currentLine)
        if (dimEnd > 0) text.setSpan(spokenSpan, 0, dimEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val now = SystemClock.uptimeMillis()
        val baseline = l.getLineBaseline(currentLine)
        val fm = paint.fontMetrics
        val row = if (spoken.isEmpty() || nextToken >= model.tokens.size) null else HighlightMotion.Box(
            -dp(7f), baseline + fm.ascent - dp(5f), l.width + dp(7f), baseline + fm.descent + dp(5f), currentLine,
        )
        rowMotion.move(row, now, animate)
        if (animate && (fixedReadingRow() || currentLine != previousLine)) {
            val target = scrollTarget(l, readingMotion.frame(now))
            if (target != targetY) {
                scrollFrom = posY
                scrollStarted = now
                scrollDuration = ScrollMotion.duration(moveDelta)
                targetY = target
            }
        }
        if (!animate) {
            targetY = scrollTarget(l, readingMotion.frame(now))
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
        return (y - above).coerceIn(0f, l.getLineTop(currentLine).toFloat().coerceAtLeast(0f))
    }

    private fun wordBox(l: StaticLayout, index: Int): HighlightMotion.Box? {
        val range = wordByToken.getOrNull(index) ?: return null
        val row = l.getLineForOffset(model.tokens[index].start)
        val start = maxOf(range.first, l.getLineStart(row))
        val end = minOf(range.last + 1, l.getLineVisibleEnd(row))
        val a = l.getPrimaryHorizontal(start)
        val b = if (end == l.getLineVisibleEnd(row)) l.getLineRight(row) else l.getPrimaryHorizontal(end)
        val baseline = l.getLineBaseline(row)
        val fm = paint.fontMetrics
        return HighlightMotion.Box(minOf(a, b) - dp(3f), baseline + fm.ascent - dp(2f),
            maxOf(a, b) + dp(3f), baseline + fm.descent + dp(2f), row)
    }

    private fun drawFocus(canvas: Canvas, motion: HighlightMotion, now: Long, row: Boolean) {
        val f = motion.frame(now)
        fun draw(box: HighlightMotion.Box?, alpha: Float) {
            if (box == null || alpha <= 0f) return
            highlightRect.set(box.left, box.top, box.right, box.bottom)
            val fill = if (row) rowPaint else focusPaint
            fill.alpha = ((if (row) 100f else 55f) * alpha).toInt()
            canvas.drawRoundRect(highlightRect, dp(7f), dp(7f), fill)
            if (row) {
                rowStroke.alpha = (125f * alpha).toInt()
                rowStroke.strokeWidth = dp(1f)
                canvas.drawRoundRect(highlightRect, dp(7f), dp(7f), rowStroke)
            }
        }
        draw(f.previousBox, f.previousAlpha)
        draw(f.box, f.alpha)
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
        targetY = scrollTarget(l, position)
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
        val at = floor(position).toInt().coerceIn(0, spoken.size.coerceAtLeast(1) - 1)
        // Keep the word focus on the confirmed reading row, even if the baseline predicts the next row.
        var visible = maxOf(nextToken, spoken.getOrNull(at) ?: model.tokens.size)
        while (visible > nextToken && (visible >= model.tokens.size || !model.tokens[visible].spoken || l.getLineForOffset(model.tokens[visible].start) > currentLine)) visible--
        val chinese = model.lang == com.olerast.suflyor.script.SpeechLang.ZH
        val box = if ((wordHighlight || chinese) && nextToken < model.tokens.size) wordBox(l, visible) else null
        focusMotion.move(box, now, true)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.save()
        if (mirrorX || mirrorY) canvas.scale(if (mirrorX) -1f else 1f, if (mirrorY) -1f else 1f, width / 2f, height / 2f)
        canvas.clipRect(0, 0, width, height)
        canvas.translate(paddingLeft.toFloat(), paddingTop - posY)
        drawFocus(canvas, rowMotion, now, true)
        drawFocus(canvas, focusMotion, now, false)
        if (model.tokens.isNotEmpty() && nextToken < model.tokens.size) {
            val top = l.getLineTop(currentLine).toFloat()
            val bottom = l.getLineBottom(currentLine).toFloat() - (lineSpacingMultiplier - 1f) * lineHeightPx / lineSpacingMultiplier
            markRect.set(-dp(9f), top + dp(3f), -dp(5.5f), bottom - dp(1f))
            markPaint.color = HIGHLIGHT
            canvas.drawRoundRect(markRect, dp(2f), dp(2f), markPaint)
        }
        l.draw(canvas)
        drawEdgeFade(canvas, l)
        canvas.restore()
        canvas.restoreToCount(layer)
        if (now < noticeUntil) {
            val message = context.getString(R.string.reading_retreated)
            noticePaint.alpha = (255f * minOf(1f, (noticeUntil - now) / 180f)).toInt()
            canvas.drawText(message, width - paddingRight - noticePaint.measureText(message), paddingTop + dp(16f), noticePaint)
        }
        if (readingMotion.running(now) || now < manualUntil || now - scrollStarted < scrollDuration ||
            focusMotion.running(now) || rowMotion.running(now) || now < noticeUntil) postInvalidateOnAnimation()
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
        val HIGHLIGHT: Int = Color.rgb(142, 196, 186)
        val ACCENT: Int = Color.rgb(255, 176, 32)
    }
}
