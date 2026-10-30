package com.olerast.suflyor.script

/** Spoken forms for written measurements. Display text and its offsets are never rewritten. */
internal object ChineseReadings {
    private const val NUMBER = "[+\\-－−]?[0-9０-９]+(?:[.．][0-9０-９]+)?"
    private const val UNIT = "(?:℃|℉|°\\s*[cCfF]|[%％‰]|摄氏度|华氏度)"
    private const val HAN_NUMBER = "[零〇一二两三四五六七八九十百千万亿点正负]{1,32}"
    const val PATTERN = "(?:(?:摄氏|华氏)\\s*(?:$NUMBER|$HAN_NUMBER)\\s*度|(?:百分之|千分之)\\s*$NUMBER|$NUMBER\\s*$UNIT|℃|℉|°\\s*[cCfF]|[%％‰])"
    private val amount = Regex("([+\\-−]?[0-9]+(?:\\.[0-9]+)?)\\s*(.+)")
    private val fraction = Regex("(百分之|千分之)\\s*([+\\-−]?[0-9]+(?:\\.[0-9]+)?)")
    private val temperature = Regex("(摄氏|华氏)\\s*([+\\-−]?[0-9]+(?:\\.[0-9]+)?|$HAN_NUMBER)\\s*度")
    private val units = mapOf("℃" to "摄氏度", "°c" to "摄氏度", "℉" to "华氏度", "°f" to "华氏度",
        "摄氏度" to "摄氏度", "华氏度" to "华氏度", "%" to "百分之", "‰" to "千分之")
    private const val DIGITS = "零一二三四五六七八九"

    fun normalize(raw: String): String? {
        units[raw.filterNot { it.isWhitespace() }]?.let { return it }
        if (raw.codePointCount(0, raw.length) == 1) return null
        temperature.matchEntire(raw)?.let { m ->
            val value = m.groupValues[2]
            val spoken = if (value.any { it in '0'..'9' }) number(value) ?: return null else value
            return spoken + m.groupValues[1] + "度"
        }
        fraction.matchEntire(raw)?.let { m ->
            return number(m.groupValues[2])?.let { m.groupValues[1] + it }
        }
        val match = amount.matchEntire(raw) ?: return null
        val unit = units[match.groupValues[2].filterNot { it.isWhitespace() }] ?: return null
        val value = number(match.groupValues[1]) ?: return null
        return if (unit.endsWith("分之")) unit + value else value + unit
    }

    private fun number(raw: String): String? {
        val sign = when (raw.first()) { '-', '−' -> "负"; '+' -> "正"; else -> "" }
        val parts = raw.removePrefix("-").removePrefix("−").removePrefix("+").split('.')
        val whole = parts[0]
        // Bound work and avoid overflow for malformed/imported text; leave unsupported values skippable.
        if (whole.length > 12 || parts.getOrNull(1).orEmpty().length > 12) return null
        val value = if (whole.length > 1 && whole[0] == '0') digits(whole) else integer(whole.toLong())
        return sign + value + if (parts.size == 2) "点" + digits(parts[1]) else ""
    }

    private fun digits(text: String): String = text.map { DIGITS[it - '0'] }.joinToString("")

    private fun integer(value: Long, leading: Boolean = true): String {
        for ((base, unit) in listOf(100_000_000L to "亿", 10_000L to "万")) {
            if (value < base) continue
            val rest = value % base
            return integer(value / base, leading) + unit + if (rest == 0L) "" else
                (if (rest < base / 10) "零" else "") + integer(rest, false)
        }
        if (value == 0L) return "零"
        val out = StringBuilder()
        var zero = false
        for ((base, unit) in listOf(1000L to "千", 100L to "百", 10L to "十", 1L to "")) {
            val digit = (value / base % 10).toInt()
            if (digit == 0) {
                if (out.isNotEmpty()) zero = true
                continue
            }
            if (zero) out.append('零')
            zero = false
            if (!(leading && out.isEmpty() && base == 10L && digit == 1)) out.append(DIGITS[digit])
            out.append(unit)
        }
        return out.toString()
    }
}
