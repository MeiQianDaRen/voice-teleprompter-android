package com.olerast.suflyor.script

/** Command tolerance is deliberately separate from fuzzy script alignment. */
internal object ChineseVoiceCommands {
    val restartPhrases = listOf(
        "欧了重新开始", "欧了从新开始", "欧了重新来", "欧了从新来", "欧了从头开始", "欧了返回开始",
    )
    private val wakes = listOf("欧了", "欧乐", "欧勒", "哦了", "噢了")
    private val actions = restartPhrases.map { it.drop(2) }.distinct()
    private val sounds = mapOf(
        "重" to "chong", "从" to "cong", "新" to "xin", "开" to "kai", "始" to "shi",
        "来" to "lai", "头" to "tou", "返" to "fan", "回" to "hui",
        "心" to "xin", "薪" to "xin", "凯" to "kai", "使" to "shi", "史" to "shi",
        "是" to "shi", "莱" to "lai", "投" to "tou", "反" to "fan", "会" to "hui",
    )
    private data class Hit(val start: Int, val end: Int, val exact: Boolean)

    /** At most one homophone in the action; no arbitrary edits or missing wake phrase. */
    private fun actionMatches(heard: List<String>, expected: String, pronunciation: (String) -> String?): Boolean {
        if (heard.size > expected.length) return false
        var differences = 0
        for (i in heard.indices) {
            val wanted = expected[i].toString()
            if (heard[i] == wanted) continue
            if (++differences > 1) return false
            val sound = sounds[heard[i]] ?: pronunciation(heard[i])
            if (sound == null || sound != sounds[wanted]) return false
        }
        return true
    }

    private fun hits(words: List<String>, pronunciation: (String) -> String?): List<Hit> = words.indices.mapNotNull { start ->
        if (start + 2 > words.size) return@mapNotNull null
        val wake = words.subList(start, start + 2).joinToString("")
        if (wake !in wakes) return@mapNotNull null
        actions.firstNotNullOfOrNull { action ->
            val end = start + 2 + action.length
            if (end > words.size) null else {
                val heard = words.subList(start + 2, end)
                if (!actionMatches(heard, action, pronunciation)) null
                else Hit(start, end, wake == "欧了" && heard.joinToString("") == action)
            }
        }
    }

    /** Partial/final deduplication is scoped to one utterance. */
    class Detector(private val pronunciation: (String) -> String? = { null }) {
        data class Decision(val restart: Boolean = false, val strip: Int = 0, val pending: Boolean = false)
        private var carry = emptyList<String>()
        private var carryAt = 0L
        private var handled = 0
        private var pendingStart = -1
        private var pendingCount = 0
        private var observations = 0
        private var previous = emptyList<String>()

        fun reset() {
            carry = emptyList()
            clearUtterance()
        }

        private fun clearUtterance() {
            handled = 0
            pendingStart = -1
            pendingCount = 0
            observations = 0
            previous = emptyList()
        }

        fun consume(words: List<String>, isFinal: Boolean, now: Long): Decision {
            if (previous.isEmpty() && (now < carryAt || now - carryAt > 2000L)) carry = emptyList()
            val prefix = carry.size
            val combined = carry + words
            val found = hits(combined, pronunciation)
            val last = found.lastOrNull()
            val fresh = last != null && found.size > handled
            if (fresh) {
                observations = if (pendingStart == last.start && pendingCount == found.size) {
                    observations + if (combined != previous) 1 else 0
                } else 1
                pendingStart = last.start
                pendingCount = found.size
            } else if (last == null) {
                pendingStart = -1
                observations = 0
            }
            val restart = fresh && (last.exact || isFinal || observations >= 2)
            if (restart) handled = maxOf(handled, found.size)
            val decision = Decision(restart,
                if (last != null && found.size <= handled) (last.end - prefix).coerceAtLeast(0) else 0,
                pending = fresh && !restart)
            previous = combined
            if (isFinal) {
                // Carry only an unfinished command suffix, never a completed command or arbitrary speech.
                val minStart = found.lastOrNull()?.end ?: 0
                val start = (minStart until combined.size).firstOrNull { index ->
                    val suffix = combined.drop(index)
                    wakes.any { wake -> actions.any { action ->
                        val phrase = wake + action
                        suffix.size < phrase.length &&
                            suffix.take(2).joinToString("") == wake.take(minOf(2, suffix.size)) &&
                            (suffix.size <= 2 || actionMatches(suffix.drop(2), action, pronunciation))
                    } }
                }
                carry = start?.let { combined.drop(it) } ?: emptyList()
                if (start == null || start >= prefix) carryAt = now
                clearUtterance()
            }
            return decision
        }
    }
}
