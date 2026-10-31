package com.olerast.suflyor.track

import com.olerast.suflyor.script.ChineseNorm
import com.olerast.suflyor.script.Token
import com.olerast.suflyor.script.ChineseVoiceCommands

/**
 * Character alignment adapted from Storm-Teleprompter-Plus (MIT, Copyright 2026 Html5syt).
 * See licenses/Storm-Teleprompter-Plus-MIT.txt. Short utterances use a committed anchor; long partials
 * align only newly added text in bounded local windows. Indices stay in the original spoken-token space.
 */
internal class ChineseAlignment(tokens: List<Token>, retakeChars: Int = ChineseRetakePolicy.DEFAULT_CHARS, pronunciation: (String) -> String? = { null }) {
    private val eligible = tokens.indices.filter { ChineseNorm.isHan(tokens[it].norm) && tokens[it].weight > 0f }
    private val indices = eligible.flatMap { i -> List(tokens[i].norm.codePointCount(0, tokens[i].norm.length)) { i } }.toIntArray()
    private val script = eligible.flatMap { ChineseNorm.words(tokens[it].norm) }
    private val local = ChineseLocalAlignment(script, indices.map { tokens[it].weight },
        indices.indices.filter { i -> tokens[indices[i]].anchor && (i == 0 || indices[i] != indices[i - 1]) }.toSet(), pronunciation)
    enum class Phase { FOLLOWING, LOW_CONFIDENCE, SILENT, RETAKE_PENDING, REANCHORING }
    var phase = Phase.FOLLOWING
        private set
    private var lastLocalHeard: List<String> = emptyList()
    private var lastLocalMatched = false
    private var failures = 0
    private var lostSince: Long? = null
    private var holdUntil = 0L
    private data class PendingFollow(val match: Match, val requiresSecond: Boolean, val isFinal: Boolean)
    private var pendingFollow: PendingFollow? = null
    private val total = tokens.size
    private var anchor = 0
    private var cursor = 0
    private var longestPartial: List<String> = emptyList()
    private var ignoredPrefix: List<String> = emptyList()
    private var retakeChars = retakeChars.coerceIn(ChineseRetakePolicy.MIN_CHARS, ChineseRetakePolicy.MAX_CHARS)
    private var pendingRetake: PendingRetake? = null
    private var handledCommands = 0
    private data class PendingRetake(val match: Match, val prefix: List<String>, val raw: List<String>,
        val tail: List<String>, val isFinal: Boolean, val since: Long, val observations: Int)

