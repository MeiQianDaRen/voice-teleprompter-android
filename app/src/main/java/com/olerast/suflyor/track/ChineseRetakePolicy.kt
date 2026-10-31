package com.olerast.suflyor.track

/** Small, bounded edit windows keep retake matching independent of Android and testable with a fake clock. */
internal object ChineseRetakePolicy {
    const val DEFAULT_CHARS = 5
    const val MIN_CHARS = 4
    const val MAX_CHARS = 8
    const val DISTANCE = 100
    const val STABLE_MS = 450L

    fun editCount(heard: List<String>, script: List<String>, start: Int, end: Int): Int? {
        val length = end - start
        if (kotlin.math.abs(heard.size - length) > 1) return null
        var previous = IntArray(length + 1) { it }
        var current = IntArray(length + 1)
        for (i in heard.indices) {
            current[0] = i + 1
            for (j in 1..length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1,
                    previous[j - 1] + if (heard[i] == script[start + j - 1]) 0 else 1)
            }
            val swap = previous; previous = current; current = swap
        }
        val edits = previous[length]
        // At most one edit AND similarity >= 80%; four letters with one substitution are only 75%.
        return edits.takeIf { it <= 1 && it * 5 <= maxOf(heard.size, length) }
    }
}
