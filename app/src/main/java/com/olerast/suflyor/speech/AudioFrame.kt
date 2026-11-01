package com.olerast.suflyor.speech

data class AudioFrame(
    val samples: FloatArray,
    val sampleRate: Int,
    val rmsDb: Float,
    val digitalSilence: Boolean,
    val capturedAtMs: Long,
    val queuedAtMs: Long,
    val discontinuity: Boolean = false,
)

/** The producer never waits for inference. An overload is an explicit stream gap, never hidden old audio. */
internal class AudioFrameQueue(private val maxBufferedMs: Int = 1200) {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") // wait/notify use the same monitor as the queue mutations.
    private val monitor = java.lang.Object()
    private val frames = ArrayDeque<AudioFrame>()
    private var bufferedMs = 0.0
    private var closed = false
    @Volatile var droppedMs = 0L
        private set

    fun offer(frame: AudioFrame): Boolean = synchronized(monitor) {
        if (closed) return false
        val duration = frame.samples.size * 1000.0 / frame.sampleRate
        val overflow = bufferedMs + duration > maxBufferedMs
        if (overflow || frame.discontinuity) {
            droppedMs += bufferedMs.toLong()
            frames.clear()
            bufferedMs = 0.0
        }
        frames.addLast(if (overflow) frame.copy(discontinuity = true) else frame)
        bufferedMs += duration
        monitor.notifyAll()
        true
    }

    fun take(): AudioFrame? = synchronized(monitor) {
        while (!closed && frames.isEmpty()) monitor.wait()
        if (closed) return null
        frames.removeFirst().also { bufferedMs -= it.samples.size * 1000.0 / it.sampleRate }
    }

    fun close() = synchronized(monitor) {
        closed = true
        frames.clear()
        bufferedMs = 0.0
        monitor.notifyAll()
    }
}

/** AudioRecord timestamps and UI/decoder clocks all use elapsedRealtime (including suspend). */
internal class AudioSampleClock(private val sampleRate: Int) {
    private var fallbackOrigin: Double? = null
    private var lastEnd = 0L

    fun endTime(readFrames: Long, nowMs: Long, hardwareFrames: Long? = null, hardwareTimeMs: Double? = null): Long {
        val elapsed = readFrames * 1000.0 / sampleRate
        if (fallbackOrigin == null) fallbackOrigin = nowMs - elapsed
        val hardware = if (hardwareFrames != null && hardwareFrames >= 0 && hardwareTimeMs?.isFinite() == true)
            hardwareTimeMs + (readFrames - hardwareFrames) * 1000.0 / sampleRate else null
        val candidate = hardware?.takeIf { it in (nowMs - 5000.0)..(nowMs + 20.0) }
            ?: (fallbackOrigin!! + elapsed)
        lastEnd = candidate.toLong().coerceIn(lastEnd.coerceAtMost(nowMs), nowMs)
        return lastEnd
    }
}
