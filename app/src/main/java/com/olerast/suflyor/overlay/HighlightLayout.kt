package com.olerast.suflyor.overlay

import com.olerast.suflyor.script.Token
import java.text.BreakIterator
import java.util.Locale

internal object HighlightLayout {
    /** Offline word boundaries, merging isolated Han characters into stable groups of 2–4 characters. */
    fun words(text: String, tokens: List<Token>, chinese: Boolean): Array<IntRange?> {
        val result = arrayOfNulls<IntRange>(tokens.size)
        val iterator = BreakIterator.getWordInstance(if (chinese) Locale.CHINESE else Locale.ENGLISH).apply { setText(text) }
        var first = -1
        var last = -1
        var count = 0
        fun finish() {
            if (first >= 0) for (i in first..last) result[i] = tokens[first].start until tokens[last].end
            first = -1; last = -1; count = 0
        }
        for (i in tokens.indices) {
            val t = tokens[i]
            if (!t.spoken) { finish(); continue }
            val han = chinese && text.substring(t.start, t.end).codePoints().allMatch {
                Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN
            }
            if (!han) {
                finish(); result[i] = t.start until t.end; continue
            }
            if (t.anchor || last >= 0 && text.substring(tokens[last].end, t.start).any { !it.isWhitespace() || it == '\n' }) finish()
            if (first < 0) first = i
            last = i
            count++
            if (count >= 4 || count >= 2 && iterator.isBoundary(t.end)) finish()
        }
        finish()
        return result
    }

    fun phrases(tokens: List<Token>): Array<IntRange?> {
        val result = arrayOfNulls<IntRange>(tokens.size)
        var first = -1
        var last = -1
        var count = 0
        fun finish() {
            if (first >= 0) {
                val range = tokens[first].start until tokens[last].end
                for (i in first..last) result[i] = range
            }
            first = -1; last = -1; count = 0
        }
        for (i in tokens.indices) {
            val token = tokens[i]
            if (!token.spoken) { finish(); continue }
            if (token.anchor || count >= 8) finish()
            if (first < 0) first = i
            last = i
            count++
        }
        finish()
        return result
    }

    fun visibleToken(actual: Int, requested: Int, count: Int, bottom: Float,
        lineBottom: (Int) -> Int, spoken: (Int) -> Boolean): Int {
        if (actual >= count) return count
        var candidate = requested.coerceIn(actual, count - 1)
        while (candidate > actual && (!spoken(candidate) || lineBottom(candidate) > bottom)) candidate--
        return candidate
    }
}
