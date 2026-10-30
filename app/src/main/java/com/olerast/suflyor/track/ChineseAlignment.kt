package com.olerast.suflyor.track

import com.olerast.suflyor.script.ChineseNorm
import com.olerast.suflyor.script.Token

/**
 * Character alignment adapted from Storm-Teleprompter-Plus (MIT, Copyright 2026 Html5syt).
 * See licenses/Storm-Teleprompter-Plus-MIT.txt. Partials are recomputed from the committed
 * utterance anchor; only final results commit it. Indices stay in the original spoken-token space.
 */
internal class ChineseAlignment(tokens: List<Token>) {
    private val eligible = tokens.indices.filter { ChineseNorm.isHan(tokens[it].norm) && tokens[it].weight > 0f }
    private val indices = eligible.flatMap { i -> List(tokens[i].norm.codePointCount(0, tokens[i].norm.length)) { i } }.toIntArray()
    private val script = eligible.flatMap { ChineseNorm.words(tokens[it].norm) }
    private val total = tokens.size
    private var anchor = 0
    private var cursor = 0

    data class Result(val position: Int, val matches: Int, val reason: String)
    private data class Match(val start: Int, val end: Int, val count: Int, val consecutive: Int, val tail: Int)

    fun reset(position: Int) {
        // A written symbol can map to several spoken characters: seek to its first character.
        var low = 0
        var high = indices.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (indices[mid] < position) low = mid + 1 else high = mid
        }
        cursor = low
        anchor = cursor
    }

    fun consume(text: String, isFinal: Boolean): Result {
        val heard = ChineseNorm.words(text).filter { ChineseNorm.isHan(it) && it !in ChineseNorm.FILLERS }.takeLast(512)
        var best: Match? = null
        // Search near the last committed position; short common fragments cannot relocate the cursor.
        for (start in anchor until minOf(script.size, anchor + 65)) {
            val candidate = match(start, heard)
            if (!confident(candidate, heard.size)) continue
            if (best == null || candidate.count > best.count ||
                (candidate.count == best.count && candidate.end - candidate.start < best.end - best.start)
            ) best = candidate
        }

        var reason = "follow"
        if (best == null) {
            best = recover(heard)
            if (best != null) reason = "follow (recovered phrase)"
        }
        val backward = if (heard.size >= 8) findBackward(heard) else null
        // A complete repeated phrase may permit a retake, but an equally good upcoming phrase wins.
        if (backward != null && (best == null || best.count < heard.size)) {
            anchor = backward
            cursor = backward + heard.size
            best = Match(backward, cursor, heard.size, heard.size, 0)
            reason = "back (confirmed phrase)"
        } else if (best != null) {
            cursor = maxOf(cursor, best.end)
        }
        if (isFinal) anchor = cursor
        return Result(if (cursor >= indices.size) total else indices[cursor], best?.count ?: 0,
            if (best == null) "no confident characters" else reason)
    }

    private fun confident(match: Match, length: Int): Boolean {
        if (length == 0 || match.count == 0 || match.tail > 2) return false
        if (length == 1) return match.start == anchor && cursor == anchor && match.count == 1 && match.end == anchor + 1
        val minimum = if (match.start == anchor) 2 else 3
        return match.count >= minimum && match.consecutive >= 2 &&
            match.count.toFloat() / length >= 0.65f &&
            match.count.toFloat() / (match.end - match.start).coerceAtLeast(1) >= 0.6f
    }

    /** Tolerate up to three missing/extra characters without treating scattered common characters as a phrase. */
    private fun match(start: Int, heard: List<String>): Match {
        var s = start
        var h = 0
        var end = start
        var matched = 0
        var run = 0
        var longest = 0
        var lastHeard = -1
        while (s < script.size && h < heard.size) {
            if (script[s] == heard[h]) {
                matched++
                run++
                longest = maxOf(longest, run)
                end = ++s
                lastHeard = h++
                continue
            }
            run = 0
            val skipScript = (1..3).firstOrNull { s + it < script.size && script[s + it] == heard[h] }
            if (skipScript != null) {
                s += skipScript
                continue
            }
            val skipHeard = (1..3).firstOrNull { h + it < heard.size && heard[h + it] == script[s] }
            if (skipHeard != null) {
                h += skipHeard
                continue
            }
            s++
            h++
        }
        return Match(start, end, matched, longest, heard.size - lastHeard - 1)
    }

    /** A wrong prefix must not block a fresh, unambiguous phrase at the end of the utterance. */
    private fun recover(heard: List<String>): Match? {
        if (heard.size < 8) return null
        val limit = minOf(script.size, cursor + 65)
        for (length in minOf(12, heard.size) downTo 6) {
            val tail = heard.takeLast(length)
            val starts = (cursor..(limit - length)).filter { start ->
                tail.indices.all { script[start + it] == tail[it] }
            }
            if (starts.size == 1) {
                val start = starts.single()
                return Match(start, start + length, length, length, 0)
            }
            if (starts.size > 1) return null
        }
        return null
    }

    private fun findBackward(heard: List<String>): Int? {
        val from = (cursor - 240).coerceAtLeast(0)
        val last = (cursor - heard.size - 1).coerceAtMost(script.size - heard.size)
        for (start in last downTo from) {
            if (heard.indices.all { script[start + it] == heard[it] }) return start
        }
        return null
    }
}
