package com.olerast.suflyor.script

/** Written symbols become spoken matching units; display text and UTF-16 offsets stay intact. */
internal object ChineseReadings {
    private const val NUMBER = "[+\\-－−]?(?:[0-9０-９]{1,3}(?:[,，][0-9０-９]{3})+|[0-9０-９]+)(?:[.．][0-9０-９]+)?"
    private const val UNIT = "(?:km/h|m/s|kg|mg|cm|mm|km|ml|[mM][²³23]|㎡|㎥|[gGlLmM]|℃|℉|°\\s*[cCfF]|[%％‰]|摄氏度|华氏度|千米每小时|米每秒|平方厘米|平方米|立方米|千克|公斤|毫克|厘米|毫米|千米|公里|毫升|克|米|升|元|美元|欧元|秒|分钟|分|小时|时|天|年|月|日|个|次|台)"
    private const val HAN_NUMBER = "[零〇一二两三四五六七八九十百千万亿点正负]{1,32}"
    private const val DATE = "[0-9０-９]{4}[-/．.][0-9０-９]{1,2}[-/．.][0-9０-９]{1,2}"
    private const val TIME = "[0-9０-９]{1,2}[:：][0-9０-９]{2}(?:[:：][0-9０-９]{2})?"
    const val PATTERN = "(?:$DATE|$TIME|[vV]?[0-9０-９]+(?:[.．][0-9０-９]+){2,}|(?:摄氏|华氏)\\s*(?:$NUMBER|$HAN_NUMBER)\\s*度|零下\\s*(?:$NUMBER|$HAN_NUMBER)(?:\\s*(?:℃|°[cC]|摄氏度|度))?|(?:百分之|千分之)\\s*$NUMBER|(?:$NUMBER|$HAN_NUMBER)\\s*[~～至到–—]\\s*(?:$NUMBER|$HAN_NUMBER)(?:\\s*$UNIT)?|$NUMBER\\s*-\\s*$NUMBER(?:\\s*$UNIT)?|[¥￥$€]\\s*$NUMBER|$NUMBER/[0-9０-９]+|(?:$NUMBER|$HAN_NUMBER)\\s*$UNIT|$NUMBER|[mM][²³]|㎡|㎥|℃|℉|°\\s*[cCfF]|[%％‰±×÷=＝≈≠≤≥+−-])"
    private val amount = Regex("([+\\-−]?[0-9]+(?:\\.[0-9]+)?|$HAN_NUMBER)\\s*($UNIT)")
    private val fraction = Regex("(百分之|千分之)\\s*([+\\-−]?[0-9]+(?:\\.[0-9]+)?)")
    private val temperature = Regex("(摄氏|华氏)\\s*([+\\-−]?[0-9]+(?:\\.[0-9]+)?|$HAN_NUMBER)\\s*度")
    private val range = Regex("($NUMBER|$HAN_NUMBER)\\s*([~至到–—-])\\s*($NUMBER|$HAN_NUMBER)(?:\\s*($UNIT))?")
    private val units = mapOf("℃" to "摄氏度", "°c" to "摄氏度", "℉" to "华氏度", "°f" to "华氏度",
        "摄氏度" to "摄氏度", "华氏度" to "华氏度", "%" to "百分之", "‰" to "千分之",
        "kg" to "千克", "公斤" to "千克", "g" to "克", "mg" to "毫克", "cm" to "厘米", "mm" to "毫米",
        "km" to "千米", "公里" to "千米", "m" to "米", "ml" to "毫升", "l" to "升",
        "m2" to "平方米", "m3" to "立方米", "km/h" to "千米每小时", "m/s" to "米每秒")
    private val symbols = mapOf("±" to "正负", "×" to "乘", "÷" to "除以", "=" to "等于", "+" to "加", "−" to "减", "-" to "减", "≈" to "约等于", "≠" to "不等于", "≤" to "小于等于", "≥" to "大于等于")
    private const val DIGITS = "零一二三四五六七八九"
    private val plainNumber = Regex("[+\\-−]?[0-9]+(?:\\.[0-9]+)?")

