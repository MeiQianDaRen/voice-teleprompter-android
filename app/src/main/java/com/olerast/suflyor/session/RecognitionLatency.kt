package com.olerast.suflyor.session

/** Estimated emission-to-arrival lag, not a ground-truth phoneme boundary or confidence score. */
internal class RecognitionLatency {
    private val recent = ArrayDeque<Long>()
    private var segment = -1L
    private var lastSpeech = -1L
    private var lastMeasuredAt = 0L
    private var estimate: Long? = null

    fun reset() {
        recent.clear()
        segment = -1L
        lastSpeech = -1L
        estimate = null
        lastMeasuredAt = 0L
    }

    fun observe(segmentId: Long, audioMs: Long?, speechMs: Long?, capturedAt: Long, now: Long,
                trusted: Boolean, isFinal: Boolean): Long? {
        if (segmentId != segment) { segment = segmentId; lastSpeech = -1L }
        if (trusted && !isFinal && audioMs != null && speechMs != null &&
            speechMs > lastSpeech && speechMs >= 0 && speechMs <= audioMs && capturedAt <= now) {
            val measured = now - capturedAt + audioMs - speechMs
            if (measured in 0L..1800L) {
                recent.addLast(measured)
                while (recent.size > 5) recent.removeFirst()
                val median = recent.sorted()[recent.size / 2]
                estimate = estimate?.let { (it * 0.4 + median * 0.6).toLong() } ?: median
                lastMeasuredAt = now
            }
            lastSpeech = speechMs
        }
        return estimate?.takeIf { now - lastMeasuredAt in 0L..2000L }
    }
}

/** Numerical diagnostics only; no recordings or transcript content. */
internal class FollowTiming {
    private var count = 0
    private var queueSum = 0L
    private var decodeSum = 0L
    private var alignSum = 0L
    private var publishSum = 0L
    private var published = 0
    private var maxQueue = 0L

    @Synchronized fun record(queue: Long, decode: Long, align: Long) {
        count++
        queueSum += queue.coerceAtLeast(0)
        decodeSum += decode.coerceAtLeast(0)
        alignSum += align.coerceAtLeast(0)
        maxQueue = maxOf(maxQueue, queue)
    }
    @Synchronized fun publish(delay: Long) { published++; publishSum += delay.coerceAtLeast(0) }
    @Synchronized fun drain(): String {
        val n = count.coerceAtLeast(1)
        val result = "Follow ms: queue=${queueSum / n} (max=$maxQueue), decode=${decodeSum / n}, align=${alignSum / n}, main=${publishSum / published.coerceAtLeast(1)}"
        count = 0; queueSum = 0; decodeSum = 0; alignSum = 0; publishSum = 0; published = 0; maxQueue = 0
        return result
    }
}
