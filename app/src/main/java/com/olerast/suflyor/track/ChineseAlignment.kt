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
    private var longestPartial: List<String> = emptyList()
    private var lastPartial: List<String> = emptyList()
    private var ignoredPrefix: List<String> = emptyList()
    private var pendingRetake: Match? = null

    data class Result(val position: Int, val matches: Int, val reason: String)
    private data class Match(val start: Int, val end: Int, val count: Int, val consecutive: Int, val tail: Int, val heardStart: Int = 0)

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
        longestPartial = emptyList()
        lastPartial = emptyList()
        ignoredPrefix = emptyList()
        pendingRetake = null
    }

    fun consume(text: String, isFinal: Boolean): Result {
        val raw = ChineseNorm.words(text).filter { ChineseNorm.isHan(it) && it !in ChineseNorm.FILLERS }.takeLast(512)
        val retracting = !isFinal && raw.size < longestPartial.size && raw == longestPartial.take(raw.size)
        if (ignoredPrefix.isNotEmpty() && raw.take(ignoredPrefix.size) != ignoredPrefix) ignoredPrefix = emptyList()
        val heard = raw.drop(ignoredPrefix.size)
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
        val completeFollow = best != null && best.count == heard.size && best.end >= cursor
        val backward = if (retracting || completeFollow) null else findBackward(heard)
        // Compare the same trailing phrase in both directions, including the same tolerated errors.
        if (backward != null) {
            val tail = heard.drop(backward.heardStart)
            val ahead = (cursor until minOf(script.size, cursor + 65)).asSequence().map { match(it, tail) }
                .firstOrNull { it.count >= backward.count && retakeConfident(it, tail.size) }
            if (ahead != null && (best == null || ahead.count >= best.count)) {
                best = ahead
                reason = "follow (recovered phrase)"
            }
        }
        // An equally strong current/full match or upcoming phrase wins over an older occurrence.
        val forwardWins = best != null && backward != null && best.count >= backward.count && best.end >= cursor
        val pending = pendingRetake
        val growing = raw.size > lastPartial.size && lastPartial.isNotEmpty() && raw.take(lastPartial.size) == lastPartial
        val confirmed = backward != null && (isFinal ||
            (backward.count >= 8 && backward.count == heard.size) ||
            (growing && pending != null && kotlin.math.abs(backward.start - pending.start) <= 2 && backward.end > pending.end))
        if (backward != null && !forwardWins && confirmed) {
            ignoredPrefix = raw.take(raw.size - heard.size + backward.heardStart)
            anchor = backward.start
            cursor = backward.end
            best = backward
            pendingRetake = null
            reason = "back (confirmed phrase)"
        } else {
            pendingRetake = if (!forwardWins) backward else null
            if (best != null) cursor = maxOf(cursor, best.end)
        }
        if (isFinal) {
            anchor = cursor
            longestPartial = emptyList()
            lastPartial = emptyList()
            ignoredPrefix = emptyList()
            pendingRetake = null
        } else {
            lastPartial = raw
            if (raw.size > longestPartial.size) longestPartial = raw
        }
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

    /** A unique trailing phrase can prove a retake even when its utterance prefix was wrong. */
    private fun findBackward(heard: List<String>): Match? {
        if (heard.size < 6) return null
        val from = (cursor - 240).coerceAtLeast(0)
        val byEnd = mutableMapOf<Int, Match>()
        for (length in minOf(24, heard.size) downTo 6) {
            val tail = heard.takeLast(length)
            for (start in (cursor - 1) downTo from) {
                val candidate = match(start, tail)
                val span = candidate.end - candidate.start
                if (candidate.end > cursor || !retakeConfident(candidate, length)) continue
                val previous = byEnd[candidate.end]
                if (previous == null || candidate.count > previous.count ||
                    (candidate.count == previous.count && span < previous.end - previous.start)) {
                    byEnd[candidate.end] = candidate.copy(heardStart = heard.size - length)
                }
            }
        }
        val best = byEnd.values.maxWithOrNull(compareBy<Match> { it.count }.thenBy { it.consecutive }
            .thenBy { -(it.end - it.start) }) ?: return null
        if (best.end == cursor) return null
        // Repeated short phrases must not pick a previous paragraph merely because it was nearer.
        if (byEnd.values.any { kotlin.math.abs(it.end - best.end) > 2 && it.count >= best.count - 1 }) return null
        return best
    }

    private fun retakeConfident(match: Match, length: Int): Boolean = match.tail == 0 &&
        match.count >= 6 && match.consecutive >= 3 && match.count.toFloat() / length >= 0.8f &&
        match.count.toFloat() / (match.end - match.start).coerceAtLeast(1) >= 0.8f
}