    fun normalize(raw: String): String? {
        if (raw.codePointCount(0, raw.length) == 1 && Character.UnicodeScript.of(raw.codePointAt(0)) == Character.UnicodeScript.HAN) return null
        val text = raw.replace(",", "").replace("，", "").replace('－', '-').replace('～', '~')
        symbols[text]?.let { return it }
        units[text.filterNot { it.isWhitespace() }]?.let { return it }
        if (Regex("[0-9]{4}[-/.][0-9]{1,2}[-/.][0-9]{1,2}").matches(text)) {
            val parts = text.split('-', '/', '.').map { it.toIntOrNull() ?: return null }
            if (runCatching { java.time.LocalDate.of(parts[0], parts[1], parts[2]) }.isFailure) return null
            return digits(parts[0].toString().padStart(4, '0')) + "年" + number(parts[1].toString()) + "月" + number(parts[2].toString()) + "日"
        }
        if (Regex("[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?").matches(text)) {
            val parts = text.split(':').map(String::toInt)
            if (parts[0] > 23 || parts.drop(1).any { it > 59 }) return null
            val minute = if (parts[1] == 0) (if (parts.size == 3) "零分" else "") else
                (if (parts[1] < 10) "零" else "") + number(parts[1].toString()) + if (parts.size == 3) "分" else ""
            return number(parts[0].toString()) + "点" + minute + if (parts.size == 3) number(parts[2].toString()) + "秒" else ""
        }
        if (Regex("v?[0-9]+(?:\\.[0-9]+){2,}").matches(text)) return text.removePrefix("v").split('.').joinToString("点", transform = ::digits)
        if (text.startsWith("零下")) {
            val value = text.removePrefix("零下").trim().replace(Regex("(?:℃|°c|摄氏度|度)$"), "")
            return "负" + (number(value) ?: return null) + if (value.length < text.removePrefix("零下").trim().length) "摄氏度" else ""
        }
        temperature.matchEntire(text)?.let { m -> return (number(m.groupValues[2]) ?: return null) + m.groupValues[1] + "度" }
        fraction.matchEntire(text)?.let { m -> return m.groupValues[1] + (number(m.groupValues[2]) ?: return null) }
        range.matchEntire(text)?.let { m ->
            return (number(m.groupValues[1]) ?: return null) + "至" + (number(m.groupValues[3]) ?: return null) + unit(m.groupValues[4])
        }
        if (text.firstOrNull() in listOf('¥', '$', '€')) {
            return (number(text.drop(1).trim()) ?: return null) + when (text[0]) { '$' -> "美元"; '€' -> "欧元"; else -> "元" }
        }
        Regex("([0-9]+)/([0-9]+)").matchEntire(text)?.let { m ->
            if (m.groupValues[2].all { it == '0' }) return null
            return (number(m.groupValues[2]) ?: return null) + "分之" + (number(m.groupValues[1]) ?: return null)
        }
        amount.matchEntire(text)?.let { m ->
            val suffix = unit(m.groupValues[2])
            val value = if (suffix == "年" && m.groupValues[1].matches(Regex("[0-9]{4}"))) digits(m.groupValues[1]) else number(m.groupValues[1]) ?: return null
            if (suffix !in units.values && !m.groupValues[2].matches(Regex(UNIT))) return null
            return if (suffix.endsWith("分之")) suffix + value else value + suffix
        }
        return if (plainNumber.matches(text)) number(text) else null
    }

    /** Context resolves identifiers and duration/ratio colons without changing the displayed manuscript. */
    fun inContext(raw: String, before: String, after: String): String? {
        if (raw == "号" && Regex("月[零〇一二三四五六七八九十0-9０-９]{1,8}$").containsMatchIn(before)) return "日"
        if (raw.codePointCount(0, raw.length) == 1 && Character.UnicodeScript.of(raw.codePointAt(0)) == Character.UnicodeScript.HAN) return null
        if (raw.matches(Regex("$HAN_NUMBER" + "点" + "$HAN_NUMBER" + "分")) && !Regex("^$HAN_NUMBER" + "秒").containsMatchIn(after)) return raw.removeSuffix("分")
        if (raw in listOf("-", "−") && !(before.trimEnd().lastOrNull()?.isDigit() == true && after.trimStart().firstOrNull()?.isDigit() == true)) return ""
        val prefix = before.takeLast(12).trimEnd().trimEnd('：', ':', ' ')
        if (Regex("[0-9]{1,2}:[0-9]{2}").matches(raw)) {
            val parts = raw.split(':').map(String::toInt)
            if (parts[1] < 60 && Regex("(?:时长|用时|耗时|持续|倒计时)$").containsMatchIn(prefix)) return number(parts[0].toString()) + "分" + number(parts[1].toString()) + "秒"
            if (Regex("(?:比例|比值)$").containsMatchIn(prefix)) return number(parts[0].toString()) + "比" + number(parts[1].toString())
        }
        if (raw.matches(Regex("[0-9]{1,12}")) && Regex("(?:编号|序号|电话|号码|邮编|型号)$").containsMatchIn(prefix)) return digits(raw)
        if (raw.matches(Regex("[+-][0-9]+(?:\\.[0-9]+)?")) && before.lastOrNull()?.isDigit() == true) return (if (raw[0] == '+') "加" else "减") + number(raw.drop(1))
        val subtraction = range.matchEntire(raw)
        if (subtraction != null && subtraction.groupValues[2] == "-" && after.trimStart().firstOrNull() in listOf('=', '＝')) {
            return number(subtraction.groupValues[1]) + "减" + number(subtraction.groupValues[3])
        }
        return normalize(raw)
    }

    private fun unit(raw: String): String = raw.filterNot(Char::isWhitespace).let { units[it] ?: it }
    private fun number(raw: String): String? {
        if (raw.matches(Regex(HAN_NUMBER))) return raw.replace('两', '二').replace('〇', '零')
        if (!plainNumber.matches(raw)) return null
        val sign = when (raw.first()) { '-', '−' -> "负"; '+' -> "正"; else -> "" }
        val parts = raw.removePrefix("-").removePrefix("−").removePrefix("+").split('.')
        val whole = parts[0]
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
            if (digit == 0) { if (out.isNotEmpty()) zero = true; continue }
            if (zero) out.append('零')
            zero = false
            if (!(leading && out.isEmpty() && base == 10L && digit == 1)) out.append(DIGITS[digit])
            out.append(unit)
        }
        return out.toString()
    }
}
