package com.olerast.suflyor.track

import java.text.BreakIterator
import java.util.Locale

/** Precomputed short anchors plus bounded weighted edit alignment. No Android or audio state. */
internal class ChineseLocalAlignment(
    private val script: List<String>,
    private val weights: List<Float>,
    punctuation: Set<Int>,
    private val pronunciation: (String) -> String?,
) {
    class Phrase(val start: Int, val end: Int, val weight: Float, val lowInformation: Boolean,
        key: () -> List<String?>) { val pinyin: List<String?> by lazy(key) }
    data class Hit(val start: Int, val end: Int, val matches: Int, val length: Int, val quality: Float)
    private val keys = HashMap<String, String?>()
    private fun key(character: String): String? {
        if (!keys.containsKey(character)) keys[character] = pronunciation(character)
        return keys[character]
    }

    val phrases: List<Phrase> = buildList {
        val joined = script.joinToString("")
        val offsets = IntArray(script.size + 1)
        for (i in script.indices) offsets[i + 1] = offsets[i] + script[i].length
        val words = BreakIterator.getWordInstance(Locale.CHINA).apply { setText(joined) }
        val wordEnds = hashSetOf<Int>()
        var boundary = words.first()
        while (boundary != BreakIterator.DONE) {
            val index = offsets.binarySearch(boundary)
            if (index >= 0) wordEnds += index
            boundary = words.next()
        }
        var start = 0
        while (start < script.size) {
            val limit = minOf(start + 8, script.size)
            val stop = punctuation.filter { it > start && it <= limit }.minOrNull()
            val end = stop ?: ((start + 4)..limit).lastOrNull { it in wordEnds } ?: limit
            val weight = weights.subList(start, end).sum()
            val from = start
            add(Phrase(start, end, weight, weight / (end - start) < 0.65f) {
                script.subList(from, end).map(::key)
            })
            start = end
        }
    }
    private val phraseEnds = phrases.map { it.end }.toSet()
    private fun locality(hit: Hit, cursor: Int): Float = hit.quality -
        (kotlin.math.abs(hit.end - cursor) - 3).coerceAtLeast(0) * 0.006f

    /** Only the latest 3–8 characters, at most 8 behind / [ahead] ahead of the live pointer. */
    fun find(heard: List<String>, cursor: Int, ahead: Int = 28, continuity: Boolean = true): Hit? {
        val hits = ArrayList<Hit>()
        val limit = minOf(script.size, cursor + ahead)
        for (length in minOf(8, heard.size) downTo 3) {
            val tail = heard.takeLast(length)
            for (start in (cursor - 8).coerceAtLeast(0) until limit) {
                for (span in (length - 1)..(length + 1)) {
                    val end = start + span
                    if (end < cursor || end > limit || span < 3) continue
                    val weight = weights.subList(start, end).sum()
                    if (weight < 2.5f || weight / span < 0.65f) continue
                    val cost = editCost(tail, start, end)
                    val quality = 1f - cost / maxOf(weight, length.toFloat())
                    if (quality < 0.8f) continue
                    // Three-letter anchors require exact characters and are confirmed by the caller.
                    if (length == 3 && cost != 0f) continue
                    hits += Hit(start, end, minOf(length, span) - if (cost > 0f && length == span) 1 else 0,
                        length, quality)
                }
            }
        }
        if (hits.isEmpty()) return null
        val ordered = hits.sortedWith(compareByDescending<Hit> { locality(it, cursor) }
            .thenByDescending { it.length }.thenByDescending { it.start <= cursor && it.end > cursor }
            .thenByDescending { it.end in phraseEnds }.thenBy { kotlin.math.abs(it.end - cursor) })
        val best = ordered.first()
        val continuous = continuity && best.start <= cursor && best.end > cursor
        if (!continuous && ordered.any { kotlin.math.abs(it.end - best.end) > 1 &&
            it.length == best.length && it.quality >= best.quality - 0.02f }) return null
        return best
    }

    /** Two DP planes: exact so far / one edit used. A second edit is never admitted. */
    private fun editCost(heard: List<String>, start: Int, end: Int): Float {
        val width = end - start + 1
        val costs = FloatArray((heard.size + 1) * width * 2) { Float.POSITIVE_INFINITY }
        fun at(i: Int, j: Int, edits: Int) = (i * width + j) * 2 + edits
        costs[at(0, 0, 0)] = 0f
        for (i in 0..heard.size) for (j in 0 until width) for (edits in 0..1) {
            val current = costs[at(i, j, edits)]
            if (!current.isFinite()) continue
            fun offer(a: Int, b: Int, e: Int, extra: Float) {
                val index = at(a, b, e)
                costs[index] = minOf(costs[index], current + extra)
            }
            if (i < heard.size && j < width - 1) {
                val expected = script[start + j]
                if (heard[i] == expected) offer(i + 1, j + 1, edits, 0f)
                else if (edits == 0) {
                    val sound = key(expected)
                    val homophone = sound != null && sound == key(heard[i])
                    offer(i + 1, j + 1, 1, weights[start + j] * if (homophone) 0.4f else 1f)
                }
            }
            if (edits == 0) {
                if (i < heard.size) offer(i + 1, j, 1, 1f)
                if (j < width - 1) offer(i, j + 1, 1, weights[start + j])
            }
        }
        return minOf(costs[at(heard.size, width - 1, 0)], costs[at(heard.size, width - 1, 1)])
    }
}
