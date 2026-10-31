package com.olerast.suflyor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
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

/**
 * Teleprompter text. Progress is shown by lines, not words: lines already read are dimmed, the current line stays
 * bright with an accent mark and sits on the reading line (smooth scrolling). Word-level highlighting is optional —
 * with ~0.5 s of recognition latency it tends to look "behind" the voice.
 * Tap a word to jump there; drag to look around (following resumes a few seconds later).
 */
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
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val focusStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; style = Paint.Style.STROKE }
    private val phrasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val highlightRect = RectF()
    private val focusMotion = HighlightMotion()
    private var phraseByToken = emptyArray<IntRange?>()
    private var currentPhrase: IntRange? = null
    private var previousPhrase: IntRange? = null
    private var phraseStarted = 0L
    private var phraseDuration = 0L
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
    private var highlightToken = 0
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
        highlightToken = 0
        phraseByToken = HighlightLayout.phrases(m.tokens)
        currentPhrase = null
        previousPhrase = null
        rebuildLayout()
        posY = targetY
    }

    fun setProgress(nextTokenIndex: Int, animate: Boolean = true, highlightTokenIndex: Int = nextTokenIndex) {
        if (nextTokenIndex == nextToken && highlightTokenIndex == highlightToken && layout != null) return
        moveDelta = nextTokenIndex - nextToken
        nextToken = nextTokenIndex
        highlightToken = highlightTokenIndex
        applyProgress(animate)
    }

    private fun applyProgress(animate: Boolean) {
        val l = layout ?: return
        val tokens = model.tokens
        text.removeSpan(spokenSpan)
        val atEnd = nextToken >= tokens.size
        val previousLine = currentLine
        currentLine = if (atEnd) l.lineCount - 1 else l.getLineForOffset(tokens[nextToken].start)
        val chinese = model.lang == com.olerast.suflyor.script.SpeechLang.ZH
        val dimEnd = if (atEnd) text.length else if (chinese) tokens[nextToken].start else l.getLineStart(currentLine)
        if (dimEnd > 0) text.setSpan(spokenSpan, 0, dimEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val readingLine = currentLine.coerceAtLeast(0)
        val above = readLines?.let {
            PrompterViewport.readOffset(readingLine, it, (height - paddingTop - paddingBottom).coerceAtLeast(0),
                l::getLineTop, l::getLineBottom).toFloat()
        } ?: (linesAbove * lineHeightPx)
        val target = (l.getLineTop(readingLine) - above).coerceAtLeast(0f)
        if (!animate) {
            targetY = target
            posY = target
            scrollFrom = target
            scrollDuration = 0L
        } else if (target != targetY && ScrollMotion.shouldScroll(moveDelta, currentLine != previousLine)) {
            val now = SystemClock.uptimeMillis()
            if (now >= manualUntil && !wasManual) posY = ScrollMotion.position(scrollFrom, targetY, now - scrollStarted, scrollDuration)
            scrollFrom = posY
            targetY = target
            scrollStarted = now
            scrollDuration = ScrollMotion.duration(moveDelta)
        }
        val now = SystemClock.uptimeMillis()
        val visible = HighlightLayout.visibleToken(nextToken, highlightToken, tokens.size,
            targetY + (height - paddingTop - paddingBottom).coerceAtLeast(0),
            { l.getLineBottom(l.getLineForOffset(tokens[it].start)) }, { tokens[it].spoken })
        val box = if ((wordHighlight || chinese) && visible < tokens.size) tokenBox(l, visible) else null
        focusMotion.move(box, now, animate)
        val phrase = phraseByToken.getOrNull(visible)
        if (phrase != currentPhrase || !animate) {
            previousPhrase = if (animate) currentPhrase else null
            currentPhrase = phrase
            phraseStarted = now
            phraseDuration = if (animate) 180L else 0L
        }
        invalidate()
    }

    private fun tokenBox(l: StaticLayout, index: Int): HighlightMotion.Box {
        val token = model.tokens[index]
        val row = l.getLineForOffset(token.start)
        val end = minOf(token.end, l.getLineVisibleEnd(row))
        val startX = l.getPrimaryHorizontal(token.start)
        val endX = if (end == l.getLineVisibleEnd(row)) l.getLineRight(row) else l.getPrimaryHorizontal(end)
        val baseline = l.getLineBaseline(row)
        val metrics = paint.fontMetrics
        return HighlightMotion.Box(minOf(startX, endX) - dp(2f), baseline + metrics.ascent - dp(1f),
            maxOf(startX, endX, startX + dp(4f)) + dp(2f), baseline + metrics.descent + dp(1f), row)
    }

    private fun drawPhrase(canvas: Canvas, l: StaticLayout, range: IntRange?, alpha: Float) {
        if (range == null || alpha <= 0f || text.isEmpty()) return
        val end = (range.last + 1).coerceAtMost(text.length)
        if (range.first >= end) return
        phrasePaint.alpha = (25f * alpha).toInt().coerceIn(0, 25)
        for (row in l.getLineForOffset(range.first)..l.getLineForOffset(end - 1)) {
            val start = maxOf(range.first, l.getLineStart(row))
            val stop = minOf(end, l.getLineVisibleEnd(row))
            if (start >= stop) continue
            val a = l.getPrimaryHorizontal(start)
            val b = if (stop == l.getLineVisibleEnd(row)) l.getLineRight(row) else l.getPrimaryHorizontal(stop)
            val baseline = l.getLineBaseline(row)
            val metrics = paint.fontMetrics
            highlightRect.set(minOf(a, b) - dp(4f), baseline + metrics.ascent - dp(2f),
                maxOf(a, b) + dp(4f), baseline + metrics.descent + dp(2f))
            canvas.drawRoundRect(highlightRect, dp(6f), dp(6f), phrasePaint)
        }
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
        applyProgress(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        val now = SystemClock.uptimeMillis()
        if (now >= manualUntil) {
            if (wasManual) {
                scrollFrom = posY
                scrollStarted = now
                scrollDuration = ScrollMotion.duration(moveDelta)
                wasManual = false
            }
            posY = ScrollMotion.position(scrollFrom, targetY, now - scrollStarted, scrollDuration)
        }
        canvas.save()
        if (mirrorX || mirrorY) canvas.scale(if (mirrorX) -1f else 1f, if (mirrorY) -1f else 1f, width / 2f, height / 2f)
        canvas.clipRect(0, 0, width, height)
        canvas.translate(paddingLeft.toFloat(), paddingTop - posY)
        val phraseFade = ScrollMotion.position(0f, 1f, now - phraseStarted, phraseDuration)
        drawPhrase(canvas, l, previousPhrase, 1f - phraseFade)
        drawPhrase(canvas, l, currentPhrase, phraseFade)
        if (phraseFade >= 1f) previousPhrase = null
        val focus = focusMotion.frame(now)
        focus.box?.let { box ->
            highlightRect.set(box.left, box.top, box.right, box.bottom)
            focusPaint.alpha = (105f * focus.alpha).toInt().coerceIn(0, 105)
            focusStroke.alpha = (150f * focus.alpha).toInt().coerceIn(0, 150)
            focusStroke.strokeWidth = dp(1f)
            canvas.drawRoundRect(highlightRect, dp(4f), dp(4f), focusPaint)
            canvas.drawRoundRect(highlightRect, dp(4f), dp(4f), focusStroke)
        }
        if (model.tokens.isNotEmpty() && nextToken < model.tokens.size) {
            val top = l.getLineTop(currentLine).toFloat()
            val bottom = l.getLineBottom(currentLine).toFloat() - (lineSpacingMultiplier - 1f) * lineHeightPx / lineSpacingMultiplier
            val x = -dp(9f)
            markRect.set(x, top + dp(3f), x + dp(3.5f), bottom - dp(1f))
            canvas.drawRoundRect(markRect, dp(2f), dp(2f), markPaint)
        }
        l.draw(canvas)
        canvas.restore()
        if (posY != targetY || now < manualUntil || focusMotion.running(now) || phraseFade < 1f) postInvalidateOnAnimation()
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
        val ACCENT: Int = Color.rgb(255, 176, 32)
    }
}