    data class Result(val position: Int, val matches: Int, val reason: String)
    private data class Match(val start: Int, val end: Int, val count: Int, val consecutive: Int, val tail: Int, val heardStart: Int = 0, val edits: Int = 0)

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
        ignoredPrefix = emptyList()
        pendingRetake = null
        handledCommands = 0
        lastLocalHeard = emptyList()
        lastLocalMatched = false
        failures = 0
        lostSince = null
        holdUntil = 0L
        pendingFollow = null
        phase = Phase.FOLLOWING
    }

    fun consume(text: String, isFinal: Boolean, nowMs: Long, speechActive: Boolean = true): Result {
        val utterance = ChineseNorm.words(text).filter { ChineseNorm.isHan(it) && it !in ChineseNorm.FILLERS }.takeLast(512)
        val commands = ChineseVoiceCommands.restartEnds(utterance)
        val restarted = commands.size > handledCommands
        if (restarted) reset(0)
        handledCommands = commands.size
        // Keep stripping the command as its partial grows; the matching final must not restart twice.
        val raw = utterance.drop(commands.lastOrNull() ?: 0)
        val retracting = !isFinal && raw.size < longestPartial.size && raw == longestPartial.take(raw.size)
        if (ignoredPrefix.isNotEmpty() && raw.take(ignoredPrefix.size) != ignoredPrefix) ignoredPrefix = emptyList()
        val heard = raw.drop(ignoredPrefix.size)
        if (!speechActive && !restarted) {
            silence()
            return Result(if (cursor >= indices.size) total else indices[cursor], 0, "silent hold")
        }
        val changed = heard != lastLocalHeard
        val previousHeard = lastLocalHeard
        var best: Match? = null
        // Search near the last committed position; short common fragments cannot relocate the cursor.
        for (start in maxOf(anchor, cursor - 16) until (if (heard.size <= 16) minOf(script.size, cursor + 29) else anchor)) {
            val candidate = match(start, heard)
            if (!confident(candidate, heard.size)) continue
            if (best == null || candidate.count > best.count ||
                (candidate.count == best.count && candidate.end - candidate.start < best.end - best.start)
            ) best = candidate
        }

        var reason = "follow"
        var shortAnchor = false
        if (best == null && !retracting && changed) {
            val growing = previousHeard.isNotEmpty() && heard.size > previousHeard.size && heard.take(previousHeard.size) == previousHeard
            val from = if (previousHeard.isEmpty()) 0 else if (growing) previousHeard.size else (heard.size - 8).coerceAtLeast(0)
            var probe = cursor
            var trusted = growing && lastLocalMatched
            var validStart = trusted
            var endpoint = minOf(from + 8, heard.size)
            while (endpoint > 0) {
                val hit = local.find(heard.subList((endpoint - 8).coerceAtLeast(0), endpoint), probe,
                    continuity = trusted || endpoint <= 8 && from == 0)
                if (from == 0 && endpoint <= 8 && hit != null && hit.start <= cursor) validStart = true
                if (hit != null) {
                    best = Match(hit.start, hit.end, hit.matches, hit.matches, 0)
                    shortAnchor = hit.length == 3 || (hit.length < 6 && !validStart && hit.end - cursor > 3)
                    probe = hit.end
                    trusted = true
                }
                if (endpoint == heard.size) {
                    if (hit == null && !validStart) best = null
                    break
                }
                endpoint = minOf(endpoint + 8, heard.size)
            }
            if (best != null) reason = "follow (local anchor)"
        }
        if (best == null && changed && heard.size >= 4 && !retracting) {
            failures++
            if (lostSince == null) lostSince = nowMs
            if (failures == 3) holdUntil = nowMs + 400L
            phase = if (nowMs - lostSince!! >= 2000L) Phase.REANCHORING else Phase.LOW_CONFIDENCE
        }
        if (best == null && (heard.size <= 16 || phase == Phase.REANCHORING)) {
            best = recover(heard)
            if (best != null) reason = "follow (recovered phrase)"
        }
        lastLocalMatched = best != null && best.end >= cursor
        lastLocalHeard = if (isFinal) emptyList() else heard
        val followCandidate = best
        if (followCandidate != null && followCandidate.end > cursor) {
            val previous = pendingFollow
            val confirmedShort = previous?.requiresSecond == true && changed &&
                followCandidate.end - previous.match.end in 0..3
            if ((shortAnchor && !confirmedShort) || nowMs < holdUntil) {
                pendingFollow = PendingFollow(followCandidate, shortAnchor && !confirmedShort, isFinal)
                best = null
                phase = Phase.LOW_CONFIDENCE
            } else {
                pendingFollow = null
                failures = 0
                lostSince = null
                phase = Phase.FOLLOWING
            }
        } else if (changed) pendingFollow = null
        val completeFollow = best != null && best.count == heard.size && best.end >= cursor
        val backward = if (retracting || completeFollow) null else findBackward(heard)
        // Compare the same trailing phrase in both directions, including the same tolerated errors.
        if (backward != null && pendingFollow == null && nowMs >= holdUntil) {
            val tail = heard.drop(backward.heardStart)
            val ahead = (cursor until minOf(script.size, cursor + 29)).asSequence().mapNotNull { retakeAt(it, tail, script.size) }
                .firstOrNull { it.edits <= backward.edits && it.count >= backward.count }
            if (ahead != null && (best == null || ahead.count >= best.count)) {
                best = ahead
                reason = "follow (recovered phrase)"
            }
        }
        // An equally strong current/full match or upcoming phrase wins over an older occurrence.
        val forwardEvidence = best ?: pendingFollow?.match
        val forwardWins = forwardEvidence != null && backward != null &&
            forwardEvidence.count >= backward.count && forwardEvidence.end >= cursor
        if (backward != null && !forwardWins) {
            pendingFollow = null
            phase = Phase.RETAKE_PENDING
            val pending = pendingRetake
            val tail = heard.takeLast(retakeChars)
            val growing = pending != null && raw.size > pending.raw.size && raw.take(pending.raw.size) == pending.raw
            val sameRun = pending != null && (backward.end == pending.match.end ||
                (growing && backward.end - pending.match.end in 1..ChineseRetakePolicy.MAX_CHARS))
            val newObservation = pending == null || pending.raw != raw || pending.isFinal != isFinal
            val stable = sameRun && pending.tail == tail && pending.match.end == backward.end
            val since = if (stable) pending.since else nowMs
            val observations = if (sameRun) pending.observations + if (newObservation) 1 else 0 else 1
            pendingRetake = PendingRetake(backward, raw.take(raw.size - heard.size + backward.heardStart),
                raw, tail, isFinal, since, observations)
            if (observations >= 2 || nowMs - since >= ChineseRetakePolicy.STABLE_MS) {
                applyRetake(pendingRetake!!)
                best = backward
                reason = "back (confirmed phrase)"
            } else best = null
        } else {
            pendingRetake = null
            if (best != null) cursor = maxOf(cursor, best.end)
        }
        if (isFinal) {
            anchor = cursor
            longestPartial = emptyList()
            ignoredPrefix = emptyList()
        } else if (raw.size > longestPartial.size) longestPartial = raw
        return Result(if (cursor >= indices.size) total else indices[cursor], best?.count ?: 0,
            if (restarted) "restart (voice command)" else if (best == null) "no confident characters" else reason)
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

    private fun retakeAt(start: Int, tail: List<String>, limit: Int): Match? {
        var best: Match? = null
        for (length in (tail.size - 1)..(tail.size + 1)) {
            val end = start + length
            if (length <= 0 || end > limit) continue
            val edits = ChineseRetakePolicy.editCount(tail, script, start, end) ?: continue
            val matches = minOf(tail.size, length) - if (length == tail.size) edits else 0
            val candidate = Match(start, end, matches, matches, 0, edits = edits)
            if (best == null || candidate.edits < best.edits ||
                (candidate.edits == best.edits && candidate.count > best.count)) best = candidate
        }
        return best
    }

    /** Only the configured trailing window is evidence, with at most one edit and a unique location. */
    private fun findBackward(heard: List<String>): Match? {
        if (heard.size < retakeChars) return null
        val tail = heard.takeLast(retakeChars)
        val from = (cursor - ChineseRetakePolicy.DISTANCE).coerceAtLeast(0)
        val candidates = ((cursor - 1) downTo from).mapNotNull { start ->
            retakeAt(start, tail, cursor)?.copy(heardStart = heard.size - retakeChars)
        }
        val best = candidates.minWithOrNull(compareBy<Match> { it.edits }.thenByDescending { it.count }
            .thenBy { kotlin.math.abs(it.end - it.start - retakeChars) }) ?: return null
        if (best.end == cursor) return null
        if (candidates.any { kotlin.math.abs(it.end - best.end) > 1 && it.edits == best.edits }) return null
        return best
    }

    private fun applyRetake(pending: PendingRetake) {
        cursor = pending.match.end
        lastLocalHeard = emptyList()
        pendingFollow = null
        failures = 0
        lostSince = null
        holdUntil = 0L
        phase = Phase.FOLLOWING
        anchor = if (pending.isFinal) cursor else pending.match.start
        ignoredPrefix = if (pending.isFinal) emptyList() else pending.prefix
        pendingRetake = null
    }

    fun confirmRetake(nowMs: Long): Result? {
        val pending = pendingRetake ?: return null
        if (nowMs - pending.since < ChineseRetakePolicy.STABLE_MS) return null
        applyRetake(pending)
        return Result(if (cursor >= indices.size) total else indices[cursor], pending.match.count, "back (stable phrase)")
    }

    fun confirmFollow(nowMs: Long): Result? {
        val pending = pendingFollow ?: return null
        if (pending.requiresSecond || nowMs < holdUntil) return null
        pendingFollow = null
        cursor = maxOf(cursor, pending.match.end)
        if (pending.isFinal) anchor = cursor
        failures = 0
        lostSince = null
        phase = Phase.FOLLOWING
        return Result(if (cursor >= indices.size) total else indices[cursor], pending.match.count, "follow (confidence restored)")
    }

    fun silence() {
        pendingRetake = null
        pendingFollow = null
        phase = Phase.SILENT
    }

    fun cancelRetake() { pendingRetake = null; pendingFollow = null }

    fun setRetakeChars(value: Int) {
        val next = value.coerceIn(ChineseRetakePolicy.MIN_CHARS, ChineseRetakePolicy.MAX_CHARS)
        if (retakeChars == next) return
        retakeChars = next
        cancelRetake()
    }
}
