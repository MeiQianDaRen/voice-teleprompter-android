package com.olerast.suflyor.overlay

import com.olerast.suflyor.script.Token

internal object HighlightLayout {
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
