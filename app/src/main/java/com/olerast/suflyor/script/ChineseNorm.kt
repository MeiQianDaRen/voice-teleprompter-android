package com.olerast.suflyor.script

import java.text.Normalizer
import java.util.Locale

/** The Chinese Zipformer emits Han characters, with or without spaces: never split only on whitespace. */
object ChineseNorm {
    // Keep measurements together; other Han characters retain one code point per displayed token.
    val WORD = Regex(ChineseReadings.PATTERN + "|\\p{IsHan}|[\\p{L}\\p{N}&&[^\\p{IsHan}]]+(?:['’][\\p{L}&&[^\\p{IsHan}]]+)*")
    val FILLERS = setOf("嗯", "呃", "啊")
    private val FUNCTION = setOf("的", "地", "得", "了", "着", "过", "是", "在", "和", "与", "及", "也", "就", "都")
    private val HAN_SPACES = Regex("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})")

    fun normalizeWord(raw: String): String {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace('〇', '零')
        return ChineseReadings.normalize(normalized) ?: normalized
    }

    fun words(text: String): List<String> = WORD.findAll(text.replace(HAN_SPACES, "")).flatMap { match ->
        val norm = normalizeWord(match.value)
        if (isHan(norm)) norm.codePoints().toArray().asSequence().map { String(Character.toChars(it)) }
        else sequenceOf(norm)
    }.toList()

    fun isHan(text: String): Boolean = text.isNotEmpty() &&
        text.codePoints().allMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }

    fun weight(norm: String, foreign: Boolean): Float = when {
        foreign || norm.any { it.isDigit() } || norm in FILLERS -> 0f
        norm in FUNCTION -> 0.5f
        else -> 1f
    }

    // Similar-looking or homophonous characters are not proof of a position. The aligner can skip isolated errors.
    fun similarity(a: String, b: String): Float = if (a == b) 1f else 0f

    /** Insert before the next token; punctuation, original characters and UTF-16 offsets stay recoverable. */
    fun lineBreaks(text: String, maxChars: Int): Set<Int> {
        val notes = Regex("\\[[^\\[\\]]*]").findAll(text).map { it.range }.toList()
        val tokens = WORD.findAll(text).filter { token -> notes.none { token.range.first in it } }.toList()
        val out = linkedSetOf<Int>()
        var count = 0
        for (i in 0 until tokens.lastIndex) {
            count++
            val between = text.substring(tokens[i].range.last + 1, tokens[i + 1].range.first)
            if (count >= maxChars.coerceAtLeast(1) || between.any { it in "。！？.!?…；;，,：:]" }) {
                out += tokens[i + 1].range.first
                count = 0
            }
        }
        return out
    }

    /** Bounded character phrases for cjkchar hotwords; don't boost every common single character. */
    fun biasPhrases(model: ScriptModel): List<String> {
        val out = linkedSetOf<String>()
        val tokens = model.tokens
        for (i in tokens.indices) {
            val phrase = StringBuilder()
            for (j in i until minOf(i + 4, tokens.size)) {
                val t = tokens[j]
                if (!t.spoken || !isHan(t.norm)) break
                if (j > i && model.displayText.substring(tokens[j - 1].end, t.start).any { !it.isWhitespace() }) break
                phrase.append(t.norm)
                if (j > i) out += phrase.toString()
            }
            if (out.size >= 1500) break
        }
        return out.take(1500)
    }
}
