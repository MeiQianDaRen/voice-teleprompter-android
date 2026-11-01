package com.olerast.suflyor.overlay

import com.olerast.suflyor.script.ScriptModel

/** A compact display copy: token indices and speech attributes stay compatible with the original tracker. */
internal object OverlayScript {
    fun compact(model: ScriptModel): ScriptModel {
        val source = model.displayText
        val offsets = IntArray(source.length + 1)
        val text = StringBuilder(source.length)
        var start = 0
        while (start < source.length) {
            var end = start
            while (end < source.length && !lineBreak(source[end])) end++
            val visible = (start until end).any { !blank(source[it]) }
            if (visible && text.isNotEmpty()) text.append('\n')
            for (i in start until end) {
                offsets[i] = text.length
                if (visible) text.append(source[i])
            }
            offsets[end] = text.length
            var next = end
            if (next < source.length) {
                next++
                if (source[end] == '\r' && source.getOrNull(next) == '\n') next++
            }
            for (i in end until next) offsets[i] = text.length
            start = next
        }
        offsets[source.length] = text.length
        val display = text.toString()
        if (display == source) return model

        fun remap(ranges: List<IntRange>) = ranges.mapNotNull { range ->
            if (range.isEmpty() || range.first < 0 || range.last >= source.length) return@mapNotNull null
            val first = offsets[range.first]
            val end = offsets[range.last + 1]
            if (end > first) first until end else null
        }
        return ScriptModel(model.title, display,
            model.tokens.map { it.copy(start = offsets[it.start], end = offsets[it.end]) },
            remap(model.emphasis), remap(model.notes), model.lang)
    }

    private fun lineBreak(c: Char) = c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'
    private fun blank(c: Char) = c.isWhitespace() || c in "\u200B\u200C\u200D\u2060\uFEFF"
}
